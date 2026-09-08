package io.kestra.plugin.figma.variables;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class UpdateVariablesTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/files/abc123/variables"))
            .willReturn(okJson("""
                {"status": 200, "error": false, "meta": {"tempIdToRealId": {"temp_collection_1": "VariableCollectionId:1:1"}}}
                """)));

        UpdateVariables task = UpdateVariables.builder()
            .id(UUID.randomUUID().toString())
            .type(UpdateVariables.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .variableCollections(Property.ofValue(List.of(Map.of("action", "CREATE", "id", "temp_collection_1", "name", "Tokens"))))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        UpdateVariables.Output output = task.run(runContext);

        assertThat(output.getTempIdToRealId().get("temp_collection_1"), is("VariableCollectionId:1:1"));
    }

    @Test
    void allEmptyChangeListsFailFast() throws Exception {
        UpdateVariables task = UpdateVariables.builder()
            .id(UUID.randomUUID().toString())
            .type(UpdateVariables.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("variableCollections"));
    }

    @Test
    void enterprisePlanRequired() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/files/abc123/variables"))
            .willReturn(aResponse().withStatus(403).withBody("{\"err\": \"Forbidden\", \"status\": 403}")));

        UpdateVariables task = UpdateVariables.builder()
            .id(UUID.randomUUID().toString())
            .type(UpdateVariables.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .variableCollections(Property.ofValue(List.of(Map.of("action", "CREATE", "id", "temp_collection_1", "name", "Tokens"))))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        Exception e = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("Enterprise"));
    }
}
