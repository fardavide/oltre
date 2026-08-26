# Identity provisioning — Apple and Google, step by step

**Status:** ready to follow. Written 2026-08-25 for issue #110 under epic #106.

A tutorial, not a summary. Follow it and tell me where it is wrong, vague, or missing a step —
corrections go into this file, so the next person to do this reads the fixed version rather than a
chat thread. Every project value was re-derived on 2026-08-25 from the repository and this machine;
provider claims come from Apple's and Google's own documentation and were then attacked by
reviewers whose job was to refute them.

---

Two portals, one registrar, one local secret store. Every value below is the real one for this
project. The only placeholders are values Apple and Google generate for you; those are written
`<LIKE_THIS>` and described so you can tell you have the right thing.

**There is no end-to-end proof available today.** The only real verification is step 53 — sign in
from both platforms and confirm the server sees one account — and that waits on a deployed server
plus two builds. Everything before it verifies that *the consoles accepted your input*, which is a
weaker claim than it feels like at the time. That is a reason to record what you did (steps 37 and
40), not a reason to skip it.

About 100 minutes end to end, comfortably split into two sittings. **Steps 1–42 can all be finished
today.** Steps 43–53 genuinely wait, and each says on what.

Four things to read before clicking anything: step 4 (the keystore just changed job), step 13 (the
capability invalidates provisioning profiles, and the first thing that exercises that is a publish),
step 17 (the p8 downloads once), step 29 (client secrets are shown once).

## Who does what

**Anything at a command line is mine — ask and I run it.** Your half is the browser: two developer
portals, a registrar, Search Console, and GitHub's settings pages. Those need your credentials and
your eyes, and nothing else here does.

| Yours, in a browser | Mine, at a command line |
|---|---|
| 6, 7, 8, 12–18, 22–34, 39, 42 (`gcloud auth login` and billing) | 1, 2, 3, 9, 10, 11, 19–21, 35, 36, 37, 38, 40, 41 |

For the steps where a portal hands you a value — a Key ID, five client IDs, a downloaded file — read
it out and I will place it, `chmod` it, verify it and record it. You should never have to type
`openssl` or `hdiutil`.

**Already done, before you start:**

- **1** — `.gitignore` deliberately left alone; the reasoning is recorded in the step.
- **2** — `~/Documents/Keys/Oltre/identity/` created at `0700`, with its README.
- **3** — release fingerprint re-derived and confirmed: `SHA1: 24:AA:53:…:DB:98` matches.
- **36** — `session-jwt.key` generated, 88 base64 characters, `0600`.
- **9, 10, 11** — `gh-pages` pushed, Pages pointed at `oltre.space`, certificate issued, Enforce
  HTTPS on. `https://oltre.space/` serves the page and `www` redirects to the apex.
- **24** — Cloud project created; Google assigned the ID `oltre-506614`, recorded in `google.env`.
- **30–35** — all five OAuth clients created and filed at `0600`, `google.env` complete, nothing
  left in `~/Downloads`. Web `…lm195vos`, iOS `…rb18lnl9` (+ its reversed form for step 48), Desktop
  `…n76952ma`, and the two Android clients `…2il2q71g` / `…d7drjab4`.
- **42** — `gcloud` installed and on the login PATH; APIs enabled; `oltre-apple-signin-p8` and
  `oltre-session-jwt-key` created in `europe-west1`; service account `oltre-server` created and
  granted `secretAccessor` per-secret. The stored p8 round-trips to the recorded digest, so Secret
  Manager is a proven third copy. Budget alert set at €2/month, 50% and 100%.
- **40, 41** — decisions round written into `decisions.md`; the stale "no off-machine copy" note in
  memory closed, and a second memory added for the sign-in credentials. GitHub secrets confirmed
  unchanged at the four `ANDROID_*` — nothing identity-related belongs there yet.
- **37, 38** — inventory written, and both keys re-derived and matched: p8 digest
  `95d11ed5…28d13d9b`, keystore SHA-1 `24:AA:53:…:DB:98` and SHA-256 `48:56:AF:…:8D:DC`. iCloud
  `caught-up`, no placeholder files.
- **19, 20, 21** — key `AuthKey_77FXWGUFQY.p8` in place at `0600`, verified P-256 / prime256v1,
  public-key digest `95d11ed5…28d13d9b` recorded in the folder README, no copy left in `~/Downloads`,
  and `apple.env` written.

**The do-today half is finished — steps 1 to 42, all of them.** Everything from 43 onward waits on
the server existing, and each of those steps says what it waits for.

## If you only have 30 minutes

Do **1, 2, 3, 4, 5, 6a, 7, 8a, 12–21**, and stop.

That is the net, the off-machine backup, the one DNS row that unblocks Cloud Run later, and the
whole Apple half including the one-shot key. **Skip 9–11 and the four A records** — step 7 says why
you must not repoint the apex unless you finish 8b, 9 and 10 in the same sitting. Google's five
clients (24–36) is a separate half hour; nothing in it is urgent except that step 5 must come before
step 32.

## Before you start

- **The Apple Developer account for team `A7Q83J6LR4`, signed in as Account Holder or Admin.** Both
  the capability and the key creation require one of those two roles.
- **One Google account, chosen now**, owning both Search Console and the Cloud project. Cloud Run's
  domain mapping checks that the Cloud project is a verified owner of `oltre.space` in Search
  Console, and mismatched accounts is the usual way that fails. **Write it into `google.env` at step
  35.**

  **Which account: one for all your personal dev work, not one per project.** The previous Google
  developer account lapsed because it was welded to an address that stopped being used — and
  per-project accounts multiply exactly that, since an account nobody signs into is an account that
  quietly dies. Pick an address that outlives any single product. **Not one on `oltre.space`**: an
  identity named after a project dies with the project, which is the same mistake one layer up.
  Apple has already forced this shape on you — one team, `A7Q83J6LR4`, for everything — and matching
  it on Google costs nothing.

  **Per product, isolate with a Cloud project rather than an account.** That is where isolation
  actually lives: quotas, budget alerts, service accounts and OAuth clients are all per-project, a
  dead project deletes cleanly, and projects are free. `oltre-506614` here; the next thing gets its
  own.

  **How firmly this binds, since it is worth knowing before you commit.** The OAuth clients cannot
  move between *projects* — that part is genuinely one-way, and it is why step 24 says to create the
  project you will actually deploy from. The *account* binding is softer than it looks: a no-org
  project is owned through IAM, so another Google account can be granted `roles/owner` and the
  original removed. Treat the project as fixed and the login as recoverable-with-effort.
- **A billing decision, before step 42.** A fresh project has no billing account, and
  `gcloud services enable secretmanager.googleapis.com run.googleapis.com` fails on it with
  `FAILED_PRECONDITION: Billing must be enabled…`. Secret Manager is not free either: $0.06 per
  active secret version per location per month, $0.03 per 10,000 access operations, with a small
  free allotment. Two secrets is about €0.12/month — real, but three orders of magnitude below the
  load balancer that step 42 rejects on cost. Steps 1–41 need no billing at all.
- **The Namecheap account holding `oltre.space`.**
- **The GitHub account `fardavide`** — you will add an account-level domain verification.
- **A terminal** with `keytool` (ships with the JDK), `openssl`, `shasum` and `gh` already
  authenticated. **`gcloud` is not installed on this machine.** If you want Part 6 today:
  `brew install --cask google-cloud-sdk`, then `gcloud init`, `gcloud auth login`. Budget 15
  minutes for that alone, or skip Part 6.
- **Apple Passwords**, for one passphrase — see step 5. It syncs to the iPhone, which is what makes
  it reachable when the Mac is not.

A note on `openssl`, because one digest in this guide is load-bearing. `/usr/bin/openssl` is
LibreSSL 3.3.6 and prints a bare hex string; Homebrew's OpenSSL 3.6.3 (first in `PATH` here) prints
`SHA2-256(stdin)= <hex>`. A restore may well be run on a machine without Homebrew, so this guide
pipes through `shasum -a 256`, which is identical on both.

---

# Part 1 — The net, before anything is downloaded

## 1. Nothing goes in `.gitignore` — and that is the decision, not an omission

An earlier draft of this document added `*.p8`, `*.p12`, `*.pem`, `*.mobileprovision`,
`client_secret*.json` and `.env*` to `.gitignore`. **They came back out.** Davide, 2026-08-25:
*"Do we need all those entries into gitignore, given we're not saving keys in the repo?"* Checked
rather than argued, and the answer is no:

- **None of those file types exists anywhere in this project.** Zero matches across the whole tree.
  They were guarding a path nothing travels.
- **The credentials live in `~/Documents/Keys/Oltre/identity/`** and arrive there from `~/Downloads`.
  Neither path is inside a checkout, so there is no ordinary way for one to be staged.
- **Secret scanning and push protection are both enabled** on `fardavide/oltre` — confirmed via the
  API. A `.p8` is PEM-wrapped and a Google client secret starts `GOCSPX-`; both are shapes GitHub
  recognises, so a push carrying one is blocked at the push rather than caught by a text file.

And the argument that is easy to get backwards: **an ignored secret is an invisible secret.** A
`.p8` that lands in the working tree while ignored produces a silent `git status` and sits there —
copied into a tarball, an artifact, a container build. Unignored, it shows up red immediately *and*
push protection still stops it leaving. For a file that must never be in a checkout at all, being
loud is worth more than being hidden.

The existing `*.jks` / `*.keystore` / `keystore.properties` lines stay, and the difference is
instructive: those cover a file that legitimately *transits* CI — the release workflow decodes the
keystore from a secret into the runner's workspace. Nothing in this document does that.

**If that ever changes, add the line then, with a comment naming the file that made it necessary.**
A `.gitignore` entry with no corresponding file is a guess, and guesses accumulate.

**What actually protects these credentials** is that they are never in the repository, that push
protection is on, and that a leak is answered by *revoking*, not by rewriting history — GitHub keeps
unreachable commits reachable by SHA, and forks keep their own copies. Revoke first, clean second.

## 2. The credential folder

**Done** — `~/Documents/Keys/Oltre/identity/`, `0700`, with a README describing every file it will
hold.

```
~/Documents/Keys/
  README.md                     what this tree is, and what does not belong in it
  Oltre/
    README.md                   the two kinds of "sign", and how they became coupled
    android-signing/            the APK code-signing key (moved here 2026-08-25)
    identity/                   the sign-in credentials — this document fills it
```

**Both folders moved out of `~/Documents/Dev/` on 2026-08-25**, which held them alongside IDE
settings, code-style XML and a spell-check dictionary. Davide: *"let's not throw random shit in a
folder where we have other stuff."* `Dev/` is dev settings; keys are not settings.

Personal credentials stay in `~/Documents/Security codes/` — password-manager emergency kits, VPN.
That split is the 2026-08-09 call and it is why `Keys/` is a separate tree rather than a folder
inside it.

`~/.oltre/` keeps its one job: the keystore's working copy, which local signing reads. Nothing in
this document adds to it.

## 3. Confirm the release fingerprint has not drifted

Before pasting it into Google's console, re-derive it.

```
pbcopy < ~/.oltre/keystore-password
```

```
keytool -list -v -alias oltre -keystore ~/.oltre/oltre-release.keystore
```

`keytool` prompts for the store password — paste it. (It is not passed as `-storepass` on purpose:
that would put it in `~/.zsh_history` and in the process table.)

Expect `SHA1: 24:AA:53:1D:64:20:C6:B9:65:86:FD:53:E6:B7:1A:E7:0C:3E:DB:98` and
`SHA256: 48:56:AF:68:C7:5D:02:E9:6C:51:70:5E:44:48:23:33:FE:5E:1F:A1:25:5F:24:38:2D:1E:23:AE:EF:B1:8D:DC`.
If either differs, **stop** — the APKs on your GitHub Releases are signed by a different key than
you think, and everything downstream of step 32 would be wrong.

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

## 5. Nothing to do — the backup already exists

**An earlier version of this document had you build an encrypted disk image here and move it into
iCloud Drive. That step is gone.** It existed to get the keys off this machine, and they already
are: `~/Documents` is synced to iCloud, so `~/Documents/Keys/` is off-machine the moment a file
lands in it.

The mistake is worth recording because it survived two weeks and cost real worry. The signing key's
README claimed since 2026-08-09 that `~/Documents` was *not* synced, reasoning that the `Documents`
entry inside iCloud Drive is a symlink out to the local folder and iCloud does not follow symlinks.
**That symlink is what macOS creates when Desktop & Documents Folders sync is switched on.** It reads
as evidence against and is evidence for. Verified 2026-08-25:
`defaults read com.apple.finder FXICloudDriveDocuments` returns `1`, and `brctl status` shows an
active container synced minutes earlier.

