package io.kestra.plugin.figma.files;

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
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ExportImageTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    private ExportImage.ExportImageBuilder<?, ?> newTaskBuilder() {
        return ExportImage.builder()
            .id(UUID.randomUUID().toString())
            .type(ExportImage.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"))
            .nodeIds(Property.ofValue(List.of("1:2")));
    }

    @Test
    void run() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/images/abc123"))
            .willReturn(okJson("""
                {"err": null, "images": {"1:2": "%s/downloads/1-2.png"}}
                """.formatted(wireMock.getRuntimeInfo().getHttpBaseUrl()))));

        wireMock.stubFor(get(urlPathEqualTo("/downloads/1-2.png"))
            .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 2, 3})));

        ExportImage task = newTaskBuilder().build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        ExportImage.Output output = task.run(runContext);

        assertThat(output.getImages(), aMapWithSize(1));
        assertThat(output.getImages().get("1:2").getScheme(), is("kestra"));
    }

    @Test
    void emptyNodeIds() throws Exception {
        ExportImage task = newTaskBuilder().nodeIds(Property.ofValue(List.of())).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("nodeIds"));
    }

    @Test
    void validScaleIsForwardedToTheApi() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/images/abc123"))
            .willReturn(okJson("""
                {"err": null, "images": {"1:2": "%s/downloads/scaled-1-2.png"}}
                """.formatted(wireMock.getRuntimeInfo().getHttpBaseUrl()))));

        wireMock.stubFor(get(urlPathEqualTo("/downloads/scaled-1-2.png"))
            .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 2, 3})));

        ExportImage task = newTaskBuilder().scale(Property.ofValue(2.0)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        ExportImage.Output output = task.run(runContext);

        assertThat(output.getImages(), aMapWithSize(1));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/images/abc123")).withQueryParam("scale", equalTo("2.0")));
    }

    @Test
    void scaleBelowMinimumIsRejected() throws Exception {
        ExportImage task = newTaskBuilder().scale(Property.ofValue(0.001)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("scale"));
    }

    @Test
    void scaleAboveMaximumIsRejected() throws Exception {
        ExportImage task = newTaskBuilder().scale(Property.ofValue(5.0)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("scale"));
    }

    @Test
    void dashFormNodeIdResolvesAgainstColonFormResponseKey() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/images/abc123"))
            .willReturn(okJson("""
                {"err": null, "images": {"1:2": "%s/downloads/1-2.png"}}
                """.formatted(wireMock.getRuntimeInfo().getHttpBaseUrl()))));

        wireMock.stubFor(get(urlPathEqualTo("/downloads/1-2.png"))
            .willReturn(aResponse().withStatus(200).withBody(new byte[]{1, 2, 3})));

        ExportImage task = newTaskBuilder().nodeIds(Property.ofValue(List.of("1-2"))).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        ExportImage.Output output = task.run(runContext);

        assertThat(output.getImages(), aMapWithSize(1));
        assertThat(output.getImages().get("1-2").getScheme(), is("kestra"));
    }

    @Test
    void partialFailure() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/images/abc123"))
            .willReturn(okJson("""
                {"err": null, "images": {"1:2": null}}
                """)));

        ExportImage task = newTaskBuilder().build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        IllegalStateException e = assertThrows(IllegalStateException.class, () -> task.run(runContext));
        assertThat(e.getMessage(), containsString("1:2"));
    }
}
