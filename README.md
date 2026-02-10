# temporal-app

Multi-module Maven project implementing async webhook processing with **Temporal.io** workflow orchestration.

## Overview

This project demonstrates distributed workflow orchestration using Temporal.io:

- **Workflow-based processing**: Each order is a durable workflow with signal-based callbacks
- **Batch processing**: Price and stock updates are batched via long-running singleton workflows
- **Automatic retries**: Activity failures handled with exponential backoff
- **Distributed tracing**: Correlation IDs + OpenTelemetry integration

## Services

| Service           | Port | Role                                                |
|-------------------|------|-----------------------------------------------------|
| **main-app**      | 8082 | Client-facing API, receives orders and final callbacks |
| **connector-app** | 8080 | Temporal workflow orchestrator (core business logic)   |
| **channel-app**   | 8081 | External price/stock validation service (simulated)    |
| **shared-model**  | N/A  | Common POJOs, exceptions, observability helpers        |
| **web-app**       | 3000 | Next.js monitoring dashboard                           |

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.9+
- Docker (for Temporal server)
- Node.js 18+ (for web-app)

### 1. Start Infrastructure

```bash
docker compose up -d  # Starts Temporal server + PostgreSQL + SigNoz
```

### 2. Build & Run

You *must* use a tmux session, if you are not familiar with tmux, you should, and run:
```bash
# brew install tmux
tmux
```

```bash
./start-all
```

To stop the entire stack use:
```bash
./stop-all
```

### 3. Test

There is no test but you can make requests and use the UI.

```bash
curl -X POST http://localhost:8082/priceAndStock \
  -H "Content-Type: application/json" \
  -d '{"orderId": "order-123", "price": 5000, "stock": 500}'

# Check status
curl http://localhost:8082/status/order-123
```

---

## Architecture

### Workflow Overview

```
┌─────────────┐                              ┌───────────────────┐
│  main-app   │  POST /webhook/priceAndStock │  connector-app    │
│   (8082)    │ ────────────────────────────▶│     (8080)        │
└──────▲──────┘                              │                   │
       │                                     │  WorkflowClient   │
       │ Final                               │    .start()       │
       │ Callback                            └─────────┬─────────┘
       │                                               │
       │                                               ▼
       │                              ┌────────────────────────────────┐
       │                              │      TEMPORAL SERVER           │
       │                              │      (localhost:7233)          │
       │                              │                                │
       │   ┌──────────────────────────┴────────────────────────────┐   │
       │   │   ProcessPriceAndStockWorkflow (per-order)            │   │
       │   │   Task Queue: "process-price-and-stock"               │   │
       │   │                                                       │   │
       │   │   1. Signal price batch workflow ─────┐               │   │
       │   │   2. Signal stock batch workflow ─────┼───┐           │   │
       │   │   3. Await callbacks (30s timeout)    │   │           │   │
       │   │   4. Send final callback to main-app  │   │           │   │
       │   └───────────────────────────────────────┼───┼───────────┘   │
       │                                           │   │               │
       │   ┌───────────────────────────────────────┘   │               │
       │   │                                           │               │
       │   ▼                                           ▼               │
       │   ┌─────────────────────┐   ┌─────────────────────┐           │
       │   │ SendPriceWorkflow   │   │ SendStockWorkflow   │           │
       │   │ (singleton)         │   │ (singleton)         │           │
       │   │                     │   │                     │           │
       │   │ Batch: 10 items/5s  │   │ Batch: 10 items/1s  │           │
       │   │                     │   │ Rate limit: 0.4/s   │           │
       │   │ Continue-as-new:100 │   │ Continue-as-new:100 │           │
       │   └──────────┬──────────┘   └──────────┬──────────┘           │
       │              │                         │                      │
       └──────────────┴─────────────────────────┴──────────────────────┘
                      │                         │
                      ▼                         ▼
              ┌───────────────────────────────────────┐
              │         channel-app (8081)            │
              │   POST /price/batch, /stock/batch     │
              │              │                        │
              │              ▼                        │
              │   POST /callback/{orderId}?type=...   │
              └───────────────────────────────────────┘
```

### Workflows

| Workflow | Type | Task Queue | Purpose |
|----------|------|------------|---------|
| `ProcessPriceAndStockWorkflow` | Per-order | `process-price-and-stock` | Orchestrates single order processing |
| `SendPriceUpdateToChannelWorkflow` | Singleton | `send-price-update-to-channel` | Batches price updates |
| `SendStockUpdateToChannelWorkflow` | Singleton | `send-stock-update-to-channel` | Batches stock updates (rate-limited) |

### ProcessPriceAndStockWorkflow

Main workflow for processing individual orders:

