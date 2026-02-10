package com.example.main.service;

import com.example.main.config.ConnectorProperties;
import com.example.main.repository.MainStateRepository;
import com.example.main.repository.MainEventRepository;
import com.example.main.repository.MainQueueRepository;
import com.example.shared.exception.NotFoundException;
import com.example.shared.exception.ValidationException;
import com.example.shared.model.CallbackResponse;
import com.example.shared.model.PagedResponse;
import com.example.shared.model.EventType;
import com.example.shared.model.OrderStatus;
import com.example.shared.model.PriceAndStockRequest;
import com.example.shared.model.PriceAndStockResponse;
import com.example.shared.model.RequestState;
import com.example.shared.model.StateEvent;
import com.example.shared.observability.QueueMetrics;
import com.example.shared.observability.RequestMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RequestService {

    private static final Logger LOG = LoggerFactory.getLogger(RequestService.class);
    
    // Queue name for metrics
    private static final String BATCH_QUEUE = "main_batch";

    private final ConnectorProperties properties;
    private final RestClient restClient;
    private final MainStateRepository stateRepository;
    private final MainEventRepository eventRepository;
    private final MainQueueRepository queueRepository;
    private final QueueMetrics queueMetrics;
    private final RequestMetrics requestMetrics;
    private boolean backgroundLoadActive = false;
    
    // Rate-based load generator
    private final ScheduledExecutorService loadExecutor = Executors.newScheduledThreadPool(4);
    private volatile ScheduledFuture<?> loadTask;
    private final AtomicInteger currentRate = new AtomicInteger(0);
    private final AtomicLong totalGenerated = new AtomicLong(0);

    public RequestService(ConnectorProperties properties, RestClient.Builder restClientBuilder,
                          MainStateRepository stateRepository, MainEventRepository eventRepository,
                          MainQueueRepository queueRepository, QueueMetrics queueMetrics, 
                          RequestMetrics requestMetrics) {
        this.properties = properties;
        this.restClient = restClientBuilder.baseUrl(properties.getUrl()).build();
        this.stateRepository = stateRepository;
        this.eventRepository = eventRepository;
        this.queueRepository = queueRepository;
        this.queueMetrics = queueMetrics;
        this.requestMetrics = requestMetrics;
    }

    public ResponseEntity<?> processPriceAndStock(PriceAndStockRequest payload) {
        String orderId = payload.orderId();
        if (orderId == null || orderId.isBlank()) {
            throw new ValidationException("orderId is required");
        }

        // Check if this order already exists in DB
        RequestState state = stateRepository.findByOrderId(orderId).orElse(null);
        String correlationId;
        
        if (state == null) {
            // New order - create new state
            correlationId = UUID.randomUUID().toString();
            state = new RequestState(orderId, correlationId, payload);
            LOG.info("NEW request buffered: orderId={}, correlationId={}, price={}, stock={}",
                    orderId, correlationId, payload.price(), payload.stock());
        } else {
            // Existing order - update and preserve history
            correlationId = state.getCorrelationId();
            state.setOriginalRequest(payload);
            LOG.info("UPDATE request buffered: orderId={}, correlationId={}, NEW price={}, NEW stock={}",
                    orderId, correlationId, payload.price(), payload.stock());
        }
        
        requestMetrics.recordRequestReceived();

        state.setStatus(OrderStatus.ACCEPTED);
        state.setAcceptedAt(LocalDateTime.now());
        
        // Save state to DB
        stateRepository.save(state);
        
        // Save event to DB
        StateEvent event = new StateEvent(EventType.REQUEST_ACCEPTED, OrderStatus.ACCEPTED, 
                "Request buffered: price=" + payload.price() + ", stock=" + payload.stock());
        eventRepository.save(orderId, event);
        
        // Add to batch queue
        queueRepository.enqueue(orderId);
        queueMetrics.recordEnqueue(BATCH_QUEUE);
        
        PriceAndStockResponse responseBody = new PriceAndStockResponse(orderId, "accepted");
        return ResponseEntity.accepted()
                .header("X-Correlation-Id", correlationId)
                .body(responseBody);
    }

    public RequestState getStatus(String orderId) {
        return stateRepository.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("Request not found: " + orderId));
    }

    public PagedResponse<RequestState> getAllStatus(String filter, int page, int size) {
        int offset = page * size;
        List<RequestState> states = stateRepository.findAllPaginated(offset, size, filter);
        long totalElements = stateRepository.countFiltered(filter);
        return PagedResponse.of(states, page, size, totalElements);
    }

    public Map<String, Object> processCallback(String orderId, CallbackResponse callbackResponse) {
        LOG.info("Callback: orderId={}", orderId);
        requestMetrics.recordCallbackReceived();

        RequestState state = stateRepository.findByOrderId(orderId)
            .orElseThrow(() -> new NotFoundException("Request not found: " + orderId));

        OrderStatus newStatus = determineStatusFromCallback(callbackResponse);
        state.setStatus(newStatus);
        state.setCompletedAt(LocalDateTime.now());
        state.setFinalResponse(callbackResponse);
        
        // Save updated state
        stateRepository.save(state);
        
        // Save event
        StateEvent event = new StateEvent(EventType.CALLBACK_RECEIVED, newStatus, 
                "Final callback: isPriceOk=" + callbackResponse.isPriceOk() + 
                ", isStockOk=" + callbackResponse.isStockOk());
        eventRepository.save(orderId, event);

        // Update metrics based on actual status
        if (OrderStatus.COMPLETED.equals(newStatus)) {
            requestMetrics.recordRequestCompleted();
        } else {
            requestMetrics.recordRequestFailed();
        }

        // Record end-to-end latency
        if (state.getSubmittedAt() != null && state.getCompletedAt() != null) {
            Duration latency = Duration.between(state.getSubmittedAt(), state.getCompletedAt());
            requestMetrics.recordCallbackLatency(latency);
        }

        return Map.of("status", "callback_received", "orderId", orderId);
    }

    private OrderStatus determineStatusFromCallback(CallbackResponse response) {
        Boolean isPriceOk = response.isPriceOk();
        Boolean isStockOk = response.isStockOk();
        
        // Both failed or have errors
        if ((isPriceOk == null && isStockOk == null) || 
            (Boolean.FALSE.equals(isPriceOk) && Boolean.FALSE.equals(isStockOk))) {
            LOG.warn("Order failed: both price and stock validation failed or null");
            return OrderStatus.ERROR;
        }
        
        // One failed/null, one succeeded - partial success
        if (isPriceOk == null || isStockOk == null || 
            Boolean.FALSE.equals(isPriceOk) || Boolean.FALSE.equals(isStockOk)) {
            LOG.warn("Order partially completed: isPriceOk={}, isStockOk={}", isPriceOk, isStockOk);
            return OrderStatus.PARTIAL;
        }
        
        // Both succeeded
        return OrderStatus.COMPLETED;
    }

    public Map<String, Object> processBurst(int count) {
        LOG.info("Burst: {} requests", count);
        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(this::sendBackgroundRequest);
        }
        return Map.of("message", "Burst of " + count + " started");
    }

    public String toggleBackgroundLoad(String action) {
        return switch (action) {
            case "activate" -> {
                backgroundLoadActive = true;
                LOG.info("Background load ACTIVATED");
                yield "Background load activated";
            }
            case "deactivate" -> {
                backgroundLoadActive = false;
                LOG.info("Background load DEACTIVATED");
                yield "Background load deactivated";
            }
            default -> throw new ValidationException("Invalid action. Use 'activate' or 'deactivate'");
        };
    }

    @Scheduled(fixedRate = 1000)
    public void backgroundTask() {
        if (backgroundLoadActive) sendBackgroundRequest();
    }

    @Scheduled(fixedRate = 1000)
    public void flushBatch() {
        int queueSize = queueRepository.size();
        if (queueSize == 0) return;

        // Dequeue up to 10 order IDs
        List<String> orderIds = queueRepository.dequeue(10);
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
            List<PriceAndStockRequest> payloads = batch.stream()
                .map(RequestState::getOriginalRequest)
                .toList();
                
            ResponseEntity<List> response = restClient.post()
                .uri("/webhook/priceAndStock/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloads)
                .retrieve()
                .toEntity(List.class);
                
            LOG.info("Connector response: status={}, body={}", response.getStatusCode(), response.getBody());
                
            if (response.getStatusCode().is2xxSuccessful()) {
                 batch.forEach(state -> {
                     state.setStatus(OrderStatus.SENT_TO_CONNECTOR);
                     state.setSubmittedAt(LocalDateTime.now());
                     stateRepository.save(state);
                     
                     StateEvent event = new StateEvent(EventType.BATCH_SENT, OrderStatus.SENT_TO_CONNECTOR, 
                             "Batch of " + batch.size() + " sent to connector");
                     eventRepository.save(state.getOrderId(), event);
                 });
                 LOG.info("Batch sent successfully: {} orders marked as SENT_TO_CONNECTOR", batch.size());
            } else {
                 handleBatchFailure(batch, "Connector rejected batch: " + response.getStatusCode());
            }
        } catch (Exception e) {
            handleBatchFailure(batch, "Batch send failed: " + e.getMessage());
            LOG.error("Batch send failed", e);
        }
    }

    /**
     * Scheduled task to update queue and request state gauge metrics.
     * Runs every 5 seconds to keep gauges up-to-date.
     */
    @Scheduled(fixedRate = 5000)
    public void updateMetricsGauges() {
        try {
            // Update queue metrics
            queueMetrics.updateQueueMetrics(BATCH_QUEUE, 
                    queueRepository::size, 
                    queueRepository::getOldestQueuedAt);
            
            // Update request counts by status
            requestMetrics.updateRequestCountsByStatus(stateRepository.countByStatus());
            
            LOG.debug("Metrics gauges updated");
        } catch (Exception e) {
            LOG.warn("Failed to update metrics gauges: {}", e.getMessage());
        }
    }
    
    private void handleBatchFailure(List<RequestState> batch, String error) {
        batch.forEach(state -> {
            state.setStatus(OrderStatus.FAILED);
            state.setError(error);
            stateRepository.save(state);
            
            StateEvent event = new StateEvent(EventType.ERROR, OrderStatus.FAILED, error);
            eventRepository.save(state.getOrderId(), event);
            
            requestMetrics.recordRequestFailed();
        });
    }

    private void sendBackgroundRequest() {
        int price = ThreadLocalRandom.current().nextInt(1000);
        int stock = ThreadLocalRandom.current().nextInt(1000);
        String orderId = "bg-" + UUID.randomUUID().toString().substring(0, 8);

        PriceAndStockRequest payload = new PriceAndStockRequest(orderId, price, stock);
        
        // Reuse the main process method to ensure batching logic applies to background load too
        processPriceAndStock(payload);
    }

    /**
     * Start continuous load generation at specified rate (requests per second).
     * @param requestsPerSecond target rate (1-1000)
     * @return status message with load configuration
     */
    public Map<String, Object> startLoad(int requestsPerSecond) {
        if (requestsPerSecond < 1 || requestsPerSecond > 1000) {
            throw new ValidationException("Rate must be between 1 and 1000 requests/second");
        }
        
        stopLoad(); // Stop any existing load
        
        currentRate.set(requestsPerSecond);
        totalGenerated.set(0);
        
        // Calculate interval in microseconds for precise timing
        long intervalMicros = 1_000_000L / requestsPerSecond;
        
        loadTask = loadExecutor.scheduleAtFixedRate(() -> {
            try {
                sendBackgroundRequest();
                totalGenerated.incrementAndGet();
            } catch (Exception e) {
                LOG.warn("Load generator error: {}", e.getMessage());
            }
        }, 0, intervalMicros, TimeUnit.MICROSECONDS);
        
        LOG.info("Load generator STARTED at {} req/s", requestsPerSecond);
        
        return Map.of(
            "status", "started",
            "rate", requestsPerSecond,
            "message", "Generating " + requestsPerSecond + " requests/second"
        );
    }
    
    /**
     * Stop the load generator.
     * @return status with total requests generated
     */
    public Map<String, Object> stopLoad() {
        if (loadTask != null) {
            loadTask.cancel(false);
            loadTask = null;
        }
        
        int rate = currentRate.getAndSet(0);
        long generated = totalGenerated.get();
        
        if (rate > 0) {
            LOG.info("Load generator STOPPED. Generated {} requests", generated);
        }
        
        return Map.of(
            "status", "stopped",
            "totalGenerated", generated,
            "previousRate", rate
        );
    }
    
    /**
     * Get current load generator status.
     * @return current status including rate and count
     */
    public Map<String, Object> getLoadStatus() {
        boolean active = loadTask != null && !loadTask.isCancelled();
        return Map.of(
            "active", active,
            "rate", currentRate.get(),
            "totalGenerated", totalGenerated.get()
        );
    }

    public Map<String, Object> clearLocalQueue() {
        LOG.info("Clearing main-app local queue and state");
        
        int batchQueueCleared = queueRepository.size();
        queueRepository.clear();
        
        int statesCleared = stateRepository.count();
        eventRepository.deleteAll();
        // Note: Deleting all states will cascade delete all events and queue entries
        stateRepository.findAll().forEach(state -> stateRepository.delete(state.getOrderId()));
        
        LOG.info("Cleared {} queued requests, {} request states", 
                batchQueueCleared, statesCleared);
        
        return Map.of(
                "batchQueueCleared", batchQueueCleared,
                "statesCleared", statesCleared
        );
    }

    public List<StateEvent> getHistory(String orderId) {
        // Verify order exists
        if (!stateRepository.findByOrderId(orderId).isPresent()) {
            throw new NotFoundException("Request not found: " + orderId);
        }
        return eventRepository.findByOrderId(orderId);
    }
}
