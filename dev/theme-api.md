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
| `csrfToken` | `String` | 当前请求的 CSRF token，可用于友链申请表单的隐藏字段 |
| `linkApplicationEnabled` | `Boolean` | 友链申请总开关与访客提交子开关同时开启时为 `true` |
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

## 访客友链申请

插件提供两套职责明确的访客申请接口：

- 原生 Form：适合同源主题页面，使用 Cookie CAPTCHA、CSRF 和 `303` 重定向。
- REST API：适合页面脚本、小程序和服务端集成，使用显式 CAPTCHA、JSON 和 Problem Details。

两套接口共用访客提交开关、验证码限流、提交限流、字段校验、重复检测、待审核容量、
持久化和通知流程；创建的申请来源都记录为 `FORM`。

### 原生 Form

当模板变量 `linkApplicationEnabled` 为 `true` 时，主题可以展示申请表单。适配需要使用
以下两个同源端点：

- `GET /links/apply/captcha`：获取验证码图片
- `POST /links/apply/submit`：提交申请，只接受
  `application/x-www-form-urlencoded`

表单必须包含 `_csrf` 和 `captchaCode`。验证码图片直接使用 `<img>` 加载即可，关联
Cookie 由浏览器自动处理。

#### 表单字段

| 字段 | 必填 | 说明 |
| ---- | ---- | ---- |
| `url` | 是 | 申请网站的 HTTP/HTTPS 地址 |
| `displayName` | 是 | 网站名称 |
| `logo` | 否 | Logo 的 HTTP/HTTPS 地址 |
| `description` | 否 | 网站描述 |
| `email` | 否 | 联系邮箱 |
| `backlink` | 否 | 反链页面的 HTTP/HTTPS 地址 |
| `feedUrls` | 否 | RSS/Atom 的 HTTP/HTTPS 地址，一行一个 |
| `captchaCode` | 是 | 图片中的五位字符，不区分大小写 |
| `_csrf` | 是 | 模板变量 `csrfToken` |

#### HTML 表单

普通表单提交后，插件会重定向回 `/links`。主题通过 `applied`、`message`、`field` 和
`value` 查询参数展示结果或回填出错字段。

```html
<p th:if="${param.applied == 'success'}" role="status">
    申请已提交，等待审核。
</p>
<p
    th:if="${param.applied == 'error' || param.applied == 'disabled'}"
    th:text="${param.message ?: '提交失败，请稍后再试'}"
    role="alert"
></p>

<form
    id="link-application-form"
    th:if="${linkApplicationEnabled}"
    method="post"
    th:action="@{/links/apply/submit}"
>
    <input type="hidden" name="_csrf" th:value="${csrfToken}">
    <label>
        网站地址
        <input name="url" type="url" required
               th:value="${param.field == 'url' ? param.value : ''}">
    </label>
    <label>
        网站名称
        <input name="displayName" required
               th:value="${param.field == 'displayName' ? param.value : ''}">
    </label>
    <label>
        Logo 地址
        <input name="logo" type="url"
               th:value="${param.field == 'logo' ? param.value : ''}">
    </label>
    <label>
        网站描述
        <textarea name="description"
                  th:text="${param.field == 'description' ? param.value : ''}"></textarea>
    </label>
    <label>
        联系邮箱
        <input name="email" type="email"
               th:value="${param.field == 'email' ? param.value : ''}">
    </label>
    <label>
        反链地址
        <input name="backlink" type="url"
               th:value="${param.field == 'backlink' ? param.value : ''}">
    </label>
    <label>
        订阅地址
        <textarea name="feedUrls" placeholder="每行一个"
                  th:text="${param.field == 'feedUrls' ? param.value : ''}"></textarea>
    </label>

    <img
        id="link-application-captcha"
        src="/links/apply/captcha"
        alt="友链申请验证码"
        width="160"
        height="48"
    >
    <label>
        验证码
        <input
            name="captchaCode"
            required
            minlength="5"
            maxlength="5"
            autocomplete="off"
        >
    </label>

    <button type="submit">申请友链</button>
    <p id="link-application-result" role="status" aria-live="polite"></p>
</form>
```

提交失败时直接展示 `message` 即可；当 `field` 指向某个字段时，可以使用 `value`
回填该字段。验证码错误不会返回原值，页面重新加载后会自动获取新的验证码。

`POST /links/apply/submit` 不协商响应格式。无论 `Accept` 是什么，所有插件业务结果都
使用上述重定向契约；非 `application/x-www-form-urlencoded` 请求返回 `415`。需要
结构化响应的页面脚本应改用下面的 REST API。

### REST API

REST API 位于 Halo 公共 API 组：

- `POST /apis/api.link.halo.run/v1alpha1/link-applications/captcha`
- `POST /apis/api.link.halo.run/v1alpha1/link-applications`

获取 CAPTCHA 成功时返回：

