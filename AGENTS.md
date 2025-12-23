# AGENTS.md

## Project Overview
Multi-module Maven project (Java/Spring Boot 3.5.9) for async webhook processing with callback pattern.

| Service | Port | Role |
|---------|------|------|
| main-app | 8082 | Client-facing API entry point |
| connector-app | 8080 | Queue orchestrator, coordinates dual processing |
| channel-app | 8081 | Async processor for price/stock validation |
| shared-model | N/A | Common POJOs, exceptions, observability helpers |

Pattern: Webhook → Correlation ID → Async Callback

## Quick Commands

### Java - All Modules
```bash
mvn clean compile                    # Build all
mvn test                            # Run all tests
mvn clean package                    # Package all
```

### Java - Single Module
```bash
# From root
mvn clean compile -pl connector-app
mvn test -pl connector-app

# Or from module directory
cd connector-app && mvn clean compile
```

### Java - Run Applications
```bash
cd connector-app && mvn spring-boot:run
cd main-app && mvn spring-boot:run
cd channel-app && mvn spring-boot:run
```

### Integration Tests
```bash
./test_main_state_management.sh
./test_new_dual_processing.sh
```

## Core Principles (Keep It Simple)

### 1. Share Code, Don't Repeat
- Use `shared-model` module for common types (POJOs, response records, observability)
- Duplicate code = technical debt

### 2. Single Responsibility Per File
- **Controllers**: Only handle HTTP in/out, delegate logic
- **Handlers**: One endpoint = one handler function
- **Services**: Business logic only
- **Types**: Pure data structures in dedicated files

### 3. Simple Over Clever
- Prefer explicit code over clever tricks
- One responsibility per function
- Avoid nested conditionals - return early
- Use standard library features

### 4. Async Pattern
- Accept → Return 202 → Process in background → Callback
- Use `@Async` or separate threads for async processing
- Always log before/after async work

## Java Guidelines

### Controllers
```java
@RestController
public class Controller {
    private static final Logger LOG = LoggerFactory.getLogger(Controller.class);
    private final Service service; // Delegate logic

    @PostMapping("/endpoint")
    public ResponseEntity<Response> handle(@RequestBody Request req) {
        return service.process(req); // One-liner handlers preferred
    }
}
```

### Services
```java
@Service
public class ProcessService {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessService.class);
    private final Map<String, Process> processes = new ConcurrentHashMap<>();

    public ResponseEntity<Response> process(Request req) {
        // Business logic here
    }
}
```

### POJOs
```java
public class Request {
    private String field;

    public Request() {} // Required for JSON

    // Getters/setters only - no logic here
    // Override toString() for debugging
}
```

### Error Handling
- Validate early: `if (missing) return badRequest();`
- Wrap external calls in try-catch
- Return `Map.of("error", "message")` for failures
- Always log errors with context (correlation ID)

### Configuration
- Use `application.yaml` in each module's `src/main/resources`
- Centralize port configs in parent POM properties:
  ```yaml
  server:
    port: ${module.port:default}
  ```
- Parent POM defines: `connector.port=8080`, `channel.port=8081`, `main.port=8082`

## Naming Conventions

- Classes: `PascalCase`
- Methods/vars: `camelCase`
- Constants: `UPPER_SNAKE_CASE`

## Testing Strategy

### Unit Tests
- Test one thing per test
- Use descriptive names: `testInvalidInput_returnsBadRequest`
- Mock external calls (HTTP, DB)
- Test both success and error paths

### Integration Tests
- Use the provided shell scripts
- Test full request flow end-to-end
- Verify callback patterns work correctly

## Anti-Patterns (Avoid These)

❌ Large files (>200 lines)
❌ Nested logic (more than 2 levels deep)
❌ Duplicated validation logic
❌ God objects doing too much
❌ Silent failures - always log errors
❌ Mixing HTTP handling with business logic
❌ Circular dependencies between modules

## Best Practices Summary

✅ **One responsibility**: Each function does one thing well
✅ **Share code**: Use shared module for common types/utilities
✅ **Early returns**: Validate first, fail fast
✅ **Explicit is better**: Clear names over clever code
✅ **Log everything**: Key events + errors with context
✅ **Graceful shutdown**: Handle interrupts properly
✅ **Simple types**: Primitives over complex abstractions
✅ **Module boundaries**: Keep connector-app, main-app, channel-app separate

## Context7
Use Context7 MCP tools for library docs automatically.
