package io.kestra.plugin.figma.projects;

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

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

@KestraTest
class ListProjectFilesTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void run() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/projects/456/files"))
            .willReturn(okJson("""
                {"name": "Project", "files": [{"key": "abc123", "name": "File A"}]}
                """)));

        ListProjectFiles task = ListProjectFiles.builder()
            .id(UUID.randomUUID().toString())
            .type(ListProjectFiles.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .projectId(Property.ofValue("456"))
            .build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getRows(), hasSize(1));
    }
}
