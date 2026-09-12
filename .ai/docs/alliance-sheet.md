# Alliance decision sheet — 0.23

Opened by the build, 2026-09-05, on Davide's ask: *"I wanna add an Alliance feature. I should be
able to create an Alliance, for a price. I can name it. The alliance got a level (help me decide
based on what). Users can share resources in the alliance (help me decide what and how). I can see
alliance members and their level. I can search an alliance by name."*

Eleven calls were taken the same day and are recorded in §0. Everything else in this file is an
argument, a proposal or a flag, in the shape [`galaxy-sheet.md`](galaxy-sheet.md),
[`adaptation-sheet.md`](adaptation-sheet.md) and [`profile-sheet.md`](profile-sheet.md) already use.
**No number below is decided**; the balance round is slice 9 (§8).

---

## 0. What is settled, and by whom

| | Settled | By |
|---|---|---|
| Sharing is a **shared treasury**, not member-to-member gifts. Nothing is withdrawable | yes | Davide, 2026-09-05 |
| The level runs on **its own alliance experience**, separate from the player's | yes | Davide, 2026-09-05 |
| Alliance XP is paid by **contributions and by finished projects** | yes | Davide, 2026-09-05 |
| A perk begins for a member **at that member's next check-in**, never backdated | yes | Davide, 2026-09-05 |
| **Production perks are on hold** — *"let's see how the Alliance feature goes, and I will see organically"* | yes | Davide, 2026-09-05 |
| The level buys **capacity and treasury projects** in v1; §4.3 is the recommendation waiting for the day perks reopen | yes | as above |
| A roster **shows commander names**, and moderation ships later | yes | Davide, 2026-09-05 |
| Joining is **request, founder approves** | yes | Davide, 2026-09-05 |
| There is an **admin role** between founder and member, and succession runs *admin → best contributor → and they must be active* | yes | Davide, 2026-09-05 |
| The level runs on the **total only** — no per-member average beside it | yes | Davide, 2026-09-05 |
| Where the alliance lives in the UI | **Claude Design's**, from the round trip | Davide, 2026-09-05 |
| The price, the gate, the ladder, the caps | open — the balance round | §5, §9 |
| Inactivity for succession is **30 days without a colony sync** | yes | Davide, 2026-09-12, issue #138 |
| A founder replaced by succession becomes an **admin**, with no automatic restoration | yes | Davide, 2026-09-12, issue #138 |
| An admin may **remove members only**, never another admin or the founder | yes | Davide, 2026-09-12, issue #138 |
| The lifecycle uses a **temporary cap of 20 members**, including founder and admins | yes | Davide, 2026-09-12, issue #138; curve deferred to balance |
| A pending join request must be **withdrawn before founding or petitioning a different alliance**; retrying the same petition preserves it | yes | Davide, 2026-09-12, issue #138 |

---

## 1. The five facts that decide the shape, before any design

Each is a property of the repository at 0.22.0, not a preference. They rule out the obvious version
of this feature, so they go first.

### 1.1 Every player is in their own galaxy

`Genesis.kt` mints `galaxySeedFor(player, at)` per account, deliberately: *"two people signing up
inside the same millisecond would otherwise open in the identical galaxy and never know it."* There
is no shared map, and `#106` §11 lists *shared galaxy* as an explicit non-goal.

**So the OGame reading of an alliance — people who live near each other and defend each other's
coordinates — is not available and cannot be faked.** An alliance here links separate universes.
Nothing can be flown between them, nothing defended, and no member can see another member's world.
Whatever it is, it is economic and social rather than spatial. Everything in §2 follows from this
one sentence.

### 1.2 A colony is one JSON document with one writer

`colonies` is one row per player — `snapshot_json` plus an optimistic `version`. The whole
concurrency design is compare-and-set on the assumption that one device writes one colony. **A
transfer is the first write in this game that touches two colonies**, and writing another player's
row synchronously is the one thing that scheme was built not to do.

The shape that keeps it, and it is the same shape the perks need in §4.2: **a debit on the giver, and
a durable row the receiver drains at their own next sync.** One writer per colony row, at-least-once
delivery, and the arrival lands where everything else in this game lands — on the way in, as
something that happened while the app was closed.

