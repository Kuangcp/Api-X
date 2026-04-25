# Api-X

[中文说明](Readme-CN.md)

JDK 21

- `gradle run` — run in development / debug
- `gradle createDistributable` — build a distributable package

## Features

- **Data directory sync (Git-friendly):** toolbar **Push** exports each collection to the app data `data/collection/{id}.json` (Postman v2.1) and env to `data/env/`; **Pull** merges from that tree (add/update by id, no deletes). See `db/AppPaths` and `app/DataDirSync`.
- **Debug data root:** in the *default* (non-redirect) data dir, set `debugHome=/path` in `app-settings.properties` so all DB/files use that path for a safe sandbox.
- Import and export Postman Collection format
- Theme switching
- Request run / execution logs
- `Ctrl+K` global search
- `Ctrl+Tab` to switch between recent requests
- Multi-environment switching and variable resolution
- Folder auth inheritance

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
| [04-http-kt.md](doc/04-http-kt.md) | Java HttpClient to Kotlin Coroutines | JDK 21 HttpClient, suspend, Flow, streaming response |
| [05-request-response.md](doc/05-request-response.md) | Request Panel & Response Display | State-driven UI, JSON highlighting, form handling |
| [06-sqlite-kt.md](doc/06-sqlite-kt.md) | SQLite in Kotlin | JDBC, schema migration, CRUD operations |
| [07-serialization.md](doc/07-serialization.md) | Serialization & JSON | kotlinx.serialization, Postman format import/export |
| [08-environment.md](doc/08-environment.md) | Environment Variable System | Env switching, variable substitution, Auth inheritance |

### Chapter 3: UI Interaction & UX

| Blog | Topic | Key Contents |
|------|-------|--------------|
| [09-tree-sidebar.md](doc/09-tree-sidebar.md) | Tree Component & Sidebar | LazyColumn multi-level tree, expand/collapse, drag-drop |
| [10-dialogs-overlay.md](doc/10-dialogs-overlay.md) | Dialogs & Global Search | Dialog, Ctrl+K search, RecentRequest switcher |
| [11-theming.md](doc/11-theming.md) | Theme System & Dynamic Colors | Material3, dark/light theme, Hex parsing |
| [12-shortcuts.md](doc/12-shortcuts.md) | Desktop Shortcut Binding | KeyEvent, Ctrl+Tab, conflict resolution |

### Chapter 4: Engineering Practice

| Blog | Topic | Key Contents |
|------|-------|--------------|
| [13-architecture.md](doc/13-architecture.md) | Desktop App Architecture | MVVM, Repository, module organization |
| [14-gradle-build.md](doc/14-gradle-build.md) | Gradle Kotlin DSL & Packaging | Compose Desktop packaging, JVM args |
| [15-postman-sync.md](doc/15-postman-sync.md) | Postman Format Compatibility | Postman v2.1, Push/Pull sync, Git management |
| [16-debug-perf.md](doc/16-debug-perf.md) | JDK 21 Debugging & Monitoring | JFR, NativeMemoryTracking, Skiko rendering |
