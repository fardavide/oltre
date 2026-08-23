# Docs index

| Doc | What it holds |
|---|---|
| [brief.md](brief.md) | Distilled project brief; Notion pointer, architecture invariants, how Davide works |
| [architecture.md](architecture.md) | Module map, dependency rule and how it is enforced |
| [decisions.md](decisions.md) | Why — ADR-style, newest last |
| [status.md](status.md) | Where the project is: slices landed, what's next, pending setup |
| [balance-log.md](balance-log.md) | Every tuning round: the feedback in Davide's words, what moved, what to watch |
| [galaxy-sheet.md](galaxy-sheet.md) | The settled design for slices 4 and 5 — trait axes, coordinates, generation, the target distribution |
| [adaptation-sheet.md](adaptation-sheet.md) | The three adaptation ladders — why they are a second branch, what they cost, why they share one research slot |
| [fleet-sheet.md](fleet-sheet.md) | The 0.4 fleet design — the run, the ship set, danger, the hull curve, the four-slice plan |
| [exploration-rewards-sheet.md](exploration-rewards-sheet.md) | **Proposed, awaiting Davide's calls.** Why exploration pays nothing, and the inversion that fixes it — danger as payout, a drive technology, fleet-arc slice 2 |
| [deposit-sheet.md](deposit-sheet.md) | The 0.9 design — a world is a finite vein: per-world deposits, 5%/day refill, why the ship hold ceiling was dropped, and the guardrail the numbers must pass |
| [galaxy-identity-sheet.md](galaxy-identity-sheet.md) | **Proposed, three calls taken.** Why the map is a phone book and has no places — regions with real star bias, generated names, world portraits, the known-worlds ledger, per-world history |
| [drawn-map-sheet.md](drawn-map-sheet.md) | The 0.12 design — the galaxy as a folded ribbon of ten banded regions, the caption, the universe, and what the worlds list keeps now the filters and the sort are gone |
| [player-strip-sheet.md](player-strip-sheet.md) | The 0.16 design — the identity strip above the rail: its height, its mark, its gauge and why nothing about it went into the save |
| [experience-sheet.md](experience-sheet.md) | The 0.17 design — the level as a fold over the event log: why nothing is stored, what a completion pays, why the ladder is a straight line, and what the level does not yet do |
| [ask-once-sheet.md](ask-once-sheet.md) | The 0.18 design — the first settings screen: where the alert question is asked, how many notifications the answer arrives in, why `One in total` is not what was drawn, and why iPhone cannot quite do it |

Work that a remote agent session cannot do — UI of any kind, screenshot baselines, or a repo only
the desktop machine has — is written up as a ready-to-paste prompt. See
[`../rules/session-roles.md`](../rules/session-roles.md) for which session may do what, and
[`../prompts/`](../prompts/) for the standing ones. A Gradle build is no longer on that list in
full: `../tools/gradle-without-agp.sh` runs `:core` and `:sim` from a session where AGP cannot
resolve, so domain and balance work can be tested rather than reasoned about.

`../tools/` holds scripts an agent runs, as opposed to `../commands/` (what to do) and
`../skills/` (how this project does a thing).

## For agents

- Read `architecture.md` and `decisions.md` before any non-trivial change — several choices were
  settled with Davide and must not be re-litigated.
- When a choice that would be expensive to reverse is made (with or by Davide), append it to
  `decisions.md` in the same PR, including the rejected alternative.
- When a slice lands, update `status.md` in the same PR.
- Notion is **not** read-only — Davide changed that on 2026-08-06. Agents read *and* write the
  Oltre page: record what the build learned, append and annotate, date every entry, never
  overwrite his calls. See `brief.md`, which is authoritative on the rules for writing there.
