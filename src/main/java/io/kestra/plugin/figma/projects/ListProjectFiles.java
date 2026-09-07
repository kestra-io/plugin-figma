package io.kestra.plugin.figma.projects;

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
import io.kestra.plugin.figma.FigmaFetchOutput;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "List the files of a Figma project.",
    description = "Calls the Figma `GET /v1/projects/:project_id/files` endpoint."
)
@Plugin(
    examples = {
        @Example(
            title = "List all files of a Figma project.",
            full = true,
            code = """
                id: figma_list_project_files
                namespace: company.team

                tasks:
                  - id: list_project_files
                    type: io.kestra.plugin.figma.projects.ListProjectFiles
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    projectId: "987654321"
                """
        )
    }
)
public class ListProjectFiles extends AbstractFigmaTask implements RunnableTask<FigmaFetchOutput> {
    @NotNull
    @Schema(title = "The Figma project ID.")
    @PluginProperty(group = "main")
    private Property<String> projectId;

    @Schema(title = "Whether to include branch metadata in the response.", description = "This plugin passes `branchData` through as-is; it does not resolve or diff branches.")
    @PluginProperty(group = "processing")
    private Property<Boolean> branchData;

    @NotNull
    @Schema(title = "How to handle the fetched files.")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public FigmaFetchOutput run(RunContext runContext) throws Exception {
        String rProjectId = runContext.render(this.projectId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `projectId` property"));

        Map<String, String> params = new LinkedHashMap<>();
        runContext.render(this.branchData).as(Boolean.class).ifPresent(b -> params.put("branch_data", String.valueOf(b)));

        JsonNode response = this.get(runContext, "/projects/" + rProjectId + "/files" + FigmaApi.queryString(params));
        JsonNode files = response.path("files");

        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        return this.fetchOutput(runContext, files, rFetchType);
    }
}
