# Kestra Figma Plugin

## What

- Provides tasks and a polling trigger under `io.kestra.plugin.figma` that call the [Figma REST API](https://www.figma.com/developers/api).
- Sub-packages: `files`, `comments`, `projects`, `variables`, `triggers`.
- Authentication is PAT-only (a Figma personal access token, or any pre-obtained OAuth 2.0 access token, passed via `accessToken`). No OAuth 2.0 authorization flow, webhooks, Dev Resources, Component/Style, Activity Log, or Library Analytics endpoints are implemented.

## Why

- What user problem does this solve? Teams need to read Figma file data, manage comments, list projects, and react to file changes from a Kestra flow, without hand-rolling HTTP calls to the Figma API.
- Why would a team adopt this plugin in a workflow? It wraps the most commonly used Figma REST endpoints with Kestra conventions (typed properties, `fetchType`, internal-storage-backed outputs) so design-ops or asset-pipeline flows compose cleanly with the rest of Kestra.
- What operational/business outcome does it enable? Automates syncing Figma file/image/comment data into downstream systems and triggering flows on design changes, without polling or parsing the Figma API by hand.

## How

### Architecture

Single-module plugin. Source packages under `io.kestra.plugin.figma`:

- `io.kestra.plugin.figma` — shared plumbing: `AbstractFigmaTask` (accessToken/baseUrl + authenticated request helpers + shared `fetchType` output handling), `FigmaApi` (static HTTP/JSON/error-mapping helpers, also used directly by the trigger since a Task and a Trigger cannot share a superclass), `FigmaApiException`, `FigmaFetchOutput`.
- `io.kestra.plugin.figma.files` — `GetFile`, `ExportImage`.
- `io.kestra.plugin.figma.comments` — `ListComments`, `CreateComment`, `DeleteComment`.
- `io.kestra.plugin.figma.projects` — `ListTeamProjects`, `ListProjectFiles`.
- `io.kestra.plugin.figma.variables` — `GetLocalVariables`, `GetPublishedVariables`, `UpdateVariables` (all three require a Figma Enterprise organization plan; 403 responses are mapped to a dedicated error).
- `io.kestra.plugin.figma.triggers` — `FileUpdated`, a polling trigger that watermarks the last-seen `lastModified` per file in the flow's namespace KV Store.

No infrastructure dependencies (Docker Compose services) — the plugin only calls the Figma REST API; unit tests stub it with WireMock.

### Key Plugin Classes

- `io.kestra.plugin.figma.AbstractFigmaTask`
- `io.kestra.plugin.figma.files.GetFile`
- `io.kestra.plugin.figma.files.ExportImage`
- `io.kestra.plugin.figma.comments.ListComments`
- `io.kestra.plugin.figma.comments.CreateComment`
- `io.kestra.plugin.figma.comments.DeleteComment`
- `io.kestra.plugin.figma.projects.ListTeamProjects`
- `io.kestra.plugin.figma.projects.ListProjectFiles`
- `io.kestra.plugin.figma.variables.GetLocalVariables`
- `io.kestra.plugin.figma.variables.GetPublishedVariables`
- `io.kestra.plugin.figma.variables.UpdateVariables`
- `io.kestra.plugin.figma.triggers.FileUpdated`

### Project Structure

```
plugin-figma/
├── src/main/java/io/kestra/plugin/figma/
│   ├── AbstractFigmaTask.java
│   ├── FigmaApi.java
│   ├── FigmaApiException.java
│   ├── FigmaFetchOutput.java
│   ├── files/
│   ├── comments/
│   ├── projects/
│   ├── variables/
│   └── triggers/
├── src/test/java/io/kestra/plugin/figma/
├── build.gradle
└── README.md
```

## Local rules

- Base the wording on the implemented packages and classes, not on template README text.
- The `@PluginProperty` annotation on the Kestra core version this plugin targets (1.2.5) has no `secret` attribute. Secret fields (`accessToken`) rely on `@ToString.Exclude` instead, and this deviation is called out inline in code comments — do not add `@PluginProperty(secret = true)`, it will not compile against this version.
- `@Min`/`@Max`/`@DecimalMin`/`@DecimalMax` bean-validation annotations on a `Property<Number>` field throw `jakarta.validation.UnexpectedTypeException` at task-run time on this Kestra version (no validator is wired for the constraint against the unwrapped `Property<T>` container). Bound numeric properties (e.g. `ExportImage.scale`) manually in the task's `run()` method instead, and document the bounds in `@Schema(description = ...)`.
- Kestra's HTTP client's `bodyHandler` special-cases `String.class` and `Byte[].class` (boxed) responses; requesting `byte[].class` (primitive) falls through to Jackson JSON deserialization and fails on any non-base64 JSON body. `FigmaApi.request` requests `String.class` and parses the JSON itself for this reason — keep that pattern for any new call site.

## References

- https://kestra.io/docs/plugin-developer-guide
- https://kestra.io/docs/plugin-developer-guide/contribution-guidelines
- https://www.figma.com/developers/api
