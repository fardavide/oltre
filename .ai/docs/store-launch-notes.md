# Store launch — notes towards a reusable flow

Opened 2026-09-16 on Davide's ask: *"I want you to take notes so we can create a reusable flow to
publish other apps. It could be some scripts, some CI automation, or even only some skills. Remember
the thing that I struggle with and that we need to double-check."*

The checklist being worked is [`store-launch.md`](store-launch.md). This file is the *residue* — what
the next app should not have to rediscover. **What it becomes is decided when Oltre is live**, not
now: a global skill, a script in `.github/scripts/`, a CI job, or nothing.

Three kinds of entry, and each one says which it is:

- **TRAP** — something that looks fine and is not. A wrong default, an irreversible choice, a gate
  that fires late.
- **FRICTION** — something that cost time and could be automated or written down once.
- **CANDIDATE** — a concrete thing to build: a script, a workflow, a skill, a template.

---

## Traps

- **TRAP — Play App Signing's default mints a new key.** Choosing it, rather than *"Provide a copy of
  your app signing key"*, changes the SHA-1 the app is signed with. Any Google Sign-In Android OAuth
  client is bound to package + SHA-1, so every Play install would fail to sign in, silently, with the
  app looking fine. Irreversible in the sense that matters: the fix is registering a third OAuth
  client and shipping a new release. *(Recorded in Oltre's `identity-provisioning.md` before we
  started — the note is what made it cheap. That is itself the argument for this file.)*
- **TRAP — the 12-testers rule is decided by an account type chosen once, at registration.** A
  personal Play account created on or after 2023-11-13 needs 12 testers opted in **continuously for
  14 days** before production access; an organization account is exempt but needs a legal entity, a
  D-U-N-S number and 2–4 weeks of verification. It is a calendar decision made in a dialog most
  people click through in five seconds.
- **TRAP — an app that requires sign-in pulls in three obligations at once**, and none of them is
  obvious from the code: Apple's mandatory Sign-In Information for the reviewer (2.1(a)), in-app
  account deletion (5.1.1(v)), and Play's **web** account-deletion URL on the Data Safety form. A
  login screen is not a feature, it is a compliance surface.
- **TRAP — typed names shown to strangers trigger Apple guideline 1.2** (filter, report, block,
  published contact) and Play's UGC policy. The trigger is the first review round that carries the
  feature, so the cost lands a release after the feature ships.
- **TRAP — Play takes an AAB, the GitHub-release path builds an APK.** A publishing pipeline that
  only ever produced APKs has a hole in it that only shows up at upload time.
- **TRAP — a generated privacy policy describes an app that has analytics and ads.** Publishing one
  that overstates collection is itself a false privacy label.
- **TRAP — the Apple agreements gate is invisible until submission.** TestFlight works with an
  expired or unaccepted Paid/Free Apps agreement; App Store distribution does not. Apple re-issues
  the Program License Agreement periodically and the account silently sits at *Pending* until
  somebody clicks through it in Agreements, Tax, and Banking. Check it **first**, not at submission.
- **TRAP — the public contact address ends up being a personal mailbox.** It goes on a privacy page,
  a deletion page, both listings and (for a UGC app) the address Apple expects report responses from,
  so it has to be per-product and send-capable, not a forwarding alias nobody replies from.
- **TRAP — the age-rating questionnaire changes under you.** Apple overhauled it in 2025 (new bands
  4+/9+/13+/16+/18+) and added Social Media questions in July 2026, **required for any submission
  from September 2026**. Blog summaries lag behind, so read the questions off Apple's own
  *Age ratings values and definitions* page on the day. Two answers set the floor: Social Media
  forces 13+, and Unrestricted Web Access forces 16+. An auth sheet is not a web view.

- **TRAP — a deferred compliance item reads as done.** Account deletion shipped, and the Apple token
  revocation Apple pairs with it was deferred to an issue that stayed open
  ([#132](https://github.com/fardavide/oltre/issues/132)). The checklist first said both had
  shipped. Before filing any store answer, verify each compliance claim against the code with a grep
  (`/auth/revoke`, SDK imports, the schema), not against docs or memory.
- **TRAP — App Privacy has more data types than the obvious one.** A game that keeps a server-side
  save collects **Gameplay Content** ("saved games"), on top of the account's User ID. A screen name
  is a **User ID** in Apple's taxonomy, not a Name.

## Friction

- **FRICTION — store requirements live in eight places**: App Review guidelines, App Store Connect's
  own required fields, Play policy, Play Console's listing form, Data Safety, IARC, the brand
  guidelines for each badge, and the screenshot size tables that change yearly. Nothing assembles
  them per-app.
- **FRICTION — screenshot sizes are a moving target and the sources are SEO blogs.** For 2026: 6.9-inch
  iPhone 1320×2868 required, 13-inch iPad 2064×2752 required when the app targets iPad, Apple scales
  down for everything smaller; Play wants each side between 320 and 3840 px. Committed UI baselines
  (393×852 here) fit neither store raw.
- **FRICTION — the assets a listing needs are not the assets an app needs.** Play's 1024×500 feature
  graphic exists for no other purpose and had to be made from scratch, while the 512×512 icon was
  already generated.
- **FRICTION — the subtitle took three rounds.** It is copy plus search terms in 30 characters.
  What worked was grounding it in a reference game's real store listing (OGame's Play subtitle
  *"Strategy in space: build colonies, research & fight for resources"*). Then treat subtitle +
  keywords as **one search-term budget**: a word dropped from the subtitle moves to keywords rather
  than disappearing. Mind the locale's spelling too: en-US says *colonization*, so a British
  *colonisation* would miss US searches.

## Candidates

- **CANDIDATE — a `store-release` skill**: the ordered checklist with the traps inline, parameterised
  by "does the app have accounts / UGC / iPad support", since those three answers generate most of
  the obligations.
- **CANDIDATE — a screenshot-frame script**: take committed UI baselines, render them onto the exact
  canvases each store demands (and nothing else), fail loudly on a size no store accepts.
- **CANDIDATE — a listing-copy template** in the repository rather than typed into two web forms:
  short description, full description, keywords, review notes — one source, two stores.
- **CANDIDATE — the privacy + deletion pages as a generated pair**, from a small declaration of what
  the server stores, so the page, the App Privacy answers and the Data Safety answers cannot drift.
- **CANDIDATE — `bundleRelease` beside `assembleRelease`** in the release workflow, plus the
  service-account publish step, once the first manual upload has proved the listing.

## To double-check when we get there

- Whether Xcode Cloud has a start condition excluding `site/**`. `decisions.md` says *"a typo fix in
  a privacy policy must not be able to cut a TestFlight build"*, but the template lives on `main` and
  every `main` commit archives. Either the condition exists in the App Store Connect UI or the
  sentence is wrong.
- Whether Apple accepts request logs (IP, 30 days) going undeclared as a data type. Apple has no IP
  category, and the logs are infrastructure logging, but this is a reading of the definitions, not
  a ruling.

- Whether Play still accepts a 9:19.5 phone screenshot, or whether the 1080×1920 canvas is actually
  required.
- Whether Apple's reviewer accepts *"sign in with your own Apple Account"* for an app whose server
  creates the account on first sign-in — the theory is yes, the evidence is a review round.
- Whether the Android OAuth client's SHA-1 survives the Play App Signing upload, proved by a Google
  sign-in from a Play-installed build and nothing less.
- Whether the iOS build number and `MARKETING_VERSION` line up with what App Store Connect will
  accept for a first public release, rather than a TestFlight one.
