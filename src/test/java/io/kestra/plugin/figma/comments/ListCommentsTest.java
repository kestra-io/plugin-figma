package io.kestra.plugin.figma.comments;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.common.FetchType;
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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@KestraTest
class ListCommentsTest {
    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();

    @Inject
    private RunContextFactory runContextFactory;

    private ListComments.ListCommentsBuilder<?, ?> newTaskBuilder() {
        return ListComments.builder()
            .id(UUID.randomUUID().toString())
            .type(ListComments.class.getName())
            .accessToken(Property.ofValue("token"))
            .baseUrl(Property.ofValue(wireMock.getRuntimeInfo().getHttpBaseUrl()))
            .fileKey(Property.ofValue("abc123"));
    }

    @Test
    void run() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/comments"))
            .willReturn(okJson("""
                {"comments": [{"id": "1", "message": "Looks good"}, {"id": "2", "message": "Fix this"}]}
                """)));

        ListComments task = newTaskBuilder().build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getRows(), hasSize(2));
        assertThat(output.getTotal(), is(2L));
    }

    @Test
    void fetchOneReturnsOnlyTheFirstRow() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/comments"))
            .willReturn(okJson("""
                {"comments": [{"id": "1", "message": "Looks good"}, {"id": "2", "message": "Fix this"}]}
                """)));

        ListComments task = newTaskBuilder().fetchType(Property.ofValue(FetchType.FETCH_ONE)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getRow(), is(notNullValue()));
        assertThat(output.getRow().get("id"), is("1"));
        assertThat(output.getTotal(), is(2L));
        assertThat(output.getRows(), is(nullValue()));
    }

    @Test
    void fetchOneOnEmptyResultReturnsNullRow() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/comments"))
            .willReturn(okJson("""
                {"comments": []}
                """)));

        ListComments task = newTaskBuilder().fetchType(Property.ofValue(FetchType.FETCH_ONE)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getRow(), is(nullValue()));
        assertThat(output.getTotal(), is(0L));
    }

    @Test
    void storeWritesTheArrayToInternalStorage() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/comments"))
            .willReturn(okJson("""
                {"comments": [{"id": "1", "message": "Looks good"}, {"id": "2", "message": "Fix this"}]}
                """)));

        ListComments task = newTaskBuilder().fetchType(Property.ofValue(FetchType.STORE)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getUri(), is(notNullValue()));
        assertThat(output.getUri().getScheme(), is("kestra"));
        assertThat(output.getSize(), is(2L));
        assertThat(output.getTotal(), is(2L));
        assertThat(output.getRows(), is(nullValue()));
    }

    @Test
    void noneReturnsAnEmptyOutput() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/files/abc123/comments"))
            .willReturn(okJson("""
                {"comments": [{"id": "1", "message": "Looks good"}]}
                """)));

        ListComments task = newTaskBuilder().fetchType(Property.ofValue(FetchType.NONE)).build();

        RunContext runContext = runContextFactory.of(task, Map.of());

        FigmaFetchOutput output = task.run(runContext);

        assertThat(output.getRows(), is(nullValue()));
        assertThat(output.getRow(), is(nullValue()));
        assertThat(output.getUri(), is(nullValue()));
        assertThat(output.getTotal(), is(nullValue()));
    }
}
