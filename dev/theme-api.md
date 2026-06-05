# 主题 API 文档

本文档介绍 plugin-links 为主题端提供的模板路由、模板变量、Finder API 和类型定义。

## 路由

### 列表页

- 模板路径：`/templates/links.html`
- 访问路径：`/links`

#### 路由可选参数

| 参数 | 说明 |
| ---- | ---- |
| `group` | 链接分组名称，对应 `LinkGroupVo.metadata.name` |

示例：

```text
/links
/links?group=link-group-abcde
```

#### 模板变量

| 变量 | 类型 | 说明 |
| ---- | ---- | ---- |
| `links` | `List<LinkVo>` | 当前页面链接列表；带 `group` 查询参数时仅返回该分组下链接 |
| `simpleGroups` | `List<LinkGroupVo>` | 所有链接分组，不包含 `links[]` |
| `groups` | `List<LinkGroupVo>` | 所有链接分组及其链接；存在未分组链接时会追加一个 `metadata.name` 为 `ungrouped` 的虚拟分组 |
| `group` | `String \| null` | 当前 URL 上的 `group` 查询参数 |
| `linksTitle` | `String` | 页面标题，来自插件设置 `base.title`，默认值为 `链接` |
| `pluginName` | `String` | 当前插件名称，可用于评论组件的 `name` |
| `_templateId` | `String` | 固定为 `"links"` |

`links` 示例：

```html
<h1 th:text="${linksTitle}"></h1>

<ul>
    <li th:each="link : ${links}">
        <a th:href="${link.spec.url}" target="_blank" rel="noopener">
            <img th:if="${link.spec.logo}" th:src="${link.spec.logo}" th:alt="${link.spec.displayName}">
            <span th:text="${link.spec.displayName}"></span>
            <small th:text="${link.spec.description}"></small>
        </a>
    </li>
</ul>
```

`simpleGroups` 和 `group` 示例：

```html
<nav>
    <a th:href="@{/links}" th:classappend="${group == null} ? 'active'">全部</a>
    <a
        th:each="item : ${simpleGroups}"
        th:href="@{/links(group=${item.metadata.name})}"
        th:classappend="${group == item.metadata.name} ? 'active'"
        th:text="${item.spec.displayName}"
    ></a>
</nav>
```

`groups` 示例：

```html
<section th:each="item : ${groups}">
    <h2 th:text="${item.spec.displayName} ?: '未分组'"></h2>
    <ul>
        <li th:each="link : ${item.links}">
            <a th:href="${link.spec.url}" target="_blank" rel="noopener">
                <span th:text="${link.spec.displayName}"></span>
            </a>
        </li>
    </ul>
</section>
```

---

## Finder API

Finder API 由两个独立对象提供，可在主题模板的任意位置使用，无需依赖 `/links` 路由页面：

- `linkFinder`：查询链接和链接分组，返回 `LinkVo` / `LinkGroupVo`。
- `linkFeedFinder`：查询链接 RSS 条目和带 RSS 条目的链接分组，返回 `LinkFeedItemPageVo` / `LinkFeedGroupVo`。

两个对象都有 `groupBy` 方法，但参数和返回值不同，请按下面各自的说明使用。

### linkFinder

`linkFinder` 对应当前实现中的 `@Finder("linkFinder")`，用于查询链接本身。

#### linkFinder.groupBy()

获取全部链接分组及其链接。存在未分组链接时，会追加一个 `metadata.name` 为 `ungrouped` 的虚拟分组。

当前实现中，返回的链接仅包含 `metadata.deletionTimestamp` 为空的链接；分组按 `spec.priority`、`metadata.creationTimestamp`、`metadata.name` 升序排列，每个分组内的链接按 `spec.priority`、`metadata.creationTimestamp`、`metadata.name` 升序排列。

**参数**：无

**返回值**：`List<LinkGroupVo>`

**示例**：

```html
<section th:each="group : ${linkFinder.groupBy()}">
    <h2 th:text="${group.spec.displayName} ?: '未分组'"></h2>
    <a th:each="link : ${group.links}" th:href="${link.spec.url}" target="_blank" rel="noopener">
        <span th:text="${link.spec.displayName}"></span>
    </a>
</section>
```

#### linkFinder.listBy(group)

根据分组获取链接列表。

**参数**：

| 参数 | 说明 |
| ---- | ---- |
| `group` | 链接分组名称，对应 `LinkGroupVo.metadata.name`。传入 `ungrouped` 时会查询 `spec.groupName` 为空的未分组链接 |

**返回值**：`List<LinkVo>`

返回的链接仅包含 `metadata.deletionTimestamp` 为空的链接，并按 `spec.priority`、`metadata.creationTimestamp`、`metadata.name` 升序排列。