```java
@WorkflowInterface
public interface ProcessPriceAndStockWorkflow {
    @WorkflowMethod
    void processPriceAndStock(PriceAndStockRequest request, String correlationId);
    
    @QueryMethod
    RequestState getState();  // Real-time state inspection
    
    @SignalMethod
    void setPriceResponse(CallbackResponse response);  // Async callback
    
    @SignalMethod
    void setStockResponse(CallbackResponse response);  // Async callback
}
```

**Workflow ID**: `price-and-stock-{orderId}` (idempotent per order)

**Flow**:
1. Receive order request
2. Signal both batch workflows (price + stock)
3. Wait for callbacks via `@SignalMethod` (30s timeout)
4. Send combined result to main-app

### Batch Workflows (Singleton Pattern)

Long-running workflows that accumulate items and send in batches:

```java
@WorkflowInterface
public interface SendPriceUpdateToChannelWorkflow {
    @WorkflowMethod
    void run(long startBatch);
    
    @SignalMethod
    void addPriceUpdate(PriceBatchItem item);  // Accumulate items
    
    @SignalMethod
    void stopWorkflow();  // Graceful shutdown
    
    @QueryMethod
    int getPendingCount();  // Items in current batch
    
    @QueryMethod
    long getBatchCounter();  // Total batches sent
}
```

**Batching Strategy**:
- **Trigger**: 10 items OR timeout (5s price / 1s stock)
- **Continue-as-new**: After 100 batches (prevents history bloat)
- **Rate limiting**: Stock worker limited to 0.4 ops/second

### Activities

| Activity | Purpose |
|----------|---------|
| `SignalPriceBatchWorkflowActivity` | SignalWithStart pattern for price batch |
| `SignalStockBatchWorkflowActivity` | SignalWithStart pattern for stock batch |
| `SendPriceBatchToChannelActivity` | HTTP POST batch to channel-app |
| `SendStockBatchToChannelActivity` | HTTP POST batch to channel-app |
| `SendFinalCallbackActivity` | Combine results + HTTP callback to main-app |

**Retry Configuration**:
```java
RetryOptions.newBuilder()
    .setMaximumAttempts(5)
    .setInitialInterval(Duration.ofSeconds(1))
    .setMaximumInterval(Duration.ofSeconds(30))
    .setBackoffCoefficient(2.0)
    .build()
```

### Workers

Three workers process different task queues:

```java
// Main worker - handles order workflows and many other tasks, it is more of a general pupose worker
@Component
public class ProcessPriceAndStockWorker {
    public static final String QUEUE = "process-price-and-stock";
    // Registers: ProcessPriceAndStockWorkflowImpl + activities
}

// Price batch worker specialized to process price request to the channel
@Component  
public class SendPriceUpdateToChannelWorker {
    public static final String QUEUE = "send-price-update-to-channel";
    // Registers: SendPriceBatchToChannelActivityImpl
}

// Stock batch worker (rate-limited) specialized to process stock request to the channel
@Component
public class SendStockUpdateToChannelWorker {
    public static final String QUEUE = "send-stock-update-to-channel";
    // Rate limited: 0.4 ops/second, uses virtual threads
}
```

---

## Connector-App Deep Dive

### Request Processing Flow

```
POST /webhook/priceAndStock
         │
         ▼
┌─────────────────────────┐
│    RequestService       │
│    .processPriceAndStock│
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────────────────────────────┐
│  WorkflowClient.start(                          │
│    workflow::processPriceAndStock,              │
│    request,                                     │
│    correlationId                                │
│  )                                              │
│                                                 │
│  WorkflowOptions:                               │
│    - workflowId: "price-and-stock-{orderId}"    │
│    - conflictPolicy: TERMINATE_EXISTING         │
│    - taskQueue: "process-price-and-stock"       │
│    - searchAttributes: correlationId            │
└─────────────────────────────────────────────────┘
```

### Callback Processing

```
POST /callback/{orderId}?type=price
         │
         ▼
┌─────────────────────────┐
│    RequestService       │
│    .processCallback     │
└───────────┬─────────────┘
            │
            ▼
┌─────────────────────────────────────────────────┐
│  ProcessPriceAndStockWorkflow workflow =        │
│    workflowClient.newWorkflowStub(              │
│      ProcessPriceAndStockWorkflow.class,        │
│      workflowId                                 │
│    );                                           │
│                                                 │
│  workflow.setPriceResponse(callback);  // Signal│
└─────────────────────────────────────────────────┘
```

### Configuration

```yaml
# connector-app/src/main/resources/application.yml
spring:
  temporal:
    connection:
      target: localhost:7233
    namespace: default
    workers-auto-discovery:
      packages:
        - com.example.temporal.temporal.workflow
```

