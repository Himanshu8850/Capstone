# Amendment AMD-002: Book reviews and ratings, author section, profile page, cart quantity control, required-field asterisks

**Status: APPROVED by the project owner, 2026-11-18.** Owner gates G-DM and G-API are approved for the changes below.

## What changed

- **Reviews and ratings are in scope** (reverses CL-04, FR-DET-07, FR-DET-08 for these items). One review per customer per book; rating 1 to 5 required; comment optional, at most 100 characters; no edit, no delete, no moderation. New table `review` (Flyway `V2`) and two operations: `listBookReviews` (public) and `createBookReview` (customer). New error code `ALREADY_REVIEWED`. OpenAPI 1.1.0, 31 operations.
- **"About the writer"** on the book detail page: the author name and the author's other books (existing `listBooks` `author` parameter). No photo, no biography, no author page, no new endpoint.
- **Profile page** `/profile`: read-only name and e-mail, gift points balance, saved addresses with the existing add, edit and delete. No wallet, no Edit Name, no points history.
- **Cart quantity control** (`QuantityControl`) on book cards and the detail page: Add to Cart, minus, quantity, plus, Go to Cart. Limit `min(10, stock)`. No change to basket rules.
- **Required-field asterisks** on mandatory fields of the registration, login, address, card, UPI and review forms. Validation is unchanged.

## Why

Owner request, 2026-11-18: the wireframes show reviews, ratings, the writer panel, a profile and a quantity control, and the owner wants them built.

## Approval

Approved by the project owner on 2026-11-18, including the data-model (G-DM) and OpenAPI (G-API) changes.

## Artifacts affected

- `spec.md`: "Amended by AMD-002" on CL-04, FR-DET-07, FR-DET-08.
- `plan.md`: "Wireframe fidelity" bullet.
- `.specify/memory/constitution.md`: OD-13 added to the Open Decisions Register (version and principles unchanged).
- `data-model.md`: new `review` table section; sentence about no review entity changed.
- `api/openapi.yaml` (1.1.0), `api/TRACEABILITY.md`, `docs/test-matrix.md`: two operations (29 to 31).
- `tasks.md`: T156 to T160.
- Code: `V2__review.sql`, Catalogue review classes, `ErrorCode`, `SecurityConfig` unchanged (public `GET /books/**` already permitted), frontend components and tests, `e2e` scenario.
