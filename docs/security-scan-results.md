# Security scan results

Task T136 (PL-08, NFR-SEC-07). Scan date: 2026-11-16. Policy: fail at CVSS 7.0 or higher; a lower finding that is
accepted must be documented here.

**Overall status: partial.** The two npm audits ran and are clean. The Java dependency scan could not run because no NVD
API key is available. No Java result is claimed. The Mend scan needs the organisation's own access and was not run.

## 1. Frontend: `npm audit --audit-level=high` (in `frontend/`)

| Item | Result |
|---|---|
| Command | `npm audit --audit-level=high` (also `npm audit --json` for the totals) |
| Packages audited | 154 in total, 35 of them production dependencies |
| Vulnerabilities | critical 0, high 0, moderate 0, low 0 (total 0) |
| Exit code | 0 |
| Outcome | **Pass** |

Newer versions exist for one development dependency only: `@testing-library/user-event` 14.6.10, latest 14.6.12 (a patch
release, test-only, not shipped). Not changed in this task; see section 5.

## 2. End-to-end tests: `npm audit --audit-level=high` (in `e2e/`)

| Item | Result |
|---|---|
| Command | `npm audit --audit-level=high` |
| Packages audited | 4 |
| Vulnerabilities | total 0 |
| Exit code | 0 |
| Outcome | **Pass** |

Newer version: `@playwright/test` 1.65.0, latest 1.65.1 (a patch release, test-only, not shipped). Not changed in this
task; see section 5.

## 3. Backend: OWASP Dependency-Check (in `backend/`)

| Item | Result |
|---|---|
| Command | `mvn -B org.owasp:dependency-check-maven:13.0.0:check` (the plugin is configured in `backend/pom.xml` with `failBuildOnCVSS` 7.0 and reads the key from `NVD_API_KEY`) |
| Outcome | **Not run to completion. No result.** |
| Reason | `NVD_API_KEY` is not set on this machine. The plugin stopped with `NvdApiException: Invalid API Key, length of 0 too short` and then `NoDataException: No documents exist`, because there is no local copy of the NVD data to fall back on. |
| Effect on the repository | None. The report folder and data directory were written outside the repository. |

What was checked instead (not a vulnerability scan): the resolved runtime versions, listed with
`mvn dependency:list -DincludeScope=runtime`. Whether each one is the latest release was not checked here.

| Component | Version |
|---|---|
| Spring Boot | 4.1.2 |
| Spring Framework (`spring-core`) | 7.0.10 |
| Spring Security (`spring-security-core`, `spring-security-web`) | 7.1.2 |
| Hibernate ORM | 7.4.13.Final |
| Embedded Tomcat | 11.0.27 |
| Jackson (`tools.jackson.core:jackson-databind`) | 3.1.7 |
| Nimbus JOSE + JWT | 10.9.1 |
| Flyway | 12.4.0 |
| PostgreSQL JDBC driver | 42.7.14 |
| Logback | 1.5.38 |
| SnakeYAML | 2.6 |

A version list is not evidence that these releases have no known vulnerability. It only shows that no old release was
chosen on purpose.

**To finish this scan:** get a free NVD API key, then run

```powershell
$env:NVD_API_KEY = '<your key>'      # in the shell only, never in a file or the repository
cd backend
mvn -B org.owasp:dependency-check-maven:13.0.0:check
```

The first run downloads the NVD data and can take a long time; later runs reuse it. Any finding at CVSS 7.0 or higher
fails the build and must be fixed or mitigated (critical within 24 hours, high within 30 days). Record the result and any
accepted lower finding in this file.

## 4. Mend

The organisation's own scanning requirement (PL-08) uses Mend. Mend access for this repository is not available to the
authoring environment, so nothing was run and no result is recorded. The owner needs to confirm Mend access and run the
scan.

## 5. Open points

| Point | Needed from |
|---|---|
| Run the backend scan with an NVD API key and record the result in section 3 | Owner (key) |
| Confirm Mend access and run its scan | Owner |
| Decide whether to take the two test-only patch updates (`@testing-library/user-event` 14.6.12, `@playwright/test` 1.65.1). Neither is shipped, and neither has a known finding. The security rules ask for the latest stable version, so an update is recommended; it changes a lock file and the full test run should be repeated afterwards | Owner |

## 6. Container and configuration review (T137)

Static review of `docker-compose.yml`, `backend/Dockerfile`, `frontend/Dockerfile`, `frontend/nginx.conf`, `.env.example`,
`.gitignore` and the Spring profiles, done on 2026-11-16. Docker and Podman are not installed here, so nothing was built,
pulled or started from these files; every row below is a read of the files, not a run.

