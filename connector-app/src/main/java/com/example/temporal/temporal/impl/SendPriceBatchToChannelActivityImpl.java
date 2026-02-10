package com.example.temporal.temporal.impl;

import com.example.shared.model.BatchPriceRequest;
import com.example.shared.model.PriceRequest;
import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.model.PriceBatchItem;
import com.example.temporal.temporal.activities.SendPriceBatchToChannelActivity;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

/**
 * Activity implementation for sending batched price updates to channel-app.
 * Sends all items in a single batch request with individual callback URLs per item.
 * Channel responds with a batch response indicating accepted/failed items.
 */
@ActivityImpl
@Service
public class SendPriceBatchToChannelActivityImpl implements SendPriceBatchToChannelActivity {
    private static final Logger LOG = Workflow.getLogger(SendPriceBatchToChannelActivityImpl.class);

    private final ConnectorProperties properties;
    private final RestClient restClient;

    public SendPriceBatchToChannelActivityImpl(ConnectorProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public void sendPriceBatch(List<PriceBatchItem> items, String batchCorrelationId) {
        LOG.info("Sending price batch to channel: batchCorrelationId={}, itemCount={}",
                batchCorrelationId, items.size());

        // Convert PriceBatchItems to PriceRequests with individual callback URLs
        List<PriceRequest> priceRequests = items.stream()
                .map(item -> {
                    // Create individual callback URL for each order
                    String callbackUrl = properties.getSelfCallbackUrl() + "/" + item.orderId() + "?type=price";
                    return new PriceRequest(item.orderId(), item.price(), callbackUrl);
                })
                .toList();

        // Create batch request
        BatchPriceRequest batchRequest = new BatchPriceRequest(priceRequests);

        LOG.info("Sending batch price request: batchCorrelationId={}, itemCount={}",
                batchCorrelationId, priceRequests.size());

        restClient.post()
                .uri(properties.getChannelPriceUrl() + "/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", batchCorrelationId)
                .body(batchRequest)
                .retrieve()
                .toBodilessEntity();

        LOG.info("Batch price request sent successfully: batchCorrelationId={}, itemCount={}",
                batchCorrelationId, items.size());
    }
}