### 1.3 Anything that adds a field to `GameState` is a wire break

`Protocol.json` is `Json { encodeDefaults = true }` with **no** `ignoreUnknownKeys`, and
`RequiredFieldsTest` pins that nothing on the wire has a default. `SyncResponse` carries
`GameSnapshot` whole. And `GameSave.decode` refuses a save *newer* than it reads —
`if (from > SCHEMA_VERSION) return null` → `Obsolete`.

**So a new field in the colony is a response the build already on somebody's phone cannot decode.**
Not a compatible addition, and not covered by the migration ladder — the ladder carries a save
*forward*, and this is a client handed something from the future. A **new route** costs none of it,
which is the argument `profile-sheet.md` §3 already made and won.

> **This is the biggest cost driver in the feature**, and it splits the epic in two:
>
> | | Touches `core` | Cost |
> |---|---|---|
> | The alliance exists — create, name, tag, search, join, roster, level | **no** | new tables, new routes, a client module, a design round trip |
> | Resources move | **yes** | a `core` mutating function, a thirteenth `ClientVerb`, a new `Event`, a save schema hop (18 → 19), an `ApiVersion` bump |
>
> **And the second half is cheapest right now, for a reason that has a clock on it.** The cutover
> already happened — `#113` shipped it and 0.22.0 is on TestFlight taking the service as its source
> of truth — so a schema hop is *already* a break for an installed build. What makes it cheap is
> **who is installed**: internal testers, which is to say Davide. The hop means everybody updates,
> and today everybody is one person.
>
> At public launch the same hop needs a server that can serve two snapshot shapes, which nothing here
> can do and which is a slice of its own. **`#114` is not what gates this** — it is the debug
> time-skip endpoint, the one-time upload of a pre-existing local save, the version bump and a
> rehearsed rollback — and the alliance's `core` work neither waits for it nor blocks it.

### 1.4 There is no push, by decision

`#106` §1: *"Do not let a design drift into push."* `#120` then found the one thing local
notifications cannot do, named a push server as the only real fix, and it was still not built.

**An alliance is the second petitioner and should not be granted either.** The consequence is a
design constraint rather than a gap: **nothing here may be time-critical.** No invite that expires,
no auction, no window to respond in. Everything another player does reaches you at your next
check-in, in the *while you were away* idiom the product is built on. Note that §0's perk-timing call
lands in exactly the same grain.

### 1.5 The tab bar is full

Five destinations, and `profile-sheet.md` §6 refused a sixth in as many words: *"there are five, the
bar is full at 320dp, and a screen unreachable from the tab bar breaks the rule this product keeps
most strictly."* Davide has handed the answer to Claude Design; the candidates are in §7.

---

## 2. What the alliance is for — the consortium

**Davide's call: a shared treasury. Members pay in; nothing comes out.**

The rejected option is worth recording because it is the one a reader assumes was meant. **Direct
member-to-member gifts are the oldest exploit in this genre**, and this game hands it a loaded gun:
a second account is one sign-in away, a fresh colony starts with stock, and there is no combat, no
loss and no risk anywhere to price a transfer against. Every mitigation — ratio caps, taxes,
cooldowns, level-matched limits — is a rule that has to be explained on a screen, and none of them is
airtight.

**A pool defuses it structurally rather than by rule.** If nothing can be withdrawn, feeding an alt
account is impossible because there is no way out. What contribution buys is the alliance's own
progression, which is shared, cannot be extracted, and is exactly the thing the level is for.

**The residual, stated rather than hidden:** contributions pay alliance XP (§3) and the level buys
real production perks (§4), so a ring of alt accounts paying into one alliance is still a way to buy
power — it is just paid for in resources those alts had to earn, at their own economy's rate. That
is a *rate limit* rather than a fix, and the levers against it are the member cap, a per-member
contribution ceiling, and a sub-linear XP curve. It is a balance question, and it is on the list.

---

## 3. The level — a separate alliance experience

**Davide's call: its own XP, paid by contributions and by finished projects.**

So the alliance has an award table of its own, beside `ExperienceBalance`'s. What that table has to
inherit from the player's, because the reasons transfer exactly:

