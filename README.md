# WMS Replenishment Module

A Spring Boot 3.5.4 / Java 24 backend implementing the replenishment module of a Warehouse Management
System (WMS), built for a technical hiring challenge (Product Engineer, Operaciones / WMS). It manages
locations, inventory, replenishment rules, and replenishment tasks, following hexagonal architecture. The
full domain model, endpoint list, and acceptance criteria are authoritative in [`SPECS.md`](SPECS.md); the
requirements and design detail live in [`docs/SRS.md`](docs/SRS.md) and
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — this README only covers running it and seeing it work.

## Prerequisites

- Java 24, managed through [`mise`](https://mise.jdx.dev/) (see `mise.toml`). Run `mise install` once if the
  toolchain isn't already provisioned.
- The Gradle wrapper is committed — no separate Gradle install needed.

## Running the app

```bash
./run.sh              # runs on :8080, Spring profile "local"
./run.sh 9090          # custom port
./gradlew bootRun      # equivalent, without the port/profile convenience
```

Once it's up:

- API base: `http://localhost:8080/api/templates`
- Swagger UI: `http://localhost:8080/api/templates/documentation`
- OpenAPI JSON: `http://localhost:8080/api/templates/openapi`

## Seed data

The seed dataset (5 locations, 3 replenishment rules, 6 inventory records) loads **unconditionally on every
startup** — no separate command or flag needed. See [`docs/SRS.md` §7](docs/SRS.md) for the exact dataset
and the expected outcome of each scenario below.

## Running the automated tests

```bash
./gradlew test
```

For running a single test class or method, see the `--tests` filter examples already documented in
[`CLAUDE.md`](CLAUDE.md#commands).

## Exercising the full flow manually

The sequence below is the exact scenario from `docs/SRS.md` §7, runnable as-is against a freshly started
app (copy-paste in order). You can follow along in Swagger UI instead if you prefer a browser.

```bash
BASE=http://localhost:8080/api/templates

# 1. Seeded locations (5: PICK-01, PICK-02, RSV-01, RSV-02, RSV-03)
curl -s $BASE/locations

# 2. Seeded stock (6 records)
curl -s $BASE/stock

# 3. Evaluate SKU-100 at PICK-01: needs 100-5=95, reserve has 60+50=110 (enough)
#    -> 2 OPEN tasks: 60 from RSV-01, then 35 from RSV-02, fullyReplenished=true
curl -s -X POST $BASE/replenishment/tasks \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-100","locationCode":"PICK-01"}'

# 4. Evaluate SKU-300 at PICK-02: needs 120-10=110, reserve only has 70 in RSV-03
#    -> 1 partial OPEN task of 70 units, fullyReplenished=false
curl -s -X POST $BASE/replenishment/tasks \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-300","locationCode":"PICK-02"}'

# 5. Evaluate SKU-200 at PICK-01: stock=40 already >= min=10
#    -> replenishmentNeeded=false, no task created
curl -s -X POST $BASE/replenishment/tasks \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-200","locationCode":"PICK-01"}'

# 6. List all tasks
curl -s $BASE/replenishment/tasks

# 7. Confirm one of the OPEN tasks from step 3 (use its real id from the response)
TASK_ID=<paste-a-task-id-here>
curl -s -X POST $BASE/replenishment/tasks/$TASK_ID/confirm

# 8. Verify the stock actually moved
curl -s "$BASE/stock?sku=SKU-100"

# 9. Verify the move is traceable back to the task
curl -s "$BASE/stock/moves?relatedTaskId=$TASK_ID"

# 10. Confirming the same task again fails with 409 (already CONFIRMED)
curl -s -w "\n%{http_code}\n" -X POST $BASE/replenishment/tasks/$TASK_ID/confirm

# 11. Cancel a different OPEN task (e.g. the one from step 4) - no stock moves
OTHER_TASK_ID=<paste-another-task-id-here>
curl -s -X POST $BASE/replenishment/tasks/$OTHER_TASK_ID/cancel
curl -s "$BASE/stock?sku=SKU-300"   # unchanged
```

A couple of error-path examples worth trying directly (all return the specific status code, never a bare
`500`):

```bash
# 400 - rule references an existing location of the wrong type (RESERVE instead of PICKING)
curl -s -w "\n%{http_code}\n" -X POST $BASE/replenishment-rules \
  -H "Content-Type: application/json" -d '{"sku":"SKU-100","locationCode":"RSV-01","min":5,"max":20}'

# 404 - location does not exist
curl -s -w "\n%{http_code}\n" -X POST $BASE/stock \
  -H "Content-Type: application/json" -d '{"sku":"SKU-100","locationCode":"NOPE-01","quantity":10}'

# 409 - moving more than what's available
curl -s -w "\n%{http_code}\n" -X POST $BASE/stock/move \
  -H "Content-Type: application/json" -d '{"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":999999}'
```

## More detail

- Requirements, domain model, and business rules: [`docs/SRS.md`](docs/SRS.md)
- Architecture decisions and module layout: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Original challenge spec: [`SPECS.md`](SPECS.md)
