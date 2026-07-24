# Hexagonal Architecture Implementation

This project implements a hexagonal (ports and adapters) architecture with a clear separation between domain logic and infrastructure concerns. The architecture is designed to make both the domain layer and API contracts exportable as reusable libraries.

## Module Structure

```
java-backend-template/
├── domain/          # Pure domain logic (exportable library)
├── api/             # API contracts and DTOs (exportable library) 
├── infra/           # Infrastructure adapters
└── src/             # Main application (orchestration)
```

## Domain Module (`domain/`)

The domain module contains pure business logic with **no external dependencies**. This makes it easily exportable as a library that can be shared across multiple applications.

### Structure

```
domain/src/main/java/io/tenoro/app/domain/
├── model/                    # Domain entities and value objects
│   ├── User.java            # User aggregate root
│   └── UserId.java          # User ID value object
├── port/
│   ├── inbound/             # Use case interfaces (driving ports)
│   │   └── UserService.java
│   └── outbound/            # Repository interfaces (driven ports)
│       └── UserRepository.java
└── service/                 # Domain services (business logic)
    └── UserDomainService.java
```

### Key Components

#### Domain Models
- **User**: Aggregate root with business rules and invariants
- **UserId**: Value object for user identification

#### Ports (Interfaces)
- **Inbound Ports**: Define use cases (what the application does)
  - `UserService`: User management operations
- **Outbound Ports**: Define external dependencies (what the application needs)
  - `UserRepository`: Data persistence contract

#### Domain Services
- **UserDomainService**: Implements business logic and orchestrates domain operations

### Benefits of Pure Domain
- **Framework Independence**: No Spring Boot, JPA, or other framework dependencies
- **Testable**: Easy to unit test with simple mocks
- **Portable**: Can be shared across multiple applications
- **Clean**: Business logic is not polluted with technical concerns

## API Module (`api/`)

The API module contains Data Transfer Objects (DTOs) and API contracts that define the external interface of the application. This module is also exportable as a library for client applications.

### Structure

```
api/src/main/java/io/tenoro/app/api/
└── dto/                     # Data Transfer Objects
    ├── CreateUserRequest.java   # Request DTO for user creation
    ├── UpdateUserRequest.java   # Request DTO for user updates
    └── UserResponse.java        # Response DTO for user data
```

### Key Components

#### DTOs with OpenAPI Documentation
- **CreateUserRequest**: Immutable request object for user creation with validation annotations
- **UpdateUserRequest**: Request object for user updates
- **UserResponse**: Record-based response object for user data
- **OpenAPI Annotations**: Comprehensive @Schema annotations for API documentation

### Benefits of Separate API Module
- **Contract Sharing**: Client applications can depend on API contracts without domain logic
- **API Evolution**: Independent versioning of API contracts
- **Documentation**: Rich OpenAPI/Swagger documentation embedded in DTOs
- **Type Safety**: Strongly typed contracts for API consumers
- **Validation**: Centralized validation rules and constraints

## Infrastructure Module (`infra/`)

The infrastructure module contains all framework-specific implementations and adapters.

### Structure

```
infra/src/main/java/io/tenoro/app/infra/
├── adapter/
│   ├── inbound/             # Driving adapters (entry points)
│   │   └── web/            # REST controllers
│   │       ├── UserController.java
│   │       └── HelloController.java
│   └── outbound/           # Driven adapters (external systems)
│       └── persistence/    # Database implementations
│           └── InMemoryUserRepository.java
└── config/                 # Spring configuration
    └── DomainConfiguration.java
```

### Key Components

#### Inbound Adapters (Controllers)
- **UserController**: REST API endpoints for user operations, uses API DTOs
- **HelloController**: Simple greeting endpoints
- **DTO Mapping**: Converts between API DTOs and domain models

#### Outbound Adapters (Repositories)
- **InMemoryUserRepository**: In-memory implementation of UserRepository port

