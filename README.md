# Módulo de Reposición WMS (Spring Boot + Arquitectura Hexagonal)

Backend Java con Spring Boot 3.5.4 / Java 24 y arquitectura hexagonal (ports & adapters) que implementa el
módulo de reposición (*replenishment*) de un WMS (Warehouse Management System): gestiona ubicaciones,
inventario, reglas de reposición y tareas de reposición.

> El enunciado completo del challenge está en [`SPECS.md`](SPECS.md); los requerimientos y el diseño en
> detalle viven en [`docs/SRS.md`](docs/SRS.md) y [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Proceso y metodología

Antes de escribir código se modeló el dominio en [`docs/SRS.md`](docs/SRS.md): las entidades (`Location`,
`InventoryItem`, `ReplenishmentRule`, `ReplenishmentTask`, `StockMove`), las reglas de negocio y las
decisiones de diseño que cubren los puntos que [`SPECS.md`](SPECS.md) dejaba abiertos — por ejemplo, cómo
priorizar entre varias ubicaciones de reserva o qué pasa con una tarea que falla al confirmarse por falta de
stock.

Con el SRS cerrado se hizo el diseño arquitectónico detallado en
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), siguiendo las convenciones de código que ya planteaba el
template original y que quedaron documentadas en [`CLAUDE.md`](CLAUDE.md) al inicializar el proyecto con
Claude Code (arquitectura hexagonal por módulo, manejo de errores sin `@ControllerAdvice`, wiring manual de
beans, etc.).

