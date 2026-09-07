package io.kestra.plugin.figma.files;

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
import java.util.List;
import java.util.Map;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Get a Figma file's document structure",
    description = "Calls the Figma `GET /v1/files/:key` endpoint. File documents can be very large, so `fetchType` defaults to `STORE`."
)
@Plugin(
    examples = {
        @Example(
            title = "Store a Figma file's document structure to internal storage",
            full = true,
            code = """
                id: figma_get_file
                namespace: company.team

                tasks:
                  - id: get_file
                    type: io.kestra.plugin.figma.files.GetFile
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                """
        )
    }
)
public class GetFile extends AbstractFigmaTask implements RunnableTask<FigmaFetchOutput> {
    @NotNull
    @Schema(title = "The Figma file key", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @Schema(title = "A list of node IDs to limit the traversal to", description = "By default the whole document is returned.")
    @PluginProperty(group = "processing")
    private Property<List<String>> ids;

    @Schema(title = "Depth of the node tree to traverse", description = "For example `1` returns only the root and its direct children.")
    @PluginProperty(group = "processing")
    private Property<Integer> depth;

    @Schema(title = "Geometry data to include", description = "Set to `paths` to export vector data.")
    @PluginProperty(group = "processing")
    private Property<String> geometry;

    @Schema(title = "A specific file version ID to retrieve", description = "By default the latest version is returned.")
    @PluginProperty(group = "source")
    private Property<String> fileVersion;

    @Schema(title = "Whether to include branch metadata in the response", description = "This plugin passes `branchData` through as-is; it does not resolve or diff branches.")
    @PluginProperty(group = "source")
    private Property<Boolean> branchData;

    @NotNull
    @Schema(
        title = "How to handle the fetched file document",
        description = "Defaults to `STORE` because file documents can be large — storing to internal storage avoids loading the whole JSON tree into the execution context. Use `FETCH`/`FETCH_ONE` only for small files."
    )
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<FetchType> fetchType = Property.ofValue(FetchType.STORE);

    @Override
    public FigmaFetchOutput run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));

        Map<String, String> params = new LinkedHashMap<>();

        List<String> rIds = runContext.render(this.ids).asList(String.class);
        if (!rIds.isEmpty()) {
            params.put("ids", String.join(",", rIds));
        }

        runContext.render(this.depth).as(Integer.class).ifPresent(d -> params.put("depth", String.valueOf(d)));
        runContext.render(this.geometry).as(String.class).ifPresent(g -> params.put("geometry", g));
        runContext.render(this.fileVersion).as(String.class).ifPresent(v -> params.put("version", v));
        runContext.render(this.branchData).as(Boolean.class).ifPresent(b -> params.put("branch_data", String.valueOf(b)));

        JsonNode file = this.get(runContext, "/files/" + rFileKey + FigmaApi.queryString(params));

        FetchType rFetchType = runContext.render(this.fetchType).as(FetchType.class).orElse(FetchType.STORE);

        return this.fetchOutput(runContext, file, rFetchType);
    }
}
