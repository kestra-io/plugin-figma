package io.kestra.plugin.figma.comments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.plugin.figma.FigmaApiException;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class DeleteCommentTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        wireMock.stubFor(delete(urlPathEqualTo("/files/abc123/comments/999"))
            .willReturn(aResponse().withStatus(200).withBody("{}")));

        DeleteComment task = DeleteComment.builder()
            .id(UUID.randomUUID().toString())
            .type(DeleteComment.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .commentId(Property.ofValue("999"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        assertThat(task.run(runContext), nullValue());
    }

    @Test
    void notFound() throws Exception {
        wireMock.stubFor(delete(urlPathEqualTo("/files/abc123/comments/999"))
            .willReturn(aResponse().withStatus(404).withBody("{\"err\": \"Not found\"}")));

        DeleteComment task = DeleteComment.builder()
            .id(UUID.randomUUID().toString())
            .type(DeleteComment.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .commentId(Property.ofValue("999"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaApiException e = assertThrows(FigmaApiException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("not found"));
    }
}