La implementación se hizo siguiendo ese mismo proceso — Organic Driven Development con TDD como método de
trabajo por defecto — con el conjunto de herramientas del ecosistema
[gentle-ai](https://github.com/Gentleman-Programming/gentle-ai) para Claude Code.

---

## 1. Instalar Java 24

Es lo **único** que necesitás instalar. Gradle viene incluido (wrapper `./gradlew`) y no hace falta base de datos.

La forma más simple es con [SDKMAN](https://sdkman.io/):

```shell
# 1. Instalar SDKMAN
curl -s "https://get.sdkman.io" | bash

# 2. Cargarlo en la terminal actual
source "$HOME/.sdkman/bin/sdkman-init.sh"

# 3. Instalar Java 24
sdk install java 24.0.2-tem
```

Verificá que quedó bien:

```shell
java -version    # debería mostrar la versión 24
```

> Si preferís, podés instalar cualquier JDK 24 a mano (Temurin, Oracle, etc.) y asegurarte de que
> `java -version` apunte a esa versión.

## 2. Levantar la app

```shell
./run.sh              # levanta en http://localhost:8080, perfil "local"
./run.sh 9090          # o el puerto que quieras
./gradlew bootRun      # equivalente, sin el atajo de puerto/perfil
```

Una vez arriba, todo cuelga del context-path `/api/templates`:

- API base: http://localhost:8080/api/templates
- **Swagger UI** (probá los endpoints desde el navegador): http://localhost:8080/api/templates/documentation
- OpenAPI JSON: http://localhost:8080/api/templates/openapi

## 3. Datos semilla (seed)

El dataset fijo (5 ubicaciones, 3 reglas de reposición, 6 registros de stock) se carga **automáticamente en
cada arranque**, sin ningún comando ni flag extra. El detalle completo está en
[`docs/SRS.md` §7](docs/SRS.md).

## 4. Correr los tests automáticos

```shell
./gradlew test
```

Para correr una clase o método puntual, fijate los ejemplos con el filtro `--tests` documentados en
[`CLAUDE.md`](CLAUDE.md#commands).

## 5. Probar el flujo completo a mano

La secuencia de abajo es el escenario exacto de [`docs/SRS.md` §7](docs/SRS.md), lista para copiar y pegar en
orden contra una app recién levantada. También podés seguirla desde Swagger UI si preferís el navegador.

```bash
BASE=http://localhost:8080/api/templates

# 1. Ubicaciones semilladas (5: PICK-01, PICK-02, RSV-01, RSV-02, RSV-03)
curl -s $BASE/locations

# 2. Stock semillado (6 registros)
curl -s $BASE/stock

# 3. Evaluar SKU-100 en PICK-01: necesita 100-5=95, reserva tiene 60+50=110 (alcanza)
#    -> 2 tareas OPEN: 60 desde RSV-01, después 35 desde RSV-02, fullyReplenished=true
curl -s -X POST $BASE/replenishment/tasks \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-100","locationCode":"PICK-01"}'

# 4. Evaluar SKU-300 en PICK-02: necesita 120-10=110, reserva solo tiene 70 en RSV-03
#    -> 1 tarea OPEN parcial de 70 unidades, fullyReplenished=false
curl -s -X POST $BASE/replenishment/tasks \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-300","locationCode":"PICK-02"}'

# 5. Evaluar SKU-200 en PICK-01: stock=40 ya >= min=10
#    -> replenishmentNeeded=false, no se crea ninguna tarea
curl -s -X POST $BASE/replenishment/tasks \
  -H "Content-Type: application/json" \
  -d '{"sku":"SKU-200","locationCode":"PICK-01"}'

# 6. Listar todas las tareas
curl -s $BASE/replenishment/tasks

# 7. Confirmar una de las tareas OPEN del paso 3 (usá su id real de la respuesta)
TASK_ID=<pegá-un-id-de-tarea-acá>
curl -s -X POST $BASE/replenishment/tasks/$TASK_ID/confirm

# 8. Verificar que el stock efectivamente se movió
curl -s "$BASE/stock?sku=SKU-100"

# 9. Verificar que el movimiento es trazable hasta la tarea
curl -s "$BASE/stock/moves?relatedTaskId=$TASK_ID"

# 10. Confirmar la misma tarea de nuevo falla con 409 (ya está CONFIRMED)
curl -s -w "\n%{http_code}\n" -X POST $BASE/replenishment/tasks/$TASK_ID/confirm

# 11. Cancelar otra tarea OPEN (por ejemplo, la del paso 4) - no mueve stock
OTHER_TASK_ID=<pegá-otro-id-de-tarea-acá>
curl -s -X POST $BASE/replenishment/tasks/$OTHER_TASK_ID/cancel
curl -s "$BASE/stock?sku=SKU-300"   # sin cambios
```

Algunos ejemplos de error que vale la pena probar directamente (siempre devuelven el status code
específico, nunca un `500` genérico):

```bash
# 400 - la regla referencia una ubicación existente pero del tipo equivocado (RESERVE en vez de PICKING)
curl -s -w "\n%{http_code}\n" -X POST $BASE/replenishment-rules \
  -H "Content-Type: application/json" -d '{"sku":"SKU-100","locationCode":"RSV-01","min":5,"max":20}'

# 404 - la ubicación no existe
curl -s -w "\n%{http_code}\n" -X POST $BASE/stock \
  -H "Content-Type: application/json" -d '{"sku":"SKU-100","locationCode":"NOPE-01","quantity":10}'

# 409 - mover más de lo disponible
curl -s -w "\n%{http_code}\n" -X POST $BASE/stock/move \
  -H "Content-Type: application/json" -d '{"sku":"SKU-100","from":"RSV-01","to":"PICK-01","quantity":999999}'
```

## 6. Colección de Postman

`postman/wms-replenishment.postman_collection.json` (junto con
`postman/wms-replenishment.postman_environment.json` para la variable `baseUrl`) cubre el circuito completo:
los 10 endpoints requeridos, el mismo recorrido de datos semilla de arriba, y los caminos de error
(400/404/409) de cada regla de negocio. Importá los dos archivos en Postman, elegí el environment "WMS
Reposición - Local" y corré toda la colección con el Collection Runner contra una app recién levantada —
los requests posteriores dependen del estado que arman los anteriores dentro de la misma carpeta, así que
corré todo de punta a punta. Las carpetas 2 a 4 usan fixtures propios (`PICK-99`/`RSV-99`/`SKU-900`) para no
pisar el escenario semillado de `SKU-100`/`SKU-200`/`SKU-300` que usa la carpeta 5, incluyendo el chequeo de
idempotencia (D4) y la garantía de que una tarea que falla al confirmar por falta de stock queda OPEN (D6).

También podés correrla sin interfaz con [Newman](https://github.com/postmanlabs/newman):

```bash
npx newman run postman/wms-replenishment.postman_collection.json \
  -e postman/wms-replenishment.postman_environment.json
```

---

## Cómo está organizado el proyecto

Arquitectura hexagonal en 3 módulos + la app:

```
domain/   -> modelo y lógica de negocio (Java puro, sin frameworks) + puertos (interfaces)
api/      -> DTOs de request/response (el contrato de la API)
infra/    -> adaptadores: controllers REST, persistencia, y wiring de beans (config)
src/      -> app Spring Boot (main) + tests
```

El ejemplo `User` que trae el template original se mantiene como referencia de convenciones — no tiene
relación con el dominio del WMS, pero muestra cómo se conecta cada capa:

```
UserController (infra)
   -> UserService (puerto inbound, domain)
      -> UserDomainService (domain)
         -> UserRepository (puerto outbound, domain)
            -> InMemoryUserRepository (infra)
```

El módulo de reposición sigue exactamente el mismo patrón para `Location`, `InventoryItem`,
`ReplenishmentRule` y `ReplenishmentTask` — por ejemplo:

```
ReplenishmentTaskController (infra)
   -> ReplenishmentTaskService (puerto inbound, domain)
      -> ReplenishmentTaskDomainService (domain)
         -> ReplenishmentTaskRepository (puerto outbound, domain)
            -> InMemoryReplenishmentTaskRepository (infra)
```

El "cableado" de las dependencias (qué implementación se inyecta en cada puerto) vive en
`infra/src/main/java/io/tenoro/app/infra/config/DomainConfiguration.java`.

La persistencia es **in-memory** (`ConcurrentHashMap`): no hace falta base de datos.

## Más detalle

- Requerimientos, modelo de dominio y reglas de negocio: [`docs/SRS.md`](docs/SRS.md)
- Decisiones de arquitectura y organización de módulos: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
- Enunciado original del challenge: [`SPECS.md`](SPECS.md)
