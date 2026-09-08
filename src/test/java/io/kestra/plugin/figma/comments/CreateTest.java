package io.kestra.plugin.figma.comments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class CreateTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/files/abc123/comments"))
            .willReturn(okJson("""
                {"id": "999", "message": "Looks good"}
                """)));

        Create task = Create.builder()
            .id(UUID.randomUUID().toString())
            .type(Create.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .message(Property.ofValue("Looks good"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        Create.Output output = task.run(runContext);

        assertThat(output.getId(), is("999"));
    }
}
