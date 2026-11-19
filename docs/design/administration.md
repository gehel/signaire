# Administration: signs and requests

## Why

SignSuisse doesn't have every sign a group uses, and an extracted sign may need a correction or a local precision
(the variant a class uses, a better definition). Users who miss a sign need a way to ask for it. Administrators handle
both, without editing bundles (which are SignSuisse's content, re-extracted at any time).

## Design

- **Changes to signs** (`/admin/signs`): an administrator edits a sign, adds a new one or deletes one. `SignEditor`
  stores the change in `sign_override` (`SignOverrideService`: the whole sign as JSON, or a deletion, with the
  administrator and the time) and applies it to the index right away. New signs get `local-…` uids. A change can be reverted (the bundle's version comes back).
- **Reapplied after every loading**: bundles replace the signs they contain, so `SignEditor.reapply()` runs after
  each loading, through [Bundle loading](bundle-loading.md).
- **Sign requests** (`/requests`, `sign_request`): a logged-in user asks for a word (language, comment). Requests are
  `OPEN`, then `ADDED` (with the uid of the sign) or `REJECTED`. Administrators (`/admin/requests`) look the word up
  on SignSuisse and import the sign (its media linked, not downloaded), link a sign already in the lexicon, create
  the sign (the edit form, pre-filled), or reject the request. Users see the state of their requests.
- **Bundles and synchronization**: see [Bundle loading](bundle-loading.md) and
  [SignSuisse synchronization](signsuisse-sync.md).

## Decisions

- **Overrides in the database, not in bundles**: bundles stay exactly what was extracted (re-extracting doesn't lose
  changes, and changes don't need re-packaging); the database is backed up with the accounts.
- **The whole sign as the override**: simpler than per-field patches, and an edited sign stays what the
  administrator saw when saving. The cost: a correction made later by SignSuisse to the same sign is hidden until the
  change is reverted.
- **`local-…` uids**: never collide with SignSuisse uids, and stay stable (lists and requests refer to them).
- **Imports link media instead of downloading them**: an import answers one request quickly; a synchronization of
  the category with media brings them later.
- **Roles by configuration** (see [Accounts](accounts.md)).

## Open questions

- No history of changes (who changed what, when, beyond the last change).
