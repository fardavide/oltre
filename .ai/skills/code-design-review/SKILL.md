---
name: code-design-review
description: >
  Guides Oltre code and architecture reviews with Davide, captures his feedback,
  and maintains a backlog of design questions. Use when he asks to review existing
  code, challenges a type or name, or asks to record a concern for a future review.
---

# Code design review

Read [the review log](../../docs/code-review-log.md) when starting a review or
recording feedback. It holds concerns, open checks, and the history of decisions;
an open entry is not an approved design change.

## Review with Davide

- Start with his current concern or a relevant open log entry. Review a bounded
  slice of actual code and its callers rather than launching an unsolicited audit.
- Show the concrete type or signature, one representative use, and the architectural
  boundary involved. Explain what the design guarantees and where it relies on convention.
- Compare the implementation with the project's decision sheets and Davide's
  directives. Distinguish verified behavior, inferred intent, and unresolved choices.
- Classify a reproduced state as intended gameplay, a transport transient, or a
  violated invariant before proposing a model change. Trace the real client flow
  as well as synthetic API calls; a missing server guard need not become a gameplay decision.
- For semantic nullability, identify each meaning of absence. Compare explicit
  domain states with nullable values; distinguish unknown, unavailable, forbidden,
  and genuinely absent where the behavior differs. Inspect persistence and wire
  boundaries separately from the domain representation.
- Review names alongside semantics: what does the type represent, and do its name,
  variants, and fields express that concept? Treat a newly proposed name as a
  proposal until the naming concern is resolved.
- For a library or shared abstraction proposal, prepare a bounded adoption plan:
  intended semantics, alternatives, affected modules and platforms, public and
  serialized contracts, migration, and validation. In particular, evaluate whether
  `Either` represents an error/value choice or whether named domain states better
  express the case under review.

## Maintain the log

- Capture each substantive piece of feedback with a stable ID, date, faithful
  summary, affected symbols or boundaries, status, and the next concrete check.
- Separate Davide's feedback, the implementation observed, an agent proposal, and
  an agreed decision. Preserve useful exact wording when paraphrasing loses meaning.
- Append follow-up findings and decisions under the same ID; retain earlier feedback
  when a decision changes. Add a separate entry for an independently resolvable topic.
- Mark an entry resolved only with the agreed outcome and evidence: a reviewed
  conclusion, or an implemented change with its verification and commit/PR reference.
- Keep unresolved questions in this log. Put settled architectural decisions in
  `decisions.md` or the owning decision sheet; route reusable conventions to the
  owning skill without turning this log into another rulebook.

Recording feedback completes a documentation task. It does not authorize the
backlog's refactors or a dependency addition; continue those only within the scope
Davide has requested.