```json
{
  "challengeId": "opaque-challenge-id",
  "image": "data:image/png;base64,...",
  "expiresInSeconds": 300
}
```

`image` 可以直接赋给 `<img src>`。每个挑战五分钟内有效且只能验证一次；任何已解码的
提交尝试都会消费其挑战，因此提交失败后也必须重新获取。重复获取 REST CAPTCHA 不会
主动使之前的挑战失效。

申请请求只接受 `application/json`：

| 字段 | 必填 | 说明 |
| ---- | ---- | ---- |
| `url` | 是 | 申请网站的 HTTP/HTTPS 地址 |
| `displayName` | 是 | 网站名称 |
| `logo` | 否 | Logo 的 HTTP/HTTPS 地址 |
| `description` | 否 | 网站描述 |
| `email` | 否 | 联系邮箱 |
| `backlink` | 否 | 反链页面的 HTTP/HTTPS 地址 |
| `feedUrls` | 否 | RSS/Atom 地址字符串数组 |
| `challengeId` | 是 | CAPTCHA 响应中的挑战标识 |
| `captchaCode` | 是 | 图片中的五位字符，不区分大小写 |

成功响应为 `201 Created`，不包含提交字段，也不返回 `Location`：

```json
{
  "id": "link-app-...",
  "status": "PENDING"
}
```

#### 浏览器 `fetch` 示例

浏览器调用不携带 Cookie。跨域能否调用由 Halo 对 `/apis/**` 的有效 CORS 配置决定，
插件不维护额外的来源白名单。

```js
const apiBase = 'https://halo.example/apis/api.link.halo.run/v1alpha1'
const captchaImage = document.querySelector('#rest-captcha')
const result = document.querySelector('#application-result')
let challengeId = null

async function refreshCaptcha() {
  const response = await fetch(`${apiBase}/link-applications/captcha`, {
    method: 'POST',
    credentials: 'omit',
  })
  if (!response.ok) throw new Error('CAPTCHA unavailable')

  const payload = await response.json()
  challengeId = payload.challengeId
  captchaImage.src = payload.image
}

async function submitApplication(fields, captchaCode) {
  try {
    const response = await fetch(`${apiBase}/link-applications`, {
      method: 'POST',
      credentials: 'omit',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        ...fields,
        feedUrls: fields.feedUrls ?? [],
        challengeId,
        captchaCode,
      }),
    })

    if (response.status === 201) {
      const created = await response.json()
      result.textContent = `申请已提交：${created.id}`
      return
    }

    const contentType = response.headers.get('content-type') || ''
    if (!contentType.includes('application/problem+json')) {
      throw new Error('Unexpected response')
    }

    const problem = await response.json()
    const problemKey = `${problem.status} ${problem.type}`
    const messages = {
      '400 https://halo.run/probs/invalid-link-application': '请检查申请内容',
      '400 https://halo.run/probs/invalid-link-application-captcha': '验证码错误或已过期',
      '403 https://halo.run/probs/link-application-disabled': '友链申请暂未开放',
      '409 https://halo.run/probs/duplicate-link-application': '该链接已经申请',
      '409 https://halo.run/probs/link-application-capacity-reached': '待审核申请已满',
      '429 https://halo.run/probs/request-not-permitted': '请求过于频繁',
      '503 https://halo.run/probs/link-application-unavailable': '服务暂时不可用',
    }
    result.textContent = messages[problemKey] ?? '暂时无法提交，请稍后再试'
  } catch {
    result.textContent = '暂时无法提交，请稍后再试'
  } finally {
    challengeId = null
    await refreshCaptcha().catch(() => {
      captchaImage.removeAttribute('src')
    })
  }
}

refreshCaptcha()
```

Halo 使用 `application/problem+json` 返回错误。客户端应以 `status + type` 做程序判断，
把 `detail` 仅作为可展示文案；字段校验错误还包含 `errors` 字符串数组，限流错误包含
正数 `retryAfterSeconds`。稳定类型如下：

| 状态 | `type` |
| ---- | ---- |
| `400` | `https://halo.run/probs/invalid-link-application` |
| `400` | `https://halo.run/probs/invalid-link-application-captcha` |
| `403` | `https://halo.run/probs/link-application-disabled` |
| `409` | `https://halo.run/probs/duplicate-link-application` |
| `409` | `https://halo.run/probs/link-application-capacity-reached` |
| `429` | `https://halo.run/probs/request-not-permitted` |
| `503` | `https://halo.run/probs/link-application-unavailable` |

匿名权限只允许创建申请和 CAPTCHA，不提供查询、修改、取消或审核接口，也没有幂等键。
如果客户端未收到已经成功持久化的 `201`，再次提交会按现有重复规则处理，可能返回
`409`。CAPTCHA、限流和创建协调状态均保存在当前 Halo 进程中；多实例部署需要保证相关
请求落到合适的实例。接口是通用 HTTP 协议，不提供特定小程序平台的 SDK。

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
