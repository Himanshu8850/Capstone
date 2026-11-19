# Frontend code review (T146)

Date: 2026-11-17. One quick pass over `frontend/src` (97 non-test files) with search scripts, against plan.md "Frontend design".

| Check | Result |
|---|---|
| Axios created only in `api/axiosClient.js` | Pass (one `axios.create`, one import) |
| `AuthContext` and `BasketContext` the only global state; no Redux, TypeScript | Pass (no match) |
| `dangerouslySetInnerHTML`, `console.log`, `debugger`, commented-out code | None found |
| Card data or tokens in storage | Pass. `localStorage` is used only in `utils/storage.js` for the guest basket (book id and quantity) |
| Banned strings (wishlist, My Writers, Bestsellers, New Launches, Book Worm, coupon, wallet, theme toggle) | None in non-test source |
| Unused files | None; every non-entry file is imported |
| Unused exports | 4 found (below); no other export is unreferenced |
| Folder layout and naming | Matches plan.md; components and pages are PascalCase, helpers camelCase |
| Reusable components and shared `validators.js`, `money.js`, `date.js` | Used; no copied validation or formatting found |
| `useAsync` on data views | Used in the data pages and components (8 files plus hooks) |
| Dependencies | Runtime: axios, react, react-dom, react-router-dom only; none unused |

## Findings and resolution

| Finding | Resolution |
|---|---|
| Unused `export` on `QUERY_KEYS` (`hooks/useCatalogueQuery.js`), `AddressSummary` (`pages/AddressesPage.jsx`), `DEFAULT_COUNTRY` (`utils/addressForm.js`), `ProtectedRoute` (`routes.jsx`) | Fixed: `export` removed (behaviour-neutral) |
| `normalizeError` and `createApiClient` exported but used only by `api/__tests__/axiosClient.test.js` | Accepted: exported for testing |
| Nine components or pages have no same-named CSS file (`AddressForm`, `BrandNav`, `CategoryNav`, `LoginPage`, `NotFoundPage`, `OrderDetailPage`, `PaymentConfirmedPage`, `PurchaseConfirmedPage`, `RegisterPage`) | Accepted: five share a CSS file (`CatalogueNav.css`, `ConfirmationPages.css`, `OrdersPage.css`) and `AddressForm`, `LoginPage`, `RegisterPage`, `NotFoundPage` import none and are styled by the common components and `styles/base.css`; adding empty files would be noise |
| `common.test.jsx` and `layout.test.jsx` are lower case | Accepted: they are test files, not components |

No feature, route or dependency was added. `npm test` and `npm run build` are run once at the end of the project (see tasks.md T135 note and the final results).
