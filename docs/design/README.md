# Design documents

One document per feature: why it exists, how it works, and the decisions behind it (with the alternatives that were
rejected and why). The code says *what*; these documents say *why*, which the code can't.

**Write the document before the implementation.** A new feature, or a change to the design of an existing one,
starts with its design document (new, or updated), as the first commit of its branch, so that it can be reviewed
before code is written. Keep it in step when the implementation changes direction. Fixes with no design choice
(lint, renames) need none.

## Template

```markdown
# <Feature>

## Why
The need: who needs it, what for, the constraints (licensing, offline use, …).

## Design
How it works: components, data, flows. Enough to find one's way in the code, not a copy of it.

## Decisions
Each decision with its reason, and the alternatives rejected.

## Open questions
What is not decided yet, known limits, possible follow-ups.
```

## Documents

- [Anki export](anki-export.md)
- [Bundle loading](bundle-loading.md)
