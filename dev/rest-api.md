# REST API 文档

本文档介绍 plugin-links 提供的 REST API，包括公共 API、Console API 和 Halo 标准 Extension CRUD 端点。

> **在线查看完整 Swagger 文档**：访问 [Swagger Editor](https://editor.swagger.io/)，点击左上角 **File -> Import URL**，输入以下地址即可：

```text
https://raw.githubusercontent.com/halo-sigs/plugin-links/refs/heads/main/api-docs/openapi/v3_0/linksV1alpha1Api.json
```

## 公共 API（匿名可访问）

此插件提供了一组位于 `api.link.halo.run/v1alpha1` 的公共匿名 JSON API，包括链接查询和
创建友链申请所需的 CAPTCHA、提交接口，可用于前端框架、小程序或服务端集成。

### 端点列表

| 端点 | 方法 | 说明                                                                                       |
| ---- | ---- |------------------------------------------------------------------------------------------|
| `/apis/api.link.halo.run/v1alpha1/links` | `GET` | 分页列出链接，支持 `keyword`、`group`、`labelSelector`、`fieldSelector`、`sort`、`page`、`size` 查询参数    |
| `/apis/api.link.halo.run/v1alpha1/links/-/random` | `GET` | 随机返回一组链接；必填查询参数 `maxSize`，取值范围为 `1` 到 `100`                                              |
| `/apis/api.link.halo.run/v1alpha1/links/-/count` | `GET` | 返回链接总数                                                                                   |
| `/apis/api.link.halo.run/v1alpha1/linkgroups` | `GET` | 返回所有链接分组数组，按 `spec.priority`、创建时间、`metadata.name` 升序排列                                   |
| `/apis/api.link.halo.run/v1alpha1/linkfeeds` | `GET` | 查询链接 RSS 条目，支持 `linkName`、`groupName`、`beforePublishedAt`、`beforeId`、`limit` 查询参数；默认关闭，需要在插件设置中开启“公开 RSS 订阅动态” |
| `/apis/api.link.halo.run/v1alpha1/link-applications/captcha` | `POST` | 获取无 Cookie 的友链申请 CAPTCHA 挑战；访客提交关闭时返回 `404` |
| `/apis/api.link.halo.run/v1alpha1/link-applications` | `POST` | 使用 JSON 和显式 CAPTCHA 创建友链申请，成功返回 `201`；不提供匿名查询、修改或取消 |

### 匿名访问说明

插件内置了 `role-template-link-anonymous` 角色模板，会自动聚合到匿名角色
（`rbac.authorization.halo.run/aggregate-to-anonymous: "true"`），因此上述端点无需登录
即可访问。查询接口只授予读取权限；友链申请接口只授予创建申请和 CAPTCHA 所需的最小
`create` 权限，不授予申请列表、详情或管理权限。

该角色**不会**授予 `console.api.link.halo.run` 或 `core.halo.run` 的访问权限，Console API 和标准 CRUD 端点仍需认证。

`linkfeeds` 会公开已抓取的 RSS 条目内容，因此默认关闭。站点管理员需要在插件设置的 **RSS 订阅** 中开启 **公开 RSS 订阅动态** 后，匿名访问者和主题才能读取该接口。公开返回值不会包含 RSS 订阅地址，也不会返回已在 Console 隐藏的条目或暴露条目的隐藏状态。

友链申请的请求体、成功响应、Problem Details 错误、CORS 和 CAPTCHA 生命周期请参考
[主题 API 文档中的“访客友链申请”](./theme-api.md#访客友链申请)。

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
| `/apis/console.api.link.halo.run/v1alpha1/rss/items` | `GET` | 游标分页查询 RSS 条目；支持 `linkName`、`groupName`、`read`、`favorite`、`readLater`、`hidden`、`beforePublishedAt`、`beforeId`、`limit`，省略 `hidden` 时仅返回未隐藏条目 |
| `/apis/console.api.link.halo.run/v1alpha1/rss/items/-/summary` | `GET` | 返回全局隐藏、可见收藏和可见稍后阅读 RSS 条目的精确总数 |
| `/apis/console.api.link.halo.run/v1alpha1/rss/items/-/hidden` | `POST` | 按稳定 ID 批量设置 RSS 条目的隐藏状态；重复 ID 只计算一次，不存在的 ID 会被忽略 |

`rss/items/-/summary` 返回 `hiddenCount`、`favoriteCount` 和 `readLaterCount` 三个数值字段：
`hiddenCount` 统计所有 `hidden = 1` 的条目，另外两个字段分别统计同时满足
`hidden = 0 AND favorite = 1`、`hidden = 0 AND read_later = 1` 的条目。

隐藏状态更新请求示例：

```json
{
  "ids": ["stable-item-id-1", "stable-item-id-2"],
  "hidden": true
}
```

成功响应中的 `requestedCount` 是去重后的请求 ID 数量，`updatedCount` 只统计实际发生
状态变化的条目。隐藏是 Console 管理状态：条目仍保留在本地缓存中，但不会进入普通、
收藏、稍后阅读、公共 REST 或主题 Finder 结果。

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
