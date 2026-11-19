# Word lists

## Why

Learners organize the signs they work on: "words I'm learning", "words for the class on Monday", "family". A list is
also what gets exported (PDF, Anki, EPUB), so it's the bridge between looking signs up and learning them.

## Design

- `WordListService` (accounts): lists of an owner, create, rename, delete, add and remove signs (one or many),
  and the lists containing a sign. Entries keep the order in which signs were added.
- Web: `/lists` (the user's lists), `/lists/{id}` (a list, with its signs from the index), the sign page adds to or
  removes from a list (or a new one), and search results can be added to a list (`SearchToListController`: the
  selected results, or all the results of the search up to a limit).
- Exports of a list: see [Exports](exports.md).

## Decisions

- **Entries reference signs by uid**, never by an index id: signs are transient. A list says how many of its signs
  are missing from the loaded bundles; they come back when their bundle is loaded again.
- **Private lists**: a list belongs to its owner; others get "not found". Sharing lists isn't needed yet.
- **htmx for adding and removing**: no page reload when adding a sign from a search; forms work without script too.
- **A limit for "add all the results"** (`SearchController.MAX_ADDED`): a vague search must not fill a list with
  thousands of signs.

## Open questions

- Sharing a list (with a class, read only) would need a sharing model.