**示例**：

```html
<ul>
    <li th:each="link : ${linkFinder.listBy('friends')}">
        <a th:href="${link.spec.url}" target="_blank" rel="noopener">
            <span th:text="${link.spec.displayName}"></span>
        </a>
    </li>
</ul>
```

#### linkFinder.random(maxSize)

随机获取链接列表。

**参数**：

| 参数 | 说明 |
| ---- | ---- |
| `maxSize` | 返回数量，取值范围为 `1` 到 `100` |

**返回值**：`List<LinkVo>`

**示例**：

```html
<ul>
    <li th:each="link : ${linkFinder.random(5)}">
        <a th:href="${link.spec.url}" target="_blank" rel="noopener">
            <span th:text="${link.spec.displayName}"></span>
        </a>
    </li>
</ul>
```

#### linkFinder.count()

获取链接总数。

**参数**：无

**返回值**：`Integer`

**示例**：

```html
<span th:text="${linkFinder.count()}"></span>
```

### linkFeedFinder

`linkFeedFinder` 对应当前实现中的 `@Finder("linkFeedFinder")`，用于查询已抓取的链接 RSS 条目。它不返回普通的 `LinkVo` 分组，而是返回 RSS 条目分页或带 `feeds` 的 `LinkFeedVo` 分组。

此 Finder 默认不公开数据。需要在插件设置的 **RSS 订阅** 中开启 **公开 RSS 订阅动态** 后，`linkFeedFinder.list(params)` 才会返回条目，`linkFeedFinder.groupBy(limit)` 才会返回分组。关闭时，前者返回空分页，后者返回空列表。公开返回值不会包含 RSS 订阅地址。

#### linkFeedFinder.list(params)

获取 RSS 条目分页。

**参数**：

| 参数 | 说明 |
| ---- | ---- |
| `groupName` | 可选，链接分组名称。当前实现会按 `Link.spec.groupName` 精确匹配，不会把 `ungrouped` 特殊转换为未分组链接 |
| `linkName` | 可选，链接名称，对应 `LinkVo.metadata.name`。如果同时传入 `groupName`，当前实现会按分组下的全部链接聚合，此参数不会再额外生效 |
| `beforePublishedAt` | 可选，游标发布时间，ISO-8601 时间字符串 |
| `beforeId` | 可选，游标 ID；通常与 `beforePublishedAt` 一起用于加载下一页 |
| `read` | 可选，按已读状态过滤 |
| `favorite` | 可选，按收藏状态过滤 |
| `readLater` | 可选，按稍后阅读状态过滤 |
| `limit` | 可选，最大条数；默认 `30`，小于等于 `0` 时也按 `30` 处理，返回分页最多 `100` 条 |

**返回值**：`LinkFeedItemPageVo`

返回值中的 `items` 会补齐作者信息：当 RSS 条目没有作者时，使用对应链接的 `spec.displayName`；同时会填充 `authorLogo` 和 `authorUrl`。

**示例**：

```html
<th:block th:with="linkFeeds = ${linkFeedFinder.list({
  limit: 20
})}">
    <ul>
        <li th:each="linkFeed : ${linkFeeds.items}">
            <a th:href="${linkFeed.url}" target="_blank" rel="noopener">
                <span th:text="${linkFeed.title}"></span>
            </a>
        </li>
    </ul>
</th:block>

```

按单个链接筛选：

```html
linkFeedFinder.list({
  limit: 20,
  linkName: 'link-name'
})
```

按下一页游标继续加载：

```html
linkFeedFinder.list({
  limit: 20,
  beforePublishedAt: linkFeeds.nextBeforePublishedAt,
  beforeId: linkFeeds.nextBeforeId
})
```

#### linkFeedFinder.groupBy(limit)

按链接分组获取每个链接最近的 RSS 条目。

**参数**：

| 参数 | 说明 |
| ---- | ---- |
| `limit` | 每个链接最多获取的 RSS 条目数。小于等于 `0` 时按默认值 `30` 处理，建议传入 `1` 到 `100` |

**返回值**：`List<LinkFeedGroupVo>`

当前实现中，返回的链接仅包含 `metadata.deletionTimestamp` 为空的链接。存在未分组链接时，会追加一个 `metadata.name` 为 `ungrouped` 的虚拟分组。

分组按 `spec.priority`、`metadata.creationTimestamp`、`metadata.name` 升序排列。命名分组内的链接会按第一条 RSS 条目的 `publishedAt` 倒序排列，没有 RSS 条目的链接排在后面；虚拟未分组分组会追加在最后。

**示例**：