- **Completions pay; starts do not** (`experience-sheet.md` §2). A contribution *is* a completion —
  the resources have left the colony and landed in the pool. Pledging is not a thing that exists.
- **Stored, not folded** (§1 of that sheet, and Davide's rejection of the first cut). `alliances.experience`
  is a column, maintained where the pool is written. Nothing recomputes a level by summing a ledger.
- **Monotonic, always.** Spending the pool on a project must not take XP away, or the level would fall
  for doing the thing the level exists to encourage. Contributions and project completions both add;
  nothing subtracts. A member leaving takes nothing with them either.

### 3.1 The two sources, and how they differ

| Source | Priced on | Why |
|---|---|---|
| A contribution | the game's own **1 : 2 : 3**, the ratio `AdaptationBalance` and every payback in the balance log already use | it is the one honest way to make a basket of three into one number |
| A finished project | a lump, larger than the sum of what it cost | a project is a *decision* the alliance took together; a contribution is only the saving up |

The second line is the whole reason both sources exist rather than one. On contributions alone the
level would track how much an alliance has hoarded; on projects alone, contributing would feel like
paying into a hole. Together the level reads as *what we chose to build*, with the saving as
progress toward it.

### 3.2 The ladder is not the player's ladder

`LEVEL_STEP` is a straight line because a player's experience is **linear in time** — measured, 4,685
points on day one against 4,818 averaged over thirty, while income grows two orders of magnitude
(`experience-sheet.md` §4). An alliance's income is contributions, and contributions come out of
member income, **which compounds**. So the alliance ladder is the case that sheet named as the other
one: *"a geometric ladder is the right shape for a game whose income is the score"* — and here it is.

Expect a geometric or steeply super-linear step, and expect it to need a real roster to fit against —
see §9 on the sim, which cannot produce one.

### 3.3 The total, and only the total — Davide's call

Put to him as total, per-member average, or total with the average printed beside it. **He took the
total alone.**

The rejected readings, recorded because the level will be argued about: a **per-member average** says
*we are good* rather than *we are many* and lets a five-person alliance top the table — at the cost of
making a quiet member a drag on everyone's number, which turns kicking into a strategy. **Printing the
average beside the total** was the middle option and it was refused too: it is a second number on the
screen that exists only to undercut the first one, and a level that needs a footnote is a level that
has not been chosen.

So a thirty-member alliance genuinely out-levels a five-member one, and that is the intended reading:
**recruiting is playing.** What keeps the total from being unbounded is the member cap growing with
the level (§4.1) — the alliance has to level up to earn the seats that let it level faster.

---

## 4. What the level buys

**Davide's call, taken twice on the same day and worth reading in order.** First: treasury projects
*and* production perks — the level should move real numbers in a member's colony, not only grant
seats. Then, on being shown what a perk costs the simulation: ***"let's leave perks on hold, let's
see how the Alliance feature goes, and I will see organically."***

**So v1 buys capacity and projects.** §4.2 and §4.3 stay in this file rather than being deleted,
because the mechanism and the recommendation are the expensive half of that decision and the day it
reopens they should not have to be re-derived.

### 4.1 The two tiers that ship, and the one on the shelf

| | Effect | Cost | Where |
|---|---|---|---|
| **Capacity** | member cap `BASE + level`, vault ceiling, the tag beside a name | alliance tables only | slice 2 |
| **Projects** | the pool buys things the alliance owns — seats, vault, standing | server + a project catalogue | slice 8 |
| **Perks** | a number in every member's colony | **`core`**: a boon field, a schema hop, an `ApiVersion` bump | **held** |

The floor holds regardless: **whatever ships must do something on the day it ships.** A vault you can
pay into that buys nothing is a control that silently does nothing, which the global rule calls worse
than a crash. Capacity plus the roster is the minimum honest effect and it is available in slice 2.

> **The risk the hold creates, stated rather than discovered.** With perks out, an alliance's
> contributions buy seats and standing, and seats and standing are *for* holding more contributions.
> That is a closed loop: the alliance's only purpose is to grow the alliance. It is a real feature —
> a name, a roster, a rank among alliances and a number that goes up are what most of this genre's
> social layer actually is — but it is the **cooperative-scoreboard** version of it, and `#72`
> (*"still an idle, not a strategy"*) is the complaint it is closest to.
>
> Which is exactly why the organic reading is the right one: **the thing to watch on the first
> install is whether contributing feels like a decision or like a donation.** If it reads as a
> donation, perks are the answer already argued below, and the epic is deliberately shaped so they
> drop in as one slice — the schema hop they need is the same one the treasury already spends.

### 4.2 How a perk would reach a pure simulation — the mechanism, banked

This is the part with an architecture in it, and Davide's timing call is what makes it tractable.

`advance` is a function of `GameState` alone. An alliance perk is a number the colony does not own,
so it has to be **written into the snapshot** by the server — one boon field, and the field is what
the schema hop in §1.3 is for. The hard part was never the field; it was *when it starts*, because a
boon backdated to the alliance's level-up instant would change what `advance` computed over hours the
client has already drawn, which is the composability property failing.

**The call — a perk begins at that member's next check-in** — removes the problem rather than
managing it. The server writes the boon when it advances that colony, so the effective instant *is*
the sync. Nothing is rewritten, nothing is recomputed, and `advance(s, t0, t2) == advance(advance(s,
t0, t1), t1, t2)` is untouched.

Two consequences, both worth stating out loud rather than discovering:

- **A member who does not open the app gains nothing until they do.** Read as a defect it is one;
  read against the product it is the check-in loop being rewarded, which is the loop the whole game
  is. The screen should say it plainly rather than let a player work it out.
- **The two rejected options are on the record.** *Backdating* means the server re-advancing a colony
  from before an instant the client already rendered. *A scheduled sweep* over every member's colony
  is a recurring moving part of exactly the kind `#106` §1 refused when it refused push — *"the only
  genuinely expensive moving part in the whole plan"* — and it bills against a free tier. (`#106` says
  that about push and does not mention a sweep; the objection transfers, the sentence is not a quote.)

