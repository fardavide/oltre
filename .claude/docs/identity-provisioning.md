# Identity provisioning — Apple and Google, step by step

**Status:** ready to follow. Written 2026-08-25, for issue #110 under epic #106.

This is a tutorial, not a summary. Follow it top to bottom and tell me where it is wrong, vague or
missing a step — corrections go back into this file rather than into a chat message, so the next
person to do this reads the fixed version.

Every value here was verified on 2026-08-25 against the repository, the machine, and Apple's and
Google's current documentation. Where something is Apple's or Google's to generate, it is written
`<LIKE_THIS>` and described so you can tell you have the right thing.

**What this gets you:** everything issue #110 needs from a human, done before the slice starts, so
the session that writes the auth code is never blocked on a portal.

Two portals, one registrar, one local secret store. Around 90 minutes of clicking.
**Steps 1–34 can all be finished today.** Steps 35–45 genuinely wait, and each one says on what.

**Read these four before you click anything**, because each is a door that only opens one way:
step 5 (the keystore just changed job), step 13 (the capability invalidates provisioning profiles),
step 17 (the `.p8` downloads exactly once), step 26 (Google client secrets are shown exactly once).

---

## Before you start

- The Apple Developer account for team **A7Q83J6LR4**, signed in as **Account Holder or Admin**.
  Both the Sign in with Apple capability and the key creation require one of those two roles.
- A Google account. **Use the same one for Google Search Console and for the Google Cloud project.**
  Cloud Run's custom-domain mapping checks that the Cloud project owner is a verified owner of
  `oltre.space` in Search Console, and mismatched accounts is the usual way that fails.
- The Namecheap account holding `oltre.space`.
- A terminal with `keytool` (ships with the JDK), `openssl`, and `gh` already authenticated.
  All three are present on this machine — checked.
- Somewhere off this laptop for one passphrase — Apple Passwords or 1Password.

**Two things are not installed, and only one of them matters today.** Checked on 2026-08-25:
`gcloud` and `firebase` are both absent (`node` and `npm` are present). Steps 1–34 need neither —
the Cloud project and all five OAuth clients are created in a browser. Only step 35, which is
optional groundwork, wants `gcloud`:

```
brew install --cask google-cloud-sdk
gcloud auth login
```

**And step 35 needs billing enabled on the Cloud project**, which steps 22–34 do not. Creating a
project, configuring the Auth Platform and creating OAuth clients are all free and need no card.
Secret Manager and Cloud Run do require a billing account attached — that is the point at which
epic §6's note applies: *"Cloud Run's free tier requires a billing account with a card on file. A
request loop or a scraper can generate real spend, and a budget alert warns rather than caps."*
If you would rather not attach a card today, stop after step 34; nothing later in the identity
slice is blocked by it.

---

# Part 1 — The net, before anything is downloaded

## 1. Widen `.gitignore` first

`.gitignore` today covers `*.jks`, `*.keystore` and `keystore.properties` and nothing else
credential-shaped. A `.p8` or a `client_secret_*.json` dropped anywhere in the checkout is
committable right now, and this is a public repository. Do this before you download anything.

Add to `/Users/davide/Dev/Projects/Oltre/.gitignore`:

```gitignore
# Sign-in credentials. One-shot downloads that must never reach a public repository.
*.p8
*.p12
*.pem
*.mobileprovision
client_secret*.json
.env
.env.*
```

Commit it on a branch and merge it, or let the identity slice carry it — but have the lines in the
working tree before step 17.

## 2. Create the local secret directory

Matches the existing convention: `~/.oltre` is already `0700` and holds `keystore-password`,
`keystore.b64` and `oltre-release.keystore` at `0600`. Do not invent a second root.

```
mkdir -p -m 700 ~/.oltre/signin
```

## 3. Confirm the release fingerprint has not drifted

Before you paste it into Google's console, re-derive it. The password is in
`~/.oltre/keystore-password`.

```
keytool -list -v -alias oltre -keystore ~/.oltre/oltre-release.keystore
```

Expect `SHA1: 24:AA:53:1D:64:20:C6:B9:65:86:FD:53:E6:B7:1A:E7:0C:3E:DB:98`. If it differs, stop —
the APKs on your GitHub Releases are signed by a different key than you think, and everything
downstream of step 22 would be wrong.

## 4. Read this before step 5

> **The Android signing key just changed job.** Until now, losing
> `~/.oltre/oltre-release.keystore` meant "no future build installs over an installed one" — bad,
> but bounded. The moment Google Sign-In is bound to its SHA-1, losing it also means **sign-in
> breaks for every player who already has the app**, because the fingerprint Google checks at
> runtime is that key's own (there is no Play App Signing here; you ship a self-signed APK on a
> GitHub Release). The recorded position as of 2026-08-09 is that both copies of that key live on
> the same SSD, with no Time Machine destination configured and `~/Documents` not actually
> iCloud-synced — the iCloud Drive "Documents" entry is a symlink and iCloud does not follow
> symlinks. That is one hardware failure, not two.

## 5. Make the off-machine encrypted copy — before registering the Android client

```
hdiutil create -size 20m -fs APFS -encryption AES-256 -volname "Oltre secrets" ~/Library/Mobile\ Documents/com~apple~CloudDocs/oltre-secrets.dmg
```

It prompts for a passphrase twice. **Put that passphrase in Apple Passwords or 1Password, not in
`~/.oltre`** — a backup whose passphrase dies with the laptop is not a backup.

```
hdiutil attach ~/Library/Mobile\ Documents/com~apple~CloudDocs/oltre-secrets.dmg
cp -p ~/.oltre/oltre-release.keystore ~/.oltre/keystore-password /Volumes/Oltre\ secrets/
```

Leave it mounted — step 31 adds the sign-in files to the same image. This is written to the real
iCloud Drive path deliberately, so it is genuinely off-machine.

---

# Part 2 — DNS and the two hostnames

Do this before the portals: DNS propagation and GitHub's certificate issuance run in the background
while you click.

`oltre.space` is at Namecheap on **Namecheap BasicDNS** (`dns1.registrar-servers.com`,
`dns2.registrar-servers.com`). The apex currently resolves to the Namecheap parking IP
`162.255.119.165`; `api.oltre.space` does not resolve at all. Everything below happens at
**Namecheap Dashboard → Domain List → Manage (oltre.space) → Advanced DNS**. Leave the nameservers
alone — Advanced DNS only applies while BasicDNS is selected.

## 6. Get the Google Search Console verification token first