```html
<section th:each="group : ${linkFeedFinder.groupBy(2)}">
    <h2 th:text="${group.spec.displayName} ?: '未分组'"></h2>
    <ul>
        <li th:each="link : ${group.links}">
            <a th:href="${link.spec.url}" target="_blank" rel="noopener">
                <span th:text="${link.spec.displayName}"></span>
            </a>
            <ul>
                <li th:each="feed : ${link.feeds}">
                    <a th:href="${feed.url}" target="_blank" rel="noopener">
                        <span th:text="${feed.title}"></span>
                    </a>
                </li>
            </ul>
        </li>
    </ul>
</section>
```

---

## 评论适配

链接页面已适配 Halo 的评论来源。在 `links.html` 模板中，可通过 `halo:comment` 标签为链接页面添加评论功能：

```html
<div th:if="${haloCommentEnabled}">
    <halo:comment
        group="plugin.halo.run"
        kind="Plugin"
        th:attr="name=${pluginName}"
    />
</div>
```

参数说明：

| 属性 | 值 | 说明 |
| ---- | ---- | ---- |
| `group` | `plugin.halo.run` | 插件评论来源的 API group |
| `kind` | `Plugin` | 插件评论来源的 kind |
| `name` | `${pluginName}` | 当前插件名称 |

> 注：评论功能依赖 Halo 的 `plugin-comment-widget` 插件。如果该插件未启用，评论标签不会渲染任何内容。

---

## 公共 REST API

如果主题使用前端框架进行客户端渲染，可以直接调用匿名公共 API。端点列表请参考 [REST API 文档](./rest-api.md)。

---

## 类型定义

### LinkVo

```json
{
  "metadata": {
    "name": "string",
    "labels": { "additionalProp1": "string" },
    "annotations": { "additionalProp1": "string" },
    "creationTimestamp": "2022-11-20T13:06:38.512Z"
  },
  "spec": {
    "url": "string",
    "displayName": "string",
    "logo": "string",
    "description": "string",
    "priority": 0,
    "groupName": "string"
  },
  "status": {
    "rss": {
      "lastFetchedAt": "2022-11-20T13:06:38.512Z",
      "lastSuccessAt": "2022-11-20T13:06:38.512Z",
      "lastError": "string",
      "failureCount": 0,
      "latestPublishedAt": "2022-11-20T13:06:38.512Z",
      "itemCount": 0,
      "feeds": [
        {
          "url": "string",
          "lastFetchedAt": "2022-11-20T13:06:38.512Z",
          "lastSuccessAt": "2022-11-20T13:06:38.512Z",
          "lastError": "string",
          "failureCount": 0,
          "etag": "string",
          "lastModified": "string",
          "validatorUpdatedAt": "2022-11-20T13:06:38.512Z",
          "latestPublishedAt": "2022-11-20T13:06:38.512Z",
          "itemCount": 0
        }
      ]
    },
    "verification": {
      "lastCheckedAt": "2022-11-20T13:06:38.512Z",
      "access": {
        "state": "ACCESSIBLE",
        "checkedAt": "2022-11-20T13:06:38.512Z",
        "statusCode": 200,
        "finalUrl": "string",
        "error": "string"
      },
      "backlink": {
        "state": "FOUND",
        "checkedAt": "2022-11-20T13:06:38.512Z",
        "scanUrl": "string",
        "targetUrl": "string",
        "matchedUrl": "string",
        "error": "string"
      }
    }
  }
}
```

> `status` 为观测状态，未执行过 RSS 刷新或链接检测时，其子字段可能为空。

在 Thymeleaf 模板或 Finder API 中，`status.verification.access.state` 和
`status.verification.backlink.state` 是 Java 枚举对象。如果需要按字符串判断状态，请先调用
`name()` 取枚举名称；公共 REST API 返回 JSON 时这些状态字段会序列化为字符串。

```html
<th:block th:with="accessState=${link.status?.verification?.access?.state?.name()}">
    <span th:if="${accessState == 'ACCESSIBLE'}">在线</span>
    <span th:if="${accessState == 'INACCESSIBLE'}">离线</span>
</th:block>
```

`status.verification.access.state` 可选值：

| 值 | 含义 |
| ---- | ---- |
| `CHECKING` | 正在检测链接是否可访问 |
| `ACCESSIBLE` | 链接可访问，最后一次可访问性检测成功 |
| `INACCESSIBLE` | 链接不可访问，可结合 `statusCode`、`finalUrl` 和 `error` 查看详情 |

`status.verification.backlink.state` 可选值：

