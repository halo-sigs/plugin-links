# REST API 文档

本文档介绍 plugin-links 提供的 REST API，包括公共 API、Console API 和 Halo 标准 Extension CRUD 端点。

> **在线查看完整 Swagger 文档**：访问 [Swagger Editor](https://editor.swagger.io/)，点击左上角 **File -> Import URL**，输入以下地址即可：

```text
https://raw.githubusercontent.com/halo-sigs/plugin-links/refs/heads/main/api-docs/openapi/v3_0/linksV1alpha1Api.json
```

## 公共 API（匿名可访问）

此插件提供了一组公共、匿名、只读的 JSON API，位于 `api.link.halo.run/v1alpha1`，方便使用 React / Vue / Svelte 等前端框架构建客户端渲染链接页的主题使用。

### 端点列表

| 端点 | 方法 | 说明                                                                                       |
| ---- | ---- |------------------------------------------------------------------------------------------|
| `/apis/api.link.halo.run/v1alpha1/links` | `GET` | 分页列出链接，支持 `keyword`、`group`、`labelSelector`、`fieldSelector`、`sort`、`page`、`size` 查询参数    |
| `/apis/api.link.halo.run/v1alpha1/links/-/random` | `GET` | 随机返回一组链接；必填查询参数 `maxSize`，取值范围为 `1` 到 `100`                                              |
| `/apis/api.link.halo.run/v1alpha1/links/-/count` | `GET` | 返回链接总数                                                                                   |
| `/apis/api.link.halo.run/v1alpha1/linkgroups` | `GET` | 返回所有链接分组数组，按 `spec.priority`、创建时间、`metadata.name` 升序排列                                   |
| `/apis/api.link.halo.run/v1alpha1/linkfeeds` | `GET` | 查询链接 RSS 条目，支持 `linkName`、`groupName`、`beforePublishedAt`、`beforeId`、`limit` 查询参数；默认关闭，需要在插件设置中开启“公开 RSS 订阅动态” |

### 匿名访问说明

插件内置了 `role-template-link-anonymous` 角色模板，会自动聚合到匿名角色（`rbac.authorization.halo.run/aggregate-to-anonymous: "true"`），因此上述端点无需登录即可访问。

该角色**不会**授予 `console.api.link.halo.run` 或 `core.halo.run` 的访问权限，Console API 和标准 CRUD 端点仍需认证。

`linkfeeds` 会公开已抓取的 RSS 条目内容，因此默认关闭。站点管理员需要在插件设置的 **RSS 订阅** 中开启 **公开 RSS 订阅动态** 后，匿名访问者和主题才能读取该接口。公开返回值不会包含 RSS 订阅地址。

### 排序说明

列表端点支持通过 `sort` 查询参数控制排序，格式为 `字段名,方向`，例如：

```text
/apis/api.link.halo.run/v1alpha1/links?sort=spec.priority,asc
```

链接和分组的排序字段为 `spec.priority`，值越小越靠前。

## Console API（需要认证）

Console API 位于 `console.api.link.halo.run/v1alpha1`，供 Console 前端使用，需要登录认证。

### 端点列表

| 端点 | 方法 | 说明 |
| ---- | ---- | ---- |
| `/apis/console.api.link.halo.run/v1alpha1/links` | `GET` | 列出链接，支持 `keyword`、`groupName`、`page`、`size`、`labelSelector`、`fieldSelector`、`sort` 等查询参数 |
| `/apis/console.api.link.halo.run/v1alpha1/links/-/detail` | `GET` | 根据 `url` 查询参数抓取站点标题、描述、图标和预览图信息，用于链接创建和批量导入 |
| `/apis/console.api.link.halo.run/v1alpha1/links/-/sort` | `POST` | 按请求体中的链接 `metadata.name` 顺序更新链接 `spec.priority` |
| `/apis/console.api.link.halo.run/v1alpha1/linkgroups/-/sort` | `POST` | 按请求体中的分组 `metadata.name` 顺序更新分组 `spec.priority` |
| `/apis/console.api.link.halo.run/v1alpha1/linkgroups/{name}` | `DELETE` | 删除指定分组；可选查询参数 `deleteLinks` 控制是否同时删除组内链接，默认 `false`，此时组内链接会变为未分组 |

### 排序请求体

`links/-/sort` 和 `linkgroups/-/sort` 使用相同的请求体结构：

```json
{
  "names": ["link-a", "link-b", "link-c"]
}
```

插件会按数组顺序从 `0` 开始写入 `spec.priority`。

## 标准 CRUD 端点（需要认证）

链接和分组的增删改查还可通过 Halo 标准 Extension CRUD 端点操作：

| 端点 | 说明 |
| ---- | ---- |
| `/apis/core.halo.run/v1alpha1/links` | 链接资源的标准 CRUD |
| `/apis/core.halo.run/v1alpha1/linkgroups` | 链接分组资源的标准 CRUD |
