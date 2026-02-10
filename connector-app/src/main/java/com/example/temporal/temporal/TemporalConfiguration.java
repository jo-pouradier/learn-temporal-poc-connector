package com.example.temporal.temporal;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.temporal.client.WorkflowClient;
import io.temporal.common.reporter.MicrometerClientStatsReporter;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.spring.boot.TemporalOptionsCustomizer;
import io.temporal.worker.WorkerFactory;
import io.micrometer.core.instrument.MeterRegistry;
import com.uber.m3.tally.RootScopeBuilder;
import com.uber.m3.tally.Scope;
import com.uber.m3.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Temporal configuration with OpenTelemetry metrics integration.
 * This enables SDK-level metrics like workflow_completed, workflow_failed,
 * activity_execution_latency, etc. to be exported via OpenTelemetry.
 */
@Configuration
public class TemporalConfiguration {

    private static final Logger log = LoggerFactory.getLogger(TemporalConfiguration.class);

    @Bean
    public WorkerFactory factory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    /**
     * Customizer for WorkflowServiceStubsOptions to add OpenTelemetry metrics.
     * This integrates Temporal SDK metrics with the OpenTelemetry Java agent.
     */
    @Bean
    public TemporalOptionsCustomizer<WorkflowServiceStubsOptions.Builder> workflowServiceStubsCustomizer(
            MeterRegistry meterRegistry) {
        return new TemporalOptionsCustomizer<>() {
            @Override
            public WorkflowServiceStubsOptions.Builder customize(WorkflowServiceStubsOptions.Builder builder) {
                log.info("Configuring Temporal SDK with OpenTelemetry metrics");
                
                // Create a Micrometer-based metrics scope for Temporal SDK
                // This will export metrics through Spring Boot's MeterRegistry
                Scope scope = new RootScopeBuilder()
                        .reporter(new MicrometerClientStatsReporter(meterRegistry))
                        .reportEvery(Duration.ofSeconds(10));

                // Configure OpenTelemetry options for tracing
                return builder.setMetricsScope(scope);
            }
        };
    }
}
