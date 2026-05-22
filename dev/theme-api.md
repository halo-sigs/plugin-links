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

Finder API 由 `linkFinder` 对象提供，可在主题模板的任意位置使用，无需依赖 `/links` 路由页面。

### groupBy()

获取全部分组及其链接。存在未分组链接时，会追加一个 `metadata.name` 为 `ungrouped` 的虚拟分组。

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

---

### listBy(group)

根据分组获取链接列表。

**参数**：

1. `group: string` — 链接分组名称，对应 `LinkGroupVo.metadata.name`。传入 `ungrouped` 可获取未分组链接。

**返回值**：`List<LinkVo>`

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

---

### random(maxSize)

随机获取链接列表。

**参数**：

1. `maxSize: int` — 返回数量，取值范围为 `1` 到 `100`

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

---

### count()

获取链接总数。

**参数**：无

**返回值**：`Integer`

**示例**：

```html
<span th:text="${linkFinder.count()}"></span>
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
  }
}
```

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
  "links": "List<LinkVo>"
}
```

> `spec.links` 为旧版字段，后续应使用 `Link.spec.groupName` 建立链接与分组的关系。

### ListResult\<LinkVo>

```json
{
  "page": 0,
  "size": 0,
  "total": 0,
  "items": "List<LinkVo>",
  "first": true,
  "last": true,
  "hasNext": true,
  "hasPrevious": true,
  "totalPages": 0
}
```

---

## Annotations 元数据适配

根据 Halo 的[元数据表单定义文档](https://docs.halo.run/developer-guide/annotations-form/)和[模型元数据文档](https://docs.halo.run/developer-guide/theme/annotations)，此插件适配了元数据表单功能。如果你需要为链接或链接分组添加额外的自定义字段，可参考以下 TargetRef 列表：

| 对应模型 | group | kind |
| ---- | ---- | ---- |
| 链接 | `core.halo.run` | `Link` |
| 链接分组 | `core.halo.run` | `LinkGroup` |
