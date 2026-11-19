# Feature Specification: BookyGeek — Complete Online Bookstore Platform

**Feature Branch**: `001-bookygeek-platform` (spec directory name only; no git branch was created because no `before_specify` hook is registered)

**Created**: 2026-11-12

**Status**: Draft — Clarify phase complete for the owner-approved decision batch (Revisions 1 to 4 applied, see §13; every decision is recorded in the Clarifications section). Revision 5 applies constitution amendment AMD-001 (Spring Boot 3.x → 4.x). All of OD-1 to OD-12 and CL-01a–d, CL-02 to CL-25 are resolved or accepted; the small plan-level details that remain are listed explicitly in §10.2. This specification is not yet approved for `/speckit-plan`.

**Input**: User description: "BookyGeek — Complete Project Specification" derived from the approved constitution (`.specify/memory/constitution.md` v2.0.0, amended by AMD-001), the Cloud Fullstack Developer Capstone deck (`Application Developer - Cloud FullStack - Capstone instructions.pptx`) and the wireframe attachment `Capstone_frontend_images/`.

---

## Clarifications

### Session 2026-11-12

- Q: Should BookyGeek have only customer accounts with the catalogue loaded from seed data and no admin screens, or also an admin role that can manage books? → A: Customer only (Option A). The catalogue (books, categories, brands) is loaded from seed data and changes only through data scripts. There are no admin screens, admin APIs or admin role. Resolves OD-2 and CL-22. Traceability: §7 (OD-2), §10 (CL-01a–d, CL-22), §10.1 (CL-10.4–CL-10.7), A-01, FR-AUTH-06..08.
- Q: Do customers have to be logged in to see the books, or can visitors who are not logged in browse the catalogue and open book detail pages? → A: Guests can browse (Option A). Anyone, without logging in, can see the landing page, catalogue, categories, brands and book details. Customer-specific functions (My Orders, reward points, saved addresses, order cancellation) still require login. Resolves CL-01a. Does not decide CL-01b (guest basket), CL-01c (basket persistence) or CL-01d (login before checkout). Traceability: §10 (CL-01, CL-01a), §10.1 (CL-10.3), FR-AUTH-06, FR-AUTH-08, FR-LAND-01..04, FR-CAT-01..10, FR-DET-01..09, A-09.
- Q: When must a customer log in: only at checkout, or before they can add a book to the basket? And can a visitor who is not logged in keep a basket? → A: Guests can add to a basket; login is required at checkout (Option A). A visitor's basket is kept in the browser and is merged into the customer's own basket at login. Checkout and payment require login. Resolves CL-01b and CL-01d. Not decided: whether any basket survives across sessions (CL-01c, A-11), and the merge rule for a book present in both baskets (plan-level detail). Traceability: §10 (CL-01, CL-01b, CL-01d), §10.1 (CL-10.3), FR-AUTH-06, FR-AUTH-08, FR-DET-03, FR-BSK-01, FR-CHK-01, User Story 1 scenario 4, User Story 4, A-09.
- Q: Should the shop keep a customer's basket after they log out or close the browser, so it is still there when they come back later? → A: Keep it for logged-in customers (Option A). A logged-in customer's basket is saved against their account and is still present after logout and re-login. A guest basket lives only in the browser and is merged at login (CL-01b). Resolves CL-01c and replaces design assumption A-11 with a confirmed decision. Traceability: §10 (CL-01, CL-01c), §10.1 (CL-10.3), FR-AUTH-08, FR-BSK-09, edge case "session ends mid-checkout", A-09, A-11.
- Q: For cancelling an order within 48 hours, when does the 48-hour clock start, and how does it work for Cash on Delivery (COD) orders? → A: Order placement time for every payment method, COD included (Option A). The 48-hour window is measured from the moment the order is placed. There is no separate rule for COD: a COD order can be cancelled within 48 hours of placement like any other order. Resolves the anchor and the COD-cancellation parts of OD-6 and CL-12. Not decided: whether the 48-hour boundary is inclusive or exclusive (CL-14), the COD payment and order states (CL-12 remainder), stock/points/refund side effects of cancellation (CL-13) and order statuses (CL-24). Traceability: §7 (OD-6), §10 (CL-12, CL-14), FR-ORD-04, FR-PAY-06, User Story 6 scenarios 3–4, edge case "Cancellation exactly at the window boundary".
- Q: How do customers get reward points: do they earn them when they buy books, or does each customer just start with a fixed balance from the seed data? → A: No earning; balances come from seed data only (Option B). Each new account starts with a fixed balance defined in the reproducible seed data. Points can only be redeemed; no order, payment or other action adds points. Resolves the earning part of OD-4. Traceability: §7 (OD-4), §10 (CL-13), §2.11 (Reward-point record), FR-RWD-06, FR-RWD-01..05.
- Q: How much is one reward point worth when redeeming, and is there a limit on how much of an order can be paid with points? → A: 1 point = ₹1, and points can cover at most 50% of the order total, so a payment method is always required for the remainder (Option B). Resolves the point value and the redemption cap of OD-4. Not decided: the starting balance amount, any minimum redemption, expiry, and whether cancelled orders restore redeemed points (CL-13). Traceability: §7 (OD-4), FR-RWD-02, FR-RWD-03, FR-RWD-06, FR-CHK-05, edge case "Redeeming more points than owned".
- Q: What does "brand" mean for browsing books, since the capstone requires customers to be able to browse by brand? → A: Brand = the book's publisher (Option A). The catalogue has a brand list of the publishers in the seed data, and selecting one lists only that publisher's books. Resolves CL-02. Not decided: where the brand control appears (not in any wireframe; Plan-phase design decision). Traceability: §10 (CL-02), §10.1 (CL-10.10), §2.11 (Category / Brand), §6.1 journey 6, FR-CAT-02.
- Q: What should a visitor enter to register, which field do they log in with, and what rules must the password meet? → A: Option A. Registration collects full name, e-mail, password and confirm password. The e-mail address is the unique login identifier and is compared case-insensitively (the same address in a different letter case counts as a duplicate). The password must be 8 to 64 characters and contain at least one letter and one digit. Resolves CL-18 and the registration part of OD-1's input rules (the authentication mechanism itself stays under OD-1). Not decided: any e-mail verification or password-reset flow (not required; no source states one). Traceability: §10 (CL-18), §11 (A-08 withdrawn, replaced by this decision), SCR-02, SCR-03, §2.11 (Customer), FR-AUTH-01..04.
- Q: How is the tentative delivery date worked out for the date shown on each book? → A: Today + 5 days for physical books, today for eBooks (Option C). The same rule applies on every screen that shows a delivery date, and stock does not change it. Resolves OD-5 and the eBook part of CL-23. Not decided: the stock model and maximum basket quantity (rest of CL-23). Traceability: §7 (OD-5), §10 (CL-23), §10.1 (CL-10.27), FR-DET-02, FR-CAT-03, FR-BSK-02, FR-CNF-01.
- Q: How do Cash on Delivery (COD) orders get their payment state, what does cancelling an order restore, and is exactly 48 hours inside or outside the cancellation window? → A: Option A. At placement every order has status "Placed". Credit Card, Debit Card and UPI orders have payment state "Paid"; a COD order has payment state "Pending (pay on delivery)" and nothing is ever collected. Cancelling an order restores stock and restores any redeemed reward points to the customer's balance. For a "Paid" order the payment record becomes "Refunded" (a simulated record only; no money moves); for a COD order the payment record becomes "Cancelled". The 48-hour window is exclusive: an order can be cancelled only while it is strictly less than 48 hours old, and a request at exactly 48:00:00 or later is rejected. Resolves CL-12, CL-13, CL-14 and OD-6, and the restoration part of OD-4. Not decided: the complete order status list beyond "Placed" and "Cancelled" and valid transitions (CL-24). Traceability: §7 (OD-4, OD-6), §10 (CL-12, CL-13, CL-14), §10.1 (CL-10.23), FR-ORD-04..06, FR-PAY-06, FR-PAY-12, FR-RWD-06, User Story 5 scenario 4, User Story 6 scenarios 3–4, edge case "Cancellation exactly at the window boundary".
- Q: How many reward points does each new account start with, is there a minimum number of points a customer must redeem at once, and do points ever expire? → A: Option B. Every new account starts with 1,000 reward points (worth ₹1,000 at 1 point = ₹1), defined in the reproducible seed data. There is no minimum redemption: a customer may redeem any whole number of points from 1 up to the lower of their balance and the 50% cap. Points do not expire. Resolves the remaining parts of OD-4, so OD-4 is fully resolved. Traceability: §7 (OD-4), §2.11 (Reward-point record), FR-RWD-01..06, FR-CHK-07, edge case "Redeeming more points than owned".
- Q: Besides "Placed" and "Cancelled", does an order have any other statuses, and what should Buy Again do when a book in an old order is out of stock, has a changed price, or has been removed from the catalogue? → A: Option A. An order has only two statuses, "Placed" and "Cancelled". The only transition is Placed → Cancelled, allowed within 48 hours of placement (FR-ORD-04); "Cancelled" is final. There is no Shipped or Delivered status. Buy Again adds each book from the old order that is still in the catalogue and in stock, at its current price and with its current delivery date (never the old price). A book that is out of stock or no longer in the catalogue is not added and is listed in a message. If the ordered quantity is more than the stock, the available quantity is added and the customer is told. Resolves CL-24. Not decided: the stock model and maximum basket quantity (rest of CL-23). Traceability: §10 (CL-24), FR-ORD-01, FR-ORD-03, FR-ORD-06, SCR-10, User Story 6 scenario 2, edge case "Customer with no order history".
- Q: How should the app choose "related products" on a book page and "recommendations" in the basket, and should the landing page include the "Bestsellers this Month" and "New Launches" sections shown in the wireframe? → A: Option A. Related products on a book's detail page are the other books in the same category as that book, never the book itself. Basket recommendations are books in the same categories as the books the customer has previously ordered, excluding books already in the basket; a customer with no order history sees an empty state and a guest sees no recommendations. The same rule feeds the "Recommended for You" section on the landing and catalogue pages (shown only when it has books to show). "Bestsellers this Month" and "New Launches" are not built; no sales or launch-date rule is defined. Resolves OD-7, CL-15 and the up-sell/cross-sell question of CL-10.12 (no separate feature). Traceability: §7 (OD-7), §10 (CL-15), §10.1 (CL-10.11, CL-10.12), FR-LAND-03, FR-CAT-09, FR-DET-04, FR-BSK-07, SCR-04, User Story 4 scenario 4.
- Q: Which architecture style does BookyGeek follow, and how is the capstone slide 4 diagram to be read? (Q0) → A: Modular monolith (approved decision). One React + Vite frontend, one Spring Boot backend that is a single deployable application with a layered structure (Controller → Service → Repository → Entity), one PostgreSQL database and seven logical backend modules that communicate in-process inside that one application. There are no independently deployable microservices, no API gateway, no inter-service REST calls, no separate database per module and no message broker or other distributed infrastructure. The slide 4 diagram is read only as a business-capability map, not as a microservices requirement. Constitution Principle II already states exactly this, so the constitution is not modified. Confirms the treatment of CL-10.30 and CL-10.31. Traceability: §5, §5.1, §2.12, §10.1 (CL-10, CL-10.30, CL-10.31).
- Q: Is the landing page a separate page from the catalogue? (CL-03) → A: No. The catalogue is the initial application page and the landing page; it shows the available books and the category navigation. There is no separate marketing home page. Resolves CL-03. Traceability: §10 (CL-03), SCR-01, SCR-04, FR-LAND-01..04, User Story 2 scenario 1.
- Q: Which wireframe-only features are built? (CL-04) → A: Only confirmed features. Not built: Wishlist ("My Wishlist", "Add to Wishlist"), My Writers, review submission, ratings and copies-sold information, separate author profile pages ("About the writer") and any other decorative or illustrative feature. Book language stays as ordinary book information. No inactive navigation link or non-functional button is displayed. Resolves CL-04. Traceability: §10 (CL-04), FR-DET-01, FR-DET-07, FR-DET-08, SCR-05, SCR-14, DS-07, §2.11, §8.
- Q: Is the Wallet payment option built? (CL-05) → A: No. Only Credit Card, Debit Card, UPI and Cash on Delivery are supported. There is no wallet balance, top-up or wallet transaction. Resolves CL-05 and CL-10.25. Traceability: §10 (CL-05), §10.1 (CL-10.25), FR-PAY-01, FR-PAY-02, SCR-08.
- Q: Are coupons built? (CL-06) → A: No. No coupon functionality, no "Apply Coupon" control, no coupon entity and no coupon API. Resolves CL-06 and CL-10.19. Traceability: §10 (CL-06), §10.1 (CL-10.19), FR-CHK-05, FR-CHK-06, FR-BSK-05, SCR-07.
- Q: How are tax, delivery charges and the order total calculated, and what do the sample totals ₹470 and ₹580 mean? (CL-07, CL-08) → A: Simulated pricing rules. Tax is 5% of the book subtotal (there is no discount, because coupons are excluded). Delivery is ₹40 per order when the order contains any physical book (physical-only and mixed physical/eBook orders), and free for an eBook-only order. Total = subtotal − coupon discount (always zero) + tax + delivery charge. Reward-point redemption is applied afterwards, within the existing 50% limit, and the eligible order total for that limit is subtotal + tax + delivery. All amounts use consistent rounding to two decimal places. There is no real GST compliance and no tax integration. ₹470 and ₹580 in the wireframes are illustrative sample amounts: totals are always calculated from these rules and never hardcoded. Resolves CL-07 and CL-08. Traceability: §10 (CL-07, CL-08), §10.1 (CL-10.26), FR-BSK-05, FR-CHK-05, FR-PAY-09, FR-RWD-03, User Story 5 scenario 6.
- Q: Which name does the user interface show? (CL-09) → A: "BookyGeek" everywhere. "Book Worm" (seen in the wireframes) is not used as the application name or tagline. Resolves CL-09. Traceability: §10 (CL-09), DS-07, DS-12, §4.2.
- Q: Which slide 4 capabilities beyond the confirmed customer journeys are built, and is logout built? (CL-10, CL-10.2) → A: Unconfirmed capabilities stay out of scope: no admin portal, admin role, store management, inventory-management dashboard, real payment-gateway integration, or unconfirmed analytics or management capability. Only the confirmed customer journeys and the seven approved modules are built. Logout IS included, in the customer account menu, and it clears or invalidates the active authentication state appropriately. Resolves CL-10 (all Category B and C items of §10.1 are out of scope) and CL-10.2. Traceability: §10 (CL-10), §10.1, FR-AUTH-05, FR-ORD-07, User Story 1 scenario 5.
- Q: Where and how does the customer redeem reward points at checkout? (CL-11) → A: A redemption area is displayed below the order summary during checkout. It shows the available reward-point balance, the maximum redeemable points and an input to enter the points to redeem. The reward rules are unchanged: 1 point = ₹1, maximum redemption 50% of the eligible order total, no minimum, no earning, 1,000 seeded points per new account, no expiry, and redeemed points are restored after a successful cancellation. Resolves CL-11. Traceability: §10 (CL-11), FR-RWD-01, FR-RWD-05, FR-CHK-07, SCR-07.
- Q: Which catalogue filters, sort options and paging are built? (CL-16) → A: All proposed controls. Filters: Language, Format and Price Range. Sorting: Relevance, Price low to high, Price high to low, Title A to Z. Pagination: 12 books per page. Filtering, sorting and pagination work together (and with category, brand, search, author and genre selections). Resolves CL-16. Traceability: §10 (CL-16), FR-CAT-04..06, FR-CAT-10, User Story 2 scenario 4.
- Q: What does the delivery address contain, and can customers save addresses? (CL-17) → A: Validated and saved multiple addresses. Fields: full name (required); phone (required, exactly 10 digits); address line 1 (required); address line 2 (optional); city (required); state (required); PIN code (required, exactly 6 digits); country (required, default India). Customers can save multiple addresses, select a saved address during checkout and manage their saved addresses. This replaces the wireframe fields first name, last name, single "Address", e-mail and phone country-code selector. Resolves CL-17. Traceability: §10 (CL-17), FR-CHK-02..04, FR-AUTH-06, §2.11 (Address).
- Q: Which screen sizes are supported? (CL-19) → A: Mobile, tablet and desktop. Mobile (below 640px): single-column layouts where appropriate. Tablet (640px–1023px): adapted layouts, including two-column layouts where appropriate. Desktop (1024px and above): the supplied wireframe composition is preserved. Breakpoints are approximate. Resolves CL-19. Traceability: §10 (CL-19), RSP-01..05, NFR-FE-04.
- Q: Which non-functional targets apply? (CL-20) → A: Practical capstone-level development targets, not guarantees. Performance: a normal API response under 1 second and an initial frontend load under 3 seconds in normal local development conditions. Browsers: latest Chrome, latest Edge, latest Firefox. Accessibility: keyboard-accessible controls, proper form labels, appropriate colour contrast and alternative text for meaningful images. No production uptime SLA is required. Resolves CL-20. Traceability: §10 (CL-20), NFR-PERF-01..02, NFR-BRW-01, NFR-ACC-01.
- Q: How does simulated payment work? (CL-21) → A: Simulated success and failure for Credit Card, Debit Card, UPI and COD. Card payments: test-only input or predefined test values trigger a simulated failure, and normal valid test data triggers a simulated success. UPI: a simulated UPI ID input and no real UPI provider integration. Payment confirmation and purchase confirmation are separate logical steps. For a successful online payment the payment is recorded as Paid, a payment confirmation is displayed, and then the purchase confirmation is displayed. For COD the payment is recorded as Pending and the order is placed successfully. Real payment credentials are never collected or stored. Resolves CL-21 and CL-10.22. Traceability: §10 (CL-21), §10.1 (CL-10.22), FR-PAY-04, FR-PAY-05, FR-PAY-08, FR-CNF-03, SCR-12, User Story 5.
- Q: How is stock modelled and how many units of a book may be in a basket? (CL-23) → A: Physical books have finite integer stock; eBooks have unlimited availability and are never decremented. Adding to the basket does not reserve stock. Stock is revalidated at checkout and again before the order is placed, decreased after a successful order placement and restored after a successful cancellation. A basket may hold at most 10 units of one book and never more than the available physical stock. Concurrent stock updates are handled safely. Resolves CL-23. Traceability: §10 (CL-23), FR-CAT-08, FR-BSK-01, FR-BSK-03, FR-BSK-08, FR-ORD-03, FR-ORD-05, FR-ORD-08, NFR-DAT-02.
- Q: What do author names, genre tags, categories and the header menu do? (CL-25) → A: Selecting an author name filters the catalogue by that author. Selecting a genre tag filters the catalogue by that genre. Categories support hierarchical navigation (example: Non-Fiction → Self Help). The header menu icon toggles the category sidebar. Every navigation element leads to a functional result. Resolves CL-25 and withdraws assumption A-06 as an open item. Traceability: §10 (CL-25), FR-CAT-01, FR-CAT-03, FR-CAT-07, FR-DET-06, FR-CHK-09, DS-07, A-06.
- Q: How is authentication implemented? (OD-1) → A: Spring Security with JWT. Customer registration, login, protected customer endpoints, JWT validation, password hashing and logout / authentication-state handling. Only customer authentication; no admin roles or screens; consistent with the single Spring Boot application. The owner approves Spring Security as an addition under constitution Principle III; the written proposal of purpose and alternatives is recorded at the plan Constitution Check. Resolves OD-1. Traceability: §7 (OD-1), §5.1, FR-AUTH-03, FR-AUTH-05, NFR-SEC-01..02.
- Q: How is the database schema managed? (OD-3) → A: Flyway. Version-controlled SQL migration scripts, with Hibernate schema validation. Hibernate automatic update or create is not the normal schema-management approach. Resolves OD-3. Traceability: §7 (OD-3), §5.1, NFR-DAT-04, NFR-DAT-05.
- Q: Which testing tools are used? (OD-8) → A: Backend: JUnit 5, Mockito and Spring Boot Test. Frontend: Vitest and React Testing Library. End-to-end: Playwright. Unit, integration and end-to-end tests cover the confirmed customer journeys. Resolves OD-8. Traceability: §7 (OD-8), §5.1, NFR-TST-01..04.
- Q: Is there a light theme or theme toggle? (OD-9) → A: Dark theme only, following the supplied wireframe visual direction with consistent styling. No light/dark toggle. Resolves OD-9. Traceability: §7 (OD-9), DS-11, DS-01.
- Q: How is the OpenAPI contract produced? (OD-10) → A: Contract first. `openapi.yaml` is maintained, authored and validated in Swagger Editor and kept in the repository. The Spring Boot APIs conform to the approved contract, and the contract is reviewed and approved before backend implementation. Resolves OD-10. Traceability: §7 (OD-10), §5.1, NFR-API-04.
- Q: How is the application run and deployed? (OD-11) → A: Docker Compose for the local frontend, backend and PostgreSQL setup. Cloud deployment is optional, only if a suitable free-tier environment is available, and is never a mandatory dependency. Clear local setup and execution instructions are provided. Resolves OD-11. Traceability: §7 (OD-11), §5.1, NFR-LOC-01..04.
- Q: How are the wireframe and data-model review items handled? (OD-12) → A: All proposed review assumptions are accepted. The five `Capstone_frontend_images` PNG files are separate wireframe references and were compared with the embedded wireframe pictures of slides 6–10 (they match). Sample data and inconsistent sample totals are illustrative, not mandatory. The data model contains only the entities and fields required by confirmed functionality, and no entity is created merely because it is common in eCommerce applications. Visual hierarchy and customer journeys are preserved; features that appear only as visual examples are out of scope unless confirmed elsewhere. The data model is reviewed before backend implementation. Resolves OD-12. Traceability: §8, §0.2, §2.11, NFR-DAT-04.


