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
