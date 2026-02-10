# AI Agent Guidelines

This document provides technical context for AI coding assistants working on this codebase.

## Project Overview

Multi-module Maven project implementing async webhook processing with Temporal.io orchestration.

**Stack**: Java 25 + Spring Boot 3.5.9 + Temporal.io | Next.js 16 + React 19 + TypeScript 5

| Module            | Port | Purpose                                        |
|-------------------|------|------------------------------------------------|
| `main-app`        | 8082 | Client-facing API entry point                  |
| `connector-app`   | 8080 | Queue orchestrator with Temporal workflows     |
| `channel-app`     | 8081 | Async price/stock validator (external service) |
| `shared-model`    | N/A  | Common POJOs, exceptions, observability        |
| `web-app`         | 3000 | Next.js React dashboard                        |

---

## Build & Run Commands

### Backend (Java/Maven)

```bash
# Build
mvn clean compile                    # Build all modules
mvn clean compile -pl connector-app  # Build single module
mvn clean package                    # Package all (creates JARs)

# Run (development)
cd channel-app && mvn spring-boot:run
cd connector-app && mvn spring-boot:run
cd main-app && mvn spring-boot:run

# Run (production JAR)
java -jar target/<module>-0.0.1-SNAPSHOT.jar
```

### Frontend (TypeScript/npm)

```bash
cd web-app
npm install        # Install dependencies
npm run dev        # Development server (localhost:3000)
npm run build      # Production build
npm run lint       # Run ESLint
```

---

## Testing Commands

### Java Tests (JUnit 5)

```bash
mvn test                                        # Run all tests
mvn test -pl connector-app                      # Run tests in single module
mvn test -Dtest=TemporalApplicationTests        # Run single test class
mvn test -Dtest=TemporalApplicationTests#contextLoads  # Run single test method
```

### Frontend Tests (Playwright E2E)

```bash
cd web-app
npx playwright test                             # Run all E2E tests
npx playwright test tests/example.spec.ts       # Run single test file
npx playwright test -g "has title"              # Run test by name pattern
npx playwright test --ui                        # Interactive UI mode
```

### Integration Tests

```bash
./test_connect_state_management.sh
./test_new_dual_processing.sh
```

---

## Code Style Guidelines

### Java

**Package Structure**: `com.example.<module>.<layer>`
- Examples: `com.example.temporal.controller`, `com.example.shared.model`

**Naming Conventions**:
- Classes: `PascalCase` (e.g., `RequestService`, `WebhookController`)
- Methods/variables: `camelCase` (e.g., `processPriceAndStock`)
- Constants: `UPPER_SNAKE_CASE` (e.g., `private static final Logger LOG`)
- Packages: `lowercase`

**Data Classes**: Use Java `record` for immutable DTOs:
```java
public record PriceAndStockRequest(@NonNull String orderId, int price, int stock) {}
```

**Logger Declaration**: Use SLF4J with class-level constant:
```java
private static final Logger LOG = LoggerFactory.getLogger(MyClass.class);
```

**Dependency Injection**: Constructor injection (no `@Autowired` on fields):
```java
private final RequestService requestService;

public WebhookController(RequestService requestService) {
    this.requestService = requestService;
}
```

**Spring Annotations**:
- Controllers: `@RestController`, `@PostMapping`, `@GetMapping`
- Services: `@Service`
- Configuration: `@Configuration`, `@ConfigurationProperties`

### TypeScript/React

**Imports**: Group by external libraries, then internal modules:
```typescript
import { useState, useEffect, useCallback } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { MyComponent } from '@/components/MyComponent';
```

**Naming**:
- Components/Interfaces: `PascalCase` (e.g., `OrderState`, `BurstButton`)
- Functions/variables: `camelCase` (e.g., `fetchMainAppStatus`)
- Files: `kebab-case.tsx` for components, `camelCase.ts` for utilities

**TypeScript**:
- Use `interface` for object shapes (not `type` aliases)
- Enable `strict` mode (already configured)
- Use path alias `@/*` for imports from project root

**Components**: Functional components with hooks, use `'use client'` directive when needed:
```typescript
'use client';
export function MyComponent({ prop }: { prop: string }) { ... }
```

---

## Error Handling

### Java

Use the centralized `GlobalExceptionHandler` in `shared-model`:

**Custom Exceptions**:
- `ValidationException` -> 400 Bad Request
- `NotFoundException` -> 404 Not Found
- `ServiceUnavailableException` -> 503 Service Unavailable

**Pattern**:
```java
if (order == null) {
    throw new NotFoundException("Order not found: " + orderId);
}
```

### TypeScript

Graceful degradation with fallback values:
```typescript
try {
    const response = await fetch(url);
    if (!response.ok) return { content: [], totalElements: 0 };
    return await response.json();
} catch (error) {
    console.error('[API] Fetch error:', error);
    return { content: [], totalElements: 0 };
}
```

---

## Temporal.io Patterns

**Workflow Interface**: Use Temporal annotations:
```java
@WorkflowInterface
public interface ProcessPriceAndStockWorkflow {
    @WorkflowMethod
    void processPriceAndStock(PriceAndStockRequest request, String correlationId);
    
    @QueryMethod
    RequestState getState();
    
    @SignalMethod
    void setPriceResponse(CallbackResponse response);
}
```

**Activity Implementations**: Separate from workflow logic, use `*ActivityImpl` suffix.

**Workers**: Register workflows and activities with task queues.

---

## Database

- **ORM**: jOOQ 3.19.15 (not JPA/Hibernate)
- **Migrations**: Flyway (SQL files in `src/main/resources/db/migration/`)
- **Database**: PostgreSQL 16
- **Connection Pool**: HikariCP

---

## Observability

**OpenTelemetry Integration**: Services instrumented with OTEL Java agent.

**Custom Metrics** (in `shared-model/observability/`):
- `process.requests.received` - Counter
- `process.requests.completed` - Counter
- `process.queue.size` - Gauge

**Span Attributes**: `order.id`, `correlation.id`, `process.status`

---

## Key Architecture Concepts

1. **Dual Identifiers**: `orderId` (business key) + `correlationId` (tracing UUID)
2. **Queue-Based Processing**: Decoupled intake from processing with batch operations
3. **Callback Pattern**: Async processing with HTTP callbacks
4. **State Machine**: `queued -> processing_price -> processing_stock -> completed/failed`

---

## Infrastructure

```bash
# Start all infrastructure (Temporal + PostgreSQL + SigNoz)
docker compose up -d

# SigNoz UI: http://localhost:8085
# Temporal UI: http://localhost:8233 (if configured)
```

---

## File Locations

| Type                    | Location                                           |
|-------------------------|----------------------------------------------------|
| Java sources            | `<module>/src/main/java/com/example/...`           |
| Java tests              | `<module>/src/test/java/com/example/...`           |
| DB migrations           | `<module>/src/main/resources/db/migration/`        |
| Spring config           | `<module>/src/main/resources/application.yml`      |
| React components        | `web-app/components/`                              |
| React pages             | `web-app/app/`                                     |
| TypeScript API client   | `web-app/lib/api.ts`                               |
| E2E tests               | `web-app/tests/`                                   |
