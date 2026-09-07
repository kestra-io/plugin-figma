package io.kestra.plugin.figma;

import io.kestra.core.exceptions.KestraRuntimeException;
import lombok.Getter;

/**
 * Raised whenever the Figma REST API returns a non-2xx response. Carries the HTTP status code so
 * callers (e.g. the variables.* tasks) can special-case specific statuses such as 403 or 429.
 */
@Getter
public class FigmaApiException extends KestraRuntimeException {
    private final int statusCode;

    public FigmaApiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }
}