So the image bought nothing, and cost a passphrase that itself needed somewhere to live — a backup
whose key is harder to keep than the thing it protects.

**Two things that are still true**, and are not solved by any of this:

- **There is no Time Machine destination on this Mac.** iCloud is the only copy that is not on this
  disk. Worth fixing, and it is a bigger fix than this document.
- **iCloud is not end-to-end encrypted unless Advanced Data Protection is on for the Apple account.**
  Otherwise Apple holds keys that can decrypt what it stores. If the goal becomes making the `.p8`
  opaque to Apple rather than merely surviving a dead SSD, **turn on Advanced Data Protection** —
  that is the real answer, and a disk image inside iCloud was never it.

What step 4 says still stands: the moment Google Sign-In is bound to the release fingerprint, the
signing key stops being about updates and starts being about sign-in. It is backed up. It is one
backup.

---

# Part 2 — DNS and the two hostnames

Do the DNS before the portals: propagation and GitHub's certificate issuance run in the background
while you click.

`oltre.space` is at Namecheap on **Namecheap BasicDNS** (`dns1.registrar-servers.com`,
`dns2.registrar-servers.com` — confirmed today). The apex resolves to the Namecheap parking IP
`162.255.119.165`, `www` is a CNAME to `parkingpage.namecheap.com`, and `api.oltre.space` does not
resolve at all. Records are edited at **Namecheap Dashboard → Domain List → Manage (oltre.space) →
Advanced DNS**. Leave the nameservers alone — Advanced DNS only applies while BasicDNS is selected.

## 6. Collect both verification tokens first

Two portals hand you a TXT value. Get both before touching Namecheap so step 7 is one visit.

**6a — Google Search Console** (required for Cloud Run's domain mapping later, and the only DNS work
that matters if you are on the 30-minute path). Go to
<https://search.google.com/search-console>, signed in as **the Google account that will own the
Cloud project**.

- **Add property** → the left-hand **Domain** box → type `oltre.space` → **Continue**.
- Google shows a TXT value of the form `google-site-verification=` followed by a 43-character
  base64url token. **Copy it; do not retype it and do not count the characters** — Search Console
  says only "your record should match exactly the verification record values given to you". Leave
  the dialog open.

The Domain property covers `api.oltre.space` as well, which is exactly what Cloud Run's domain
mapping needs. It is one of the very few deployment-adjacent things not blocked today.

**6b — GitHub Pages domain verification.** Skip this if you are skipping the site today; if you are
adding the A records, this is not optional (see step 7). Go to
<https://github.com/settings/pages> → **Add a domain** → `oltre.space` → GitHub shows a
TXT record named `_github-pages-challenge-fardavide` with a token. Copy both.

GitHub is explicit about why: *"Domain takeovers can happen when you delete your repository, when
your billing plan is downgraded, or after any other change which unlinks the custom domain or
disables GitHub Pages while the domain remains configured for GitHub Pages and is not verified."*
Verification is per-account, not per-repo — after it, only repositories owned by `fardavide` can
publish to `oltre.space`.

## 7. The one Namecheap visit — hygiene, then records

**Namecheap → Domain List → Manage (oltre.space).**

**Do the registrar hygiene first, because it is the part nobody comes back for.** The Apple Return
URL is `https://api.oltre.space/v1/auth/apple/callback` permanently, and Apple delivers
authorisation codes there. Whoever controls the zone controls that host, and that is account
takeover for the whole player base rather than downtime.

- **Auto-Renew: ON.** `oltre.space` was registered 2026-08-25, so the deadline is 2027-08-25 with no
  in-product warning of any kind.
- **Registrar Lock: ON** (Namecheap calls it *Transfer Lock*, on the Domain tab).
- **2FA on the Namecheap account**, with the recovery codes stored where step 39 says.
- **Check the registrant email is one you actually read** — ICANN verification mail goes there, and
  an unverified registrant contact suspends the domain.

Then **Advanced DNS → Host Records**.

**If you are on the 30-minute path, add only the Search Console TXT row and stop here.** The A
records below repoint the apex at GitHub, and a custom domain pointed at GitHub Pages that is *not*
verified and *not* configured is exactly the takeover shape GitHub warns about. Either do 6b, 7, 8b,
9 and 10 in one sitting, or leave the parking records alone.

Doing the whole thing: **delete** the parking entries first (bin icon at the right of each row) —
the `A Record` for host `@` pointing at `162.255.119.165`, and the `CNAME Record` for host `www`
pointing at `parkingpage.namecheap.com`. Between that deletion and step 10 the apex serves a GitHub
404 rather than a parking page. That is expected, it is not you having broken the domain, and it
lasts as long as the DNS TTL plus however long you take over steps 9–10.

Then **Add New Record**:

| Type | Host | Value | TTL |
|---|---|---|---|
| TXT Record | `@` | `google-site-verification=<43-CHAR-TOKEN>` | Automatic |
| TXT Record | `_github-pages-challenge-fardavide` | `<TOKEN-FROM-STEP-6b>` | Automatic |
| A Record | `@` | `185.199.108.153` | Automatic |
| A Record | `@` | `185.199.109.153` | Automatic |
| A Record | `@` | `185.199.110.153` | Automatic |
| A Record | `@` | `185.199.111.153` | Automatic |
| CNAME Record | `www` | `fardavide.github.io` | Automatic |

**Enter the CNAME value without a trailing dot.** Namecheap normalises it; GitHub's own instruction
is that the record points at `<user>.github.io`, excluding the repository name. If Namecheap's
validator objects to the bare form, add the dot — either is accepted by DNS, and step 8 verifies
which one you got.

Namecheap's Advanced DNS saves per row: click the green tick at the right of each row as you finish
it. Some layouts also show a **Save all changes** button at the top of the Host Records block; if
you do not see one, the green ticks were the save.

Optional, thirty seconds — IPv6 for GitHub Pages. Four `AAAA Record` rows on host `@`:
`2606:50c0:8000::153`, `2606:50c0:8001::153`, `2606:50c0:8002::153`, `2606:50c0:8003::153`.

**Nothing for `api.oltre.space` yet.** Cloud Run hands you the exact records when the domain mapping
is created (step 43), and inventing them now would only have to be deleted. Note that Apple does not
resolve `api.oltre.space` when you register it at step 15 — that is precisely why no domain
association file is needed.

## 8. Verify both

**8a** — back in the still-open Search Console dialog, click **Verify**. If it fails, wait a few
minutes and press it again.

```
dig +short TXT oltre.space
```

**8b** — at <https://github.com/settings/pages>, click **Verify** on the domain you added.

```
dig +short TXT _github-pages-challenge-fardavide.oltre.space
```

## 9. Create the public site on a `gh-pages` branch

Use a dedicated orphan branch, not `main` and not `main`'s `/docs` folder. Two reasons: `/docs`
already contains `ui-mockup.html`, which would become publicly served; and **merging to `main`
publishes** — Xcode Cloud archives to TestFlight and the release workflow can cut a GitHub Release.
A typo fix in a privacy policy should not be able to ship a build. (Pushing an orphan branch fires
no CI: the workflow triggers only on push-to-`main` and `pull_request`.)

In a throwaway clone, so nothing touches your working checkout:

```
git clone https://github.com/fardavide/oltre.git /tmp/oltre-pages
```

```
git -C /tmp/oltre-pages switch --orphan gh-pages
```

```
touch /tmp/oltre-pages/.nojekyll
```

`.nojekyll` turns off Jekyll, so the files served are exactly the files committed and directories
beginning with a dot are published. That is what makes step 16's contingency host possible at all,
and it means `/privacy` and `/terms` must later be `privacy/index.html` and `terms/index.html`
rather than Markdown.

Write `/tmp/oltre-pages/index.html`. Here it is in full, so this is a paste and not a writing task:

```html
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Oltre</title>
    <style>
      :root { color-scheme: dark; }
      body {
        margin: 0; min-height: 100vh;
        display: grid; place-items: center;
        background: #05070f; color: #e6e8ef;
        font: 16px/1.6 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        text-align: center; padding: 2rem;
      }
      h1 { font-size: 2.5rem; margin: 0 0 0.5rem; letter-spacing: 0.02em; }
      p { margin: 0; max-width: 32rem; color: #9aa1b4; }
    </style>
  </head>
  <body>
    <main>
      <h1>Oltre</h1>
      <p>An asynchronous space colonisation game. In development.</p>
    </main>
  </body>
</html>
```

"Honest" means it excludes exactly two things: any claim the app is available, and anything shaped
like a privacy policy or terms page. **Do not put placeholder `/privacy` or `/terms` up.** A privacy
policy a player could read and that is not true is worse than a 404. Those land with the identity
release (step 46).

```
git -C /tmp/oltre-pages add -A
```

```
git -C /tmp/oltre-pages commit -m "Public site: home page for oltre.space"
```

```
git -C /tmp/oltre-pages push -u origin gh-pages
```

## 10. Point GitHub Pages at the apex

<https://github.com/fardavide/oltre/settings/pages>

- **Build and deployment → Source:** Deploy from a branch. **Branch:** `gh-pages`, folder `/ (root)`.
  Save.
- **Custom domain:** type `oltre.space` → **Save**. GitHub runs a DNS check and commits a `CNAME`
  file containing `oltre.space` to the branch.
- Wait for the certificate, then tick **Enforce HTTPS** when it stops being greyed out. GitHub says
  *"it can take up to 24 hours before this option is available"*.

**Two things met doing this for real, 2026-08-25.**

**Pages may already be enabled**, in which case the API answers `409 GitHub Pages is already
enabled` and the UI shows a site at `fardavide.github.io/oltre`. Not a problem — set the source and
the domain on the existing site rather than creating one.

**Setting the custom domain fails while Enforce HTTPS is on**, with a confusing
`404 The certificate does not exist yet`. It is a genuine ordering problem, not a mistyped record:
GitHub will not accept a domain it is expected to enforce HTTPS for before it has a certificate for
it, and it cannot get one until the domain is set. Set the domain *alone*, let the certificate
issue, then turn HTTPS enforcement back on:

```
gh api -X PUT repos/fardavide/oltre/pages -f cname=oltre.space
```

```
gh api -X PUT repos/fardavide/oltre/pages -F https_enforced=true
```

Check `protected_domain_state` comes back `verified` — that is step 6b's account-level verification
having landed, and it is what stops anyone else publishing to `oltre.space`.

## 11. Confirm — and know which failures are normal

```
curl -sI https://oltre.space/
```

`HTTP/2 200` is the finished state. Inside your window you may well get one of these instead, and
none of them means you mistyped a record:

| What you see | What it is |
|---|---|
| TLS handshake / certificate error | GitHub has not issued the certificate yet. Up to 24 hours. |
| `HTTP/2 404` | DNS is right, Pages has not finished its first build. Minutes. |
| Connection refused, or the Namecheap parking page | DNS has not propagated to your resolver yet. |
| `HTTP/2 200` from something that is not your page | Wrong: an A record is mistyped. Re-check step 7. |
| Plain `http://` answers `200` instead of `301` | Enforce HTTPS was just switched on and has not reached the edge. Minutes to hours; `https_enforced` in the API is already `true`. |

`https://www.oltre.space/` should redirect to the apex — GitHub creates that redirect automatically
when the apex is the configured custom domain.

---

# Part 3 — Apple

Everything in this part is doable today. The old assumption that the Services ID was blocked on a
server URL is wrong now that `api.oltre.space` is a settled, permanent hostname.

## 12. Enable Sign in with Apple on the App ID

<https://developer.apple.com/account/resources/identifiers/list> → **Identifiers** in the sidebar →
filter **App IDs** → **select the row whose Bundle ID is `dev.fardavide.oltre`, then click Edit**.

Apple's steps, verbatim: *"Select the App ID you want to update, then click Edit. Select the
corresponding checkboxes to enable the app capabilities you want to allow. Click Save. If a warning
dialog appears, click Confirm to finalize your changes."* There is an **Edit** button; do not go
hunting for a checkbox on the row itself.

- Tick **Sign in with Apple**.
- A **Configure** button appears beside it. Click it. The modal offers a choice Apple describes as
  *"whether this App ID should be enabled as a primary or grouped with an existing primary App
  ID"* — i.e. a pair reading roughly **"Enable as a primary App ID"** and **"Group with an existing
  primary App ID"**. Choose **primary**. Save the modal.
- **Save**, then **Confirm** in the warning dialog.

