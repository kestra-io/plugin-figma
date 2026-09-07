package io.kestra.plugin.figma.comments;

import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.models.tasks.VoidOutput;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.figma.AbstractFigmaTask;
import io.kestra.plugin.figma.FigmaApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
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
    title = "Delete a comment on a Figma file.",
    description = "Calls the Figma `DELETE /v1/files/:key/comments/:comment_id` endpoint."
)
@Plugin(
    examples = {
        @Example(
            title = "Delete a comment on a Figma file.",
            full = true,
            code = """
                id: figma_delete_comment
                namespace: company.team

                tasks:
                  - id: delete_comment
                    type: io.kestra.plugin.figma.comments.DeleteComment
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                    commentId: "1234567890"
                """
        )
    }
)
public class DeleteComment extends AbstractFigmaTask implements RunnableTask<VoidOutput> {
    @NotNull
    @Schema(title = "The Figma file key.", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @NotNull
    @Schema(title = "The ID of the comment to delete.")
    @PluginProperty(group = "main")
    private Property<String> commentId;

    @Override
    public VoidOutput run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));
        String rCommentId = runContext.render(this.commentId).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `commentId` property"));

        try {
            this.delete(runContext, "/files/" + rFileKey + "/comments/" + rCommentId);
        } catch (FigmaApiException e) {
            if (e.getStatusCode() == 404) {
                throw new FigmaApiException(
                    "Figma API 404: comment '" + rCommentId + "' not found in file '" + rFileKey +
                        "' — check the comment ID and that it hasn't already been deleted.",
                    404
                );
            }

            throw e;
        }

        return null;
    }
}
