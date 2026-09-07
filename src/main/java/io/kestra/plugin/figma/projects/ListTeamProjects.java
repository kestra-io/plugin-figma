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
    title = "List the projects of a Figma team",
    description = "Calls the Figma `GET /v1/teams/:team_id/projects` endpoint."
)
@Plugin(
    examples = {
        @Example(
            title = "List all projects of a Figma team",
            full = true,
            code = """
                id: figma_list_team_projects
                namespace: company.team

                tasks:
                  - id: list_team_projects
                    type: io.kestra.plugin.figma.projects.ListTeamProjects
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    teamId: "123456789"
                """
        )
    }
)
public class ListTeamProjects extends AbstractFigmaTask implements RunnableTask<FigmaFetchOutput> {
    @NotNull
    @Schema(title = "The Figma team ID")
    @PluginProperty(group = "main")
    private Property<String> teamId;

    @NotNull
    @Schema(title = "How to handle the fetched projects")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public FigmaFetchOutput run(RunContext runContext) throws Exception {
        String rTeamId = runContext.render(this.teamId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `teamId` property"));

        JsonNode response = this.get(runContext, "/teams/" + rTeamId + "/projects");
        JsonNode projects = response.path("projects");

        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        return this.fetchOutput(runContext, projects, rFetchType);
    }
}
