# Feature: WMS Replenishment Module

## Objective

Implement the replenishment module described in `SPECS.md`, designed in `docs/SRS.md` v1.0 and
`docs/ARCHITECTURE.md` v1.0: 11 REST endpoints (10 from the spec + `GET /stock/moves`), the domain model
(`Location`, `InventoryItem`, `ReplenishmentRule`, `ReplenishmentTask`, `StockMove`), and the seed dataset —
inside the existing hexagonal `domain` / `api` / `infra` modules, following the `User` feature's conventions
except where `docs/ARCHITECTURE.md` deliberately documents a deviation (AD-05: scoped
`@RestControllerAdvice`).

**Why**: this is the core deliverable of the hiring challenge. Correctness of the replenishment business
rules and full endpoint coverage are explicit acceptance criteria.

## Scope

In scope: everything in `docs/SRS.md` §4 (FR-LOC, FR-STK, FR-RUL, FR-TSK, FR-MOV) and §7 (seed data),
built per `docs/ARCHITECTURE.md` AD-01–AD-09. Out of scope: everything in `docs/SRS.md` §2.6 (auth,
multi-warehouse, order picking, procurement, relational persistence).

## Constraints

- Authorized scope: implementation (write access), per explicit user confirmation.
- TDD: **enabled**. Source: `CLAUDE.md` ("This project follows Organic Driven Development (ODD) with TDD
  as the default working process, per the user's global Claude Code configuration"). Runner: `./gradlew
  test` (JUnit 5; Gradle runs the `test` task in every subproject that defines one, so this alone covers
  `domain`, `infra`, and the root app). Red → green → refactor per task, no invented evidence.
- Each task closes with one work-unit commit on `feature/replenishment-module` (already branched off
  `main`), Conventional Commits, no AI attribution (per global config).
- RDD (receipt-driven development): **off** (`gentle-ai review mode status` confirmed `off (decided by
  default)`). No native review ceremony; verification follows the RDD-off tier from `gentle-ai review
  assess` per task.
- Delivery strategy: `ask-on-risk` (default) — single feature branch, ask only if the ~400-line/task budget
  or native risk assessment calls for slicing into multiple PRs. Push/PR remain the user's decision.
- Artifacts (code, identifiers, comments, tests, commit messages) default to English, per global config.

## Tasks

- [x] **T1 — Location + error-handling scaffolding** — commit `04e1f44`
  FR-LOC-01/02. Establishes the AD-05 pattern every later controller reuses: `NotFoundException`,
  `ConflictException`, `ReplenishmentExceptionHandler` (`@RestControllerAdvice` scoped to the new
  controllers only). Files: `domain/model/Location.java`, `LocationType.java`, `domain/exception/*`,
  `domain/port/{inbound,outbound}/Location*.java`, `domain/service/LocationDomainService.java`,
  `infra/adapter/outbound/persistence/InMemoryLocationRepository.java`,
  `infra/adapter/inbound/web/LocationController.java` + mapper, `infra/config/ReplenishmentExceptionHandler.java`,
  `api/dto/location/*`, `DomainConfiguration` wiring, domain unit tests, one MockMvc integration test.

- [x] **T2 — InventoryItem: load and query stock** — commit `8923385`
  FR-STK-01/02. Depends on T1. Files: `domain/model/InventoryItem.java`,
  `domain/port/{inbound,outbound}/... Stock/Inventory...`, `domain/service/StockDomainService.java`
  (partial: `loadStock`, `queryStock`), `InMemoryInventoryRepository.java`, `StockController.java`
  (partial), `api/dto/stock/{LoadStockRequest,InventoryItemResponse}.java`.

- [x] **T3 — StockMove: atomic move + history** — commit `79e7875`
  FR-STK-03, FR-MOV-01, BR-05/06/10/11, AD-02, AD-07 (`CopyOnWriteArrayList`). Depends on T2. Adds the
  coarse-grained lock in `StockDomainService` covering **both** `loadStock` and `moveStock` (AD-02 —
  verify with a concurrency test, not just a sequential one). Files: `domain/model/StockMove.java`,
  `StockMoveRepository.java` (outbound port), `InMemoryStockMoveRepository.java`, `StockController.java`
  (`POST /stock/move`, `GET /stock/moves`), `api/dto/stock/{MoveStockRequest,StockMoveResponse}.java`.

- [x] **T4 — ReplenishmentRule** — commit `5fce5d1`
  FR-RUL-01, BR-01–04. Depends on T1 (validates location exists and is `PICKING`). Files:
  `domain/model/ReplenishmentRule.java`, ports, `ReplenishmentRuleDomainService.java`,
  `InMemoryReplenishmentRuleRepository.java`, `ReplenishmentRuleController.java`, `api/dto/rule/*`.

- [ ] **T5 — Seeder**
  §7 seed dataset, D14 (seed never produces a `StockMove`), AD-08. Depends on T1, T2/T3, T4.
  `infra/config/WarehouseSeeder.java` (`CommandLineRunner`), sequenced `LocationService` →
  `ReplenishmentRuleService` → `StockService.loadStock`. No new domain logic — verify by booting the app
  and checking `GET /locations`, `GET /stock`, rules via Swagger/curl.

- [ ] **T6 — ReplenishmentTask: evaluate/generate + list**
  FR-TSK-01/02, D1–D5, AD-04 (selection as a private method). Depends on T2, T3, T4, T5 (seed data used
  for manual verification). Files: `domain/model/ReplenishmentTask.java`, `ReplenishmentTaskStatus.java`,
  ports, `ReplenishmentTaskDomainService.java` (partial), `InMemoryReplenishmentTaskRepository.java`,
  `ReplenishmentTaskController.java` (partial), `api/dto/task/*`.

- [ ] **T7 — ReplenishmentTask: confirm + cancel**
  FR-TSK-03/04, BR-07/08/09, AD-03 (`ReplenishmentTaskDomainService` composes `StockService.moveStock`).
  Depends on T6. Completes `ReplenishmentTaskController` and `ReplenishmentTaskDomainService`.

- [ ] **T8 — README + end-to-end verification**
  Deliverable requirement (`SPECS.md` "Entregables"): README section on how to run, seed, and exercise the
  full flow. Depends on T1–T7. Full `./gradlew test` run, manual pass of all 11 endpoints via
  Swagger/curl against the seeded scenario (including the SKU-100/SKU-300/SKU-200 walkthrough from
  `docs/SRS.md` §7), traceability check against `docs/SRS.md` §8 acceptance criteria.

## Acceptance Criteria

Per `SPECS.md`: all endpoints work end-to-end (Swagger/curl); every error returns the correct status
(400/404/409), no bare 500s for expected cases; API documented in OpenAPI/Swagger; replenishment business
rules covered by tests. Per `docs/SRS.md` §8 traceability table.

## Progress

- 2026-09-20: Branched `feature/replenishment-module` off `main`. Committed pending `CLAUDE.md`/`mise.toml`
  (chore), `docs/SRS.md` (docs), `docs/ARCHITECTURE.md` (docs) — all pre-existed uncommitted on `main`.
  Task file created.
- 2026-09-20: T1 delegated to a writer (TDD red→green→refactor, tests written first). On review, found the
  writer's `ReplenishmentExceptionHandler` used `@RestControllerAdvice(basePackageClasses = ...)`, which
  scopes by package, not by class — since `LocationController` shares a package with `UserController`, this
  didn't actually achieve AD-05's "scoped to the new controllers" intent (it was inert today only because
  `UserController` already self-handles every exception, not because of real isolation). Fixed to
  `assignableTypes` for exact per-class scoping, re-ran the full suite, committed as `04e1f44`.

## Verification Evidence

- **T1** (`04e1f44`): `./gradlew test` — BUILD SUCCESSFUL, all tests green (`LocationDomainServiceTest`
  3/3 domain-only, `LocationControllerIntegrationTest` 3/3 MockMvc, pre-existing `UserControllerIntegrationTest`
  1/1 unaffected). Re-verified myself after the `assignableTypes` fix, not just from the writer's report.
  `gentle-ai review assess` could not run standalone (requires the full `review status` inventory handshake
  to declare untracked paths, which engages the native review lifecycle machinery) — RDD is off and that
  lifecycle isn't authorized, so treated as unassessable → high tier, satisfied via manual independent
  review: read every new domain file, reviewed the full diff (`git diff --stat`, `git diff` on the modified
  file), found and fixed the scoping defect above.
- **T2** (`8923385`): `./gradlew test` — BUILD SUCCESSFUL, 18/18 tests green (`StockDomainServiceTest` 6/6,
  `StockControllerIntegrationTest` 5/5, T1 and `User` tests unaffected, 7/7 unchanged). Re-ran myself after
  reading every new file; no defects found, no fix needed this round.
- **T3** (`79e7875`): `./gradlew clean test` — BUILD SUCCESSFUL. `StockDomainServiceTest` 16/16 (confirmed
  via JUnit XML report, including `moveStock_ShouldConserveQuantity_UnderConcurrentMoves` with 0
  failures/errors), `StockControllerIntegrationTest` 14/14, T1/T2/`User` tests unaffected. Independently
  re-ran the full suite myself (not just trusting the writer's report) and read every touched file.
- **T4** (`5fce5d1`): `./gradlew clean test` — BUILD SUCCESSFUL, 17 actionable tasks executed. All tests
  green including `ReplenishmentRuleDomainServiceTest` (6, new) and
  `ReplenishmentRuleControllerIntegrationTest` (5, new); T1/T2/T3/`User` unaffected. Read
  `ReplenishmentRuleDomainService`, `ReplenishmentRule`, and the `DomainConfiguration`/`LocationRepository`
  diffs myself; no defects found.

- 2026-09-20: T2 delegated and reviewed clean — no defects found this time (writer correctly extended
  `ReplenishmentExceptionHandler`'s `assignableTypes` and `DomainConfiguration` additively, matched T1's
  style throughout). `StockService.query(String sku, String location)` chosen over `Optional<String>`
  parameters — nullable `String` mirrors `@RequestParam(required = false)` directly, no unwrap ceremony;
  AND-filter composition lives in `StockDomainService`, not the repository. `POST /stock` returns `200` (not
  `201` like `POST /locations`) — deliberate, since it's upsert/set semantics (D8), not pure creation.
  Committed as `8923385`.

- 2026-09-20: T3 delegated and reviewed — the architecturally critical task. Verified independently (not
  just from the writer's report): read the full `StockDomainService.moveStock`/`loadStock` implementation,
  confirmed both share the single `stockLock` monitor exactly as AD-02 requires; read the 20-thread/200-unit
  concurrency test and confirmed via the JUnit XML report it actually ran and passed (0 failures); re-ran
  `./gradlew clean test` myself, 17 actionable tasks executed, BUILD SUCCESSFUL. `git diff --stat` matched
  the expected file list exactly — no scope creep. Writer added a `from != to` → 400 check not in my
  original task description, verified it's directly required by SRS FR-STK-03 (not invented). Writer also
  self-reported doing the "remove the lock, watch the concurrency test fail" sanity check before restoring
  it — a good practice I'll ask for again on any future concurrency-sensitive task. Committed as `79e7875`.

- 2026-09-20: T4 delegated and reviewed clean — no logic defects. Writer correctly flagged and made two
  reasoned deviations rather than following my instructions literally: (1) added `findByCode` to
  `LocationRepository`, which forced a one-line mechanical addition to the `LocationRepository` fakes inside
  `LocationDomainServiceTest`/`StockDomainServiceTest` just to keep those compiling — verified the diff is
  exactly that one method each, nothing else changed; (2) did NOT add an explicit
  `ReplenishmentRuleRepository` `@Bean` to `DomainConfiguration` (I'd asked for one) — verified against the
  actual file that repositories are never manually beaned there, only `@Repository`-scanned and injected as
  constructor params; the writer's version matches the real convention, my instruction was wrong on this
  detail. Committed as `5fce5d1`.

## Next Step

Start T5 (Seeder), delegated to a bounded writer.
