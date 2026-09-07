package io.kestra.plugin.figma.comments;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.figma.AbstractFigmaTask;
import io.kestra.plugin.figma.FigmaApi;
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
    title = "Create a comment on a Figma file",
    description = "Calls the Figma `POST /v1/files/:key/comments` endpoint. Provide `commentId` to reply to an existing comment, or `clientMeta` to pin the comment to a canvas position or a node."
)
@Plugin(
    examples = {
        @Example(
            title = "Post a comment pinned to a canvas position on a Figma file",
            full = true,
            code = """
                id: figma_create_comment
                namespace: company.team

                tasks:
                  - id: create_comment
                    type: io.kestra.plugin.figma.comments.CreateComment
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                    message: "This flow finished the design review."
                    clientMeta:
                      x: 100
                      y: 200
                """
        )
    }
)
public class CreateComment extends AbstractFigmaTask implements RunnableTask<CreateComment.Output> {
    @NotNull
    @Schema(title = "The Figma file key", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @NotNull
    @Schema(title = "The comment message")
    @PluginProperty(group = "main")
    private Property<String> message;

    @Schema(title = "The ID of the comment to reply to", description = "Omit to create a new top-level comment.")
    @PluginProperty(group = "main")
    private Property<String> commentId;

    @Schema(
        title = "The position to pin the comment to",
        description = "Either a canvas position (`x`/`y`), or a node anchor (`node_id`, optionally with `node_offset`). See the Figma API documentation for the full `client_meta` shape."
    )
    @PluginProperty(group = "main")
    private Property<Map<String, Object>> clientMeta;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));
        String rMessage = runContext.render(this.message).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `message` property"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("message", rMessage);
        runContext.render(this.commentId).as(String.class).ifPresent(id -> body.put("comment_id", id));
        Map<String, Object> rClientMeta = runContext.render(this.clientMeta).asMap(String.class, Object.class);
        if (!rClientMeta.isEmpty()) {
            body.put("client_meta", rClientMeta);
        }

        JsonNode response = this.post(runContext, "/files/" + FigmaApi.encodePathSegment(rFileKey) + "/comments", body);

        String id = response.path("id").asText(null);
        if (id == null) {
            throw new IllegalStateException("Figma did not return an `id` for the created comment — unexpected response shape: " + response);
        }

        return Output.builder().id(id).build();
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The ID of the created comment")
        private final String id;
    }
}