---

## 0. Source Review Record and Conventions

### 0.1 Sources and their authority

| Source | Used for | Tag |
|--------|----------|-----|
| Approved constitution v1.0.0 | Architecture, stack, principles, project-added requirements, open decisions OD-1..OD-12 | `[CON]` |
| Capstone deck text (slides 2–3, 5–13) | Official functional requirements, customer journeys, delivery expectations. Only what the slide text states. | `[CAP]` |
| Capstone deck slide 4 architecture diagram | Capability map; classified in §10.1; not a requirements list | `[CAP-S4]` |
| `Capstone_frontend_images/*.PNG` and slide 5 picture | Visual reference for screens, layouts, components, sample data. A visible element is an observation, not an approved function. | `[WF]` |
| Project-added requirements named in the constitution (UPI, Cash on Delivery) | Extra payment methods | `[PRJ]` |
| The owner's `/speckit-specify` request (functional list in its section 2) | Items the owner explicitly asked to be specified that are not in the PPT text or constitution (e.g., search, remove from basket). Treated as an explicit project instruction, not as a capstone requirement. | `[APD]` |
| Proposed designs/behaviours not given by any source | Design assumptions, NOT capstone-provided and NOT approved | `[PD]` |

Where sources conflict, the conflict is recorded in §10 and not silently resolved.

### 0.2 Image inspection record

No image-processing limit was hit. Images were inspected in batches; each image was inspected exactly once.

| Batch | File | Inspected | Corresponding PPT slide |
|-------|------|-----------|-------------------------|
| 1 | `Capstone_frontend_images/Catalogue_page.PNG` | Yes | Slide 6 |
| 1 | `Capstone_frontend_images/Shopping_cart.PNG` | Yes | Slide 7 |
| 1 | `Capstone_frontend_images/Payment.PNG` | Yes | Slide 8 |
| 2 | `Capstone_frontend_images/Payment_screen.PNG` | Yes | Slide 9 |
| 2 | `Capstone_frontend_images/Purchase_confirmation.PNG` | Yes | Slide 10 |
| 3 | PPT slide 4 picture (architecture diagram, embedded `image5.png`) | Yes | Slide 4 |
| 3 | PPT slide 5 picture (embedded `image6.png`: full-resolution catalogue/landing wireframe) | Yes | Slide 5 |
| 3 | PPT slide 5 picture (embedded `image7.png`: blank laptop frame, no design content) | Yes | Slide 5 |
| 4 | PPT slides 6–10 embedded pictures (`image8`, `image10`, `image11`, `image12`, `image13`) and the shared slide frame `image9` (OD-12 comparison, Clarification 2026-11-12) | Yes | Slides 6–10 |

**Comparison result (OD-12, Clarification 2026-11-12)**: the embedded pictures of slides 6–10 match the five attachments (`Catalogue_page.PNG`, `Shopping_cart.PNG`, `Payment.PNG`, `Payment_screen.PNG`, `Purchase_confirmation.PNG`); `image9` is a blank laptop frame with no design content. **Still not opened** (limitation): the slide 1 logo files (`image3.emf`, `image4.svg`) and `image1`/`image2`. No claim is made about any content not seen.

### 0.3 Requirement conventions

Every requirement has a unique ID, a source tag, a reference (slide / image), dependencies or business rules, any governing open decision, and testable acceptance criteria. "Ref" lists the PPT slide (`S#`) and image filename where applicable. Currency and date presentation (₹, "Delivery by Mon, 21 Jul") are observed from wireframes (assumption A-04).

---

## 1. User Scenarios & Testing *(mandatory)*

Priorities express delivery ordering only. Per constitution Principle I, **all** journeys are mandatory for completion; none may be dropped.

### User Story 1 — Register and log in (Priority: P1)