There is only one app on this team, so primary is unambiguously right, and the Services ID in step 14
must attach to a primary.

## 13. Read the dialog, then confirm — and then trigger an archive

> Apple's warning is real and says: *"Provisioning profiles that contain a modified App ID become
> invalid. You'll need to regenerate the provisioning profiles that use that App ID."* Also:
> *"Enabling a capability will affect provisioning profiles for all eligible platforms."*
>
> **Expect not to regenerate anything by hand.** `iosApp/project.yml` sets
> `CODE_SIGN_STYLE: Automatic` and Xcode Cloud uses cloud signing, so the next archive should mint a
> profile carrying the new entitlement. Locally, Xcode should regenerate the development profile on
> the next build; if it does not, untick and re-tick "Automatically manage signing" once. But note
> that this is an expectation, not a documented guarantee: Apple's *Provisioning with managed
> capabilities* page — the one that promises automatic inclusion — lists CarPlay, Multicast
> Networking and similar, and **Sign in with Apple is not on it**. If the first archive fails
> signing, regenerate the distribution profile by hand at
> <https://developer.apple.com/account/resources/profiles/list>; there is nothing subtle about the
> fix, only about noticing you need it.
>
> **So notice it on purpose.** Nothing on a pull request signs anything — CI's iOS job runs only
> `./gradlew :client:shell:linkDebugFrameworkIosSimulatorArm64`, which compiles the framework and
> never archives. Left alone, a regeneration failure surfaces as a broken Xcode Cloud archive on
> `main`, i.e. a failed TestFlight publish, discovered by whatever unrelated feature merges next.
> **Start an Xcode Cloud build by hand now, while you still remember you made this change.**
>
> **Order matters.** Do this portal step *before* the entitlement in step 47 reaches `main`, or the
> first archive after it fails signing.

## 14. Register the Services ID

<https://developer.apple.com/account/resources/identifiers/list> → the add button **(+)** on the top
left → **Services IDs** → **Continue**.

- **Description:** `Oltre Sign in with Apple`
- **Identifier:** `dev.fardavide.oltre.signin`

**Continue** → review → **Register**.

Apple documents no restriction on the description field, but the portal is known to reject some
special characters. Plain ASCII avoids finding out.

The identifier **cannot** be `dev.fardavide.oltre`: the portal answers *"An App ID with Identifier
'…' is not available. Please enter a different string."* because your own App ID already holds it.
(Apple does not document the namespace rule; the behaviour is consistent and the conclusion — pick a
different string — does not depend on knowing why.)

What this thing *is*: the OAuth `client_id` for every client that is not an Apple-native app talking
to `AuthenticationServices`. It is a web credential, not a second app.

## 15. Configure the Services ID

Click `dev.fardavide.oltre.signin` → tick **Sign in with Apple** → **Configure**.

- **Primary App ID:** `dev.fardavide.oltre`
- The modal has a section headed **Website URLs**. **If you do not see text inputs, click the plus
  icon next to Website URLs first** — the fields are behind it, and this is where people conclude
  the page is broken.
- **Domains and Subdomains:** `api.oltre.space`  *(no scheme, no trailing slash)*
- **Return URLs:** `https://api.oltre.space/v1/auth/apple/callback`

**Done** → **Continue** → **Save**.

Apple's own wording is that you *"provide your domains, subdomains, or return URLs as a
comma-delimited list"* and *"must provide at least one domain or subdomain"* — which matters the
first time you add a second URL. As an individual enrolee you may register **at most 10 website
URLs** in total (organisations get 100), so do not start a URL-per-environment habit.

Apple rejects `http`, `localhost`, bare IP addresses and any URL containing a fragment. Return URLs
must be absolute with scheme, host and path, and must later match the `redirect_uri` parameter
**byte for byte**. A mismatch fails the token exchange; do not diagnose it from the error code
alone — Apple documents `invalid_client` as being about the client identifier, and a `redirect_uri`
mismatch is normally reported as `invalid_grant`. Compare the two strings character by character
instead.

**Apple does not resolve the host here.** `api.oltre.space` returning nothing today is fine and is
the same fact as step 16's "expect not to need this".

## 16. If — and only if — the portal demands a domain association file

Apple's current help page states in as many words: *"You don't need to upload a file on your server
to complete the registration process for domains and subdomains."* Numerous third-party guides are
stale on this. **Expect not to need it.**

If the portal nevertheless demands
`apple-developer-domain-association.txt`, the honest position is that **you are blocked until
something can serve `https://api.oltre.space/.well-known/apple-developer-domain-association.txt`**
with HTTP 200 as `text/plain` and no redirect. Hosting it on the apex does not help: Apple would be
demanding the file for the domain being registered, and the Return URL's host must itself be a
registered domain, so registering the apex instead leaves step 15 unfinishable.

The two workable moves, in order of cost:

1. **Defer step 15 only.** Steps 17–21 — the key — are unaffected, and the key is the one-shot half.
   Come back to the Services ID configuration when the server (step 44) can answer that path.
2. **Point `api.oltre.space` temporarily at any host you control that can serve one static file**,
   register the domain, then repoint it. The Services ID configuration survives the repointing;
   nothing re-checks.

## 17. Read this, then create the key

> **The `.p8` downloads exactly once.** Apple: *"Save this file in a secure place because the key is
> not saved in your developer account and you won't be able to download it again. If the Download
> button is disabled, you previously downloaded the key."* There is no recovery path — a lost p8
> means revoking the key and creating a replacement. **Do not close the browser tab until step 20
> has both copies on disk and the digest written down.**

## 18. Create the key

<https://developer.apple.com/account/resources/authkeys/list> → **Keys** in the sidebar → the add
button **(+)**.

Apple's flow, and note there is **no Register button on this screen** — that belongs to the
Identifiers flow:

- **Key Name:** `Oltre Sign in with Apple`
- Tick **Sign in with Apple**, then **Continue**.
- Click **Configure** beside it, select Primary App ID `dev.fardavide.oltre`, **Continue**.
- Review the key configuration, then **Confirm**.

**Do not click Download yet.** Step 19 reads a value off this screen first.

**Scope, since it is not obvious:** the key is scoped to a *primary App ID and its group* — not to
the whole team, and not to the Services ID. Because `dev.fardavide.oltre.signin` is configured
against `dev.fardavide.oltre`, **one key signs client secrets for both `client_id`s**. A primary App
ID may hold at most two such keys; that headroom exists so you can rotate.

## 19. Read the Key ID, then download and move the key

The **Key ID** is the 10-character alphanumeric string shown with the key, e.g. `ABC123DEFG`. Write
it down **now** — it is what you substitute into every command in step 20, and it is unrecoverable
from the key material. It stays visible in the portal (unlike the key itself), and it is also in the
filename, which is the only other reason a lost Key ID is recoverable.

Now click **Download**, then **Done**. The file lands in `~/Downloads` as `AuthKey_<KEYID>.p8` —
around 250 bytes of PKCS#8 PEM starting `-----BEGIN PRIVATE KEY-----`.

Look before you move, because a glob in the one step where the file is unrecoverable is how an APNs
or App Store Connect key from a previous year gets silently swept along:

```
ls -l ~/Downloads/AuthKey_*.p8
```

Then move **the one file, by name**:

```
mv ~/Downloads/AuthKey_<KEYID>.p8 ~/Documents/Keys/Oltre/identity/
```

```
chmod 600 ~/Documents/Keys/Oltre/identity/AuthKey_<KEYID>.p8
```

`~/Downloads` is indexed, swept by cleanup tools and readable by anything you run. Leaving a
one-shot key there "for a few minutes" is how it gets lost.

## 20. Prove it is the right file, and fingerprint it

```
openssl pkey -in ~/Documents/Keys/Oltre/identity/AuthKey_<KEYID>.p8 -noout -text_pub
```

Expect `Public-Key: (256 bit)`, `ASN1 OID: prime256v1` and `NIST CURVE: P-256`. Anything else means
the wrong file — ES256 is defined over P-256 and nothing else will sign a client secret Apple
accepts.

**`-text_pub`, not `-text`.** The obvious command is `-noout -text`, and it prints the **private
key** to the terminal. That is merely untidy when a human runs it in a scrollback they own; it is a
real leak when an agent runs it, because the one-shot key then lives in a transcript. Same check,
public half only.

```
openssl pkey -in ~/Documents/Keys/Oltre/identity/AuthKey_<KEYID>.p8 -pubout -outform DER | shasum -a 256
```

Write that 64-character hex digest down for step 38. It is not secret, and it is the only thing that
lets you tell whether a restored backup is the same key — exactly the discipline already used for
the keystore's certificate SHA-256.

Only now close the browser tab.

## 21. Record the Apple identifiers

**Do not use TextEdit** (`open -e`): its smart quotes and text substitutions will silently mangle a
pasted client ID or redirect URI, and every value in these files must be byte-exact. Use a code
editor, or a heredoc, or `nano`.

```
(umask 077; nano ~/Documents/Keys/Oltre/identity/apple.env)
```

Contents, filling in the Key ID:

```
OLTRE_APPLE_TEAM_ID=A7Q83J6LR4
OLTRE_APPLE_KEY_ID=<10-CHAR-KEY-ID>
OLTRE_APPLE_BUNDLE_ID=dev.fardavide.oltre
OLTRE_APPLE_SERVICES_ID=dev.fardavide.oltre.signin
OLTRE_APPLE_REDIRECT_URI=https://api.oltre.space/v1/auth/apple/callback
```

None of these is secret — Team ID `A7Q83J6LR4` is already committed in `iosApp/project.yml`, which
is correct and not a leak. They live here because the Key ID changes on every rotation, and
separating it from the key is what produces a broken deploy six months later.

## 22. Register the server-to-server notification endpoint (optional today, cheap now)

<https://developer.apple.com/account/resources/identifiers/list> → `dev.fardavide.oltre` → **Edit** →
**Sign in with Apple** → **Configure** → **Server-to-Server Notification Endpoint**:

```
https://api.oltre.space/v1/auth/apple/notifications
```

This is how you learn a player deleted their Apple Account or revoked the app, and it pairs with
`/auth/revoke` for the in-app account deletion App Review requires. It is worth entering today
purely because `api.oltre.space` is permanent, and it is harmless while the server does not exist —
nobody has signed in, so there is nothing to notify about. One endpoint per app group, shared by the
App ID and the Services ID.

Two things to carry into the deployment slice:

- **Apple requires TLS 1.2 or higher at that endpoint.** That is a constraint on the Cloud Run
  service, not on this click.
- The endpoint is a publicly reachable POST target. **It must verify Apple's signature before it
  acts on anything** — see step 45's neighbourhood in Part 7.

If the portal refuses the URL for any reason, leave the field blank, save the modal without it, and
**re-open the App ID to confirm the primary Sign in with Apple configuration is still there** before
moving on. Apple's Save/Confirm flow is where a silent drop would happen.

Not applicable here, but worth knowing the direction of travel: **from 1 January 2026 developers
based in the Republic of Korea must supply a notification endpoint** when registering a new Services
ID or updating an existing one. Apple is making this mandatory jurisdiction by jurisdiction.

## 23. Re-open everything and confirm it persisted

Two minutes, and it is the only check available on this half. Apple's multi-button
Done/Continue/Save flow is exactly where a value gets dropped without an error.

- `dev.fardavide.oltre` → **Edit** → **Sign in with Apple** ticked, **Configure** shows *primary*,
  and the notification endpoint still reads what you typed.
- `dev.fardavide.oltre.signin` → **Configure** → **Primary App ID** is `dev.fardavide.oltre`, and
  **Website URLs** still lists `api.oltre.space` and the full callback URL.
- Keys list → the key is there, and its **Download** button is now greyed out. That is the expected
  state and it confirms you have the only copy.

---

# Part 4 — Google

## 24. Create the Cloud project

<https://console.cloud.google.com/projectcreate>

- **Project name:** `Oltre`
- **Project ID:** **`oltre-506614`** — what Google actually assigned, 2026-08-25.

**Done.** The rest of this step is kept because the ID it produced is not the one this guide
originally told you to type, and the reason matters.

**You may not get to choose it.** The console derives an ID from the project name and appends
random digits; the *Edit* control that lets you override it is easy to miss and is not always
offered. This guide previously said to type `oltre-prod`, which was wrong in a way that would only
have surfaced at step 42, six `--project=` flags later.

**And it is immutable.** Google: a project ID *"cannot be changed after the project is created"*.
So `oltre-506614` is permanent for as long as the project is — renaming the *project* is possible
and cosmetic, renaming the *ID* is not. Recorded in `google.env`; nothing else in this repository
holds it.
- **Location:** No organisation.