Go to <https://search.google.com/search-console>, signed in as the Google account that will own the
Cloud project.

- **Add property** → the left-hand **Domain** box → type `oltre.space` → **Continue**.
- Google shows a TXT record value that looks like
  `google-site-verification=<44-CHARACTERS>`. Copy it. Leave the dialog open.

This Domain property covers `api.oltre.space` as well, which is exactly what Cloud Run's domain
mapping needs later. It is one of the very few deployment-adjacent things not blocked today.

## 7. Edit the Namecheap records — one visit, six additions

**Namecheap → Domain List → Manage (oltre.space) → Advanced DNS → Host Records.**

**Delete** the parking entries first (bin icon on the right of each row): typically an
`A Record` or `URL Redirect Record` for host `@`, and a `CNAME Record` for host `www` pointing at
`parkingpage.namecheap.com`.

Then **Add New Record**, six times:

| Type | Host | Value | TTL |
|---|---|---|---|
| A Record | `@` | `185.199.108.153` | Automatic |
| A Record | `@` | `185.199.109.153` | Automatic |
| A Record | `@` | `185.199.110.153` | Automatic |
| A Record | `@` | `185.199.111.153` | Automatic |
| CNAME Record | `www` | `fardavide.github.io.` | Automatic |
| TXT Record | `@` | `google-site-verification=<44-CHARACTERS>` | Automatic |

Click the green tick on each row, then **Save all changes**.

Optional, and worth thirty seconds — IPv6 for GitHub Pages. Four `AAAA Record` rows on host `@`:
`2606:50c0:8000::153`, `2606:50c0:8001::153`, `2606:50c0:8002::153`, `2606:50c0:8003::153`.

Nothing for `api.oltre.space` yet: Cloud Run hands you the exact records when the domain mapping is
created (step 35), and inventing them now would only have to be deleted.

## 8. Verify in Search Console

Back in the still-open dialog, click **Verify**. If it fails, wait a few minutes for the TXT to
propagate and press it again. Check with:

```
dig +short TXT oltre.space
```

## 9. Create the public site on a `gh-pages` branch

Use a dedicated orphan branch, not `main` and not `main`'s `/docs` folder. Two reasons: `/docs`
already contains `ui-mockup.html`, which would become publicly served; and **merging to `main`
publishes** — Xcode Cloud archives to TestFlight and the release workflow can cut a GitHub Release.
A typo fix in a privacy policy should not be able to ship a build.

In a throwaway clone, so nothing touches your working checkout:

```
git clone https://github.com/fardavide/oltre.git /tmp/oltre-pages
cd /tmp/oltre-pages
git switch --orphan gh-pages
touch .nojekyll
```

