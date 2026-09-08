package io.kestra.plugin.figma.variables;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTask;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.figma.AbstractFigmaTask;
import io.kestra.plugin.figma.FigmaApi;
import io.kestra.plugin.figma.FigmaApiException;
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
    title = "Create, update, or delete Figma variables and variable collections in bulk",
    description = """
        Calls the Figma `POST /v1/files/:file_key/variables` endpoint with the bulk-write envelope \
        (`variableCollections`, `variableModes`, `variables`, `variableModeValues`), matching the \
        Figma API's own field names and semantics — see the Figma REST API documentation for the \
        shape of each entry, including the temporary-ID convention (`action: CREATE` entries use a \
        client-chosen temporary ID that Figma remaps to a real ID in the response). **Requires a \
        Figma Enterprise organization plan with variables enabled** — a 403 response is mapped to a \
        dedicated error explaining that requirement. If you're not on an Enterprise plan, read/write \
        variables from a client-side Figma plugin instead."""
)
@Plugin(
    examples = {
        @Example(
            title = "Create a variable collection and a variable in a Figma file (Enterprise plan required)",
            full = true,
            code = """
                id: figma_update_variables
                namespace: company.team

                tasks:
                  - id: update_variables
                    type: io.kestra.plugin.figma.variables.UpdateVariables
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                    variableCollections:
                      - action: CREATE
                        id: "temp_collection_1"
                        name: "Design Tokens"
                    variables:
                      - action: CREATE
                        id: "temp_variable_1"
                        name: "primary-color"
                        variableCollectionId: "temp_collection_1"
                        resolvedType: COLOR
                """
        )
    }
)
public class UpdateVariables extends AbstractFigmaTask implements RunnableTask<UpdateVariables.Output> {
    @NotNull
    @Schema(title = "The Figma file key", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @Schema(title = "Variable collections to create, update, or delete", description = "See the Figma API documentation for the `VariableCollection` change entry shape.")
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> variableCollections;

    @Schema(title = "Variable modes to create, update, or delete", description = "See the Figma API documentation for the `VariableMode` change entry shape.")
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> variableModes;

    @Schema(title = "Variables to create, update, or delete", description = "See the Figma API documentation for the `Variable` change entry shape.")
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> variables;

    @Schema(title = "Variable values to set per mode", description = "See the Figma API documentation for the `VariableModeValue` change entry shape.")
    @PluginProperty(group = "main")
    private Property<List<Map<String, Object>>> variableModeValues;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));

        Map<String, Object> body = new LinkedHashMap<>();
        putIfNotEmpty(body, "variableCollections", runContext.render(this.variableCollections).asList(Map.class));
        putIfNotEmpty(body, "variableModes", runContext.render(this.variableModes).asList(Map.class));
        putIfNotEmpty(body, "variables", runContext.render(this.variables).asList(Map.class));
        putIfNotEmpty(body, "variableModeValues", runContext.render(this.variableModeValues).asList(Map.class));

        if (body.isEmpty()) {
            throw new IllegalArgumentException(
                "At least one of `variableCollections`, `variableModes`, `variables`, or `variableModeValues` must be non-empty — " +
                    "otherwise this task would call the Figma API with no changes and report success having changed nothing."
            );
        }

        JsonNode response;
        try {
            response = this.post(runContext, "/files/" + FigmaApi.encodePathSegment(rFileKey) + "/variables", body);
        } catch (FigmaApiException e) {
            throw this.enterprisePlanError(e);
        }

        JsonNode tempIdToRealIdNode = response.path("meta").path("tempIdToRealId");
        Map<String, String> tempIdToRealId = new LinkedHashMap<>();
        tempIdToRealIdNode.properties().forEach(entry -> tempIdToRealId.put(entry.getKey(), entry.getValue().asText()));

        return Output.builder().tempIdToRealId(tempIdToRealId).build();
    }

    private static void putIfNotEmpty(Map<String, Object> body, String key, List<?> value) {
        if (value != null && !value.isEmpty()) {
            body.put(key, value);
        }
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Map of temporary ID to the real ID Figma assigned it", description = "Only contains entries for `CREATE` actions that used a client-chosen temporary ID.")
        private final Map<String, String> tempIdToRealId;
    }
}
