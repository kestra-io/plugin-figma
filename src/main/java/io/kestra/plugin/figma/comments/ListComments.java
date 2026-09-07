package io.kestra.plugin.figma.comments;

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
    title = "List the comments on a Figma file",
    description = "Calls the Figma `GET /v1/files/:key/comments` endpoint."
)
@Plugin(
    examples = {
        @Example(
            title = "List all comments on a Figma file",
            full = true,
            code = """
                id: figma_list_comments
                namespace: company.team

                tasks:
                  - id: list_comments
                    type: io.kestra.plugin.figma.comments.ListComments
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                """
        )
    }
)
public class ListComments extends AbstractFigmaTask implements RunnableTask<FigmaFetchOutput> {
    @NotNull
    @Schema(title = "The Figma file key", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @Schema(title = "Whether to return comment messages as markdown instead of plain text")
    @PluginProperty(group = "processing")
    private Property<Boolean> asMd;

    @NotNull
    @Schema(title = "How to handle the fetched comments")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.FETCH);

    @Override
    public FigmaFetchOutput run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));

        Map<String, String> params = new LinkedHashMap<>();
        runContext.render(this.asMd).as(Boolean.class).ifPresent(b -> params.put("as_md", String.valueOf(b)));

        JsonNode response = this.get(runContext, "/files/" + FigmaApi.encodePathSegment(rFileKey) + "/comments" + FigmaApi.queryString(params));
        JsonNode comments = response.path("comments");

        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.FETCH);

        return this.fetchOutput(runContext, comments, rFetchType);
    }
}