Create `/tmp/oltre-pages/index.html`. Something minimal and honest — a title, one sentence, and
nothing that pretends to be a privacy policy. Paste this if you do not want to write one:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Oltre</title>
    <style>
      body { margin: 0; min-height: 100vh; display: grid; place-items: center;
             background: #0b0e17; color: #e8eaf2;
             font: 16px/1.6 ui-sans-serif, system-ui, sans-serif; }
      main { max-width: 32rem; padding: 2rem; }
      h1 { font-weight: 600; letter-spacing: 0.04em; margin: 0 0 0.5rem; }
      p { color: #9aa3b8; margin: 0; }
    </style>
  </head>
  <body>
    <main>
      <h1>Oltre</h1>
      <p>An asynchronous space colonisation game. Everything progresses while the app is closed.</p>
    </main>
  </body>
</html>
``` `.nojekyll` matters: it turns off Jekyll, so
files served are exactly the files committed and directories beginning with a dot are published.
That is what makes the step-16 contingency possible, and it means `/privacy` and `/terms` will need
to be `privacy/index.html` and `terms/index.html` rather than Markdown.

```
git add -A
git commit -m "Public site: home page for oltre.space"
git push -u origin gh-pages
```

Do **not** put placeholder privacy or terms pages up. A privacy policy a player could read and that
is not true is worse than a 404. Those pages land with the identity release (step 39).

## 10. Point GitHub Pages at the apex

<https://github.com/fardavide/oltre/settings/pages>

- **Build and deployment → Source:** Deploy from a branch. **Branch:** `gh-pages`, folder `/ (root)`.
  Save.
- **Custom domain:** type `oltre.space` → **Save**. GitHub runs a DNS check and commits a `CNAME`
  file containing `oltre.space` to the branch.
- Wait for the certificate. It is usually minutes, occasionally up to 24 hours. When **Enforce
  HTTPS** stops being greyed out, tick it.

## 11. Confirm

```
curl -sI https://oltre.space/
```

Expect `HTTP/2 200`. `https://www.oltre.space/` should redirect to the apex.

---

# Part 3 — Apple

Everything in this part is doable today. The old assumption that the Services ID was blocked on a
server URL is wrong now that `api.oltre.space` is a settled, permanent hostname.

## 12. Enable Sign in with Apple on the App ID

<https://developer.apple.com/account/resources/identifiers/list> → filter **App IDs** → click the row
whose Bundle ID is `dev.fardavide.oltre`.

- Tick the **Sign in with Apple** checkbox.
- An **Edit** / **Configure** button appears beside it. Click it and choose
  **"Enable as a primary App ID"**. Save the modal.
- **Save** at the top right.

## 13. Read the dialog, then confirm

> Apple's warning is real and says: *"Provisioning profiles that contain a modified App ID become
> invalid. You'll need to regenerate the provisioning profiles that use that App ID."* Accept it.
>
> You do **not** need to regenerate anything by hand. `iosApp/project.yml` sets
> `CODE_SIGN_STYLE: Automatic`, and Xcode Cloud uses cloud signing — it mints certificates and
> profiles at archive time, so the next `main` archive regenerates the profile carrying the new
> entitlement. Xcode Cloud needs no configuration change, no new secret and no `ci_scripts` edit.
> Locally, Xcode regenerates the development profile on the next build; if it does not, untick and
> re-tick "Automatically manage signing" once.
>
> **Order matters.** Do this portal step *before* the entitlement in step 40 reaches `main`, or the
> first archive after it fails signing — and that archive is a TestFlight publish.

"Primary" versus "grouped" is close to one-way: an App ID enabled by grouping cannot itself group
further identifiers, and the Services ID in step 14 must attach to a *primary*. There is only one app
on this team, so primary is unambiguously right.

## 14. Register the Services ID

<https://developer.apple.com/account/resources/identifiers/list> → blue **+** → **Services IDs** →
Continue.

- **Description:** `Oltre Sign in with Apple` (plain ASCII; Apple rejects punctuation here)
- **Identifier:** `dev.fardavide.oltre.signin`

Continue → Register.

The identifier **cannot** be `dev.fardavide.oltre`. App IDs and Services IDs share one namespace
across the entire developer programme, so that string is already taken by your own App ID.

What this thing *is*: the OAuth `client_id` for every client that is not an Apple-native app talking
to `AuthenticationServices`. It is a web credential, not a second app.

## 15. Configure the Services ID

Click `dev.fardavide.oltre.signin` → tick **Sign in with Apple** → **Configure**.

- **Primary App ID:** `dev.fardavide.oltre`
- **Domains and Subdomains:** `api.oltre.space`  *(no scheme, no trailing slash)*
- **Return URLs:** `https://api.oltre.space/v1/auth/apple/callback`

**Done** → **Continue** → **Save**.

Apple rejects `http`, `localhost`, bare IP addresses and any URL containing a fragment. Return URLs
must be absolute with scheme, host and path, and must later match the `redirect_uri` parameter
**byte for byte** — one trailing slash is enough to fail with `invalid_client`. As an individual
account you may register at most 10 website URLs in total, so do not start a URL-per-environment
habit.

## 16. If — and only if — the portal demands a domain association file

Apple's current help page states in as many words that you do **not** need to upload a file to your
server to register domains and subdomains. Most third-party guides are stale on this. If the portal
nevertheless asks for `apple-developer-domain-association.txt`:

- The file belongs at `https://api.oltre.space/.well-known/apple-developer-domain-association.txt`,
  which does not exist yet — but `oltre.space` does, and GitHub Pages will serve
  `.well-known/apple-developer-domain-association.txt` from the `gh-pages` branch **because you
  committed `.nojekyll`**. Add it there, register the apex as the domain, and it must return HTTP
  200 as `text/plain` with no redirect.
- Expect not to need this.

## 17. Read this, then create the key

> **The `.p8` downloads exactly once.** Apple: *"Save this file in a secure place because the key is
> not saved in your developer account and you won't be able to download it again. If the Download
> button is disabled, you previously downloaded the key."* There is no recovery path — a lost p8
> means revoking the key and creating a replacement. **Do not close the browser tab until step 19
> has both copies on disk.**

<https://developer.apple.com/account/resources/authkeys/list> → **+**

- **Key Name:** `Oltre Sign in with Apple`
- Tick **Sign in with Apple**, click **Configure** beside it, select Primary App ID
  `dev.fardavide.oltre`, **Save**.
- **Continue** → **Register**.

**Scope, since it is not obvious:** the key is scoped to a *primary App ID and its group* — not to
the whole team, and not to the Services ID. Because `dev.fardavide.oltre.signin` is configured
against `dev.fardavide.oltre`, **one key signs client secrets for both `client_id`s**. A primary App
ID may hold at most two such keys; that headroom exists so you can rotate — create the new one, cut
over, revoke the old one.

## 18. Download it and move it immediately

Click **Download**. The file lands in `~/Downloads` as `AuthKey_<KEYID>.p8` — around 250 bytes of
PKCS#8 PEM starting `-----BEGIN PRIVATE KEY-----`.

```
mv ~/Downloads/AuthKey_*.p8 ~/.oltre/signin/
chmod 600 ~/.oltre/signin/AuthKey_*.p8
```

`~/Downloads` is indexed, swept by cleanup tools and readable by anything you run. Leaving a
one-shot key there "for a few minutes" is how it gets lost.

## 19. Prove it is the right file, and fingerprint it

```
openssl pkey -in ~/.oltre/signin/AuthKey_<KEYID>.p8 -noout -text
```

Expect `Private-Key: (256 bit)` and `ASN1 OID: prime256v1`. Anything else means the wrong file.

```
openssl pkey -in ~/.oltre/signin/AuthKey_<KEYID>.p8 -pubout -outform DER | openssl dgst -sha256
```

Write that digest down for step 32. It is not secret, and it is the only thing that lets you tell
whether a restored backup is the same key — exactly the discipline already used for the keystore's
certificate SHA-256.

Only now close the browser tab.

## 20. Read the Key ID and record the Apple identifiers

<https://developer.apple.com/account/resources/authkeys/list> → click the key. The **Key ID** is the
10-character alphanumeric string under the key name, e.g. `ABC123DEFG`. It is permanently visible in
the portal, unlike the key itself, and it also happens to be in the filename — which is the only
reason a lost Key ID is recoverable at all.

```
touch ~/.oltre/signin/apple.env
chmod 600 ~/.oltre/signin/apple.env
open -e ~/.oltre/signin/apple.env
```

Paste, filling in the Key ID:

```
OLTRE_APPLE_TEAM_ID=A7Q83J6LR4
OLTRE_APPLE_KEY_ID=<10-CHAR-KEY-ID>
OLTRE_APPLE_BUNDLE_ID=dev.fardavide.oltre
OLTRE_APPLE_SERVICES_ID=dev.fardavide.oltre.signin
OLTRE_APPLE_REDIRECT_URI=https://api.oltre.space/v1/auth/apple/callback
```

None of these is secret — Team ID `A7Q83J6LR4` is already committed in `iosApp/project.yml`, which is
correct and not a leak. They live here because the Key ID changes on every rotation and separating it
from the key is what produces a broken deploy six months later.

## 21. Register the server-to-server notification endpoint (optional today, cheap now)

<https://developer.apple.com/account/resources/identifiers/list> → `dev.fardavide.oltre` →
**Sign in with Apple** → **Configure** → **Server-to-Server Notification Endpoint**:

```
https://api.oltre.space/v1/auth/apple/notifications
```

This is how you learn a player deleted their Apple Account or revoked the app, and it pairs with
`/auth/revoke` for the in-app account deletion App Review requires. It is worth entering today
purely because `api.oltre.space` is permanent — and it is harmless while the server does not exist,
because nobody has signed in yet, so there is nothing to notify about. One endpoint per app group,
shared by the App ID and the Services ID.

---

# Part 4 — Google

## 22. Create the Cloud project

<https://console.cloud.google.com/projectcreate>

- **Project name:** `Oltre`
- **Project ID:** `oltre-prod` (globally unique; Google will suffix it if taken — note whatever you
  end up with)
- **Location:** No organisation.

With no organisation attached, the Audience user type is forced to **External** and Internal is
greyed out. That is correct here; do not go looking for Internal.

OAuth clients belong to a project and cannot be moved between projects. Use the same project that
will host Cloud Run, so branding, clients and the service live together.

## 23. Run the Auth Platform wizard

<https://console.cloud.google.com/auth/overview> → **Get started**. This is where
"APIs & Services → OAuth consent screen" moved to; most tutorials still name the old menu item,
which no longer exists.

- **App name:** `Oltre`
- **User support email:** a dropdown, not a free-text box — it offers only the signed-in Google
  account or a Google Group you own — so an Apple private-relay alias will not appear in the list,
  and neither will an address that is not attached to a Google account. Whatever you pick is shown
  to every user who signs in, so choose deliberately.
- **Audience:** External
- **Developer contact:** the same address
- Agree to the User Data Policy → **Create**.

Leave the App domain block (home page / privacy / terms / authorized domains) **empty** for now. It
is only needed for brand verification, which is step 44 and is cosmetic. When you do fill it, add the
authorized domain *before* the URLs — the page rejects URLs whose domain is not already listed.

## 24. Add the scopes — three, and no more

<https://console.cloud.google.com/auth/scopes> → **Add or remove scopes** → tick:

```
openid
https://www.googleapis.com/auth/userinfo.email
https://www.googleapis.com/auth/userinfo.profile
```

**Update** → **Save**.

All three are **non-sensitive**, and that is the whole reason this stays cheap: Google's OAuth app
verification review is mandatory only for sensitive or restricted scopes. One sensitive scope drags
you into a multi-week review. Do not add anything else here.

You will store only `sub`. The email and profile claims arrive anyway from the mobile SDKs, so
requesting them changes nothing about verification — but it does mean the server must never persist
the email and must never log a raw ID token.

## 25. Do nothing on the Audience page

<https://console.cloud.google.com/auth/audience>

**Add no test users. Do not publish.** This is counter-intuitive and it is the single biggest piece
of busywork you can avoid. Google's own wording:

> *"The only exception to this behavior is if your app requests a subset of the following: name,
> email address, and user profile (through the userinfo.email, userinfo.profile, openid scopes or
> their OpenID Connect equivalents). For such requests, your users do not need to be in the trusted
> user list, they will not see a warning message, and their authorizations will not expire after 7
> days."*

Oltre requests exactly that subset. So while the project sits in **Testing**: any Google account can
sign in, there is no unverified-app warning, there is no 100-user cap in force, and the 7-day
refresh-token expiry never applies. Adding a test user buys nothing and costs something permanent —
*"a test user consumes a project's test user quota once added to the project"*, and removing them
does not return the slot.

The only thing Testing costs you is that the consent sheet shows the raw client identity rather than
"Oltre" and a logo. Buying that is step 43–44.

**Check this rather than trust it, because being wrong here is expensive.** If the exemption did not
apply, every account that is not on the allow-list would be refused, and you would find out from a
tester rather than from the console. The check costs a minute and happens at step 45, when there is
something to sign in to: sign in with a **second Google account that is not on any list**. If it
works with no "Google hasn't verified this app" interstitial, the exemption is real and no test user
is ever needed. If it does not, add that account under **Test users** on the Audience page and open
an issue on the epic — the guide is wrong and the next reader should not repeat it.

## 26. Read this before creating clients

> **A client secret is shown exactly once, on the creation dialog.** Since June 2025 the console
> shows only the last four characters afterwards. **Download the JSON before closing each dialog.**
> A client may hold at most two secrets, so recovery is rotation, not retrieval.
>
> Two of the five clients issue a secret: **Web** and **Desktop**. Android and iOS clients have no
> secret at all and never will — if a tutorial tells you to put one in the app, it is describing the
> Web client and the advice is wrong.

All five are created at <https://console.cloud.google.com/auth/clients> → **Create client**.

## 27. Client 1 of 5 — Web application (the audience)

- **Application type:** Web application
- **Name:** `Oltre server`
- **Authorized JavaScript origins:** leave empty
- **Authorized redirect URIs:** leave empty

This client exists only to be the server-side audience. It needs no redirect URI because the server
never runs a Google web redirect flow — Android and iOS use the platform SDKs, desktop uses loopback.

Download the JSON on the dialog, then:

```
mv ~/Downloads/client_secret_*.json ~/.oltre/signin/google-web-client.json
chmod 600 ~/.oltre/signin/google-web-client.json
```

The client ID looks like `123456789012-abc123def456ghi789jkl012mno345pq.apps.googleusercontent.com`;
the secret begins `GOCSPX-`. Your design (verify the ID token against JWKS) never uses that secret —
store it anyway rather than lose it.

## 28. Client 2 of 5 — iOS

- **Application type:** iOS
- **Name:** `Oltre iOS`
- **Bundle ID:** `dev.fardavide.oltre`
- **Team ID:** `A7Q83J6LR4`
- **App Store ID:** leave empty — a TestFlight-only build has no numeric App Store id yet, and
  nothing breaks meanwhile.

Team ID is optional in the console but required if App Check / App Attest is ever turned on.

## 29. Client 3 of 5 — Android, release key

Step 5's off-machine backup must be done before this one.

- **Application type:** Android
- **Name:** `Oltre Android (release)`
- **Package name:** `dev.fardavide.oltre`
- **SHA-1 certificate fingerprint:** `24:AA:53:1D:64:20:C6:B9:65:86:FD:53:E6:B7:1A:E7:0C:3E:DB:98`

SHA-1 only — the console does not accept the SHA-256 that `./gradlew signingReport` also prints.
Play App Signing is not involved: you ship a self-signed APK on a GitHub Release, so the fingerprint
Google sees at runtime is this keystore's own.

## 30. Client 4 of 5 — Android, debug key

Yes, a second client. An Android OAuth client holds exactly one package + SHA-1 pair.

- **Application type:** Android
- **Name:** `Oltre Android (debug)`
- **Package name:** `dev.fardavide.oltre`
- **SHA-1 certificate fingerprint:** `39:1D:08:0B:9F:4A:80:87:0C:CA:4E:9A:5F:34:57:81:E4:3B:A1:E1`

Skip it and sign-in works in release and fails on every `./gradlew installDebug` build with
`DEVELOPER_ERROR` / status 10 — an error that reads like a code bug for an afternoon.

If the console answers *"an OAuth2 client already exists for this package name and SHA-1"*, some
other Google Cloud or Firebase project of yours has claimed `dev.fardavide.oltre`; the pair is
globally unique and you must delete it there first.

## 31. Client 5 of 5 — Desktop app

- **Application type:** Desktop app
- **Name:** `Oltre desktop`

There are no further fields — the console asks for nothing else, and deliberately has no redirect-URI
box: loopback is implicit for this client type and the port is chosen at runtime.

Download the JSON on the dialog:

```
mv ~/Downloads/client_secret_*.json ~/.oltre/signin/google-desktop-client.json
chmod 600 ~/.oltre/signin/google-desktop-client.json
```

A secret **is** issued and it will ship inside the desktop binary. Google's own documentation says
native apps cannot keep secrets and treats that as expected — the security comes from PKCE. Treat a
leak of this one as a non-event, and never reuse the Web client's secret here.

## 32. Record the Google identifiers

```
touch ~/.oltre/signin/google.env
chmod 600 ~/.oltre/signin/google.env
open -e ~/.oltre/signin/google.env
```

```
OLTRE_GOOGLE_WEB_CLIENT_ID=<...>.apps.googleusercontent.com
OLTRE_GOOGLE_IOS_CLIENT_ID=<...>.apps.googleusercontent.com
OLTRE_GOOGLE_ANDROID_RELEASE_CLIENT_ID=<...>.apps.googleusercontent.com
OLTRE_GOOGLE_ANDROID_DEBUG_CLIENT_ID=<...>.apps.googleusercontent.com
OLTRE_GOOGLE_DESKTOP_CLIENT_ID=<...>.apps.googleusercontent.com
```

All five are public identifiers. They are recorded here because the failure mode is not leaking them,
it is losing track of which of five is which.

## 33. Generate the server's own session-signing key

Nobody hands you this one. It is the value that makes Oltre's own session tokens forgeable if it
leaks, and it is the only credential here you can rotate freely — the cost of rotation is that every
player signs in again.

```
openssl rand -base64 64 | tr -d '\n' > ~/.oltre/signin/session-jwt.key
chmod 600 ~/.oltre/signin/session-jwt.key
```

Do not reuse the keystore password. Do not commit a default value to source as a "dev fallback" — a
dev fallback that ships is a server with a publicly known signing key.

---

# Part 5 — Store, back up, and prove the backup

## 34. Write the inventory, mirror, and test the restore

Create `~/.oltre/signin/README.md` with: what each file is, which are secret, the p8 public-key
SHA-256 from step 19, the Key ID, the Services ID, and the date each was created. Without it a
restored p8 is an anonymous 250-byte file and nobody knows which Key ID it belongs to — and the Key
ID is unrecoverable from the key material.

Second local copy, matching the existing "Oltre Android signing" convention in `~/Documents/Dev`:

```
mkdir -p -m 700 ~/Documents/Dev/"Oltre sign-in"
cp -p ~/.oltre/signin/* ~/Documents/Dev/"Oltre sign-in"/
```

Off-machine copy, into the image mounted at step 5:

```
cp -p ~/.oltre/signin/* /Volumes/Oltre\ secrets/
hdiutil detach /Volumes/Oltre\ secrets
```

Then **test the restore rather than assuming it**:

```
hdiutil attach ~/Library/Mobile\ Documents/com~apple~CloudDocs/oltre-secrets.dmg
openssl pkey -in /Volumes/Oltre\ secrets/AuthKey_<KEYID>.p8 -pubout -outform DER | openssl dgst -sha256
hdiutil detach /Volumes/Oltre\ secrets
```

The digest must equal step 19's. This is the only step that converts "I copied it somewhere" into
"losing the laptop does not lose it".

**Nothing goes into GitHub secrets today.** CI builds the APK and runs the tests, and needs neither
the p8 nor any Google value to do that; the four existing `ANDROID_*` secrets remain the complete
list. A GitHub secret is write-only, so it is not a backup — every extra copy of the p8 is another
place it can leak from with no compensating recoverability. When the identity slice actually reads
these at build time, that is the moment for:

```
gh secret set GOOGLE_OAUTH_WEB_CLIENT_ID --repo fardavide/oltre --body "<web-client-id>"
gh secret set GOOGLE_OAUTH_IOS_CLIENT_ID --repo fardavide/oltre --body "<ios-client-id>"
gh secret set GOOGLE_OAUTH_DESKTOP_CLIENT_ID --repo fardavide/oltre --body "<desktop-client-id>"
gh secret set GOOGLE_OAUTH_DESKTOP_CLIENT_SECRET --repo fardavide/oltre --body "GOCSPX-..."
```

---

# Part 6 — One deployment decision worth locking today

## 35. Pick the Cloud Run region now, because two things freeze around it

Cloud Run **custom domain mapping is a Preview feature**, documented by Google as having latency
issues and as not recommended for production, and among EU regions it is supported **only in
`europe-north1`, `europe-west1` and `europe-west4`**. The alternatives Google names are a global
external Application Load Balancer — which carries a standing monthly cost and so breaks the
zero-euro target — or Firebase Hosting, which is low cost and could also serve static content.

**Recommendation: `europe-west1`.** It is on the supported list, it is EU, and it is close to the
Frankfurt-ish Neon regions.

This matters today because Secret Manager replication locations **cannot be changed after creation**.
If you want the groundwork done now (needs the `gcloud` CLI; none of it needs Cloud Run to exist):

```
gcloud services enable secretmanager.googleapis.com run.googleapis.com --project=oltre-prod
gcloud secrets create oltre-apple-signin-p8 --project=oltre-prod --replication-policy=user-managed --locations=europe-west1 --data-file=$HOME/.oltre/signin/AuthKey_<KEYID>.p8
gcloud secrets create oltre-session-jwt-key --project=oltre-prod --replication-policy=user-managed --locations=europe-west1 --data-file=$HOME/.oltre/signin/session-jwt.key
gcloud iam service-accounts create oltre-server --project=oltre-prod --display-name="Oltre server runtime"
```

Then grant read access per-secret rather than project-wide, so "the server can read two secrets" does
not become "the server can read everything":

```
gcloud secrets add-iam-policy-binding oltre-apple-signin-p8 --project=oltre-prod --member=serviceAccount:oltre-server@oltre-prod.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
gcloud secrets add-iam-policy-binding oltre-session-jwt-key --project=oltre-prod --member=serviceAccount:oltre-server@oltre-prod.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
```

Secret Manager, unlike a GitHub secret, **is readable back** — so once step 34's restore test has
passed, this doubles as a genuine third copy.

Create `oltre-google-web-client-secret` and `oltre-database-url` later: an empty secret version is
worse than a missing one, because the instance starts and then fails at the first query.

---

# Part 7 — What genuinely waits, and on what

## 36. Cloud Run domain mapping for `api.oltre.space`
**Waits on:** a deployed Cloud Run service (nothing in `server/` produces a container image yet).
When it exists, create the mapping in the region chosen at step 35; Google hands you the DNS records
to add at Namecheap. Search Console ownership of `oltre.space` is already done (step 8), which is the
part that could have blocked you.

## 37. Google Secret Manager entries for the DB and the Web client secret, and the Cloud Run deploy
**Waits on:** a Neon connection string and a container image. When deploying, put every secret in
**one** `--set-secrets` flag — it is a dict flag and passing it twice replaces rather than merges,
silently dropping the earlier entries. Mount the p8 as a **file** (`/secrets/apple/signin.p8`), not
an env var: PEM newlines, and a mounted secret is re-read on every access whereas an env-var secret
is resolved once before the instance starts and never changes for that instance's life.

## 38. Add a startup self-check to the server
**Waits on:** the auth code existing. Load the p8 and sign a throwaway ES256 JWT *before* the process
binds the port. Cloud Run keeps the previous revision serving if a new one fails to become ready — so
this converts "the key is broken" from an outage into a failed deploy. And unlike `PORT`, none of
these values may have a default: a defaulted signing key is a silent catastrophe.

## 39. Publish `https://oltre.space/privacy` and `https://oltre.space/terms`
**Waits on:** the policy text, which must describe what the shipped build actually does — so it
cannot be finalised before the build exists. Required for App Store submission and for Google brand
verification; **not** required for sign-in to work, and not for TestFlight internal distribution.

Commit `privacy/index.html` and `terms/index.html` to `gh-pages`. The skeleton below is the shape,
not legal advice — it is what this app actually does, and the one judgement you have to supply is the
retention period:

> **What is collected.** When you sign in with Apple or Google, Oltre receives an identifier for your
> account from that provider and stores it. It is a pseudonymous string; it is not your name or your
> email address, and it identifies you only within Oltre.
>
> **What is not collected.** Oltre does not store your email address, your name, or your provider
> profile. It contains no analytics, no advertising and no third-party tracking. Nothing you do in
> the game is shared with anyone.
>
> **What else is stored.** The state of your colony — buildings, research, fleets, the event log —
> and the instant it was last updated.
>
> **Where it is stored, and by whom.** On servers in the European Union, using Google Cloud Run
> (Google Ireland Limited) and Neon (Postgres). The controller is Davide Farella; contact
> `<CONTACT_ADDRESS>`.
>
> **How long.** Until you delete your account, or after `<N>` months of inactivity.
>
> **Deleting it.** Settings → Delete account, inside the app. Deletion removes the account record and
> the colony, and cannot be undone.
>
> **Your rights.** Under the GDPR you may request access to, correction of, or erasure of your data,
> and may complain to your national data protection authority. Contact `<CONTACT_ADDRESS>`.

Two things worth deciding rather than copying. **The contact address becomes public** on a page
linked from the App Store — a dedicated alias is worth more than a personal inbox. And **the
retention period has to be a number you will actually honour**: if nothing deletes inactive accounts,
say "until you delete your account" and nothing more, rather than promising a sweep that does not
exist.

## 40. The iOS entitlement — the one the compiler cannot catch
**Waits on:** the identity slice. `iosApp/project.yml` declares no entitlements block at all today,
so `CODE_SIGN_ENTITLEMENTS` is unset and the archive ships without `com.apple.developer.applesignin`
no matter what the App ID says. The app builds, launches and looks fine, and the Sign in with Apple
button fails at runtime with `ASAuthorizationError 1000` — a dead control that nothing in the build
catches. Under `targets.Oltre`, sibling of `settings`:

```yaml
    entitlements:
      path: iosApp/Oltre.entitlements
      properties:
        com.apple.developer.applesignin: [Default]
```

Then `xcodegen generate` in `iosApp/`, and commit the project **and** its shared scheme. Never
hand-edit `project.pbxproj`. `Default` is the only value for normal operation.

## 41. The iOS URL scheme for Google
**Waits on:** the same slice. Add the reversed iOS client ID as a `CFBundleURLScheme` in
`iosApp/project.yml`: strip `.apps.googleusercontent.com` from `OLTRE_GOOGLE_IOS_CLIENT_ID` and
prefix `com.googleusercontent.apps.`. Miss it and the browser completes the Google flow and never
returns to the app — a hang with no error, which is again a dead control.

## 42. In-app account deletion
**Waits on:** the server owning an account record; there is nothing to delete server-side today. This
is a hard App Store gate (5.1.1(v)), not a TestFlight one. It must start in the app, "typically in
account settings"; a `mailto:` or support form is a rejection; deactivating is not deleting; and where
Sign in with Apple was used it must also revoke the token through Apple's REST API. **Note the
collision with invariant 2**: appending an `AccountDeleted` event does not satisfy this if the prior
events still hold the subject id. The log has to go.

## 43. The App Store Connect App Privacy answers
**Waits on:** a build that actually collects the identifier being in testers' hands — Apple expects
the published answers to describe the shipped version. Then: Identifiers → **User ID**, Linked to
user **Yes** (forced: the subject id is the account key and every save row hangs off it), Used for
tracking **No**, purpose **App Functionality**. The email inside the ID token needs no declaration
*while it is genuinely not retained* — log a raw ID token once in Cloud Run and that stops being
true and the published label becomes wrong. These answers can be changed at any time without
shipping an app update.

## 44. Google brand verification
**Waits on:** step 39's pages, plus filling the branding App domain block (authorized domain
`oltre.space` first, then the three URLs), plus **publishing** the app. Google's criterion is
explicit: verification applies once the app is External **and** Published. Automated review takes
minutes; a manual fallback is 2–3 business days. **A compliant result is valid for only 7 days** —
if you do not press *Publish branding* inside that window the status reverts to "Need to re-verify".
Cosmetic: without it the consent sheet shows the raw client identity instead of "Oltre" and a logo,
which to a player reads like a phishing page. Logo must be square, ≤120×120 px, under 1 MB.

