# Amendment AMD-001: Spring Boot 3.x → actively supported Spring Boot 4.x

**Status: APPROVED AND APPLIED — Option A, MAJOR (2.0.0), 2026-11-12.**
Applied to `.specify/memory/constitution.md` (Principle III, version 2.0.0, Sync Impact note) and `spec.md` (§5, header,
Revision 5). Backend scaffolding may now start; the exact 4.x version is pinned at scaffolding (Phase 1) after checking the
official Spring Boot support page and migration guide.

**Raised**: 2026-11-12 | **Proposed by**: planning review | **Affects**: constitution Principle III, spec §5, plan, research

## 1. Problem

- Constitution Principle III fixes the backend stack as "Java 21, **Spring Boot 3.x**, …". Spec §5 repeats "Spring Boot 3".
- The owner reports that **no Spring Boot 3.x line currently has active open-source support**.
- Principle III also requires versions to be "latest stable, actively maintained … free of known critical/high
  vulnerabilities", and the workspace security rules forbid recommending end-of-life or extended-support-only
  software. The constitution therefore contradicts itself and the security rules for any 3.x version.
- Governance says a conflict "MUST be raised and resolved by amendment, not ignored". This document raises it.
- Limitation: the support status was not independently re-verified while writing this proposal (no live source was
  available). The owner's statement is taken as the premise. The owner should confirm the current status on the
  official Spring Boot support page at approval time, and the exact 4.x version is chosen at scaffolding.

## 2. Proposed amendment (exact text — to be applied only after approval)

### 2.1 Constitution (`.specify/memory/constitution.md`)

| Location | Current text | Proposed text |
|---|---|---|
| Principle III, Backend bullet | `Backend: Java 21, Spring Boot 3.x, Spring Data JPA, Hibernate, Maven.` | `Backend: Java 21, Spring Boot 4.x (the latest stable release of an actively supported 4.x line), Spring Data JPA, Hibernate, Maven.` |
| Footer version line | `**Version**: 1.0.0 \| **Ratified**: 2026-11-12 \| **Last Amended**: 2026-11-12` | `**Version**: 2.0.0 \| **Ratified**: 2026-11-12 \| **Last Amended**: <approval date>` |
| Sync-impact note (if the constitution keeps one) | — | `AMD-001: Spring Boot 3.x → 4.x because no 3.x line is in active OSS support; approved by <owner> on <date>.` |

Versioning rationale: Governance defines MAJOR as "removal or incompatible redefinition of a principle or the
**fixed stack**/architecture". Changing the fixed framework generation is read as MAJOR (1.0.0 → 2.0.0). The owner may
instead rule it MINOR (1.1.0); this does not change the substance.

### 2.2 Specification (`specs/001-bookygeek-platform/spec.md`)

| Location | Current text | Proposed text |
|---|---|---|
| §5 "Stack" bullet (line 548) | `… Java 21, Spring Boot 3, Spring Data JPA, Hibernate, Maven; …` | `… Java 21, Spring Boot 4.x, Spring Data JPA, Hibernate, Maven; …` |
| §13 Revision Log | — | New "Revision 5 — Stack amendment AMD-001": what changed (Spring Boot 3.x → 4.x), why (no 3.x line in active support), approver, date. No requirement ID, business rule, module or architecture changes. |
| Header status line | — | Add: "Revision 5 applies constitution amendment AMD-001." |

No functional requirement (FR-/NFR-) references Spring Boot 3; none changes.

## 3. Compatibility implications

Items below reflect the generally documented changes of the Spring Boot 3 → 4 generation. They are **to be re-checked
against the official Spring Boot 4 migration guide and release notes at scaffolding time**; they are inputs for the
owner's decision, not verified facts about a specific 4.x patch release.