| 值 | 含义 |
| ---- | ---- |
| `CHECKING` | 正在检测对方页面是否包含本站链接 |
| `FOUND` | 已在检测页面找到指向本站的链接 |
| `MISSING` | 已完成检测，但未找到指向本站的链接 |
| `NOT_CONFIGURED` | 未配置回链检测地址，未执行回链检测 |
| `FAILED` | 回链检测失败，可结合 `error` 查看失败原因 |

### LinkGroupVo

```json
{
  "metadata": {
    "name": "string",
    "labels": { "additionalProp1": "string" },
    "annotations": { "additionalProp1": "string" },
    "creationTimestamp": "2022-11-20T13:06:38.512Z"
  },
  "spec": {
    "displayName": "string",
    "priority": 0,
    "links": ["string"]
  },
  "links": []
}
```

> `spec.links` 为旧版字段，后续应使用 `Link.spec.groupName` 建立链接与分组的关系。

### ListResult\<LinkVo>

```json
{
  "page": 0,
  "size": 0,
  "total": 0,
  "items": [],
  "first": true,
  "last": true,
  "hasNext": true,
  "hasPrevious": true,
  "totalPages": 0
}
```

### LinkFeedItemVo

```json
{
  "id": "string",
  "linkName": "string",
  "url": "string",
  "title": "string",
  "summary": "string",
  "author": "string",
  "authorUrl": "string",
  "authorLogo": "string",
  "publishedAt": "2022-11-20T13:06:38.512Z",
  "fetchedAt": "2022-11-20T13:06:38.512Z",
  "updatedAt": "2022-11-20T13:06:38.512Z"
}
```

### LinkFeedVo

```json
{
  "metadata": {
    "name": "string",
    "labels": { "additionalProp1": "string" },
    "annotations": { "additionalProp1": "string" },
    "creationTimestamp": "2022-11-20T13:06:38.512Z"
  },
  "spec": {
    "url": "string",
    "displayName": "string",
    "logo": "string",
    "description": "string",
    "priority": 0,
    "groupName": "string",
    "rss": {
      "enabled": true
    }
  },
  "status": {
    "rss": {
      "lastFetchedAt": "2022-11-20T13:06:38.512Z",
      "lastSuccessAt": "2022-11-20T13:06:38.512Z",
      "lastError": "string",
      "failureCount": 0,
      "latestPublishedAt": "2022-11-20T13:06:38.512Z",
      "itemCount": 0,
      "feeds": [
        {
          "lastFetchedAt": "2022-11-20T13:06:38.512Z",
          "lastSuccessAt": "2022-11-20T13:06:38.512Z",
          "lastError": "string",
          "failureCount": 0,
          "etag": "string",
          "lastModified": "string",
          "validatorUpdatedAt": "2022-11-20T13:06:38.512Z",
          "latestPublishedAt": "2022-11-20T13:06:38.512Z",
          "itemCount": 0
        }
      ]
    },
    "verification": {
      "lastCheckedAt": "2022-11-20T13:06:38.512Z",
      "access": {
        "state": "ACCESSIBLE",
        "checkedAt": "2022-11-20T13:06:38.512Z",
        "statusCode": 200,
        "finalUrl": "string",
        "error": "string"
      },
      "backlink": {
        "state": "FOUND",
        "checkedAt": "2022-11-20T13:06:38.512Z",
        "scanUrl": "string",
        "targetUrl": "string",
        "matchedUrl": "string",
        "error": "string"
      }
    }
  },
  "feeds": []
}
```

> `LinkFeedVo` 会保留链接状态字段，但不会公开 RSS 订阅地址：`spec.rss.feedUrls` 和 `status.rss.feeds[].url` 会被清理。

### LinkFeedItemPageVo

```json
{
  "items": [],
  "nextBeforePublishedAt": "string",
  "nextBeforeId": "string",
  "hasNext": true
}
```

### LinkFeedGroupVo

```json
{
  "metadata": {
    "name": "string",
    "labels": { "additionalProp1": "string" },
    "annotations": { "additionalProp1": "string" },
    "creationTimestamp": "2022-11-20T13:06:38.512Z"
  },
  "spec": {
    "displayName": "string",
    "priority": 0,
    "links": ["string"]
  },
  "links": []
}
```


---

## Annotations 元数据适配

根据 Halo 的[元数据表单定义文档](https://docs.halo.run/developer-guide/annotations-form/)和[模型元数据文档](https://docs.halo.run/developer-guide/theme/annotations)，此插件适配了元数据表单功能。如果你需要为链接或链接分组添加额外的自定义字段，可参考以下 TargetRef 列表：

| 对应模型 | group | kind |
| ---- | ---- | ---- |
| 链接 | `core.halo.run` | `Link` |
| 链接分组 | `core.halo.run` | `LinkGroup` |
