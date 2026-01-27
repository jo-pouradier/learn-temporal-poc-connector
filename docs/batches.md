# Temporal Batch Processing Guide

## Table of Contents
1. [Overview](#overview)
2. [Batch Processing Patterns](#batch-processing-patterns)
3. [Current Architecture](#current-architecture)
4. [Batch Configuration](#batch-configuration)
5. [Error Handling](#error-handling)
6. [Advanced Temporal Patterns](#advanced-temporal-patterns)
7. [Monitoring & Observability](#monitoring--observability)
8. [Performance Optimization](#performance-optimization)
9. [Testing Strategy](#testing-strategy)
10. [Future Enhancements](#future-enhancements)
11. [Comparison: Activity vs Workflow Batching](#comparison-activity-vs-workflow-batching)
12. [Reference & Examples](#reference--examples)
13. [Troubleshooting](#troubleshooting)
14. [Related Documentation](#related-documentation)

---

## Overview

### What is Batch Processing?

Batch processing is a design pattern where multiple items are collected and processed together as a group (batch) rather than individually. In Temporal, batch processing combines the orchestration power of workflows with the efficiency of grouped operations.

**Key Benefits:**
- **Throughput**: Process 100s-1000s of items per second vs dozens individually
- **Efficiency**: Amortize overhead (network, DB connections) across multiple items
- **Cost**: Reduce API calls, database transactions, and activity invocations
- **Simplicity**: Single workflow execution tracks entire batch

**When to Use Batch Processing:**
- High-volume queue processing (e.g., order updates, data sync)
- Rate-limited external APIs (batch requests save quota)
- Database operations (bulk inserts/updates)
- Event aggregation and analytics
- Message fanout scenarios

**When NOT to Use Batch Processing:**
- Low latency requirements (< 1 second response time)
- Items require independent retry logic
- Processing time varies significantly per item
- Strong ordering guarantees needed per item

---

## Batch Processing Patterns

### Pattern 1: Activity-Based Batching (Current Implementation)

**Architecture:**
```
Workflow (Orchestrator)
    ↓
Dequeue Activity → Returns List<Job>
    ↓
Process Batch Activity → Iterates and processes each item
```

**Code Structure:**
```java
// Workflow
public void processPriceBatch() {
    var batch = dequeuePricesActivities.DequeuePrices();  // Activity 1
    processPriceBatchActivities.processPriceBatch(batch);  // Activity 2
}
```

**Characteristics:**
- ✅ **Simple**: Minimal workflow code
- ✅ **Efficient**: Single activity invocation for entire batch
- ✅ **Transactional**: Dequeue and process in separate activities
- ❌ **All-or-nothing**: Entire batch retried on failure
- ❌ **Limited parallelism**: Sequential processing per batch
- ❌ **Fixed batch size**: Hardcoded in activities

**Best For:**
- Homogeneous workloads (similar processing time per item)
- Non-critical item-level failures
- High-throughput scenarios (1000+ items/sec)

---

### Pattern 2: Workflow-Based Batching (Child Workflows)

**Architecture:**
```
Parent Workflow (Batch Coordinator)
    ↓
Dequeue Activity → Returns List<Job>
    ↓
For each job: Start Child Workflow (parallel)
    ↓
Wait for all Child Workflows to complete
    ↓
Aggregate Results
```

**Code Example:**
```java
@Override
public BatchResult processPriceBatch() {
    // Dequeue batch
    var batch = dequeuePricesActivities.DequeuePrices();
    
    // Start child workflow for each item (with parallelism control)
    List<Promise<ProcessResult>> promises = new ArrayList<>();
    for (PriceJob job : batch) {
        ChildWorkflowOptions options = ChildWorkflowOptions.newBuilder()
            .setWorkflowId("price-" + job.orderId())
            .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_TERMINATE)
            .build();
            
        ProcessItemWorkflow child = Workflow.newChildWorkflowStub(
            ProcessItemWorkflow.class, options);
        promises.add(Async.function(child::processItem, job));
    }
    
    // Wait for all and collect results
    List<ProcessResult> results = new ArrayList<>();
    for (Promise<ProcessResult> promise : promises) {
        results.add(promise.get());
    }
    
    return aggregateResults(results);
}
```

**Characteristics:**
- ✅ **Parallel execution**: Multiple items processed concurrently
- ✅ **Independent retries**: Each item has own retry policy
- ✅ **Granular observability**: See individual item progress in UI
- ✅ **Flexible**: Mix of success/failure handled gracefully
- ❌ **Higher overhead**: One workflow execution per item
- ❌ **Complexity**: More code to manage coordination
- ❌ **History size**: Can grow large with many items

**Best For:**
- Heterogeneous workloads (variable processing time)
- Critical operations requiring item-level tracking
- Moderate batch sizes (10-100 items)
- Complex business logic per item

---

### Pattern 3: Hybrid Approach

**Architecture:**
```
Workflow (Orchestrator)
    ↓
Dequeue Activity → Returns List<Job> (e.g., 100 items)
    ↓
Split into sub-batches (e.g., 10 sub-batches of 10 items)
    ↓
For each sub-batch: Start Child Workflow (parallel)
    ↓
Each Child Workflow: Process Batch Activity
    ↓
Aggregate Results
```

**Characteristics:**
- ✅ **Balanced**: Combines efficiency of batching with parallelism of child workflows
- ✅ **Scalable**: Process 1000s of items efficiently
- ✅ **Configurable**: Tune sub-batch size and parallelism
- ⚠️ **Complex**: Requires careful tuning and monitoring

**Best For:**
- Very high throughput (10,000+ items/sec)
- Mixed workload characteristics
- Need both speed and failure isolation

---

## Current Architecture

### Overview

Your application uses **Activity-Based Batching** for processing price and stock updates from queues stored in PostgreSQL.

**Key Components:**
```
┌─────────────────────────────────────────────────────────────┐
│                     Temporal Schedule                        │
│           (Triggers every 5 seconds by default)              │
└────────────────────────┬────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│         SendPriceToChannelWorkflow (Orchestrator)            │
│                                                              │
│  1. Call DequeuePricesActivities.DequeuePrices()            │
│     → Returns List<PriceJob> (max 10 items)                 │
│                                                              │
│  2. Call ProcessPriceBatchActivities.processPriceBatch()     │
│     → Sends each job to channel via HTTP                     │
└─────────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────────┐
│                  PostgreSQL Queue Tables                     │
│                                                              │
│  connector_price_queue:                                      │
│    - order_id (PK)                                           │
│    - correlation_id                                          │
│    - price                                                   │
│    - queued_at (for FIFO ordering)                          │
│                                                              │
│  connector_stock_queue: (same structure)                     │
└─────────────────────────────────────────────────────────────┘
```

---

### Price Queue Processing Flow

#### Step 1: Workflow Trigger

**File:** `SendPriceToChannelWorkflowImpl.java`

```java
@WorkflowImpl(taskQueues = "send-price-to-channel")
public class SendPriceToChannelWorkflowImpl implements SendPriceToChannelWorkflow {

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))  // Max 1 minute per activity
            .build();

    private final ProcessPriceBatchActivities processPriceBatchActivities =
            Workflow.newActivityStub(ProcessPriceBatchActivities.class, ACTIVITY_OPTIONS);

    private final DequeuePricesActivities dequeuePricesActivities =
            Workflow.newActivityStub(DequeuePricesActivities.class, ACTIVITY_OPTIONS);

    @Override
    public void processPriceBatch() {
        // 1. Dequeue batch from PostgreSQL
        var batch = dequeuePricesActivities.DequeuePrices();
        
        // 2. Process batch (send to channel)
        processPriceBatchActivities.processPriceBatch(batch);
    }
}
```

**Key Points:**
- Workflow is **stateless** - no internal state between activities
- **1-minute timeout** per activity (can process 10 items in < 1 minute)
- **Sequential execution** - dequeue then process
- No error handling at workflow level (relies on activity retries)

---

#### Step 2: Dequeue Activity

**File:** `DequeuePricesActivitiesImpl.java`

```java
@ActivityImpl
@Service
public class DequeuePricesActivitiesImpl implements DequeuePricesActivities {
    private PriceQueueRepository priceQueueRepository;
    private static final int BATCH_LIMIT = 10;  // ⚠️ Hardcoded

    public DequeuePricesActivitiesImpl(PriceQueueRepository priceQueueRepository) {
        this.priceQueueRepository = priceQueueRepository;
    }

    @Override
    public List<PriceQueueRepository.PriceJob> DequeuePrices() {
        return priceQueueRepository.dequeue(BATCH_LIMIT);
    }
}
```

**Repository Implementation:**

**File:** `PriceQueueRepository.java` (excerpt)

```java
public List<PriceJob> dequeue(int limit) {
    LOG.debug("Dequeuing up to {} price jobs", limit);
    
    // 1. Select oldest entries (FIFO)
    List<PriceJob> jobs = dsl.select(
            CONNECTOR_PRICE_QUEUE.ORDER_ID,
            CONNECTOR_PRICE_QUEUE.CORRELATION_ID,
            CONNECTOR_PRICE_QUEUE.PRICE)
        .from(CONNECTOR_PRICE_QUEUE)
        .orderBy(CONNECTOR_PRICE_QUEUE.QUEUED_AT)  // FIFO ordering
        .limit(limit)
        .fetch(this::toPriceJob);

    // 2. Delete dequeued items (in same transaction)
    if (!jobs.isEmpty()) {
        List<String> orderIds = jobs.stream().map(PriceJob::orderId).toList();
        dsl.deleteFrom(CONNECTOR_PRICE_QUEUE)
            .where(CONNECTOR_PRICE_QUEUE.ORDER_ID.in(orderIds))
            .execute();
        
        LOG.debug("Dequeued {} price jobs", jobs.size());
    }
    
    return jobs;
}
```

**Key Points:**
- **Atomic operation**: Select + Delete in single transaction
- **FIFO ordering**: `ORDER BY queued_at` ensures oldest first
- **Idempotency**: Uses `order_id` as primary key (duplicates ignored)
- **Returns empty list** if queue is empty (not an error)

---

#### Step 3: Process Batch Activity

**File:** `ProcessPriceBatchActivitiesImpl.java`

```java
@ActivityImpl
@Service
public class ProcessPriceBatchActivitiesImpl implements ProcessPriceBatchActivities {
    private static final Logger LOG = LoggerFactory.getLogger(ProcessPriceBatchActivitiesImpl.class);
    private static final String PRICE_QUEUE = "connector_price";

    private final RequestService requestService;
    private final ConnectorProperties properties;

    @Override
    public void processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
        for (PriceQueueRepository.PriceJob job : batch) {
            LOG.info("Sending price to channel: orderId={}, correlationId={}, price={}",
                    job.orderId(), job.correlationId(), job.price());
            
            // Send HTTP request to channel
            requestService.processJob(
                job.orderId(), 
                job.correlationId(), 
                "price", 
                properties.getChannelPriceUrl(),  // http://localhost:8081/price
                new PriceRequest(job.orderId(), job.price())
            );
        }
    }
}
```

**Key Points:**
- **Sequential processing**: Items processed one-by-one in `for` loop
- **Synchronous HTTP**: Blocks until channel responds (or timeout)
- **Individual error handling**: Each item failure logged, state updated
- **No early exit**: Continues processing remaining items even if one fails
- **Side effects**: Updates database state, sends callbacks

---

### Stock Queue Processing

Stock queue follows **identical pattern** to price queue:

**Files:**
- `SendStockToChannelWorkflowImpl.java` (same structure)
- `DequeueStocksActivitiesImpl.java` (same logic, different repo)
- `ProcessStockBatchActivitiesImpl.java` (same logic, different URL)

**Key Difference:**
- Different task queue: `send-stock-to-channel`
- Different endpoint: `http://localhost:8081/stock`
- Different state: `OrderStatus.PROCESSING_STOCK`

---

### Main-App Batching (Upstream)

**File:** `main-app/src/main/java/com/example/main/service/RequestService.java`

```java
@Scheduled(fixedRate = 1000)  // Every 1 second
public void flushBatch() {
    int queueSize = queueRepository.size();
    if (queueSize == 0) return;

    // Dequeue up to 10 order IDs
    List<String> orderIds = queueRepository.dequeue(10);  // Same batch size!
    if (orderIds.isEmpty()) return;
    
    // Record dequeue metrics
    queueMetrics.recordDequeue(BATCH_QUEUE, orderIds.size());
    
    // Load states from DB
    List<RequestState> batch = orderIds.stream()
        .map(orderId -> stateRepository.findByOrderId(orderId).orElse(null))
        .filter(state -> state != null)
        .toList();
    
    if (batch.isEmpty()) return;
    
    LOG.info("Flushing batch of {} requests", batch.size());
    
    try {
        // Send batch to connector via HTTP
        List<PriceAndStockRequest> payloads = batch.stream()
            .map(RequestState::getOriginalRequest)
            .toList();
            
        ResponseEntity<Map> response = restClient.post()
            .uri("/webhook/priceAndStock/batch")  // Batch endpoint!
            .contentType(MediaType.APPLICATION_JSON)
            .body(payloads)
            .retrieve()
            .toEntity(Map.class);
            
        if (response.getStatusCode().is2xxSuccessful()) {
             batch.forEach(state -> {
                 state.setStatus(OrderStatus.SENT_TO_CONNECTOR);
                 stateRepository.save(state);
                 
                 StateEvent event = new StateEvent(EventType.BATCH_SENT, 
                         OrderStatus.SENT_TO_CONNECTOR, 
                         "Batch of " + batch.size() + " sent to connector");
                 eventRepository.save(state.getOrderId(), event);
             });
             LOG.info("Batch sent successfully");
        } else {
             handleBatchFailure(batch, "Connector rejected batch: " + response.getStatusCode());
        }
    } catch (Exception e) {
        handleBatchFailure(batch, "Batch send failed: " + e.getMessage());
        LOG.error("Batch send failed", e);
    }
}
```

**Key Points:**
- **Spring @Scheduled**: Not using Temporal (yet - see schedule.md for migration)
- **Batch HTTP endpoint**: Sends all 10 items in single request
- **All-or-nothing**: Entire batch succeeds or fails together
- **Same batch size**: 10 items (matches connector batch size)

---

### Architecture Diagram: Complete Flow

```
┌──────────────┐
│   Client     │
│  (HTTP POST) │
└──────┬───────┘
       │
       ↓
┌──────────────────────────────────────────────────────────────┐
│                      Main-App (8082)                          │
│                                                               │
│  1. Accept request → Save to main_batch_queue                │
│  2. @Scheduled(1s) → Dequeue 10 items                        │
│  3. POST /webhook/priceAndStock/batch (10 items in body)     │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ↓ HTTP Batch Request
┌──────────────────────────────────────────────────────────────┐
│                   Connector-App (8080)                        │
│                                                               │
│  4. POST /webhook/priceAndStock/batch endpoint               │
│     → Split batch: enqueue each to price & stock queues      │
│                                                               │
│  5. Temporal Schedule triggers (every 5s):                    │
│     • SendPriceToChannelWorkflow                             │
│     • SendStockToChannelWorkflow                             │
│                                                               │
│  6. Each workflow:                                            │
│     ┌─────────────────────────────────────┐                  │
│     │ DequeuePricesActivity               │                  │
│     │   → SELECT ... LIMIT 10             │                  │
│     │   → DELETE dequeued items           │                  │
│     │   → Return List<PriceJob>          │                  │
│     └────────────┬────────────────────────┘                  │
│                  │                                            │
│     ┌────────────▼────────────────────────┐                  │
│     │ ProcessPriceBatchActivity           │                  │
│     │   → for (job : batch)               │                  │
│     │       POST /price (individual)      │                  │
│     └─────────────────────────────────────┘                  │
└────────────────────────┬─────────────────────────────────────┘
                         │ 
                         ↓ HTTP (10 individual requests)
┌──────────────────────────────────────────────────────────────┐
│                    Channel-App (8081)                         │
│                                                               │
│  7. POST /price or /stock (individual items)                  │
│     → Process async (1-3 seconds)                            │
│     → POST /callback (back to connector)                      │
└──────────────────────────────────────────────────────────────┘
```

**Batch Sizes:**
- Main → Connector: **10 items** (HTTP batch)
- Connector dequeue: **10 items** (from DB)
- Connector → Channel: **10 individual HTTP requests** (not batched)

**Observation:**
The channel receives **individual requests**, not batches. This is by design since channel callbacks are per-item.

---

## Batch Configuration

### Current Configuration

**Batch Size:**
```java
// connector-app: DequeuePricesActivitiesImpl.java
private static final int BATCH_LIMIT = 10;  // ⚠️ Hardcoded

// main-app: RequestService.java
List<String> orderIds = queueRepository.dequeue(10);  // ⚠️ Hardcoded
```

**Activity Timeouts:**
```java
// SendPriceToChannelWorkflowImpl.java
private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(1))  // Max 1 minute
        .build();
```

**Queue Limits:**
```yaml
# connector-app/src/main/resources/application.yaml
connector:
  queue-size: 10000  # Max items per queue
  queue-processing-interval: 5s  # Schedule trigger frequency
```

**Schedule Interval:**
- **Current**: 5 seconds (from `application.yaml`)
- **Effect**: Workflows triggered every 5 seconds, dequeue up to 10 items each
- **Max throughput**: ~2 items/sec per queue (10 items / 5 seconds)

---

### Recommended: Configurable Batch Size

#### Step 1: Add Property to ConnectorProperties

**File:** `connector-app/src/main/java/com/example/temporal/config/ConnectorProperties.java`

```java
@Component
@ConfigurationProperties(prefix = "connector")
public class ConnectorProperties {
    // ... existing properties ...
    
    private int batchSize = 10;  // Default to current value

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        if (batchSize < 1 || batchSize > 1000) {
            throw new IllegalArgumentException("Batch size must be between 1 and 1000");
        }
        this.batchSize = batchSize;
    }
}
```

#### Step 2: Update application.yaml

```yaml
connector:
  queue-size: 10000
  queue-processing-interval: 5s
  batch-size: 10  # Add this (configurable per environment)
```

#### Step 3: Inject into Activity Implementations

```java
@ActivityImpl
@Service
public class DequeuePricesActivitiesImpl implements DequeuePricesActivities {
    private final PriceQueueRepository priceQueueRepository;
    private final ConnectorProperties properties;  // Inject

    public DequeuePricesActivitiesImpl(PriceQueueRepository priceQueueRepository, 
                                       ConnectorProperties properties) {
        this.priceQueueRepository = priceQueueRepository;
        this.properties = properties;
    }

    @Override
    public List<PriceQueueRepository.PriceJob> DequeuePrices() {
        int batchSize = properties.getBatchSize();  // Use config
        LOG.debug("Dequeuing up to {} price jobs", batchSize);
        return priceQueueRepository.dequeue(batchSize);
    }
}
```

**Benefits:**
- ✅ Tune batch size per environment (dev: 5, prod: 50)
- ✅ A/B test different batch sizes
- ✅ No code changes to adjust throughput
- ✅ Hot reload via Spring DevTools or restart

---

### Calculating Activity Timeouts

**Formula:**
```
StartToCloseTimeout = (AvgProcessingTimePerItem × MaxBatchSize) + BufferTime
```

**Example:**
- Avg processing time per item: 2 seconds (HTTP to channel)
- Max batch size: 50 items
- Buffer time: 30 seconds (for retries, network latency)
- **Timeout = (2s × 50) + 30s = 130 seconds = ~2.5 minutes**

**Updated Configuration:**
```java
private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofMinutes(3))  // Increased from 1 minute
        .setRetryOptions(RetryOptions.newBuilder()
            .setMaximumAttempts(3)  // Retry up to 3 times
            .setBackoffCoefficient(2.0)  // Exponential backoff
            .build())
        .build();
```

**Important:**
- **StartToCloseTimeout** = Total time allowed for activity execution (including retries within activity)
- **ScheduleToCloseTimeout** = Total time including time in task queue (usually not needed)
- **ScheduleToStartTimeout** = Max time in queue before picked up by worker
- **HeartbeatTimeout** = For long-running activities (not needed for batch processing)

---

### Retry Policies

#### Activity-Level Retries (Recommended)

```java
ActivityOptions activityOptions = ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(3))
    .setRetryOptions(RetryOptions.newBuilder()
        .setMaximumAttempts(3)  // Retry entire batch up to 3 times
        .setInitialInterval(Duration.ofSeconds(5))  // First retry after 5s
        .setBackoffCoefficient(2.0)  // Double delay each retry (5s, 10s, 20s)
        .setMaximumInterval(Duration.ofMinutes(1))  // Cap at 1 minute
        .setDoNotRetry(IllegalArgumentException.class.getName())  // Don't retry validation errors
        .build())
    .build();
```

**When Activity Retries:**
- Activity throws exception
- Activity times out (exceeds `StartToCloseTimeout`)
- Worker crashes mid-activity (Temporal auto-retries)

**Pros:**
- ✅ Simple - let Temporal handle retries
- ✅ Automatic - no custom code
- ✅ Visible - see retry attempts in Temporal UI

**Cons:**
- ❌ All-or-nothing - entire batch retried
- ❌ Wasted work - re-processes successful items
- ❌ State inconsistency - if some items partially processed

---

#### Item-Level Retry Logic (Future Enhancement)

```java
@Override
public BatchResult processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    List<String> succeeded = new ArrayList<>();
    List<String> failed = new ArrayList<>();
    Map<String, String> errors = new HashMap<>();
    
    for (PriceQueueRepository.PriceJob job : batch) {
        int retries = 0;
        boolean success = false;
        
        while (retries < 3 && !success) {
            try {
                LOG.info("Processing job: orderId={}, attempt={}", job.orderId(), retries + 1);
                requestService.processJob(job.orderId(), job.correlationId(), "price", 
                                          properties.getChannelPriceUrl(), 
                                          new PriceRequest(job.orderId(), job.price()));
                succeeded.add(job.orderId());
                success = true;
            } catch (Exception e) {
                retries++;
                LOG.warn("Failed job: orderId={}, attempt={}, error={}", 
                         job.orderId(), retries, e.getMessage());
                
                if (retries >= 3) {
                    failed.add(job.orderId());
                    errors.put(job.orderId(), e.getMessage());
                } else {
                    // Exponential backoff: 1s, 2s, 4s
                    Workflow.sleep(Duration.ofSeconds((long) Math.pow(2, retries - 1)));
                }
            }
        }
    }
    
    LOG.info("Batch processing complete: succeeded={}, failed={}", 
             succeeded.size(), failed.size());
    
    return new BatchResult(succeeded, failed, errors);
}

// Return type
public record BatchResult(List<String> succeeded, List<String> failed, Map<String, String> errors) {}
```

**Pros:**
- ✅ Granular - only failed items retried
- ✅ Partial success - some items succeed even if others fail
- ✅ Better observability - track per-item status

**Cons:**
- ⚠️ More complex code
- ⚠️ Longer activity execution time
- ⚠️ Need to handle partial results in workflow

---

## Error Handling

### Error Scenarios

#### 1. Dequeue Activity Failure

**Causes:**
- Database connection lost
- SQL query timeout
- PostgreSQL unavailable

**Current Behavior:**
- Activity throws exception
- Temporal retries entire activity (if retry policy configured)
- If max retries exceeded: workflow fails

**Impact:**
- ❌ No items dequeued this cycle
- ✅ Items remain in queue (not lost)
- ⚠️ Queue backlog grows if persistent

**Recommended Handling:**
```java
@Override
public List<PriceQueueRepository.PriceJob> DequeuePrices() {
    try {
        int batchSize = properties.getBatchSize();
        return priceQueueRepository.dequeue(batchSize);
    } catch (Exception e) {
        LOG.error("Failed to dequeue prices: {}", e.getMessage(), e);
        
        // Option 1: Return empty list (workflow continues, no items processed)
        return List.of();
        
        // Option 2: Throw exception (Temporal retries activity)
        // throw new RuntimeException("Dequeue failed", e);
    }
}
```

**Trade-offs:**
- **Return empty list**: Workflow succeeds, schedule continues, next trigger retries
- **Throw exception**: Workflow fails, visible in Temporal UI, requires manual intervention

---

#### 2. Process Batch Activity Failure

**Causes:**
- Channel-app unavailable (connection refused)
- Channel-app timeout (> 1 minute)
- Channel-app returns 5xx error
- Network partition

**Current Behavior:**
```java
// In ProcessPriceBatchActivitiesImpl
for (PriceQueueRepository.PriceJob job : batch) {
    // ⚠️ If processJob() throws exception:
    // - Loop exits immediately
    // - Remaining items not processed
    // - Activity fails
    // - Temporal retries entire activity
    requestService.processJob(...);
}
```

**Impact:**
- ❌ **Items already processed in this batch are RE-PROCESSED on retry**
- ❌ Duplicate HTTP requests to channel
- ⚠️ Queue items already deleted (not put back)

**Problem:**
If batch = [job1, job2, job3] and job2 fails:
1. job1 sent to channel ✅
2. job2 fails ❌ → Activity throws exception
3. job3 not processed ⏭️
4. Temporal retries activity
5. Batch already dequeued (items gone from DB)
6. Retry re-fetches NEW batch (job4, job5, job6)
7. **job3 is LOST** ❌❌❌

---

**Solution 1: Separate Dequeue and Process**

Current approach already does this! Dequeue and Process are **separate activities**.

**Why it works:**
- Dequeue activity completes successfully → items removed from DB
- Process activity fails → only processing retried, not dequeue
- Temporal replay ensures same batch processed on retry

**Temporal Replay Mechanism:**
```
Workflow Execution:
  ├─ DequeuePricesActivity → Returns [job1, job2, job3]
  │  (Completed, recorded in history)
  │
  └─ ProcessPriceBatchActivity → Receives [job1, job2, job3]
     (Failed, will retry)

On Retry:
  ├─ DequeuePricesActivity → REPLAYED from history (no re-execution)
  │  Returns same [job1, job2, job3]
  │
  └─ ProcessPriceBatchActivity → Retries with same [job1, job2, job3]
     (Fresh execution attempt)
```

**Key Insight:**
Temporal **deterministic replay** ensures activities return same result on retry. Dequeue activity is NOT re-executed; its result is replayed from workflow history.

**However:** This causes duplicate processing if some items succeeded before failure!

---

**Solution 2: Graceful Degradation (Current)**

```java
@Override
public void processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    for (PriceQueueRepository.PriceJob job : batch) {
        try {
            LOG.info("Sending price to channel: orderId={}", job.orderId());
            requestService.processJob(job.orderId(), job.correlationId(), "price", 
                                      properties.getChannelPriceUrl(), 
                                      new PriceRequest(job.orderId(), job.price()));
        } catch (Exception e) {
            // ⚠️ Log error but CONTINUE processing remaining items
            LOG.error("Failed to process job: orderId={}, error={}", job.orderId(), e.getMessage());
            // Could track failed items and return them
        }
    }
    // Activity succeeds even if some items failed
}
```

**Pros:**
- ✅ Partial success - some items processed
- ✅ Activity always succeeds (no retries)
- ✅ Queue continues draining

**Cons:**
- ❌ Failed items lost (not retried)
- ❌ No visibility into failures unless checking logs

---

**Solution 3: Dead Letter Queue (Future Enhancement)**

```java
@Override
public BatchResult processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    List<String> succeeded = new ArrayList<>();
    List<PriceQueueRepository.PriceJob> failed = new ArrayList<>();
    
    for (PriceQueueRepository.PriceJob job : batch) {
        try {
            requestService.processJob(job.orderId(), job.correlationId(), "price", 
                                      properties.getChannelPriceUrl(), 
                                      new PriceRequest(job.orderId(), job.price()));
            succeeded.add(job.orderId());
        } catch (Exception e) {
            LOG.error("Failed to process job: orderId={}, error={}", job.orderId(), e.getMessage());
            failed.add(job);
        }
    }
    
    // Move failed items to dead letter queue
    if (!failed.isEmpty()) {
        deadLetterQueueRepository.enqueueBatch(failed);
    }
    
    return new BatchResult(succeeded.size(), failed.size());
}
```

**Dead Letter Queue Schema:**
```sql
CREATE TABLE connector_price_dead_letter_queue (
    order_id VARCHAR(255) PRIMARY KEY,
    correlation_id VARCHAR(255),
    price DECIMAL(10,2),
    original_queued_at TIMESTAMP,
    failed_at TIMESTAMP DEFAULT NOW(),
    failure_reason TEXT,
    retry_count INT DEFAULT 0
);
```

**Benefits:**
- ✅ No data loss
- ✅ Manual review and retry
- ✅ Debugging aid
- ✅ Poison message isolation

---

### Idempotency

**Why It Matters:**
Temporal may retry activities, causing duplicate processing. Your application must handle duplicate requests gracefully.

**Current Idempotency Mechanisms:**

#### 1. Database Primary Key (order_id)

```java
// PriceQueueRepository.enqueue()
dsl.insertInto(CONNECTOR_PRICE_QUEUE)
    .set(CONNECTOR_PRICE_QUEUE.ORDER_ID, orderId)
    .set(CONNECTOR_PRICE_QUEUE.CORRELATION_ID, correlationId)
    .set(CONNECTOR_PRICE_QUEUE.PRICE, BigDecimal.valueOf(price))
    .onConflict(CONNECTOR_PRICE_QUEUE.ORDER_ID)
    .doNothing()  // Idempotent: ignore duplicates
    .execute();
```

**Effect:**
- Duplicate enqueue requests for same `order_id` are ignored
- Safe to retry enqueue operations

---

#### 2. HTTP Idempotency (Correlation-Id)

```java
// ProcessPriceBatchActivitiesImpl → RequestService
restClient.post()
    .uri(url)
    .header("X-Correlation-Id", correlationId)  // Track request
    .header("X-Callback-Url", callbackUrl)
    .body(body)
    .retrieve()
    .toBodilessEntity();
```

**Channel-App Responsibility:**
Channel should track `correlationId` and detect duplicate requests.

**Example Channel Implementation:**
```java
@PostMapping("/price")
public ResponseEntity<?> handlePrice(
        @RequestBody PriceRequest request,
        @RequestHeader("X-Correlation-Id") String correlationId) {
    
    // Check if already processed
    if (processedRequestCache.containsKey(correlationId)) {
        LOG.info("Duplicate request detected: correlationId={}", correlationId);
        return ResponseEntity.ok().build();  // Idempotent response
    }
    
    // Process request
    processPrice(request, correlationId);
    
    // Cache as processed
    processedRequestCache.put(correlationId, true);
    
    return ResponseEntity.accepted().build();
}
```

**Considerations:**
- Cache size: Use LRU cache to limit memory
- Cache duration: Keep for at least 24 hours (max workflow duration)
- Distributed cache: Redis for multi-instance deployments

---

### Transaction Boundaries

**Current Implementation:**

```java
// PriceQueueRepository.dequeue() - ATOMIC
public List<PriceJob> dequeue(int limit) {
    // SELECT and DELETE in SAME jOOQ DSLContext transaction
    List<PriceJob> jobs = dsl.select(...).fetch();  // Read
    dsl.deleteFrom(...).execute();                  // Delete
    return jobs;
}
```

**jOOQ Default Behavior:**
- Each `DSLContext` operation is **auto-commit** by default
- **NOT transactional** across multiple operations

**Problem:**
If DELETE fails after SELECT:
- Items read but not removed from queue
- Next dequeue returns same items (duplicate processing)

---

**Solution: Explicit Transactions**

```java
@Transactional  // Spring transaction management
public List<PriceJob> dequeue(int limit) {
    LOG.debug("Dequeuing up to {} price jobs", limit);
    
    List<PriceJob> jobs = dsl.select(
            CONNECTOR_PRICE_QUEUE.ORDER_ID,
            CONNECTOR_PRICE_QUEUE.CORRELATION_ID,
            CONNECTOR_PRICE_QUEUE.PRICE)
        .from(CONNECTOR_PRICE_QUEUE)
        .orderBy(CONNECTOR_PRICE_QUEUE.QUEUED_AT)
        .limit(limit)
        .forUpdate()  // ⚠️ Row-level lock (prevents concurrent dequeue)
        .fetch(this::toPriceJob);

    if (!jobs.isEmpty()) {
        List<String> orderIds = jobs.stream().map(PriceJob::orderId).toList();
        int deleted = dsl.deleteFrom(CONNECTOR_PRICE_QUEUE)
            .where(CONNECTOR_PRICE_QUEUE.ORDER_ID.in(orderIds))
            .execute();
        
        LOG.debug("Dequeued {} price jobs (deleted {} rows)", jobs.size(), deleted);
        
        // Sanity check
        if (deleted != jobs.size()) {
            LOG.warn("Deleted {} rows but selected {} jobs", deleted, jobs.size());
        }
    }
    
    return jobs;
}
```

**Key Changes:**
1. `@Transactional` - SELECT and DELETE in same transaction
2. `forUpdate()` - Row-level lock prevents concurrent dequeue of same items
3. Sanity check - Verify deleted count matches selected count

**Benefits:**
- ✅ Atomic dequeue
- ✅ No duplicate processing from concurrent workers
- ✅ Consistent state

---

## Advanced Temporal Patterns

### Pattern 1: Child Workflows for Parallel Processing

**Use Case:**
Process 100 items in batch, but send 10 items to channel in parallel (instead of sequentially).

**Implementation:**

```java
@WorkflowImpl(taskQueues = "send-price-to-channel")
public class SendPriceToChannelWorkflowImpl implements SendPriceToChannelWorkflow {

    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(1))
            .build();

    private final DequeuePricesActivities dequeuePricesActivities =
            Workflow.newActivityStub(DequeuePricesActivities.class, ACTIVITY_OPTIONS);

    @Override
    public BatchResult processPriceBatch() {
        // 1. Dequeue batch
        List<PriceQueueRepository.PriceJob> batch = dequeuePricesActivities.DequeuePrices();
        
        if (batch.isEmpty()) {
            return new BatchResult(0, 0, List.of(), List.of());
        }
        
        // 2. Start child workflow for each item (with parallelism control)
        List<Promise<ProcessResult>> promises = new ArrayList<>();
        
        for (PriceQueueRepository.PriceJob job : batch) {
            ChildWorkflowOptions options = ChildWorkflowOptions.newBuilder()
                .setWorkflowId("process-price-" + job.orderId())
                .setParentClosePolicy(ParentClosePolicy.PARENT_CLOSE_POLICY_TERMINATE)
                .build();
            
            ProcessItemWorkflow child = Workflow.newChildWorkflowStub(
                ProcessItemWorkflow.class, options);
            
            // Async.function returns a Promise (non-blocking)
            Promise<ProcessResult> promise = Async.function(child::processItem, job);
            promises.add(promise);
        }
        
        // 3. Wait for all child workflows (blocks until all complete)
        List<ProcessResult> results = new ArrayList<>();
        for (Promise<ProcessResult> promise : promises) {
            try {
                results.add(promise.get());  // Blocking call
            } catch (Exception e) {
                LOG.error("Child workflow failed: {}", e.getMessage());
                results.add(new ProcessResult(null, false, e.getMessage()));
            }
        }
        
        // 4. Aggregate results
        long succeeded = results.stream().filter(ProcessResult::success).count();
        long failed = results.size() - succeeded;
        List<String> successIds = results.stream()
            .filter(ProcessResult::success)
            .map(ProcessResult::orderId)
            .toList();
        List<String> failedIds = results.stream()
            .filter(r -> !r.success())
            .map(ProcessResult::orderId)
            .toList();
        
        return new BatchResult((int) succeeded, (int) failed, successIds, failedIds);
    }
}
```

**Child Workflow:**

```java
@WorkflowInterface
public interface ProcessItemWorkflow {
    @WorkflowMethod
    ProcessResult processItem(PriceQueueRepository.PriceJob job);
}

@WorkflowImpl(taskQueues = "process-item")
public class ProcessItemWorkflowImpl implements ProcessItemWorkflow {
    
    private static final ActivityOptions ACTIVITY_OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setBackoffCoefficient(2.0)
                .build())
            .build();
    
    private final ProcessItemActivity activity = 
        Workflow.newActivityStub(ProcessItemActivity.class, ACTIVITY_OPTIONS);
    
    @Override
    public ProcessResult processItem(PriceQueueRepository.PriceJob job) {
        try {
            activity.sendToChannel(job);
            return new ProcessResult(job.orderId(), true, null);
        } catch (Exception e) {
            return new ProcessResult(job.orderId(), false, e.getMessage());
        }
    }
}

// Result record
public record ProcessResult(String orderId, boolean success, String error) {}
public record BatchResult(int succeeded, int failed, List<String> successIds, List<String> failedIds) {}
```

**Parallelism Control:**

To limit concurrent child workflows (e.g., max 10 at a time):

```java
@Override
public BatchResult processPriceBatch() {
    List<PriceQueueRepository.PriceJob> batch = dequeuePricesActivities.DequeuePrices();
    
    if (batch.isEmpty()) {
        return new BatchResult(0, 0, List.of(), List.of());
    }
    
    int maxConcurrency = 10;
    List<ProcessResult> allResults = new ArrayList<>();
    
    // Process in chunks of maxConcurrency
    for (int i = 0; i < batch.size(); i += maxConcurrency) {
        int end = Math.min(i + maxConcurrency, batch.size());
        List<PriceQueueRepository.PriceJob> chunk = batch.subList(i, end);
        
        // Start child workflows for this chunk
        List<Promise<ProcessResult>> promises = chunk.stream()
            .map(job -> {
                ChildWorkflowOptions options = ChildWorkflowOptions.newBuilder()
                    .setWorkflowId("process-price-" + job.orderId())
                    .build();
                ProcessItemWorkflow child = Workflow.newChildWorkflowStub(
                    ProcessItemWorkflow.class, options);
                return Async.function(child::processItem, job);
            })
            .toList();
        
        // Wait for this chunk to complete before starting next
        for (Promise<ProcessResult> promise : promises) {
            allResults.add(promise.get());
        }
    }
    
    // Aggregate results
    // ... (same as before)
}
```

**Pros:**
- ✅ **Parallel execution** - 10x faster (10 items processed concurrently)
- ✅ **Independent retries** - Each item has own retry policy
- ✅ **Granular visibility** - See each child workflow in Temporal UI
- ✅ **Partial success** - Some items succeed even if others fail

**Cons:**
- ⚠️ **Higher overhead** - One workflow execution per item
- ⚠️ **History size** - Parent workflow history includes all child results
- ⚠️ **Complexity** - More code to manage coordination

**When to Use:**
- Batch size: 10-100 items
- Heterogeneous processing time (some items take 1s, others 10s)
- Critical operations requiring item-level tracking
- Acceptable overhead (worth the visibility)

---

### Pattern 2: Continue-As-New for Large Batches

**Use Case:**
Process millions of items from queue over hours/days without workflow history growing too large.

**Problem:**
Temporal workflow history is limited to ~50,000 events. Large batches can exceed this.

**Solution:**
Use `Workflow.continueAsNew()` to periodically start a fresh workflow execution, passing state forward.

**Implementation:**

```java
@WorkflowImpl(taskQueues = "send-price-to-channel")
public class SendPriceToChannelWorkflowImpl implements SendPriceToChannelWorkflow {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_BATCHES_PER_EXECUTION = 20;  // Continue-as-new after 20 batches

    private final DequeuePricesActivities dequeuePricesActivities = /* ... */;
    private final ProcessPriceBatchActivities processPriceBatchActivities = /* ... */;

    @Override
    public void processPriceBatchContinuous(int batchesProcessed) {
        Workflow.getLogger(SendPriceToChannelWorkflowImpl.class)
                .info("Starting batch processing: batchesProcessed={}", batchesProcessed);
        
        int batchCount = 0;
        
        while (batchCount < MAX_BATCHES_PER_EXECUTION) {
            // Dequeue batch
            List<PriceQueueRepository.PriceJob> batch = dequeuePricesActivities.DequeuePrices();
            
            if (batch.isEmpty()) {
                Workflow.getLogger(SendPriceToChannelWorkflowImpl.class)
                        .info("Queue empty, stopping");
                return;  // No more items, workflow completes
            }
            
            // Process batch
            processPriceBatchActivities.processPriceBatch(batch);
            
            batchCount++;
        }
        
        // Reached MAX_BATCHES_PER_EXECUTION, continue as new
        Workflow.getLogger(SendPriceToChannelWorkflowImpl.class)
                .info("Continuing as new: totalBatchesProcessed={}", 
                      batchesProcessed + batchCount);
        
        Workflow.continueAsNew(batchesProcessed + batchCount);
    }
}
```

**How Continue-As-New Works:**

```
Workflow Execution 1 (WorkflowId: "price-processor-1")
├─ Process batch 1-20
├─ Call Workflow.continueAsNew(20)
└─ Complete (history archived)

Workflow Execution 2 (SAME WorkflowId: "price-processor-1", NEW RunId)
├─ Process batch 21-40
├─ Call Workflow.continueAsNew(40)
└─ Complete (history archived)

Workflow Execution 3
├─ Process batch 41-60
├─ Queue empty → Return
└─ Complete
```

**Key Points:**
- **Same WorkflowId, different RunId** - Appears as continuation in Temporal UI
- **Fresh history** - Each new execution starts with empty history
- **State passed forward** - `batchesProcessed` parameter carries state
- **No limit** - Can continue indefinitely (days, weeks)

**Pros:**
- ✅ **Unbounded processing** - No history size limit
- ✅ **Long-running** - Process millions of items over days
- ✅ **Efficient** - History archived after each continuation

**Cons:**
- ⚠️ **Stateless** - Must pass all state as workflow arguments
- ⚠️ **Breaking changes** - Workflow code changes require careful deployment
- ⚠️ **Debugging** - History split across multiple executions

**When to Use:**
- Very large queues (millions of items)
- Long-running batch processors (> 1 hour)
- History size concerns (> 10,000 events)

---

### Pattern 3: Dynamic Batch Sizing

**Use Case:**
Adjust batch size based on queue depth or processing performance.

**Algorithm:**
```
If queue depth > 1000: batchSize = 100 (drain quickly)
If queue depth < 100: batchSize = 10 (normal processing)
If avg processing time > 5s per item: batchSize = 5 (reduce timeout risk)
```

**Implementation:**

```java
@Override
public void processPriceBatch() {
    // 1. Query queue depth
    int queueDepth = queueCheckActivity.getQueueDepth();
    
    // 2. Calculate dynamic batch size
    int batchSize = calculateBatchSize(queueDepth);
    
    Workflow.getLogger(SendPriceToChannelWorkflowImpl.class)
            .info("Dynamic batch size: {} (queue depth: {})", batchSize, queueDepth);
    
    // 3. Dequeue with dynamic size
    List<PriceQueueRepository.PriceJob> batch = 
        dequeuePricesActivities.DequeuePrices(batchSize);
    
    // 4. Process batch
    processPriceBatchActivities.processPriceBatch(batch);
}

private int calculateBatchSize(int queueDepth) {
    if (queueDepth > 10000) return 100;  // Aggressive draining
    if (queueDepth > 1000) return 50;
    if (queueDepth > 100) return 20;
    return 10;  // Default
}
```

**Pros:**
- ✅ **Adaptive** - Responds to system load
- ✅ **Efficiency** - Large batches when needed, small otherwise
- ✅ **Self-tuning** - No manual intervention

**Cons:**
- ⚠️ **Complexity** - More logic to maintain
- ⚠️ **Testing** - Harder to reproduce behavior
- ⚠️ **Timeouts** - Must ensure activity timeout accommodates max batch size

**When to Use:**
- Highly variable queue depth
- Cost-sensitive (pay per activity execution)
- Need automatic scaling

---

## Monitoring & Observability

### Current Metrics

Your application already tracks queue metrics via `QueueMetrics` class.

**Existing Metrics:**

| Metric Name | Type | Description | Labels |
|-------------|------|-------------|--------|
| `queue.enqueue` | Counter | Total items enqueued | `queue`, `service` |
| `queue.dequeue` | Counter | Total items dequeued | `queue`, `service` |
| `queue.size` | Gauge | Current queue size | `queue`, `service` |
| `queue.lag.seconds` | Gauge | Age of oldest item (seconds) | `queue`, `service` |

**Recording Points:**

```java
// In PriceQueueRepository or RequestService
queueMetrics.recordEnqueue(PRICE_QUEUE);                    // After enqueue
queueMetrics.recordDequeue(PRICE_QUEUE, batch.size());      // After dequeue
queueMetrics.setQueueSize(PRICE_QUEUE, priceQueueRepository.size());  // Periodic update
queueMetrics.updateQueueLag(PRICE_QUEUE, priceQueueRepository.getOldestQueuedAt());  // Periodic update
```

**Scheduled Gauge Updates:**

```java
@Scheduled(fixedRate = 5000)  // Every 5 seconds
public void updateMetricsGauges() {
    try {
        queueMetrics.updateQueueMetrics(PRICE_QUEUE, 
                priceQueueRepository::size, 
                priceQueueRepository::getOldestQueuedAt);
        queueMetrics.updateQueueMetrics(STOCK_QUEUE, 
                stockQueueRepository::size, 
                stockQueueRepository::getOldestQueuedAt);
    } catch (Exception e) {
        LOG.warn("Failed to update metrics gauges: {}", e.getMessage());
    }
}
```

---

### Recommended Batch-Specific Metrics

#### 1. Batch Processing Duration

**Metric Name:** `batch.processing.duration` (Histogram)

**Implementation:**
```java
// In ProcessPriceBatchActivitiesImpl
@Override
public void processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    long startTime = System.currentTimeMillis();
    
    try {
        for (PriceQueueRepository.PriceJob job : batch) {
            // ... process job ...
        }
    } finally {
        long duration = System.currentTimeMillis() - startTime;
        batchMetrics.recordBatchDuration("price", duration, batch.size());
    }
}
```

**Use Cases:**
- Identify slow batches
- Correlate batch size with processing time
- Calculate optimal batch size

---

#### 2. Items Per Batch (Histogram)

**Metric Name:** `batch.size` (Histogram)

**Implementation:**
```java
// In DequeuePricesActivitiesImpl
@Override
public List<PriceQueueRepository.PriceJob> DequeuePrices() {
    List<PriceQueueRepository.PriceJob> batch = priceQueueRepository.dequeue(BATCH_LIMIT);
    
    // Record batch size
    batchMetrics.recordBatchSize("price", batch.size());
    
    return batch;
}
```

**Analysis:**
- **Median batch size** - Typical batch size
- **P95/P99 batch size** - Maximum observed batch sizes
- **Empty batches** - Count of `size = 0` (wasted workflow executions)

---

#### 3. Batch Success/Failure Rates

**Metric Names:** 
- `batch.items.succeeded` (Counter)
- `batch.items.failed` (Counter)

**Implementation:**
```java
@Override
public BatchResult processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    int succeeded = 0;
    int failed = 0;
    
    for (PriceQueueRepository.PriceJob job : batch) {
        try {
            requestService.processJob(...);
            succeeded++;
        } catch (Exception e) {
            failed++;
        }
    }
    
    // Record metrics
    batchMetrics.recordBatchResult("price", succeeded, failed);
    
    return new BatchResult(succeeded, failed);
}
```

**Use Cases:**
- Alert on high failure rates
- Track quality of data in queue
- Identify problematic items

---

### Temporal UI Insights

#### Viewing Batch Workflows

1. **Navigate to Workflows** → Filter by task queue: `send-price-to-channel`
2. **Workflow Details** → See:
   - Workflow start/end time
   - Activity executions (DequeuePrices, ProcessPriceBatch)
   - Activity inputs/outputs (batch contents)
   - Retry attempts
   - Failure reasons

3. **Timeline View** → Visualize:
   - Activity execution duration
   - Gaps between activities (idle time)
   - Parallel executions (if using child workflows)

#### Identifying Bottlenecks

**Slow Batch Processing:**
```
DequeuePricesActivity: 50ms (fast)
ProcessPriceBatchActivity: 30 seconds (slow!)
```
**Diagnosis:** Channel-app is slow or batch too large

**Frequent Retries:**
```
ProcessPriceBatchActivity: Attempt 1 (failed), Attempt 2 (failed), Attempt 3 (success)
```
**Diagnosis:** Intermittent channel failures, check channel logs

**Empty Batches:**
```
DequeuePricesActivity: Returns []
ProcessPriceBatchActivity: Skipped (no items)
```
**Optimization:** Reduce schedule frequency or check queue before triggering

---

### Custom Span Attributes (OpenTelemetry)

**Adding Batch Context to Traces:**

```java
@Override
public void processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    Span span = Span.current();
    
    // Add batch metadata to span
    span.setAttribute("batch.size", batch.size());
    span.setAttribute("batch.queue", "connector_price");
    span.setAttribute("batch.order_ids", batch.stream()
                                              .map(PriceQueueRepository.PriceJob::orderId)
                                              .collect(Collectors.joining(",")));
    
    try {
        for (PriceQueueRepository.PriceJob job : batch) {
            // Create child span for each item
            Span itemSpan = tracer.spanBuilder("process-price-item")
                                   .setAttribute("order.id", job.orderId())
                                   .setAttribute("correlation.id", job.correlationId())
                                   .startSpan();
            
            try {
                requestService.processJob(...);
                itemSpan.setStatus(StatusCode.OK);
            } catch (Exception e) {
                itemSpan.recordException(e);
                itemSpan.setStatus(StatusCode.ERROR, e.getMessage());
            } finally {
                itemSpan.end();
            }
        }
    } finally {
        span.setAttribute("batch.succeeded", succeededCount);
        span.setAttribute("batch.failed", failedCount);
    }
}
```

**SigNoz Trace View:**
```
SendPriceToChannelWorkflow (parent span)
├─ DequeuePricesActivity
│  └─ batch.size = 10
└─ ProcessPriceBatchActivity
   ├─ batch.size = 10
   ├─ batch.queue = connector_price
   ├─ batch.order_ids = order-1,order-2,...
   ├─ process-price-item (order-1) ✓
   ├─ process-price-item (order-2) ✓
   ├─ process-price-item (order-3) ✗ (failed)
   └─ batch.succeeded = 9, batch.failed = 1
```

---

### Alerting Strategies

#### 1. High Queue Lag

**Condition:**
```
queue.lag.seconds{queue="connector_price"} > 300
```
**Meaning:** Oldest item in queue is > 5 minutes old

**Possible Causes:**
- Batch processing too slow
- Channel-app unavailable
- Not enough workers

**Actions:**
- Scale up workers
- Increase batch size
- Investigate channel-app performance

---

#### 2. Queue Backlog Growing

**Condition:**
```
deriv(queue.size{queue="connector_price"}[5m]) > 10
```
**Meaning:** Queue size increasing by > 10 items/minute

**Possible Causes:**
- Enqueue rate > dequeue rate
- Batch processing failures
- Workers down

**Actions:**
- Check worker status
- Review failure logs
- Temporarily increase schedule frequency

---

#### 3. High Batch Failure Rate

**Condition:**
```
rate(batch.items.failed[5m]) / rate(batch.items.total[5m]) > 0.1
```
**Meaning:** > 10% of items failing

**Possible Causes:**
- Channel-app errors
- Invalid data in queue
- Network issues

**Actions:**
- Check channel-app logs
- Review failed item details in Temporal UI
- Inspect dead letter queue (if implemented)

---

## Performance Optimization

### Batch Size Tuning

**Trade-offs:**

| Batch Size | Throughput | Latency | Failure Impact | Overhead |
|------------|------------|---------|----------------|----------|
| **Small (1-5)** | Low | Low (ms) | Minimal | High (many activities) |
| **Medium (10-50)** | Medium | Medium (seconds) | Moderate | Balanced |
| **Large (100-1000)** | High | High (minutes) | Severe | Low (few activities) |

**Finding Optimal Size:**

1. **Measure baseline** (current: 10 items)
   - Avg processing time per item: 2 seconds
   - Activity timeout: 1 minute
   - Max batch size: 30 items (1 minute / 2 seconds)

2. **Test different sizes**
   - Test batch sizes: 5, 10, 20, 50
   - Monitor throughput, latency, failure rate for 10 minutes each
   - Collect metrics

3. **Analyze results**
   
   | Batch Size | Throughput (items/sec) | P95 Latency | Failure Rate |
   |------------|------------------------|-------------|--------------|
   | 5 | 2.5 | 10s | 1% |
   | 10 | 4.8 | 20s | 2% |
   | 20 | 8.9 | 40s | 3% |
   | 50 | 15.2 | 100s | 8% ⚠️ |

4. **Choose optimal** (balancing throughput vs failure rate)
   - **Recommendation:** Batch size 20
   - **Reason:** 2x throughput with acceptable failure rate

---

### Parallel Processing

**Current:** Sequential processing (one item at a time)

```java
for (PriceQueueRepository.PriceJob job : batch) {
    requestService.processJob(...);  // Blocks until complete
}
```

**Optimized:** Parallel HTTP requests

```java
@Override
public void processPriceBatch(List<PriceQueueRepository.PriceJob> batch) {
    int parallelism = 5;  // Max 5 concurrent HTTP requests
    
    ExecutorService executor = Executors.newFixedThreadPool(parallelism);
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    
    for (PriceQueueRepository.PriceJob job : batch) {
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            try {
                requestService.processJob(job.orderId(), job.correlationId(), "price", 
                                          properties.getChannelPriceUrl(), 
                                          new PriceRequest(job.orderId(), job.price()));
            } catch (Exception e) {
                LOG.error("Failed to process job: orderId={}, error={}", job.orderId(), e.getMessage());
            }
        }, executor);
        futures.add(future);
    }
    
    // Wait for all to complete
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    
    executor.shutdown();
}
```

**Performance Gain:**
- **Sequential:** 10 items × 2 seconds = 20 seconds
- **Parallel (5 threads):** 10 items / 5 threads × 2 seconds = 4 seconds
- **Speedup:** 5x faster ✅

**Caveats:**
- ⚠️ **Thread management** - ExecutorService must be properly shut down
- ⚠️ **Connection pooling** - RestClient must support concurrent requests
- ⚠️ **Error handling** - Collect failures from CompletableFutures
- ⚠️ **Rate limiting** - Don't overwhelm channel-app

---

### Database Optimization

#### 1. Efficient Dequeue Query

**Current (Good):**
```java
List<PriceJob> jobs = dsl.select(...)
    .from(CONNECTOR_PRICE_QUEUE)
    .orderBy(CONNECTOR_PRICE_QUEUE.QUEUED_AT)  // Uses index
    .limit(10)
    .fetch();
```

**Ensure Index Exists:**
```sql
CREATE INDEX idx_price_queue_queued_at 
ON connector_price_queue(queued_at);
```

**Why It Matters:**
- Without index: Full table scan (slow for large queues)
- With index: Index seek (fast even for millions of rows)

---

#### 2. Connection Pooling

**Verify HikariCP Configuration:**

```yaml
# application.yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # Max concurrent DB connections
      minimum-idle: 5         # Min idle connections
      connection-timeout: 30000  # 30 seconds
      idle-timeout: 600000       # 10 minutes
      max-lifetime: 1800000      # 30 minutes
```

**For Batch Processing:**
- **Multiple workers**: Increase `maximum-pool-size` to accommodate all workers
- **High concurrency**: Ensure DB server can handle connections (max_connections in PostgreSQL)

---

### Worker Scaling

**Current Setup:**
- Single worker instance processing batches sequentially

**Scaling Options:**

#### Option 1: Vertical Scaling (More Resources)

```bash
java -Xmx4G -Xms2G -jar connector-app.jar
```

**Effect:**
- More memory for connection pooling
- Faster garbage collection
- Better throughput per worker

---

#### Option 2: Horizontal Scaling (More Workers)

```bash
# Start 3 worker instances
java -jar connector-app.jar --server.port=8080 &
java -jar connector-app.jar --server.port=8081 &
java -jar connector-app.jar --server.port=8082 &
```

**Effect:**
- **3x throughput** (3 workers processing batches in parallel)
- Temporal automatically distributes work across workers
- No code changes needed

**Temporal Task Queue:**
- Workers poll same task queue: `send-price-to-channel`
- Temporal ensures each workflow assigned to exactly one worker
- Load balancing automatic

---

## Testing Strategy

### Unit Tests

#### Test 1: Dequeue Activity

```java
@ExtendWith(MockitoExtension.class)
class DequeuePricesActivitiesImplTest {
    
    @Mock
    private PriceQueueRepository priceQueueRepository;
    
    @Mock
    private ConnectorProperties properties;
    
    @InjectMocks
    private DequeuePricesActivitiesImpl activity;
    
    @Test
    void testDequeuePrices_Success() {
        // Arrange
        when(properties.getBatchSize()).thenReturn(10);
        List<PriceJob> expectedBatch = List.of(
            new PriceJob("order-1", "corr-1", 100),
            new PriceJob("order-2", "corr-2", 200)
        );
        when(priceQueueRepository.dequeue(10)).thenReturn(expectedBatch);
        
        // Act
        List<PriceJob> result = activity.DequeuePrices();
        
        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).orderId()).isEqualTo("order-1");
        verify(priceQueueRepository).dequeue(10);
    }
    
    @Test
    void testDequeuePrices_EmptyQueue() {
        // Arrange
        when(properties.getBatchSize()).thenReturn(10);
        when(priceQueueRepository.dequeue(10)).thenReturn(List.of());
        
        // Act
        List<PriceJob> result = activity.DequeuePrices();
        
        // Assert
        assertThat(result).isEmpty();
    }
}
```

---

#### Test 2: Process Batch Activity

```java
@ExtendWith(MockitoExtension.class)
class ProcessPriceBatchActivitiesImplTest {
    
    @Mock
    private RequestService requestService;
    
    @Mock
    private ConnectorProperties properties;
    
    @InjectMocks
    private ProcessPriceBatchActivitiesImpl activity;
    
    @Test
    void testProcessPriceBatch_AllSucceed() {
        // Arrange
        when(properties.getChannelPriceUrl()).thenReturn("http://localhost:8081/price");
        List<PriceJob> batch = List.of(
            new PriceJob("order-1", "corr-1", 100),
            new PriceJob("order-2", "corr-2", 200)
        );
        
        // Act
        activity.processPriceBatch(batch);
        
        // Assert
        verify(requestService, times(2)).processJob(anyString(), anyString(), eq("price"), anyString(), any());
    }
    
    @Test
    void testProcessPriceBatch_SomeFailures() {
        // Arrange
        when(properties.getChannelPriceUrl()).thenReturn("http://localhost:8081/price");
        List<PriceJob> batch = List.of(
            new PriceJob("order-1", "corr-1", 100),
            new PriceJob("order-2", "corr-2", 200)
        );
        
        // Mock first succeeds, second fails
        doNothing().when(requestService).processJob(eq("order-1"), anyString(), anyString(), anyString(), any());
        doThrow(new RuntimeException("Channel unavailable"))
            .when(requestService).processJob(eq("order-2"), anyString(), anyString(), anyString(), any());
        
        // Act & Assert
        // Current implementation: exception propagates, activity fails
        assertThatThrownBy(() -> activity.processPriceBatch(batch))
            .isInstanceOf(RuntimeException.class)
            .hasMessageContaining("Channel unavailable");
        
        // Verify order-1 was processed before failure
        verify(requestService).processJob(eq("order-1"), anyString(), anyString(), anyString(), any());
    }
}
```

---

### Integration Tests with Temporal Test Server

```java
@SpringBootTest
class SendPriceToChannelWorkflowIntegrationTest {
    
    private TestWorkflowEnvironment testEnv;
    private Worker worker;
    private WorkflowClient client;
    
    @Mock
    private PriceQueueRepository priceQueueRepository;
    
    @Mock
    private RequestService requestService;
    
    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        worker = testEnv.newWorker("send-price-to-channel");
        
        // Register workflow
        worker.registerWorkflowImplementationTypes(SendPriceToChannelWorkflowImpl.class);
        
        // Register activities with mocks
        DequeuePricesActivitiesImpl dequeueActivity = new DequeuePricesActivitiesImpl(priceQueueRepository, properties);
        ProcessPriceBatchActivitiesImpl processActivity = new ProcessPriceBatchActivitiesImpl(requestService, properties);
        
        worker.registerActivitiesImplementations(dequeueActivity, processActivity);
        
        testEnv.start();
        client = testEnv.getWorkflowClient();
    }
    
    @AfterEach
    void tearDown() {
        testEnv.close();
    }
    
    @Test
    void testBatchProcessing_Success() {
        // Arrange
        List<PriceJob> batch = List.of(
            new PriceJob("order-1", "corr-1", 100),
            new PriceJob("order-2", "corr-2", 200),
            new PriceJob("order-3", "corr-3", 300)
        );
        when(priceQueueRepository.dequeue(anyInt())).thenReturn(batch);
        
        // Act
        SendPriceToChannelWorkflow workflow = client.newWorkflowStub(
            SendPriceToChannelWorkflow.class,
            WorkflowOptions.newBuilder()
                .setWorkflowId("test-workflow-1")
                .setTaskQueue("send-price-to-channel")
                .build()
        );
        
        workflow.processPriceBatch();
        
        // Assert
        verify(priceQueueRepository).dequeue(anyInt());
        verify(requestService, times(3)).processJob(anyString(), anyString(), eq("price"), anyString(), any());
    }
}
```

---

### Load Tests

#### Test Scenario: High Queue Depth

```bash
#!/bin/bash
# load_test_batch_processing.sh

echo "=== Batch Processing Load Test ==="
echo "Populating queue with 10,000 items..."

# Populate queue
for i in {1..10000}; do
  curl -s -X POST http://localhost:8082/priceAndStock \
    -H "Content-Type: application/json" \
    -d "{\"orderId\": \"load-test-$i\", \"price\": $((RANDOM % 1000)), \"stock\": $((RANDOM % 100))}" > /dev/null
done

echo "Queue populated. Monitoring processing..."

# Monitor queue size over time
for i in {1..60}; do
  queue_size=$(curl -s http://localhost:8080/actuator/metrics/queue.size | jq '.measurements[0].value')
  echo "Time ${i}s: Queue size = $queue_size"
  sleep 1
done

echo "Load test complete."
```

**Expected Results:**
- Queue size decreases steadily
- Throughput: ~2-5 items/second (depending on batch size and schedule interval)
- No failures or timeouts

---

## Future Enhancements

### 1. Configurable Batch Size (High Priority)

**Status:** Documented, ready to implement

**Current State:** Hardcoded `BATCH_LIMIT = 10`

**Implementation Steps:**

1. Add `batchSize` property to `ConnectorProperties`
2. Update `application.yaml` with `batch-size: 10`
3. Inject properties into dequeue activities
4. Use `properties.getBatchSize()` instead of hardcoded constant

**Benefits:**
- ✅ Tune per environment
- ✅ No code changes for adjustments
- ✅ A/B testing capability

**Effort:** Low (1-2 hours)

---

### 2. Item-Level Error Tracking (Medium Priority)

**Status:** Future enhancement

**Description:** Track which items in batch failed and return detailed results.

**Implementation:**
- Return `BatchResult` record from process activity
- Include `succeeded`, `failed`, `successIds`, `failedIds`, `errors` map
- Log failures for debugging

**Benefits:**
- ✅ Visibility into partial failures
- ✅ Identify problematic items
- ✅ Better debugging

**Effort:** Medium (4-6 hours)

---

### 3. Dead Letter Queue (Medium Priority)

**Status:** Future enhancement

**Description:** Move failed items to separate queue for manual review.

**Implementation:**
- Create DLQ table in PostgreSQL
- Create DLQ repository
- Move failures to DLQ in process activity
- Add retry endpoint for manual reprocessing

**Benefits:**
- ✅ No data loss
- ✅ Manual review capability
- ✅ Debugging aid

**Effort:** High (8-10 hours)

---

### 4. Dynamic Batch Sizing (Low Priority)

**Status:** Future enhancement

**Description:** Automatically adjust batch size based on queue depth.

**Algorithm:**
```java
private int calculateDynamicBatchSize() {
    int queueDepth = priceQueueRepository.size();
    if (queueDepth > 10000) return 100;
    if (queueDepth > 1000) return 50;
    if (queueDepth > 100) return 20;
    return 10;
}
```

**Benefits:**
- ✅ Automatic scaling
- ✅ Responds to load

**Effort:** Medium (6-8 hours)

---

### 5. Batch Result Aggregation (Low Priority)

**Status:** Future enhancement

**Description:** Store batch processing statistics for analytics.

**Implementation:**
- Create `batch_processing_log` table
- Log batch execution details
- Create analytics endpoint

**Benefits:**
- ✅ Historical analysis
- ✅ Performance trends
- ✅ Capacity planning

**Effort:** High (10-12 hours)

---

## Comparison: Activity vs Workflow Batching

### Side-by-Side Comparison

| Aspect | Activity-Based (Current) | Workflow-Based (Child Workflows) |
|--------|--------------------------|-----------------------------------|
| **Code Complexity** | ⭐ Simple | ⭐⭐⭐ Complex |
| **Parallel Execution** | ❌ Sequential | ✅ Concurrent |
| **Item-Level Retries** | ❌ Batch-level only | ✅ Individual retries |
| **Observability** | ⭐⭐ Activity level | ⭐⭐⭐ Item level |
| **History Size** | ⭐ Small | ⭐⭐⭐ Large |
| **Overhead** | ⭐ Low | ⭐⭐⭐ High |
| **Throughput** | ⭐⭐ Medium | ⭐⭐⭐ High |
| **Latency** | ⭐⭐ Medium | ⭐⭐⭐ Low |
| **Failure Impact** | ⭐⭐⭐ High (entire batch) | ⭐ Low (single item) |
| **Best For** | High volume, homogeneous | Critical, heterogeneous |

---

### When to Use Activity-Based Batching

✅ **Use When:**
- High throughput required (1000+ items/sec)
- Items have similar processing time
- Failures are rare
- Cost-sensitive (fewer activity executions)
- Simple requirements (no complex error handling)

**Example Use Cases:**
- Data sync jobs
- Event aggregation
- Bulk notifications
- Log processing

---

### When to Use Workflow-Based Batching

✅ **Use When:**
- Item-level tracking required
- Heterogeneous processing time
- Complex error handling needed
- Parallel execution important
- Moderate volume (< 1000 items/sec)

**Example Use Cases:**
- Financial transactions
- Order fulfillment
- Multi-step workflows
- Compliance-critical operations

---

## Reference & Examples

### Complete Code Example: Configurable Batch Size

See detailed implementation in [Batch Configuration](#batch-configuration) section above.

**Summary:**
1. Add `batchSize` to `ConnectorProperties`
2. Update `application.yaml`
3. Inject into activities
4. Use `properties.getBatchSize()`

---

### Temporal CLI Commands

#### List Batch Workflows

```bash
temporal workflow list \
  --query 'WorkflowType="SendPriceToChannelWorkflow"' \
  --namespace default
```

#### Describe Specific Workflow

```bash
temporal workflow describe \
  --workflow-id "price-queue-schedule-2024-01-15T10:30:00Z" \
  --namespace default
```

#### View Workflow History

```bash
temporal workflow show \
  --workflow-id "price-queue-schedule-2024-01-15T10:30:00Z" \
  --namespace default \
  --output json | jq '.events[] | select(.eventType == "ActivityTaskCompleted")'
```

---

## Troubleshooting

### Issue: Batch Processing Slow

**Symptoms:**
- Workflows taking > 1 minute to complete
- Queue backlog growing
- High `queue.lag.seconds` metric

**Possible Causes:**

1. **Channel-app slow or unavailable**
   
   **Solution:** Scale channel-app or optimize its processing

2. **Batch size too large**
   
   **Solution:** Reduce batch size to 5-10 items

3. **Database connection pool exhausted**
   
   **Solution:** Increase `maximum-pool-size` in `application.yaml`

4. **Not enough workers**
   
   **Solution:** Start additional worker instances

---

### Issue: Items Lost from Queue

**Symptoms:**
- Items enqueued but never processed
- Queue size decreases but no items sent to channel
- Missing workflow executions in Temporal UI

**Possible Causes:**

1. **Transaction rollback during dequeue**
   
   **Solution:** Ensure `@Transactional` on dequeue method

2. **Activity failure after dequeue**
   
   **Solution:** Add error handling in ProcessBatchActivity

3. **Concurrent dequeue without locking**
   
   **Solution:** Add `forUpdate()` to dequeue query

---

### Issue: Batch Timeouts

**Symptoms:**
- Activities timing out (exceeding `StartToCloseTimeout`)
- Workflows failing with "Activity timeout" error
- Incomplete batch processing

**Solution:**

Increase timeout or reduce batch size:
```java
ActivityOptions.newBuilder()
    .setStartToCloseTimeout(Duration.ofMinutes(5))  // Increase from 1 minute
    .build()
```

---

### Issue: Duplicate Processing

**Symptoms:**
- Same `order_id` processed multiple times
- Duplicate HTTP requests to channel

**Possible Causes:**

1. **Activity retry without idempotency**
   
   **Solution:** Ensure channel-app is idempotent (uses `correlationId`)

2. **Multiple workers dequeuing same items**
   
   **Solution:** Add `forUpdate()` row lock in dequeue query

---

## Related Documentation

- **[Temporal Schedules Guide](./schedule.md)** - How schedules trigger batch workflows
- **[Temporal Official Documentation](https://docs.temporal.io/)** - Core concepts
- **[Temporal Java SDK Javadoc](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/index.html)** - API reference
- **[jOOQ Documentation](https://www.jooq.org/doc/latest/manual/)** - Database operations
- **[Spring Boot Temporal](https://github.com/temporalio/sdk-java/tree/master/temporal-spring-boot-autoconfigure-alpha)** - Spring integration

---

## Summary

This document covered:

✅ **Batch Processing Patterns** - Activity-based, Workflow-based, Hybrid approaches  
✅ **Current Architecture** - Detailed explanation of your implementation  
✅ **Batch Configuration** - Size, timeouts, retry policies  
✅ **Error Handling** - Dequeue failures, processing failures, idempotency  
✅ **Advanced Patterns** - Child workflows, continue-as-new, dynamic sizing  
✅ **Monitoring** - Metrics, Temporal UI, OpenTelemetry integration  
✅ **Performance Optimization** - Batch size tuning, parallel processing, worker scaling  
✅ **Testing** - Unit tests, integration tests, load tests  
✅ **Future Enhancements** - Configurable batch size, DLQ, item-level tracking  
✅ **Troubleshooting** - Common issues and solutions

**Key Takeaways:**

1. **Current implementation is solid** - Activity-based batching with FIFO queue, transactional dequeue
2. **Configurable batch size** should be high priority (easy win)
3. **Monitor batch metrics** - Duration, size, failure rate
4. **Consider child workflows** for critical operations requiring item-level tracking
5. **Dead letter queue** prevents data loss from failures

**Next Steps:**

1. Implement configurable batch size (1-2 hours)
2. Add batch-specific metrics (2-3 hours)
3. Evaluate need for child workflows based on requirements
4. Set up alerting for queue lag and batch failures

---

**Document Version:** 1.0  
**Last Updated:** 2024-01-15  
**Author:** AI Assistant (based on codebase analysis)

---

This comprehensive guide serves as your reference for all batch processing concerns in your Temporal application. Feel free to extend it as your use cases evolve!