With no organisation attached, the Audience user type is forced to **External** and Internal is
greyed out. That is correct here; do not go looking for Internal.

OAuth clients belong to a project and cannot be moved between projects. Use the same project that
will host Cloud Run, so branding, clients and the service live together.

## 25. Decide billing now, not at step 42

Nothing from here to step 41 needs a billing account. **Step 42 does** — `gcloud services enable`
fails on an unbilled project with `FAILED_PRECONDITION: Billing must be enabled for activation of
service(s)`, and Cloud Run needs it too.

The cost is not zero and is not close to the thing step 42 rejects: Secret Manager is **$0.06 per
active secret version per location per month** and **$0.03 per 10,000 access operations**, with a
free allotment covering the first 10,000 accesses. Two secrets is about €0.12/month. The load
balancer step 42 turns down is tens of euros. "Zero-euro target" means "no standing infrastructure
bill", and this is inside it.

If you link a card, set the budget alert from step 42 in the same visit. If you do not, stop after
step 41 today; nothing is lost.

## 26. Run the Auth Platform wizard

<https://console.cloud.google.com/auth/overview> → **Get started**. This is where "APIs & Services →
OAuth consent screen" moved to; most tutorials still name the old menu item, which no longer exists.

- **App name:** `Oltre`
- **User support email:** a dropdown, not a free-text box — it offers only the signed-in Google
  account or a Google Group you own — an Apple private-relay alias will not appear in that list, and
  neither will any address not attached to a Google account. **Whatever you pick is published on the consent screen to every user who signs in**, so if
  the only option is a personal address you would rather not publish, create a Google Group first
  and pick that instead. This is a decision, not a form field.
- **Audience:** External
- **Developer contact:** the same address
- Agree to the User Data Policy → **Create**.

Leave the **Branding** page's App domain block (home page / privacy / terms / authorized domains)
**empty** for now. It is only needed for brand verification, which is step 52 and is cosmetic. When
you do fill it, add the authorized domain *before* the URLs — the page rejects URLs whose domain is
not already listed.

## 27. Add the scopes — three, and no more

The Google Auth Platform's sidebar reads Overview / Branding / Audience / **Data Access** / Clients /
Verification Center. **There is no "Scopes" item** — scopes live under **Data Access**, and that is
the same trap step 26 warns about for the old consent-screen menu.

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

## 28. Do nothing on the Audience page

<https://console.cloud.google.com/auth/audience>

**Add no test users. Do not publish.** This is counter-intuitive, so here is the source, which is
Google's *Manage App Audience* page — not the refresh-token page that gets quoted for it:

> *"The only exception to this behavior is if your app requests a subset of the following: name,
> email address, and user profile (through the userinfo.email, userinfo.profile, openid scopes or
> their OpenID Connect equivalents). For such requests, your users do not need to be in the trusted
> user list, they will not see a warning message, and their authorizations will not expire after 7
> days."*

Oltre requests exactly that subset, and the exception covers all three consequences of Testing
status — the trusted-user list, the unverified-app warning, and the 7-day expiry — not just the
expiry. So while the project sits in **Testing**: any Google account can sign in, there is no
warning, the 100-user cap is not in force. Adding a test user buys nothing and costs something
permanent: *"a test user consumes a project's test user quota once added to the project"*, and
removing them does not return the slot.

**The escape hatch, so this is not a cliff.** The risk here is not theoretical damage, it is that
the failure would show up months later as "sign-in works for me and fails for testers". If a real
tester ever gets `access_denied` with *"currently being tested and can only be accessed by
developer-approved testers"*, add that one account as a test user and move on — one slot out of a
hundred, and it tells you immediately that the exception is not applying. That is a cheap detector,
not a reason to pre-emptively add anyone.

The only thing Testing costs you is that the consent sheet shows the raw client identity rather than
"Oltre" and a logo. Buying that is step 52.

## 29. Read this before creating clients

> **A client secret is shown exactly once, on the creation dialog.** Since June 2025 the console
> shows only the last four characters afterwards. **Download the JSON before closing each dialog.**
> A client may hold at most two secrets, so recovery is rotation, not retrieval.
>
> Two of the five clients issue a secret: **Web** and **Desktop**. Android and iOS clients have no
> secret at all and never will — if a tutorial tells you to put one in the app, it is describing the
> Web client and the advice is wrong.

All five are created at <https://console.cloud.google.com/auth/clients> → **Create client**.

## 30. Client 1 of 5 — Web application (the audience)

- **Application type:** Web application
- **Name:** `Oltre server`
- **Authorized JavaScript origins:** leave empty
- **Authorized redirect URIs:** leave empty

This client exists only to be the server-side audience. It needs no redirect URI because the server
never runs a Google web redirect flow — Android and iOS use the platform SDKs, desktop uses loopback.
This is the standard shape of a Credential Manager `serverClientId` client. Google's documentation
does not state outright that both fields may be empty; **if the console refuses to save, put
`https://api.oltre.space/v1/auth/google/unused` in Authorized redirect URIs.** Nothing will ever
request it, and it is inside a domain you own.

Afterwards, a correctly-created audience-only client shows: type Web application, no origins, no
redirect URIs, one client ID, one secret. That is all it needs to be.

Download the JSON on the dialog, then:

```
mv ~/Downloads/client_secret_*.json ~/Documents/Keys/Oltre/identity/google-web-client.json
```

```
chmod 600 ~/Documents/Keys/Oltre/identity/google-web-client.json
```

The client ID looks like `123456789012-abc123def456ghi789jkl012mno345pq.apps.googleusercontent.com`;
the secret begins `GOCSPX-`. Your design (verify the ID token against JWKS) never uses that secret —
store it anyway rather than lose it.

## 31. Client 2 of 5 — iOS

- **Application type:** iOS
- **Name:** `Oltre iOS`
- **Bundle ID:** `dev.fardavide.oltre`
- **Team ID:** `A7Q83J6LR4`
- **App Store ID:** leave empty — a TestFlight-only build has no numeric App Store id yet, and
  nothing breaks meanwhile.

Team ID is optional in the console but required if App Check / App Attest is ever turned on.

## 32. Client 3 of 5 — Android, release key

**Step 5's off-machine copy must exist before this one** — this is the click that makes the keystore
load-bearing for sign-in.

- **Application type:** Android
- **Name:** `Oltre Android (release)`
- **Package name:** `dev.fardavide.oltre`
- **SHA-1 certificate fingerprint:** `24:AA:53:1D:64:20:C6:B9:65:86:FD:53:E6:B7:1A:E7:0C:3E:DB:98`

SHA-1 only — the console does not accept the SHA-256 that `./gradlew signingReport` also prints.
Play App Signing is not involved: you ship a self-signed APK on a GitHub Release, so the fingerprint
Google sees at runtime is this keystore's own.

## 33. Client 4 of 5 — Android, debug key

Yes, a second client. An Android OAuth client holds exactly one package + SHA-1 pair, and Google is
explicit: *"For each SHA-1 fingerprint you obtain, you must create an OAuth Client ID of type
Android."*

Derive the fingerprint rather than trusting a literal — the debug keystore is machine-local,
Android Studio regenerates it, and it expires:

```
keytool -list -v -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android
```

On this machine today that prints
`39:1D:08:0B:9F:4A:80:87:0C:CA:4E:9A:5F:34:57:81:E4:3B:A1:E1`. **On a second machine, or after
Android Studio regenerates `~/.android/debug.keystore`, it will be a different value and you will
need another Android client for it** — or copy the existing debug keystore across, which is the
cheaper habit.

- **Application type:** Android
- **Name:** `Oltre Android (debug)`
- **Package name:** `dev.fardavide.oltre`
- **SHA-1 certificate fingerprint:** whatever the command above printed

Skip this client and sign-in works in release and fails on every `./gradlew installDebug` build.
Because this project uses Credential Manager, the symptom is a `GetCredentialException` reading
**`[28444] Developer console is not set up correctly`** — not the `DEVELOPER_ERROR` / status 10 that
older guides describe, which belongs to the deprecated `com.google.android.gms.auth.api.signin`
path. Either way it reads like a code bug for an afternoon.

If the console answers *"an OAuth2 client already exists for this package name and SHA-1"*, some
other Google Cloud or Firebase project of yours has claimed the pair; it is globally unique and you
must delete it there first. To find it: sign in to <https://console.firebase.google.com> with each of
your Google accounts in turn and look for a project containing an Android app with package
`dev.fardavide.oltre`, then check <https://console.cloud.google.com/auth/clients> for each Cloud
project the account can see. There is no cross-account search; it is a manual sweep, which is the
argument for using one account throughout.

## 34. Client 5 of 5 — Desktop app

- **Application type:** Desktop app
- **Name:** `Oltre desktop`

There are no further fields — the console asks for nothing else, and deliberately has no
redirect-URI box: loopback is implicit for this client type and the port is chosen at runtime.

Download the JSON on the dialog:

```
mv ~/Downloads/client_secret_*.json ~/Documents/Keys/Oltre/identity/google-desktop-client.json
```

```
chmod 600 ~/Documents/Keys/Oltre/identity/google-desktop-client.json
```

A secret **is** issued and it will ship inside the desktop binary. Google's own documentation treats
that as expected — the security comes from PKCE. Treat a leak of this one as a non-event, and never
reuse the Web client's secret here.

## 35. Record the Google identifiers

**Done.** `~/Documents/Keys/Oltre/identity/google.env` holds the project ID, all five client IDs and
the reversed iOS client ID that step 48 needs.

**Every client has a downloadable file, and the download is how you tell them apart.** The console
does not label a saved file with the client type, but the file's own shape does — which matters
because two Android clients are indistinguishable by name:

| File shape | Which client |
|---|---|
| `{"web": {…}}` with `client_secret` | The Web client — audience #1 |
| A `.plist` with `CLIENT_ID` and `REVERSED_CLIENT_ID` | iOS |
| `{"installed": {…}}` **with** `client_secret` | Desktop — audience #2 |
| `{"installed": {…}}` **without** `client_secret` | Android |

That last row is the useful one. Android and iOS clients have no secret and never will; Web and
Desktop do. So an `installed` file with a secret is the desktop client, and an `installed` file
without one is Android — no need to remember which download was which.

**What the files cannot tell you is which Android client is release and which is debug.** Neither
records its SHA-1. Both are recorded in `google.env` on creation order, and the folder README says
so plainly rather than pretending otherwise. It is bookkeeping, not configuration: an Android client
ID is never named in code — the client binds a package and a fingerprint that Google checks at
runtime, and a token from an Android app carries the *Web* client ID as its audience. Getting the
two round the wrong way mislabels a record and breaks nothing.

## 36. Generate the server's own session-signing key

**Done** — 88 base64 characters at `0600`, no trailing newline.

Nobody hands you this one. It is the value that makes Oltre's own session tokens forgeable if it
leaks, and it is the only credential here you can rotate freely — the cost of rotation is that every
player signs in again.

```
(umask 077; openssl rand -base64 64 | tr -d '\n' > ~/Documents/Keys/Oltre/identity/session-jwt.key)
```

Do not reuse the keystore password. Do not commit a default value to source as a "dev fallback" — a
dev fallback that ships is a server with a publicly known signing key.

---

# Part 5 — Store, back up, and prove the backup

## 37. Write the inventory

Without it a restored p8 is an anonymous 250-byte file and nobody knows which Key ID it belongs to —
and the Key ID is unrecoverable from the key material. Paste this into
`~/Documents/Keys/Oltre/identity/README.md` and fill it in:

