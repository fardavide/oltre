# Store launch — the live to-do

The working checklist for putting Oltre on the **App Store** and **Google Play**, and then putting
the two store buttons on the README and on oltre.space beside a smaller APK link.

**How this file is used.** One line per item, each owned by either *Davide* (a portal, an account, a
payment, a decision) or *the build* (a repository change). Tick as we go; nothing is ticked on the
strength of "should work". Delete the file when the two listings are live and the buttons are up —
anything worth keeping moves into `identity-provisioning.md` or `decisions.md` first.

Status legend: `[ ]` open · `[~]` in progress · `[x]` done · `[!]` blocked on a decision.

**Order, Davide 2026-09-16: the App Store first.** Play Console would not load, so registration moves
to 2026-09-17; §4 and the two pages in §5 run ahead of §2 and §3. The public contact address is
`oltre@fardavide.dev` on Davide's own domain — pending his confirmation, and named in exactly one
place in the generated pages so it is one edit if it changes.

---

## 0. What is already true

Facts read out of the repository on 2026-09-16, so no step below re-establishes them.

- Apple Developer Program membership exists — team `A7Q83J6LR4`. Xcode Cloud archives every `main`
  commit to **TestFlight internal testing**, so an App Store Connect **app record already exists**
  for `dev.fardavide.oltre`. Read off App Information on 2026-09-26: Name **Oltre**, SKU
  `OLTRE-IOS`, Apple ID **`6798498388`**, primary language English (U.S.), standard licence
  agreement. So the App Store button's URL is `https://apps.apple.com/app/id6798498388` — it
  resolves only once the app is live.
- Where the data lives, checked 2026-09-26 for the privacy page: Neon project `Oltre` in
  `aws-eu-central-1` (Frankfurt) with **6 hours** of point-in-time history, so a deleted account
  survives in restore history for at most 6 hours; Cloud Run in `europe-west1`; Cloud Logging's
  `_Default` bucket (request logs — IP address, user agent) keeps **30 days**. No analytics, crash
  reporting or push SDK in the client.
- Android ships as a **signed APK on GitHub Releases** (`release-android.yml`), from the keystore in
  `~/Documents/Keys/Oltre/` whose SHA-1 is bound to the Google Sign-In Android OAuth client.
- One identifier across both stores: `dev.fardavide.oltre`. Version `0.25.1`, `targetSdk 36`,
  `minSdk 26`.
