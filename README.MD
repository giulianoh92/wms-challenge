# Backend Template (Spring Boot + Arquitectura Hexagonal)

Template para resolver el challenge. Es un backend Java con Spring Boot y arquitectura
hexagonal (ports & adapters), con un ejemplo funcional (`User`) que podés usar de referencia.

> El enunciado del challenge está en [`SPECS.md`](SPECS.md).

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

> Si preferís, podés instalar cualquier JDK 24 a mano (Temurin, Oracle, etc.) y asegurarte de que `java -version` apunte a esa versión.

## 2. Levantar la app

```shell
./run.sh            # levanta en http://localhost:8080
./run.sh 9090       # o el puerto que quieras
```

Equivalente, si preferís Gradle directo:

```shell
./gradlew bootRun
```

## 3. Probar que anda

Todo cuelga del context-path `/api/templates`:

- **Swagger UI** (probá los endpoints desde el navegador): http://localhost:8080/api/templates/documentation
- Health: http://localhost:8080/api/templates/actuator/health

Ejemplo con `curl` usando el CRUD de `User` que ya viene:

```shell
BASE=http://localhost:8080/api/templates

# Crear un usuario
curl -X POST "$BASE/users" -H 'Content-Type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com"}'

# Listar
curl "$BASE/users"
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

El ejemplo `User` muestra cómo se conecta cada capa:

```
UserController (infra)
   -> UserService (puerto inbound, domain)
      -> UserDomainService (domain)
         -> UserRepository (puerto outbound, domain)
            -> InMemoryUserRepository (infra)
```

El "cableado" de las dependencias (qué implementación se inyecta en cada puerto) vive en
`infra/src/main/java/io/tenoro/app/infra/config/DomainConfiguration.java`.

La persistencia del ejemplo es **in-memory** (un `Map` en memoria): no necesitás DB para empezar.
