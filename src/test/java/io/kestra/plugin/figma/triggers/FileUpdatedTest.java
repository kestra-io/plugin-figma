package io.kestra.plugin.figma.triggers;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.DefaultRunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.figma.FigmaApiException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class FileUpdatedTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    private FileUpdated newTrigger(String triggerId) {
        return FileUpdated.builder()
            .id(triggerId)
            .type(FileUpdated.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .build();
    }

    private ConditionContext newConditionContext(FileUpdated trigger, TriggerContext triggerContext) {
        Flow flow = Flow.builder()
            .id(triggerContext.getFlowId())
            .namespace(triggerContext.getNamespace())
            .revision(1)
            .tenantId("main")
            .build();

        DefaultRunContext runContext = (DefaultRunContext) runContextFactory.of(flow, trigger);
        runContextFactory.initializer().forScheduler(runContext, triggerContext, trigger);

        return ConditionContext.builder().runContext(runContext).flow(flow).build();
    }

    private TriggerContext newTriggerContext(String flowId, String triggerId) {
        return TriggerContext.builder()
            .tenantId("main")
            .namespace("company.team")
            .flowId(flowId)
            .triggerId(triggerId)
            .date(ZonedDateTime.now())
            .build();
    }

    @Test
    void firstPollRecordsWithoutFiring() throws Exception {
        String flowId = "flow_" + UUID.randomUUID().toString().replace("-", "");
        String triggerId = "trigger_" + UUID.randomUUID().toString().replace("-", "");

        FileUpdated trigger = newTrigger(triggerId);
        TriggerContext triggerContext = newTriggerContext(flowId, triggerId);
        ConditionContext conditionContext = newConditionContext(trigger, triggerContext);

        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(okJson("""
                {"name": "Test File", "lastModified": "2024-01-01T00:00:00Z", "document": {}}
                """)));

        Optional<Execution> execution = trigger.evaluate(conditionContext, triggerContext);

        assertThat(execution.isPresent(), is(false));
    }

    @Test
    void unchangedDoesNotFire() throws Exception {
        String flowId = "flow_" + UUID.randomUUID().toString().replace("-", "");
        String triggerId = "trigger_" + UUID.randomUUID().toString().replace("-", "");

        FileUpdated trigger = newTrigger(triggerId);
        TriggerContext triggerContext = newTriggerContext(flowId, triggerId);
        ConditionContext conditionContext = newConditionContext(trigger, triggerContext);

        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(okJson("""
                {"name": "Test File", "lastModified": "2024-01-01T00:00:00Z", "document": {}}
                """)));

        // first poll: records the initial timestamp without firing
        trigger.evaluate(conditionContext, triggerContext);

        // second poll: same lastModified, still no fire
        Optional<Execution> execution = trigger.evaluate(conditionContext, triggerContext);

        assertThat(execution.isPresent(), is(false));
    }

    @Test
    void advancedLastModifiedFiresOnce() throws Exception {
        String flowId = "flow_" + UUID.randomUUID().toString().replace("-", "");
        String triggerId = "trigger_" + UUID.randomUUID().toString().replace("-", "");

        FileUpdated trigger = newTrigger(triggerId);
        TriggerContext triggerContext = newTriggerContext(flowId, triggerId);
        ConditionContext conditionContext = newConditionContext(trigger, triggerContext);

        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(okJson("""
                {"name": "Test File", "lastModified": "2024-01-01T00:00:00Z", "document": {}}
                """)));

        // first poll: records the initial timestamp without firing
        trigger.evaluate(conditionContext, triggerContext);

        wireMock.resetAll();
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(okJson("""
                {"name": "Test File", "lastModified": "2024-02-01T00:00:00Z", "document": {}}
                """)));

        Optional<Execution> execution = trigger.evaluate(conditionContext, triggerContext);

        assertThat(execution.isPresent(), is(true));

        // a re-poll with the same (now current) lastModified must not fire again
        Optional<Execution> replay = trigger.evaluate(conditionContext, triggerContext);
        assertThat(replay.isPresent(), is(false));
    }

    @Test
    void forbiddenPollFailsTheEvaluationInsteadOfStalling() {
        String flowId = "flow_" + UUID.randomUUID().toString().replace("-", "");
        String triggerId = "trigger_" + UUID.randomUUID().toString().replace("-", "");

        FileUpdated trigger = newTrigger(triggerId);
        TriggerContext triggerContext = newTriggerContext(flowId, triggerId);
        ConditionContext conditionContext = newConditionContext(trigger, triggerContext);

        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(aResponse().withStatus(403).withBody("{\"err\": \"Forbidden\", \"status\": 403}")));

        FigmaApiException e = assertThrows(FigmaApiException.class, () -> trigger.evaluate(conditionContext, triggerContext));

        assertThat(e.getStatusCode(), is(403));
        assertThat(e.getMessage(), containsString("403"));
    }

    @Test
    void notFoundPollFailsTheEvaluationInsteadOfStalling() {
        String flowId = "flow_" + UUID.randomUUID().toString().replace("-", "");
        String triggerId = "trigger_" + UUID.randomUUID().toString().replace("-", "");

        FileUpdated trigger = newTrigger(triggerId);
        TriggerContext triggerContext = newTriggerContext(flowId, triggerId);
        ConditionContext conditionContext = newConditionContext(trigger, triggerContext);

        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(aResponse().withStatus(404).withBody("{\"err\": \"Not found\", \"status\": 404}")));

        FigmaApiException e = assertThrows(FigmaApiException.class, () -> trigger.evaluate(conditionContext, triggerContext));

        assertThat(e.getStatusCode(), is(404));
        assertThat(e.getMessage(), containsString("404"));
    }
}
