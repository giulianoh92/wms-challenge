# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this repo is

A Spring Boot backend template (Java 24, Gradle multi-module, hexagonal architecture) being turned into a
**WMS (Warehouse Management System) replenishment module**. The full challenge spec — domain model
(`Location`, `InventoryItem`, `ReplenishmentRule`, `ReplenishmentTask`), the 10 required REST endpoints, the
seed dataset, and acceptance criteria — is authoritative in [`SPECS.md`](SPECS.md). Do not restate or fork
that spec elsewhere; read it directly when domain requirements are needed.

Core rule to keep in mind while implementing: when a picking location's stock for a SKU falls below its
`ReplenishmentRule.min`, it must be replenished from reserve location(s) up to `max`. `POST /stock/move` is
the atomic primitive every replenishment operation (including task confirmation) is built on — it must never
leave stock partially moved or negative.

The repo currently only implements the example `User` feature (CRUD, no relation to the WMS domain). It
exists purely as the **convention template** to follow when building `Location`/`InventoryItem`/
`ReplenishmentRule`/`ReplenishmentTask`.

## Commands

```bash
./run.sh              # run on :8080 (Spring profile "local")
./run.sh 9090          # run on a custom port
./gradlew bootRun      # equivalent, without the profile/port convenience

./gradlew build        # build all modules
./gradlew test         # run the full test suite (JUnit 5 via useJUnitPlatform)

# single test class / method (standard Gradle --tests filter; no repo-specific wrapper exists)
./gradlew test --tests "io.tenoro.app.UserControllerIntegrationTest"
./gradlew test --tests "io.tenoro.app.UserControllerIntegrationTest.getAllUsers_ShouldReturnEmptyListOfUsers"
```

- API base once running: `http://localhost:8080/api/templates` — Swagger UI at `.../documentation`, OpenAPI
  JSON at `.../openapi`. The context path is `/api/${spring.application.name}`
  (`src/main/resources/application.properties`), currently `templates`.
- No linter, formatter, or static-analysis plugin is configured anywhere in the Gradle build (checked all
  `*.kts` files) — there is nothing to run beyond compile/test.

## Module structure and dependency flow

Root project is `templates` (per `settings.gradle.kts`), containing subprojects `:domain`, `:api`, `:infra`,
plus the root itself as the bootable Spring Boot app (`src/`, applies the Boot plugin directly).

Confirmed from the actual `dependencies {}` blocks (not from prose docs, which drift):

```
domain   -> no module deps, no Spring deps (plain java plugin). Framework-free by construction.
api      -> no module deps either — does NOT depend on domain. Only springdoc + spring-core/data-commons,
            for @Schema annotations on DTOs. DTOs are structurally decoupled from domain models.
infra    -> depends on api(project(":domain")) [exposed transitively] and project(":api").
            Spring web/data-jpa/actuator, springdoc-webmvc-ui, H2 (runtime only, currently unused by User).
root app -> depends on domain, infra, and api directly; this is the composition root.
```

`@SpringBootApplication(scanBasePackages = "io.tenoro.app")` in `Application.java` is what pulls in beans
from the other modules' packages — don't assume component scanning is automatic across modules without it.

