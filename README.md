# plugin-links

Halo 2.0 的链接管理插件，支持在 Console 管理链接，并为主题端提供 `/links` 页面路由、Finder API 和匿名公共 REST API。

## 功能特性

- 支持为链接设置名称、URL、Logo、描述、分组和排序
- 支持链接分组管理、分组排序，以及删除分组时保留或删除组内链接
- 支持批量导入链接，可按需自动获取站点标题、描述和图标
- 主题端 `/links` 页面路由，可通过 `group` 参数按分组展示
- 提供 `linkFinder` Finder API，可在主题任意位置渲染链接
- 提供匿名可访问的公共 REST API，方便前端框架构建客户端渲染链接页

## 安装使用

1. 下载，目前提供以下两个下载方式：
    - GitHub Releases：访问 [Releases](https://github.com/halo-sigs/plugin-links/releases) 下载 Assets 中的 JAR 文件。
    - Halo 应用市场：<https://www.halo.run/store/apps/app-hfbQg>
2. 安装，插件安装和更新方式可参考：<https://docs.halo.run/user-guide/plugins>
3. 安装完成之后，访问 Console 左侧的**链接**菜单项，即可进行管理。
4. 前台访问地址为 `/links`，需要注意的是，此插件需要主题提供模板（`links.html`）才能访问 `/links`。

## 主题适配

此插件为主题端提供了：

- **列表路由** `/links`（模板 `links.html`），支持通过 `group` 查询参数筛选分组
- **Finder API**（`linkFinder`）：可在主题任意位置渲染链接，无需依赖路由页面
- **公共 REST API**：供 React / Vue / Svelte 等前端框架构建客户端渲染链接页使用
- **评论来源适配**：可在链接页面接入 Halo 评论组件

详细的主题适配文档请参考：

- [主题 API 文档](./dev/theme-api.md) — 模板路由、模板变量、Finder API、评论适配、类型定义
- [REST API 文档](./dev/rest-api.md) — 公共 API、Console API 和标准 CRUD 端点

## 开发文档

- [开发文档](./dev/dev.md) — 本地开发、构建、测试、API 客户端生成