### 4.3 Which rates a perk would touch — the recommendation, on the shelf

Davide asked the build to decide this one and then held the whole tier. The answer is recorded for
the day it reopens: **build and research speed, alone** — with storage caps as the natural second if
it wants a companion — and **production percentages explicitly not**.

Four candidates, and the argument is mostly about one of them:

| | | Verdict |
|---|---|---|
| **Build and research speed** | a divisor on duration, the grammar `PlaceholderBalance.upgradeDuration(building, toLevel, roboticsFactory, naniteFactory)` already implements and tests | **recommended** |
| **Storage caps** | a bigger ceiling before production is wasted | a good second: it is the perk that is *worth most while you are away*, which is this game's own axis |
| **Fleet cargo and flight time** | the `Prospecting` / `Propulsion` grammar, aimed at the frontier | defer — smaller share of the loop, and `#75` is still open in the window ladder |
| **Resource production %** | a multiplier on mine output | **rejected for v1** |

Three reasons, and the first is the one that decides it:

1. **A duration perk cannot rewrite history, so the timing call is invisible.** `UpgradeJob` and
   `ResearchJob` carry `completesAt`, fixed when the job starts. A boon that divides a duration
   therefore applies to what a player starts *next* and to nothing already running — so *"it begins at
   your next check-in"* needs no explanation at all. Under a production multiplier the same rule is
   the confusing case: a player would ask why the three days they were away did not count, and the
   honest answer is architecture rather than design.
2. **It does not compound into the economy.** A production multiplier stacks on mines, on the
   research branch's own multipliers and on the energy throttle, so a perk that reads as small becomes
   the largest term in a month. That is the trap `experience-sheet.md` §3 already named in another
   costume — a number that *"would quietly become a second resource counter"* — and it is also what
   would make membership mandatory rather than attractive. A wait divisor has a natural ceiling: the
   wait cannot go below zero, and nobody's income changes.
3. **It is felt every single check-in**, which is the whole product. In a game whose loop is *come
   back when it is done*, shortening the wait is the most value per unit of power there is.

What this costs in `core`, stated so the slice can be sized: one boon field on `GameState`, one term
in `upgradeDuration`, one in the research duration that already rides Robotics, and the schema hop
and `ApiVersion` bump that §1.3 makes unavoidable for any of the four.

