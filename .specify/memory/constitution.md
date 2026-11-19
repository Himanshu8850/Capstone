# BookyGeek Constitution

## Core Principles

### I. Capstone Fidelity & Complete Customer Journeys (NON-NEGOTIABLE)
BookyGeek is an online bookstore that lets customers browse, select and purchase books, as defined by
the Cloud Fullstack Developer Capstone deck. Every customer journey in the deck MUST be delivered; none
may be dropped, merged away or deferred without a recorded amendment to this constitution. The journeys are:

1. Login page and user authentication (plus user registration).
2. Landing page showing book availability.
3. Product catalogue per category: select category, browse by category, browse brands.
4. Book details with a tentative delivery date, and related products for selection.
5. Basket: add, update and remove products; recommendations based on order history.
6. Order history with Buy Again; cancel an order within 48 hours.
7. Checkout: select delivery address, initiate payment with a suitable option, redeem gift/reward points.
8. Payment completion, payment confirmation, and purchase confirmation.

Payment options MUST include dummy Credit Card and Debit Card (deck requirement) and UPI and Cash on
Delivery (project-added requirements). Each journey MUST map to at least one specification item and one
passing end-to-end test before the project is declared complete.

*Rationale*: The capstone is evaluated against these journeys; omission is the primary delivery risk.

### II. Monolithic, Modular, Layered Architecture
The system MUST consist of exactly three runtime parts: one React frontend, one Spring Boot backend and one
PostgreSQL database. The backend MUST be a single deployable application organised into logical business
modules: User & Authentication, Book Catalogue, Category & Brand, Shopping Cart, Order, Payment, and
Reward Points. Within each module the code MUST follow the layers Controller -> Service -> Repository ->
Entity, with DTOs at the API boundary and centralised exception handling.

- Controllers MUST NOT access repositories directly; entities MUST NOT be returned from controllers.
- Modules communicate through in-process service calls only. A module MUST NOT read another module's
  tables directly via its repository; it MUST go through that module's service.
- PROHIBITED: microservices, API gateway, inter-service REST calls, per-module databases, message
  brokers or any other distributed-system machinery, unless added by constitutional amendment.
- Abstractions (interfaces, generic base classes, factories) MUST be justified by at least two concrete
  uses or a stated requirement; speculative abstraction is not allowed.

*Rationale*: A monolith keeps a capstone-sized system understandable, testable and demonstrable.

### III. Approved Technology Stack
The following stack is fixed. Changing or adding to it requires the process in Governance.

- Frontend: React, Vite, JavaScript (not TypeScript), React Router, Axios, plain CSS.
- Backend: Java 21, Spring Boot 4.x (the latest stable release of an actively supported 4.x line), Spring Data JPA, Hibernate, Maven.
- Database: PostgreSQL. API: REST with an OpenAPI specification.

Any additional library, framework, tool or service (examples: Spring Security, Bean Validation, Lombok,
Flyway/Liquibase, springdoc, Testcontainers, Vitest, Tailwind) is an "addition". Each addition MUST be
proposed in writing with its purpose and alternatives and MUST be explicitly approved before use. Approved
versions MUST be the latest stable, actively maintained release, free of known critical/high
vulnerabilities, and recorded in `pom.xml` / `package.json` with lock files committed.

*Rationale*: Prevents stack drift and keeps the deck's technology expectations (Spring Boot, React,
PostgreSQL, OpenAPI) intact. Note: the deck permits Angular or React and CSS/Tailwind; React and plain CSS
are the project's recorded choices.

### IV. Contract-First API Development
An OpenAPI specification MUST be written, reviewed and approved before any backend endpoint is implemented.

- Endpoints MUST be implemented to match the approved specification; any deviation requires the spec to
  be updated and re-approved in the same pull request.
- APIs MUST follow REST conventions: resource-oriented URIs, correct HTTP methods, and correct status
  codes (e.g., 201 on create, 204 on delete, 400/401/403/404/409 for client errors).
- All request bodies, path/query parameters MUST be validated server-side; invalid input MUST yield a
  4xx response using one consistent error response schema defined in the specification.
- Every endpoint MUST be documented in the specification, and each MUST have at least one test that
  verifies its response (status and body shape) against that specification.
- Pull requests that add or change APIs MUST include the relevant API documentation.

*Rationale*: Mirrors the deck's workflow (wireframes -> OpenAPI -> Spring Boot -> verify responses).

### V. Data Integrity & Reviewed Database Design
The PostgreSQL data model MUST be derived from the capstone wireframes and reviewed and approved before
backend implementation begins.

- Every table MUST have a primary key; relationships MUST be enforced with foreign keys; appropriate
  NOT NULL, UNIQUE and CHECK constraints MUST be applied.
- Data MUST NOT be duplicated unless a documented reason exists (e.g., price snapshot on an order line
  to preserve historical totals).
- Operations that change multiple records (placing an order, payment, reward-point redemption, order
  cancellation, stock changes) MUST execute in a single transaction that rolls back on failure.
- Schema changes MUST be reproducible from version-controlled artefacts, not manual database edits.
- Dummy/seed data MUST be reproducible and MUST NOT contain real personal or payment data.