```markdown
# Oltre sign-in credentials

Created <DATE>. Lives in ~/Documents/Keys/Oltre/identity/, which iCloud carries off-machine.

| File | What it is | Secret? |
|---|---|---|
| AuthKey_<KEYID>.p8 | Apple Sign in with Apple key. ONE-SHOT: cannot be re-downloaded. | Yes |
| apple.env | Apple identifiers, none secret. | No |
| google-web-client.json | Google Web OAuth client (the server-side audience). | Yes (secret inside) |
| google-desktop-client.json | Google Desktop OAuth client. Secret ships in the binary. | Nominally |
| google.env | Owning Google account, project ID, five client IDs. None secret. | No |
| session-jwt.key | Oltre's own session-JWT signing key. Freely rotatable. | Yes |

## Verification values

- p8 public-key SHA-256: <64 HEX CHARS>
  openssl pkey -in AuthKey_<KEYID>.p8 -pubout -outform DER | shasum -a 256
- Android release cert SHA-1: 24:AA:53:1D:64:20:C6:B9:65:86:FD:53:E6:B7:1A:E7:0C:3E:DB:98
- Android release cert SHA-256: 48:56:AF:68:C7:5D:02:E9:6C:51:70:5E:44:48:23:33:FE:5E:1F:A1:25:5F:24:38:2D:1E:23:AE:EF:B1:8D:DC

## Identifiers

- Apple Team ID: A7Q83J6LR4
- Apple Key ID: <10 CHARS>            (also in the p8 filename; not recoverable from key material)
- Apple Services ID: dev.fardavide.oltre.signin
- Apple primary App ID: dev.fardavide.oltre
- Google account: <ACCOUNT>
- Google Cloud project: <PROJECT ID>

## Which account owns which portal

- Apple Developer (team A7Q83J6LR4): <APPLE ID>
- Google Cloud + Search Console: <ACCOUNT>
- GitHub (repo, Pages, domain verification): fardavide
- Namecheap (oltre.space, expires 2027-08-25): <NAMECHEAP LOGIN>

## Rotation

- p8: create the second key (max two per primary App ID), deploy it, confirm the startup
  self-check passes on the new one, THEN revoke the old. In that order.
- session-jwt.key: regenerate freely; every player signs in again.
- Google client secrets: two per client, so rotate by adding then removing.
```

## 38. Prove the copies are real — both keys

No mirroring step: `~/Documents/Keys/` is already the archive, and iCloud already carries it off the
machine. What is left is the half that actually matters and is usually skipped — **checking that
what is stored is what you think is stored.**

A `.p8` carries no identity of its own. A restored one is an anonymous 250-byte file, and the Key ID
that gives it meaning lives only in its filename and in `apple.env`. So verify against the digest
recorded at step 20:

```
openssl pkey -in ~/Documents/Keys/Oltre/identity/AuthKey_<KEYID>.p8 -pubout -outform DER | shasum -a 256
```

Must equal step 20's digest.

```
keytool -list -v -alias oltre -keystore ~/Documents/Keys/Oltre/android-signing/oltre-release.keystore
```

Must print `SHA1: 24:AA:53:1D:64:20:C6:B9:65:86:FD:53:E6:B7:1A:E7:0C:3E:DB:98`. **This half is not
optional.** Step 4 has just argued that this key now controls sign-in for every installed user;
having a copy and never proving it is the same key is the same mistake one level up.

**Then confirm iCloud has actually taken them**, because "it is in Documents" is an assumption until
something says so:

```
brctl status | head -5
```

Look for a recent `last-sync` and `has-synced-down`. In Finder, no file in `~/Documents/Keys/`
should carry a cloud or upload badge. If iCloud is paused, out of quota, or signed out, everything
above still passes and you still have one copy on one SSD — the exact failure step 4 is about.

**Which copies exist, and what each survives:**

| Copy | Survives |
|---|---|
| `~/.oltre/` (keystore only) | Nothing — it is the working copy, on the same disk |
| `~/Documents/Keys/` | A mistaken `rm`, and — via iCloud — this machine dying |
| Secret Manager (step 42, later) | Everything above, and it is readable back, unlike a GitHub secret |

**No fourth copy.** Every additional place a one-shot key exists is another place it can leak from,
with no compensating recoverability.

## 39. Accounts, 2FA and renewals

After this guide runs, **four accounts hold state that cannot be reconstructed from the repository**:
Apple Developer, Google Cloud, GitHub and Namecheap. Confirm 2FA is on for all four and that the
recovery codes are stored where step 5's passphrase went — not in iCloud Keychain alone. The
account-to-portal map is already in step 37's README template; that is the artefact that makes this
recoverable by someone who is not you.

Two renewals with no in-product warning and total blast radius:

- **`oltre.space`, expires 2027-08-25.** Auto-Renew on (step 7).
- **The Apple Developer Program membership, annual.** Expiry invalidates certificates and profiles,
  stops TestFlight builds reaching testers, and makes the App ID and Services ID configuration
  unusable. It is the one renewal that takes the whole Apple half down at once.

Both are in the expiry-clocks table below.

## 40. Record what you decided

This project keeps irreversible choices in `.claude/docs/decisions.md`. Add a round covering:
primary rather than grouped App ID; `europe-west1`; five OAuth clients and why there are two Android
ones; Testing rather than Published, with the *Manage App Audience* quote; `api.oltre.space` as
permanent; and the Google account and project ID.

And close the open loop in memory:
`~/.claude/projects/-Users-davide-Dev-Projects-Oltre/memory/android-release-keystore.md` currently
says *"as of 2026-08-09 there is still no off-machine copy"* and *"raise this again rather than
assume it was handled"*. Step 38 changed that fact. Update it, including the restore-test result and
the digest, or the next session either re-raises it or — worse — trusts the stale note.

## 41. Nothing goes into GitHub secrets today

CI builds the APK and runs the tests, and needs neither the p8 nor any Google value to do that; the
four existing `ANDROID_*` secrets remain the complete list. A GitHub secret is write-only, so it is
not a backup — every extra copy of the p8 is another place it can leak from with no compensating
recoverability.

When the identity slice actually reads these at build time, that is the moment. The public client
IDs can go on the command line; **the secret cannot**, because `--body` puts it in `~/.zsh_history`
permanently and in the process table while it runs:

```
gh secret set GOOGLE_OAUTH_WEB_CLIENT_ID --repo fardavide/oltre --body "<web-client-id>"
```

```
gh secret set GOOGLE_OAUTH_IOS_CLIENT_ID --repo fardavide/oltre --body "<ios-client-id>"
```

```
gh secret set GOOGLE_OAUTH_DESKTOP_CLIENT_ID --repo fardavide/oltre --body "<desktop-client-id>"
```

```
gh secret set GOOGLE_OAUTH_DESKTOP_CLIENT_SECRET --repo fardavide/oltre < ~/Documents/Keys/Oltre/identity/desktop-secret.txt
```

(`gh secret set` reads the value from standard input when `--body` is omitted.)

**Why the desktop secret is a GitHub secret at all, given it ships in the binary and its leak is a
non-event:** not to protect it, but so it never gets committed to the repository and so rotating it
is a secret update rather than a source change. Settle that here so the identity slice does not
re-litigate it.

---

# Part 6 — One deployment decision worth locking today

## 42. Pick the Cloud Run region now, because two things freeze around it

Cloud Run **custom domain mapping is a Preview feature**, documented by Google as having latency
issues and as not recommended for production, and among EU regions it is supported **only in
`europe-north1`, `europe-west1` and `europe-west4`**. The alternatives Google names are a global
external Application Load Balancer — a standing monthly cost, which breaks the zero-euro target —
or Firebase Hosting, which is low cost and could also serve static content.

**Recommendation: `europe-west1`.** On the supported list, EU, close to the Frankfurt-ish Neon
regions.

**And choosing the preview mapping now does not put the permanent-hostname promise at risk**, which
is the reason it is safe to proceed at all. `api.oltre.space` is a name in a zone you control. If the
mapping turns out to be too slow or is withdrawn, you repoint the same hostname at Firebase Hosting
or a load balancer and every build already on a phone keeps working. The preview feature is
replaceable; the hostname is not, and the hostname is the part that is committed.

This matters *today* because Secret Manager replication locations **cannot be changed after
creation**. Needs `gcloud` (not installed — see "Before you start") and a linked billing account
(step 25). None of it needs Cloud Run to exist:

```
gcloud services enable secretmanager.googleapis.com run.googleapis.com --project=oltre-506614
```

```
gcloud secrets create oltre-apple-signin-p8 --project=oltre-506614 --replication-policy=user-managed --locations=europe-west1 --data-file=$HOME/Documents/Keys/Oltre/identity/AuthKey_<KEYID>.p8
```

```
gcloud secrets create oltre-session-jwt-key --project=oltre-506614 --replication-policy=user-managed --locations=europe-west1 --data-file=$HOME/Documents/Keys/Oltre/identity/session-jwt.key
```

```
gcloud iam service-accounts create oltre-server --project=oltre-506614 --display-name="Oltre server runtime"
```

Then grant read access per-secret rather than project-wide, so "the server can read two secrets"
does not become "the server can read everything":

```
gcloud secrets add-iam-policy-binding oltre-apple-signin-p8 --project=oltre-506614 --member=serviceAccount:oltre-server@oltre-506614.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
```

```
gcloud secrets add-iam-policy-binding oltre-session-jwt-key --project=oltre-506614 --member=serviceAccount:oltre-server@oltre-506614.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
```

That project ID is fixed and immutable — see step 24.

**The bindings cannot be tested before a service exists** — there is nothing to run as. What you can
check is that they were recorded:

```
gcloud secrets get-iam-policy oltre-apple-signin-p8 --project=oltre-506614
```

**Done 2026-08-25**, except the budget alert. What ran, and what it produced:

- Both APIs enabled.
- `oltre-apple-signin-p8` and `oltre-session-jwt-key`, both `user-managed` replication pinned to
  `europe-west1`.
- Service account `oltre-server@oltre-506614.iam.gserviceaccount.com`.
- `roles/secretmanager.secretAccessor` granted on each secret **individually**. Confirmed with
  `get-iam-policy`: that service account is the only member on either.
- **The stored p8 verified by round-trip** — reading version 1 back out and re-deriving its
  public-key digest gives `95d11ed5…28d13d9b`, matching step 20. That is what makes Secret Manager a
  genuine third copy rather than an assumption: unlike a GitHub secret it is readable back.

**The budget alert: set 2026-08-25**, €2/month with email at 50% and 100%, in the console at
**Billing → Budgets & alerts**. It is the only guard the zero-euro target has — the thing that would
catch a load balancer or a minimum-instance setting being switched on months from now, when nobody
is looking.

Browser-only, deliberately, and **it is also the one step here nobody verified afterwards.**
`gcloud billing budgets list` needs the Cloud Billing Budget API enabled *and* application-default
credentials with a quota project, which is a third API and a second auth mode to read back a number
that is visible on the page that set it. So this step rests on having seen it, not on a check —
worth knowing if the first surprising bill ever arrives.

Create `oltre-google-web-client-secret` and `oltre-database-url` later: an empty secret version is
worse than a missing one, because the instance starts and then fails at the first query.

---

# Part 7 — What genuinely waits, and on what

> **Steps 43, 44 and 45 are `#111`, and all of it is done except two things Davide has to do
> himself** — the Namecheap record in 43 and the budget-alert test in 45a. `#129` merged on
> 2026-08-26, the first deploy created the service, and 43, 44d and the three checks the Done-means
> could not pass by assuming all ran that hour. What each one produced is in its own step below.

## 43. Cloud Run domain mapping for `api.oltre.space`

**Created 2026-08-26. What is left is one DNS record, and it is Davide's** — Namecheap is his
account and nothing in a session can reach it. The mapping is waiting on it and says so:
`DomainRoutable` is true, `CertificateProvisioned` is `Unknown` with *"You must configure your DNS
records for certificate issuance to begin."*

| Host | Type | Value |
|---|---|---|
| `api` | `CNAME` | `ghs.googlehosted.com.` |

**Waits on:** the first successful deploy — so on 44a. Search Console ownership of `oltre.space` is
already done (step 8a), which is the part that could have blocked you, and the `gcloud` account is the
same one that verified it.

**`gcloud beta` is not installed by default and the create command will not prompt for it** in a
non-interactive session — it exits with *"This prompt could not be answered"*. One
`gcloud components install beta --quiet` first, and it is a one-off.

```
gcloud beta run domain-mappings create --service=oltre-server --domain=api.oltre.space --project=oltre-506614 --region=europe-west1
```

It prints the DNS records to add. For a subdomain that is normally a single `CNAME` to
`ghs.googlehosted.com`; take what it actually prints rather than that sentence. At Namecheap:
**Domain List → oltre.space → Manage → Advanced DNS**, host `api`, and leave the apex records from
step 7 alone — they are what serves the site.

Then, and this is the check rather than the click:

```
gcloud beta run domain-mappings describe --domain=api.oltre.space --project=oltre-506614 --region=europe-west1 --format='value(status.conditions[].type, status.conditions[].status)'
```

`CertificateProvisioned` goes true minutes to hours after the record propagates, and **`curl` answers
a TLS error until it does**. That is the expected state, not a failure — the `run.app` URL keeps
working throughout, which is why nothing in the client hard-codes either one yet.

**Apple requires TLS 1.2 or higher at this hostname** (step 22). Google's managed certificate is 1.2+
and there is nothing to configure; it is recorded here because it is a constraint on this step rather
than on that click.