## 45. The cross-platform `sub` check — the one nothing replaces
**Waits on:** a live server plus a TestFlight build and an APK. Sign in with the **same Apple
Account** from both and confirm the server sees an **identical `sub`** and creates one account, not
two. Apple's `sub` is stable per team and app group; because `dev.fardavide.oltre.signin` is
configured against primary App ID `dev.fardavide.oltre` they are one group and should agree — but
nothing in the portal confirms it. If they disagree, the Services ID is attached to the wrong primary
or the App ID was enabled as *grouped*. Catching that here means redoing step 12; catching it after
players have accounts means a migration.

(Google is different and simpler: Google's discovery document returns
`subject_types_supported: ["public"]`, so `sub` is a **global** Google account identifier, identical
across every client in every Cloud project. One account works on phone and desktop for that reason,
and moving Cloud projects would *not* fork accounts. The flip side: it identifies the same human to
every app using Google Sign-In, so never put it in a URL or a log. Store it as `VARCHAR(255)`.)

---

# What you do **not** need to do

Each of these appears in guides and none of it applies here.

- **`apple-developer-domain-association.txt`.** Apple's current help page says outright you do not
  need to upload a file to your server to register domains and subdomains. Listing `api.oltre.space`
  in the Services ID configuration is the whole of it. Contingency in step 16 if the portal
  disagrees.