*Rationale*: Cart, order, payment and reward points must stay consistent under failure.

### VI. Frontend Quality & Consistency
The frontend MUST use the capstone wireframes as the UI reference.

- Pages are composed from reusable components in a documented, consistent folder structure; naming
  follows one convention (e.g., PascalCase components, camelCase functions/variables).
- Navigation MUST use React Router; all backend communication MUST go through a shared Axios instance
  (no ad-hoc `fetch`/Axios calls scattered across components).
- Every data-fetching view MUST show a loading state and handle API errors with a user-visible message;
  every form MUST validate input client-side (in addition to, never instead of, server validation).
- Layouts MUST be responsive and verified at desktop width (deck minimum); styling MUST be consistent
  and use shared CSS (variables/tokens) with no duplicated style blocks.
- Whenever light/dark themes are present they MUST be visually consistent across all pages (deck review
  criterion). Whether a user-facing theme toggle is delivered is an open decision (OD-9).
- Review before completion: component structure, folder organisation, responsiveness, reusability,
  naming, theme consistency, and removal of duplicate or unused code.

*Rationale*: These are the deck's explicit frontend review criteria (slide 13).

### VII. Test-Verified Delivery
Testing MUST validate requirements, not merely exercise code.

- Test cases MUST be prepared from the specification and acceptance scenarios before a feature is
  declared done, together with maintained dummy test data.
- Backend: unit tests for service-layer business rules, and integration tests for API endpoints and
  database persistence (verifying data is actually saved in PostgreSQL).
- Frontend: tests of key components and flows, and manual/automated verification of buttons, forms,
  navigation and the cart, checkout and payment flows.
- Complete customer journeys (Principle I) MUST be validated end to end.
- Business rules MUST each have an explicit test, at minimum: 48-hour cancellation boundary (allowed
  just under 48 hours, rejected after), reward-point redemption limits, cart totals, Buy Again, and
  payment-method handling.
- Known defects MUST be fixed (or recorded as accepted exceptions by the owner) before completion.
  The full backend test suite MUST pass before a pull request is merged.

*Rationale*: The deck requires test cases, dummy data, API verification and cart/payment flow checks.

### VIII. Secure by Default
- Credentials, secrets, keys and connection strings MUST NOT be hardcoded or committed; configuration
  MUST come from environment variables (or a secrets manager), with per-environment profiles. A
  `.gitignore` MUST exclude secret-bearing files; a non-secret `.env.example` SHOULD document variables.
- Passwords MUST be stored only as salted, adaptive hashes (e.g., bcrypt/Argon2); never plaintext, MD5 or SHA-1.
- Authentication and authorization MUST be enforced on the server for every protected endpoint
  (least privilege; users may access only their own cart, orders, addresses and reward points).
- Card payments are dummy: the system MUST NOT persist full card numbers (PAN) or CVV, and MUST NOT log
  secrets, tokens, passwords or payment details.
- All user input MUST be validated server-side; database access MUST use parameterised queries / JPA
  (no string-concatenated SQL); outputs MUST be encoded for their context.
- Errors MUST return generic client messages with no stack traces or internals; details are logged
  server-side only. Logs SHOULD be structured and use proper levels.
- Services MUST bind to localhost or specific internal addresses during development, never `0.0.0.0`;
  any non-local traffic MUST use TLS 1.2+ with certificate validation enabled. Cross-origin access MUST be an allowlist.
- Dependencies MUST be kept current and scanned for known vulnerabilities; critical/high findings MUST be
  patched or mitigated with a documented decision.

*Rationale*: Aligns with workspace security policy and the project's own security requirements.

### IX. Spec-Driven Workflow & Traceable Delivery (NON-NEGOTIABLE)
Development follows GitHub Spec Kit in this order, and a later phase MUST NOT begin until the earlier one
is approved: constitution -> specify -> clarify -> plan -> tasks -> implement.

- Requirements MUST be specified before planning; open clarifications MUST be resolved before final plan.
- Architecture, data model and OpenAPI contract MUST be approved before implementation starts.
- Tasks MUST be generated before implementation, and implementation MUST follow the approved tasks.
- Approved requirements, plans and contracts MUST NOT be modified silently; changes require an explicit,
  recorded revision (what changed, why, who approved).
- Documentation (README, API docs, specs, deployment notes) MUST be updated in the same pull request as
  the change it describes.
- Unresolved decisions MUST be recorded as open decisions or clarifications, never silently assumed.

*Rationale*: Keeps specifications, code and documentation synchronised and auditable.

## Scope, Stack & Domain Constraints

- **In scope**: the customer journeys in Principle I and the seven backend modules in Principle II.
- **Reward points**: customers can redeem gift/reward points during checkout; earning, valuation, limits
  and expiry rules are undefined and MUST be specified (OD-4) before implementation.
- **Order cancellation**: permitted only within the 48-hour window defined by OD-6; attempts after that
  window MUST be rejected with a clear message and no state change. The window anchor and COD behaviour
  MUST be resolved during Specify/Clarify before implementation.