## 44. Neon, the last two secrets, and the deploy

Everything except 44a **is done** — `#111` built it and this section is the record. Read 44a, do it,
and the rest happens on its own.

### 44a. Neon — **done 2026-08-26**

`oltre-database-url` exists: `user-managed` replication pinned to `europe-west1`, one version, and
`roles/secretmanager.secretAccessor` granted to `oltre-server@` and to nothing else — `get-iam-policy`
confirms a single member, and `oltre-deployer` is deliberately not on it. **Verified by round trip**,
the same check step 42 used for the p8: reading version 1 back and hashing it gives `3fed7552…`,
matching the file it was created from. It points at the **pooled** endpoint, database `neondb`.

The string itself is at `~/Documents/Keys/Oltre/identity/neon-database-url`, `0600`, beside the other
credentials — assembled from the `PGUSER`/`PGPASSWORD` Davide saved in `env` and piped to `gcloud`
from the file, so it never passed through a terminal, a shell history or a session.

**It is the *second* password on that role.** The first was pasted into an agent session on
2026-08-26 and reset in Neon the same hour; see the box below, which is the part of this step worth
keeping now that the rest of it is history. What follows is what to do if it ever has to be done
again.

Nothing in a session can create this: it needs an account, and the connection string is key material
that must not enter one.

1. <https://console.neon.tech> → sign up → **create a project in an EU region** (`aws-eu-central-1`,
   Frankfurt, is the closest to `europe-west1`). Free plan.
2. Copy the **pooled** connection string — the one whose host carries `-pooler`, which the console
   offers behind a *Connection pooling* toggle. Cloud Run starts and stops instances all day and each
   one opens a pool; the pooled endpoint keeps that off Neon's connection limit and skips a Postgres
   backend start on every connection, which matters here because the pool deliberately drains to
   nothing between syncs (see `PostgresDatabase.kt`).
3. Put it in Secret Manager, pinned to the same location as the other two — **replication locations
   cannot be changed after creation** (step 42):

```
gcloud secrets create oltre-database-url --project=oltre-506614 --replication-policy=user-managed --locations=europe-west1 --data-file=-
```

**Paste the string, press Return, then Ctrl-D.** `--data-file=-` reads standard input, so the value
never reaches your shell history — which is the whole reason it is not written as an `echo`. If your
paste added a trailing newline, that is fine: it is stripped where it is read.

**Paste it exactly as the console prints it.** Neon gives a *libpq* URI —
`postgresql://user:password@host/database?sslmode=require` — and the server converts it (see
`DatabaseUrl.kt`). **Do not turn it into a JDBC URL by hand**: that is what `#109` assumed it would
be given, and it is the one form no provider prints. Hand-editing it is also how `sslmode` gets
dropped.

> **And it does not go into a chat, an issue or a commit — including to Claude.** This rule already
> exists at the top of this document for the `.p8`; it says *session* and it means it. A connection
> string pasted into an agent session is a password in a transcript, and the answer is not to worry
> about it afterwards but to **reset the role's password in Neon and start again** — Neon console →
> **Branches → Roles → the role → Reset password**, which takes seconds and invalidates the old one.
> Learned on 2026-08-26, from doing it.
>
> **If a rotation happens after the first deploy**, it is one command and a redeploy rather than a
> new secret:
>
> ```
> gcloud secrets versions add oltre-database-url --project=oltre-506614 --data-file=-
> ```
>
> The service pins `oltre-database-url:latest`, so the new version reaches the next revision and not
> the running one — which makes the redeploy that picks it up a free chance to do `#111`'s
> colony-survives-a-redeploy check.

4. Let the server — and only the server — read it:

```
gcloud secrets add-iam-policy-binding oltre-database-url --project=oltre-506614 --member=serviceAccount:oltre-server@oltre-506614.iam.gserviceaccount.com --role=roles/secretmanager.secretAccessor
```

```
gcloud secrets get-iam-policy oltre-database-url --project=oltre-506614
```

That should list exactly one member. `oltre-deployer` is deliberately not on it: the account that
deploys the service may not read what the service reads.

### 44b. The two audiences, as repository variables — **done 2026-08-26**

Both are set, read out of `google.env` and `apple.env` rather than typed, and `gh variable list`
confirms them. `GOOGLE_CLIENT_IDS` is the **Web** and **Desktop** client ids; `APPLE_CLIENT_IDS` is
the bundle id and the Services ID. Neither Android client id is named, and neither needs to be.

If they ever have to be set again:
<https://github.com/fardavide/oltre/settings/variables/actions> → **New repository variable**, twice:

| Name | Value |
|---|---|
| `APPLE_CLIENT_IDS` | `dev.fardavide.oltre,dev.fardavide.oltre.signin` |
| `GOOGLE_CLIENT_IDS` | the **Web** client id and the **Desktop** client id from `google.env`, comma-separated |

**Variables and not secrets, because none of these is one** — an OAuth client id travels in every
redirect a browser makes, and Apple's are already in `iosApp/project.yml`. They are out of the
workflow file so that adding a sixth client is a settings change rather than a commit.

**Two per provider and never one**, which is the trap `#110` records: the Web client is the audience
for *both* phones and the Desktop client is a second one. A single-audience server passes every test
written against a generated keypair and then refuses the desktop build — the only build the behaviour
and screenshot suites run on. Check the exact strings against `~/Documents/Keys/Oltre/identity/`
rather than against memory.

### 44c. Everything else — done, and here is what it does

`.github/workflows/deploy-server.yml` builds `installDist`, runs the suite, authenticates by
**Workload Identity Federation** — no service-account JSON in a GitHub secret — pushes the image to
Artifact Registry and runs one `gcloud run deploy`. It fires on a push to `main` that touches
`server/`, `protocol/`, `core/`, the `Dockerfile` or itself, and on manual dispatch. Provisioned
2026-08-26 and verified by reading each one back:

- Artifact Registry `europe-west1-docker.pkg.dev/oltre-506614/oltre`.
- Workload identity pool `github`, provider `fardavide-oltre`, **condition
  `assertion.repository=='fardavide/oltre'`** — a token from any other repository cannot use it.
- Service account `oltre-deployer@`, with `roles/run.admin`, `artifactregistry.writer` on that one
  repository, and `iam.serviceAccountUser` on `oltre-server@`. **It holds no `secretAccessor` on
  anything**, which is the point of it being a second account.

The four things the deploy command has to get right, three of which fail **silently**:

- **`--service-account=oltre-server@…`.** Omit it and Cloud Run runs as the Compute Engine default
  service account, which on a project with **no organisation** — step 24 — carries `roles/editor`,
  i.e. read access to every secret including the p8 and the Neon connection string. The org policy
  that would prevent that grant needs an organisation to be enforced by. Step 42 builds least
  privilege; this flag is what uses it, and nothing else in the pipeline would catch its absence.
- **One `--set-secrets` flag, not several.** It is a dict flag — *"all existing secrets will be
  removed first"* — so passing it twice replaces rather than merges and drops the earlier entries.
- **Mount the p8 as a file** (`/secrets/apple/signin.p8`), not an env var: PEM newlines, and a mounted
  secret is re-read on every access whereas an env-var secret is resolved once before the instance
  starts and never changes for that instance's life.
- **`--set-env-vars` needs `^@^`, and this is the one the walkthrough had not spotted.** That flag is
  *itself* comma-delimited, and `APPLE_CLIENT_IDS` and `GOOGLE_CLIENT_IDS` are comma-separated lists —
  so written the obvious way the second audience becomes a variable named after an OAuth client id
  with an empty value. gcloud's leading `^<delimiter>^` is what that syntax is for, and it is the
  same failure as a single-audience check arriving through a shell.

**The service is created by the first run of that workflow**, not by hand. There is nothing to
pre-create: `gcloud run deploy` creates a service that does not exist, and every flag it needs is on
that one command.

### 44d. The keep-warm ping — **done 2026-08-26**

`oltre-keep-warm` exists, `*/10 * * * *`, in `europe-west1`, pointed at
`https://oltre-server-6bi5dbyb5a-ew.a.run.app/health` — **the `run.app` URL, because step 43's
certificate is not issued yet and a ping at `api.oltre.space` would fail every ten minutes until it
is.** Repointing it is one `gcloud scheduler jobs update http … --uri=…` the day the certificate goes
true.

**Forced once and read back rather than assumed**, which is what a scheduled job most deserves: it is
a control nobody looks at again, and one that silently stopped firing would look exactly like one
that works. `gcloud scheduler jobs run` and then the execution log —
`AttemptFinished … URL_CRAWLED. Original HTTP response code number = 204` — with the matching
`Google-Cloud-Scheduler` entry in the service's own request log, 9 ms. Two things about doing it that
way, both cheap and both nearly missed:

- **`gcloud scheduler jobs run` returns before the attempt happens.** The forced run showed up in the
  Cloud Run request log about forty seconds later. Looking too early reads as a job that does
  nothing, which is the one conclusion worth not jumping to.
- **`jobs describe` is not the place to look.** `lastAttemptTime` and `status` stayed empty through
  two successful attempts. The execution log is the record; the job resource is not.

`#106` §6 put the job on the service every ten minutes so a player never meets the cold start. Cloud
Run bills per request, so 144 a day is free, and the free tier allows three jobs.

```
gcloud scheduler jobs create http oltre-keep-warm --project=oltre-506614 --location=europe-west1 --schedule="*/10 * * * *" --uri="https://oltre-server-6bi5dbyb5a-ew.a.run.app/health" --http-method=GET --attempt-deadline=30s --description="Keeps the Cloud Run instance warm. Touches no store — see below."
```

**`/health` returns `204` and reaches nothing, and that is load-bearing rather than lazy.** A health
check that asked the database whether it was there would be the better endpoint on almost any other
host — and here it would keep **Neon** awake around the clock. Neon's free plan bills *compute hours*
and scales the branch to zero after a few minutes idle, so a ping that woke it every ten minutes
would run it for the whole month against an allowance of a fraction of that. The €0 target depends on
this route doing nothing, and an integration test asserts it by handing the server repositories that
raise if they are touched.

**Measured 2026-08-26, before the job was created**, because once the ping exists there is nothing
left to measure. Thirty-two minutes after the last request, so the instance had well and truly idled
out:

| | `curl` total | Cloud Run's own request log |
|---|---|---|
| cold | **5.063 s** | **4.538 s** |
| warm | **0.166 s**, then 0.162 s | 0.0044 s, then 0.0050 s |
| the scheduler's own ping | — | 0.0090 s |

**So the cold start is about 4.9 seconds and not the one to two `#106` §6 assumed**, with startup CPU
boost already on. The two columns are worth having side by side: `curl` from a laptop carries a TLS
handshake and a transatlantic-ish round trip, which is the whole of the warm number — 0.166 s of
which the server accounts for 0.004. Cloud Run's log is what the server did, and it is the honest
figure for what a player would feel.

**Which settles the interval rather than merely justifying it.** Ten minutes is comfortably inside
Cloud Run's idle window, so the instance never gets the chance to go cold; and the thing being
avoided is five seconds of an app that is *supposed* to open in a glance, not one. A slower cold
start makes the ping worth more, not less.

```
curl -o /dev/null -s -w 'cold: %{time_total}s\n' https://oltre-server-6bi5dbyb5a-ew.a.run.app/health
```

```
gcloud logging read 'resource.type=cloud_run_revision AND resource.labels.service_name=oltre-server AND httpRequest.requestUrl:"/health"' --project=oltre-506614 --freshness=10m --limit=5 --format='value(timestamp, httpRequest.status, httpRequest.userAgent, httpRequest.latency)'
```

And **do not** reach for:

```
gcloud run services update oltre-server --project=oltre-506614 --region=europe-west1 --no-cpu-boost
```

Leave the boost on; it is what keeps that 4.9 from being worse.

## 45. The startup self-check and the rate limit — **done, `#111`**

Both are code and both are in `server/`. Recorded here because this step is what asked for them.

**The self-check** is `appleSigningKey` plus `AppleSigningKey.selfCheck`, called from `Main.kt`
*before* `embeddedServer(...).start(...)`. It reads `APPLE_SIGNIN_KEY_FILE`, `APPLE_TEAM_ID` and
`APPLE_KEY_ID`, parses the PKCS#8 PEM and signs a throwaway ES256 token that is never sent anywhere.
Cloud Run keeps the previous revision serving when a new one never becomes ready, so a key that is
truncated, mounted from the wrong secret or generated on the wrong curve is a **failed deploy** rather
than an outage beginning the first time somebody deletes their account. **None of the three has a
default**, as this step asked: half-set is refused at boot, and absent altogether is the dev loop and
says so in the log.