- **Apple's Private Email Relay Service, and any SPF/DKIM work.** That service exists so you can
  *send* mail to a user's `@privaterelay.appleid.com` address. Your server sends no email and stores
  only `sub`. Skipping it is the step.
- **Requesting Apple's `name` or `email` scopes.** Omit `scope` from the authorize request entirely.
  Those scopes are returned exactly once, on a user's first-ever authorisation, and never again — so
  a bug that drops them is unrecoverable for that user. Omitting them sidesteps that *and* removes
  any need for relay configuration.
- **Google OAuth app verification review.** Mandatory only for sensitive or restricted scopes. Your
  three are all non-sensitive.
- **Google test users.** See step 25 — they buy nothing for this scope set and burn a permanent slot.
- **Publishing the Google app**, unless you want brand verification. Nothing functional changes.
- **A second Apple key.** One key signs client secrets for both `client_id`s because the Services ID
  is configured against the primary App ID.
- **Regenerating provisioning profiles by hand** after step 12. Automatic signing and Xcode Cloud
  handle it.
- **Anything Play Console.** No listing exists, so Play Data Safety and Play's mandatory web account
  deletion URL do not apply. They become obligations only if Android distribution moves off GitHub
  Releases — and if it ever does, choose *"Provide a copy of your app signing key"* in Play App
  Signing so SHA-1 `24:AA:53:…:98` survives and client 3 keeps working. Letting Google generate a new
  key instead means every Play install fails with `DEVELOPER_ERROR` until a third Android OAuth
  client is registered from Play Console → Protected with Play → Play Store distribution → Play app
  signing.