---

## API Endpoints

### main-app (8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/priceAndStock` | Submit order `{orderId, price, stock}` |
| GET | `/status` | List all order states (paginated) |
| GET | `/status/{orderId}` | Get order state |
| POST | `/burst/{count}` | Load test: submit N orders |
| DELETE | `/queues` | Clear all queues and states |

### connector-app (8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/webhook/priceAndStock` | Receive order from main-app |
| POST | `/webhook/priceAndStock/batch` | Batch order submission |
| GET | `/status` | List workflow states (paginated) |
| GET | `/status/{orderId}` | Get workflow state |
| POST | `/callback/{orderId}` | Receive callback from channel-app |
| GET | `/batch/health` | Batch workflow health status |

### channel-app (8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/price` | Validate price (async callback) |
| POST | `/stock` | Validate stock (async callback) |
| POST | `/price/batch` | Batch price validation |
| POST | `/stock/batch` | Batch stock validation |

---

## Monitoring

### Temporal UI

```bash
# Access at http://localhost:8233
docker compose up -d
```

View:
- Running/completed workflows
- Workflow history and state
- Task queue metrics

### SigNoz (OpenTelemetry)

Use dashboard in [monitoring](monitoring) (some charts does not work this is normal)

Telemetry is automatic using the script [start-all](start-all.sh)
```bash
# Access at http://localhost:8085
# Login: admin@signoz.local / Admin@123

# Run with OTEL agent
cd connector-app && \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
OTEL_SERVICE_NAME=connector-app \
java -javaagent:target/opentelemetry-javaagent.jar \
  -jar target/connector-app-0.0.1-SNAPSHOT.jar
```

### Custom Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `process.requests.received` | Counter | Orders received |
| `process.requests.completed` | Counter | Orders completed |
| `process.queue.size` | Gauge | Batch queue size |
| `process.callback.latency` | Histogram | End-to-end latency |

---

## Commands Reference

```bash
# Build
mvn clean package
mvn test -pl connector-app

# Infrastructure
docker compose up -d      # Start Temporal + PostgreSQL + SigNoz
docker compose down -v    # Stop and clear data

# Development
cd connector-app && mvn spring-boot:run
cd channel-app && mvn spring-boot:run
cd main-app && mvn spring-boot:run

# Testing
curl -X POST http://localhost:8082/priceAndStock \
  -H "Content-Type: application/json" \
  -d '{"orderId": "test-1", "price": 100, "stock": 50}'

curl http://localhost:8082/status/test-1 | jq

# Load testing
curl -X POST http://localhost:8082/burst/100

# Health checks
curl http://localhost:8080/batch/health
curl http://localhost:8080/actuator/health
```

---

## Key Temporal Patterns Used

1. **Signal-Based Callbacks**: Workflows receive async results via `@SignalMethod`
2. **Query Methods**: Real-time workflow state inspection
3. **SignalWithStart**: Atomically start singleton workflow if not exists, then signal
4. **Continue-As-New**: Prevent event history bloat in long-running batch workflows
5. **Activity Retry**: Exponential backoff with configurable attempts
6. **Worker Rate Limiting**: `setMaxTaskQueueActivitiesPerSecond()` for external API compliance
7. **Virtual Threads**: Java 21+ for improved worker concurrency
8. **Workflow ID Conflict Policy**: `TERMINATE_EXISTING` for last-event-wins semantics

---

## Project Structure

```
temporal-app/
├── connector-app/           # Temporal workflow orchestrator
│   └── src/main/java/com/example/temporal/
│       ├── temporal/
│       │   ├── workflow/    # Workflow interfaces
│       │   ├── impl/        # Workflow + activity implementations
│       │   ├── activities/  # Activity interfaces
│       │   ├── worker/      # Worker configurations
│       │   └── utils/       # WorkflowIdBuilder, TemporalHelper
│       ├── controller/      # REST endpoints
│       ├── service/         # Business logic
│       └── repository/      # jOOQ database access
├── main-app/                # Client API
├── channel-app/             # External service simulator
├── shared-model/            # Common POJOs, exceptions
├── web-app/                 # Next.js dashboard
├── docker-compose.yml       # Infrastructure
└── AGENTS.md                # AI coding guidelines
```

---

## Error Handling

| Scenario | Temporal Behavior |
|----------|-------------------|
| Activity failure | Retry with exponential backoff (max 5 attempts) |
| Callback timeout | `ApplicationFailure` after 30s, workflow fails |
| Channel unreachable | Activity retries, then workflow fails |
| Duplicate orderId | Previous workflow terminated (conflict policy) |