#### Configuration
- **DomainConfiguration**: Wires domain services with infrastructure components

## Main Application (`src/`)

The main application serves as the composition root, bringing together domain, API, and infrastructure modules.

### Key Files
- **Application.java**: Spring Boot main class with component scanning configuration

## API Endpoints

### User Management
- `POST /api/users` - Create a new user
- `GET /api/users` - Get all users
- `GET /api/users/{id}` - Get user by ID
- `GET /api/users/by-email?email={email}` - Get user by email
- `PUT /api/users/{id}/name` - Update user name
- `PUT /api/users/{id}/email` - Update user email
- `DELETE /api/users/{id}` - Delete user

### Health Check
- `GET /hello` - Simple greeting
- `GET /hello/greeting?name={name}` - Custom greeting

## Running the Application

### Using Gradle
```bash
./gradlew bootRun
```

### Using the JAR
```bash
./gradlew build
java -jar build/libs/demo-0.0.1-SNAPSHOT.jar
```

### Using the Run Script
```bash
./run.sh [port]
```

## Testing the API

Use the provided test script:
```bash
./test-api.sh
```

Or test manually with curl:
```bash
# Create a user
curl -X POST http://localhost:8080/api/users \
  -H "Content-Type: application/json" \
  -d '{"name": "John Doe", "email": "john@example.com"}'

# Get all users
curl http://localhost:8080/api/users
```

## Architecture Benefits

### 1. **Separation of Concerns**
- Business logic is isolated in the domain module
- Infrastructure concerns are in the infra module
- Application configuration is in the main module

### 2. **Testability**
- Domain logic can be tested without Spring Boot
- Easy to mock external dependencies
- Clear boundaries between layers

### 3. **Flexibility**
- Easy to swap implementations (e.g., database, web framework)
- Domain logic is not tied to specific technologies
- Can support multiple interfaces (REST, GraphQL, CLI)

### 4. **Reusability**
- Domain module can be packaged as a library
- Other applications can depend on the domain module
- Business logic can be shared across microservices

### 5. **Maintainability**
- Clear module boundaries
- Dependencies flow in one direction (infra → domain)
- Easy to understand and modify

## Example Usage as Library

### Using Domain Module
Other projects can use the domain module as a dependency:

```kotlin
// In another project's build.gradle.kts
dependencies {
    implementation("io.tenoro:java-backend-template-domain:0.0.1-SNAPSHOT")
}
```

Then implement their own infrastructure adapters:
```java
@Component
public class DatabaseUserRepository implements UserRepository {
    // JPA/MongoDB/Redis implementation
}
```

### Using API Module
Client applications can depend on the API module for type-safe contracts:

```kotlin
// In a client project's build.gradle.kts
dependencies {
    implementation("io.tenoro:java-backend-template-api:0.0.1-SNAPSHOT")
}
```

Then use the DTOs for API communication:
```java
// Client code
CreateUserRequest request = new CreateUserRequest("John Doe", "john@example.com");
UserResponse response = apiClient.createUser(request);
```

### OpenAPI Documentation
The API module provides rich OpenAPI documentation through annotations:
- Detailed schema descriptions for all DTOs
- Validation constraints and examples
- Type information and patterns
- Automatic Swagger UI generation

Access the interactive API documentation at: `http://localhost:8080/swagger-ui.html`

## Dependency Flow

```
Main Application
       ↓
   Infrastructure ──→ API (DTOs)
       ↓               ↓
     Domain ←──────────┘
```

**Key Principles:**
- Infrastructure depends on both Domain and API modules
- API module has minimal dependencies (only SpringDoc for documentation)
- Domain module has zero external dependencies
- Main application orchestrates all modules

This architecture allows multiple applications to share:
1. **Business Logic** (domain module)
2. **API Contracts** (api module)
3. **Custom Infrastructure** (their own implementations)

Perfect for microservices architectures where consistent domain logic and API contracts are crucial across services.