| Area | Implication for BookyGeek | Effort / risk |
|---|---|---|
| Java 21 | Java 21 stays supported (Boot 4 baseline is Java 17 or later). The constitution's Java 21 is unaffected. UBI `openjdk-21` images stay valid. | None |
| Underlying generation | Spring Framework 7, Jakarta EE 11 level APIs, newer Hibernate ORM and Spring Data, Spring Security 7. Seven-module layered design, JPA entities and REST controllers are unaffected in structure. | Low–medium: re-check Hibernate/JPQL behaviour (locking queries, `validate`) |
| Starters / auto-configuration | Boot 4 is modularised: some starters and auto-configuration classes are renamed or split. Flyway needs its dedicated Boot starter (the bare `flyway-core` artefact alone no longer triggers auto-configuration); web, validation, security and OAuth2 resource-server starters may be renamed. Plan lists these by purpose, not by artifact name, so the Additions Register stays valid. | Low: dependency names fixed in Phase 1 |
| Spring Security (OD-1) | Deprecated configuration styles are removed; the lambda DSL is required. JWT-in-HttpOnly-cookie, CSRF cookie repository, `token_version` validator and PBKDF2 `DelegatingPasswordEncoder` (PL-03) remain implementable; class/package names must be confirmed. | Medium: security config is written once at Phase 4, test it early |
| JSON | Jackson 3 becomes the default (new `tools.jackson` packages). DTOs are Java records with no custom (de)serialisers planned, so impact is small. | Low |
| Testing (OD-8) | Test slices/annotations moved to technology-specific test starters; `@MockBean`/`@SpyBean` are replaced by `@MockitoBean`/`@MockitoSpyBean`. Boot 4's dependency management may select a newer JUnit Jupiter generation than the "JUnit 5" named in OD-8 (same programming model). **Owner to confirm that whichever JUnit Jupiter version Boot 4 manages is acceptable under OD-8.** | Low; wording decision only |
| Dependency scanning (PL-08) | OWASP Dependency-Check and `npm audit` are framework-independent. | None |
| Docker / Compose / Flyway SQL / OpenAPI / React | Independent of the Boot generation. | None |
| Ecosystem maturity | Fewer tutorials and third-party examples; early patch releases of a new major line can carry migration bugs. Mitigation: use the newest patch of the supported 4.x line and keep Phase 1's "empty app builds" exit criterion. | Medium (schedule), low (design) |
| Existing artefacts | No code, migration or API exists yet, so there is **no migration cost** — only documentation updates (see §5). | None |

## 4. Options considered

| Option | Result |
|---|---|
| **A. Amend to Spring Boot 4.x (recommended)** | Satisfies "actively maintained" and the security policy; no code exists, so switching now is cheapest. |
| B. Amend to a version-agnostic wording: "Spring Boot, the latest stable release of an actively supported line (currently 4.x)" | Avoids a repeat amendment when 4.x ages out. Rejected as default because it weakens the "fixed stack" principle and the owner asked for 4.x; acceptable if the owner prefers it. |
| C. Stay on 3.x and record an owner-approved exception | Violates Principle III's own maintenance rule and the security policy (EOL/extended-support-only software). Not recommended. |

## 5. Artefacts that change if approved

- `constitution.md`: Principle III line, version, last-amended date (§2.1).
- `spec.md`: §5 stack line, §13 Revision 5, header status (§2.2).
- `plan.md`: Technical Context, Gate Register (G-AMD-001 → Approved), R-01 closed, Phase 1 dependency, Constitution Check row III.
- `research.md`: R-21 rewritten from "risk" to "decision".
- `data-model.md`, `contracts/`, `quickstart.md`: no change expected.
- Phase 1 scaffolding task: pin the newest patch of the supported 4.x line and verify starters against the migration guide.

If rejected or no decision: Phase 1 backend scaffolding stays blocked; planning artefacts remain valid as written.

## 6. Approval record

- [x] Owner confirmed current Spring Boot 3.x support status: stated by the owner as the premise of this amendment (§1); to be re-verified on the official Spring Boot support page when the 4.x version is pinned.
- [x] Option chosen: **A** (Spring Boot 4.x)
- [x] Versioning: **MAJOR 2.0.0** (the fixed stack changed; matches the constitution's own Governance definition)
- [x] JUnit Jupiter version managed by Boot 4 accepted under OD-8: **yes** (same JUnit Jupiter programming model; the version Boot 4 manages is used)
- [x] Approved by: Project owner Date: 2026-11-12