- **Adding anything to GitHub secrets today.** See step 34.

---

# Irreversible and destructive — the list

1. **The `.p8` downloads once.** No recovery. Revoke-and-replace is the only path.
2. **Enabling the capability invalidates existing provisioning profiles** for `dev.fardavide.oltre`.
   Costless here, but only because signing is automatic — and only if the portal step precedes the
   entitlement reaching `main`.
3. **"Enable as a primary App ID" versus grouping** is close to one-way.
4. **Binding Google Sign-In to the release keystore** upgrades that key from "needed for updates" to
   "needed for anyone to sign in". Step 5 exists for this.
5. **Google client secrets are shown once**, last four characters thereafter, two per client.
6. **Secret Manager replication locations are fixed at creation.**
7. **A Google test user permanently consumes a slot.**
8. **OAuth clients cannot move between Cloud projects**, and an Android client ID is bound to
   package + SHA-1, so re-issuing means a new app release.
9. **A package + SHA-1 pair is globally unique** across all Google Cloud and Firebase projects.
10. **Google auto-deletes OAuth clients** with no credential/token request *and* no configuration
    change for six months — email warning 30 days before, restorable for 30 days after, permanent
    thereafter. Provisioning all five today is fine; touching the config counts as activity, but do
    not let the identity slice slip a year.

