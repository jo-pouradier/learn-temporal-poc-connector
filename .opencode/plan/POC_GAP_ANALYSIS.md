# POC Gap Analysis & Implementation Plan

## 1. Executive Summary
This document analyzes the current state of the generic "Channel Manager" POC (Pre-Temporal V1) against the business requirements. The system consists of a **Main App** (Source of Truth), a **Connector App** (Orchestrator), and a **Channel App** (Mock External Service like Amazon/Zalando).

**Current Status:** The core structure (Spring Boot modules, Queues, Async logic) is in place, but several specific logic requirements (batching, random failures, visualization) are missing.

## 2. Component Analysis & Gaps

### A. Main App
*   **Role:** Source of truth, entry point.
*   **Current State:** Sends updates 1-by-1 to Connector.
*   **Requirement Gap:**
    *   **Batching:** Must batch updates by 10 before sending to Connector.
    *   **Communication:** Needs to send these batches via a Bulk/Batch API to Connector (currently uses single endpoint).
*   **Status:** ⚠️ Partial (Missing Batching logic).

### B. Connector App
*   **Role:** Async buffer & orchestrator.
*   **Current State:** Has Queues (Price/Stock). Drains the *entire* queue every 10s.
*   **Requirement Gap:**
    *   **Ingestion:** Needs to accept Batch requests from Main.
    *   **Processing:** Logic should explicitly process "batches of 10" (currently just `while(queue.poll())`).
    *   **Visualization:** Needs data exposed for the Frontend.
*   **Status:** ⚠️ Partial (Logic needs refinement).

### C. Channel App
*   **Role:** Mock external platform.
*   **Current State:** Simple sleep (1-3s), Range validation.
*   **Requirement Gap:**
    *   **Chaos Engineering:** Needs 25% random failure rate with random reasons.
    *   **Latency:** Delay must be 1-10s (currently 1-3s).
    *   **Persistence:** Needs to store the "Order State" (Price/Stock) to verify updates actually happened.
*   **Status:** ⚠️ Partial (Missing Chaos & State).

### D. Frontend
*   **Role:** Visualization.
*   **Current State:** Non-existent.
*   **Requirement Gap:** Needs to visualize real-time status of orders in Main and Connector.
*   **Status:** ❌ Missing.

---

## 3. Implementation Plan (TODOs)

The following tickets outline the work required to close the gaps.

### Phase 1: Channel Realism (The Mock)
**Ticket [CHAN-01]: Enhance Channel Simulation Logic**
*   **Description:** Update `ChannelService` to match strict chaos requirements.
*   **Tasks:**
    1.  Update `sleep` duration to random between 1000ms and 10000ms.
    2.  Implement `RandomFailure` logic: 25% of requests should return HTTP 202 but send a "Failed" callback later (or fail immediately 500? *Clarification: "response 202... api doit fail 1 fois sur 4". Usually implies the sync call fails or async callback fails. We will implement Async Callback failure for realism*).
    3.  Add `OrderStore` (Map) to save the final Price/Stock for an Order ID.
    4.  Expose `GET /channel/orders/{id}` to verify state.

### Phase 2: Batching Architecture (Main -> Connector)
**Ticket [CONN-01]: Bulk Ingestion API**
*   **Description:** Allow Connector to receive multiple updates in one HTTP call.
*   **Tasks:**
    1.  Create `POST /webhook/priceAndStock/batch`.
    2.  Accept `List<PriceAndStockRequest>`.
    3.  Queue all items individually.

**Ticket [MAIN-01]: Outbound Batch Aggregator**
*   **Description:** Main app should not send updates immediately.
*   **Tasks:**
    1.  Implement an internal buffer (BlockingQueue).
    2.  Create a consumer that flushes when `size >= 10` OR `max_wait > 5s`.
    3.  Call Connector's new Batch API.

### Phase 3: Connector Orchestration
**Ticket [CONN-02]: Strict Batch Processing**
*   **Description:** Ensure Connector processes queues in chunks of 10.
*   **Tasks:**
    1.  Refactor `@Scheduled` tasks.
    2.  Instead of `while(poll != null)`, use `List<Job> batch = new ArrayList<>(); queue.drainTo(batch, 10);`.
    3.  Log/Trace "Processing Batch ID: XYZ".

### Phase 4: Visualization
**Ticket [FRONT-01]: Real-time Dashboard**
*   **Description:** A simple UI to track order flow.
*   **Tasks:**
    1.  Create a simple HTML/JS page (served by Main or Connector).
    2.  Poll `Main GET /status` and `Connector GET /status`.
    3.  Show a table: `Order ID | Main Status | Connector Status | Channel State`.
    4.  Add button: "Trigger Burst (10)".

---

## 4. Recommended Additional Features (Bonus)

To better demonstrate the complexity Temporal solves (vs this custom solution):

1.  **"The Lost Callback" Scenario:**
    *   *Feature:* Configure Channel to occasionally *never* send a callback (0.5% chance).
    *   *Why:* This highlights the need for timeouts/retries which are hard to code manually but free in Temporal.

2.  **Service Restart Simulation:**
    *   *Feature:* Ability to kill/restart Connector during a batch process.
    *   *Why:* Demonstrates in-memory queue data loss (unless we add Redis/DB persistence, which increases V1 complexity).

3.  **Idempotency Check:**
    *   *Feature:* Channel should reject duplicate updates with same Correlation ID.
    *   *Why:* Common real-world issue.
