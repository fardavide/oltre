# Code review log

Davide's feedback on Oltre's code and architecture, with questions to examine in
future reviews. Open entries are concerns or proposals, not settled decisions.
Record follow-ups under their original ID so the reasoning and outcome remain traceable.

Statuses: **Open** (needs review), **Planned** (an agreed review/change has a plan),
**Resolved** (an agreed outcome has evidence), **Deferred** (deliberately postponed,
with the reason recorded).

## Review queue

| ID | Topic | Status | Next check |
|---|---|---|---|
| CR-001 | Semantic nullability and explicit unknown states | Open | Review the roster types and classify each meaning of absence |
| CR-002 | Experience type and field names | Open | Review the current signature and representative callers together |
| CR-003 | Arrow `Either` adoption | Open | Prepare a scoped evaluation and adoption plan before adding the dependency |
| CR-004 | Architecture and adherence to directives | Open | Walk one feature through its modules and compare it with the recorded decisions |
| CR-005 | Colony invariant and out-of-order API calls | Resolved | Required timestamp retained; missing caller colony returns `404 / NoColony` |

## CR-001 — Semantic nullability and explicit unknown states

**Recorded:** 2026-09-12. **Status:** Open.

**Feedback:** Davide challenged the explanation that null means unknown:
“why don’t type this unknown”. He wants to review whether the types express the
actual states rather than depending on a verbal explanation of null.

**Observed implementation:** During Alliance #140, the agent first proposed making
`AllianceMember.experience` and `JoinRequest.experience` nullable. Following that
feedback, commit `97754d03` introduced a required sealed `ExperienceReading` with
`Known(earned: Experience)` and `Unknown`. `Experience` itself was not made nullable.
Protocol tests passed, including distinguishing known zero from unknown and
rejecting null for these fields. This verification does not settle the wider design review.

**Checks:** Review both experience fields and their persistence mapping. Examine
other roster nullability separately: `AllianceRosterResponse.pending` currently
uses null for a member who cannot see petitions, while an empty list means there
are no petitions. `PlayerProfile` also has optional fields. Decide whether each
case should retain null or use a named state; no blanket replacement is agreed.

**Decision:** The request to express unknown explicitly is captured; the final
representation and its consistency across boundaries remain under review.

## CR-002 — Experience type and field names

**Recorded:** 2026-09-12. **Status:** Open.

**Feedback:** Davide is not happy with the naming selected by the agent and wants
to review naming as part of the code's design.

**Current names:** `ExperienceReading`, `Known`, `Unknown`, and `Known.earned` in
`protocol/src/commonMain/kotlin/dev/fardavide/oltre/protocol/AllianceRoster.kt`.
These are the agent's current choices, not a confirmed naming decision from Davide.

**Checks:** Present the declaration and a mapping/use site. Establish whether the
concept is earned experience, knowledge of that value, or a reported value at the
last sync; then compare names that communicate the agreed concept. Keep the
existing `Experience` value wrapper distinct from the state describing its availability.

**Decision:** No replacement name has been agreed.

## CR-003 — Arrow `Either` adoption

**Recorded:** 2026-09-12. **Status:** Open; proposal from Davide.

**Feedback:** Davide would like to try `Either` from Arrow KT, with proper planning
for introducing it into the project.

**Checks:** Define the first use case and what each side means. Compare `Either`
with existing result types and named sealed domain states, including whether an
unknown experience value is an error at all. Evaluate affected KMP targets and
modules, dependency boundaries, caller ergonomics, exhaustive handling, and any
serialization or compatibility impact. Propose a small first adoption with its
migration and validation steps before expanding it.

**Decision:** Arrow has not been added by this documentation work. Neither a
project-wide `Either` convention nor replacing `ExperienceReading` with it is agreed.

## CR-004 — Architecture and adherence to directives

**Recorded:** 2026-09-12. **Status:** Open.

**Feedback:** Davide has given directives but has not reviewed much of the resulting
code or architecture. He wants a guided review to see how those directives are
being maintained and to retain feedback and future checks.

**Checks:** Begin with Alliance #140, the source of the current type concerns.
Walk the protocol, endpoint, repository, persistence mapping, and tests; compare
their responsibilities and dependencies with `architecture.md`, `decisions.md`,
and `alliance-sheet.md`. Record discrepancies and separate questions rather than
silently treating the existing implementation as the intended architecture.

**Decision:** This entry establishes a review starting point; the review itself
has not been performed by creating these files.

## CR-005 — A member who has never synced a colony

**Recorded:** 2026-09-12. **Status:** Resolved; original design question withdrawn on 2026-09-13.

**Finding:** The ticket's assumption that membership always comes after founding
a colony is not enforced by the server. A diagnostic in
`AllianceRosterEndpointsTest` authenticates a player without creating a colony,
successfully founds an alliance, and then receives
`500 Internal(detail=an alliance member has no colony)` from the roster handler.
The test expecting a readable roster is deliberately red pending the semantic decision.

**Model gap:** `AllianceMember.lastSyncedAt: Instant` cannot represent a player
who has never synced. `ExperienceReading.Unknown` addresses missing experience
but cannot supply a truthful sync timestamp.

**Agent proposal:** Use a required explicit sync state, provisionally named
`ColonySync`, with `NeverSynced` and `Synced(at: Instant)`. The alternative is to
require a colony before alliance participation and define how existing members
without colonies are handled. A third option is to pause implementation for the
guided roster-model review. No new nullable timestamp or invented timestamp is proposed.

**Decision:** Asked Davide in the session; awaiting his choice. The type name and
signature above are proposals, not approved declarations.

**Correction, 2026-09-13:** Davide: “A player starts with a colony.” The agent had
incorrectly turned an out-of-order API call into a gameplay choice. `App` automatically
calls `colony.found()` on first launch and keeps the game behind its gate until a
colony exists. The diagnostic bypassed that startup flow.

**Updated decision:** Retain the required `lastSyncedAt: Instant`; withdraw the
`ColonySync`/`NeverSynced` proposal and the gameplay picker. Missing caller colony
is an invalid request sequence, handled by the existing `ApiError.NoColony`, as on
sync. Do not introduce a new timestamp state or expand this slice into a redesign
of account creation or alliance participation.

**Next check:** Replace the diagnostic expectation with the explicit missing-colony
refusal and verify both repository implementations and the endpoint status. This
is a robustness check, not an open gameplay decision.

**Outcome, 2026-09-13:** The updated endpoint regression failed with expected 404
versus actual 500; the PostgreSQL regression reproduced a null timestamp dereference.
The in-memory repository now checks the caller's colony, and the joined PostgreSQL
read requires that colony to exist. Both refuse an absent caller colony with
`NoColony`; the endpoint maps it to 404. Focused roster, profile, account deletion,
and route tests passed after the fix. The required timestamp and gameplay remain intact.
Implementation reference: `d379eaa8`.