- **Tentative delivery date**: MUST be displayed on the book detail view, as required by the capstone; its
  calculation rule and any required display details MUST be resolved during Specify (OD-5).
- **Recommendations / related products**: derived from order history and catalogue relationships
  (category/brand); algorithm kept as simple as the specification allows (OD-7).
- **Payments**: dummy only (Credit Card, Debit Card, UPI, Cash on Delivery); no real payment gateway,
  no real money movement. Introducing a real gateway requires an amendment.
- **Out of scope unless specified later**: admin back-office UI, multi-vendor marketplace, microservices,
  native mobile apps, real payment processing.

## Delivery Workflow & Quality Gates

- **Source control**: the project lives in the owner's personal GitHub repository, with access granted to
  the evaluator. Work happens on meaningful feature branches (e.g., `feature/api-implementation`), never
  directly on `main`; commits use clear, meaningful messages; branches are pushed and merged via Pull Request.
- **Pull requests** MUST describe the change, link the spec/task it implements, include API documentation
  when APIs change, and show that tests pass.
- **Quality gates before merge**: (1) build succeeds (`mvn clean install`, frontend build); (2) tests pass;
  (3) no hardcoded secrets; (4) API matches the approved OpenAPI spec; (5) docs updated;
  (6) Constitution Check in the plan is satisfied or a justified exception is recorded.
- **Clean code**: readable names, small cohesive methods/components, no dead or duplicated code,
  consistent formatting.
- **Local readiness**: the application MUST run locally with frontend-backend integration and verified
  PostgreSQL connectivity; backend start via `mvn spring-boot:run`; written setup/run/deployment instructions are mandatory.
- **Cloud readiness**: the design MUST remain cloud-deployable (12-factor style configuration via
  environment variables). Actual cloud deployment is optional and depends on cloud access (OD-11).
- **Submission**: a demonstration video explaining the development and implementation process MUST be
  prepared for the evaluator, together with the PR(s) and documentation.

## Governance

This constitution supersedes other project practices. Where it conflicts with an instruction, the
conflict MUST be raised and resolved by amendment, not ignored.

- **Amendments**: proposed in writing (what, why, impact on existing artefacts), approved by the project
  owner, then recorded here with an updated version and amended date, and any affected spec/plan/tasks
  MUST be reconciled in the same change set.
- **Versioning** (semantic): MAJOR = removal or incompatible redefinition of a principle or the fixed
  stack/architecture; MINOR = new principle/section or materially expanded guidance; PATCH = wording
  and clarification only.
- **Compliance review**: every plan MUST pass a Constitution Check; every pull request review MUST verify
  compliance; violations MUST be fixed or carry a recorded, owner-approved justification (complexity
  tracking). Principles marked NON-NEGOTIABLE cannot be waived per-feature, only amended.
- **Runtime guidance**: Spec Kit skills in `.bob/skills/` and templates in `.specify/templates/`.

### Open Decisions Register

These are deliberately NOT decided here. Each MUST be resolved (via `/speckit-clarify`, plan or
amendment) before the phase noted.

- **OD-1 Authentication mechanism** (stateless JWT vs. server session) and use of Spring Security (an
  "addition" under Principle III). The authentication approach MUST support the capstone login and
  registration journeys. Resolve the authentication mechanism and any additional dependency decision
  before the plan. Before: plan.
- **OD-2 Roles/authorization model**: customer only, or customer plus admin (catalogue management). Before: specify.
- **OD-3 Schema migration tool** (Flyway/Liquibase vs. JPA-generated schema + SQL scripts). Before: plan.
- **OD-4 Reward-point rules**: earning rate, point value, redemption cap, expiry. Before: specify.
- **OD-5 Tentative delivery date rule** (fixed lead time vs. per-book/stock based). Before: specify.
- **OD-6 48-hour window anchor**: order placement time vs. payment confirmation; behaviour for COD. Before: specify.
- **OD-7 Recommendation algorithm** and the definition of "related products". Before: specify/plan.
- **OD-8 Test tooling** (JUnit/Mockito/Testcontainers on backend; Vitest/React Testing Library on frontend). Before: plan.
- **OD-9 Dark/light theme**: whether a user-facing toggle is required or only consistency of any theme. Before: specify.
- **OD-10 OpenAPI tooling** (hand-written YAML vs. generated, springdoc, contract validation). Before: plan.
- **OD-11 Cloud deployment** target (if any) and container use. Before: plan.
- **OD-12 Wireframe conformance**: wireframe images in the deck MUST be reviewed by a human to derive
  entities, fields and layouts; this constitution assumes nothing beyond the deck's text. Before: specify.
- OD-13 (AMD-002): reviews, ratings and the writer section are in scope.

**Version**: 2.0.0 | **Ratified**: 2026-11-12 | **Last Amended**: 2026-11-12

**Sync Impact (AMD-001)**: Spring Boot 3.x → 4.x because no 3.x line is in active OSS support; approved by the project owner on 2026-11-12. MAJOR bump (fixed stack changed). JUnit Jupiter version managed by Spring Boot 4 is accepted under OD-8 (same JUnit Jupiter programming model).
