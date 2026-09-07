package io.kestra.plugin.figma;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import io.kestra.core.exceptions.IllegalVariableEvaluationException;
import io.kestra.core.http.HttpRequest;
import io.kestra.core.http.HttpResponse;
import io.kestra.core.http.client.HttpClient;
import io.kestra.core.http.client.HttpClientException;
import io.kestra.core.http.client.HttpClientResponseException;
import io.kestra.core.http.client.configurations.HttpConfiguration;
import io.kestra.core.runners.RunContext;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Low-level HTTP plumbing shared by every Figma task and the polling trigger. Kept as static
 * methods (rather than a shared superclass) because a Task and a Trigger cannot extend a common
 * base class in Kestra.
 */
public final class FigmaApi {
    public static final String DEFAULT_BASE_URL = "https://api.figma.com/v1";

    private FigmaApi() {
    }

    public static JsonNode request(
        RunContext runContext,
        String rBaseUrl,
        String rAccessToken,
        String method,
        String path,
        Object jsonBody
    ) throws IllegalVariableEvaluationException, IOException {
        HttpRequest.HttpRequestBuilder requestBuilder = HttpRequest.builder()
            .uri(URI.create(rBaseUrl + path))
            .method(method)
            .addHeader("X-Figma-Token", rAccessToken);

        if (jsonBody != null) {
            requestBuilder.body(HttpRequest.JsonRequestBody.builder().content(jsonBody).build());
        }

        try (HttpClient client = HttpClient.builder().runContext(runContext).configuration(HttpConfiguration.builder().build()).build()) {
            // Requesting `byte[].class` would make the Kestra HTTP client try to Jackson-deserialize
            // the JSON body *into* a byte array (it only special-cases `Byte[].class`, not the
            // primitive form) — request the raw JSON as a String instead and parse it ourselves.
            HttpResponse<String> response = client.request(requestBuilder.build(), String.class);
            return parseBody(response.getBody());
        } catch (HttpClientResponseException e) {
            throw mapError(e);
        } catch (HttpClientException e) {
            throw new IOException("Failed to call the Figma API at '" + path + "': " + e.getMessage(), e);
        }
    }

    public static JsonNode parseBody(String body) throws IOException {
        if (body == null || body.isEmpty()) {
            return NullNode.getInstance();
        }

        return JacksonMapper.ofJson().readTree(body);
    }

    public static FigmaApiException mapError(HttpClientResponseException e) {
        int code = e.getResponse() != null ? e.getResponse().getStatus().getCode() : -1;
        String detail = e.getMessage();
        String retryAfter = null;

        if (e.getResponse() != null) {
            Object rawBody = e.getResponse().getBody();
            if (rawBody instanceof byte[] bytes && bytes.length > 0) {
                try {
                    JsonNode node = JacksonMapper.ofJson().readTree(bytes);
                    if (node.hasNonNull("err")) {
                        detail = node.get("err").asText();
                    } else if (node.hasNonNull("message")) {
                        detail = node.get("message").asText();
                    }
                } catch (IOException ignored) {
                    // keep the raw exception message as detail when the error body isn't valid JSON
                }
            }

            retryAfter = e.getResponse().getHeaders().firstValue("Retry-After").orElse(null);
        }

        if (code == 429) {
            String hint = retryAfter != null ? " Retry after " + retryAfter + " seconds." : "";
            return new FigmaApiException(
                "Figma API rate limit exceeded (429): " + detail + "." + hint +
                    " This plugin does not retry automatically — compose a `retry` block on the task instead.",
                code
            );
        }

        return new FigmaApiException("Figma API " + code + ": " + detail, code);
    }

    public static String queryString(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return "";
        }

        List<String> pairs = params.entrySet().stream()
            .filter(entry -> entry.getValue() != null)
            .map(entry -> URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
            .toList();

        return pairs.isEmpty() ? "" : pairs.stream().collect(Collectors.joining("&", "?", ""));
    }
}