A visitor creates an account and logs in so they can use protected customer functionality. (Capstone journey steps 1–2 cover the login page and authentication; registration comes from constitution Principle I and the owner's request, not from the PPT text.)

**Why this priority**: Orders, checkout, payment, rewards and order history are tied to a customer identity.

**Independent Test**: Register a new customer, log in, and reach a protected page; confirm an unauthenticated visitor cannot.

**Acceptance Scenarios**:

1. **Given** a visitor on the registration page, **When** they submit valid details, **Then** an account is created and they can log in.
2. **Given** an existing account, **When** a visitor registers with the same e-mail address (in any letter case; CL-18), **Then** registration is rejected with a clear message and no second account exists.
3. **Given** a registered customer, **When** they submit wrong credentials, **Then** login fails with a generic message that does not reveal which part was wrong.
4. **Given** an unauthenticated visitor, **When** they open My Orders, **Then** they are directed to log in and, after success, returned to the page they wanted (return-to-page is a design assumption, A-12). Checkout and payment also require login (Clarification 2026-11-12, CL-01d).
5. **Given** a logged-in customer, **When** they choose Log out in the customer account menu, **Then** the active authentication state is cleared or invalidated, protected screens are no longer accessible and My Orders redirects to login (Clarification 2026-11-12, CL-10.2).

### User Story 2 — Discover books on the landing page and catalogue (Priority: P1)

A customer sees book availability on the landing page (the catalogue page, Clarification 2026-11-12, CL-03), selects a category, browses brands, searches, filters and sorts. (Capstone steps 3, 5, 6 and slide 5 cover availability, categories and brands. Search, filtering and sorting come from the wireframe and the owner's request, not from the PPT text.)

**Why this priority**: Without browsing there is nothing to buy.

**Independent Test**: Open the landing page, select a category, search for a title, apply a filter and a sort, and open a book.

**Acceptance Scenarios**:

1. **Given** books exist, **When** the application opens, **Then** the catalogue page is shown as the landing page with the books, their availability and the category navigation; there is no separate marketing home page (Clarification 2026-11-12, CL-03).
2. **Given** the catalogue, **When** the customer selects a category, **Then** only books in that category are listed and the category is visibly selected.
3. **Given** a search term, **When** the customer searches, **Then** matching books are listed; **Given** no match, **Then** an empty state is shown.
4. **Given** a category, a language filter, a sort option (Relevance, Price low to high, Price high to low or Title A to Z) and a page, **When** applied together, **Then** results satisfy all selections and at most 12 books are shown per page (Clarification 2026-11-12, CL-16).
5. **Given** an unavailable book (a physical book with zero stock; eBooks are always available), **When** displayed, **Then** it is marked unavailable and cannot be added to the basket (Clarification 2026-11-12, CL-23).
6. **Given** a visitor who is not logged in, **When** they open the landing page, a category, a brand or a book detail page, **Then** the content is shown without a login prompt (Clarification 2026-11-12, CL-01a).
7. **Given** a book card or detail page, **When** the customer selects an author name or a genre tag, **Then** the catalogue shows only that author's or that genre's books; **Given** the header menu icon, **Then** it toggles the category sidebar, in which categories are navigated hierarchically (Clarification 2026-11-12, CL-25).


### User Story 3 — View book details and related products (Priority: P1)

A customer opens a book, sees its details, a tentative delivery date and related products, and selects a related product. (Capstone steps 7 and "Related products appear for selection".)

**Independent Test**: Open a book and verify details, delivery date, Add to Cart and the Related Reads list; open a related book.

**Acceptance Scenarios**:

1. **Given** a book, **When** its page opens, **Then** cover, title, author, description, format, price and delivery date are shown.
2. **Given** related products exist, **When** the customer selects one, **Then** its detail page opens.
3. **Given** a book id that does not exist, **When** requested, **Then** a not-found state is shown.

### User Story 4 — Manage the basket (Priority: P1)

A customer adds books, changes quantities, removes books and sees totals and order-history-based recommendations. (Capstone step 8, the slide 7 heading "Shopping Cart for update" and "Recommends items based on Order History". Removal is from the owner's request; the PPT text does not state it.)

**Independent Test**: Add two books, change a quantity, remove one, verify totals and badge.

**Acceptance Scenarios**:

1. **Given** a book detail page, **When** Add to Cart is used, **Then** the basket and header count update.
2. **Given** a basket line, **When** the quantity changes, **Then** line and grand totals recompute.
3. **Given** a basket, **When** a line is removed, **Then** it disappears and totals recompute; **Given** the last line is removed, **Then** an empty-basket state is shown.
4. **Given** a customer with order history, **When** the basket is viewed, **Then** recommendations (books in the same categories as the customer's previously ordered books, excluding basket items) are shown; **Given** no history, **Then** an empty state is shown, and a guest sees no recommendations (Clarification 2026-11-12, OD-7).
5. **Given** a visitor who is not logged in, **When** they add a book to the basket and then log in, **Then** the book is present in their customer basket (Clarification 2026-11-12, CL-01b).
6. **Given** a visitor who is not logged in with a non-empty basket, **When** they proceed to checkout, **Then** they are directed to log in and then returned to checkout with their basket (Clarification 2026-11-12, CL-01d).
7. **Given** a basket line, **When** the quantity would exceed 10 units of that book or the available physical stock, **Then** the increase is rejected with a message (Clarification 2026-11-12, CL-23).

### User Story 5 — Check out, pay (dummy) and receive confirmation (Priority: P1)

A customer selects or enters a delivery address, selects a payment method, optionally redeems reward points, pays with dummy processing and sees payment and purchase confirmation. (Capstone steps 9–12.)

**Independent Test**: With items in the basket, complete checkout with each of Credit Card, Debit Card, UPI and Cash on Delivery and verify confirmation, order creation and basket clearing.

**Acceptance Scenarios**:

1. **Given** a basket and a valid address, **When** the customer pays with valid test card data, **Then** the payment is recorded as Paid, a payment confirmation is shown, and then the purchase-confirmation screen lists the purchased books with a Continue-your-Shopping action (Clarification 2026-11-12, CL-21).
2. **Given** an invalid card number, **When** submitted, **Then** the form shows field errors and no order is created.
3. **Given** a simulated payment failure (triggered by a predefined test card value, Clarification 2026-11-12, CL-21), **When** the payment is attempted, **Then** the customer sees a failure message, no confirmed order exists, reward points are not consumed, stock is unchanged and the basket is intact.
4. **Given** Cash on Delivery is chosen, **When** the customer confirms, **Then** the order is placed with status "Placed" and payment state "Pending (pay on delivery)" (Clarification 2026-11-12, CL-12).
5. **Given** a double-click on Pay Now, **When** processed, **Then** exactly one order and one successful payment result.
6. **Given** a basket, **When** the totals are shown, **Then** tax is 5% of the book subtotal, delivery is ₹40 when the order contains any physical book and free for an eBook-only order, there is no coupon or discount line, and amounts are rounded consistently to two decimal places (Clarification 2026-11-12, CL-07).

### User Story 6 — Order history, Buy Again and cancellation (Priority: P2)

A customer reviews previous orders, buys a previous order again and cancels an eligible order within the 48-hour restriction. (Capstone slide 3 and "Cancel Order Within 48 hrs".)

**Independent Test**: With an existing order, open My Orders, use Buy Again, and attempt cancellation inside and outside the window.

**Acceptance Scenarios**:

1. **Given** a customer with orders, **When** My Orders opens, **Then** only that customer's orders are listed; **Given** none, **Then** an empty state.
2. **Given** an order, **When** Buy Again is used, **Then** its books that are still in the catalogue and in stock are added at their current price and delivery date, books that are out of stock or removed are not added and are listed in a message, and an over-stock quantity is reduced to the stock with a message (Clarification 2026-11-12, CL-24).
3. **Given** an order placed less than 48 hours ago (the window runs from order placement for every payment method, including COD, and is exclusive at 48:00:00; Clarification 2026-11-12, OD-6, CL-14), **When** cancelled, **Then** its status becomes "Cancelled", stock and any redeemed points are restored, and the payment record is updated (CL-13).
4. **Given** an order that is 48 hours old or older, **When** cancellation is attempted, **Then** it is rejected with a clear message and no state change.

### User Story 7 — Redeem gift/reward points (Priority: P2)

A customer applies available reward points during checkout to reduce the payable amount. (Capstone slide 3 step 10 "Redeem gift points".) The rules are resolved (OD-4, CL-11).

**Independent Test**: With a points balance, apply points at checkout and verify the new total and balance after payment.

**Acceptance Scenarios**:

1. **Given** a points balance, **When** points are entered in the redemption area below the order summary (showing the balance and the maximum redeemable points), **Then** the payable amount reduces and the summary shows the reduction.
2. **Given** a balance of zero, **When** checkout is displayed, **Then** redemption is unavailable with an explanation.
3. **Given** a failed payment, **When** points were applied, **Then** the balance is unchanged.

### Edge Cases

- Basket contains a book that became unavailable or changed price before checkout → customer is told; payment cannot proceed until resolved.
- Two customers buy the last available copy concurrently → only one succeeds; the other is informed and stock never becomes negative (CL-23).
- Basket quantity increased beyond 10 units of one book or beyond the available physical stock → rejected with a message (CL-23).
- Empty basket → checkout is not reachable; empty state is shown.
- Customer with no order history → Buy Again and order-history recommendations show empty states.
- Expired card date, malformed card number or CVV, empty required address fields, a phone number that is not exactly 10 digits, a PIN code that is not exactly 6 digits → inline validation, no submission (CL-17).
- Back/refresh/duplicate submission on the confirmation or payment screen → no duplicate order or payment.
- Cancellation exactly at the window boundary → the window is exclusive: an order exactly 48:00:00 old is rejected, one that is 47:59:59 old is cancelled (OD-6, CL-14, resolved); tests required on both sides.
- Cancellation of an already-cancelled order → rejected, no state change.
- Session expires or the session ends mid-checkout → the user is asked to log in again (checkout requires login, CL-01d); the logged-in customer's basket is retained across sessions (FR-BSK-09, CL-01c).
- API/network failure on any screen → user-visible error message and a retry path.
- Redeeming more points than owned, or more than 50% of the order total → rejected; total never negative and a payment method is always required for the remainder (Clarification 2026-11-12, OD-4).
- Reward-point and payment states must never disagree with order state (e.g., confirmed order with failed payment).

---

## 2. Requirements *(mandatory)*

Source tags (defined in §0.1): `[CAP]` capstone slide text, `[CAP-S4]` slide 4 diagram, `[CON]` constitution, `[PRJ]` project-added payment methods, `[WF]` wireframe observation, `[APD]` the owner's specify request, `[CLR]` an owner answer recorded in the Clarifications section, `[PD]` design assumption. A requirement is attributed only to sources that actually state it; where a requirement combines sources, each source is named for the part it supports. "OD/CL" lists the decisions (all resolved) that govern the requirement.

### 2.1 User authentication (module: User & Authentication)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-AUTH-01 | Visitors MUST be able to register an account by providing full name, e-mail address, password and password confirmation. The e-mail address is the account's unique login identifier and is compared case-insensitively; registering with an e-mail already in use (in any letter case) MUST be rejected. Registration does not require e-mail verification (Clarification 2026-11-12, CL-18). | `[CON]` Principle I (journey 1 "plus user registration"); `[APD]` "User registration"; `[CLR]` Clarification 2026-11-12 (CL-18). The PPT text (S3 steps 1–2) names only the login page and user authentication. | OD-1 (mechanism); CL-18 (resolved) | Valid details create an account that can log in; a second registration with the same e-mail, including a different letter case, is rejected with a clear message and no second account exists. |
| FR-AUTH-02 | Registration input MUST be validated on client and server: full name, e-mail and password are required; the e-mail must be a well-formed address; the password must be 8 to 64 characters and contain at least one letter and one digit; password confirmation must match the password (Clarification 2026-11-12, CL-18). | `[CON]` Principles IV, VI, VIII; `[CLR]` Clarification 2026-11-12 (CL-18) | CL-18 (resolved) | Each invalid field shows a message (blank name, malformed e-mail, password shorter than 8 or longer than 64 characters, password without a letter or without a digit, mismatched confirmation); the server rejects the same inputs when client checks are bypassed. |
| FR-AUTH-03 | Registered customers MUST be able to log in with their e-mail address and password. Authentication uses Spring Security with JWT: a successful login returns a JWT that identifies the customer on protected requests, and the server validates it on every protected operation (Clarification 2026-11-12, OD-1). The e-mail is matched case-insensitively. | `[CAP]` S3 steps 1–2; `[CLR]` Clarification 2026-11-12 (CL-18, login identifier; OD-1, mechanism) | OD-1 (resolved); CL-18 (resolved); token lifetime and storage: §10.2 | Valid credentials grant access to protected screens, including when the e-mail is typed in a different letter case; a protected request without a valid JWT is rejected; the header account menu reflects signed-in state. |
| FR-AUTH-04 | Failed login MUST show a generic failure message that does not disclose whether the e-mail or password was wrong. | `[CON]` Principle VIII (generic client error messages); the specific non-disclosure rule is `[PD]` | CL-18 (resolved) | Wrong e-mail and wrong password produce the same message; no session/authenticated state results. |
| FR-AUTH-05 | Authenticated customers MUST be able to log out from the customer account menu. Logout clears or invalidates the active authentication state, after which protected screens are inaccessible (Clarification 2026-11-12, CL-10.2). | `[CLR]` Clarification 2026-11-12 (CL-10, CL-10.2); the slide 4 "Login & Logout" label is a capability-map observation only | OD-1 (resolved); CL-10.2 (resolved); token invalidation detail: §10.2 | After logout, opening My Orders redirects to login and a protected request made with the previous credential is not accepted by the client flow; the logout control is in the account menu. |
| FR-AUTH-06 | Customer-specific functions — My Orders (order history), reward points, saved addresses and order cancellation — MUST require authentication, and unauthenticated access MUST NOT expose customer data. Unauthenticated access SHOULD redirect to login and, after login, return the user to the requested page (design assumption A-12). Checkout and payment also require authentication (Clarification 2026-11-12, CL-01d). Protected endpoints are enforced by Spring Security with JWT (OD-1). | `[CON]` Principle VIII (server-side authorisation on protected resources); `[CAP]` S3 steps 1–2 (authentication); `[CLR]` Clarification 2026-11-12 (checkout and payment require login; OD-1); redirect and return-to-page `[PD]` | OD-1 (resolved) | Direct URL access to My Orders, checkout or payment while logged out shows no customer data and redirects to login; after login the intended page opens. |
| FR-AUTH-07 | A customer MUST be able to access only their own basket, orders, addresses and reward points. The system has a single role, customer; no admin role exists (Clarification 2026-11-12, OD-2). | `[CON]` Principle VIII; `[CLR]` Clarification 2026-11-12 | — | Requests for another customer's order/basket/addresses are denied; own data is unaffected. |
| FR-AUTH-08 | Guests (visitors who are not logged in) MUST be able to browse the landing page, catalogue, categories, brands and book details without logging in (Clarification 2026-11-12, CL-01a), and MUST be able to add books to a basket that is kept in the browser and merged into the customer's own basket when they log in (CL-01b). Login is required at checkout and payment (CL-01d, FR-AUTH-06). Whether any basket persists across sessions (CL-01c) is resolved by FR-BSK-09 (a logged-in customer's basket persists); a guest basket stays in the browser. The rule for a book present in both the guest and the customer basket is a plan-level detail. No other guest capability is assumed. | `[CLR]` Clarification 2026-11-12 (CL-01a, CL-01b, CL-01c, CL-01d); `[CAP-S4]` diagram shows "Guest User" reaching the Home Page (supporting observation, CL-10.3) | OD-1 (resolved) | A logged-out visitor can open the landing page, a category, a brand and a book detail page, sees no customer data, and can add a book to the basket. After login the guest basket items appear in the customer's basket. |

### 2.2 Landing page (cross-module presentation)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-LAND-01 | The catalogue page is the landing page: it is the initial application page and displays book availability (books shown with availability status) and the category navigation. There is no separate marketing home page (Clarification 2026-11-12, CL-03). | `[CAP]` S5 ("Landing page with the availability of books"); `[CLR]` Clarification 2026-11-12 (CL-03) | CL-03 (resolved) | Opening the application root shows the catalogue with books and categories; available and unavailable books are visibly distinguishable. |
| FR-LAND-02 | The catalogue/landing page header MUST provide navigation to the catalogue and categories, the basket, My Orders, and the account menu (login for guests; account menu with logout for customers). The header menu icon toggles the category sidebar (Clarification 2026-11-12, CL-25). Only functional navigation items are displayed: no inactive link or button appears (CL-04). | `[CAP]` S3 (catalogue, order history, basket journeys); `[WF]` Catalogue_page.PNG header (cart icon, profile icon, My Orders link); `[CLR]` Clarification 2026-11-12 (CL-04, CL-25) | CL-04, CL-25 (resolved) | Each navigation element reaches its destination; the menu icon shows and hides the category sidebar; no header element is inert. |
| FR-LAND-03 | The landing page and the catalogue page MUST present a "Recommended for You" section, populated by the FR-BSK-07 rule and shown only when it has books to show. The "Bestsellers this Month" and "New Launches" sections depicted in the wireframe are NOT built, and no sales or launch-date rule is defined for them (Clarification 2026-11-12, OD-7, CL-15). | `[WF]` Catalogue_page.PNG / S5 picture (visual observation only; not stated in the PPT text or the constitution); `[CLR]` Clarification 2026-11-12 (OD-7, CL-15) | OD-7 (resolved); CL-15 (resolved) | "Recommended for You" renders book cards when the customer has order history and is hidden otherwise; no "Bestsellers this Month" or "New Launches" section appears. |
| FR-LAND-04 | When no books exist or loading fails, the landing page MUST show an empty or error state. | `[CON]` Principle VI (loading and error handling); the empty state is `[PD]` | — | Empty dataset → empty state; failed request → visible error with retry. |

### 2.3 Book catalogue (modules: Book Catalogue, Category & Brand)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-CAT-01 | The catalogue MUST provide hierarchical category navigation (for example Non-Fiction → Self Help); selecting a category MUST list only its books and mark the category selected. An "All" entry is included as depicted in the wireframe. Selecting a parent category lists the books of that category and its sub-categories. The header menu icon toggles the category sidebar (Clarification 2026-11-12, CL-25). | `[CAP]` S3 steps 3, 5 and S6 (select category; access catalogue for each category); the "All" entry and 19 listed categories are `[WF]` Catalogue_page.PNG; `[CLR]` Clarification 2026-11-12 (CL-25, hierarchy) | Assumption A-03; CL-25 (resolved); hierarchy depth and seed list: §10.2 | Selecting "Mystery" lists only Mystery books; selecting "Non-Fiction" includes its sub-category books; "All" lists every book; selected entry is highlighted; the sidebar toggles from the header menu icon. |
| FR-CAT-02 | Customers MUST be able to browse by brand. A brand is the book's publisher (the same value shown on the detail page as "Published by"). The catalogue MUST offer a brand list made up of the publishers present in the seed data, and selecting a brand MUST list only that publisher's books. Where the brand control appears is not shown in any wireframe and is a design decision for the Plan phase. | `[CAP]` S3 step 6; `[CLR]` Clarification 2026-11-12 (brand = publisher) | CL-02 (meaning resolved; control placement not in wireframes) | Selecting a publisher from the brand list shows only books from that publisher; a brand with no books does not appear in the list; every seeded book has exactly one brand. |
| FR-CAT-03 | Each book card MUST show cover, title, author, short description, format, category/genre tags, price and delivery date. Selecting the author name filters the catalogue by that author; selecting a genre tag filters the catalogue by that genre (Clarification 2026-11-12, CL-25). No separate author profile page exists (CL-04). | `[WF]` Catalogue_page.PNG (the delivery date is also `[CAP]` S3 step 7); `[CLR]` Clarification 2026-11-12 (CL-25, CL-04) | CL-25 (resolved) | A card exposes all listed fields; selecting an author shows only that author's books; selecting a genre tag shows only that genre's books. |
| FR-CAT-04 | Customers MUST be able to search books by text; results MUST match at least title and author. | `[WF]` Catalogue_page.PNG (search box "Search you want to read here"); `[APD]` "Search for books". Not stated in the PPT text or the constitution; the matching fields are `[PD]` (A-05). | A-05 | A title term returns that book; an unmatched term shows an empty state. |
| FR-CAT-05 | The catalogue MUST provide Language, Format and Price Range filters, each defaulting to "All", combinable with category, brand, search, author, genre, sorting and paging (Clarification 2026-11-12, CL-16). The option lists come from the seed data; the price bands are a plan-level detail. | `[WF]` Catalogue_page.PNG; `[APD]` "filtering ... where established by the supplied wireframes"; `[CLR]` Clarification 2026-11-12 (CL-16). Not stated in the PPT text. | CL-16 (resolved); price bands and option lists: §10.2 | Filters narrow results; combined selections return only books satisfying all, and remain applied while sorting and paging. |
| FR-CAT-06 | The catalogue MUST provide a Sort control with four options — Relevance (default), Price low to high, Price high to low, and Title A to Z — that works together with filters and paging (Clarification 2026-11-12, CL-16). How "Relevance" orders results is a plan-level detail. | `[WF]` Catalogue_page.PNG; `[APD]` "sorting ... where established by the supplied wireframes"; `[CLR]` Clarification 2026-11-12 (CL-16). Not stated in the PPT text. | CL-16 (resolved); relevance ordering: §10.2 | Each sort option reorders results as named; the default is Relevance; the chosen sort persists across paging. |
| FR-CAT-07 | Selecting a book (title or cover) MUST open its book detail page. Author-name and genre-tag selections filter the catalogue as defined in FR-CAT-03 (Clarification 2026-11-12, CL-25). | `[CAP]` S3 step 7 ("Select the product"); `[WF]` book cards; `[CLR]` Clarification 2026-11-12 (CL-25) | CL-25 (resolved) | Selecting a book opens its detail page; the author and genre links filter instead. |
| FR-CAT-08 | A physical book with zero stock MUST be marked as unavailable and MUST NOT be addable to the basket. eBooks have unlimited availability and are never unavailable for stock reasons (Clarification 2026-11-12, CL-23). | `[CAP]` S5 (availability of books shown); `[APD]` "Unavailable products"; `[CLR]` Clarification 2026-11-12 (CL-23) | CL-23 (resolved) | A physical book with stock 0 shows an unavailable indicator and its add action is disabled or rejected with a message; an eBook is always available. |
| FR-CAT-09 | The catalogue page MUST show the "Recommended for You" section defined in FR-LAND-03, populated by the FR-BSK-07 rule (Clarification 2026-11-12, OD-7, CL-15). | `[WF]` Catalogue_page.PNG section label (visual observation); `[CLR]` Clarification 2026-11-12 | OD-7 (resolved); CL-15 (resolved) | Refer to FR-LAND-03 for requirements and acceptance criteria (section renders recommended book cards for logged-in customers with order history; hidden otherwise). |
| FR-CAT-10 | Catalogue results MUST be paged at 12 books per page, with paging working together with category, brand, search, author, genre, filters and sorting (Clarification 2026-11-12, CL-16). | `[CLR]` Clarification 2026-11-12 (CL-16) | CL-16 (resolved) | More than 12 matching books produce more than one page; each page holds at most 12 books; changing a filter or sort returns to the first page of the new results. |

### 2.4 Book details and related products

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-DET-01 | The detail page MUST show cover image(s), title, author, description, publisher, format, categories, price and language. Language is ordinary book information (Clarification 2026-11-12, CL-04). The author name and categories link as defined in FR-CAT-03 and FR-DET-06. | `[CAP]` S3 step 7 ("Select the product"); the listed fields and the front/back cover images are `[WF]` Shopping_cart.PNG observations; `[CLR]` Clarification 2026-11-12 (CL-04, CL-25) | CL-04, CL-25 (resolved) | All fields display for a seeded book; front and back cover images display where provided. |
| FR-DET-02 | The detail page MUST display a tentative delivery date in the form shown by the wireframe ("Delivery by Mon, 21 Jul"). The date is calculated as follows (Clarification 2026-11-12, OD-5): for a physical book (any format other than eBook, e.g. Paperback or Hard Cover) it is the current date plus 5 days; for an eBook it is the current date. The rule is the same on every screen that shows a delivery date (cards, detail, basket, related reads, confirmation). Stock level does not change the date. | `[CAP]` S3 step 7 (tentative delivery date); the display form is `[WF]` Shopping_cart.PNG; `[CLR]` Clarification 2026-11-12 (calculation rule) | OD-5 (resolved) | A physical book shows today + 5 days and an eBook shows today, in the "Mon, 21 Jul" style, on every screen that shows a delivery date; tests use a fixed clock to assert the exact dates. |
| FR-DET-03 | The detail page MUST provide an Add to Cart action that adds one unit and updates the header basket count. Visitors who are not logged in may use it (CL-01b). | `[CAP]` S3 step 8 and S7 ("Add the product(s) to basket"); `[WF]` Shopping_cart.PNG button and badge; `[CLR]` Clarification 2026-11-12 (guest add) | — | Clicking adds the book and increments the badge, with or without login; disabled for unavailable books. |
| FR-DET-04 | The detail page MUST display related products ("Related Reads" in the wireframe), each entry showing cover, title, author, description, format, categories, price and delivery date. Related products are the other books in the same category as the displayed book, never the displayed book itself (Clarification 2026-11-12, OD-7). | `[CAP]` S3 step 7 (related products appear for selection); the "Related Reads" label and per-entry fields are `[WF]` Shopping_cart.PNG; `[CLR]` Clarification 2026-11-12 (OD-7) | OD-7 (resolved) | List shows only books sharing a category with the displayed book, excluding the displayed book; a book with no same-category books shows an empty state. |
| FR-DET-05 | Selecting a related product MUST open its detail page. | `[CAP]` S3 "Related products appear for selection" | — | Click opens the related book's page. |
| FR-DET-06 | The detail page MUST show breadcrumb navigation that follows the category hierarchy (for example "Home / Non-Fiction / Self Help" as depicted in the wireframe); each breadcrumb link navigates to its level (Clarification 2026-11-12, CL-25). | `[WF]` Shopping_cart.PNG (visual observation); `[CLR]` Clarification 2026-11-12 (CL-25) | CL-25 (resolved) | Each breadcrumb link opens the catalogue at that level; the last level is the book's category. |
| FR-DET-07 | The detail page MUST show an "About the writer" author section (author name, bio, and other books) and rating stars near the price, amended by AMD-002 and AMD-003. Copies-sold information is excluded. | `[WF]` Shopping_cart.PNG; `[CLR]` AMD-002, AMD-003 | AMD-002, AMD-003 (resolved) | The detail page displays the "About the writer" section (author name, bio, and other books) and average rating stars; copies-sold information is not displayed. |
| FR-DET-08 | Customer reviews (rating 1 to 5 and optional comment submission) MUST be supported on the detail page (AMD-002). Wishlist and Follow Writer are client-side only features persisted through browser localStorage without backend APIs (AMD-002). | `[WF]` Shopping_cart.PNG; `[CLR]` AMD-002 | AMD-002 (resolved) | Customers can submit and view book reviews and ratings on the detail page; wishlist and follow-writer interactions are saved locally. |
| FR-DET-09 | A non-existent book MUST produce a not-found state with navigation back. | `[CON]` Principle IV (correct status codes) and VI (user-visible errors); the not-found screen is `[PD]` | — | Unknown id shows not-found, not an error dump. |

### 2.5 Shopping basket (module: Shopping Cart)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-BSK-01 | Customers, including visitors who are not logged in, MUST be able to add books to the basket; adding a book already present MUST increase its quantity. A basket MUST hold at most 10 units of one book and, for a physical book, never more than the available stock; an attempt to exceed either limit is rejected with a message (Clarification 2026-11-12, CL-23). Adding to the basket does not reserve stock. A guest basket is kept in the browser and merged into the customer's basket at login (CL-01b); the rule for a book present in both is a plan-level detail (§10.2). | `[CAP]` S3 step 8 and S7 (add products to basket); `[CLR]` Clarification 2026-11-12 (guest add and merge; CL-23); quantity-merge behaviour is `[PD]` | CL-23 (resolved) | Second add yields quantity 2, not a duplicate line; the 11th unit of a book, or a unit beyond physical stock, is rejected; stock is unchanged by adding to the basket. A guest basket's items are present in the customer's basket after login. |
| FR-BSK-02 | The basket MUST list each item with cover, title, author, description, format, categories, unit price, delivery date and a quantity control. | `[WF]` Payment.PNG "Shopping Cart" panel | — | All fields visible for each line. |
| FR-BSK-03 | Customers MUST be able to update quantities (increase/decrease, minimum 1, maximum per FR-BSK-01); line and total amounts MUST recompute. | `[CAP]` S7 (heading "Shopping Cart for update"); `[WF]` Payment.PNG quantity steppers; `[APD]` "Update product quantities"; `[CLR]` Clarification 2026-11-12 (CL-23, maximum). The minimum of 1 is `[PD]`. | CL-23 (resolved) | Quantity 1→3 updates line and total; cannot go below 1 or above min(10, stock). |
| FR-BSK-04 | Customers MUST be able to remove a product from the basket. | `[APD]` "Remove products". Design assumption `[PD]`: the PPT text states no removal, and no removal control is visible in any wireframe. | `[PD]` control design | Removed line disappears and totals recompute; last removal shows empty basket. |
| FR-BSK-05 | The basket MUST display item prices, the subtotal, tax, the delivery charge and the order total using these rules (Clarification 2026-11-12, CL-07): tax = 5% of the book subtotal; delivery = ₹40 per order when the order contains any physical book, free for an eBook-only order; total = subtotal − coupon discount (always 0, no coupons) + tax + delivery. Amounts use consistent rounding to two decimal places. No discount line, coupon control or real GST is shown. The sample totals in the wireframes (₹470, ₹580) are illustrative and MUST NOT be hardcoded. | `[APD]` "Display relevant pricing and totals"; `[WF]` Payment.PNG "Grand Total" panel (visual only); `[CLR]` Clarification 2026-11-12 (CL-06, CL-07, CL-08) | CL-06, CL-07, CL-08 (resolved) | Subtotal ₹1,000.00 physical-only → tax ₹50.00, delivery ₹40.00, total ₹1,090.00; the same basket as eBook-only → tax ₹50.00, delivery ₹0.00, total ₹1,050.00; a mixed basket pays ₹40 delivery; the total equals the sum of the displayed lines. |
| FR-BSK-06 | The header basket icon SHOULD show the number of basket items. | `[WF]` cart badge (visual observation); count semantics are `[PD]` | — | Badge matches basket content after every change. |
| FR-BSK-07 | The basket MUST display recommendations based on the customer's order history: books in the same categories as the books the customer has previously ordered, excluding books already in the basket. A customer with no order history sees an empty state and a guest sees no recommendations (Clarification 2026-11-12, OD-7). | `[CAP]` S3, S7; `[CLR]` Clarification 2026-11-12 (OD-7) | OD-7 (resolved) | With history, recommendations share a category with a previously ordered book and exclude basket items; without history, an empty state; a guest sees none. |
| FR-BSK-08 | Basket availability, stock and price MUST be revalidated at checkout and again immediately before the order is placed; changes MUST be reported to the customer and block payment until resolved or acknowledged (Clarification 2026-11-12, CL-23). | Design/technical requirement `[PD]`, derived from `[CON]` Principle V (data consistency); `[CLR]` Clarification 2026-11-12 (CL-23). Not a capstone requirement. | CL-23 (resolved) | Reducing stock or changing a price after adding to the basket then proceeding shows a message and blocks payment until resolved; stock reduced to below the basket quantity cannot be ordered. |
| FR-BSK-09 | A logged-in customer's basket MUST be saved against their account and MUST persist across sessions, including after logout and re-login (Clarification 2026-11-12, CL-01c). A guest basket is kept only in the browser (CL-01b). | `[CLR]` Clarification 2026-11-12 (CL-01c) | — | After the customer logs out and logs in again (or the session expires), basket contents are retained. A guest basket is not stored against any account until login. |

### 2.6 Orders (module: Order Management)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-ORD-01 | Customers MUST be able to browse their previous orders (My Orders) showing order reference, date, status ("Placed" or "Cancelled" only; Clarification 2026-11-12, CL-24), items and total. | `[CAP]` S3 and S6 ("User Browses Order History"); `[WF]` "My Orders" header link. The listed fields and newest-first order are `[PD]`. | CL-24 (resolved) | Only the customer's orders appear, newest first; empty state when none. |
| FR-ORD-02 | Customers SHOULD be able to open an order to see its items, address, payment method, amounts and status. | `[PD]` design assumption (no source states an order-detail view) | — | Detail matches the placed order. |
| FR-ORD-03 | Buy Again MUST add the order's books that are still in the catalogue and available (a physical book with stock above zero, or an eBook) to the basket at their current price and current delivery date (never the old price). A book that is out of stock or no longer in the catalogue MUST NOT be added and MUST be listed in a message; if the ordered quantity exceeds the stock (or the 10-unit basket limit), the available quantity is added and the customer is told (Clarification 2026-11-12, CL-24, CL-23). | `[CAP]` S3, S6; `[CLR]` Clarification 2026-11-12 (CL-24, CL-23) | CL-24, CL-23 (resolved) | Unavailable book is reported and not added; available ones appear in basket at current prices; an over-stock quantity is reduced to the stock with a message. |
| FR-ORD-04 | Customers MUST be able to cancel an order only within 48 hours of the time the order was placed. The same rule applies to every payment method, including Cash on Delivery; there is no COD exception. Attempts after the window MUST be rejected with a clear message and no state change. The window is exclusive: an order is cancellable only while it is strictly less than 48 hours old, and a request at exactly 48:00:00 or later is rejected (Clarification 2026-11-12, CL-14). | `[CAP]` S3 step 12; `[CON]` Scope; `[CLR]` Clarification 2026-11-12 (OD-6 anchor, COD and boundary) | OD-6 (resolved), CL-14 (resolved), CL-12, CL-13 | An order 47:59:59 old is cancelled; an order exactly 48:00:00 old or older is rejected; the same holds for a COD order; tests use a fixed clock. |
| FR-ORD-05 | A successful cancellation MUST, in one atomic operation: set the order status to "Cancelled"; return the ordered physical-book quantities to stock (eBooks are never counted); restore any reward points redeemed on the order to the customer's balance; and update the payment record (a "Paid" payment becomes "Refunded" as a simulated record with no money movement; a COD "Pending (pay on delivery)" payment becomes "Cancelled") (Clarification 2026-11-12, CL-13, CL-23). | `[CON]` Principle V; `[CLR]` Clarification 2026-11-12 (CL-13, CL-23) | CL-13, CL-23 (resolved), OD-4 | After cancellation the order, stock, points balance and payment record show the stated values; a failure mid-cancellation leaves all records unchanged. |
| FR-ORD-06 | Cancelling an already-cancelled order, or another customer's order, MUST be rejected with no state change. | `[CON]` Principle VIII | — | Second cancel returns a rejection; foreign order is denied. |
| FR-ORD-07 | Order modification, order return and return shipment (shown in the architecture diagram) are NOT built; they are out of scope (Clarification 2026-11-12, CL-10). | `[CAP-S4]` diagram observation; Category B (CL-10.14, CL-10.16, CL-10.28); `[CLR]` Clarification 2026-11-12 (CL-10) | CL-10 (resolved) | No order-modification, return or return-shipment control or API exists. |
| FR-ORD-08 | Physical books have finite integer stock; eBooks have unlimited availability and are never decremented. Stock is checked at checkout and again before order placement, decreased in the same atomic operation as a successful order placement, and restored by a successful cancellation (FR-ORD-05). Concurrent orders for the same book MUST be handled safely so that stock never becomes negative and two orders cannot both take the last unit. | `[CLR]` Clarification 2026-11-12 (CL-23); `[CON]` Principle V | CL-23 (resolved); concurrency mechanism: §10.2 | Two simultaneous orders for the last unit of a book result in exactly one placed order and one rejection with a message; stock never falls below zero; an eBook order leaves its availability unchanged; a cancelled order restores physical stock. |

### 2.7 Checkout (modules: Order, User)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-CHK-01 | Checkout MUST start from a non-empty basket and present: the basket items, an Address section (omitted when the basket contains only eBooks, per AMD-004), a Grand Total summary and a Pay Now action. Checkout and payment require login (Clarification 2026-11-12, CL-01d); a visitor who is not logged in is sent to login first and then returned to checkout. | `[CAP]` S3 steps 9–10 and S8 (select address; initiate payment); `[WF]` Payment.PNG; `[CLR]` Clarification 2026-11-12 (login at checkout); AMD-004 | AMD-004 (resolved) | Empty basket cannot reach checkout; the required regions are present (address section shown for physical/mixed orders, omitted for eBook-only orders per AMD-004); a logged-out visitor opening checkout is redirected to login and, after login, reaches checkout with the merged basket. |
| FR-CHK-02 | For orders containing physical books, the delivery-address form MUST capture: full name (required); phone (required, exactly 10 digits); address line 1 (required); address line 2 (optional); city (required); state (required); PIN code (required, exactly 6 digits); country (required, default India). An address is not required for eBook-only checkout (AMD-004). | `[WF]` Payment.PNG (layout only); `[CLR]` Clarification 2026-11-12 (CL-17); AMD-004 | CL-17 (resolved); AMD-004 (resolved); seeded state list: §10.2 | For physical/mixed orders, all listed fields are present; country defaults to India; no e-mail, first/last name or country-code field exists; for eBook-only orders, address entry is bypassed. |
| FR-CHK-03 | Customers MUST be able to save multiple delivery addresses, select a saved address during checkout (which populates the form) and manage their saved addresses (add, edit, delete). With none saved, checkout explains that none exist (Clarification 2026-11-12, CL-17). | `[CAP]` S3 step 9 (select the address for delivery); `[WF]` Payment.PNG "Use Saved Address" checkbox; `[APD]` "Support saved delivery addresses where required by the wireframe"; `[CLR]` Clarification 2026-11-12 (CL-17) | CL-17 (resolved) | A customer can hold several saved addresses; selecting one fills the fields; edits and deletions are reflected in the saved list; no saved address shows a message. |
| FR-CHK-04 | Entered address data MUST be validated client and server side per FR-CHK-02 (required fields; phone exactly 10 digits; PIN exactly 6 digits); invalid data MUST block progress with inline errors (Clarification 2026-11-12, CL-17). | `[CON]` Principles IV, VI; `[CLR]` Clarification 2026-11-12 (CL-17) | CL-17 (resolved) | A 9- or 11-digit phone, a 5- or 7-digit PIN, non-digit characters, or a blank required field block Pay Now with an inline error; the server rejects the same input. |
| FR-CHK-05 | The order summary MUST show the item prices (n items), subtotal, tax, delivery charge and the overall payable total per FR-BSK-05, updated whenever quantities or applied reward points change. No coupon discount line is shown. Reward points are applied after the total is calculated (FR-RWD-02, FR-RWD-03). | `[WF]` Payment.PNG "Grand Total" panel (layout only); `[APD]` "Display order summary, applicable discounts and total amount" (no discount applies because coupons are excluded); `[CLR]` Clarification 2026-11-12 (CL-06, CL-07, CL-08) | CL-06, CL-07, CL-08 (resolved), OD-4 | The payable total equals subtotal + tax + delivery minus applied points; no discount or coupon line appears; points line appears only when points are applied. |
| FR-CHK-06 | Coupon entry (the "Apply Coupon" control), coupon validation and coupon discounts are NOT built: there is no coupon control, coupon entity or coupon API (Clarification 2026-11-12, CL-06). Reward points (FR-RWD) are independent of coupons. | `[WF]` Payment.PNG "Apply Coupon" field and Discount line (visual observation); `[CAP-S4]` "Coupons" (diagram label, Category B, CL-10.19); `[CLR]` Clarification 2026-11-12 (CL-06) | CL-06 (resolved) | No coupon field, button, entity or endpoint exists anywhere in the application. |
| FR-CHK-07 | Gift/reward point redemption MUST be offered during checkout in an area displayed below the order summary. It shows the available balance, the maximum redeemable points and an input for the points to redeem; the rules of FR-RWD apply (Clarification 2026-11-12, CL-11). | `[CAP]` S3 step 10, S8; `[CLR]` Clarification 2026-11-12 (CL-11) | OD-4 (resolved); CL-11 (resolved) | The redemption area appears below the order summary with the three listed elements and its result is reflected in the summary. |
| FR-CHK-08 | Pay Now MUST proceed to payment-method selection and payment only when basket, address and amounts are valid. | `[CAP]` S3 step 10 (initiate payment); the validity gating is `[CON]` Principles IV and VI (validation) | — | Invalid state blocks navigation with a visible reason. |
| FR-CHK-09 | Checkout MUST show breadcrumb navigation as depicted in the wireframe; breadcrumb links navigate correctly (Clarification 2026-11-12, CL-25: every navigation element works). | `[WF]` Payment.PNG (visual observation); `[CLR]` Clarification 2026-11-12 (CL-25) | CL-25 (resolved) | Each breadcrumb link navigates to its destination. |

### 2.8 Payment (module: Payment Management) — dummy processing only

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-PAY-01 | The system MUST offer exactly Credit Card, Debit Card, UPI and Cash on Delivery. Credit Card and Debit Card are capstone requirements; UPI and Cash on Delivery are project-added. | `[CAP]` S3 step 10, S9; `[PRJ]` UPI, COD; `[CON]` Principle I; `[CLR]` Clarification 2026-11-12 (CL-05) | CL-05 (resolved) | Four methods selectable; no other method offered. |
| FR-PAY-02 | The Wallet option shown in the payment wireframe is NOT built: there is no wallet balance, top-up, wallet transaction or Wallet payment option (Clarification 2026-11-12, CL-05). | `[WF]` Payment_screen.PNG; `[CAP-S4]` "Gift & Wallet"; `[CLR]` Clarification 2026-11-12 (CL-05) | CL-05 (resolved) | No Wallet option appears on the payment screen and no wallet endpoint exists. |
| FR-PAY-03 | The payment screen MUST show the title "Complete Payment", the payable amount, the selectable payment methods, the form for the selected method and a Pay Now action. | `[WF]` Payment_screen.PNG | — | All elements present. |
| FR-PAY-04 | Card payment (credit and debit) MUST capture card number (format XXXX-XXXX-XXXX-XXXX), name on card, CVV and expiry (MM/YYYY) and validate them. Normal valid test data produces a simulated success; predefined test values produce a simulated failure (Clarification 2026-11-12, CL-21). | `[WF]` Payment_screen.PNG; `[CLR]` Clarification 2026-11-12 (CL-21) | A-07; CL-21 (resolved); the predefined failure values: §10.2 | Malformed number/CVV, empty name or past expiry blocks submission with inline errors; valid test data succeeds; each predefined failure value is rejected as a simulated failure. |
| FR-PAY-05 | UPI payment MUST capture a simulated UPI ID and simulate the outcome; no real UPI provider is integrated (Clarification 2026-11-12, CL-21). | `[PRJ]`; `[CLR]` Clarification 2026-11-12 (CL-21) | CL-21 (resolved); UPI ID format and failure value: §10.2 | A valid simulated UPI ID produces success; a predefined failure UPI ID produces a failure per FR-PAY-08; a blank or malformed ID blocks submission. |
| FR-PAY-06 | Cash on Delivery MUST NOT require payment details. A COD order's 48-hour cancellation window runs from order placement, the same as every other method (Clarification 2026-11-12, OD-6). The COD order is placed with status "Placed" and payment state "Pending (pay on delivery)"; nothing is collected in the system (Clarification 2026-11-12, CL-12). | `[PRJ]`; `[CLR]` Clarification 2026-11-12 (cancellation window, COD states) | OD-6 (resolved), CL-12 (resolved) | A COD order is created with status "Placed" and payment state "Pending (pay on delivery)" and can be cancelled within 48 hours of placement. |
| FR-PAY-07 | All payment processing MUST be simulated: no real gateway and no real money movement. | `[CON]` Scope | — | No external payment call exists; verified by review and tests. |
| FR-PAY-08 | Simulated payment MUST produce both success and failure outcomes in a repeatable way for testing (card: test values; UPI: simulated ID), and no real credential is collected or stored (Clarification 2026-11-12, CL-21). | `[APD]` "Unsuccessful dummy payment outcomes"; `[CON]` Principle VII (testing); `[CLR]` Clarification 2026-11-12 (CL-21) | CL-21 (resolved) | Both outcomes are reproducible in test runs with documented test values. |
| FR-PAY-09 | The payable amount shown MUST equal the order total (subtotal + tax + delivery, per FR-BSK-05) minus applied reward points. The wireframe sample amount is illustrative only (CL-08). | Design/technical requirement `[PD]`, derived from `[CON]` Principle V (consistency); `[WF]` Payment_screen.PNG; `[CLR]` Clarification 2026-11-12 (CL-07, CL-08) | CL-07, CL-08 (resolved) | Payment-screen amount equals the checkout payable total. |
| FR-PAY-10 | On failure the customer MUST see a failure message; no confirmed order is created, points are not consumed, the basket is retained and retry or another method is possible. | `[CON]` Principle V (consistency) and VI (user-visible errors); retry and alternative-method behaviour is `[PD]` | — | Verified for each method. |
| FR-PAY-11 | Repeated submission of the same payment MUST NOT create duplicate orders or payments. | `[CON]` Principle V | — | Double-click yields one order and one payment. |
| FR-PAY-12 | Every payment attempt and its outcome MUST be recorded and consistent with the order state. A payment record has one of the states "Paid" (card, debit, UPI success), "Pending (pay on delivery)" (COD), "Failed", "Refunded" (simulated, after cancelling a "Paid" order) or "Cancelled" (after cancelling a COD order) (Clarification 2026-11-12, CL-12, CL-13). | `[CON]` Principle V; `[CLR]` Clarification 2026-11-12 | CL-12, CL-13 (resolved) | No confirmed order with a failed payment; no paid payment without an order; payment states match the order state after placement and after cancellation. |
| FR-PAY-13 | Full card numbers and CVV MUST NOT be stored or logged. | `[CON]` Principle VIII | — | Persistence and log inspection show neither value. |

### 2.9 Reward points (module: Reward Points Management)

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-RWD-01 | A customer MUST see their available reward-point balance in the redemption area below the order summary at checkout (Clarification 2026-11-12, CL-11). | `[CAP]` S3 step 10 and S8 ("Redeem gift points"); `[CLR]` Clarification 2026-11-12 (CL-11) | CL-11 (resolved) | Balance shown at checkout in the redemption area. |
| FR-RWD-02 | A customer MUST be able to apply points at checkout, reducing the payable amount, with the reduction visible in the summary. One reward point is worth ₹1 (Clarification 2026-11-12, OD-4). | `[CAP]` S3, S8; `[CLR]` Clarification 2026-11-12 (point value) | OD-4 (value resolved) | Applying N points reduces the payable total by ₹N. |
| FR-RWD-03 | Redemption MUST NOT exceed the customer's balance and MUST NOT exceed 50% of the eligible order total, where the eligible order total is subtotal + tax + delivery charge (Clarification 2026-11-12, CL-07). The redemption area shows the maximum redeemable points. Because of the 50% cap the payable total can never reach zero through points, so a payment method is always required for the remainder. How a fractional cap is rounded is a plan-level detail (§10.2). | `[CON]` Principle V; `[CLR]` Clarification 2026-11-12 (cap; CL-07; CL-11) | OD-4 (resolved), CL-07 (resolved) | Attempts to redeem more than the balance, or more than 50% of the eligible total, are rejected with a message. Redeeming exactly the permitted maximum succeeds and leaves a payable amount greater than zero. |
| FR-RWD-04 | Points MUST be deducted only when the order/payment succeeds, in one atomic operation with it. | `[CON]` Principle V | — | Failure leaves the balance unchanged. |
| FR-RWD-05 | A zero balance MUST show redemption as unavailable with an explanation. | `[PD]` | — | Zero-balance state verified. |
| FR-RWD-06 | Customers do NOT earn reward points from purchases: a customer's balance comes only from reproducible seed data, and every new account starts with a fixed seed-defined balance (Clarification 2026-11-12, OD-4). Points can only be redeemed. The point value (₹1 per point) and the redemption cap (50% of the order total) are decided in FR-RWD-02 and FR-RWD-03. The starting balance is 1,000 points (₹1,000) per new account, there is no minimum redemption (any whole number of points from 1 up to the lower of the balance and the 50% cap) and points do not expire (Clarification 2026-11-12, OD-4). Reward points redeemed on an order are restored to the customer's balance when that order is cancelled (FR-ORD-05; Clarification 2026-11-12, CL-13). | `[CON]` Scope; `[CLR]` Clarification 2026-11-12 (no earning) | **OD-4** (resolved), CL-13 | A new account shows a balance of 1,000 points (₹1,000). Redeeming 1 point succeeds; no order, payment or other action increases a balance; points never expire. Boundary tests: redeem the lower of balance and the 50% cap succeeds, one point more is rejected. |

### 2.10 Payment and purchase confirmation

| ID | Requirement | Source / Ref | Deps / Rules / OD-CL | Acceptance criteria |
|----|-------------|--------------|----------------------|---------------------|
| FR-CNF-01 | After successful payment (or accepted COD placement), the purchase-confirmation screen MUST show a success indicator, the message that the purchase of the listed reads is successful, and each purchased book (cover, title, author, description, format, categories, price, delivery date). | `[CAP]` S3 step 12 (purchase confirmation); the success indicator, message and per-book fields are `[WF]` Purchase_confirmation.PNG | — | All elements present for every purchased book. |
| FR-CNF-02 | The screen MUST provide a "Continue your Shopping" action that returns to browsing. | `[WF]` Purchase_confirmation.PNG button; `[APD]` "an appropriate continuation action, such as Continue Shopping". Not stated in the PPT text. | — | Click opens the landing/catalogue page. |
| FR-CNF-03 | Payment confirmation and purchase confirmation are separate steps. After a successful online payment (Paid) a payment confirmation is displayed first; the purchase confirmation (FR-CNF-01) is displayed afterwards. For COD the payment is recorded as Pending and the order is placed successfully, so no payment-success confirmation is shown (Clarification 2026-11-12, CL-21). | `[CAP]` S3 steps 11–12; `[CLR]` Clarification 2026-11-12 (CL-21) | CL-21 (resolved) | A successful card, debit or UPI payment shows a payment-success message and then the purchase confirmation; a COD order goes to purchase confirmation with payment shown as pending. |
| FR-CNF-04 | After success, the purchased items MUST be removed from the basket, the header count updated, and the order MUST appear in My Orders. | `[PD]` | — | Basket empty, badge cleared, order listed. |
| FR-CNF-05 | The confirmation SHOULD show an order reference. | `[PD]` (not in wireframe) | — | Reference matches My Orders. |
| FR-CNF-06 | Refreshing or navigating back from the confirmation MUST NOT create another order. | `[CON]` Principle V | — | Verified by refresh/back test. |

### 2.11 Key Entities *(conceptual only; no tables or relationships are finalised here — see OD-12 and the Plan phase)*

- **Customer**: registered person; identity (full name; unique e-mail address used as the login identifier, compared case-insensitively; Clarification 2026-11-12, CL-18), credentials (never retrievable), saved addresses, reward-point balance.
- **Address**: full name, phone (10 digits), address line 1, optional address line 2, city, state, PIN code (6 digits) and country (default India), as in FR-CHK-02; a customer may save several (Clarification 2026-11-12, CL-17).
- **Book**: title, author, description, publisher, format, language, price, cover image(s), availability (physical books carry an integer stock quantity; eBooks do not), tentative delivery date (calculated, not stored). No rating or copies-sold field (Clarification 2026-11-12, CL-04, CL-23).
- **Category**: hierarchical grouping of books (a category may have a parent category, e.g. Non-Fiction → Self Help; Clarification 2026-11-12, CL-25); "All" is a navigation entry, not stored data. **Brand**: the book's publisher (Clarification 2026-11-12, CL-02); not a separate entity from the publisher value on a book.
- **Basket / Basket item**: a customer's selected books and quantities.
- **Order / Order item**: purchase record with status, placement time, price snapshot, address, amounts.
- **Payment**: attempt and outcome for an order; method type; no PAN/CVV retained.
- **Reward-point record**: balance and movements (redeem; restore on order cancellation, Clarification 2026-11-12, CL-13). There is no earn movement (Clarification 2026-11-12, OD-4); the starting balance comes from seed data: 1,000 points per new account, no minimum redemption, no expiry (Clarification 2026-11-12, OD-4).
- **Reviews & Author Profiles**: `review` entity (rating 1..5, optional comment, customer_id, book_id; AMD-002) and `author_profile` entity (author name, biography; AMD-003).
- **Frontend-only features**: Wishlist and Follow Writer are client-side only features persisted through browser `localStorage` (AMD-002); no backend APIs or database entities exist for them.
- **Not entities**: coupon and wallet records do not exist (Clarification 2026-11-12, CL-05, CL-06). Only entities and fields required by confirmed functionality are created (OD-12).

### 2.12 Backend module coverage (constitution Principle II)

| Module | Representative requirements |
|--------|------------------------------|
| User and Authentication Management | FR-AUTH-01..08, FR-CHK-02..04 (addresses) |
| Book Catalogue Management | FR-LAND-01..04, FR-CAT-01..10, FR-DET-01..09 |
| Category and Brand Management | FR-CAT-01, FR-CAT-02, FR-CAT-07 |
| Shopping Cart Management | FR-BSK-01..09, FR-DET-03 |
| Order Management | FR-ORD-01..08, FR-CHK-01..09, FR-CNF-01..06 |
| Payment Management | FR-PAY-01..13 |
| Reward Points Management | FR-RWD-01..06, FR-CHK-07 |

---

## 3. Non-Functional Requirements *(testable; the performance, browser and accessibility figures are development targets decided in CL-20, not guarantees; no production uptime SLA applies)*

| ID | Requirement (derived from constitution) | Acceptance criteria |
|----|------------------------------------------|---------------------|
| NFR-SEC-01 | Passwords MUST be stored only as salted, adaptive hashes. | Inspection of stored data shows no plaintext or fast-hash passwords. |
| NFR-SEC-02 | Authentication uses Spring Security with JWT (Clarification 2026-11-12, OD-1): passwords are hashed, the JWT is validated on every protected request, and authentication and authorisation MUST be enforced server-side on every protected operation; customer resources are accessible only by their owner. Only customer authentication exists (no admin roles). | Tests with another customer's identity, with no identity, and with an invalid or tampered JWT are denied. |
| NFR-SEC-03 | Credentials, secrets and connection strings MUST come from environment-specific configuration and MUST NOT be committed. | Repository scan finds none; the app starts using supplied environment configuration. |
| NFR-SEC-04 | Full card numbers and CVV MUST NOT be persisted or logged; secrets, tokens, passwords and payment details MUST NOT be logged. | Storage and log inspection after test payments. |
| NFR-SEC-05 | All user input MUST be validated server-side; injection-safe data access and context-aware output encoding MUST be used. | Malicious input tests produce rejections or inert output. |
| NFR-SEC-06 | Errors returned to clients MUST be generic with no internals; details are logged server-side only. | Forced-error tests show no stack traces or system details to the client. |
| NFR-SEC-07 | Dependencies MUST be maintained and scanned for known critical/high vulnerabilities. | Scan report attached to the delivery. |
| NFR-DAT-01 | Customer, book, basket, order, payment and reward-point data MUST remain mutually consistent. | Consistency checks after each journey and failure scenario. |
| NFR-DAT-02 | Operations spanning several records (order placement, payment, point redemption, cancellation, stock changes) MUST be all-or-nothing. | Injected mid-operation failure leaves no partial state. |
| NFR-DAT-03 | Order and payment states MUST never contradict (see FR-PAY-12). | State-matrix tests. |
| NFR-DAT-04 | The data model MUST be reviewed and approved before backend implementation (constitution Principle V; OD-12) and contain only the entities and fields required by confirmed functionality; seed/test data MUST be reproducible and contain no real personal or payment data. | Approval recorded; seed run is repeatable; no entity exists for a wireframe-only feature (coupon, wallet, wishlist, review). |
| NFR-DAT-05 | The database schema MUST be managed by version-controlled Flyway SQL migration scripts, with Hibernate schema validation enabled; Hibernate automatic schema update or create MUST NOT be the normal schema-management approach (Clarification 2026-11-12, OD-3). | A clean database is built from the migrations alone and passes Hibernate validation; no auto-DDL setting is enabled. |
| NFR-API-01 | APIs MUST follow REST conventions with correct methods and status codes. | Contract tests per endpoint. |
| NFR-API-02 | All API responses MUST use consistent structures, including one consistent error structure. | Contract tests confirm shape. |
| NFR-API-03 | Requests MUST be validated; invalid requests return client errors. | Negative tests per endpoint. |
| NFR-API-04 | The OpenAPI contract (`openapi.yaml`) MUST be authored contract-first and validated in Swagger Editor, kept in the repository, and approved before backend implementation; each implemented endpoint MUST conform to that contract and be tested against it (Clarification 2026-11-12, OD-10). | The contract validates in Swagger Editor; approval is recorded; traceability matrix endpoint↔contract↔test. |
| NFR-FE-01 | Every data-loading screen MUST show a loading state and a user-visible, actionable error message on failure. | Simulated slow and failing requests per screen. |
| NFR-FE-02 | Every form MUST validate input on the client in addition to server validation. | Invalid input blocked per form. |
| NFR-FE-03 | UI MUST be assembled from reusable components with a consistent folder structure and naming. | Review against the constitution Principle VI checklist. |
| NFR-FE-04 | Layouts MUST be responsive at three sizes — mobile (below 640px), tablet (640–1023px) and desktop (1024px and up) — as in §4.5 (Clarification 2026-11-12, CL-19). Breakpoints are approximate. | Verification record per screen at each of the three sizes. |
| NFR-TST-01 | Each business rule MUST have an explicit test: cancellation boundary (47:59:59 accepted, 48:00:00 rejected), reward limits (balance and 50% cap), pricing (5% tax, ₹40 delivery or free for eBook-only, rounding), stock and basket limits (10 per book, stock cap, concurrent orders), Buy Again, payment-method handling and simulated failures, address validation (10-digit phone, 6-digit PIN), unavailable-product handling. | Test list maps rule → test. |
| NFR-TST-02 | Backend tests MUST use JUnit 5, Mockito and Spring Boot Test: unit tests, API integration tests and database-persistence verification (Clarification 2026-11-12, OD-8). | Test run and report. |
| NFR-TST-03 | Frontend flows (buttons, forms, navigation, basket, checkout, payment) MUST be verified with Vitest and React Testing Library (Clarification 2026-11-12, OD-8). | Documented test results. |
| NFR-TST-04 | Each customer journey (§6.1 traceability) MUST have an end-to-end test case with dummy data, implemented with Playwright (Clarification 2026-11-12, OD-8). | All journeys pass before completion. |
| NFR-TST-05 | Known defects MUST be fixed or recorded as owner-accepted exceptions before completion. | Defect list closed. |
| NFR-MNT-01 | Code MUST be organised with clear separation of responsibilities and consistent conventions; no dead or duplicated code. | Review checklist. |
| NFR-MNT-02 | README, API documentation, specs and deployment notes MUST be kept in sync with changes. | Docs updated in the same pull request as the change. |
| NFR-LOC-01 | The complete application (frontend, backend and PostgreSQL) MUST run locally on loopback (127.0.0.1) with frontend–backend integration, environment configuration, seed data, and verified database connectivity (Clarification 2026-11-12, OD-11). | Fresh-environment run following the written setup and execution instructions. |
| NFR-LOC-02 | Written setup, run and deployment instructions MUST exist. | A reviewer follows them successfully. |
| NFR-LOC-03 | The design MUST remain cloud-deployable; actual cloud deployment is optional, attempted only if a suitable free-tier environment is available, and is never a mandatory dependency (Clarification 2026-11-12, OD-11). | Configuration is environment-driven; the application is complete and demonstrable without any cloud deployment. |
| NFR-LOC-04 | Refer to NFR-LOC-01 and NFR-LOC-02 for authoritative local setup and execution instructions (environment configuration, start-up, seed data and test values) (Clarification 2026-11-12, OD-11). | A reviewer starts the application from the instructions alone. |
| NFR-PERF-01 | In normal local development conditions a typical API response MUST take under 1 second (development target, not a guarantee; Clarification 2026-11-12, CL-20). | Measured on seed data in the local development environment. |
| NFR-PERF-02 | In normal local development conditions the initial frontend load MUST take under 3 seconds (development target; Clarification 2026-11-12, CL-20). | Measured in the local development environment. |
| NFR-BRW-01 | The application MUST work in the latest versions of Chrome, Edge and Firefox (Clarification 2026-11-12, CL-20). | The customer journeys are exercised in each of the three browsers. |
| NFR-ACC-01 | Accessibility basics MUST be met: keyboard-accessible controls, proper form labels, appropriate colour contrast and alternative text for meaningful images (Clarification 2026-11-12, CL-20). No production uptime SLA is required. | Keyboard-only walk-through of each journey; every form control has a label; contrast and alt text checked per screen. |

### 3.1 Capstone delivery expectations

| ID | Requirement | Source / Ref | Acceptance criteria |
|----|-------------|--------------|---------------------|
| DEL-01 | Work MUST be maintained in a personal GitHub repository using meaningful branches and commits, pushed and merged via Pull Requests; the evaluator MUST be granted access. | `[CAP]` S11–S13; `[CON]` Delivery | PR history visible to the evaluator. |
| DEL-02 | Pull Requests that change APIs MUST include API documentation. | `[CAP]` S12 step 9; `[CON]` Principle IV | PR contains the documentation. |
| DEL-03 | A demonstration video explaining the development and implementation process MUST be recorded and submitted. | `[CAP]` S11; `[CON]` Delivery | Video delivered. |

---

## 4. Frontend Design and Wireframe Preservation

### 4.1 Screen-by-screen visual reference inventory

Design status: **Supplied** = a wireframe in the capstone material; **Proposed** = project design decision (`[PD]`), NOT provided by the capstone.

| Screen ID | Screen | Image file | PPT slide | Design status | Related requirements | Remaining visual notes |
|-----------|--------|------------|-----------|---------------|----------------------|-----------------------------|
| SCR-01 | Landing / Home (the catalogue itself, SCR-04) | `Catalogue_page.PNG` (same layout is embedded as the slide 5 picture) | S5 | **Supplied** via SCR-04: the landing page is the catalogue (CL-03 resolved); no separate screen | FR-LAND-01..04 | — |
| SCR-02 | Login | none | S3 step 1 | **Proposed** | FR-AUTH-03..05 | — |
| SCR-03 | Registration | none | S3 steps 1–2 | **Proposed** | FR-AUTH-01..02 | — (fields resolved by CL-18) |
| SCR-04 | Catalogue / landing page (hierarchical category sidebar, filters, sort, paging, Recommended for You) | `Catalogue_page.PNG` | S6 (also S5) | **Supplied** | FR-CAT-01..10, FR-LAND-01..04 | Brand-control placement and filter/option lists: §10.2 |
| SCR-05 | Book details with Related Reads (slide heading says "Shopping Cart") | `Shopping_cart.PNG` | S7 | **Supplied**; wireframe-only elements removed per CL-04 | FR-DET-01..09, FR-BSK-01 | — |
| SCR-06 | Basket (stand-alone view and recommendations) | `Payment.PNG` shows a "Shopping Cart" panel inside checkout only | S7, S8 | **Partly supplied** (item rows + steppers shown in SCR-07); stand-alone basket, remove control and recommendations panel are **Proposed** | FR-BSK-01..09 | Remove control and recommendation placement (Plan phase) |
| SCR-07 | Checkout (basket panel, address form and saved addresses, order summary, reward redemption below the summary) | `Payment.PNG` | S8 | **Supplied** (address fields, saved-address management and reward area per CL-17 and CL-11; coupon and discount removed) | FR-CHK-01..09, FR-RWD-01..05 | — |
| SCR-08 | Payment (Credit Card, Debit Card, UPI, Cash on Delivery) | `Payment_screen.PNG` | S9 | **Supplied** for Credit Card; UPI and COD forms **Proposed**; Wallet removed (CL-05) | FR-PAY-01..13 | — |
| SCR-09 | Purchase confirmation | `Purchase_confirmation.PNG` | S10 | **Supplied** | FR-CNF-01..06 | Order reference (proposed) |
| SCR-10 | Order history and Buy Again ("My Orders") | none (nav link visible in all supplied screens) | S3, S6 | **Proposed** | FR-ORD-01..03 | CL-24 (resolved) |
| SCR-11 | Order detail and cancel | none | S3 step 12 | **Proposed** | FR-ORD-02, 04..06 | OD-6, CL-12..14 |
| SCR-12 | Payment confirmation step (shown for successful card, debit and UPI payments, before the purchase confirmation) | none distinct | S3 step 11 | **Proposed** | FR-CNF-03 | — |
| SCR-13 | Empty, loading, error and not-found states | none | — | **Proposed** | NFR-FE-01 | — |
| SCR-14 | *(Excluded)* "My Wishlist" and "My Writers" pages | none (nav links visible in wireframes) | — | **Not built** (CL-04) | — | — |

### 4.2 Extracted visual details for supplied screens

**Decision overlay (Clarification 2026-11-12).** The descriptions below record what the wireframes show. Where an approved decision differs, the decision wins: the name is "BookyGeek" (CL-09), not "Book Worm"; Wishlist, My Writers, reviews, ratings, copies sold and the author profile are not built (CL-04); there is no Wallet (CL-05); there is no coupon control or discount line (CL-06); the sample amounts are illustrative (CL-08); the address fields follow CL-17; "Bestsellers this Month" and "New Launches" are not built (CL-15). No inactive link or button is displayed.

**Common to all supplied screens (header and chrome)** — `[WF]`
- Slim top bar with a menu/list icon and the brand text "Book Worm" (displayed as "BookyGeek", CL-09), a vertical divider, and links "My Orders", "My Wishlist", "My Writers". Right side: a cart icon with a small red numeric badge (value 2 in samples) and a circular profile icon. **Observation vs approved function:** only "My Orders" (order-history journey), the basket icon (basket journey) and the account menu (login journey; includes logout, CL-10.2) are built. "My Wishlist" and "My Writers" are not built and not displayed (CL-04). The menu/list icon toggles the category sidebar (CL-25).
- Page background is near-black; **no application footer is drawn in any supplied wireframe**. The word "Footer" appearing on the PPT slides is a slide placeholder, not an application footer, and neither the PPT text nor the constitution requires a footer. No footer is specified (see withdrawn DS-08).
- Light text on dark surfaces; links (author names, genre tags, breadcrumbs, "Published by") are blue and underlined; the primary action buttons are a saturated blue with white text and a trailing icon; rating stars are yellow; success tick is green.
- Typography resembles a clean grotesque sans-serif (it resembles IBM Plex Sans, but the exact family is a Plan-phase decision): large light-weight page/section headings, medium-weight titles, smaller regular descriptions, bold prices and "Delivery by" dates.

**SCR-04 Catalogue** — `Catalogue_page.PNG`, slide 6 (and the slide 5 picture)
- Layout: left vertical category rail with a highlighted "All" row at the top, then Romance, Mystery, Science Fiction, Fantasy, Historical, Biography, Self-help, Memoir, Travel, Cooking, Children's, Young Adult, Comics & Graphic Novels, Poetry, Drama, Science, Philosophy, Religion, Language Learning.
- Top of the content area: a filter row with a Search field ("Search you want to read here", magnifier icon) and dropdowns Language (All), Format "(Paperback, ebook etc)" (All), Price Range (All), Sort by (Relevance).
- Content: three titled sections in the wireframe, "Recommended for You", "Bestsellers this Month", "New Launches"; only "Recommended for You" is built (Clarification 2026-11-12, OD-7, CL-15). Each section is a three-column row of horizontal book cards (cover on the left; title, "by" author link, one-line description, format, genre-tag links, bold ₹ price, "Delivery by Mon, 21 Jul" on the right); the author link filters by author and a genre-tag link filters by genre (CL-25). Results are paged at 12 books (CL-16). Sample formats seen: Paperback, Hard Cover/Hardcover, eBook. Sample prices: ₹99–₹499 (illustrative).
- Spacing: generous gutters between cards; section headings left-aligned above each row.

**SCR-05 Book details and Related Reads** — `Shopping_cart.PNG`, slide 7
- Breadcrumb "Home / Non-Fiction / Self Help" (follows the category hierarchy, CL-25); left: two cover images side by side (front and back); centre: title, "by" author link, tagline, "Published by: ABC Publishers", format, genre links, large price, "Delivery by" date, a blue "Add to Cart" button with a cart icon; below: Language. *(Not built, CL-04: the grey "Add to Wishlist" button, the Rating stars and the "Sells (145 copies sold)" line.)*
- *(Not built, CL-04: the lower-left "About the writer" panel and the "Reviews" area with the review text area, rating row, Submit button and existing review.)*
- Right column separated by a vertical divider: "Related Reads" — three stacked compact cards (cover, title, author link, description, format, genre links, price, delivery date).

**SCR-07 Checkout** — `Payment.PNG`, slide 8
- Breadcrumb "Home / Non-Fiction / Self Help / Joy of Minimalism / Checkout /"; page heading "Shopping Cart".
- Panel 1 (dark gray surface, full width): book rows with cover, title, author link, description, format, genre links, price, delivery date, and a quantity stepper ("1 − | +") under each.
- Panel 2 (left, wide): "Address" with saved-address selection and management (CL-17) and the fields full name, phone (10 digits), address line 1, address line 2 (optional), city, state, PIN (6 digits), country (India default). *(Replaces the wireframe fields First Name, Last Name, single Address, e-mail and the phone country-code drop-down.)*
- Panel 3 (right): decorative books illustration and the order summary: item price, tax (5% of the subtotal), delivery charge (₹40, or free for an eBook-only order) and total amount (CL-07), then a blue "Pay Now" button. The reward-point redemption area (balance, maximum redeemable points, points input) sits below the summary (CL-11). *(Not built: the "Apply Coupon" field and Discount line, CL-06. The wireframe sample figures ₹508, ₹62 and ₹470 are illustrative, CL-08.)*

**SCR-08 Payment** — `Payment_screen.PNG`, slide 9
- Full-viewport illustrated dark-blue background (stylised books, geometric flecks) under the standard header; a compact dark centred dialog titled "Complete Payment" with "Payable Amount: ₹580" on the right of its title bar.
- Left vertical method list: Credit Card (selected, blue left accent), Debit card, UPI. *(The wireframe's Wallet entry is not built, CL-05. Cash on Delivery is added by the project.)* Right: fields Card Number (XXXX-XXXX-XXXX-XXXX), Name on Card (Name), CVV (XXX), Date of Expiry (MM/YYYY), and a blue "Pay Now" button at bottom-right. The sample "Payable Amount: ₹580" is illustrative (CL-08).

**SCR-09 Purchase confirmation** — `Purchase_confirmation.PNG`, slide 10
- Same illustrated background; a larger centred dark dialog with a green check-in-circle, the text "Your purchase of the following reads is successful", two book summaries side by side (cover, title, author, description, format, genres, price, delivery date), and a blue "Continue your Shopping" button centred below.

### 4.3 Screens without supplied designs — proposed designs `[PD]`

These are project design decisions and are not capstone-provided. They MUST reuse the dark visual language, header, typography, buttons, form fields, cards and spacing of §4.2:

- **Login and Registration (SCR-02/03)**: a centred dark panel on the near-black or illustrated background used by SCR-08/09; labelled dark inputs, blue primary button, blue text links for switching between login and registration, inline error text.
- **Landing / Home (SCR-01)**: is the catalogue (SCR-04) itself; there is no separate home page (CL-03).
- **Order History and Buy Again (SCR-10)**: list of dark surface order cards (reference, date, status, book rows, total) with "Buy Again" as a primary button and a secondary "Cancel order" action shown according to FR-ORD-04.
- **Order detail (SCR-11)** and **Basket (SCR-06)**: dark surface panels with the same item-row layout as SCR-07.
- **Empty/loading/error/not-found states (SCR-13)**: dark panel with short message and an action button.

### 4.4 Shared design-system requirements (high level; no tokens or technology chosen here)

| ID | Requirement | Acceptance criteria |
|----|-------------|---------------------|
| DS-01 | Every screen MUST use one palette derived from the supplied wireframes: near-black page background, dark-gray surfaces, light text, blue accent for primary actions and links, red for the basket badge/errors, green for success. | Side-by-side review: no screen uses a conflicting palette. |
| DS-02 | Typography MUST follow one hierarchy: page heading, section heading, item title, body/description, caption, price (emphasised), delivery date (emphasised). | The same role has the same style on all screens. |
| DS-03 | Primary buttons (blue, filled, optional trailing icon) and secondary buttons (grey) MUST look and behave the same everywhere. | Review of all buttons. |
| DS-04 | Form controls MUST share one style: dark filled field, label above, placeholder text, drop-downs, visible error state. | Review of all forms. |
| DS-05 | Book cards MUST share one structure (cover, title, author link, description, format, genre links, price, delivery date) with the compact (Related Reads) and standard (catalogue) variants. | Same fields appear in the same order. |
| DS-06 | Surfaces, borders, spacing and alignment MUST be consistent across screens. | Review against SCR-04/07. |
| DS-07 | The common header MUST be consistent across all screens and contain only functional elements: the brand "BookyGeek" (CL-09), the "My Orders" link, the basket icon with item count, the account menu (login for guests; for customers a menu including logout, CL-10.2) and the menu icon that toggles the category sidebar (CL-25). "My Wishlist" and "My Writers" are not built and not displayed (CL-04). | Header content and style are the same on every screen; no header element is inactive or points to an out-of-scope function. |
| DS-08 | **Withdrawn.** The earlier requirement that a footer exist on every screen had no source: no supplied wireframe draws a footer, and neither the PPT text nor the constitution requires one. No footer is specified. A footer MAY be considered later as an optional design enhancement; it is not a requirement and no layout element replaces it. The ID is retained only so DS-01..DS-12 numbering stays stable. | N/A (withdrawn). |
| DS-09 | Reusable UI components MUST be used rather than copy-pasted markup. | Code review (Plan/Implement phase). |
| DS-10 | No new CSS framework or UI library MAY be introduced without approval under constitution Principle III. | Dependency review. |
| DS-11 | The visual theme is dark only, following the supplied wireframes, with no light theme and no theme toggle (Clarification 2026-11-12, OD-9); the theme MUST be consistent on all screens. | Theme review: every screen uses the one dark theme; no toggle exists. |
| DS-12 | The brand text in the header MUST be "BookyGeek"; "Book Worm" (seen in the wireframes) is not used anywhere in the UI (Clarification 2026-11-12, CL-09). | Search of the UI finds no "Book Worm". |

### 4.5 Responsive behaviour

The desktop wireframe composition is the primary reference; three layout sizes are supported (Clarification 2026-11-12, CL-19). Breakpoints are approximate.

| ID | Requirement | Acceptance criteria |
|----|-------------|---------------------|
| RSP-01 | **Desktop** (1024px and above; `[CAP]` S13 steps 3 and 5: review "responsiveness" and "Verify responsiveness on desktop"; compositions `[WF]`): preserve the wireframe compositions (category rail + three-column cards; two-column details with Related Reads; three-panel checkout; centred dialogs). | Desktop review against wireframes. |
| RSP-02 | **Tablet** (640px–1023px): adapted layouts, including two-column layouts where appropriate; fewer card columns; category rail collapsible; Related Reads below the main details; checkout panels may stack. | No horizontal scrolling; all actions reachable. |
| RSP-03 | **Mobile** (below 640px): single-column layouts where appropriate; single-column cards; category rail and filter row behind toggles; header navigation condensed; checkout panels and payment dialog stack and fit the width. | No horizontal scrolling; forms fully usable. |
| RSP-04 | Content, actions and information MUST remain equivalent across sizes. | Same functions available at every size. |
| RSP-05 | The breakpoints 640px and 1024px are approximate and may be adjusted during design without changing the three layout sizes (CL-19). | Recorded in CL-19. |

### 4.6 Visual acceptance criteria per screen

| Screen | Criteria |
|--------|----------|
| SCR-04 | Compare with `Catalogue_page.PNG`: header (BookyGeek, My Orders, basket, account menu); category sidebar with hierarchy and highlighted selection; filter row (search, Language, Format, Price Range, Sort by with four options); "Recommended for You" section only; three-column horizontal cards with all card fields; 12 books per page; dark theme consistent. |
| SCR-05 | Compare with `Shopping_cart.PNG`: breadcrumb following the hierarchy; two covers; title/author/publisher/format/price/delivery; Add to Cart (blue); Related Reads in a right column. No wishlist, rating, copies-sold, author-profile or review elements (CL-04). |
| SCR-07 | Compare with `Payment.PNG`: breadcrumb; basket panel with steppers; address form with the CL-17 fields and saved-address selection; order summary arithmetic (subtotal, 5% tax, delivery, total) and Pay Now; reward-redemption area below the summary. No coupon field or discount line. |
| SCR-08 | Compare with `Payment_screen.PNG`: illustrated background; dialog title and payable amount; method list (Credit Card, Debit Card, UPI, Cash on Delivery) with selected accent; card fields; Pay Now. No Wallet. |
| SCR-09 | Compare with `Purchase_confirmation.PNG`: success tick; message; book summaries; Continue-your-Shopping button; illustrated background. |
| SCR-01, 02, 03, 06, 10, 11, 13 | Consistent with DS-01..DS-07 and DS-09..DS-12 (DS-08 is withdrawn); usable and responsive per RSP-01..04; interactions work; consistent with SCR-04/07 look and feel. |
| All screens | Navigation targets work; loading, error and empty states are present; layout matches the supplied desktop composition where one exists. |

---

## 5. Constitutional Constraints (quoted for traceability; unchanged)

- Architecture: one React frontend, one Spring Boot backend, one PostgreSQL database; monolithic, modular, layered backend with the seven modules in §2.12; no microservices, API gateway, inter-service REST, per-module databases or message brokers.
- Stack: React, Vite, JavaScript, React Router, Axios, plain CSS; Java 21, Spring Boot 4.x, Spring Data JPA, Hibernate, Maven; PostgreSQL; REST with OpenAPI. Any addition requires written proposal and approval.
- Contract-first API (OpenAPI approved before endpoints), reviewed data model before backend implementation, spec-driven phase order.

### 5.1 Owner-approved technology additions (Principle III)

The constitution is unchanged. The owner approved the following additions to the base stack in the Clarifications section; the written proposal (purpose and alternatives considered) required by Principle III for each is recorded at the plan Constitution Check, and none of them changes the architecture or the seven modules.

| Addition | Purpose | Decision |
|----------|---------|----------|
| Spring Security with JWT | Customer registration, login, protected endpoints, JWT validation, password hashing, logout | OD-1 |
| Flyway (SQL migrations) with Hibernate schema validation | Reproducible, version-controlled schema | OD-3 |
| JUnit 5, Mockito, Spring Boot Test; Vitest, React Testing Library; Playwright | Backend, frontend and end-to-end tests | OD-8 |
| Swagger Editor (authoring/validation of `openapi.yaml`) | Contract-first API | OD-10 |
| Docker Compose | Local run of frontend, backend and PostgreSQL | OD-11 |

Mend or an equivalent dependency scanner required by the security rules is not named by any decision (§10.2).

---

## 6. Traceability

### 6.1 Capstone journey coverage (slide 3, 12 journeys)

| # | Capstone journey (S3) | Requirements |
|---|------------------------|--------------|
| 1 | Login page | FR-AUTH-01..04 |
| 2 | User authentication | FR-AUTH-03..07 (JWT; logout FR-AUTH-05 in the account menu, CL-10.2) |
| 3 | Access product catalogue for each category | FR-CAT-01, FR-LAND-01..04 (the catalogue is the landing page) |
| 4 | User browses order history with Buy Again | FR-ORD-01..03 |
| 5 | Select the category of products | FR-CAT-01, FR-CAT-07 |
| 6 | Browse the brands | FR-CAT-02 (CL-02 resolved: brand = publisher) |
| 7 | Select the product (tentative delivery date); related products | FR-CAT-07, FR-DET-01..05 |
| 8 | Add product(s) to basket | FR-DET-03, FR-BSK-01..06, FR-ORD-08 |
| 9 | Select the address for delivery | FR-CHK-02..04 (multiple saved addresses) |
| 10 | Initiate payment with the right option; redeem gift points | FR-CHK-07..08, FR-PAY-01..13, FR-RWD-01..06 |
| 11 | Payment confirmation | FR-CNF-03, FR-PAY-10, FR-PAY-12 |
| 12 | Confirmation on purchase; cancel order within 48 hours | FR-CNF-01..06, FR-ORD-04..06 |
| — | Recommends items based on order history | FR-BSK-07, FR-CAT-09 |

### 6.2 Wireframe and slide traceability

| Slide | Content | Spec reference |
|-------|---------|----------------|
| S2, S11–S13 | Objectives, workflow, delivery steps | §3.1, NFR-LOC, NFR-TST |
| S3 | 12 customer journeys | §6.1 |
| S4 | Architecture/capability diagram (inspected) | §10.1 classification (CL-10.1–CL-10.31); CL-01a–d, CL-05, CL-06, CL-10 |
| S5 | Landing page with availability | SCR-01, FR-LAND, CL-03 |
| S6 | Catalogue | SCR-04 |
| S7 | "Shopping Cart" heading showing book details and Related Reads | SCR-05, SCR-06 |
| S8 | Payment & Purchase (address, payment initiation, gift points) | SCR-07 |
| S9 | Payment screen | SCR-08 |
| S10 | Purchase confirmation | SCR-09 |

### 6.3 Source distinction summary

- **Capstone-required**: all 12 journeys of §6.1, Credit Card and Debit Card payment (S9 "e.g., Credit card, Debit card"), delivery expectations in §3.1.
- **Constitution/project-added**: UPI and Cash on Delivery (`[PRJ]`, constitution Principle I), user registration (constitution Principle I), security/data/testing principles, monolithic architecture.
- **Owner's specify request (`[APD]`, not in the PPT text or the constitution)**: search, remove from basket, update quantities, saved addresses where the wireframe requires them, order summary and discounts, continuation action after purchase, unsuccessful dummy payment outcomes, unavailable products.
- **Wireframe-observed (visual observations, not approved functions unless a source also states them)**: search box, filters, sort, book sections, breadcrumbs, Continue-your-Shopping button. Wishlist, My Writers, reviews, ratings, copies sold, author profile, coupon, Wallet and the "Bestsellers"/"New Launches" sections are also seen in the wireframes but are NOT built (CL-04, CL-05, CL-06, CL-15).
- **Design assumptions (`[PD]`)**: all screens listed "Proposed" in §4.1, empty/error states, tablet/mobile layouts, order reference, return-to-page after login (A-12), order-detail view. No footer is specified.
- **Owner clarifications (`[CLR]`)**: customer-only role and seed-data catalogue (OD-2, CL-22); guest browsing (CL-01a); guest basket kept in the browser and merged at login (CL-01b); login required at checkout (CL-01d); logged-in basket persistence (CL-01c, FR-BSK-09); 48-hour cancellation window measured from order placement for all payment methods including COD (OD-6, CL-12; FR-ORD-04, FR-PAY-06); no reward-point earning, balances come from seed data only (OD-4; FR-RWD-06); 1 point = ₹1 and redemption capped at 50% of the order total (OD-4; FR-RWD-02, FR-RWD-03); brand = publisher (CL-02; FR-CAT-02); delivery date = current date + 5 days for physical books, current date for eBooks (OD-5; FR-DET-02); registration with full name, e-mail, password and confirmation, e-mail as the unique case-insensitive login identifier, password 8 to 64 characters with a letter and a digit (CL-18; FR-AUTH-01..04); every order is "Placed" at placement, Credit Card/Debit Card/UPI orders are "Paid", COD orders are "Pending (pay on delivery)" with nothing collected (CL-12; FR-ORD-04, FR-PAY-06); cancellation atomically restores stock and redeemed points, a "Paid" payment becomes "Refunded" (simulated record only) and a COD payment becomes "Cancelled" (CL-13, OD-4 restoration, OD-6; FR-ORD-05, FR-PAY-12, FR-RWD-06); the 48-hour window is exclusive, cancellable only while strictly under 48 hours old (CL-14, OD-6; FR-ORD-04); each new account starts with 1,000 reward points, no minimum redemption, no expiry (OD-4 resolved; FR-RWD-06); an order has only the statuses "Placed" and "Cancelled" with the single transition Placed → Cancelled, and Buy Again uses current catalogue price and stock, listing books not added (CL-24; FR-ORD-01, FR-ORD-03); related products are the other books in the same category, and recommendations are books in the same categories as previously ordered books (hidden or empty without history, none for guests); only "Recommended for You" is built and "Bestsellers this Month" and "New Launches" are not (OD-7, CL-15; FR-DET-04, FR-BSK-07, FR-CAT-09, FR-LAND-03); the batch of owner-approved decisions of Revision 4 — modular monolith confirmed (Q0); catalogue = landing page (CL-03); wireframe-only features, Wallet and coupons excluded (CL-04, CL-05, CL-06); pricing 5% tax and ₹40 delivery, sample totals illustrative (CL-07, CL-08); "BookyGeek" brand (CL-09); unconfirmed slide 4 capabilities out of scope and logout included (CL-10, CL-10.2); reward area below the summary (CL-11); filters, four sorts and 12 per page (CL-16); address fields and saved addresses (CL-17); three responsive sizes (CL-19); development targets (CL-20); simulated payment outcomes and separate confirmation steps (CL-21); stock model and basket limit of 10 (CL-23); author/genre filtering, category hierarchy and sidebar toggle (CL-25); Spring Security with JWT (OD-1); Flyway (OD-3); test tooling (OD-8); dark theme only (OD-9); contract-first OpenAPI (OD-10); Docker Compose (OD-11); review assumptions accepted (OD-12).
- **Withdrawn**: DS-08 (footer), assumptions A-06 (author-link behaviour, now decided by CL-25), A-08 (e-mail identifier, later confirmed by CL-18) and A-11 (replaced by FR-BSK-09).

---

## 7. Open Decisions Impact (OD-1..OD-12 — all resolved and recorded in the Clarifications section)

| OD | Decision | Requirements affected | Clarification required |
|----|----------|-----------------------|------------------------|
| OD-1 | Authentication mechanism and Spring Security | FR-AUTH-03..08, NFR-SEC-02 | **Resolved (Clarification 2026-11-12):** Spring Security with JWT — customer registration, login, protected endpoints, JWT validation, password hashing, logout; customer only. Token lifetime and storage are plan-level (§10.2). |
| OD-2 | Roles / authorisation model | FR-AUTH-06..08, FR-ORD-07, CL-22 | **Resolved (Clarification 2026-11-12): customer only.** The catalogue is loaded from seed data; there are no admin screens, admin APIs or admin role. |
| OD-3 | Migration approach | NFR-DAT-04, NFR-DAT-05 | **Resolved (Clarification 2026-11-12):** Flyway SQL migrations with Hibernate schema validation; no automatic schema update or create. |
| OD-4 | Reward-point rules | FR-RWD-01..06, FR-CHK-05, FR-CHK-07, FR-ORD-05 | **Resolved (Clarification 2026-11-12):** no earning; balances come only from seed data. 1 point = ₹1; redemption is capped at 50% of the order total; redeemed points are restored when the order is cancelled (CL-13); each new account starts with 1,000 points (₹1,000); there is no minimum redemption; points do not expire. |
| OD-5 | Tentative delivery-date calculation | FR-DET-02, FR-CAT-03, FR-BSK-02, FR-CNF-01 | **Resolved (Clarification 2026-11-12):** physical books = current date + 5 days; eBooks = current date; display style "Mon, 21 Jul". |
| OD-6 | 48-hour window anchor and COD behaviour | FR-ORD-04..05, FR-PAY-06, NFR-TST-01 | **Resolved (Clarification 2026-11-12):** the window runs from order placement for every payment method, COD included; the boundary is exclusive (cancellable only while strictly less than 48 hours old); COD orders are placed "Placed" with payment "Pending (pay on delivery)". |
| OD-7 | Recommendation algorithm and "related products" | FR-DET-04, FR-BSK-07, FR-CAT-09, FR-LAND-03 | **Resolved (Clarification 2026-11-12):** related products = other books in the same category as the displayed book; basket and "Recommended for You" recommendations = books in the same categories as previously ordered books, excluding basket items; no history or guest = empty state or section hidden; no separate up-sell/cross-sell feature. |
| OD-8 | Test tooling | NFR-TST-01..04 | **Resolved (Clarification 2026-11-12):** JUnit 5, Mockito, Spring Boot Test (backend); Vitest, React Testing Library (frontend); Playwright (end-to-end). |
| OD-9 | Dark/light theme | DS-01, DS-11, RSP, all screens | **Resolved (Clarification 2026-11-12):** dark theme only; no light theme and no toggle. |
| OD-10 | OpenAPI tooling | NFR-API-04 | **Resolved (Clarification 2026-11-12):** contract-first; `openapi.yaml` authored and validated in Swagger Editor, kept in the repository and approved before backend implementation. |
| OD-11 | Cloud deployment and containers | NFR-LOC-01..04 | **Resolved (Clarification 2026-11-12):** Docker Compose for local use; cloud deployment optional (free tier) and never mandatory. |
| OD-12 | Human wireframe/data-model review | SCR inventory (§4), Key Entities | **Resolved (Clarification 2026-11-12):** all review assumptions accepted; see §8. |

---

## 8. OD-12 — Human review items (resolved)

All items were resolved by the owner (Clarification 2026-11-12):

1. **Wireframe set**: the five `Capstone_frontend_images` files are accepted as the wireframe references; screens without a supplied design remain proposed designs (§4.3).
2. **Image comparison**: the five PNG files were compared with the pictures embedded in slides 6–10 and match (§0.2). Slide 5 reuses the catalogue picture and slide 4 is the capability map.
3. **Data-model needs**: only the entities and fields required by confirmed functionality are included (§2.11). Not created: author profile, review, rating, wishlist, coupon, wallet. The data model is reviewed before backend implementation (NFR-DAT-04).
4. **Sample data**: sample prices, tax, delivery, discount and totals (₹470, ₹580) are illustrative, not mandatory (CL-08).
5. **Wireframe-only elements**: features that appear only as visual examples are out of scope unless confirmed elsewhere (CL-04, CL-05, CL-06).

---

## 9. Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of the 12 capstone customer journeys in §6.1 are completable end to end in a single local environment, each with a passing end-to-end test.
- **SC-002**: A new visitor can register, log in, find a book, add it to the basket, check out with each of the four payment methods and see the purchase confirmation with no unexplained errors.
- **SC-003**: For each supplied screen (SCR-04, 05, 07, 08, 09), 100% of the elements listed in §4.2 are present and positioned as in the original wireframe, verified by a reviewer.
- **SC-004**: 100% of screens pass the shared design-system review (DS-01..DS-12, excluding the withdrawn DS-08) and the desktop/tablet/mobile checks (RSP-01..04).
- **SC-005**: In every tested failure scenario (payment failure, mid-operation error, double submission) there are zero inconsistent records, e.g. no confirmed order with a failed payment and no points deducted without an order.
- **SC-006**: Zero full card numbers or CVV values are found in stored data or logs after all payment tests.
- **SC-007**: Each business rule listed in NFR-TST-01 has at least one passing test, including both sides of the cancellation boundary (47:59:59 accepted, 48:00:00 rejected).
- **SC-008**: Every implemented endpoint traces to the approved OpenAPI contract and has a passing response-verification test.
- **SC-009**: A reviewer can set up, start and use the application locally by following the written instructions only.
- **SC-010**: Every open decision (OD-1..OD-12) and clarification item (CL-01a–d, CL-02..CL-25, and the Category B/C items of CL-10 in §10.1) has a decision recorded in the Clarifications section; the small plan-level details listed in §10.2 are settled in the Plan phase before the work that depends on them begins.

---

## 10. Source Conflicts, Discrepancies and Clarification Register

Every item is resolved and recorded in the Clarifications section. No default has been silently chosen; the small plan-level details that remain are listed in §10.2.

| ID | Item | Evidence | Affects | Related OD |
|----|------|----------|---------|-----------|
| CL-01 | Guest access (parent item, split into the four independent decisions CL-01a–d below). CL-01a, CL-01b, CL-01c and CL-01d are all resolved (guests can browse and add to a basket; login is required at checkout; a logged-in customer's basket persists across sessions). The slide 4 diagram shows a Guest User reaching the Home Page, but S3 begins with Login. | S4 vs S3 | FR-AUTH-06/08 | OD-1 |
| CL-01a | **Resolved (Clarification 2026-11-12): guests can browse.** Visitors who are not logged in may browse the catalogue and view book details. | S4 vs S3; `[CLR]` | FR-AUTH-08, FR-LAND, FR-CAT, FR-DET | OD-1 |
| CL-01b | **Resolved (Clarification 2026-11-12): guests can add to a basket.** The guest basket is kept in the browser and merged into the customer's basket at login. | S4 vs S3; `[CLR]` | FR-AUTH-08, FR-DET-03, FR-BSK-01 | OD-1 |
| CL-01c | **Resolved (Clarification 2026-11-12): a logged-in customer's basket persists across sessions** (saved against the account, retained after logout and re-login). A guest basket stays in the browser only. | `[CLR]` | FR-AUTH-08, FR-BSK-09 | OD-1 |
| CL-01d | **Resolved (Clarification 2026-11-12): login is required at checkout and payment.** | S3, S4; `[CLR]` | FR-AUTH-06, FR-AUTH-08, FR-CHK-01 | OD-1 |
| CL-02 | **Resolved (Clarification 2026-11-12): brand = publisher.** "Browse the brands" means browsing by the book's publisher (the detail page shows "Published by: ABC Publishers"). The brand list is the publishers in the seed data. The wireframes show no brand control, so its placement remains a design decision for the Plan phase. | S3 step 6; S7; S4; `[CLR]` | FR-CAT-02 | OD-12 |
| CL-03 | **Resolved (Clarification 2026-11-12):** the catalogue is the landing page; there is no separate marketing home page. | S5, S6; `[CLR]` | SCR-01, SCR-04, FR-LAND | OD-12 (resolved) |
| CL-04 | **Resolved (Clarification 2026-11-12):** only confirmed features are built. Not built: Wishlist, My Writers, review submission, ratings, copies sold, author profile pages and decorative features. Language stays as book information. No inactive link or button is displayed. Amended by AMD-002. | S7, nav on all screens; `[CLR]` | FR-DET-07..08, SCR-14, DS-07 | OD-12 (resolved) |
| CL-05 | **Resolved (Clarification 2026-11-12):** no Wallet. Only Credit Card, Debit Card, UPI and Cash on Delivery. | S9, S4 vs constitution; `[CLR]` | FR-PAY-01/02 | — |
| CL-06 | **Resolved (Clarification 2026-11-12):** no coupons — no control, entity or API. | S8, S4; `[CLR]` | FR-CHK-05/06, FR-BSK-05 | — |
| CL-07 | **Resolved (Clarification 2026-11-12):** tax is 5% of the book subtotal; delivery is ₹40 per order for physical-only and mixed orders and free for eBook-only orders; total = subtotal − coupon discount (0) + tax + delivery; reward redemption follows within the 50% limit; two-decimal rounding; no real GST. | S8; `[CLR]` | FR-BSK-05, FR-CHK-05, FR-RWD-03 | — |
| CL-08 | **Resolved (Clarification 2026-11-12):** ₹470 and ₹580 are illustrative sample amounts and are never hardcoded. | S8 vs S9; `[CLR]` | FR-PAY-09 | OD-12 (resolved) |
| CL-09 | **Resolved (Clarification 2026-11-12):** the UI shows "BookyGeek" everywhere; "Book Worm" is not used. | all screens; `[CLR]` | DS-07, DS-12 | — |
| CL-10 | Slide 4 architecture diagram: capabilities beyond the approved scope and module structure (Store creation, shipping/shipment, returns, refunds, Wallet, order modification, up-sell/cross-sell, roles and entitlements, payment gateway integration). Each item is classified in §10.1 (CL-10.1–CL-10.31): 13 Category A (logical capability inside the monolith), 15 Category B (potential scope addition requiring clarification), 3 Category C (conflict with the constitution). Category B and C items are NOT required by this specification. **Resolved (Clarification 2026-11-12):** unconfirmed capabilities stay out of scope (no admin portal or role, store management, inventory dashboard, real payment gateway, or unconfirmed analytics or management capability); logout is included (CL-10.2). | S4 vs constitution; `[CLR]` | FR-ORD-07, FR-PAY-02, FR-PAY-07, FR-CHK-06, FR-AUTH-05, scope | OD-2 (resolved) |
| CL-11 | **Resolved (Clarification 2026-11-12):** a redemption area below the order summary shows the balance, the maximum redeemable points and an input for the points to redeem; the reward rules are unchanged. | S8; `[CLR]` | FR-RWD-01, FR-CHK-07 | OD-4 (resolved) |
| CL-12 | **Resolved (Clarification 2026-11-12):** at placement every order has status "Placed"; a card, debit or UPI order has payment state "Paid"; a COD order has payment state "Pending (pay on delivery)" and nothing is collected. A COD order is cancellable within 48 hours of placement like any other order. | constitution; `[CLR]` | FR-PAY-06, FR-PAY-12, FR-ORD-04 | OD-6 (resolved) |
| CL-13 | **Resolved (Clarification 2026-11-12):** cancelling an order, atomically, sets status "Cancelled", returns quantities to stock and restores redeemed reward points; a "Paid" payment becomes "Refunded" (simulated record only, no money movement) and a COD payment becomes "Cancelled". | S4 "Refund"; `[CLR]` | FR-ORD-05, FR-PAY-12, FR-RWD-06 | OD-4, OD-6 (resolved) |
| CL-14 | **Resolved (Clarification 2026-11-12):** the 48-hour boundary is exclusive; an order is cancellable only while strictly less than 48 hours old, and a request at exactly 48:00:00 or later is rejected. | S3; `[CLR]` | FR-ORD-04 | OD-6 (resolved) |
| CL-15 | **Resolved (Clarification 2026-11-12):** only "Recommended for You" is built (books in the same categories as previously ordered books, excluding basket items; hidden when there is no history); "Bestsellers this Month" and "New Launches" are not built and have no defined rule. | S6; `[CLR]` | FR-LAND-03, FR-CAT-09 | OD-7 (resolved) |
| CL-16 | **Resolved (Clarification 2026-11-12):** filters Language, Format and Price Range; sorts Relevance, Price low to high, Price high to low, Title A to Z; 12 books per page; all work together. Option lists and price bands are plan-level (§10.2). | S6; `[CLR]` | FR-CAT-05/06/10 | — |
| CL-17 | **Resolved (Clarification 2026-11-12):** full name, phone (10 digits), line 1, optional line 2, city, state, PIN (6 digits) and country (default India); multiple saved addresses that can be selected at checkout and managed. | S8; `[CLR]` | FR-CHK-02..04, FR-AUTH-06 | OD-12 (resolved) |
| CL-18 | **Resolved (Clarification 2026-11-12):** registration collects full name, e-mail, password and password confirmation; the e-mail address is the unique login identifier (case-insensitive); the password must be 8 to 64 characters with at least one letter and one digit; no e-mail verification is required. | `[CLR]` | FR-AUTH-01/02/03/04 | OD-1 (resolved) |
| CL-19 | **Resolved (Clarification 2026-11-12):** mobile below 640px (single column), tablet 640–1023px (adapted, including two-column), desktop 1024px and up (wireframe composition); breakpoints approximate. | `[CLR]` | RSP-01..05, NFR-FE-04 | OD-9 (resolved) |
| CL-20 | **Resolved (Clarification 2026-11-12):** development targets, not guarantees — API response under 1 second and initial frontend load under 3 seconds in normal local conditions; latest Chrome, Edge and Firefox; keyboard access, labels, contrast and alt text; no uptime SLA. | `[CLR]` | NFR-PERF-01/02, NFR-BRW-01, NFR-ACC-01 | — |
| CL-21 | **Resolved (Clarification 2026-11-12):** simulated success and failure; card test values trigger failure and normal valid test data succeeds; UPI uses a simulated UPI ID; payment confirmation and purchase confirmation are separate steps; online success is "Paid", COD is "Pending"; credentials are never stored. The exact failure values are plan-level (§10.2). | S3; `[CLR]` | FR-PAY-04/05/08, FR-CNF-03 | — |
| CL-22 | **Resolved (Clarification 2026-11-12):** catalogue data enters the system only as reproducible seed data (books, categories, brands); there is no administration path. | S11 "dummy test data"; `[CLR]` | scope | OD-2 (resolved) |
| CL-23 | **Resolved (Clarification 2026-11-12):** physical books have finite integer stock and eBooks unlimited availability; adding to the basket reserves nothing; stock is revalidated at checkout and before placement, decremented after placement and restored on cancellation; at most 10 units of a book per basket and never above physical stock; concurrent updates are handled safely. Delivery-date semantics: OD-5. | S6; `[CLR]` | FR-CAT-08, FR-BSK-01/03/08, FR-ORD-03/05/08, FR-DET-02 | OD-5 (resolved) |
| CL-24 | **Resolved (Clarification 2026-11-12):** an order has only the statuses "Placed" and "Cancelled"; the only transition is Placed → Cancelled within 48 hours (FR-ORD-04) and "Cancelled" is final. Buy Again adds books still in the catalogue and in stock at their current price and delivery date, lists the ones not added, and reduces an over-stock quantity to the stock with a message. | S3, S6; `[CLR]` | FR-ORD-01/03 | OD-6 (resolved), CL-23 (resolved) |
| CL-25 | **Resolved (Clarification 2026-11-12):** an author name filters the catalogue by author; a genre tag filters by genre; categories are hierarchical (e.g. Non-Fiction → Self Help); the header menu icon toggles the category sidebar; all navigation leads to a functional result. | Wireframes for S5–S8; `[CLR]` | FR-CAT-01/03/07, FR-DET-06, FR-CHK-09, DS-07 | OD-12 (resolved) |

### 10.1 CL-10 — Slide 4 architecture diagram: capability classification

Slide 4 (heading "Architecture") contains one picture: a business-capability map. Its arrows show a **customer flow** (Registered User → User Login → Home Page → Browse → Add to Cart → Payment Processing → Shipment; Guest User → Home Page). They are not service calls, and the diagram shows no API gateway, service-to-service communication or separate databases. Its six capability boxes (Member, Store, Catalog, Order, Payment, Shipping) are read in this specification **only** as business capabilities of the single monolithic application. They are not deployable services. This specification does not add, rename or split backend modules: the seven modules of constitution Principle II are unchanged.

Categories:

- **A — Logical capability inside the monolith**: maps to an existing approved module and to a capstone or constitution journey.
- **B — Potential scope addition requiring clarification**: could be built inside the monolith but is not stated by the PPT text or the constitution. **Not required by this specification.**
- **C — Conflict with the approved constitution**: cannot be adopted without a constitutional amendment. **Not adopted.**

Summary: 13 items in Category A, 15 in Category B, 3 in Category C (31 rows). CL-10.2 is counted once, under Category A, for its login part (a capstone and constitution requirement); its logout part was Category B and is now included by owner decision (Clarification 2026-11-12, FR-AUTH-05). All remaining Category B items are out of scope and all Category C items are not adopted (CL-10 resolved).

| ID | Slide 4 item | Cat | Maps to (approved module / source) | Treatment in this specification |
|----|--------------|-----|------------------------------------|---------------------------------|
| CL-10.1 | Registered User → User Login → Home Page (flow) | A | User & Authentication; `[CAP]` S3 steps 1–2 | Covered by FR-AUTH-03. |
| CL-10.2 | "Login & Logout" (Member) | A | User & Authentication; login is `[CAP]` S3 steps 1–2 and `[CON]` Principle I; logout confirmed by owner (Clarification 2026-11-12) | Login: FR-AUTH-03. Logout: FR-AUTH-05 (MUST), in the customer account menu; it clears or invalidates the authentication state. Resolved. |
| CL-10.3 | Guest User → Home Page (flow); "Guest/Registered Users" (guest part) | B (guest browsing resolved by Clarification 2026-11-12, CL-01a) | User & Authentication; Book Catalogue | Guest browsing is confirmed by CL-01a (FR-AUTH-08). Other guest capabilities are not specified. Guest add to basket (CL-01b), login at checkout (CL-01d) and basket persistence (CL-01c) are resolved. |
| CL-10.4 | "Role & Entitlement" (Member) | B | User & Authentication; OD-2 | Not required. Only the own-data rule (FR-AUTH-07) is specified. OD-2 is resolved as customer only: no roles or entitlements beyond the customer. |
| CL-10.5 | "Browse Catalog by Entitlement" (Catalog) | B | Book Catalogue; depends on CL-10.4 | Not required. With a single customer role (OD-2 resolved) there are no entitlement-based catalogue views. |
| CL-10.6 | "Create Stores" (Store) | B | None — there is no Store module. The constitution Scope lists admin back-office UI and multi-vendor marketplace as out of scope unless specified later | Not required and currently excluded by the constitution's Scope section. Adopting it would also add a module (CL-10.31). |
| CL-10.7 | "Create Catalogs" (Store) | B | Book Catalogue; CL-22 | Not required. CL-22 is resolved: the catalogue comes from seed data only. |
| CL-10.8 | "Create Store Policies" (Store) | B | None | No source defines store policies. Not required. |
| CL-10.9 | Home Page → Browse → Add to Cart (flow) | A | Book Catalogue; Shopping Cart; `[CAP]` S3 | Covered by FR-LAND, FR-CAT, FR-DET and FR-BSK. |
| CL-10.10 | "Browse Products by Category & Brand" (Catalog) | A | Book Catalogue; Category & Brand; `[CAP]` S3 steps 5–6 | FR-CAT-01, FR-CAT-02. Brand = publisher (CL-02, resolved). |
| CL-10.11 | "Recommended Products based on Order History" (Catalog) | A | Shopping Cart / Order / Book Catalogue; `[CAP]` S3, S7 | FR-BSK-07. Rule resolved (OD-7, Clarification 2026-11-12): same categories as previously ordered books. |
| CL-10.12 | "Show Up Sale & Cross Sale Products" (Catalog) | B | Book Catalogue; may overlap "related products" (OD-7) | Not required beyond related products (FR-DET-04) and order-history recommendations (FR-BSK-07). Up-sell/cross-sell is not a distinct feature (OD-7, Clarification 2026-11-12). |
| CL-10.13 | "Order Checkout", "Create ... Order", "Confirm Order" (Order) | A | Order Management; `[CAP]` S3 steps 9–12 | FR-CHK and FR-CNF. |
| CL-10.14 | "Modify Order" (Order) | B | Order Management | Not required (FR-ORD-07). No source defines what could be modified. |
| CL-10.15 | "Cancel Order" (Order) | A | Order Management; `[CAP]` S3 step 12 | FR-ORD-04..06. Window rule: OD-6 (resolved). |
| CL-10.16 | "Return Order" (Order) | B | Order Management | Not required (FR-ORD-07). Cancellation and the simulated refund record are covered by FR-ORD-05. |
| CL-10.17 | "Order History" (Order) | A | Order Management; `[CAP]` S3, S6 | FR-ORD-01..03. |
| CL-10.18 | "Redeem Gift points" (Order) | A | Reward Points Management; `[CAP]` S3, S8 | FR-RWD and FR-CHK-07. Rules: OD-4. |
| CL-10.19 | "Coupons" (Order) | B | Order Management / Payment Management; CL-06 | Not built (FR-CHK-06). CL-06 resolved: no coupons. |
| CL-10.20 | "Payment processing" performed as a dummy, in-process simulation | A | Payment Management; constitution Scope | FR-PAY-07..08. This is the only reading of payment processing that is adopted. |
| CL-10.21 | "Payment processing using Gateway" read as integration with a real external payment gateway | C | Constitution Scope: payments are dummy only, with no real payment gateway and no real money movement; introducing one requires an amendment | Conflict. Not adopted. The dummy-payment requirement is preserved (FR-PAY-07). |
| CL-10.22 | "Payment Confirmation" (Payment) | A | Payment Management; `[CAP]` S3 step 11 | FR-CNF-03. CL-21 resolved: payment confirmation is a separate step before the purchase confirmation. |
| CL-10.23 | "Refund processing" (Payment) | B (C if it involves real money movement) | Payment Management / Order Management; CL-13 | Not required as a separate feature. A simulated refund record is part of order cancellation (FR-ORD-05, CL-13 resolved); no real money moves, which would conflict with the constitution. |
| CL-10.24 | "Gift" in "Gift & Wallet" (Payment) | A | Reward Points Management; `[CAP]` S3 "Redeem gift points" | Read as gift/reward points: FR-RWD. Rules: OD-4. |
| CL-10.25 | "Wallet" in "Gift & Wallet" (Payment) | B | None approved; the constitution's payment methods exclude Wallet | Not offered (FR-PAY-02). CL-05 resolved: no Wallet. |
| CL-10.26 | "Shipping Rate Calculation" (Shipping) | B | None — there is no Shipping module; delivery charges appear in the checkout wireframe | No shipping-rate feature. The simulated delivery-charge rule (₹40 per order with any physical book, free for eBook-only) is part of the order total (CL-07, FR-BSK-05). |
| CL-10.27 | "Approximate Delivery Time" (Shipping) | A | Book Catalogue / Order Management; `[CAP]` S3 step 7 (tentative delivery date) | The capability is the tentative delivery date (FR-DET-02). Calculation: current date + 5 days for physical books, current date for eBooks (OD-5, resolved). It does not imply a Shipping module. |
| CL-10.28 | "Return Shipment" (Shipping) | B | None | Not required (FR-ORD-07). |
| CL-10.29 | "Shipment" (flow step after Payment Processing) | B | None | Shipment creation, tracking or management is not stated by the PPT text, the constitution or any wireframe. Not required. Whether order statuses include shipment states: CL-24. |
| CL-10.30 | Reading the diagram's capability boxes (Member, Store, Catalog, Order, Payment, Shipping) as independently deployable services, or adding an API gateway or inter-service communication | C | Constitution Principle II prohibits microservices, API gateway and inter-service REST calls | Conflict. Not adopted. The diagram is read only as a business capability map inside the single modular monolith (Q0 confirmed). |
| CL-10.31 | Adding "Store", "Shipping" or any other new backend module | C | Principle II names the seven approved modules | Conflict unless the owner approves a constitutional amendment. No new module is introduced by this specification. |

### 10.2 Remaining plan-level details (not open clarification questions)

These are design details the Plan phase settles within the approved decisions; none changes scope, architecture or a business rule already decided. Items marked "approval" are gates the owner has already required.

| # | Detail | Needed by |
|---|--------|-----------|
| PL-01 | Rule when a book is in both the guest basket and the customer basket at login (for example, add the quantities, capped at 10 and at stock) | FR-BSK-01, FR-AUTH-08 |
| PL-02 | Rounding of a fractional 50% reward cap (for example, round down to a whole point) and the rounding mode for two-decimal amounts | FR-RWD-03, FR-BSK-05 |
| PL-03 | JWT lifetime, storage on the client and how logout invalidates the token | FR-AUTH-03, FR-AUTH-05 |
| PL-04 | Where the brand (publisher) control appears in the catalogue (no wireframe shows it) | FR-CAT-02 |
| PL-05 | Seed lists: languages, formats, states, categories (hierarchy depth and parent/child pairs), price bands | FR-CAT-01, FR-CAT-05, FR-CHK-02 |
| PL-06 | The predefined card test values and the UPI ID values that trigger a simulated failure, and the UPI ID format | FR-PAY-04, FR-PAY-05, FR-PAY-08 |
| PL-07 | Meaning of "Relevance" ordering and the stock-concurrency mechanism (for example, optimistic or pessimistic locking) | FR-CAT-06, FR-ORD-08 |
| PL-08 | Dependency vulnerability scanning tool (the security rules name Mend; no decision selects the tool) | NFR-SEC-07 |
| PL-09 | Written Principle III proposals (purpose, alternatives) for the additions in §5.1, recorded at the plan Constitution Check | §5.1 |
| PL-10 | Approval gates already required by the owner: the data model reviewed before backend implementation (NFR-DAT-04) and the `openapi.yaml` contract approved before backend implementation (NFR-API-04) | Implementation |


---

## 11. Assumptions

- **A-01**: BookyGeek is a customer-facing storefront; there is no administration UI, admin API or admin role (constitution Scope; confirmed by Clarification 2026-11-12, OD-2).
- **A-02**: All payments are simulated; no real gateway or money movement (constitution).
- **A-03**: The seeded categories include at least those shown in the wireframe, arranged in a hierarchy (CL-25); the category list is data, not hard-coded.
- **A-04**: Currency is Indian Rupee (₹) and delivery dates use the "Mon, 21 Jul" style, observed from wireframes.
- **A-05**: Search matches at least title and author (design assumption; no source defines the matching fields). It works together with filters, sorting and paging (CL-16).
- **A-06**: *(Withdrawn; now a confirmed decision.)* An earlier draft assumed that selecting an author name lists that author's books. CL-25 now confirms that an author name filters by author and a genre tag filters by genre (FR-CAT-03).
- **A-07**: Credit and Debit Card options use the same field set; the wireframe shows the form only under Credit Card. Predefined test values trigger simulated failures (CL-21).
- **A-08**: *(Withdrawn; now a confirmed decision.)* An earlier draft assumed e-mail as the unique login identifier without a source. It was withdrawn, and the same choice is now confirmed by Clarification 2026-11-12 (CL-18) and specified in FR-AUTH-01..04.
- **A-09**: Authenticated customers are the primary actors for customer-specific functions; guests may browse the catalogue and book details (Clarification 2026-11-12, CL-01a). Other guest behaviour is not assumed; cross-session basket persistence is deferred to CL-01c.
- **A-10**: Dummy test data is provided for demonstration (capstone S11).
- **A-11**: *(Withdrawn as an assumption; now a confirmed decision.)* The basket persistence of a logged-in customer is decided by Clarification 2026-11-12 (CL-01c) and specified in FR-BSK-09.
- **A-12**: After logging in, the customer is returned to the protected page they originally requested (design assumption; not required by any source; see FR-AUTH-06).

---

## 12. Out of Scope for this Specification

Implementation plan, tasks, Java/React code, database tables and relationships, the OpenAPI document, technology selection beyond the owner-approved decisions, and any modification of the approved constitution.

---

## 13. Revision Log

### Revision 1 — Review and correction (2026-11-12)

Corrections made in response to the owner's review. No open decision (OD-1..OD-12) was resolved; the architecture and the approved stack were not touched; the constitution was not modified.

| Change | Detail |
|--------|--------|
| DS-08 withdrawn | The footer mandate had no source (no footer in any supplied wireframe; none in the PPT text or the constitution). The ID is kept as "Withdrawn" so DS-01..DS-12 numbering and references remain stable. A footer is recorded only as an optional future design enhancement. |
| DS-07 corrected | The header requirement is limited to elements required by the capstone or shown in the wireframes. "My Wishlist" and "My Writers" are recorded as wireframe visual observations, not mandated navigation (CL-04). |
| CL-01 split | CL-01 is now a parent item with four independent sub-decisions: CL-01a (guest browse), CL-01b (guest basket), CL-01c (basket persistence), CL-01d (login before checkout). References in FR-AUTH-06, FR-AUTH-08, FR-BSK-09, FR-DET-03, FR-CHK-01, A-09 and the edge cases were updated. |
| FR-AUTH-06 narrowed | It previously required login for checkout and payment, which silently resolved CL-01d. Checkout and payment login is now left to CL-01d. Return-to-requested-page after login is now a SHOULD (design assumption A-12). User Story 1 scenario 4 was aligned. |
| CL-10 expanded | New §10.1 classifies the 31 capability items of the slide 4 diagram into Category A (13), B (15) and C (3). No item is adopted as a microservice, gateway or new module. |
| CL-25 added | New clarification for wireframe-implied link/navigation semantics (author and genre link targets, category hierarchy, header menu icon). Assumption A-06 (author link lists the author's books) was withdrawn and replaced by CL-25. |
| Source attribution audit | A new tag `[APD]` (explicit item in the owner's Specify request, not in the PPT or constitution) was introduced. "user brief" and unqualified `[CAP]`/`[CAP-S4]` labels were replaced. Reclassified or re-attributed requirements: FR-AUTH-01, 04, 05, 06; FR-LAND-01, 02, 03, 04; FR-CAT-01, 04, 05, 07, 08, 09, 10; FR-DET-01, 02, 04, 06, 09; FR-BSK-01, 03, 04, 05, 08; FR-CHK-01, 03, 05, 08, 09; FR-PAY-08, 09, 10; FR-CNF-01, 02. The list reflects the rows edited in this pass; any row not listed retains its original source label. See the updated rows. |
| Cross-reference fixes | NFR-FE-04 now points to §4.5 (was §6.4); NFR-TST-04 now points to §6.1 (was §7); SC-004 excludes withdrawn DS-08; SC-010 covers CL-25 and the CL-01 split; §6.2 and §6.3 updated. |
| Not changed | All 12 capstone journeys, all other requirement IDs and acceptance criteria, OD-1..OD-12, CL-02..CL-24 (CL-01 split only), the seven backend modules and the approved stack. |

### Revision 2 — Final consistency correction (2026-11-12)

Targeted correction pass. No open decision (OD-1..OD-12) was resolved; the architecture, the seven modules, the approved stack and the dummy-payment-only rule were not touched; the constitution was not modified.

| Change | Detail |
|--------|--------|
| A-08 withdrawn | The e-mail-as-login-identifier assumption was still active in §11 although it was meant to be withdrawn (Revision 1 did not record it). It is now marked withdrawn. CL-18 now asks explicitly which field is the login identifier, plus registration fields and password rules. FR-AUTH-02 no longer names "e-mail format". CL-18 now also governs FR-AUTH-03 and FR-AUTH-04. The e-mail field in the checkout address form (FR-CHK-02) is an address field only. |
| FR-CHK-05 corrected | Mandatory: item prices and the overall payable total. Tax and delivery charges follow CL-07. A discount line appears only for an approved, applied discount. Reward-point deduction and presentation follow OD-4. Acceptance criteria and the FR-BSK-05 and FR-PAY-09 wording were aligned (the ₹470 wireframe sample is illustrative only). |
| FR-CHK-06 corrected | Coupon entry and coupon discounts are not required until CL-06 is resolved, and no discount line is mandatory. The earlier sentence "The discount line MUST still be displayed" was removed. The reward-point requirements (FR-RWD-01..06, FR-CHK-07) are unchanged. |
| FR-AUTH-05 reclassified | The constitution requires login, authentication and registration (Principle I, OD-1) but does not mention logout; neither does the PPT text. Logout rested only on the slide 4 "Login & Logout" label. It is now a SHOULD and a proposed project decision `[PD]`, and becomes a MUST only if the owner confirms it via CL-10.2. Login and authentication requirements (FR-AUTH-03) are unchanged. The "log out" wording in User Story 1's independent test and in one edge case was neutralised. |
| CL-10.2 reclassified | Split by part: login is Category A (`[CAP]` S3 steps 1–2, `[CON]` Principle I); logout is Category B. It is still counted once under Category A in the 13/15/3 summary, which is unchanged, and the summary now says so. |
| CL-06 and OD-4 separated | CL-06 covers coupons only and no longer lists OD-4. OD-4 remains the owner of reward-point rules (FR-RWD-01..06, FR-CHK-05, FR-CHK-07, FR-ORD-05, CL-11, CL-13). CL-10.19 notes that coupons and reward points are decided independently. |
| Not changed | FR-CHK-07 and FR-RWD-01..06, the 12 capstone journeys, all requirement IDs, OD-1..OD-12, and CL-01a–d and CL-02..CL-25 other than CL-06 and CL-18. |

### Revision 3 — Clarification session 2026-11-12 (clarify phase)

Decisions made by the owner were recorded in the Clarifications section and applied to the affected requirements, registers and assumptions. No requirement ID was added or removed; the architecture, the seven modules, the approved stack and the dummy-payment-only rule were not touched; the constitution was not modified.

| Change | Detail |
|--------|--------|
| Resolved | OD-2, OD-4, OD-5, OD-6, OD-7; CL-01a–d, CL-02, CL-12, CL-13, CL-14, CL-15, CL-18, CL-22, CL-24; delivery-date part of CL-23 |
| Requirements updated | FR-AUTH-01..04, FR-BSK-07, FR-CAT-09, FR-DET-02, FR-DET-04, FR-LAND-03, FR-ORD-01, FR-ORD-03..05, FR-PAY-06, FR-PAY-12, FR-RWD-02, FR-RWD-03, FR-RWD-06 |
| Withdrawn | "Bestsellers this Month" and "New Launches" sections are not built (FR-LAND-03); A-08 and A-11 are replaced by confirmed decisions |
| Still open at that time | CL-03..CL-11, CL-16, CL-17, CL-19..CL-21, CL-23 (stock model and maximum quantity), CL-25, the Category B/C items of CL-10.x, OD-9 and OD-12; plan-level OD-1, OD-3, OD-8, OD-10, OD-11 (all resolved in Revision 4) |

### Revision 4 — Owner-approved decision batch (2026-11-12, clarify phase)

The owner supplied a batch of approved decisions. They are recorded as bullets in the Clarifications section and applied to the requirements, registers and assumptions. The constitution (v1.0.0) was not modified; the monolithic architecture, the seven backend modules and the approved stack were preserved; no implementation plan, tasks, database schema, OpenAPI document or code were produced.

| Change | Detail |
|--------|--------|
| Resolved | Q0 (architecture confirmed), CL-03, CL-04, CL-05, CL-06, CL-07, CL-08, CL-09, CL-10 and CL-10.2, CL-11, CL-16, CL-17, CL-19, CL-20, CL-21, CL-23, CL-25, OD-1, OD-3, OD-8, OD-9, OD-10, OD-11, OD-12 |
| Requirements updated | FR-AUTH-03, 05, 06; FR-LAND-01, 02; FR-CAT-01, 03, 05, 06, 07, 08, 10; FR-DET-01, 06, 07, 08; FR-BSK-01, 03, 05, 08; FR-ORD-03, 05, 07; FR-CHK-02..07, 09; FR-PAY-01, 02, 04, 05, 08, 09; FR-RWD-01, 03; FR-CNF-03 |
| Requirements and sections added | FR-ORD-08; NFR-DAT-05, NFR-LOC-04, NFR-PERF-01, NFR-PERF-02, NFR-BRW-01, NFR-ACC-01; §5.1; §10.2 |
| Other sections updated | Header status, §0.2, user stories 1, 2, 4, 5, 7 and edge cases, §2.11, §2.12, NFR-SEC-02, NFR-DAT-04, NFR-API-04, NFR-FE-04, NFR-TST-01..04, NFR-LOC-01/03, §4.1–§4.6, DS-07/11/12, RSP-01..05, §6.1, §6.3, §7, §8, §9, §10, §10.1, §11 |
| Conflicts resolved | Wireframe "Book Worm", Wishlist, My Writers, reviews, ratings, Wallet, coupon, discount and the old address fields are overridden by the decisions; the earlier FR-CHK-02 address field list was replaced; the earlier rule that logout was only a proposal was replaced; the earlier "stock model open" notes were closed; wireframe sample totals are illustrative |
| Preserved | All earlier resolved decisions (Revisions 1–3), including guest basket merge, 48-hour exclusive cancellation, Placed/Cancelled statuses, 1,000 seeded points, 1 point = ₹1, 50% cap, no earning, brand = publisher, delivery dates, registration rules |
| Still to be settled in the Plan phase | The plan-level details PL-01..PL-10 in §10.2 |

### Revision 5 — Stack amendment AMD-001 (2026-11-12, plan phase)

Applies constitution amendment AMD-001, approved by the project owner on 2026-11-12. See `specs/001-bookygeek-platform/amendments/AMD-001-spring-boot-4.md`.

| Change | Detail |
|--------|--------|
| Stack | Spring Boot 3.x → 4.x (latest stable release of an actively supported 4.x line). Reason: no Spring Boot 3.x line is in active open-source support, which contradicts constitution Principle III's maintenance rule and the security policy. Updated in §5 and in the constitution (v1.0.0 → v2.0.0, MAJOR). |
| Test tooling (OD-8) | Unchanged in substance: JUnit, Mockito and Spring Boot Test remain the backend tools. The JUnit Jupiter version managed by Spring Boot 4 is accepted. |
| Not changed | No requirement ID (FR-/NFR-), business rule, module, architecture or clarification decision changed. No FR/NFR referenced Spring Boot 3. |