| Check | Result | Evidence |
|---|---|---|
| Images only from `registry.redhat.io` | **Pass** | `postgres` and `postgres-test`: `rhel9/postgresql-18:1`. Backend: build `ubi9/openjdk-21:1.24`, runtime `ubi9/openjdk-21-runtime:1.24` (JRE only). Frontend: build `ubi9/nodejs-22-minimal:1`, runtime `ubi9/nginx-126:1`. No `docker.io`, `alpine` or `latest` tag. |
| Non-root user | **Pass** | Both Dockerfiles end with `USER 1001` and a numeric user. The Postgres image runs as its own non-root user. |
| Read-only root file system | **Pass** | `read_only: true` on every service through the shared `x-hardened` block. Writable paths are `tmpfs` (`/tmp`, `/var/run/postgresql`, the test database's data directory) or the named data volume. nginx keeps its pid and temp paths under `/tmp`. |
| Capabilities dropped | **Pass** | `cap_drop: [ALL]` and `no-new-privileges:true` on every service. No capability is added back. |
| Host ports bound to `127.0.0.1` | **Pass** | `127.0.0.1:5432`, `5433`, `8080` and `3000` only. Outside Compose the backend binds `127.0.0.1` by default (`server.address: ${SERVER_ADDRESS:127.0.0.1}`). |
| Container-internal bind to `0.0.0.0` | **Recorded exception** | The backend container sets `SERVER_ADDRESS: 0.0.0.0` so the frontend container can reach it. This is the exception recorded in `plan.md` Complexity Tracking; the host port stays on `127.0.0.1`. |
| No secrets in images or the repository | **Pass** | A pattern search over the whole working tree (private keys, AWS, GitHub, Slack and OpenAI-style keys, JDBC URLs with a password, JWTs) found nothing. `.env.example` holds `CHANGE_ME` placeholders only. `.env`, `node_modules`, `target` and `dist` are ignored. The only password-like literals are the fixed unit-test secrets and sample passwords in test sources. The Dockerfiles copy no `.env`, and `.dockerignore` files were added for `backend/` and `frontend/` so a local `.env`, `target/` or `node_modules/` can never enter a build context. |
| TLS and `Secure` cookies documented | **Pass** | `README.md` section "Production: TLS and cookies". The `prod` profile sets the cookie `Secure` flag to `true`, and `COOKIE_SECURE` defaults to `true`. |
| Security headers | **Pass** | `nginx.conf` sets `nosniff`, `X-Frame-Options: DENY`, a `Referrer-Policy` and a Content-Security-Policy. HSTS is left to the TLS terminator and is documented in the README. |
| Timeouts | **Pass** | nginx sets client, send, keep-alive and proxy timeouts. |
| Dependabot | **Present** | `.github/dependabot.yml` covers Maven, both npm projects and both Dockerfiles. There is no CI workflow, which T144 allows only with separate written approval. |

**Not verifiable here (open points, to be done on a machine with Docker or Podman, T141):**

| Point | Why it matters |
|---|---|
| `docker compose config` and a full `docker compose up --build` | Nothing in these files has been parsed by Compose or built. |
| The image tags exist and can be pulled after `registry.redhat.io` login | The tags were checked against the Red Hat catalog by the authoring notes in the Dockerfiles, but not pulled. They must be re-verified and re-pinned at build time, as the Dockerfile comments say. |
| Image signature verification and an image vulnerability scan | Required by the security rules before deployment. Not possible without a container engine. |
| The `/dev/tcp` health checks work inside the runtime images | They need `bash` in the image; this was not tried. |
| Whether `ubi9/nginx-126` and `ubi9/openjdk-21-runtime` are the smallest suitable Red Hat images | They are the Red Hat runtime images for these stacks; no smaller one was compared. |

**Observation, not a defect:** `backend` loads the whole `.env` through `env_file`, so it also receives `POSTGRES_PASSWORD`
and `POSTGRES_USER`, which only the database needs. It is not exposed anywhere, but passing only the variables the backend
uses would follow least privilege more closely. Changing it alters the approved Compose file, so it is left for the owner.
## 7. Final secret scan of the working tree (replaces the repository-history scan)

Date: 2026-11-17. One search of every file in the working tree (483 files; excluded: `node_modules`, `target`, `dist`, lock files, `.git`, `.bob`, test output, images and the capstone deck) for the patterns `api key`, `secret`, `token`, `password` followed by `:` or `=` and a value of 12 or more characters, `BEGIN ... PRIVATE KEY`, AWS (`AKIA...`) and `sk-...` keys, and `jdbc:postgresql://user:pass@` connection strings.

| Item | Result |
|---|---|
| Hits | 7, all benign: test-only constants (`unit-test-only-secret-of-at-least-32-bytes!`, a random per-run `JWT_SECRET` in `IntegrationTestBase`), three form-validation lines in `LoginPage.jsx` and `RegisterPage.jsx`, and one line in `handoffSummary.md` |
| Real secret found | One: the local throw-away database password was written in `handoffSummary.md` (two places). It was replaced by a placeholder and a second search for the exact password gave 0 matches. The password only protects a throw-away local database on 127.0.0.1 and is stored outside the repository; change it if that database is ever reused |
| Result after the fix | Empty (no secret in the working tree) |

The dependency scans in section 3 (OWASP Dependency-Check) and the Mend scan were not run: owner-accepted exception (no NVD API key, no Mend access). The npm audit results in sections 1 and 2 (0 vulnerabilities) stand.
