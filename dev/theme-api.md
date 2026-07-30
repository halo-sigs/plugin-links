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

插件设置中的“友链申请”总开关默认关闭。总开关和“允许访客提交”子开关同时开启时，
`linkApplicationEnabled` 为 `true`，主题才应展示申请入口。

待审核申请达到管理员配置的容量时，`linkApplicationEnabled` 仍为 `true`，验证码图片
端点也保持可用。容量可能随审核进度动态释放，主题无需读取或展示容量值，提交结果以
`POST /links/apply` 的实际响应为准。

主题展示申请表单时，必须加载内置图形验证码，并随表单提交 `captchaCode`。

验证码图片端点为同源的 `GET /links/captcha`。开启访客申请时，它返回固定
`160 x 48` PNG，并通过路径为 `/links`、有效期五分钟、`HttpOnly`、`SameSite=Lax`
的 Cookie 关联一次性挑战；HTTPS 请求还会设置 `Secure`。图片响应禁止缓存。每次成功
加载或刷新图片都会覆盖 Cookie 并使上一张图片失效，提交尝试无论成功与否也会使当前
挑战和 Cookie 失效。

提交端点为同源、CSRF 保护的 `POST /links/apply`，仅接受
`application/x-www-form-urlencoded`。它不接受 JSON 请求体；主题可以使用普通 HTML
表单完成提交，也可以在同一端点上通过内容协商获取 JSON 结果。

字段如下：

| 字段 | 必填 | 说明 |
| ---- | ---- | ---- |
| `url` | 是 | 申请网站的 HTTP/HTTPS 地址 |
| `displayName` | 是 | 网站名称 |
| `logo` | 否 | Logo 的 HTTP/HTTPS 地址 |
| `description` | 否 | 网站描述 |
| `email` | 否 | 联系邮箱 |
| `backlink` | 否 | 反链页面的 HTTP/HTTPS 地址 |
| `feedUrls` | 否 | RSS/Atom 的 HTTP/HTTPS 地址，一行一个 |
| `captchaCode` | 是 | 当前图片中的五位英文字母或数字，不区分大小写 |
| `_csrf` | 是 | 使用模板变量 `csrfToken` |

HTML 示例：

```html
<form
    id="link-application-form"
    th:if="${linkApplicationEnabled}"
    method="post"
    th:action="@{/links/apply}"
>
    <input type="hidden" name="_csrf" th:value="${csrfToken}">
    <input name="url" required th:value="${param.field == 'url' ? param.value : ''}">
    <input name="displayName" required
           th:value="${param.field == 'displayName' ? param.value : ''}">
    <input name="logo">
    <textarea name="description"></textarea>
    <input name="email" type="email">
    <input name="backlink">
    <textarea name="feedUrls" placeholder="每行一个订阅地址"></textarea>
    <p id="captcha-help">请输入图片中的五位字符。看不清时可刷新页面换一张。</p>
    <img
        id="link-application-captcha"
        src="/links/captcha"
        alt="友链申请图形验证码"
        width="160"
        height="48"
        aria-describedby="captcha-help"
    >
    <input
        name="captchaCode"
        required
        minlength="5"
        maxlength="5"
        autocomplete="off"
        aria-describedby="captcha-help"
    >
    <button id="link-application-submit" type="submit">申请友链</button>
    <p id="link-application-result" role="status" aria-live="polite"></p>
</form>
```

图片直接加载即可设置关联 Cookie，所以以上表单在禁用 JavaScript 时仍可完整提交。
如需提供不丢失表单内容的刷新操作，可增加键盘可操作的按钮：

```html
<button type="button" id="refresh-link-captcha">换一张验证码</button>

<script>
  const image = document.querySelector('#link-application-captcha')
  document.querySelector('#refresh-link-captcha').addEventListener('click', () => {
    image.src = `/links/captcha?refresh=${Date.now()}`
  })
</script>
```

每次刷新都会使旧图片失效，多标签页也会相互覆盖同一 Cookie。验证码仅提供图形挑战，
不提供音频或其他非视觉挑战；主题应保留说明文字和键盘可操作的刷新按钮。

不使用 JavaScript 时，端点继续返回 `303 See Other` 并重定向回 `/links`：

- 成功：`applied=success`
- 验证或限流失败：`applied=error&message=...`；字段错误还包含 `field`，并在有原值时包含
  `value`，主题可据此回填
- 验证码缺失、格式错误、答案错误、过期或重放统一返回
  `applied=error&field=captchaCode&message=验证码错误或已过期，请重新输入`，且不会返回
  `value` 或其他已提交字段；主题应重新加载图片
- 待审核申请达到容量：
  `applied=error&message=待审核申请数量已达上限，请稍后再试`
- 容量设置或待审核数量暂时无法读取：
  `applied=error&message=暂时无法提交，请稍后再试`
- 两种容量错误都不包含 `field`、`value` 或实际容量；已经验证的 CAPTCHA 和提交频率
  额度仍会被消耗
- 功能关闭：`applied=disabled&message=友链申请功能暂未开放`

### 异步提交