---

# The expiry clocks

**Apple client secret (the one people get wrong).** It is a JWT you sign with the p8, and Apple's
ceiling is `exp ≤ iat + 15777000` seconds — six months. Requesting more comes back as
`invalid_client`.

*When the clock starts:* only when the server actually mints one, which happens only when it calls
`POST https://appleid.apple.com/auth/token` or `/auth/revoke`. The plan of record — fetch Apple's
JWKS and verify the ID token server-side — never exchanges an authorisation code, so on the
native-iOS path **no secret is minted and no clock starts at all**. The p8 is still needed for
`/auth/revoke` at account deletion, and for the Android and desktop web flows, which do exchange a
code.

*The three options, and why only one is safe:*

- **A — mint in-process, per request, `exp = now + 300`.** Recommended. No expiry event exists, so
  none can be missed; failure surfaces at deploy via step 38's self-check while the old revision
  keeps serving. The p8 is already on the server, so a long-lived secret buys nothing.
- **B — a scheduled GitHub Action writing a new Secret Manager version.** Fails silently and all at
  once. GitHub disables cron workflows after 60 days without new commits (and only *commits* reset
  that timer — not releases, tags, issues or merged PRs); scheduled-run failures notify only the last
  committer; and an env-var-mounted secret does not pick up a new version without a redeploy, so a
  job that "succeeded" can have changed nothing.
- **C — a calendar reminder.** The worst: one missed notification takes sign-in down for every user
  simultaneously, with no signal until they complain. Use C only as an annual backstop for the key
  itself — *is it still valid, does the backup still restore* — never as the mechanism.

One more detail that bites: the secret's `sub` claim is **case-sensitive and must byte-match the
`client_id` for that particular flow** — `dev.fardavide.oltre` for an iOS-originated code,
`dev.fardavide.oltre.signin` for Android or desktop. One p8, two different JWTs. A single hard-coded
`sub` works on exactly one platform and returns `invalid_client` on the other.

**Everything else with a clock:**

| Thing | Clock |
|---|---|
| Apple `.p8` key | No expiry. Rotate by creating a second key (max two per primary App ID), cutting over, then revoking. |
| Apple authorisation code | Single-use, five minutes. A replayed code returns `invalid_grant`. |
| Google brand verification result | 7 days to press *Publish branding*, else re-verify. |
| Google OAuth client | Auto-deleted after 6 months of no use and no config change. |
| Google access token | 3600 s; irrelevant, you use the ID token. |
| Oltre session-JWT key | No expiry. Freely rotatable; cost is everyone signs in again. |

---

# The values, and what each looks like

