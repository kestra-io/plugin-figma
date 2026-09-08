package io.kestra.plugin.figma.triggers;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.exceptions.ResourceExpiredException;
import io.kestra.core.models.annotations.Example;
import io.kestra.core.models.annotations.Plugin;
import io.kestra.core.models.annotations.PluginProperty;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.AbstractTrigger;
import io.kestra.core.models.triggers.PollingTriggerInterface;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.models.triggers.TriggerOutput;
import io.kestra.core.models.triggers.TriggerService;
import io.kestra.core.runners.RunContext;
import io.kestra.plugin.figma.FigmaApi;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.slf4j.Logger;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Trigger a flow when a Figma file is modified",
    description = """
        Polls the Figma `GET /v1/files/:key?depth=1` endpoint and compares the response's \
        `lastModified` timestamp against the last value seen for this trigger. The first evaluation \
        only records the timestamp — it never fires an execution, to avoid a spurious run when the \
        trigger is first enabled. A 403 or 404 while polling (deleted or unshared file) fails the \
        evaluation instead of silently keeping a stale timestamp."""
)
@Plugin(
    examples = {
        @Example(
            title = "Trigger a flow whenever a Figma file changes",
            full = true,
            code = """
                id: figma_file_updated
                namespace: company.team

                tasks:
                  - id: log_change
                    type: io.kestra.plugin.core.log.Log
                    message: "File {{ trigger.fileKey }} changed at {{ trigger.lastModified }}"

                triggers:
                  - id: file_updated
                    type: io.kestra.plugin.figma.triggers.FileUpdated
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                    interval: PT5M
                """
        )
    }
)
public class FileUpdated extends AbstractTrigger implements PollingTriggerInterface, TriggerOutput<FileUpdated.Output> {
    private static final String STATE_NAME = "figma-file-updated";
    private static final String STATE_SUB_NAME = "last-modified";

    @NotNull
    @Schema(title = "The interval between two polls")
    @PluginProperty(group = "execution")
    @Builder.Default
    private Duration interval = Duration.ofMinutes(5);

    @NotNull
    @Schema(title = "The Figma file key to watch", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @Schema(
        title = "Figma access token",
        description = """
            A personal access token generated from your Figma account settings (Account Settings > \
            Personal access tokens), scoped to at least file read access. A valid OAuth 2.0 access \
            token obtained through your own authorization flow also works — this plugin does not \
            implement the OAuth flow itself, only PAT-style bearer tokens passed as-is."""
    )
    @PluginProperty(group = "connection", secret = true)
    @ToString.Exclude
    @NotNull
    private Property<String> accessToken;

    @Schema(title = "Figma API base URL", description = "Defaults to the public Figma REST API. Override only to point at a self-hosted proxy or a test server.")
    @PluginProperty(group = "connection")
    @Builder.Default
    private Property<String> baseUrl = Property.ofValue(FigmaApi.DEFAULT_BASE_URL);

    @Override
    public Duration getInterval() {
        return this.interval;
    }

    @Override
    public Optional<Execution> evaluate(ConditionContext conditionContext, TriggerContext triggerContext) throws Exception {
        RunContext runContext = conditionContext.getRunContext();
        Logger logger = runContext.logger();

        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));
        String rAccessToken = runContext.render(this.accessToken).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `accessToken` property"));
        String rBaseUrl = runContext.render(this.baseUrl).as(String.class).orElse(FigmaApi.DEFAULT_BASE_URL);

        logger.debug("Polling Figma file '{}' for changes", rFileKey);
        JsonNode file = FigmaApi.request(runContext, rBaseUrl, rAccessToken, "GET", "/files/" + FigmaApi.encodePathSegment(rFileKey) + "?depth=1", null);

        String lastModified = file.path("lastModified").asText(null);
        if (lastModified == null) {
            throw new IllegalStateException("Figma file '" + rFileKey + "' response did not include a `lastModified` field — unexpected response shape.");
        }

        // scopes the state to this trigger instance and file: RunContext.stateStore() already scopes
        // by flow, so this only needs to further disambiguate multiple FileUpdated triggers on the
        // same flow (or the same trigger watching more than one file across polls).
        String stateValue = triggerContext.getTriggerId() + "_" + rFileKey;
        String previousLastModified = readWatermark(runContext, stateValue);

        if (previousLastModified == null) {
            // first evaluation: record the baseline without firing, to avoid a spurious run when the trigger is first enabled
            putWatermark(runContext, stateValue, lastModified);
            return Optional.empty();
        }

        Instant current = Instant.parse(lastModified);
        Instant previous = Instant.parse(previousLastModified);

        if (!current.isAfter(previous)) {
            return Optional.empty();
        }

        logger.info("Figma file '{}' changed, lastModified advanced from '{}' to '{}'", rFileKey, previous, current);

        Execution execution = TriggerService.generateExecution(this, conditionContext, triggerContext, Output.builder()
            .fileKey(rFileKey)
            .lastModified(current)
            .build());

        // persist the watermark only once the execution has actually been built: if the scheduler
        // crashes before this point, the next poll re-fires (a recoverable duplicate) instead of
        // silently dropping the change (which an earlier watermark write would cause)
        putWatermark(runContext, stateValue, lastModified);

        return Optional.of(execution);
    }

    private static String readWatermark(RunContext runContext, String stateValue) throws Exception {
        try (var stream = runContext.stateStore().getState(STATE_NAME, STATE_SUB_NAME, stateValue)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (FileNotFoundException | ResourceExpiredException e) {
            return null;
        }
    }

    private static void putWatermark(RunContext runContext, String stateValue, String lastModified) throws Exception {
        runContext.stateStore().putState(STATE_NAME, STATE_SUB_NAME, stateValue, lastModified.getBytes(StandardCharsets.UTF_8));
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "The Figma file key that changed")
        private final String fileKey;

        @Schema(title = "The file's new `lastModified` timestamp")
        private final Instant lastModified;
    }
}
