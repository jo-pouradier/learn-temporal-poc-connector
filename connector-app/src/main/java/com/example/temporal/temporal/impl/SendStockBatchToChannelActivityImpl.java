package com.example.temporal.temporal.impl;

import com.example.shared.model.BatchStockRequest;
import com.example.shared.model.StockRequest;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.model.StockBatchItem;
import com.example.temporal.temporal.activities.SendStockBatchToChannelActivity;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Activity implementation for sending batched stock updates to channel-app.
 * Sends all items in a single batch request with individual callback URLs per item.
 * Channel responds with a batch response indicating accepted/failed items.
 */
@ActivityImpl
@Service
public class SendStockBatchToChannelActivityImpl implements SendStockBatchToChannelActivity {
    private static final Logger LOG = Workflow.getLogger(SendStockBatchToChannelActivityImpl.class);

    private final ConnectorProperties properties;
    private final RestClient restClient;

    public SendStockBatchToChannelActivityImpl(ConnectorProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void sendStockBatch(List<StockBatchItem> items, String batchCorrelationId) {
        LOG.info("Sending stock batch to channel: batchCorrelationId={}, itemCount={}",
                batchCorrelationId, items.size());

        // Convert StockBatchItems to StockRequests with individual callback URLs
        List<StockRequest> stockRequests = items.stream()
                .map(item -> {
                    // Create individual callback URL for each order
                    String callbackUrl = properties.getSelfCallbackUrl() + "/" + item.orderId() + "?type=stock";
                    return new StockRequest(item.orderId(), item.stock(), callbackUrl);
                })
                .toList();

        // Create batch request
        BatchStockRequest batchRequest = new BatchStockRequest(stockRequests);

        LOG.info("Sending batch stock request: batchCorrelationId={}, itemCount={}",
                batchCorrelationId, stockRequests.size());

        restClient.post()
                .uri(properties.getChannelStockUrl() + "/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", batchCorrelationId)
                .body(batchRequest)
                .retrieve()
                .toBodilessEntity();

        LOG.info("Batch stock request sent successfully: batchCorrelationId={}, itemCount={}",
                batchCorrelationId, items.size());
    }
}
