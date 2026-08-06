# Docs index

| Doc | What it holds |
|---|---|
| [brief.md](brief.md) | Distilled project brief; Notion pointer, architecture invariants, how Davide works |
| [architecture.md](architecture.md) | Module map, dependency rule and how it is enforced |
| [decisions.md](decisions.md) | Why — ADR-style, newest last |
| [status.md](status.md) | Where the project is: slices landed, what's next, pending setup |

Work that a remote agent session cannot do — anything needing a Gradle build, or a repo only the
desktop machine has — is written up as a ready-to-paste prompt in [`../prompts/`](../prompts/).

## For agents

- Read `architecture.md` and `decisions.md` before any non-trivial change — several choices were
  settled with Davide and must not be re-litigated.
- When a choice that would be expensive to reverse is made (with or by Davide), append it to
  `decisions.md` in the same PR, including the rejected alternative.
- When a slice lands, update `status.md` in the same PR.
- Notion is read-only. If it looks wrong or stale, say so in chat.