The case worth knowing about, because loading alone would miss it: **a P-384 key is a perfectly good
`ECPrivateKey`** — the PEM decodes and the cast holds — and it cannot sign ES256, because the
algorithm names the curve as well as the hash. That is why the check signs rather than merely loads.

**The rate limit** is `RateLimiter`, twenty requests a minute per caller with the whole burst
available at once, on `/v1/auth/*` and on nothing else — those are the only routes reachable without
a session and the only ones that do a signature check before knowing who is asking. It is keyed on the
**last** hop of `X-Forwarded-For`, because everything before that is whatever the caller wrote. A
refusal is `429` with `Retry-After` and `ApiError.TooManyRequests`, which carries the same number.

**And the endpoint from step 22 now exists.** `POST /v1/auth/apple/notifications` was registered with
Apple months before there was a server and would have started 404ing the day `#113` shipped. It
verifies Apple's signature against the same key set the ID-token verifier uses before it acts on
anything — it is a POST target anybody can reach and one of the four things it can say is *delete this
account*. **Only `account-delete` deletes**: `consent-revoked` is the player turning Sign in with
Apple off in Settings, which is an unlink, and signing in again hands back the same subject.

## 45a. The budget alert — **yours, and it is the one thing `#111` cannot pass by assuming**

The alert itself exists: **€2/month with email at 50% and 100%, set 2026-08-25** (step 42). What
`#111` asks for is that it has been **tested by lowering its threshold once**, and step 42 already
records why that matters here more than usual — *"it is also the one step here nobody verified
afterwards"*, because reading it back needs a third API and a second auth mode.

<https://console.cloud.google.com/billing> → **Budgets & alerts** → the Oltre budget → **Edit**:

1. Lower the amount to **€0.01** and save.
2. Wait for the mail. Google evaluates budgets several times a day rather than instantly, so this is
   *not* a same-minute check — give it a day before concluding anything.
3. **Set it back to €2** and confirm on the page that it saved.

If no mail arrives, the alert has never worked and nothing else would have told you. That is the
whole value of doing it: the alert is the only guard the zero-euro target has, and an untested one is
a guard nobody has seen fire.

## 46. Publish `https://oltre.space/privacy` and `https://oltre.space/terms`
**Waits on:** the policy text, which must describe what the shipped build actually does — so it
cannot be finalised before the build exists. Required for App Store submission and for Google brand
verification; **not** required for sign-in to work, and not for TestFlight internal distribution.

Commit `privacy/index.html` and `terms/index.html` to `gh-pages`. The skeleton below is the shape,
not legal advice — it is what this app actually does, and the one judgement you supply is retention:

> **What is collected.** When you sign in with Apple or Google, Oltre receives an identifier for
> your account from that provider and stores it. It is a pseudonymous string; it is not your name or
> your email address, and it identifies you only within Oltre.
>
> **What is not collected.** Oltre does not store your email address, your name, or your provider
> profile. It contains no analytics, no advertising and no third-party tracking.
>
> **What else is stored.** The state of your colony — buildings, research, fleets, the event log —
> and the instant it was last updated.
>
> **Where, and by whom.** On servers in the European Union, using Google Cloud Run (Google Ireland
> Limited) and Neon (Postgres). The controller is Davide Farella; contact `<CONTACT_ADDRESS>`.
>
> **How long.** Until you delete your account, or after `<N>` months of inactivity.
>
> **Deleting it.** Settings → Delete account, inside the app. Deletion removes the account record
> and the colony, and cannot be undone.
>
> **Your rights.** Under the GDPR you may request access to, correction of, or erasure of your data,
> and may complain to your national data protection authority. Contact `<CONTACT_ADDRESS>`.

Two things to decide rather than copy. **The contact address becomes public** on a page linked from
the App Store, so a dedicated alias beats a personal inbox. And **the retention period has to be a
number you will honour**: if nothing sweeps inactive accounts, write "until you delete your account"
and stop there rather than promising a sweep that does not exist.

## 47. The iOS entitlement — the one the compiler cannot catch
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

**XcodeGen resolves `entitlements.path` relative to the directory holding the spec**, so this writes
`/Users/davide/Dev/Projects/Oltre/iosApp/iosApp/Oltre.entitlements` — the same nesting as the
existing `info.path: iosApp/Info.plist`, which today resolves to
`/Users/davide/Dev/Projects/Oltre/iosApp/iosApp/Info.plist`. Create it one level up and the archive
ships without the entitlement, which is precisely the dead control this step exists to prevent.

Then `xcodegen generate` in `iosApp/`, and commit the project **and** its shared scheme. Never
hand-edit `project.pbxproj`. `Default` is the only value for normal operation.

## 48. The iOS URL scheme for Google
**Waits on:** the same slice. The key is **`CFBundleURLSchemes`** (plural), an array of strings
inside a dictionary inside the **`CFBundleURLTypes`** array — there is no `CFBundleURLScheme`. Under
`targets.Oltre.info.properties` in `/Users/davide/Dev/Projects/Oltre/iosApp/project.yml`:

```yaml
        CFBundleURLTypes:
          - CFBundleURLSchemes:
              - com.googleusercontent.apps.<REVERSED-IOS-CLIENT-ID>
```

The value is `OLTRE_GOOGLE_IOS_CLIENT_ID` with `.apps.googleusercontent.com` stripped and
`com.googleusercontent.apps.` prefixed. Miss it and the browser completes the Google flow and never
returns to the app — a hang with no error, which is again a dead control.

## 49. In-app account deletion
**Waits on:** the server owning an account record; there is nothing to delete server-side today.
Guideline 5.1.1(v): *"If your app supports account creation, you must also offer account deletion
within the app."* It is a hard App Store gate, not a TestFlight one. It must start in the app; a
`mailto:` or support form is a rejection; deactivating is not deleting; and where Sign in with Apple
was used it must also revoke the token through Apple's REST API (`POST
https://appleid.apple.com/auth/revoke`, which needs the p8 — see the expiry clocks). **Note the
collision with invariant 2:** appending an `AccountDeleted` event does not satisfy this if the prior
events still hold the subject id. The log has to go.

## 50. The sign-in screen is a design round trip with a constraint attached
**Waits on:** Claude Design. Put this in the prompt rather than discovering it at submission: Apple's
Human Interface Guidelines require you to *"prominently display a Sign in with Apple button"* and to
*"make a Sign in with Apple button no smaller than other sign-in buttons, and avoid making people
scroll to see the button"*, using Apple's own button treatment rather than a hand-rolled one. A
sign-in screen with Google above a smaller custom Apple button is a rejection *after* the screen is
designed, baselined and shipped — and fixing it then costs a new design round, new screenshot
baselines and a new release.

Guideline 4.8 itself is satisfied by offering Apple alongside Google; it is the presentation rule
that bites, and it is a design input, not an implementation detail.

## 51. App Store Connect — App Privacy, and reviewer sign-in
**Waits on:** a build that actually collects the identifier being in testers' hands; Apple expects
the published answers to describe the shipped version.

**App Privacy:** Identifiers → **User ID**, Linked to user **Yes** (forced: the subject id is the
account key and every save row hangs off it), Used for tracking **No**, purpose **App Functionality**.
The email inside the ID token needs no declaration *while it is genuinely not retained* — log a raw
ID token once in Cloud Run and that stops being true and the published label becomes wrong. These
answers can be changed at any time without shipping an app update.

**Sign-In Information is mandatory for an app that requires login**, and guideline 2.1(a) says so
plainly: *"include demo account info (and turn on your back-end service!) if your app includes a
login."* "The reviewer can use their own Apple Account" is a routine rejection, because the reviewer's
account will not have a working path through a server that has never seen it. This needs a decided
answer — a provisioned reviewer account the server already knows, or a built-in demo mode (which
Apple allows *"with prior approval"*) — and because it constrains the server design it belongs
alongside step 49 rather than being met at submission.

## 52. Google brand verification
**Waits on:** step 46's pages, plus filling the Branding App domain block (authorized domain
`oltre.space` first, then the three URLs), plus **publishing** the app: verification applies once the
app is External **and** Published. Automated review takes minutes; a manual fallback is 2–3 business
days. **A compliant result is valid for only 7 days** — if you do not press *Publish branding* inside
that window the status reverts to "Need to re-verify". Logo must be square, ≤120×120 px, under 1 MB.

**Reconciling this with step 28**, because Google's Android docs will contradict this guide if you
read them cold. The *Sign in with Google on Android* prerequisites list brand verification as setup:
*"Your brand must be verified for your app name to be visible to users on the Sign in with Google
consent screen."* Read the sentence closely — what it gates is **your app name being visible**, not
sign-in working. Without verification the flow completes and the consent sheet shows the raw client
identity instead of "Oltre" and a logo, which to a player reads like a phishing page. It is a real
thing to fix before a public launch and it is not a blocker for TestFlight or for you testing on your
own phone. Both pages are right.

## 53. The cross-platform `sub` check — the one nothing replaces
**Waits on:** a live server plus a TestFlight build and an APK. Sign in with the **same Apple
Account** from both and confirm the server sees an **identical `sub`** and creates one account, not
two.

Apple describes the user identifier as **team-scoped** — WWDC22's *Enhance your Sign in with Apple
experience* calls it a *"unique, stable, team-scoped user identifier"*, the same across every app in
your developer team and across web, iOS and Android — so this *should* agree by construction, and
nothing in the portal confirms it. If it disagrees, the first hypothesis is **not** the grouping: on
team scoping, the primary/grouped choice should not affect `sub` at all. Look instead at whether the
two clients are really on team `A7Q83J6LR4`, and at whether the app has ever been transferred
between teams (a transfer is the documented case where the identifier changes, and Apple publishes a
migration note for it). Grouping still matters for other things — which primary a Services ID hangs
off, and the private-relay address — so keep step 12's choice as made; just do not reach for it as
the explanation here.

Run the check anyway. It is cheap, it is the only end-to-end proof in the whole document, and
catching a disagreement after players have accounts means a migration.

(Google is different and simpler: Google's discovery document returns
`"subject_types_supported": ["public"]` — confirmed live today — so `sub` is a **global** Google
account identifier, identical across every client in every Cloud project. One account works on phone
and desktop for that reason, and moving Cloud projects would *not* fork accounts. The flip side: it
identifies the same human to every app using Google Sign-In, so never put it in a URL or a log.
Google specifies a maximum length of 255 characters and says *"always use the sub field as the
unique-identifier key for the user"* — store it as `VARCHAR(255)`.)

---

# If you stop half-way

Nothing here is left in a broken state by stopping, with one exception. Where you are:

| Stopped after | State | Safe to leave? |
|---|---|---|
| 7, having deleted the parking records | Apex serves a GitHub 404 | Yes, but ugly. Finish 9–10, or re-add the parking A record. |
| 7, having added only the TXT rows | Nothing changed for visitors | Yes — this is the 30-minute path's stopping point. |
| 12 | App ID advertises a capability the app does not request | Yes. Nothing requests it. But the next `main` archive re-signs — see step 13. |
| 15 | Services ID points at a host that does not resolve | Yes. Nothing calls it; Apple does not re-check. |
| **19, before step 38** | **A one-shot, unrecoverable key exists in exactly one place** | **No. This is the one. Finish 38 before you close the laptop.** |
| 22 | An endpoint registered for a server that does not exist | Yes. Nobody has signed in, so there is nothing to notify. |
| 32–34 | Five OAuth clients nothing uses | Yes — but the six-month auto-delete clock starts now. See the irreversible list. |
| 36 | A session key on disk that no server reads | Yes. Freely regenerable anyway. |
| 41 | No `gcloud`, no billing, no Secret Manager | Yes. Part 6 is groundwork, not a dependency of anything above it. |

---

# What you do **not** need to do

Each of these appears in guides and none of it applies here.

- **`apple-developer-domain-association.txt`.** Apple's current help page: *"You don't need to upload
  a file on your server to complete the registration process for domains and subdomains."* Listing
  `api.oltre.space` in the Services ID configuration is the whole of it. Contingency in step 16.
- **Apple's Private Email Relay Service, and any SPF/DKIM work.** That service exists so you can
  *send* mail to a user's `@privaterelay.appleid.com` address. Your server sends no email and stores
  only `sub`.
- **Requesting Apple's `name` or `email` scopes.** Omit `scope` from the authorize request entirely.
  Those are returned exactly once, on a user's first-ever authorisation, and never again — so a bug
  that drops them is unrecoverable for that user. Omitting them sidesteps that *and* removes any need
  for relay configuration.
- **Google OAuth app verification review.** Mandatory only for sensitive or restricted scopes. Your
  three are all non-sensitive.
- **Google test users.** See step 28 — they buy nothing for this scope set and burn a permanent slot.
  Add one only as a diagnostic if a real tester is refused.
- **Publishing the Google app**, unless you want brand verification. Nothing functional changes; see
  step 52 for why Google's Android docs make it sound otherwise.
- **A second Apple key.** One key signs client secrets for both `client_id`s because the Services ID
  is configured against the primary App ID.
- **Regenerating provisioning profiles by hand** after step 12 — *probably*. Automatic signing and
  Xcode Cloud are expected to handle it; step 13 says how you find out if they did not.
- **Anything Play Console.** No listing exists, so Play Data Safety and Play's mandatory web account
  deletion URL do not apply. They become obligations only if Android distribution moves off GitHub
  Releases — and if it ever does, choose *"Provide a copy of your app signing key"* in Play App
  Signing so SHA-1 `24:AA:53:…:98` survives and client 3 keeps working. Letting Google generate a new
  key instead means every Play install fails until a third Android OAuth client is registered from
  Play Console → Protected with Play → Play Store distribution → Play app signing.
- **Adding anything to GitHub secrets today.** See step 41.

---

# Irreversible and destructive — the list

1. **The `.p8` downloads once.** No recovery. Revoke-and-replace is the only path.
2. **Enabling the capability invalidates existing provisioning profiles** for `dev.fardavide.oltre`
   — Apple states this outright. Expected to be costless because signing is automatic, but the first
   thing that exercises it is an Xcode Cloud archive, i.e. a TestFlight publish. Step 13.
3. **Turning the Sign in with Apple capability back off is destructive.** Apple: *"Turning off the
   Sign in with Apple capability will reset any saved configurations."* (Grouping itself is *not*
   one-way — Apple documents an **Ungroup all apps** control and says *"Ungrouping a group of apps
   will convert each grouped App ID to a primary. Authentication will continue to function"*, only
   recommending a new Authentication Key per app afterwards. The genuinely one-way fact is the one
   step 14 states: a grouped App ID cannot itself group further identifiers.)
