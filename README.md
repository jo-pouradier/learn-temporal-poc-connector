# temporal-app

Multi-module Maven project implementing async webhook processing with a callback pattern.

<!-- TOC -->
* [temporal-app](#temporal-app)
  * [Purpose](#purpose)
  * [Overview](#overview)
  * [Services](#services)
  * [Project Structure](#project-structure)
  * [Quick Start](#quick-start)
    * [1. Build](#1-build)
    * [2. Run Services (3 terminals)](#2-run-services-3-terminals)
    * [3. Test](#3-test)
  * [Architecture](#architecture)
    * [Identifiers](#identifiers)
    * [Async Flow](#async-flow)
    * [State Transitions](#state-transitions)
    * [Key Design Patterns](#key-design-patterns)
  * [API Endpoints](#api-endpoints)
    * [connect-app (8082)](#connect-app-8082)
    * [connector-app (8080)](#connector-app-8080)
    * [channel-app (8081)](#channel-app-8081)
  * [Commands Reference](#commands-reference)
    * [Build](#build)
    * [Run (Development)](#run-development)
    * [Run (Production JAR)](#run-production-jar)
    * [Testing](#testing)
    * [Integration Tests](#integration-tests)
    * [Health Checks](#health-checks)
    * [Stop All Services](#stop-all-services)
  * [Monitoring (OpenTelemetry + SigNoz)](#monitoring-opentelemetry--signoz)
    * [Setup](#setup)
    * [Run with Monitoring](#run-with-monitoring)
    * [Custom Metrics](#custom-metrics)
    * [Span Attributes](#span-attributes)
    * [SigNoz Usage](#signoz-usage)
    * [Docker Commands](#docker-commands)
    * [Troubleshooting](#troubleshooting)
  * [Error Handling](#error-handling)
  * [TODO](#todo)
<!-- TOC -->

## Purpose

This project serves as a **Proof of Concept (POC) V1** to demonstrate the inherent complexity of building a distributed, asynchronous Channel Manager system *without* an orchestration engine.

The goal is to simulate a real-world scenario where:
- **Main App** (Source of Truth) receives order updates.
- **Connector App** batches these updates (logic often manually implemented).
- **Channel App** (External Service) behaves unreliably (latency, failures).

We are building this "hard way" first to highlight the challenges of:
- Managing distributed state (What if the connector crashes?).
- Handling complex batching logic manually.
- Tracking the status of an order across multiple async services.
- Dealing with partial failures (e.g., Price updated, Stock failed).

**V2** will implement **Temporal.io** to solve these problems elegantly.

## Overview

This project demonstrates:
- **Async request processing**: Receives client requests, immediately returns 202 Accepted, then processes asynchronously
- **Queue-based orchestration**: Jobs are queued and batch-processed every 10 seconds
- **Dual callback pattern**: Price and stock are validated separately, then combined before final callback
- **Distributed tracing**: Uses correlation IDs for request tracking and OpenTelemetry for observability

## Services

| Service | Port | Role |
|---------|------|------|
| **connect-app** | 8082 | Client-facing API entry point |
| **connector-app** | 8080 | Queue orchestrator, coordinates dual processing |
| **channel-app** | 8081 | Async processor for price/stock validation |
| **shared-model** | N/A | Common POJOs, exceptions, observability helpers |

## Project Structure

```
temporal-app/
├── connect-app/      # Client-facing API (port 8082)
├── connector-app/    # Queue orchestrator (port 8080)
├── channel-app/      # Price/stock validator (port 8081)
├── shared-model/     # Common POJOs and response records
├── README.md         # This file
└── AGENTS.md         # AI assistant coding guidelines
```

---

## Quick Start

### 1. Build

```bash
mvn clean package
```

### 2. Run Services (3 terminals)

```bash
# Terminal 1: channel-app (start first)
cd channel-app && mvn spring-boot:run

# Terminal 2: connector-app
cd connector-app && mvn spring-boot:run

# Terminal 3: connect-app
cd connect-app && mvn spring-boot:run
```

### 3. Test

```bash
# Send a request (orderId is required)
curl -i -X POST http://localhost:8082/priceAndStock \
  -H "Content-Type: application/json" \
  -d '{"orderId": "order-123", "price": 5000, "stock": 500}'

# Response:
# - Header: X-Correlation-Id: <uuid>
# - Body: {"orderId": "order-123", "status": "accepted"}

# Check status
curl http://localhost:8082/status/order-123
```

---

## Architecture

### Identifiers

| Identifier | Purpose | Location |
|------------|---------|----------|
| **orderId** | Business identifier (primary key in connect-app) | Request body |
| **correlationId** | Tracing identifier (UUID for distributed tracing) | `X-Correlation-Id` header |

### Async Flow

```
  Client              connect-app           connector-app          channel-app
    │                     │                      │                      │
    │ POST /priceAndStock │                      │                      │
    │ {orderId, price,    │                      │                      │
    │  stock}             │                      │                      │
    │────────────────────>│                      │                      │
    │                     │                      │                      │
    │                     │ POST /webhook        │                      │
    │                     │ Header: X-Correlation-Id                    │
    │                     │ Header: X-Callback-Url: /callback/{orderId} │
    │                     │ Body: {orderId, price, stock}               │
    │                     │─────────────────────>│                      │
    │                     │                      │                      │
    │                     │                      │ ┌──────────────────┐ │
    │                     │                      │ │ Enqueue:         │ │
    │                     │                      │ │ - priceQueue     │ │
    │                     │                      │ │ - stockQueue     │ │
    │                     │                      │ └──────────────────┘ │
    │                     │                      │                      │
    │                     │   202 Accepted       │                      │
    │  202 Accepted       │<─────────────────────│                      │
    │<────────────────────│                      │                      │
    │                     │                      │                      │
    
              ═══════════ ASYNC (every 10s) ═══════════
    
    │                     │                      │ @Scheduled           │
    │                     │                      │ drain queues         │
    │                     │                      │                      │
    │                     │                      │ POST /price, /stock  │
    │                     │                      │─────────────────────>│
    │                     │                      │     202 Accepted     │
    │                     │                      │<─────────────────────│
    │                     │                      │                      │
    
              ═══════════ CALLBACKS (1-3s later) ═══════════
    
    │                     │                      │                      │ @Async
    │                     │                      │ POST /callback?type= │ process
    │                     │                      │<─────────────────────│
    │                     │                      │                      │
    │                     │                      │ Both received?       │
    │                     │                      │ → Combine result     │
    │                     │                      │                      │
    │                     │ POST /callback/{orderId}                    │
    │                     │<─────────────────────│                      │
```

### State Transitions

```
queued → processing_price → processing_stock → completed
   │                                              
   └─────────────────────────────────────────→ failed (on error)
```

| Status | Trigger |
|--------|---------|
| `queued` | Request received, job in queue |
| `processing_price` | Price sent to channel |
| `processing_stock` | Stock sent to channel |
| `completed` | Both callbacks received |
| `failed` | Error or queue full |

### Key Design Patterns

1. **Queue-Based Processing**: Decouples intake from processing, handles traffic spikes (max 1000 jobs/queue), batch processing every 10 seconds
2. **Dual Identifiers**: orderId (business) + correlationId (tracing)
3. **Dual Callbacks**: Price and stock processed in parallel, connector waits for both then combines
4. **POJO Responses**: All services return typed POJOs (`PriceAndStockResponse`, `ChannelResponse`, `CallbackReceivedResponse`)

---

## API Endpoints

### connect-app (8082)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/priceAndStock` | Submit new request (body: `{orderId, price, stock}`) |
| GET | `/status` | Get all statuses |
| GET | `/status/{orderId}` | Get specific status by orderId |
| POST | `/callback/{orderId}` | Receive final callback |
| POST | `/burst/{count}` | Load test: send N requests in parallel |
| POST | `/background-load/activate` | Start continuous load (1 req/sec) |
| POST | `/background-load/deactivate` | Stop continuous load |

### connector-app (8080)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/webhook/priceAndStock` | Receive from connect |
| GET | `/status` | Get all statuses |
| GET | `/status/{orderId}` | Get specific status by orderId |
| POST | `/callback/{orderId}` | Receive channel callbacks |

### channel-app (8081)

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/price` | Validate price async |
| POST | `/stock` | Validate stock async |

**Headers:** `X-Correlation-Id` (tracing), `X-Callback-Url` (where to send result)

---

## Commands Reference

### Build

```bash
mvn clean compile           # Build all
mvn clean compile -pl connector-app  # Build single module
mvn clean package           # Package all (creates JARs)
mvn test                    # Run tests
```

### Run (Development)

```bash
cd channel-app && mvn spring-boot:run
cd connector-app && mvn spring-boot:run
cd connect-app && mvn spring-boot:run
```

### Run (Production JAR)

```bash
cd channel-app && java -jar target/channel-app-0.0.1-SNAPSHOT.jar
cd connector-app && java -jar target/connector-app-0.0.1-SNAPSHOT.jar
cd connect-app && java -jar target/connect-app-0.0.1-SNAPSHOT.jar
```

### Testing

```bash
# Single request
curl -X POST http://localhost:8082/priceAndStock \
  -H "Content-Type: application/json" \
  -d '{"orderId": "order-1", "price": 5000, "stock": 500}'

# Check status
curl http://localhost:8082/status | jq
curl http://localhost:8082/status/order-1 | jq

# Multiple requests
for i in {1..10}; do
  curl -s -X POST http://localhost:8082/priceAndStock \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"order-$i\", \"price\":$((1000 * i)), \"stock\":$((100 * i))}"
  echo ""
done

# Burst test (server-side)
curl -X POST http://localhost:8082/burst/50

# Background load
curl -X POST http://localhost:8082/background-load/activate
curl -X POST http://localhost:8082/background-load/deactivate
```

### Integration Tests

```bash
./test_connect_state_management.sh
./test_new_dual_processing.sh
```

### Health Checks

```bash
for port in 8080 8081 8082; do
  status=$(curl -s -o /dev/null -w '%{http_code}' http://localhost:$port/actuator/health 2>/dev/null || echo "DOWN")
  echo "Port $port: $status"
done
```

### Stop All Services

```bash
pkill -f "connector-app\|connect-app\|channel-app"
```

---

## Monitoring (OpenTelemetry + SigNoz)

```
Applications → OpenTelemetry Java Agent → SigNoz Collector (4317) → SigNoz UI (8085)
```

**Monitored:** connector-app, connect-app  
**Not monitored:** channel-app (black box)

### Setup

```bash
# Start SigNoz
docker compose up -d

# Access UI: http://localhost:8085
# Login: admin@signoz.local / Admin@123
```

### Run with Monitoring

```bash
# connector-app with OpenTelemetry
cd connector-app && \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
OTEL_EXPORTER_OTLP_PROTOCOL=grpc \
OTEL_LOGS_EXPORTER=otlp \
OTEL_METRICS_EXPORTER=otlp \
OTEL_TRACES_EXPORTER=otlp \
OTEL_SERVICE_NAME=connector-app \
OTEL_METRIC_EXPORT_INTERVAL=5000 \
java -javaagent:target/opentelemetry-javaagent.jar \
  -jar target/connector-app-0.0.1-SNAPSHOT.jar

# connect-app with OpenTelemetry
cd connect-app && \
OTEL_EXPORTER_OTLP_ENDPOINT=http://localhost:4317 \
OTEL_EXPORTER_OTLP_PROTOCOL=grpc \
OTEL_LOGS_EXPORTER=otlp \
OTEL_METRICS_EXPORTER=otlp \
OTEL_TRACES_EXPORTER=otlp \
OTEL_SERVICE_NAME=connect-app \
OTEL_METRIC_EXPORT_INTERVAL=5000 \
java -javaagent:target/opentelemetry-javaagent.jar \
  -jar target/connect-app-0.0.1-SNAPSHOT.jar

# channel-app (no monitoring)
cd channel-app && java -jar target/channel-app-0.0.1-SNAPSHOT.jar
```

### Custom Metrics

| Metric | Type | Description |
|--------|------|-------------|
| `process.requests.received` | Counter | Total requests received |
| `process.requests.completed` | Counter | Successfully completed |
| `process.requests.failed` | Counter | Failed requests |
| `process.callbacks.sent` | Counter | Callbacks sent upstream |
| `process.callbacks.received` | Counter | Callbacks received |
| `process.queue.size` | Gauge | Current queue size |
| `process.callback.latency` | Histogram | End-to-end latency (ms) |

### Span Attributes

| Attribute | Description |
|-----------|-------------|
| `order.id` | Business order identifier |
| `correlation.id` | Distributed tracing ID |
| `process.status` | Current process status |
| `callback.type` | price, stock, or combined |

### SigNoz Usage

- **Traces**: Filter by `order.id = your-order-id` to see full request flow
- **Metrics**: Search `process.requests.received`, group by `service`

### Docker Commands

```bash
docker compose up -d      # Start SigNoz
docker compose down       # Stop SigNoz
docker compose down -v    # Stop and clear all data
docker compose logs -f signoz  # View logs
```

### Troubleshooting

- **No traces?** Check `docker compose ps` and look for `[otel.javaagent]` in app logs
- **Services not showing?** Wait 30-60s after first request
- **Port conflicts?** `lsof -ti:4317 | xargs kill -9`

---

## UI

```
                                Visual Flow Diagram 
┌──────────────────────────────────────────────────────────────────────────┐
│                              FRONTEND                                    │
│                          (Next.js / React)                               │
│                                                                          │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                    fetchAllDashboardData()                         │  │
│  │                                                                    │  │
│  │   ┌──────────┐   ┌──────────┐   ┌──────────────────────────────┐   │  │
│  │   │ main-app │   │connector │   │ channel-app (per order)      │   │  │
│  │   │ /status  │   │ /status  │   │ /orders/{id} x N orders      │   │  │
│  │   └────┬─────┘   └────┬─────┘   └──────────────┬───────────────┘   │  │
│  │        │              │                        │                   │  │
│  │        └──────────────┼────────────────────────┘                   │  │
│  │                       │                                            │  │
│  │                       v                                            │  │
│  │            ┌─────────────────────┐                                 │  │
│  │            │   Merge by orderId  │  ← AGGREGATION HAPPENS HERE     │  │
│  │            │  (in StatusTable)   │                                 │  │
│  │            └──────────┬──────────┘                                 │  │
│  └───────────────────────│────────────────────────────────────────────┘  │
│                          v                                               │
│  ┌────────────────────────────────────────────────────────────────────┐  │
│  │                      DataTable Display                             │  │
│  │  ┌─────────┬────────────┬────────────┬─────────────┬─────────────┐ │  │
│  │  │Order ID │ Main App   │ Connector  │Channel Price│Channel Stock│ │  │
│  │  │         │ (8082)     │ (8080)     │ (8081)      │ (8081)      │ │  │
│  │  ├─────────┼────────────┼────────────┼─────────────┼─────────────┤ │  │
│  │  │order-1  │ COMPLETED  │ QUEUED     │ $100 ✓      │ 50 ✓        │ │  │
│  │  │order-2  │ PROCESSING │ PROCESSING │ $75 pending │ 30 pending  │ │  │
│  │  └─────────┴────────────┴────────────┴─────────────┴─────────────┘ │  │
│  └────────────────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────────────┘
BACKEND
┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
│    main-app      │  │  connector-app   │  │   channel-app    │
│    (8082)        │  │     (8080)       │  │     (8081)       │
│                  │  │                  │  │                  │
│ RequestState:    │  │ RequestState:    │  │ ChannelOrderState│
│ - orderId        │  │ - orderId        │  │ - price          │
│ - status         │  │ - status         │  │ - stock          │
│ - originalRequest│  │ - priceCallback  │  │ - priceStatus    │
│ - finalResponse  │  │ - stockCallback  │  │ - stockStatus    │
│ - eventHistory   │  │ - eventHistory   │  │ - lastUpdatedAt  │
│                  │  │                  │  │                  │
│                  │  │                  │  │                  │
│     Database     │  │     Database     │  │     In-memory    │
└──────────────────┘  └──────────────────┘  └──────────────────┘
```
Key Observations
1. No centralized backend aggregation - Each service maintains its own state independently
2. Frontend is the aggregator - The frontend merges data from all 3 services using orderId as the join key
3. N+2 requests per refresh - 2 paginated status calls + N individual channel calls (one per order)
4. Polling-based updates - Frontend polls every 1 second when enabled

## Error Handling

| Scenario | Response |
|----------|----------|
| Queue full | 503, state=failed |
| Channel unreachable | Error callback to connect |
| Invalid price/stock | priceOk/stockOk=false in callback |

---

## TODO

- [x] Change usage of correlationId and OrderId
- [x] Add monitoring to follow processes and orders
- [ ] better filtering on UI -> separate main and connector
- [ ] The main queue is not well registered with background load
- [ ] Explain clearly the issue with the current workflow
- [ ] Add temporal implementation
