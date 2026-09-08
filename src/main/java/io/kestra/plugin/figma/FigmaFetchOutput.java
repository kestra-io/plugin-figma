package io.kestra.plugin.figma;

import io.kestra.core.models.tasks.Output;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Shared output shape for every Figma task that supports {@code fetchType}. Produced by
 * {@link AbstractFigmaTask#fetchOutput}.
 */
@Builder
@Getter
public class FigmaFetchOutput implements Output {
    @Schema(title = "The list of fetched items", description = "Populated when `fetchType` is `FETCH`.")
    private final List<Object> rows;

    @Schema(title = "The single fetched item", description = "Populated when `fetchType` is `FETCH_ONE`.")
    private final Map<String, Object> row;

    @Schema(title = "The internal storage URI of the stored payload", description = "Populated when `fetchType` is `STORE`.")
    private final URI uri;

    @Schema(title = "The number of items stored or fetched")
    private final Long size;

    @Schema(title = "The total number of items returned by the Figma API for this request", description = "Only populated for list-shaped responses (e.g. comments, projects, files).")
    private final Long total;
}