Versions: Java 24 (toolchain), Spring Boot 3.5.4, springdoc-openapi 2.8.6, Gradle 8.14.3 wrapper (jar is
committed to the repo, don't regenerate it casually).

## Conventions from the `User` feature (the map to follow for new features)

Package shape: `io.tenoro.app.<layer>.<sublayer>` — `domain.model` / `domain.port.inbound` /
`domain.port.outbound` / `domain.service` / `api.dto` / `infra.adapter.inbound.web[.mappers]` /
`infra.adapter.outbound.persistence` / `infra.config`.

**Error handling — no global exception handler.** There is no `@ControllerAdvice`/`@ExceptionHandler`
anywhere in the repo. Every controller method wraps its body in `try/catch/finally`:
`IllegalArgumentException` → manually built `ErrorResponse` + `400`; a bare `RuntimeException` (used for
"not found" from the domain service) → `404`; anything else → generic `500`/"Internal server error". Reads
that can miss use `Optional.isPresent()` and return `404` directly instead of throwing. New controllers are
expected to follow this same local try/catch shape unless you deliberately introduce a shared handler —
if you do, note it here so the convention is known repo-wide.

**Validation is manual, domain-side — no Bean Validation.** Zero `jakarta.validation` annotations anywhere
(`@Valid`, `@NotNull`, etc. all absent). Constraints (non-null, non-blank, format, cross-field, "already
exists") are enforced in the domain model's constructor/mutators and in the domain service, throwing
`IllegalArgumentException`. DTOs carry only `@Schema` documentation annotations, nothing enforced.

**OpenAPI annotations**: `@Schema` on DTO classes/fields (`api` module); `@Tag`/`@Operation`/`@ApiResponses`/
`@Parameter` on controller methods (`infra` module). Request-body annotations use the fully-qualified
`@io.swagger.v3.oas.annotations.parameters.RequestBody` to avoid clashing with Spring's `@RequestBody`.

**Bean wiring** happens in `infra/.../config/DomainConfiguration.java`: the domain service is manually
`new`'d inside a `@Bean` factory method, with the outbound port injected as a method parameter resolved from
the Spring context (supplied by an `@Repository`-annotated infra adapter). Domain classes themselves carry
no Spring annotations — only `infra`'s config/adapters know about Spring. Follow this pattern rather than
annotating domain services directly.

**Model/DTO style**: Lombok `@Data @Builder` classes with `private final` fields and an explicit validating
constructor, not Java `record`s. "Mutation" is done via methods that return a new instance (e.g.
`updateName`), never setters. One inconsistency to be aware of: `ErrorResponse` is a plain mutable POJO
(no-arg constructor + setters), unlike every other DTO — don't copy that shape for new error/response types
without a reason.

**Persistence**: in-memory only (`ConcurrentHashMap`-backed `@Repository`, e.g. `InMemoryUserRepository`).
H2 + JPA are present as dependencies and in the test datasource config but are not actually wired to the
`User` feature — treat them as unused scaffolding, not a persistence pattern to imitate, unless real DB
persistence is deliberately introduced.

## Testing

JUnit 5 + Spring `MockMvc`, no WebTestClient, no Testcontainers. The only existing test is
`src/test/java/io/tenoro/app/UserControllerIntegrationTest.java` (one test method) — **there are no domain
unit tests anywhere**, despite `domain/build.gradle.kts` declaring JUnit test dependencies. `domain`, `api`,
and `infra` currently have no `test` source sets at all.

Integration test shape: `@SpringBootTest(classes = Application.class)`, `@AutoConfigureWebMvc`,
`@Import(TestConfig.class)`, `@ActiveProfiles("test")`,
`@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)`; `MockMvc` is built manually in `@BeforeEach`
via `MockMvcBuilders.webAppContextSetup(...)` — **this does not apply the servlet context path**, so tests
call e.g. `get("/users")`, not `get("/api/templates/users")`. Keep this in mind when writing new controller
tests. `TestConfig` (`@TestConfiguration`) provides a `@Primary` `ObjectMapper` with `JavaTimeModule` +
`ParameterNamesModule` and `FAIL_ON_UNKNOWN_PROPERTIES` disabled.

Given the acceptance criteria require replenishment business rules to be covered by tests, and given there
is currently no precedent for isolated domain unit tests in this repo, that gap is expected to be filled as
part of implementing the new module — don't assume the single existing integration test is a complete
testing pattern to replicate as-is.

## Process

This project follows Organic Driven Development (ODD) with TDD as the default working process, per the
user's global Claude Code configuration — not something to re-derive or reconfigure per session.
