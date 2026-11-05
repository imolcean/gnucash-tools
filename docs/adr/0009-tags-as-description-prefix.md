# Tags live as a `[A] [B] text` prefix in the description

Status: Accepted, 2026-11-04

## Context

GnuCash has no tag concept. The user already tags transactions by prefixing the description with bracketed tags, e.g. `[Tag 1] [Tag 2]  Rewe` and `[Tag 1] Rewe again` in the sample book. Spacing is inconsistent (note the double space before `Rewe`). Storing tags anywhere else (custom slots, a side table) would be invisible to desktop and its reports and searches.

## Decision

- Tags are stored only in `transactions.description` as a prefix of bracketed names followed by the text.
- **Parse** tolerantly: any whitespace between tags and before the text; a tag is the content of a leading `[...]` group.
- **Compose** canonically: `[A] [B] text`, single spaces. Existing descriptions are only rewritten when the user edits that transaction.
- The UI shows tags as **chips**, autocompleted from tags already used in the book; typing a new name creates it. Description autocomplete matches the text after the tags.

## Consequences

- Desktop keeps showing and searching tags as plain text; no schema change.
- A literal `[` at the start of a description is read as a tag.
- fincore-core has a real `Tag`; the future importer parses the same prefix.
