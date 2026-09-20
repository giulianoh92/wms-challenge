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

- [ ] **T1 — Location + error-handling scaffolding**
  FR-LOC-01/02. Establishes the AD-05 pattern every later controller reuses: `NotFoundException`,
  `ConflictException`, `ReplenishmentExceptionHandler` (`@RestControllerAdvice` scoped to the new
  controllers only). Files: `domain/model/Location.java`, `LocationType.java`, `domain/exception/*`,
  `domain/port/{inbound,outbound}/Location*.java`, `domain/service/LocationDomainService.java`,
  `infra/adapter/outbound/persistence/InMemoryLocationRepository.java`,
  `infra/adapter/inbound/web/LocationController.java` + mapper, `infra/config/ReplenishmentExceptionHandler.java`,
  `api/dto/location/*`, `DomainConfiguration` wiring, domain unit tests, one MockMvc integration test.

- [ ] **T2 — InventoryItem: load and query stock**
  FR-STK-01/02. Depends on T1. Files: `domain/model/InventoryItem.java`,
  `domain/port/{inbound,outbound}/... Stock/Inventory...`, `domain/service/StockDomainService.java`
  (partial: `loadStock`, `queryStock`), `InMemoryInventoryRepository.java`, `StockController.java`
  (partial), `api/dto/stock/{LoadStockRequest,InventoryItemResponse}.java`.

- [ ] **T3 — StockMove: atomic move + history**
  FR-STK-03, FR-MOV-01, BR-05/06/10/11, AD-02, AD-07 (`CopyOnWriteArrayList`). Depends on T2. Adds the
  coarse-grained lock in `StockDomainService` covering **both** `loadStock` and `moveStock` (AD-02 —
  verify with a concurrency test, not just a sequential one). Files: `domain/model/StockMove.java`,
  `StockMoveRepository.java` (outbound port), `InMemoryStockMoveRepository.java`, `StockController.java`
  (`POST /stock/move`, `GET /stock/moves`), `api/dto/stock/{MoveStockRequest,StockMoveResponse}.java`.

- [ ] **T4 — ReplenishmentRule**
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
  Task file created. No implementation started yet.

## Verification Evidence

(none yet — filled in per task as work completes)

## Next Step

Start T1 (Location + error-handling scaffolding), delegated to a bounded writer, TDD red→green→refactor.
