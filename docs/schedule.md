# Temporal Schedules Guide

## Table of Contents
1. [Overview](#overview)
2. [Temporal Cron vs Temporal Schedules](#temporal-cron-vs-temporal-schedules)
3. [Current Architecture](#current-architecture)
4. [Implementation Plan](#implementation-plan)
5. [Migration Steps](#migration-steps)
6. [Testing Strategy](#testing-strategy)
7. [Benefits](#benefits)
8. [Reference](#reference)

---

## Overview

**Temporal Schedules** are a native Temporal feature that allows you to trigger workflows on a recurring basis **without needing external schedulers** like Spring's `@Scheduled` or `ScheduledExecutorService`.

This document outlines the migration from Spring-based scheduling to Temporal Schedules for the `SendPriceToChannelWorkflow` and `SendStockToChannelWorkflow` periodic execution.

---

## Temporal Cron vs Temporal Schedules

### Temporal Cron Workflows (Older Approach)

```java
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setTaskQueue("send-price-to-channel")
    .setCronSchedule("*/5 * * * *") // Every 5 minutes
    .build();
```

**Characteristics:**
- **Single long-running workflow** that repeatedly executes
- Uses cron syntax only (no interval support natively)
- The workflow **continues indefinitely** - same workflow execution ID
- Limited control after creation (hard to pause/modify without stopping the workflow)
- History can grow large over time
- Tightly coupled to the workflow lifecycle

**Lifecycle:**
```
Start CronWorkflow
    ↓
Execute → Wait → Execute → Wait → Execute ...
(same workflow execution, history keeps growing)
```

---

### Temporal Schedules (Modern Approach - RECOMMENDED)

```java
ScheduleClient scheduleClient = client.newScheduleClient();
Schedule schedule = Schedule.newBuilder()
    .setAction(
        ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType(SendPriceToChannelWorkflow.class)
            .setTaskQueue("send-price-to-channel")
            .build()
    )
    .setSpec(
        ScheduleSpec.newBuilder()
            .setIntervals(ScheduleIntervalSpec.newBuilder()
                .setEvery(Duration.ofMillis(1000)) // Every 1 second
                .build())
            .build()
    )
    .build();
```

**Characteristics:**
- **Separate workflows** created for each execution
- Supports **intervals, cron, and calendar-based** scheduling
- Each execution is independent with its own history
- **Pause/Resume/Update** schedules without affecting running workflows
- Better observability - see schedule state in Temporal UI
- Can control overlap behavior (skip, buffer, allow-all, cancel-other)
- Backfill support for missed runs

**Lifecycle:**
```
Schedule (independent entity)
    ↓ trigger
Workflow Execution 1 (completes)
    ↓ trigger
Workflow Execution 2 (completes)
    ↓ trigger
Workflow Execution 3 (completes)
(each is a separate workflow with fresh history)
```

---

### Key Differences Table

| Feature | Cron Workflows | Temporal Schedules |
|---------|---------------|-------------------|
| **Execution Model** | Single continuous workflow | New workflow per trigger |
| **Schedule Syntax** | Cron only | Interval, Cron, Calendar |
| **History Growth** | Grows indefinitely | Fresh history each run |
| **Pause/Resume** | Must stop workflow | Built-in schedule control |
| **Overlap Control** | Manual handling | Built-in policies |
| **Observability** | Limited | Dedicated UI section |
| **Backfill** | No | Yes |
| **Update Schedule** | Must restart workflow | Update without restart |
| **Use Case** | Legacy, simple cron needs | Modern recurring tasks |

---

## Current Architecture

### Spring-Based Scheduling (Before Migration)

```
QueueProcessorScheduler (Spring ScheduledExecutorService)
    ↓ every 1000ms (configurable)
Creates new SendPriceToChannelWorkflow instance
Creates new SendStockToChannelWorkflow instance
```

**File:** `connector-app/src/main/java/com/example/temporal/service/QueueProcessorScheduler.java`

**Key Components:**
- Uses `ScheduledExecutorService` with `scheduleAtFixedRate()`
- Interval configurable via `ConnectorProperties.queueProcessingIntervalMs`
- Checks queue size before creating workflow (optimization)
- Supports dynamic rescheduling at runtime
- Manages lifecycle via `@PostConstruct` / `@PreDestroy`

**Limitations:**
- Requires application to be running
- Not visible in Temporal UI
- Doesn't survive application restarts
- Spring dependency for scheduling
- No built-in distributed coordination

---

### Target Architecture (After Migration)

```
Temporal Schedule (managed by Temporal Server)
    ↓ every 1000ms (configurable)
Automatically starts SendPriceToChannelWorkflow
Automatically starts SendStockToChannelWorkflow
```

**Benefits:**
1. **Durability**: Schedules survive application restarts
2. **Observability**: View/manage schedules in Temporal Web UI
3. **No Spring Dependency**: Pure Temporal solution
4. **Distributed**: Works across multiple workers automatically
5. **Controllable**: Pause/resume without code changes
6. **Monitorable**: Temporal tracks schedule execution history

---

## Implementation Plan

### Components Overview

#### Files to Create:
1. **`TemporalScheduleManager.java`** - Replaces `QueueProcessorScheduler`
2. **(Future)** `ScheduleController.java` - REST API for schedule management

#### Files to Modify:
1. **`QueueProcessorScheduler.java`** - **DELETE** (replaced by `TemporalScheduleManager`)
2. **`TemporalApplication.java`** - Remove `@EnableScheduling`
3. **`SchedulingConfig.java`** - **DELETE** (no longer needed if no other `@Scheduled` usage)

#### Configuration:
- Keep existing `connector.queue-processing-interval-ms` in `application.yaml`
- Schedules will use this value for trigger intervals

---

### 1. TemporalScheduleManager Design

**Location:** `connector-app/src/main/java/com/example/temporal/service/TemporalScheduleManager.java`

**Responsibilities:**
- Create/update two Temporal Schedules at startup:
  - `price-queue-schedule`: Triggers `SendPriceToChannelWorkflow`
  - `stock-queue-schedule`: Triggers `SendStockToChannelWorkflow`
- Support dynamic interval updates via `reschedule()` method
- Provide `getStatus()` for health checks
- Handle errors gracefully (log but don't fail startup)

**Key Implementation Details:**

```java
@Component
public class TemporalScheduleManager {
    private static final Logger LOG = LoggerFactory.getLogger(TemporalScheduleManager.class);
    private static final String PRICE_SCHEDULE_ID = "price-queue-schedule";
    private static final String STOCK_SCHEDULE_ID = "stock-queue-schedule";
    
    private final WorkflowClient client;
    private final ConnectorProperties properties;
    private final PriceQueueRepository priceQueueRepository;
    private final StockQueueRepository stockQueueRepository;
    
    @PostConstruct
    public void initialize() {
        createOrUpdateSchedules();
    }
    
    private void createOrUpdateSchedules() {
        long intervalMs = properties.getQueueProcessingIntervalMs();
        if (intervalMs <= 0) {
            LOG.warn("Invalid interval {}ms, using default 1000ms", intervalMs);
            intervalMs = 1000;
        }
        
        createOrUpdatePriceSchedule(intervalMs);
        createOrUpdateStockSchedule(intervalMs);
    }
    
    public synchronized void reschedule() {
        LOG.info("Rescheduling with new interval: {}ms", properties.getQueueProcessingIntervalMs());
        createOrUpdateSchedules();
    }
    
    public ScheduleStatus getStatus() {
        // Query Temporal for schedule state
    }
}
```

**Schedule Configuration:**

```java
Schedule schedule = Schedule.newBuilder()
    .setAction(
        ScheduleActionStartWorkflow.newBuilder()
            .setWorkflowType(SendPriceToChannelWorkflow.class)
            .setTaskQueue("send-price-to-channel")
            .build()
    )
    .setSpec(
        ScheduleSpec.newBuilder()
            .setIntervals(ScheduleIntervalSpec.newBuilder()
                .setEvery(Duration.ofMillis(intervalMs))
                .build())
            .build()
    )
    .setPolicy(
        SchedulePolicy.newBuilder()
            .setOverlapPolicy(ScheduleOverlapPolicy.OVERLAP_POLICY_ALLOW_ALL)
            .build()
    )
    .build();
```

**Overlap Policy: ALLOW_ALL**
- Multiple concurrent executions are permitted
- Good for queue processing where batches might arrive faster than processing
- Each workflow execution is independent

**Alternative Overlap Policies:**
- `SKIP`: Skip new execution if previous is still running
- `CANCEL_OTHER`: Cancel previous execution and start new one
- `TERMINATE_OTHER`: Terminate previous forcefully (use with caution)

---

### 2. Workflow Empty Queue Handling

**Current Approach (Keeping for Now):**
Queue empty check happens in scheduler before workflow creation:

```java
// In QueueProcessorScheduler
if (priceQueueRepository.size() == 0) {
    return; // Skip workflow creation
}
```

**With Temporal Schedules:**
Schedules will always trigger workflows, even if queue is empty. The workflow/activity will handle "no items" gracefully.

**Future Enhancement (Document for Later):**
Move empty queue check to workflow level for cleaner separation:

```java
@Override
public void processPriceBatch() {
    // Check queue size via activity or query
    int queueSize = queueCheckActivity.getPriceQueueSize();
    if (queueSize == 0) {
        Workflow.getLogger(SendPriceToChannelWorkflowImpl.class)
                .info("Price queue empty, skipping batch");
        return;
    }
    
    var batch = dequeuePricesActivities.DequeuePrices();
    processPriceBatchActivities.processPriceBatch(batch);
}
```

**Benefits of Workflow-Level Check:**
- Temporal tracks all executions (including no-ops)
- Better visibility into schedule triggers
- Activities handle their own preconditions
- More testable (mock queue check activity)

**Current Approach is Fine Because:**
- Simpler for initial migration
- Reduces unnecessary workflow executions
- Activities already handle empty batches gracefully
- Can optimize later without breaking changes

---

### 3. Remove Spring Scheduling Dependencies

**Files to Modify:**

#### `TemporalApplication.java`
```java
// REMOVE this annotation:
@EnableScheduling
```

#### `QueueProcessorScheduler.java`
```java
// DELETE entire file (185 lines)
// Replaced by TemporalScheduleManager
```

#### `SchedulingConfig.java`
```java
// DELETE if no other @Scheduled methods exist
// Keep if other parts of the app use @Scheduled
```

**Check for Other @Scheduled Usage:**
```bash
# Search for @Scheduled in codebase
grep -r "@Scheduled" --include="*.java" .
```

If found in other services (like `RequestService:264`), keep `SchedulingConfig.java` for those use cases.

---

### 4. Configuration

**Keep Existing Property:**
```yaml
# application.yaml
connector:
  queue-processing-interval-ms: 1000
```

**Optional Additional Properties:**
```yaml
connector:
  queue-processing-interval-ms: 1000
  schedule:
    overlap-policy: ALLOW_ALL  # SKIP, CANCEL_OTHER, TERMINATE_OTHER
    timezone: UTC               # Schedule timezone
    pause-on-failure: false     # Pause schedule on repeated failures
```

---

### 5. Schedule Management API (Future Enhancement)

**Location:** `connector-app/src/main/java/com/example/temporal/controller/ScheduleController.java`

**Endpoints:**
```java
@RestController
@RequestMapping("/api/schedules")
public class ScheduleController {
    
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        // Return status of both schedules
    }
    
    @PostMapping("/pause/{scheduleId}")
    public ResponseEntity<Void> pause(@PathVariable String scheduleId) {
        // Pause specified schedule
    }
    
    @PostMapping("/resume/{scheduleId}")
    public ResponseEntity<Void> resume(@PathVariable String scheduleId) {
        // Resume specified schedule
    }
    
    @PutMapping("/interval")
    public ResponseEntity<Void> updateInterval(@RequestParam long intervalMs) {
        // Update interval for both schedules
    }
}
```

**Note:** This is marked as future work. Initial implementation will rely on Temporal Web UI and CLI for schedule management.

---

## Migration Steps

### Prerequisites
- Temporal Server running (local or cloud)
- Temporal Web UI accessible (http://localhost:8233 for local)
- Temporal CLI installed (optional, for manual testing)

### Step-by-Step Migration

#### 1. Create TemporalScheduleManager
```bash
# Create new file
touch connector-app/src/main/java/com/example/temporal/service/TemporalScheduleManager.java

# Implement the manager as described above
```

#### 2. Write Unit Tests
```bash
# Create test file
touch connector-app/src/test/java/com/example/temporal/service/TemporalScheduleManagerTest.java

# Test:
# - Schedule creation
# - Schedule updates
# - Error handling
# - Status queries
```

#### 3. Update TemporalApplication
```java
// Remove @EnableScheduling annotation
@SpringBootApplication
// @EnableScheduling  <- REMOVE THIS
public class TemporalApplication {
    public static void main(String[] args) {
        SpringApplication.run(TemporalApplication.class, args);
    }
}
```

#### 4. Delete Old Scheduler
```bash
# Delete QueueProcessorScheduler
rm connector-app/src/main/java/com/example/temporal/service/QueueProcessorScheduler.java

# Check if SchedulingConfig is still needed
grep -r "@Scheduled" connector-app/src --include="*.java"

# If no other @Scheduled usage, delete SchedulingConfig
# rm connector-app/src/main/java/com/example/temporal/config/SchedulingConfig.java
```

#### 5. Test Application Startup
```bash
cd connector-app
mvn clean compile
mvn spring-boot:run
```

**Verify:**
- Application starts without errors
- Logs show schedule creation messages
- No Spring scheduling errors

#### 6. Verify Schedules in Temporal UI
```bash
# Open Temporal Web UI
open http://localhost:8233

# Navigate to "Schedules" section
# Verify presence of:
# - price-queue-schedule
# - stock-queue-schedule

# Check schedule details:
# - Interval matches config (1000ms)
# - Overlap policy: ALLOW_ALL
# - Status: RUNNING
```

#### 7. Manual Schedule Trigger Test
```bash
# Via Temporal UI:
# 1. Go to Schedules
# 2. Click on "price-queue-schedule"
# 3. Click "Trigger Now"
# 4. Verify workflow starts in Workflows list

# Via Temporal CLI:
temporal schedule trigger --schedule-id price-queue-schedule
temporal schedule trigger --schedule-id stock-queue-schedule
```

#### 8. Run Integration Tests
```bash
# Test dual processing flow
./test_new_dual_processing.sh

# Test state management
./test_main_state_management.sh
```

#### 9. Test Dynamic Interval Update
```java
// Update property (via config or endpoint if implemented)
properties.setQueueProcessingIntervalMs(2000);

// Call reschedule
scheduleManager.reschedule();

// Verify in Temporal UI:
// - Next run time reflects new interval (2000ms)
```

#### 10. Update Documentation
```bash
# Update AGENTS.md with:
# - Temporal Schedules usage
# - How to view schedules in Temporal UI
# - How to manually trigger/pause schedules
# - Future enhancement notes
```

---

## Testing Strategy

### Unit Tests

**File:** `connector-app/src/test/java/com/example/temporal/service/TemporalScheduleManagerTest.java`

**Test Cases:**
```java
@Test
void testScheduleCreation() {
    // Mock ScheduleClient
    // Verify create() called with correct params
    // Verify interval from properties used
}

@Test
void testScheduleUpdate() {
    // Update interval in properties
    // Call reschedule()
    // Verify update() called on both schedules
}

@Test
void testInvalidInterval() {
    // Set interval to 0 or negative
    // Verify default 1000ms used
    // Verify warning logged
}

@Test
void testConnectionFailure() {
    // Mock Temporal connection failure
    // Verify error logged
    // Verify application doesn't crash
}

@Test
void testGetStatus() {
    // Mock schedule state from Temporal
    // Verify status returned correctly
}
```

---

### Integration Tests

**Test 1: Schedule Creation**
```bash
# Start connector-app
cd connector-app && mvn spring-boot:run

# Check logs for schedule creation
# Expected log output:
# "Creating Temporal schedule: price-queue-schedule with interval 1000ms"
# "Creating Temporal schedule: stock-queue-schedule with interval 1000ms"

# Verify in Temporal UI
open http://localhost:8233
# Navigate to Schedules → verify both schedules exist
```

**Test 2: Workflow Execution**
```bash
# Trigger schedule manually
temporal schedule trigger --schedule-id price-queue-schedule

# Check workflow started
temporal workflow list

# Verify workflow completes successfully
# Check logs for batch processing
```

**Test 3: Interval Update**
```bash
# Update interval in application.yaml
# connector.queue-processing-interval-ms: 5000

# Restart application OR call reschedule endpoint

# Verify in Temporal UI:
# Schedule shows new interval
# Next run time updated accordingly
```

**Test 4: Multiple Workers**
```bash
# Start multiple connector-app instances
# mvn spring-boot:run (instance 1)
# mvn spring-boot:run (instance 2)

# Verify:
# - Only one set of schedules exists (idempotent creation)
# - Workflows distributed across workers
# - No duplicate schedule creation errors
```

**Test 5: Application Restart**
```bash
# Stop connector-app
# Ctrl+C

# Verify schedules still running in Temporal UI
# (they should continue triggering workflows)

# Start connector-app again
# Verify schedules not duplicated
# Verify manager finds existing schedules
```

---

### Manual Verification Checklist

- [ ] Application starts without errors
- [ ] Schedules appear in Temporal Web UI
- [ ] Schedule IDs: `price-queue-schedule`, `stock-queue-schedule`
- [ ] Interval matches config (`1000ms` by default)
- [ ] Overlap policy: `ALLOW_ALL`
- [ ] Status: `RUNNING`
- [ ] Manually trigger schedule → workflow starts
- [ ] Workflow processes queue items correctly
- [ ] Update interval → schedule updated in UI
- [ ] Restart application → schedules persist
- [ ] Stop application → schedules continue running
- [ ] Logs show schedule creation/update messages
- [ ] No Spring scheduling errors in logs

---

## Benefits

### Before (Spring Scheduling)
❌ Requires application running  
❌ Not visible in Temporal UI  
❌ Doesn't survive restarts  
❌ Spring dependency  
❌ Manual distributed coordination  
❌ Limited observability  

### After (Temporal Schedules)
✅ **Durability**: Schedules survive app crashes/restarts  
✅ **Observability**: View all schedules in Temporal Web UI  
✅ **Distributed**: Multiple workers handle scheduled workflows automatically  
✅ **Decoupled**: No Spring scheduling dependency  
✅ **Controllable**: Pause/resume without code changes  
✅ **Monitorable**: Temporal tracks schedule execution history  
✅ **Scalable**: Temporal coordinates across instances  
✅ **Flexible**: Update intervals without redeployment (via API)  

---

## Reference

### Temporal Schedule API

**Create Schedule:**
```java
ScheduleClient scheduleClient = client.newScheduleClient();

ScheduleHandle handle = scheduleClient.createSchedule(
    "price-queue-schedule",
    Schedule.newBuilder()
        .setAction(
            ScheduleActionStartWorkflow.newBuilder()
                .setWorkflowType(SendPriceToChannelWorkflow.class)
                .setTaskQueue("send-price-to-channel")
                .build()
        )
        .setSpec(
            ScheduleSpec.newBuilder()
                .setIntervals(ScheduleIntervalSpec.newBuilder()
                    .setEvery(Duration.ofMillis(1000))
                    .build())
                .build()
        )
        .setPolicy(
            SchedulePolicy.newBuilder()
                .setOverlapPolicy(ScheduleOverlapPolicy.OVERLAP_POLICY_ALLOW_ALL)
                .build()
        )
        .build(),
    ScheduleOptions.newBuilder().build()
);
```

**Update Schedule:**
```java
ScheduleHandle handle = scheduleClient.getHandle("price-queue-schedule");

handle.update((ScheduleUpdateInput input) -> {
    Schedule.Builder builder = input.getDescription().getSchedule().toBuilder();
    
    // Update interval
    builder.setSpec(
        ScheduleSpec.newBuilder()
            .setIntervals(ScheduleIntervalSpec.newBuilder()
                .setEvery(Duration.ofMillis(2000)) // New interval
                .build())
            .build()
    );
    
    return new ScheduleUpdate(builder.build());
});
```

**Query Schedule Status:**
```java
ScheduleHandle handle = scheduleClient.getHandle("price-queue-schedule");
ScheduleDescription desc = handle.describe();

boolean isPaused = desc.getSchedule().getState().isPaused();
Instant nextRun = desc.getInfo().getNextActionTimes().get(0);
long numActions = desc.getInfo().getNumActions();
```

**Pause/Resume Schedule:**
```java
ScheduleHandle handle = scheduleClient.getHandle("price-queue-schedule");

// Pause
handle.pause("Pausing for maintenance");

// Resume
handle.unpause("Resuming normal operation");
```

**Trigger Schedule Manually:**
```java
ScheduleHandle handle = scheduleClient.getHandle("price-queue-schedule");
handle.trigger(ScheduleTriggerOptions.newBuilder().build());
```

**Delete Schedule:**
```java
ScheduleHandle handle = scheduleClient.getHandle("price-queue-schedule");
handle.delete();
```

---

### Temporal CLI Commands

**List Schedules:**
```bash
temporal schedule list
```

**Describe Schedule:**
```bash
temporal schedule describe --schedule-id price-queue-schedule
```

**Trigger Schedule:**
```bash
temporal schedule trigger --schedule-id price-queue-schedule
```

**Pause Schedule:**
```bash
temporal schedule toggle --schedule-id price-queue-schedule --pause --reason "Maintenance"
```

**Resume Schedule:**
```bash
temporal schedule toggle --schedule-id price-queue-schedule --unpause --reason "Resuming"
```

**Update Schedule Interval:**
```bash
temporal schedule update --schedule-id price-queue-schedule \
    --interval 2s
```

**Delete Schedule:**
```bash
temporal schedule delete --schedule-id price-queue-schedule
```

---

### Schedule Specification Options

**Interval-Based (Current Use Case):**
```java
ScheduleSpec.newBuilder()
    .setIntervals(ScheduleIntervalSpec.newBuilder()
        .setEvery(Duration.ofMillis(1000))
        .build())
    .build()
```

**Cron-Based:**
```java
ScheduleSpec.newBuilder()
    .setCronExpressions("*/5 * * * *") // Every 5 minutes
    .build()
```

**Calendar-Based:**
```java
ScheduleSpec.newBuilder()
    .setCalendars(ScheduleCalendarSpec.newBuilder()
        .setHour(9) // 9 AM
        .setMinute(0)
        .setDayOfWeek(1, 5) // Monday to Friday
        .build())
    .build()
```

**Mixed:**
```java
ScheduleSpec.newBuilder()
    .setIntervals(ScheduleIntervalSpec.newBuilder()
        .setEvery(Duration.ofHours(1))
        .build())
    .setStartAt(Instant.now().plus(Duration.ofHours(2))) // Start in 2 hours
    .setEndAt(Instant.now().plus(Duration.ofDays(30))) // End in 30 days
    .build()
```

---

### Overlap Policies

| Policy | Behavior | Use Case |
|--------|----------|----------|
| `ALLOW_ALL` | Allow concurrent executions | High-throughput queue processing (current) |
| `SKIP` | Skip if previous still running | Resource-intensive tasks |
| `CANCEL_OTHER` | Cancel previous, start new | Latest data always processed |
| `TERMINATE_OTHER` | Force terminate previous | Critical updates (use carefully) |
| `BUFFER_ONE` | Queue one execution | Avoid dropping work |
| `BUFFER_ALL` | Queue all executions | Never drop work |

---

### Error Handling Best Practices

**Schedule Creation Retry:**
```java
private void createScheduleWithRetry(String scheduleId, Schedule schedule, int maxRetries) {
    int attempts = 0;
    while (attempts < maxRetries) {
        try {
            scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
            LOG.info("Schedule created successfully: {}", scheduleId);
            return;
        } catch (ScheduleAlreadyRunningException e) {
            LOG.info("Schedule already exists: {}, updating instead", scheduleId);
            updateSchedule(scheduleId, schedule);
            return;
        } catch (Exception e) {
            attempts++;
            LOG.warn("Failed to create schedule {}, attempt {}/{}: {}", 
                     scheduleId, attempts, maxRetries, e.getMessage());
            if (attempts >= maxRetries) {
                LOG.error("Failed to create schedule {} after {} attempts", scheduleId, maxRetries, e);
            } else {
                sleep(Duration.ofSeconds(5));
            }
        }
    }
}
```

**Graceful Degradation:**
```java
@PostConstruct
public void initialize() {
    try {
        createOrUpdateSchedules();
    } catch (Exception e) {
        LOG.error("Failed to initialize Temporal Schedules, scheduling will not work", e);
        // Don't crash the application
        // Consider fallback to Spring scheduling or manual triggers
    }
}
```

---

### Monitoring and Observability

**Metrics to Track:**
- Schedule creation success/failure rate
- Schedule trigger frequency
- Workflow execution count per schedule
- Schedule update latency
- Queue processing throughput

**Temporal Web UI Views:**
1. **Schedules List**: Overview of all schedules
2. **Schedule Detail**: Trigger history, next run, pause state
3. **Workflow List**: Filter by schedule ID to see triggered workflows
4. **Metrics Dashboard**: Schedule execution stats

**Logging Best Practices:**
```java
LOG.info("Creating schedule: {} with interval {}ms", scheduleId, intervalMs);
LOG.info("Schedule created successfully: {}", scheduleId);
LOG.warn("Schedule already exists: {}, updating interval to {}ms", scheduleId, intervalMs);
LOG.error("Failed to create schedule: {}", scheduleId, e);
```

---

## Troubleshooting

### Issue: Schedules Not Appearing in UI

**Possible Causes:**
- Temporal Server not running
- Wrong namespace
- Schedule creation failed silently

**Solutions:**
```bash
# Check Temporal Server is running
temporal server check

# List schedules in specific namespace
temporal schedule list --namespace default

# Check application logs for errors
grep "schedule" connector-app/logs/app.log
```

---

### Issue: Workflows Not Triggering

**Possible Causes:**
- Schedule paused
- Worker not running
- Task queue mismatch
- Workflow implementation not registered

**Solutions:**
```bash
# Check schedule status
temporal schedule describe --schedule-id price-queue-schedule

# Verify schedule not paused
# If paused: temporal schedule toggle --schedule-id price-queue-schedule --unpause

# Check workers are running
temporal task-queue list-partition --task-queue send-price-to-channel

# Manually trigger to test
temporal schedule trigger --schedule-id price-queue-schedule
```

---

### Issue: Duplicate Schedules Created

**Possible Causes:**
- Multiple application instances without idempotent creation
- Schedule ID not consistent

**Solutions:**
```java
// Use create-or-update pattern
try {
    scheduleClient.createSchedule(scheduleId, schedule, options);
} catch (ScheduleAlreadyRunningException e) {
    LOG.info("Schedule exists, updating: {}", scheduleId);
    ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
    handle.update(input -> new ScheduleUpdate(schedule));
}
```

---

### Issue: Interval Not Updating

**Possible Causes:**
- Cached property value
- Update not called after property change
- Schedule update failed silently

**Solutions:**
```java
// Force property reload
properties.refresh();

// Verify new interval
LOG.info("New interval: {}ms", properties.getQueueProcessingIntervalMs());

// Call reschedule
scheduleManager.reschedule();

// Verify in Temporal UI
```

---

## Future Enhancements

### 1. Workflow-Level Empty Queue Check
**Status:** Documented, not yet implemented

**Description:** Move queue size check from scheduler to workflow level for better visibility.

**Implementation:**
```java
@WorkflowImpl(taskQueues = "send-price-to-channel")
public class SendPriceToChannelWorkflowImpl implements SendPriceToChannelWorkflow {
    
    @Override
    public void processPriceBatch() {
        // Add queue check activity
        int queueSize = queueCheckActivity.getPriceQueueSize();
        
        if (queueSize == 0) {
            Workflow.getLogger(SendPriceToChannelWorkflowImpl.class)
                    .info("Price queue empty, skipping batch");
            return;
        }
        
        var batch = dequeuePricesActivities.DequeuePrices();
        processPriceBatchActivities.processPriceBatch(batch);
    }
}
```

**Benefits:**
- Temporal tracks all schedule triggers (including no-ops)
- Better visibility into execution patterns
- Activities own their preconditions
- More testable

---

### 2. REST API for Schedule Management
**Status:** Planned for later

**Endpoints:**
```
GET    /api/schedules/status
POST   /api/schedules/pause/{scheduleId}
POST   /api/schedules/resume/{scheduleId}
PUT    /api/schedules/interval
POST   /api/schedules/trigger/{scheduleId}
```

**Use Cases:**
- DevOps dashboard integration
- Automated schedule control based on system load
- CI/CD pipeline integration (pause during deployments)

---

### 3. Advanced Schedule Configurations

**Time-Based Scheduling:**
```java
// Only run during business hours
ScheduleSpec.newBuilder()
    .setCalendars(ScheduleCalendarSpec.newBuilder()
        .setHour(9, 17) // 9 AM to 5 PM
        .setDayOfWeek(1, 5) // Monday to Friday
        .build())
    .build()
```

**Adaptive Intervals:**
```java
// Slower during off-peak hours
if (isBusinessHours()) {
    return Duration.ofMillis(1000); // 1 second during business hours
} else {
    return Duration.ofMillis(5000); // 5 seconds off-peak
}
```

**Backfill Support:**
```java
SchedulePolicy.newBuilder()
    .setCatchupWindow(Duration.ofHours(1))
    .build()
```

---

## Rollback Plan

If issues arise during migration:

### 1. Immediate Rollback
```bash
# Delete Temporal Schedules
temporal schedule delete --schedule-id price-queue-schedule
temporal schedule delete --schedule-id stock-queue-schedule

# Restore QueueProcessorScheduler from git
git checkout HEAD -- connector-app/src/main/java/com/example/temporal/service/QueueProcessorScheduler.java

# Re-add @EnableScheduling
git checkout HEAD -- connector-app/src/main/java/com/example/temporal/TemporalApplication.java

# Rebuild and restart
cd connector-app && mvn clean package && mvn spring-boot:run
```

### 2. Verify Rollback
```bash
# Check logs for Spring scheduling messages
grep "Scheduled" connector-app/logs/app.log

# Verify workflows triggering
tail -f connector-app/logs/app.log | grep "process.*queue"
```

---

## Related Documentation

- [Temporal Schedules Official Docs](https://docs.temporal.io/workflows#schedule)
- [Temporal Java SDK Schedule API](https://www.javadoc.io/doc/io.temporal/temporal-sdk/latest/io/temporal/client/schedules/package-summary.html)
- [Temporal Web UI Guide](https://docs.temporal.io/web-ui)
- [Temporal CLI Reference](https://docs.temporal.io/cli)

---

## Questions or Issues?

If you encounter problems during migration:

1. Check Temporal Web UI for schedule status
2. Review application logs for errors
3. Verify Temporal Server connectivity
4. Test manual schedule trigger
5. Check worker registration for task queues
6. Consult this document's troubleshooting section

For additional help, refer to:
- Temporal Slack community: https://temporal.io/slack
- Temporal forums: https://community.temporal.io
- Project AGENTS.md for coding guidelines
