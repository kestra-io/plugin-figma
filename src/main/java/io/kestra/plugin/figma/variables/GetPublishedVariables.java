package io.kestra.plugin.figma.variables;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.common.FetchType;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.figma.AbstractFigmaTask;
import io.kestra.plugin.figma.FigmaApi;
import io.kestra.plugin.figma.FigmaApiException;
import io.kestra.plugin.figma.FigmaFetchOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get the published variables and variable collections of a Figma file",
    description = """
        Calls the Figma `GET /v1/files/:file_key/variables/published` endpoint. **Requires a Figma \
        Enterprise organization plan with variables enabled** — a 403 response is mapped to a \
        dedicated error explaining that requirement, since it's the most common cause on non-Enterprise \
        orgs. If you're not on an Enterprise plan, read/write variables from a client-side Figma plugin \
        instead."""
)
@Plugin(
    examples = {
        @Example(
            title = "Get the published variables of a Figma file (Enterprise plan required)",
            full = true,
            code = """
                id: figma_get_published_variables
                namespace: company.team

                tasks:
                  - id: get_published_variables
                    type: io.kestra.plugin.figma.variables.GetPublishedVariables
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                """
        )
    }
)
public class GetPublishedVariables extends AbstractFigmaTask implements RunnableTask<FigmaFetchOutput> {
    @NotNull
    @Schema(title = "The Figma file key", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @NotNull
    @Schema(title = "How to handle the fetched variables")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH_ONE);

    @Override
    public FigmaFetchOutput run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));

        JsonNode response;
        try {
            response = this.get(runContext, "/files/" + FigmaApi.encodePathSegment(rFileKey) + "/variables/published");
        } catch (FigmaApiException e) {
            throw this.enterprisePlanError(e);
        }

        JsonNode meta = response.path("meta");

        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH_ONE);

        return this.fetchOutput(runContext, meta, rFetchType);
    }
}