---

## 5. Founding, joining, and who runs it

### 5.1 The price — proposals, all dials

Davide's ask says *for a price*, which the game has an idiom for: a `Resources` cost on the 1 : 2 : 3.

- **A real commitment, not a formality** — in the neighbourhood of a Hauler or a mid facility level,
  so founding is a week-two decision rather than a day-one one.
- **Joining costs nothing and is gated by a person, not by a number** (§5.2). A gate on *founding*
  keeps the world from filling with one-member alliances; a level gate on *joining* would put the
  progression system in front of the content, which is what `experience-sheet.md` §5 warns against.
- **The gate on founding, if there is one, is the player level.** That would be the first thing the
  player level gates, in its narrowest form — a social opt-in rather than a mechanic — and the
  cheapest place to learn whether gating on level reads as earned or as arbitrary.
- **Disbanding does not refund**, and the screen says so before the tap rather than after.

### 5.2 Joining is a request the founder answers — Davide's call

Put to him as open, request-and-approve, or invite-only. **He took request-and-approve.**

It is also the option this game's constraints were pointing at. With no push (§1.4) an invite is slow
by construction — the invitee learns of it whenever they next open the app — and *open* leaves kick as
the only defence against somebody filling the seats the alliance levelled up to earn. A request
arrives, sits, and is answered at a check-in: **the pending-request list is a reason to open the app,
which is the loop the whole product is**.

**Nothing about it may expire**, per §1.4. A request waits until it is answered or withdrawn.

### 5.3 Roles, and what happens when the founder stops playing — Davide's call

> *"Goes to an admin if any, other best contributor, as far as it's an active player."*

That sentence adds a **role model** the earlier drafts did not have, and it is the right place for it:
an alliance whose only privileged account is the founder is one absence away from a roster nobody can
rename, admit to, or spend.

**Three roles: founder, admin, member.** The founder promotes and demotes admins; admins can answer
join requests and spend the treasury; only the founder can rename or disband. (Which of those an admin
gets is a proposal — the split above is the conservative one, keeping the two irreversible acts with
the founder.)

**Succession, in order:** an **active admin**, longest-serving first; failing that, the **largest
contributor** who is active; failing that, the alliance sits dormant rather than disbanding — nothing
is destroyed for an absence.

**"Active" is the one term here with no definition yet**, and it needs one that is cheap to evaluate:
the natural candidate is *has synced within N days*, which the server already knows —
`colonies.last_updated_at` is denormalised out of the snapshot for exactly this kind of question. **N
is Davide's**, and it wants to be long: succession is not a race, and taking an alliance off somebody
who was on holiday is worse than a fortnight of dormancy.

**Succession runs on a read, not on a timer.** A scheduled sweep would be the recurring moving part
`#106` refused (§4.2); evaluating the rule when somebody opens the alliance is free and is soon
enough for a thing measured in weeks.

---

## 6. Names, tags, search — and the deferral that expires here

- **The name is unique, unlike a commander name.** `profile-sheet.md` §1 settled that two commanders
  may share one; an alliance cannot, or *search by name* and *join that one* have no answer. A
  normalised column — trimmed, case-folded, whitespace-collapsed — under a unique index. The refusal
  is a designed state, because **it is the first refusal in this app a finger can actually reach**
  (status.md, 0.22.0 pending: the profile face has no error state because nothing could refuse it).
- **A short tag beside the name**, 2–5 characters, also unique: what fits next to a commander in a
  roster where the full name does not, and the genre's own convention.
- **Search is a route, rate-limited and paginated.** `RateLimit.kt` already exists. A normalised
  column and a prefix match is enough at this scale; a trigram index is for when there is something
  to measure.
- **Search results carry the alliance, never its members** — name, tag, level, seats used, join
  policy. The roster sits behind membership; a pending request does not grant access.

### Search implementation decisions (2026-09-12, #139)

Davide approved NFKC compatibility normalisation before Unicode case folding and whitespace
collapse. Full-width letters and Roman-numeral letter forms share the ordinary spelling for
uniqueness and search; the display name stays verbatim. Existing stored names are backfilled in one
locked transaction at schema application. A compatibility collision refuses the migration before
any update and names the affected alliance IDs, so resolution never silently chooses a winner.

