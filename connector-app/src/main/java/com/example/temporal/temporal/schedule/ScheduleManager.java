package com.example.temporal.temporal.schedule;

import com.example.temporal.config.ConnectorProperties;
import com.example.temporal.repository.PriceQueueRepository;
import com.example.temporal.repository.StockQueueRepository;
import com.example.temporal.temporal.workflow.SendPriceToChannelWorkflow;
import com.example.temporal.temporal.workflow.SendStockToChannelWorkflow;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.*;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


@Component
public class ScheduleManager {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduleManager.class);
    public static final String PRICE_SCHEDULE_ID = "price-queue-schedule";
    public static final String STOCK_SCHEDULE_ID = "stock-queue-schedule";

    private final WorkflowClient client;
    private final ScheduleClient scheduleClient;
    private final ConnectorProperties properties;
    private final PriceQueueRepository priceQueueRepository;
    private final StockQueueRepository stockQueueRepository;

    public ScheduleManager(WorkflowClient client, ScheduleClient scheduleClient, ConnectorProperties properties, PriceQueueRepository priceQueueRepository, StockQueueRepository stockQueueRepository) {
        this.client = client;
        this.scheduleClient = scheduleClient;
        this.properties = properties;
        this.priceQueueRepository = priceQueueRepository;
        this.stockQueueRepository = stockQueueRepository;
    }

    @PostConstruct
    public void initialize() {
        createOrUpdateSchedules();
    }

    private void createOrUpdateSchedules() {
        Duration interval = properties.getQueueProcessingInterval();
        createOrUpdateSchedule(interval, SendPriceToChannelWorkflow.class, PRICE_SCHEDULE_ID, "send-price-to-channel");
        createOrUpdateSchedule(interval, SendStockToChannelWorkflow.class, STOCK_SCHEDULE_ID, "send-stock-to-channel");
    }

    private <T> void createOrUpdateSchedule(Duration interval, Class<T> clazz, String scheduleId, String queue) {
        Schedule schedule = Schedule.newBuilder()
                .setAction(
                        ScheduleActionStartWorkflow.newBuilder()
                                .setWorkflowType(clazz)
                                .setOptions(WorkflowOptions.newBuilder()
                                        .setWorkflowId(UUID.randomUUID().toString())
                                        .setTaskQueue(queue)
                                        .build())
                                .build()
                )
                .setSpec(
                        ScheduleSpec.newBuilder()
                                .setIntervals(List.of(new ScheduleIntervalSpec(interval)))
                                .build()
                )
                .setPolicy(
                        SchedulePolicy.newBuilder()
                                .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_ALLOW_ALL)
                                .build()
                )
                .build();

        var schedules = scheduleClient.listSchedules();
        if (schedules.noneMatch(s -> Objects.equals(s.getScheduleId(), scheduleId))) {
            scheduleClient.createSchedule(scheduleId, schedule, ScheduleOptions.newBuilder().build());
        }

    }

    public synchronized void reschedule(String scheduleId, Duration interval) {
        LOG.info("Rescheduling with new interval: {}ms", properties.getQueueProcessingInterval());
        ScheduleHandle handle = scheduleClient.getHandle(scheduleId);
        handle.update( scheduleUpdateInput -> {
            Schedule.Builder builder = Schedule.newBuilder(scheduleUpdateInput.getDescription().getSchedule())
                    // update interval
                    .setSpec(
                            ScheduleSpec.newBuilder()
                                    .setIntervals(List.of(new ScheduleIntervalSpec(interval)))
                                    .build()
                    );
            return new ScheduleUpdate(builder.build());
        });
    }


}
