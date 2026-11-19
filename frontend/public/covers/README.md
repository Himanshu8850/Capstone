# Book covers

Generated artwork only; no third-party images are used (PL-05). Each of the 60 books has its own front and back cover
built from its title, author and description (a themed symbol, colour and layout), so the cover suits the book name.

| File | Used for |
|---|---|
| `{bookId}-front.svg` | `frontCoverUrl` of the book with that id, for example `/covers/7-front.svg` |
| `{bookId}-back.svg` | `backCoverUrl` of the book with that id (optional; the column is nullable) |
| `default.svg` | Fallback shown when a book has no cover or an image fails to load (hand-kept, not regenerated) |

The seed script (`backend/src/main/resources/db/seed/R__seed_books.sql`) uses these paths and book ids `1..60`.

## Alt-text convention

Every cover image uses the alt text **`Cover of {title}`** (for example `Cover of The Habit Loop`). The back cover
uses **`Back cover of {title}`**. The fallback image uses `Cover of {title}` as well, so the meaning is not lost.

## Regenerating

```
cd frontend
node scripts/generate-covers.mjs
```

The script reads titles, authors and descriptions from `R__seed_books.sql`, so it must be re-run when those change.
The output is deterministic.