Search returns 20 results ordered by experience descending, then normalised name and ID ascending
under PostgreSQL `C` collation. Its versioned opaque cursor is bound to the normalised query. Names
are literal prefixes, including `%`, `_` and backslash; no tag or roster search is added. A separate
address-keyed allowance of 60 searches per minute leaves the authentication budget available.

### Roster implementation notes (2026-09-13, #140)

The roster is the caller's own alliance, selected from the authenticated account;
it accepts no alliance or player selector. Its member and petition IDs are opaque
surrogates, and it carries each chosen `PlayerProfile` without exposing account IDs.
Only founders and admins see pending petitions; ordinary members receive the
contract's existing `pending = null`.

Earned experience is stored beside the colony snapshot in the same insert or
compare-and-set update. A roster reads that column, not the growing save history.
Rows predating the column remain SQL null until their next write. Davide asked that
unknown be explicit in the type: the wire currently uses `ExperienceReading.Known`
or `ExperienceReading.Unknown`, preserving known zero as distinct from unknown.
The type and field names remain a review topic in `code-review-log.md`.

Davide clarified that a player starts with a colony. Startup automatically founds
one before opening the game, so `lastSyncedAt` remains a required `Instant`.
A synthetic roster call made before founding the caller's colony receives the
existing `NoColony` error with status 404; it does not introduce another gameplay
state or an invented timestamp.

This slice uses the ticket's proposed default ordering: founder, admins, members;
within a role, longest-serving first, then private account ID under `C` collation
as the stable tie-breaker. The tie-breaker is never sent. Pending petitions are
ordered by request time and the same private ID tie-breaker. This is the ticket's
defensible default, not a new settled gameplay call about ranking members.

> **`profile-sheet.md` §4.3's deferral expires the day this ships.** It reads: *"Whether the name is
> ever shown to another player. Nothing is, yet. The answer changes nothing in this slice and
> everything in the one that adds a blocklist."* An alliance name is user-generated content shown to
> strangers, and a roster shows commander names to people who did not choose them.
>
> **This is an App Store item, not a nicety.** Guideline 1.2 asks an app with user-generated content
> for a content filter, a report mechanism, a way to block an abusive user, and published developer
> contact. It is the surface most likely to fail review, exactly as account deletion was in `#106`
> §7.
>
> **Davide's call, 2026-09-05: names are shown, and moderation ships later.** So the roster reads as
> people from the first release and the compliance slice is a follow-up rather than a gate. The risk
> is accepted rather than absent, and it is worth naming precisely so nobody is surprised by it: **the
> App Review round that first carries a roster is the one that can be rejected for it**, and a
> rejection there costs a release rather than a commit. The cheapest insurance if that ever looks
> close is the fallback in §6's own logic — a roster of marks and levels shows nobody's typed text.

---

## 7. Where it lives — Claude Design's, from the round trip

Davide's call. The prompt carries the four candidates and the constraint, and the frame decides:

1. **A sixth tab.** Refused once at 320dp; would need the bar redrawn, not extended.
2. **A face on `OltreBottomSheet`.** Precedented — `SheetFace` already has six — and probably too
   small for a roster, a search and a treasury together.
3. **Off the player strip.** The strip is already tappable and opens the profile editor; an alliance
   is the other half of *who you are*. Cheapest to draw.
4. **Displacing a destination.** The most honest if the alliance is meant to be a pillar.

The frame is also owed: the create flow and its price, the search face and its empty state, the
roster and what it shows of a member, the contribute control, the project list, the **refusal** face
for a name already taken, and the **held** face for every one of them — the alliance is entirely
server-side, so every control on it is unreachable with no signal.

**Per `session-roles.md`, the local session emits the prompt, waits, and does not open a pull request
for the half it could build.**

---

## 8. How it slices