请求头中的 `Accept` 明确让 `application/json` 比 `text/html` 具有更高优先级时，同一
端点返回 JSON。未发送 `Accept`、发送 `*/*`、HTML 优先或两者优先级相同时，仍返回上述
`303`；两种表示都不可接受时返回空的 `406 Not Acceptable`，且不会处理申请。

JSON 响应使用以下结构，`field` 仅在错误与具体字段有关时出现：

```json
{
  "status": "error",
  "code": "VALIDATION_FAILED",
  "field": "url",
  "message": "URL格式错误"
}
```

`code` 用于程序分支，`message` 是可直接展示的文字，不应作为程序判断条件。代码集合
是稳定但可扩展的，主题必须为未知代码保留通用处理。

| HTTP 状态 | `code` | `field` | 含义 |
| --------- | ------ | ------- | ---- |
| `201` | `APPLICATION_CREATED` | 无 | 申请已创建 |
| `403` | `APPLICATION_DISABLED` | 无 | 访客申请未开启 |
| `422` | `INVALID_CAPTCHA` | `captchaCode` | CAPTCHA 无效或过期 |
| `422` | `VALIDATION_FAILED` | 具体字段 | 表单字段校验失败 |
| `409` | `DUPLICATE_APPLICATION` | `url` | 链接已经申请 |
| `429` | `RATE_LIMITED` | 无 | 提交过于频繁；响应同时包含准确的 `Retry-After` |
| `409` | `CAPACITY_REACHED` | 无 | 待审核申请达到容量 |
| `503` | `APPLICATION_UNAVAILABLE` | 无 | 暂时无法完成申请 |
| `415` | `UNSUPPORTED_MEDIA_TYPE` | 无 | 请求体不是表单编码 |

协商得到的 `303` 与 JSON 结果都包含 `Vary: Accept`；JSON 响应还包含
`Cache-Control: no-store`。请求体不是表单编码时，仅明确选择 JSON 的客户端获得上述
`415` JSON 结构，其他客户端获得不承诺插件结构的 `415`。JSON 结果不会返回提交值、
申请对象或通用 `data` 字段。

下面的完整示例直接序列化现有表单，因此 `_csrf` 的来源仍由主题模板决定；它不要求
主题通过特定的 `meta` 或 `data-*` 属性传递 CSRF Token 或
`linkApplicationEnabled`。主题应只在该表单已经渲染时加载这段脚本：

```js
const form = document.querySelector('#link-application-form')
const submitButton = document.querySelector('#link-application-submit')
const result = document.querySelector('#link-application-result')
const captchaImage = document.querySelector('#link-application-captcha')
const defaultButtonText = submitButton.textContent

const captchaConsumedCodes = new Set([
  'INVALID_CAPTCHA',
  'VALIDATION_FAILED',
  'DUPLICATE_APPLICATION',
  'RATE_LIMITED',
  'CAPACITY_REACHED',
  'APPLICATION_UNAVAILABLE',
])

function refreshCaptcha() {
  captchaImage.src = `/links/captcha?refresh=${Date.now()}`
}

form.addEventListener('submit', async (event) => {
  event.preventDefault()
  submitButton.disabled = true
  submitButton.textContent = '提交中…'
  result.textContent = ''

  try {
    const response = await fetch(form.action, {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        Accept: 'application/json',
        'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8',
      },
      body: new URLSearchParams(new FormData(form)),
    })

    const contentType = response.headers.get('content-type') || ''
    if (!contentType.toLowerCase().startsWith('application/json')) {
      throw new Error('Non-JSON response')
    }

    const payload = await response.json()
    const isEnvelope =
      (payload.status === 'success' || payload.status === 'error') &&
      typeof payload.code === 'string' &&
      typeof payload.message === 'string'
    if (!isEnvelope) {
      throw new Error('Unexpected JSON response')
    }

    result.textContent = payload.message || '提交未完成，请稍后重试'

    if (
      response.status === 201 &&
      payload.status === 'success' &&
      payload.code === 'APPLICATION_CREATED'
    ) {
      form.reset()
      refreshCaptcha()
      return
    }

    if (payload.field) {
      const field = form.elements.namedItem(payload.field)
      if (field instanceof HTMLElement) {
        field.focus()
      }
    }

    if (captchaConsumedCodes.has(payload.code)) {
      refreshCaptcha()
    }
  } catch {
    result.textContent = '暂时无法提交，请稍后再试'
  } finally {
    submitButton.disabled = false
    submitButton.textContent = defaultButtonText
  }
})
```

验证码错误以及验证码已经验证后的所有业务失败都会使当前挑战失效，示例因此刷新图片；
成功后也会重置表单并取得新挑战。如果主题成功后关闭表单，则无需立即刷新。

Halo Security 会在处理器之前校验 CSRF。无效或缺失的 Token 保持平台原生 `403`，不承诺
上述插件 JSON 结构。`406`、平台错误、网络错误以及其他非 JSON 响应也在结构之外；
异步主题应像示例一样显示自己的通用提示，不解析重定向 URL 或任意响应正文。

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
