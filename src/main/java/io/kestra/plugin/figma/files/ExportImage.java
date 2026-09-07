package io.kestra.plugin.figma.files;

import com.fasterxml.jackson.databind.JsonNode;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
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
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@SuperBuilder
@ToString
@EqualsAndHashCode
@Getter
@NoArgsConstructor
@Schema(
    title = "Export Figma nodes as images",
    description = """
        Calls the Figma `GET /v1/images/:key` endpoint, which returns temporary URLs (valid for \
        roughly 30 minutes) rather than image bytes. This task downloads every returned URL in the \
        same run and stores the images in Kestra's internal storage — the raw, expiring Figma URLs \
        are never exposed as output."""
)
@Plugin(
    examples = {
        @Example(
            title = "Export two nodes of a Figma file as PNG images",
            full = true,
            code = """
                id: figma_export_image
                namespace: company.team

                tasks:
                  - id: export_image
                    type: io.kestra.plugin.figma.files.ExportImage
                    accessToken: "{{ secret('FIGMA_ACCESS_TOKEN') }}"
                    fileKey: "abc123XYZ"
                    nodeIds:
                      - "1:2"
                      - "1:3"
                    format: PNG
                """
        )
    }
)
public class ExportImage extends AbstractFigmaTask implements RunnableTask<ExportImage.Output> {
    @NotNull
    @Schema(title = "The Figma file key", description = "Found in the file's URL: `https://www.figma.com/file/:fileKey/...`.")
    @PluginProperty(group = "main")
    private Property<String> fileKey;

    @NotNull
    @Schema(title = "The node IDs to export", description = "Must contain at least one ID — the Figma API returns a 400 error otherwise.")
    @PluginProperty(group = "main")
    private Property<List<String>> nodeIds;

    @NotNull
    @Schema(title = "The output image format")
    @PluginProperty(group = "processing")
    @Builder.Default
    private Property<ImageFormat> format = Property.ofValue(ImageFormat.PNG);

    @Schema(title = "A scaling factor to apply to the exported image", description = "Must be between `0.01` and `4`. Ignored for `SVG` and `PDF`.")
    @PluginProperty(group = "processing")
    private Property<Double> scale;

    @Schema(title = "Whether to include the node ID as an attribute on exported SVG elements")
    @PluginProperty(group = "processing")
    private Property<Boolean> svgIncludeId;

    @Schema(title = "Whether to use the full node bounding box, ignoring any clipping")
    @PluginProperty(group = "processing")
    private Property<Boolean> useAbsoluteBounds;

    @Override
    public Output run(RunContext runContext) throws Exception {
        String rFileKey = runContext.render(this.fileKey).as(String.class)
            .orElseThrow(() -> new IllegalArgumentException("Missing required `fileKey` property"));

        List<String> rNodeIds = runContext.render(this.nodeIds).asList(String.class);
        if (rNodeIds.isEmpty()) {
            throw new IllegalArgumentException("`nodeIds` must not be empty — the Figma images endpoint returns a 400 error without at least one node ID.");
        }

        ImageFormat rFormat = runContext.render(this.format).as(ImageFormat.class).orElse(ImageFormat.PNG);

        Map<String, String> params = new LinkedHashMap<>();
        params.put("ids", String.join(",", rNodeIds));
        params.put("format", rFormat.name().toLowerCase());
        Double rScale = runContext.render(this.scale).as(Double.class).orElse(null);
        if (rScale != null) {
            if (rScale < 0.01 || rScale > 4) {
                throw new IllegalArgumentException("`scale` must be between 0.01 and 4, got " + rScale);
            }
            params.put("scale", String.valueOf(rScale));
        }
        runContext.render(this.svgIncludeId).as(Boolean.class).ifPresent(b -> params.put("svg_include_id", String.valueOf(b)));
        runContext.render(this.useAbsoluteBounds).as(Boolean.class).ifPresent(b -> params.put("use_absolute_bounds", String.valueOf(b)));

        JsonNode response = this.get(runContext, "/images/" + FigmaApi.encodePathSegment(rFileKey) + FigmaApi.queryString(params));

        if (response.hasNonNull("err")) {
            throw new IllegalStateException("Figma image export failed: " + response.get("err").asText());
        }

        JsonNode images = response.path("images");
        List<String> failedNodeIds = new ArrayList<>();
        Map<String, String> urlsToDownload = new LinkedHashMap<>();

        for (String nodeId : rNodeIds) {
            JsonNode url = images.get(nodeId);
            if (url == null || url.isNull()) {
                failedNodeIds.add(nodeId);
            } else {
                urlsToDownload.put(nodeId, url.asText());
            }
        }

        if (!failedNodeIds.isEmpty()) {
            throw new IllegalStateException(
                "Figma failed to render the following node(s): " + String.join(", ", failedNodeIds) +
                    ". They may not exist in the file, or the export format/scale combination may be unsupported for that node type."
            );
        }

        String extension = "." + rFormat.name().toLowerCase();
        Map<String, URI> imageUris = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : urlsToDownload.entrySet()) {
            imageUris.put(entry.getKey(), this.downloadToStorage(runContext, entry.getKey(), entry.getValue(), extension));
        }

        return Output.builder().images(imageUris).build();
    }

    private URI downloadToStorage(RunContext runContext, String nodeId, String url, String extension) throws IllegalVariableEvaluationException, IOException {
        Logger logger = runContext.logger();
        logger.debug("Downloading exported image for node '{}'", nodeId);

        HttpRequest request = HttpRequest.builder().uri(URI.create(url)).build();
        AtomicReference<URI> stored = new AtomicReference<>();
        AtomicReference<IOException> failure = new AtomicReference<>();

        try (HttpClient client = HttpClient.builder().runContext(runContext).configuration(HttpConfiguration.builder().build()).build()) {
            client.request(request, httpResponse -> {
                try {
                    stored.set(runContext.storage().putFile(httpResponse.getBody(), nodeId + extension));
                } catch (IOException e) {
                    failure.set(e);
                }
            });
        } catch (HttpClientResponseException e) {
            FigmaApiException mapped = FigmaApi.mapError(e);
            logger.error("Failed to download exported image for node '{}': {}", nodeId, mapped.getMessage());
            throw mapped;
        } catch (HttpClientException e) {
            logger.error("Failed to download exported image for node '{}': {}", nodeId, e.getMessage());
            throw new IOException("Failed to download exported image for node '" + nodeId + "': " + e.getMessage(), e);
        }

        if (failure.get() != null) {
            throw failure.get();
        }

        return stored.get();
    }

    public enum ImageFormat {
        PNG,
        JPG,
        SVG,
        PDF
    }

    @Builder
    @Getter
    public static class Output implements io.kestra.core.models.tasks.Output {
        @Schema(title = "Map of node ID to the internal storage URI of the exported image")
        private final Map<String, URI> images;
    }
}