| # | Slice | Ends with | `core` | Session |
|---|---|---|---|---|
| 1 | **The contract** — `:protocol` alliance types, name/tag value classes, payloads, `ApiError` additions | pure data + tests | no | Cloud |
| 2 | **The alliance exists** — tables, repository, the three roles, create / rename / request / approve / leave / kick / promote / disband, seats, succession | a colony belongs to a group | no | Cloud |
| 3 | **Search** — normalisation, uniqueness, the index, rate limit, pagination | a name finds an alliance | no | Cloud |
| 4 | **The roster** — members and their player levels, denormalised out of the snapshot at sync | you can see who is in it | no | Cloud |
| 5 | **The client's alliance layer** — `:client:alliance:{data,domain}`, offline rule, cache, the `FakeOltreApi` extension | tested against a fake, no UI | no | Cloud |
| 6 | **The design round trip** — §7 | frames | — | Local + Claude Design |
| 7 | **The screens** — `:client:alliance:{ui,presentation}`, shell wiring, baselines, robots | a player founds one and finds one | no | Local |
| 8 | **The treasury** — the contribution verb, a new `Event`, the pool, projects, alliance XP and its ladder | resources move and the level climbs | **yes** | Cloud, then Local |
| 9 | **The balance round** — the price, the gate, the ladder, the caps | numbers with a reason | no | Local + a device |

Two things deliberately outside the epic, each with a trigger rather than a date:

- **The perks** — the boon field, the duration divisor, the next-check-in rule. Held by Davide until
  the feature has been played (§4). One slice when it comes back, and it spends a schema hop slice 8
  has already paid for.
- **Moderation and UGC compliance** — name filtering, report, block, developer contact, the
  privacy-policy delta. Deferred by Davide (§6). Its trigger is an App Review round, not a sprint.

**Suggested grouping into releases**, since a PR here batches a coherent milestone: slices 1–7 are
the alliance existing and touch no `core` at all, so they are one release; slice 8 is the economy and
is the second. That also puts the design round trip's screens in front of a device sooner, which is
where every number in slice 9 has to come from anyway.

Notes belonging to the whole epic rather than one slice:

- **Nothing alliance queues offline, and only one of them is even a verb.** The distinction was got
  wrong in the first draft and is worth stating precisely: `OfflineRule` is a property of
  `ClientVerb`, and **none of the alliance routes is a `ClientVerb`** — they are a second surface
  beside the sync pair, exactly as the profile is (`profile-sheet.md` §3). So there is no arm to add
  to `offlineRule`'s `when` for any of them; with no signal they simply cannot be asked, and the
  screen says so in the held vocabulary. **The one exception is the treasury's contribution**, which
  *is* a verb because it mutates a `GameState` — and it is `LOOK_DONT_ACT`, because its destination is
  an alliance the server may have changed underneath and `core` cannot replay membership.
  `offlineRule`'s `when` has no `else`, so the treasury slice cannot compile without saying so.
- **New routes only, never a field on `SyncResponse`** (§1.3) — the alliance is read beside the sync,
  not inside it, exactly as the profile is.
- **The coverage gate blocks a merge if any number falls.** A large new server surface lands as a big
  denominator; budget the tests inside each slice, per `#106` §8.
- **`:sim` cannot model this.** The harness drives one colony against no opponents; there is no bot
  that joins an alliance and no roster to measure. **This is the first mechanic in Oltre with no
  simulator** — its numbers come from arithmetic and a device rather than a thirty-day run, which is
  the footing the tilt constants were on, with the same expectation that the first install moves them.
- **A new `Event` member has to be priced in `ExperienceBalance.awardFor`**, which names all twelve
  rather than defaulting them. A contribution is a decision, so it plausibly pays the *player* a
  little as well as the alliance — but see `experience-sheet.md` §3, *what you did, not what you own*:
  an award that scaled with the size of a contribution would be an XP tap. Flat, small, or zero.

---

## 9. Open, and every one is Davide's

1. **The price and the gate on founding** (§5.1).
2. **What "active" means** for succession — settled 2026-09-12: a colony sync within 30 days (§0).
3. **Which powers an admin gets** (§5.3) — settled 2026-09-12: answer requests and remove members only;
   rename, disband and setting roles stay with the founder. A replaced founder becomes an admin (§0).
4. **The anti-alt levers** (§2) — member cap, per-member contribution ceiling, sub-linear XP curve, or
   an accepted residual.
5. **The ladder's step and the member cap's curve** — the balance round, and it has no simulator (§8).