4. **Binding Google Sign-In to the release keystore** upgrades that key from "needed for updates" to
   "needed for anyone to sign in". Steps 5 and 38 exist for this.
5. **Google client secrets are shown once**, last four characters thereafter, two per client.
6. **Secret Manager replication locations are fixed at creation.**
7. **A Google test user permanently consumes a slot** — *"a test user consumes a project's test user
   quota once added to the project"*, and removing them does not return it.
8. **OAuth clients cannot move between Cloud projects**, and an Android client ID is bound to
   package + SHA-1, so re-issuing means a new app release.
9. **A Google Cloud project ID cannot be reused** — *"it cannot be in use or previously used; this
   includes deleted projects"*. And **a package + SHA-1 pair is globally unique** across all Google
   Cloud and Firebase projects.
10. **Google auto-deletes OAuth clients** with no credential or token request *and* no configuration
    change, programmatic or manual, for six months — email warning 30 days before, restorable within
    30 days after deletion, permanent thereafter. Provisioning all five today is fine; touching the
    config counts as activity, but do not let the identity slice slip a year.
11. **A secret pushed to a public repository cannot be un-pushed.** GitHub keeps unreachable commits
    reachable by SHA and forks keep their own copies, so a history rewrite is cleanup, not
    remediation. **Revoke first, clean second.** Step 1 is the net that stops this; this entry is
    what to do when a net fails.

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
  none can be missed; failure surfaces at deploy via step 45's self-check while the old revision
  keeps serving. The p8 is already on the server, so a long-lived secret buys nothing.
- **B — a scheduled GitHub Action writing a new Secret Manager version.** Fails silently and all at
  once. GitHub disables scheduled workflows *"when no repository activity has occurred in 60 days"*;
  notifications for scheduled workflows go *"to the user who last modified the cron syntax in the
  workflow file"* — which on a repo with one committer is you, and on any other repo is not
  necessarily whoever broke it; and an env-var-mounted secret does not pick up a new version without
  a redeploy, so a job that "succeeded" can have changed nothing.
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
| **Apple Developer Program membership** | **Annual. Expiry invalidates certificates and profiles, stops TestFlight reaching testers, and makes the App ID and Services ID configuration unusable. Takes the whole Apple half down at once.** |
| **`oltre.space` registration** | **Expires 2027-08-25. Auto-Renew on (step 7). Loss of the zone is account takeover, not downtime.** |
| Apple `.p8` key | No expiry. Rotate by creating a second key (max two per primary App ID), deploying it, confirming step 45's self-check passes on the new one, **then** revoking the old. That order is the difference between a rotation and an outage. |
| Apple authorisation code | Single-use, five minutes. A replayed code returns `invalid_grant`. |
| Google brand verification result | 7 days to press *Publish branding*, else re-verify. |
| Google OAuth client | Auto-deleted after 6 months of no use and no config change; restorable within 30 days. |
| Google access token | 3600 s; irrelevant, you use the ID token. |
| Android debug certificate | Machine-local and regenerable; a new one needs a new Android OAuth client (step 33). |
| Oltre session-JWT key | No expiry. Freely rotatable; cost is everyone signs in again. |

---

# The values, and what each looks like

| Value | Looks like | Secret? | Where it lives |
|---|---|---|---|
| Apple Team ID | `A7Q83J6LR4` | No | Already in `iosApp/project.yml`; `apple.env`; JWT `iss` |
| Apple Key ID | 10 chars, e.g. `ABC123DEFG` | No | `apple.env`; JWT `kid`; also in the p8 filename |
| Apple `.p8` | `AuthKey_<KEYID>.p8`, ~250 B, `-----BEGIN PRIVATE KEY-----` | **Yes, one-shot** | `~/Documents/Keys/Oltre/identity/` 0600, `~/Documents/Keys/Oltre/identity/`, the DMG, Secret Manager `oltre-apple-signin-p8`, mounted at `/secrets/apple/signin.p8` |
| p8 public-key SHA-256 | 64 hex chars from `… | shasum -a 256` | No | `README.md` in both copies — the thing that verifies a restore |
| Apple Services ID | `dev.fardavide.oltre.signin` | No | `apple.env`; the `client_id` in every Android/desktop browser URL |
| iOS bundle identifier | `dev.fardavide.oltre` | No | Repo; the `client_id` for the native iOS flow |
| Apple client secret JWT | ES256, `exp = now + 300` | Derived, never stored | In-memory only, in the body of the POST to `/auth/token` |
| Google account + project ID | an address; `oltre-506614` | No | `google.env` — nothing else records them |
| Google Web client ID | `NNNNNNNNNNNN-xxxx.apps.googleusercontent.com` | No | `google.env`; `setServerClientId` on Android; `serverClientID` on iOS; audience #1 |
| Google Web client secret | `GOCSPX-…` | **Yes, one-shot** | `google-web-client.json` + backups. Unused by the JWKS design |
| Google iOS client ID | same shape | No | `GIDConfiguration(clientID:)` |
| Reversed iOS client ID | `com.googleusercontent.apps.NNNNNNNNNNNN-xxxx` | No | `iosApp/project.yml`, under `CFBundleURLTypes` → `CFBundleURLSchemes` |
| Google Android release client ID | same shape | No | Never named in code — the client binds package + fingerprint |
| Google Android debug client ID | same shape | No | Same |
| Google Desktop client ID | same shape | No | Embedded in the desktop binary; audience #2 |
| Google Desktop client secret | `GOCSPX-…` | Nominally, in practice public | Ships in the binary; PKCE is the real protection |
| Session-JWT signing key | 64 random bytes, base64, one line | **Yes** | `session-jwt.key` + backups + Secret Manager `oltre-session-jwt-key` |
| Neon connection string | `postgres://…` with password inline | **Yes** | Secret Manager `oltre-database-url` only, when it exists |

---

# The five facts the client and server code must be built on

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
Google can: it bounces through `https://api.oltre.space/v1/auth/apple/callback`. Fact 5 is how the
result gets back.

**2. The server accepts two audiences per provider, as an allow-list, and checks `azp` and `nonce`
as well.**

```
Apple  aud in { dev.fardavide.oltre, dev.fardavide.oltre.signin }
Apple  iss  = "https://appleid.apple.com"
Google aud in { WEB_CLIENT_ID, DESKTOP_CLIENT_ID }
Google azp in { WEB, IOS, ANDROID_RELEASE, ANDROID_DEBUG, DESKTOP }   // all five of your own
Google iss in { "https://accounts.google.com", "accounts.google.com" }   // both spellings are issued
```

Android and iOS both yield `aud = WEB_CLIENT_ID` (with `azp` carrying the platform client ID), which
is precisely what `setServerClientId` / `serverClientID` are for. Omit `serverClientID` on iOS and
`aud` silently becomes the iOS client ID and every iOS login is rejected by a server that looks
correct.

**`aud` alone is not enough.** Google's `azp` is *"only needed when the party requesting the ID token
is not the same as the audience"* — which is exactly your situation on every platform. A Google ID
token minted for somebody else's app that names your Web client ID as *its* server client ID arrives
with `aud = WEB_CLIENT_ID` and an `azp` that is none of your five, and it passes an `aud`-only
allow-list. Check `azp` against your own set.

**And bind a nonce.** Google describes it as *"a random value generated by your app that enables
replay protection"*; Apple's authorize request takes one too. Generate it per attempt, pass it in,
verify it round-trips in the ID token, and reject a repeat. Without it a captured ID token replays
for its whole validity window.

**3. Two signing algorithms in one integration.** Apple signs its ID token with **RS256**; your Apple
client secret is signed **ES256**. Google's discovery document advertises RS256 only. Pin each
verifier to its own algorithm explicitly and never read `alg` from an incoming token header. Both
providers rotate their JWKS and return several keys at once, so select by the token header's `kid`
and re-fetch on an unknown one. Cache the JWKS; and do not call Google's `tokeninfo` endpoint in
production — Google flags it as debug-only.

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
out-of-band flow (`urn:ietf:wg:oauth:2.0:oob`) is **no longer supported**, not merely deprecated, and
directing a Google OAuth request into an embedded webview returns `disallowed_useragent` — shell out
to the real system browser.

And on Android, `GetGoogleIdOption` with `setFilterByAuthorizedAccounts(true)` throws
`NoCredentialException` for a user who has never consented. Catch it and retry with `false`, or use
`GetSignInWithGoogleOption` for the explicit button. Without that fallback the first tap on a fresh
device does nothing visible — which is the exact defect this project forbids shipping.

**5. The desktop Apple callback hands back a code, never a session.** This is the riskiest edge in
the whole integration and it has no framework to fall back on, because the desktop client has no
registered redirect of its own. Specify it now:

- The app generates a per-attempt `state` (128 bits of entropy) and a `handoff_verifier`, keeps both
  in memory, and sends **only `state`** to Apple in the authorize URL.
- `POST /v1/auth/apple/callback` receives `code` + `state`, **rejects any `state` it did not issue**,
  exchanges the code with Apple, mints the session, and stores it server-side keyed by
  `SHA-256(handoff_verifier)` — single use, TTL 120 seconds.
- The browser renders a plain "you can return to Oltre" page. **No token in the URL, no token in the
  fragment, no token in the page.**
- The desktop app calls `POST /v1/auth/desktop/claim` over TLS with `state` and the raw
  `handoff_verifier`; the server returns the session exactly once and deletes it.

The two obvious inventions — redirecting to whatever `http://127.0.0.1:PORT` the request names, or
printing a token for the user to paste — hand the session to any local process, or to any page that
can guess a port. Neither is acceptable, and both are what gets written if this is left as "hand the
result back to the app".

---

# Feedback

This document is meant to be corrected. When something is wrong, missing, or assumes a step you
could not follow, say so and it gets fixed here — including the parts taken from Apple's and
Google's own documentation, which goes stale often enough that a portal disagreeing with this file
is a finding worth recording rather than working around silently.

Most likely to be wrong, in order:

1. **Portal wording and button placement.** Both companies reorganise. The paths are current as of
   2026-08-25; the shape of what you are doing outlasts the labels.
2. **Step 28, adding no test users.** Verified from Google's documentation and doubted by one
   reviewer. The escape hatch in that step is the detector — if a tester is ever refused, the
   exemption is not applying and the step is wrong.
3. **Step 16, the domain association file.** Apple's current help page says it is not needed; many
   third-party guides disagree. If the portal asks for it, that is a real finding.
