# Api-X

[English](Readme.md)

JDK 21

- `gradle run` 调试运行
- `gradle createDistributable` 打包

## 功能列表

- 顶栏 **Push / Pull**：将集合按 id 导出到应用数据目录下 `data/collection/*.json`（Postman v2.1），环境为 `data/env/environments.json`；Pull 从该目录合并进本地（仅新增与更新、不删）。可对该 `data` 目录做 Git 管理。详见 `DataDirSync`、`AppPaths`。
- 在**未重定向的**主数据目录的 `app-settings.properties` 中设置 `debugHome=路径`，可将**全部**数据根目录指到空目录，便于隔离调试、不影响正式数据。
- 导入导出 Postman Collection 格式数据
- 主题切换
- 请求运行日志
- Ctrl+K 全局搜索
- Ctrl+Tab 切换最近 Request
- 多 Env 切换和变量解析
- 文件夹 Auth 继承

## 页面截图

![](./img/Snipaste_2026-04-24_19-36-21.png)
![](./img/Snipaste_2026-04-24_19-36-37.png)
![](./img/Snipaste_2026-04-24_19-36-55.png)
![](./img/Snipaste_2026-04-24_19-39-55.png)
![](./img/Snipaste_2026-04-24_19-40-07.png)
![](./img/Snipaste_2026-04-24_19-41-31.png)
