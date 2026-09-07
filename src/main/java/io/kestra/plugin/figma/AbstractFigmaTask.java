package io.kestra.plugin.figma;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.Task;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
public abstract class AbstractFigmaTask extends Task {
    @Schema(
        title = "Figma access token",
        description = """
            A personal access token generated from your Figma account settings (Account Settings > \
            Personal access tokens), scoped to what the task needs to do. A valid OAuth 2.0 access \
            token obtained through your own authorization flow also works — this plugin does not \
            implement the OAuth flow itself, only PAT-style bearer tokens passed as-is."""
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @NotNull
    protected Property<String> accessToken;

    @Schema(
        title = "Figma API base URL",
        description = "Defaults to the public Figma REST API. Override only to point at a self-hosted proxy or a test server."
    )
    @PluginProperty(group = "connection")
    @Builder.Default
    protected Property<String> baseUrl = Property.ofValue(FigmaApi.DEFAULT_BASE_URL);

    protected String renderAccessToken(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(this.accessToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `accessToken` property"));
    }

    protected String renderBaseUrl(RunContext runContext) throws IllegalVariableEvaluationException {
        return runContext.render(this.baseUrl).as(String.class).orElse(FigmaApi.DEFAULT_BASE_URL);
    }

    protected JsonNode get(RunContext runContext, String path) throws IllegalVariableEvaluationException, IOException {
        return FigmaApi.request(runContext, renderBaseUrl(runContext), renderAccessToken(runContext), "GET", path, null);
    }

    protected JsonNode post(RunContext runContext, String path, Object body) throws IllegalVariableEvaluationException, IOException {
        return FigmaApi.request(runContext, renderBaseUrl(runContext), renderAccessToken(runContext), "POST", path, body);
    }

    protected JsonNode delete(RunContext runContext, String path) throws IllegalVariableEvaluationException, IOException {
        return FigmaApi.request(runContext, renderBaseUrl(runContext), renderAccessToken(runContext), "DELETE", path, null);
    }

    /**
     * The three `variables.*` endpoints are Figma Enterprise-org only; a 403 there almost always
     * means the org isn't Enterprise rather than a token/scope issue, so it deserves its own message.
     */
    protected FigmaApiException enterprisePlanError(FigmaApiException e) {
        if (e.getStatusCode() == 403) {
            return new FigmaApiException(
                "Figma API 403: this endpoint requires a Figma Enterprise organization plan with variables " +
                    "enabled. If you're not on an Enterprise plan, read/write variables from a client-side " +
                    "Figma plugin instead. Original error: " + e.getMessage(),
                403
            );
        }

        return e;
    }

    /**
     * Turns a JSON node plus a {@link FetchType} into the shared {@link FigmaFetchOutput} shape.
     * Array nodes (comments/projects/files listings) produce `rows`/`total`; object nodes
     * (a file document, a variables `meta` block) produce a single `row`.
     */
    protected FigmaFetchOutput fetchOutput(RunContext runContext, JsonNode node, FetchType fetchType) throws IOException {
        FigmaFetchOutput.FigmaFetchOutputBuilder output = FigmaFetchOutput.builder();

        if (fetchType == null || fetchType == FetchType.NONE) {
            return output.build();
        }

        if (node.isArray()) {
            List<Object> rows = JacksonMapper.ofJson().convertValue(node, JacksonMapper.LIST_TYPE_REFERENCE);
            long total = rows.size();

            return switch (fetchType) {
                case FETCH -> output.rows(rows).size(total).total(total).build();
                case FETCH_ONE -> output.row(rows.isEmpty() ? null : toMap(rows.getFirst())).total(total).build();
                case STORE -> output.uri(store(runContext, node)).size(total).total(total).build();
                case NONE -> output.build();
            };
        }

        Map<String, Object> row = JacksonMapper.ofJson().convertValue(node, JacksonMapper.MAP_TYPE_REFERENCE);

        return switch (fetchType) {
            case FETCH, FETCH_ONE -> output.row(row).build();
            case STORE -> output.uri(store(runContext, node)).size(1L).build();
            case NONE -> output.build();
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> toMap(Object row) {
        return (Map<String, Object>) row;
    }

    private URI store(RunContext runContext, JsonNode node) throws IOException {
        File tempFile = runContext.workingDir().createTempFile(".json").toFile();
        JacksonMapper.ofJson().writeValue(tempFile, node);

        return runContext.storage().putFile(tempFile);
    }
}
