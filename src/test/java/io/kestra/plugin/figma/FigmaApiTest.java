package io.kestra.plugin.figma;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.figma.comments.List;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class FigmaApiTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void rateLimitIsMappedToAnActionableError() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123"))
            .willReturn(aResponse()
                .withStatus(429)
                .withHeader("Retry-After", "30")
                .withBody("{\"err\": \"Too Many Requests\", \"status\": 429}")));

        // any task is enough to obtain a RunContext; FigmaApi.request is the shared entry point
        // used by every task and the trigger, so it's tested directly here rather than per-task.
        List task = List.builder()
            .id(UUID.randomUUID().toString())
            .type(List.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaApiException e = assertThrows(FigmaApiException.class, () -> FigmaApi.request(
            runContext,
            wireMock.getRuntimeInfo().getHttpBaseUrl(),
            "token",
            "GET",
            "/files/abc123",
            null
        ));

        assertThat(e.getStatusCode(), is(429));
        assertThat(e.getMessage(), containsString("rate limit"));
        assertThat(e.getMessage(), containsString("Retry after 30 seconds"));
    }
}
