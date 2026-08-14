package com.langfuse.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.extension.TestWatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.langfuse.api.LangfuseApi;
import com.langfuse.api.model.OpentelemetryExportTracesRequest;
import com.langfuse.api.model.OtelAttribute;
import com.langfuse.api.model.OtelAttributeValue;
import com.langfuse.api.model.OtelResource;
import com.langfuse.api.model.OtelResourceSpan;
import com.langfuse.api.model.OtelScope;
import com.langfuse.api.model.OtelScopeSpan;
import com.langfuse.api.model.OtelSpan;
import com.langfuse.api.opentelemetry.OpentelemetryApi.APIOpentelemetryExportTracesRequest;
import com.langfuse.testcontainers.LangfuseContainer;

/**
 * Abstract base class for Langfuse client integration tests.
 *
 * <p>Uses the Testcontainers singleton container pattern to start a single
 * {@link LangfuseContainer} instance that is shared across all test classes.
 * The container starts once per JVM and is cleaned up automatically by Ryuk.
 *
 * @author Eric Deandrea
 * @see <a href="https://testcontainers.com/guides/testcontainers-container-lifecycle/#_using_singleton_containers">Singleton Containers</a>
 */
abstract class AbstractLangfuseClientTest {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractLangfuseClientTest.class);

    static LangfuseContainer langfuse = new LangfuseContainer();
    static LangfuseApi client;

    static {
        langfuse.start();
        client = createClient();
    }

    @RegisterExtension
    TestWatcher watcher = new TestWatcher() {
        @Override
        public void testFailed(ExtensionContext context, Throwable cause) {
            LOG.error("Test {}.{} failed: {}",
                    context.getTestClass().map(Class::getName).orElse(""),
                    context.getTestMethod().map(Method::getName).orElse(""),
                    Optional.ofNullable(cause).map(Throwable::getMessage).orElse(""));

            langfuse.getAllLogs().forEach((container, logs) ->
                    LOG.error("=== {} ===\n{}", container, logs));
        }
    };

    /**
     * Ingests a trace with a single root span via the OTel endpoint.
     *
     * @param traceId   32-char hex trace ID
     * @param spanId    16-char hex span ID
     * @param traceName the trace name (set as {@code langfuse.trace.name} resource attribute)
     */
    static void ingestTrace(String traceId, String spanId, String traceName) {
        ingestTrace(traceId, spanId, traceName, Map.of());
    }

    /**
     * Ingests a trace with a single root span and additional resource attributes via the OTel endpoint.
     *
     * @param traceId         32-char hex trace ID
     * @param spanId          16-char hex span ID
     * @param traceName       the trace name (set as {@code langfuse.trace.name} resource attribute)
     * @param traceAttributes additional {@code langfuse.trace.*} attributes (e.g. {@code user.id}, {@code session.id})
     */
    static void ingestTrace(String traceId, String spanId, String traceName, Map<String, String> traceAttributes) {
        var nowNanos = String.valueOf(System.currentTimeMillis() * 1_000_000L);

        var span = OtelSpan.builder()
                .traceId(traceId)
                .spanId(spanId)
                .name("root-span")
                .kind(1)
                .startTimeUnixNano(nowNanos)
                .endTimeUnixNano(String.valueOf(Long.parseLong(nowNanos) + 1_000_000_000L))
                .build();

        ingestTraceWithSpans(traceName, traceAttributes, span);
    }

    /**
     * Ingests a trace with the given spans via the OTel endpoint.
     *
     * @param traceName       the trace name (set as {@code langfuse.trace.name} resource attribute)
     * @param traceAttributes additional {@code langfuse.trace.*} attributes
     * @param spans           one or more spans to include
     */
    static void ingestTraceWithSpans(String traceName, Map<String, String> traceAttributes, OtelSpan... spans) {
        var attributes = new ArrayList<OtelAttribute>();

        attributes.add(OtelAttribute.builder()
                .key("langfuse.trace.name")
                .value(OtelAttributeValue.builder()
                        .stringValue(traceName)
                        .build())
                .build());

        traceAttributes.forEach((key, value) ->
                attributes.add(OtelAttribute.builder()
                        .key("langfuse.trace." + key)
                        .value(OtelAttributeValue.builder()
                                .stringValue(value)
                                .build())
                        .build()));

        var resourceSpan = OtelResourceSpan.builder()
                .resource(OtelResource.builder()
                        .attributes(attributes)
                        .build())
                .scopeSpans(List.of(OtelScopeSpan.builder()
                        .scope(OtelScope.builder()
                                .name("test")
                                .build())
                        .spans(List.of(spans))
                        .build()))
                .build();

        var response = client.opentelemetry().opentelemetryExportTraces(
                APIOpentelemetryExportTracesRequest.newBuilder()
                        .opentelemetryExportTracesRequest(OpentelemetryExportTracesRequest.builder()
                                .resourceSpans(List.of(resourceSpan))
                                .build())
                        .build());

        assertThat(response)
                .isNotNull();
    }

    /**
     * Creates a new {@link LangfuseApi} client configured to connect to the shared container.
     *
     * @return a configured client instance
     */
    static LangfuseApi createClient() {
        return LangfuseApi.builder()
                .username(langfuse.getPublicKey())
                .password(langfuse.getSecretKey())
                .url(langfuse.getLangfuseUrl())
                .logRequests()
                .logResponses()
                .prettyPrint()
                .build();
    }
}