- **In-app account deletion ships** (`AuthEndpoints.deleteAccount`, Settings → Delete account), so
  guideline 5.1.1(v) is satisfied. **Apple token revocation does not** — nothing calls
  `/auth/revoke` yet, and it is tracked as [#132](https://github.com/fardavide/oltre/issues/132).
  Apple has asked for it since June 2022 of any app offering deletion and Sign in with Apple, so it
  is a pre-submission item in §4. *(Corrected 2026-09-26: this line first said revocation shipped.)*
- **Sign-in is mandatory to play** — *"No colony is created until you are in"*. That pulls in the
  reviewer sign-in note (Apple 2.1(a)), Play Data Safety, and Play's web account-deletion URL.
- **The roster shows typed commander names and typed alliance names to strangers**, and moderation
  was deferred with an explicit trigger: *"Its trigger is an App Review round, not a sprint"*
  (`alliance-sheet.md` §6, §"outside the epic"). This submission is that round. See §1.
- No privacy policy page and no terms page exist. oltre.space is one generated `index.html`.

---

## 1. Decisions — Davide's, taken 2026-09-16

- [x] **UGC moderation ships before the App Store round.** A small slice — report a name, block a
  member, a name filter, published developer contact — rather than hiding typed names or accepting
  a guideline 1.2 rejection. Scoped in §7.
- [x] **The build writes the privacy policy and the deletion page**, Davide reviews before they go
  up. Not a generator: a generated policy describes an app that collects analytics and serves ads,
  neither of which is true here, and a false privacy label is itself a rejection.
- [x] **First Play upload by hand.** The build produces a signed AAB; Davide uploads it. Automation
  is a follow-up and cannot run before a first release exists anyway.
- [x] **Play registers as a personal account, and the closed test is decided later.** Davide,
  2026-09-16. So production access on Play is gated on a 12-tester / 14-day closed test that has not
  started — everything else (listing, AAB, App Store, moderation, the site) proceeds regardless, and
  **Play production is the last thing that can go live**, not the first. The $25 registration is
  still unpaid; the account does not exist until it is.

  The fork as it stood, kept because the next app faces it too:
  - **Personal** — instant, and then **12 testers opted in continuously for 14 days** before
    production access. The 14-day clock can run under everything else, so it costs two weeks of
    calendar, not two weeks of work.
  - **Organization** — exempt from the tester requirement, but needs a legal entity and a D-U-N-S
    number, and verification runs 2–4 weeks (longer if the D-U-N-S has to be requested first).

  Whichever is chosen, **register today**: on the personal path the clock starts only once a closed
  test is live, and the App Store half proceeds in parallel either way.

---

## 2. Google Play — the account and the irreversible choice

- [~] Davide: the Google account for it exists; the **$25 registration is not paid**, so there is no
      developer account yet. Pay it, choosing the type §1 settles, and note the creation date.
- [ ] Davide: create the app — *Oltre*, game, free, and record the default language.
- [ ] **Play App Signing: choose "Provide a copy of your app signing key" and upload the existing
      keystore.** Not the default, and irreversible. Letting Google mint a new key changes the SHA-1
      the Android OAuth client is bound to, and **every Play install fails to sign in** until a third
      Android OAuth client is registered. `identity-provisioning.md` records this trap verbatim.
- [ ] Build: add a signed **AAB** to the release path — Play takes an App Bundle, not the APK the
      GitHub release publishes. `:androidApp:bundleRelease`, same signing config.
- [ ] Davide: upload the first AAB to **internal testing** and install it from Play on his own phone.
- [ ] Verify **Sign in with Google still works from that Play-installed build** — the one check that
      proves the signing-key choice landed right. A failure here is the SHA-1, nothing else.

## 3. Google Play — the listing

- [ ] Store listing: short description (80 chars), full description (4000), app icon
      (`art/icon/android/play-store-512.png`, already generated), **feature graphic 1024×500 — does
      not exist yet, the build generates it from `art/icon/threshold.svg`**.
- [ ] Phone screenshots (2–8). The Roborazzi baselines are 393×852; Play wants each side between 320
      and 3840 px, so they are rendered onto a 1080×1920 canvas rather than uploaded raw.
- [ ] Tablet screenshots — only if the listing declares tablet support.
- [ ] **Data safety** form: account identifier (`sub`) collected, linked to the user, not used for
      tracking, purpose App Functionality — the same answers as the Apple App Privacy label, which
      `identity-provisioning.md` §51 already decided.
- [ ] **Account deletion URL** on the data safety form — the web page from §5.
- [ ] Content rating questionnaire (IARC), target audience, ads declaration (none), news (no),
      government (no), financial features (none).
- [ ] Privacy policy URL from §5.
- [ ] Closed test with 12 testers for 14 days — **required, and deliberately not started yet** (§1).
      Nothing else waits on it; Play *production* waits on nothing else.
- [ ] Apply for production access, then release to production.

## 4. App Store — the listing and the review

- [ ] Davide: in App Store Connect, open the existing `dev.fardavide.oltre` record and fill the **App
      Store** tab (TestFlight has never needed it).
- [ ] Screenshots: **6.9-inch iPhone** (1320×2868) is required, and because
      `TARGETED_DEVICE_FAMILY` is `"1,2"` a **13-inch iPad** set (2064×2752) is required too. Apple
      scales these down for every smaller device. Captured from the simulator, not from Roborazzi.
- [ ] Name, subtitle, keywords, description, promotional text, category (Games → Strategy), support
      URL, marketing URL, copyright.
  - [x] Name: **Oltre** (reserved with the record).
  - [x] Subtitle: **`Online space colonization`**. Davide chose it on 2026-09-26. It's in OGame's
        register without naming it, with *online* added at his request. It uses US spelling
        because the primary language is English (U.S.), and search does not match across
        spellings. Saved.
  - [ ] Keywords must carry **`strategy`**, which the subtitle gave up for *online*. Subtitle and
        keywords are indexed together, so a word in one is wasted in the other.
  - [x] Category: Games, subcategories Strategy + Simulation, no secondary category. Saved.
  - [x] Content Rights: *No* third-party content. Saved.
- [~] **Age rating: 4+.** Every answer below was checked against the code on 2026-09-26: no combat,
      weapons, chat, ads, rankings or web view in any player-facing string.
  - Parental Controls No · Age Assurance No.
  - Unrestricted Web Access No: the Google sign-in sheet is an auth session, not browsing.
  - **User-Generated Content Yes.** Typed commander and alliance names are shown to strangers.
    Declaring it costs nothing at 4+, and hiding it would contradict the moderation slice.
  - **Social Media No.** Davide decided this on 2026-09-26: there's no feed, only exact search
    by name. *Yes* would force 13+ and a Social Media descriptor.
  - Messaging and Chat No · Advertising No.
  - Every Mature Themes, Medical, Sexuality and Violence item: None / No.
  - Gambling No · Simulated Gambling None · Contests None · Loot Boxes No. Probe results are
    random but never bought.
- [~] **App Privacy.** Data is collected: **Yes**. Two data types, each **App Functionality** only,
      **linked** to the user, **not** used for tracking:
  - **Identifiers → User ID.** This covers the provider `sub`, the surrogate account id, and the
    commander name, since Apple's definition includes *"screen name, handle"*. This is §51 of
    `identity-provisioning.md`.
  - **User Content → Gameplay Content.** The colony save plus alliance names, seats and
    contributions. Apple's definition names *"saved games"* and *"user-generated content
    in-game"*. §51 missed this one.
  - Not declared: email, which is verified in the token and discarded, and no device ID, since no
    IDFA/IDFV/ANDROID_ID is read. Also no diagnostics, crash data, usage data or location.
    Cloud Run request logs (IP, 30 days) are infrastructure logging; they are stated in the
    privacy policy and not filed as a data type.
  - [ ] Privacy Policy URL `https://oltre.space/privacy.html`, which resolves once §5 merges.
- [ ] **Apple token revocation, [#132](https://github.com/fardavide/oltre/issues/132)**, lands
      before submission.
- [ ] **Sign-In Information**: the app requires sign-in, so the field is mandatory. The server creates
      an account on first sign-in, so a reviewer signing in with their own Apple Account works — say
      exactly that in the notes, or a rejection under 2.1(a) is the default outcome.
- [ ] Review notes: the game is asynchronous, so nothing visible happens in the first minute by
      design. Say so, and say what to tap.
- [ ] Pick the build already on TestFlight, submit, answer the review.

## 5. The two web pages both stores demand — the build's

- [x] `site/static/privacy.html` is drafted on branch `store-launch-pages`. Every claim is checked
      against `schema.sql`, the live Neon project, the Cloud Logging buckets and the client (no
      analytics/push SDK, local notifications only). It deliberately says nothing about Apple
      revocation until [#132](https://github.com/fardavide/oltre/issues/132) lands.
- [x] `site/static/delete-account.html` is drafted: the in-app steps, what goes and what stays, and
      an email route for someone locked out. Oltre stores no email, so that route asks for a
      commander name and alliance rather than promising a lookup that cannot exist.
- [x] Both are static files, copied verbatim by `build_site.py`, and linked from the landing page
      footer with the contact address. A local build writes them and resolves every footer link.
- [ ] Davide reads both. Two claims are his to confirm: he is the **controller**, and the page
      says Oltre is **not directed at children under 13**, which Play's target-audience form will
      ask about too.
- [ ] Merge, which also cuts a TestFlight build, since every `main` commit does. Then paste the URL
      into App Privacy.

## 6. The buttons — README and oltre.space

Blocked until the two listings are live; a store button that leads nowhere is a dead control.

- [ ] `site/index.html`: two primary buttons — **App Store** and **Google Play** — and the APK
      demoted to a smaller text-only button with a background. The APK's version, size and URL stay
      derived from the published release; the two store URLs are constants.
- [ ] Store badges: Apple's and Google's official badge artwork, both of which carry brand rules
      about size and clear space. Committed under `art/`, copied in by `ASSETS` in `build_site.py` so
      a missing file fails the build rather than the page.
- [ ] `README.md` §Install: the same three, in the same order.
- [ ] A screenshot test or a build assertion is not available for the site; check the rendered page.

---

## 7. The moderation slice — the build's

Decided in §1. It is the compliance surface `alliance-sheet.md` §6 deferred, and both stores want the
same four things of an app that shows one player's typed text to another.

- [ ] **A name filter** on what can be typed — commander name and alliance name, on the server, so an
      old client cannot route around it.
- [ ] **Report** — a member on the roster, and an alliance in search results. It has to reach
      somewhere a human reads.
- [ ] **Block** — a blocked commander's name and alliance stop being shown to the blocker.
- [ ] **Published developer contact** — an address on oltre.space and in the App Store listing, which
      the privacy page in §5 needs anyway.
- [ ] Design round trip for the report and block faces, per `.ai/rules/session-roles.md`.
- [ ] Balance/design calls that are Davide's, collected before the slice starts rather than during.

---

## 8. Notes for a reusable flow

Kept in [`store-launch-notes.md`](store-launch-notes.md), on Davide's ask (2026-09-16): every step
that was friction, every thing that needs double-checking, and every candidate for a script, a CI
job or a skill — so publishing the *next* app is not this document again. What the notes become is
decided at the end, not now.
