package io.kestra.plugin.figma.variables;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.figma.FigmaFetchOutput;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class GetLocalVariablesTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/variables/local"))
            .willReturn(okJson("""
                {"status": 200, "error": false, "meta": {"variables": {"VariableID:1:1": {"name": "color/primary"}}}}
                """)));

        GetLocalVariables task = GetLocalVariables.builder()
            .id(UUID.randomUUID().toString())
            .type(GetLocalVariables.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getRow(), is(notNullValue()));
        assertThat(output.getRow().get("variables"), is(notNullValue()));
    }

    @Test
    void enterprisePlanRequired() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/variables/local"))
            .willReturn(aResponse().withStatus(403).withBody("{\"err\": \"Forbidden\", \"status\": 403}")));

        GetLocalVariables task = GetLocalVariables.builder()
            .id(UUID.randomUUID().toString())
            .type(GetLocalVariables.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        Exception e = assertThrows(Exception.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("Enterprise"));
    }
}
