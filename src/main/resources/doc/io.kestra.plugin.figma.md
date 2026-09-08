# How to use the Figma plugin

This plugin lets you read Figma file documents and images, manage file comments, list team projects, read and write variables, and trigger flows when a file changes, using the [Figma REST API](https://www.figma.com/developers/api).

## Authentication

Every task and the trigger require:

- `accessToken` — a Figma access token. Generate a personal access token (PAT) from your Figma account settings under **Account Settings > Personal access tokens**, scoped to the resources the task needs (e.g. file content, comments, or Enterprise `file_variables` for the `variables.*` tasks). A valid OAuth 2.0 access token obtained through your own authorization flow also works — this plugin only forwards the token as the `X-Figma-Token` header, it does not implement the OAuth 2.0 authorization-code flow itself.
- `baseUrl` — optional, defaults to `https://api.figma.com/v1`. Override only to point at a self-hosted proxy or a test server.

Store the token as a [Kestra secret](https://kestra.io/docs/concepts/secret) and reference it with `{{ secret('FIGMA_ACCESS_TOKEN') }}` rather than hardcoding it in a flow. If most of your flows share the same token, set it once via [plugin defaults](https://kestra.io/docs/workflow-components/plugin-defaults) instead of repeating it on every task.

## Enterprise plan gating

The three `variables.*` tasks (`GetLocalVariables`, `GetPublishedVariables`, `UpdateVariables`) call Figma variables endpoints that **require a Figma Enterprise organization plan** with variables enabled. If your organization isn't on an Enterprise plan, these tasks fail with a 403 error explaining the requirement — read or write variables from a client-side Figma plugin instead. Every other task and the trigger work on any Figma plan.

## Tasks

Tasks that support `fetchType` (`files.GetFile`, `comments.List`, `projects.ListTeamProjects`, `projects.ListProjectFiles`, `variables.GetLocalVariables`, `variables.GetPublishedVariables`) all share the same output shape: `rows`/`total` for `FETCH`, a single `row` for `FETCH_ONE`, or `uri`/`size` when `STORE`s the payload to Kestra's internal storage. `files.GetFile` defaults `fetchType` to `STORE` because file documents can be very large.

### Files

- `files.GetFile` — get a file's document structure (`GET /v1/files/:key`). Required: `fileKey`. Optional: `ids`, `depth`, `geometry`, `fileVersion`, `branchData`.
- `files.ExportImage` — export one or more nodes as images (`GET /v1/images/:key`). Required: `fileKey`, `nodeIds`. Optional: `format` (`PNG`/`JPG`/`SVG`/`PDF`), `scale`, `svgIncludeId`, `useAbsoluteBounds`. Downloads every returned image in the same run and stores them internally, rather than exposing Figma's raw (and short-lived) URLs as output. A per-node render failure fails the whole task, listing the failed node IDs. Accepts either dash form (`1-2`) or colon form (`1:2`) node IDs.

### Comments

- `comments.List` — list a file's comments (`GET /v1/files/:key/comments`). Required: `fileKey`. Optional: `asMd`.
- `comments.Create` — post a comment (`POST /v1/files/:key/comments`). Required: `fileKey`, `message`. Optional: `commentId` (reply to an existing comment), `clientMeta` (pin to a canvas position or a node).
- `comments.Delete` — delete a comment (`DELETE /v1/files/:key/comments/:comment_id`). Required: `fileKey`, `commentId`.

### Projects

- `projects.ListTeamProjects` — list a team's projects (`GET /v1/teams/:team_id/projects`). Required: `teamId`.
- `projects.ListProjectFiles` — list a project's files (`GET /v1/projects/:project_id/files`). Required: `projectId`. Optional: `branchData`.

### Variables

*Requires a Figma Enterprise organization plan — see [Enterprise plan gating](#enterprise-plan-gating).*

- `variables.GetLocalVariables` — get a file's local variables and collections (`GET /v1/files/:file_key/variables/local`). Required: `fileKey`.
- `variables.GetPublishedVariables` — get a file's published variables and collections (`GET /v1/files/:file_key/variables/published`). Required: `fileKey`.
- `variables.UpdateVariables` — bulk create/update/delete variables, collections, modes, and mode values (`POST /v1/files/:file_key/variables`). Required: `fileKey`. Optional: `variableCollections`, `variableModes`, `variables`, `variableModeValues`, matching the Figma API's own bulk-write envelope. Outputs `tempIdToRealId`, mapping any client-chosen temporary IDs used for `CREATE` actions to the real IDs Figma assigned them.

## Triggers

- `triggers.FileUpdated` — polls a Figma file (`GET /v1/files/:key?depth=1`) and starts an execution whenever the file's `lastModified` timestamp advances past the last value seen. Required: `fileKey`, `accessToken`. Optional: `interval` (default `PT5M`), `baseUrl`. The first evaluation only records the current timestamp — it never fires an execution, avoiding a spurious run when the trigger is first enabled. A 403 or 404 while polling (e.g. a deleted or unshared file) fails the evaluation loudly rather than silently keeping a stale timestamp. Outputs `fileKey` and `lastModified`.
