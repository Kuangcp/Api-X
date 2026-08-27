# Api-X

[中文说明](Readme-CN.md)

JDK 25 JBR + Gradle 9.4.1 + Kotlin 2.4.0 + Compose 1.11.1

- `gradle run` — run in development / debug
- `gradle createDistributable` — build a distributable app image
- `gradle packageMsi` — build a Windows MSI installer

Data Dir: `~/.local/share/api-x`

**Ref**

> [Compatibility and versions](https://kotlinlang.org/docs/multiplatform/compose-compatibility-and-versioning.html)  
> [Native distributions](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html)  
> [icons](https://fonts.google.com/icons)

## Overview

Api-X is a Compose Desktop API debugging tool for Java backend developers, especially Spring Boot, Spring AI, AgentScope, and MCP-based agent development workflows.

It focuses on practical local debugging: collections, environments, auth inheritance, request/response history, OpenAPI synchronization, SSE streaming, and MCP tool/resource/prompt inspection.

## Core Features

- **HTTP request debugging:** multi-tab request editing, headers/params/body/auth panels, streaming response display, response search, and request execution logs.
- **Collection tree:** collections, folders, requests, drag-and-drop ordering, rename/delete/duplicate, and global search.
- **Environment variables:** multiple environments, variable substitution, collection/folder/request auth inheritance, and environment manager.
- **Postman compatibility:** import/export Postman Collection v2.1 and keep `_api_x_id` metadata for stable merge.
- **Data directory sync:** toolbar **Push** exports collections to `data/collection/{id}.json` and environments to `data/env/`; **Pull** merges by id without deleting local data. The `data` directory is Git-friendly.
- **Debug data root:** in the default data dir, set `debugHome=/path` in `app-settings.properties` to redirect all DB/files to a sandbox directory.
- **Shortcuts:** `Ctrl+K` global search and `Ctrl+Tab` recent request switcher.
- **Theme and UI:** dark/light themes, custom colors, resizable sidebars, and persistent window/tree state.

## Spring Boot / OpenAPI Workflow

Api-X can create and refresh a collection from a Spring Boot OpenAPI endpoint such as:

```text
http://localhost:8080/v3/api-docs
```

You can also omit the protocol when creating a collection:

```text
localhost:8080/v3/api-docs
```

Api-X will default it to `http://`.

OpenAPI behavior:

- One OpenAPI URL maps to one collection.
- OpenAPI `tags` map to folders.
- Paths/operations map to requests.
- The OpenAPI URL is stored in collection metadata and can be edited later in collection settings.
- Right-click a bound collection and choose **Refresh OpenAPI** to sync new API definitions.
- Refresh uses `METHOD + normalized path` as the sync key, so path variables like `/users/{id}` and `/users/{userId}` are treated as the same API shape.
- Refresh preserves local debugging state such as edited URL, headers, params, body, auth, request id, and response history.
- Removed server-side APIs are not deleted automatically in the current version; this avoids accidental loss of local debug data.

## MCP Debugging

Api-X includes a session-style MCP debugger for agent/tool development.

Supported flow:

- Connect to an MCP SSE endpoint and keep the session alive.
- Load `tools/list`, `resources/list`, and `prompts/list` into the left tree.
- Select a tool/resource/prompt to switch the editor context without reconnecting.
- Send repeated `tools/call`, `resources/read`, and `prompts/get` requests on the same session.
- View protocol messages, notifications, raw stream logs, and responses in the right panel.
- Persist MCP catalog, session logs, and per-item draft parameters under the request's `mcp/` directory.
- Edit tool/prompt arguments with either JSON or a generated form based on schema metadata.
- Refresh catalog manually or automatically when list-changed notifications arrive.

Current transport status:

- **SSE:** available for day-to-day debugging.
- **stdio:** design documented, lower-level code exists, but full UI validation is still planned. See [doc/roadmap/mcp_stdio.md](doc/roadmap/mcp_stdio.md).

## Packaging Notes

Windows MSI packaging uses a stable `upgradeUuid` so newer versions can upgrade existing installations when the version number increases.

Important notes:

- Increase `version` before publishing a new MSI, for example `1.4.0` -> `1.4.1`.
- The default MSI is upgrade-friendly.
- If you need to install to a custom directory from the command line, use:

```powershell
msiexec /i api-x-x.y.z.msi INSTALLDIR="D:\Apps\api-x"
```

## Screenshots

![](./img/Snipaste_2026-04-24_19-36-21.png)
![](./img/Snipaste_2026-04-24_19-36-37.png)
![](./img/Snipaste_2026-04-24_19-36-55.png)
![](./img/Snipaste_2026-04-24_19-39-55.png)
![](./img/Snipaste_2026-04-24_19-40-07.png)
![](./img/Snipaste_2026-04-24_19-41-31.png)

## Technical Blog Series

For Java developers and Kotlin beginners. Learn Compose Desktop development through the Api-X project.

> Full outline: [doc/toc.md](doc/toc.md)

### Chapter 1: Kotlin Basics & Desktop Development

| Blog | Topic | Key Contents |
|------|-------|--------------|
| [01-basic-kotlin.md](doc/01-basic-kotlin.md) | Java to Kotlin Quick Start | var/val, data class, lambda, null safety, extension functions |
| [02-compose-desktop.md](doc/02-compose-desktop.md) | Compose Desktop First Steps | @Composable, state management, remember, LaunchedEffect |
| [03-compose-layout.md](doc/03-compose-layout.md) | Compose Layout & Material Design | Row/Column/Box, LazyColumn, MaterialTheme, Modifier |

### Chapter 2: Core Features Implementation

| Blog | Topic | Key Contents |
|------|-------|--------------|
| [04-http-kt.md](doc/04-http-kt.md) | Java HttpClient to Kotlin Coroutines | JDK HttpClient, suspend, Flow, streaming response |
| [05-request-response.md](doc/05-request-response.md) | Request Panel & Response Display | State-driven UI, JSON highlighting, form handling, history |
| [06-sqlite-kt.md](doc/06-sqlite-kt.md) | SQLite in Kotlin | JDBC, schema migration, CRUD operations |
| [07-serialization.md](doc/07-serialization.md) | Serialization & JSON | kotlinx.serialization, Postman format import/export |
| [08-environment.md](doc/08-environment.md) | Environment Variable System | Env switching, variable substitution, Auth inheritance |

### Chapter 3: UI Interaction & UX

| Blog | Topic | Key Contents |
|------|-------|--------------|
| [09-tree-sidebar.md](doc/09-tree-sidebar.md) | Tree Component & Sidebar | LazyColumn multi-level tree, expand/collapse, drag-drop |
| [10-dialogs-overlay.md](doc/10-dialogs-overlay.md) | Dialogs & Global Search | Dialogs, Ctrl+K search, RecentRequest switcher |
| [11-theming.md](doc/11-theming.md) | Theme System & Dynamic Colors | Dark/light theme, custom colors, Hex parsing |
| [12-shortcuts.md](doc/12-shortcuts.md) | Desktop Shortcut Binding | KeyEvent, Ctrl+Tab, conflict resolution |

### Chapter 4: Engineering Practice

| Blog | Topic | Key Contents |
|------|-------|--------------|
| [13-architecture.md](doc/13-architecture.md) | Desktop App Architecture | MVVM-style state, Repository, module organization |
| [14-gradle-build.md](doc/14-gradle-build.md) | Gradle Kotlin DSL & Packaging | Compose Desktop packaging, JVM args, native distributions |
| [15-postman-sync.md](doc/15-postman-sync.md) | Postman Format Compatibility | Postman v2.1, Push/Pull sync, Git management |
| [16-debug-perf.md](doc/16-debug-perf.md) | JDK Debugging & Monitoring | JFR, NativeMemoryTracking, Skiko rendering |