| Value | Looks like | Secret? | Where it lives |
|---|---|---|---|
| Apple Team ID | `A7Q83J6LR4` | No | Already in `iosApp/project.yml`; `apple.env`; JWT `iss` |
| Apple Key ID | 10 chars, e.g. `ABC123DEFG` | No | `apple.env`; JWT `kid`; also in the p8 filename |
| Apple `.p8` | `AuthKey_<KEYID>.p8`, ~250 B, `-----BEGIN PRIVATE KEY-----` | **Yes, one-shot** | `~/.oltre/signin/` 0600, `~/Documents/Dev/Oltre sign-in/`, the DMG, Secret Manager `oltre-apple-signin-p8`, mounted at `/secrets/apple/signin.p8` |
| p8 public-key SHA-256 | `SHA2-256(stdin)= <hex>` | No | `README.md` in both copies — the thing that verifies a restore |
| Apple Services ID | `dev.fardavide.oltre.signin` | No | `apple.env`; the `client_id` in every Android/desktop browser URL |
| iOS bundle identifier | `dev.fardavide.oltre` | No | Repo; the `client_id` for the native iOS flow |
| Apple client secret JWT | ES256, `exp = now + 300` | Derived, never stored | In-memory only, in the body of the POST to `/auth/token` |
| Google Web client ID | `NNNNNNNNNNNN-xxxx.apps.googleusercontent.com` | No | `google.env`; `setServerClientId` on Android; `serverClientID` on iOS; audience #1 |
| Google Web client secret | `GOCSPX-…` | **Yes, one-shot** | `google-web-client.json` + backups. Unused by the JWKS design |
| Google iOS client ID | same shape | No | `GIDConfiguration(clientID:)` |
| Reversed iOS client ID | `com.googleusercontent.apps.NNNNNNNNNNNN-xxxx` | No | `iosApp/project.yml` `CFBundleURLSchemes` |
| Google Android release client ID | same shape | No | Never named in code — the client binds package + fingerprint |
| Google Android debug client ID | same shape | No | Same |
| Google Desktop client ID | same shape | No | Embedded in the desktop binary; audience #2 |
| Google Desktop client secret | `GOCSPX-…` | Nominally, in practice public | Ships in the binary; PKCE is the real protection |
| Session-JWT signing key | 64 random bytes, base64, one line | **Yes** | `session-jwt.key` + backups + Secret Manager `oltre-session-jwt-key` |
| Neon connection string | `postgres://…` with password inline | **Yes** | Secret Manager `oltre-database-url` only, when it exists |

---

# The four facts the client and server code must be built on

Not steps — but they are decided by what you provision above, and getting any of them wrong produces
an error with no useful message.

**1. Which `client_id` each platform presents to Apple.**

```
iOS      -> dev.fardavide.oltre          (bundle identifier; native ASAuthorizationAppleIDProvider)
Android  -> dev.fardavide.oltre.signin   (Services ID; web flow in a Custom Tab)
Desktop  -> dev.fardavide.oltre.signin   (Services ID; system browser, identical to Android)
```

Desktop is the trap. Even on macOS, a Compose Desktop build cannot present the bundle identifier —
`AuthenticationServices` is reachable only from a signed native bundle carrying the entitlement, and
the same binary ships to Windows and Linux. It is a web client. And because Apple rejects
`localhost` and IPs as return URLs, desktop Apple sign-in cannot use a loopback redirect the way
Google can: it has to bounce through `https://api.oltre.space/v1/auth/apple/callback` and hand the
result back to the app.

**2. The server accepts two audiences per provider, as an allow-list, never an equality check.**

```
Apple  aud in { dev.fardavide.oltre, dev.fardavide.oltre.signin }
Google aud in { WEB_CLIENT_ID, DESKTOP_CLIENT_ID }
Google iss in { "https://accounts.google.com", "accounts.google.com" }   // both spellings are issued
Apple  iss  = "https://appleid.apple.com"
```

Android and iOS both yield `aud = WEB_CLIENT_ID` (with `azp` carrying the platform client ID), which
is precisely what `setServerClientId` / `serverClientID` are for. Omit `serverClientID` on iOS and
`aud` silently becomes the iOS client ID and every iOS login is rejected by a server that looks
correct.

**3. Two signing algorithms in one integration.** Apple signs its ID token with **RS256**; your Apple
client secret is signed **ES256**. Pin each verifier to its own algorithm explicitly and never read
`alg` from an incoming token header. Both providers rotate their JWKS and return several keys at
once, so select by the token header's `kid` and re-fetch on an unknown one. Cache the JWKS; and do
not call Google's `tokeninfo` endpoint in production — Google flags it as debug-only.

```
Apple  JWKS: https://appleid.apple.com/auth/keys
Google JWKS: https://www.googleapis.com/oauth2/v3/certs  (discover via /.well-known/openid-configuration)
```

**4. The desktop Google flow needs the client secret, and the system browser.** Google marks
`client_secret` "Optional" in the installed-app token exchange for exactly one reason — *"The client
secret is not applicable to requests from clients registered as Android, iOS, or Chrome
applications"* — and **Desktop app is not on that list**. PKCE does not substitute for it. The POST
to `https://oauth2.googleapis.com/token` carries `client_id`, `client_secret`, `code`,
`code_verifier`, `grant_type=authorization_code`, `redirect_uri=http://127.0.0.1:PORT`. The
out-of-band flow (`urn:ietf:wg:oauth:2.0:oob`) is **removed**, not deprecated, and directing a Google
OAuth request into an embedded webview returns `disallowed_useragent` — shell out to the real system
browser.

And on Android, `GetGoogleIdOption` with `setFilterByAuthorizedAccounts(true)` throws
`NoCredentialException` for a user who has never consented. Catch it and retry with `false`, or use
`GetSignInWithGoogleOption` for the explicit button. Without that fallback the first tap on a fresh
device does nothing visible — which is the exact defect this project forbids shipping.

---

# Feedback

This document is meant to be corrected. When something is wrong, missing, or assumes a step you
could not follow, say so and it gets fixed here — including the parts that came from Apple's and
Google's own documentation, which is stale often enough that a portal disagreeing with this file is
worth recording rather than working around silently.

Things most likely to be wrong, in order:

1. **Portal wording and button placement.** Apple and Google both reorganise. The paths are current
   as of 2026-08-25; the shape of what you are doing outlasts the labels.
2. **Step 25, no test users.** Verified from Google's documentation, doubted by one reviewer, and
   checkable at step 45. See the note there.
3. **Step 16, the domain association file.** Apple's help page says it is not needed; many
   third-party guides disagree. If the portal asks for it, that is a real finding.
