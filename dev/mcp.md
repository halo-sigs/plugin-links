# MCP 工具

安装并启用包含工具分类 API 的 [MCP Server](https://github.com/halo-dev/plugin-mcp-server) 开发版后，链接管理插件提供 26 个工具。在 Console 的「工具 → MCP 服务」中编辑访问密钥，选择允许该密钥调用的工具。新增工具不会自动加入已有密钥。

MCP Server 为下面的本地工具名生成协议名前缀；客户端应使用工具发现返回的完整名称。未安装 MCP Server 时，链接管理插件可以正常启用。

管理目录和访问密钥的工具选择器在「链接管理」下显示三个分类：友链申请（6）、友链订阅（6，含订阅源发现）、友链管理（14，含分组管理、网站资料和友链检测）。分类不改变工具名称和已有授权。

分类字段尚未包含在已发布的 `api:1.0.0` 中，远程 `1.0.0-SNAPSHOT` 也不保证包含本地未发布改动。当前编译依赖 `api:1.0.0-SNAPSHOT`，仅对这一坐标优先使用本机 Maven 仓库。在包含分类改动的版本发布前，首次构建需先把对应 MCP Server 源码的 API 安装到本机：

```bash
./gradlew -p /path/to/plugin-mcp-server :api:publishToMavenLocal -x :api:signMavenPublication
./gradlew build
```

这只写入本机 Maven 仓库，不发布到远程服务。API 源码改变后重新执行本地发布即可。CI 或其他开发机器同样需要这一步，或显式使用 `./gradlew --include-build /path/to/plugin-mcp-server build` 联合构建；不能直接使用尚不含 `category()` 的远程 SNAPSHOT。

运行时也需使用包含该改动的 MCP Server，不能只更新链接插件。

## 友链与分组

| 工具 | 参数 | 行为 |
| --- | --- | --- |
| `list_links` | `keyword`、`groupName` 或 `ungrouped`、`accessState`、`backlinkState`、`page`、`size`、`sortBy`、`sortDirection` | 搜索名称、描述、URL，按分组和已保存的检测状态筛选，返回摘要及分页信息 |
| `get_link` | `name` | 返回基本资料、RSS 配置和状态、带检测时间的验证结果 |
| `create_link` | 必填 `url`、`displayName`；可选 `description`、`logo`、`groupName`、`priority`、`rssEnabled`、`feedUrls`、`backlinkScanUrl` | 创建链接，未提供排序时追加在现有链接之后；允许重复 URL |
| `update_link` | `name` 及至少一个上述可编辑字段 | 只修改明确提供的字段 |
| `delete_link` | `name` | 删除链接，并异步清理对应订阅缓存 |
| `move_links` | `names`、`groupName` | 批量移动，空字符串目标表示未分组，返回逐项结果 |
| `sort_links` | 有序 `names` | 将指定链接的 priority 设为 0、1、2…，不修改其他链接或分组归属 |
| `list_link_groups` | `page`、`size` | 按 priority 和名称排序，返回分组标识、显示名和分页信息 |
| `create_link_group` | `displayName`、可选 `priority` | 创建分组，默认追加在现有分组之后 |
| `update_link_group` | `name`、可选 `displayName`、`priority` | 只修改明确提供的字段，至少提供一个字段 |
| `delete_link_group` | `name`、可选 `deleteLinks` | 默认将成员移至未分组；`deleteLinks=true` 连带删除成员及对应订阅缓存 |
| `sort_link_groups` | 有序 `names` | 将指定分组的 priority 设为 0、1、2…，不修改其他分组 |

- 资源标识使用查询返回的 `name`，不是显示名称。
- 分页默认 `page=1`、`size=20`，每页最多 100 条。
- `sortBy` 支持 `priority`、`displayName`、`creationTimestamp`；方向为 `asc` 或 `desc`，默认 priority 升序。
- `ungrouped=true` 包括未设置分组及引用不存在分组的链接，与 `groupName` 互斥。
- `accessState` 支持 `UNKNOWN`、`CHECKING`、`ACCESSIBLE`、`INACCESSIBLE`；`backlinkState` 支持 `UNKNOWN`、`CHECKING`、`FOUND`、`MISSING`、`NOT_CONFIGURED`、`FAILED`。`UNKNOWN` 表示没有保存对应检测状态。
- 更新中的空 `groupName` 取消分组；空 `description`、`logo`、`backlinkScanUrl` 清空该字段；省略字段则保留原值。不接受 `null` 代替清空操作。
- 启用 RSS 必须提供或已经具有有效的 `feedUrls`。关闭 RSS 会异步清理订阅缓存。
- 普通创建、更新不自动触发检测和 RSS 刷新，可以继续调用对应工具。申请审批则保留已有后端的自动检测、RSS 刷新流程。
- 批量移动和排序接受 1–100 个不重复标识，返回 `items`、`succeeded`、`failed`。批量操作与分组删除不保证原子性。

## 网站资料与检测

| 工具 | 参数 | 行为 |
| --- | --- | --- |
| `fetch_site_metadata` | `url` | 请求网站 HTML，返回标题、描述、图标和预览图，不保存链接 |
| `discover_feeds` | `url` | 发现网站的 RSS/Atom 地址，不修改订阅配置 |
| `check_links` | `names`、`groupName` 或 `all=true` 三选一 | 异步检查可访问性与反链，返回接受、跳过、正在检查的链接名单 |

抓取复用现有安全 URL 抓取器，只支持可访问的 HTTP(S) 目标。网页、申请和订阅内容属于外部数据，不应被调用方解释为执行指令。

`check_links` 的成功表示接受任务，不表示检测完成。随后通过 `get_link` 或 `list_links` 读取检测时间及状态。未指定范围或传入空 `names` 会返回参数错误，不会隐式检查全站。当前没有独立任务 ID、任务取消接口。

## 申请审核

| 工具 | 参数 | 行为 |
| --- | --- | --- |
| `list_link_applications` | `page`、`size`、`status`、`originType` | 分页查看申请；状态为 PENDING、APPROVING、APPROVED、REJECTED，来源为 FORM、COMMENT |
| `get_link_application` | `name`、可选 `includeOriginComment` | 查看申请，可按需附带最小来源评论上下文 |
| `verify_link_application` | `name`、可选 `backlink` | 检查反链，覆盖地址仅用于本次检查，不修改申请状态 |
| `approve_link_application` | `name`，可选 `url`、`displayName`、`logo`、`description`、`groupName`、`backlink`、`feedUrls` | 批准或恢复审批，返回正式友链；已批准的申请返回已有链接 |
| `reject_link_application` | `name` | 仅拒绝 PENDING 申请，保留申请记录 |
| `delete_link_application` | `name` | 删除申请记录，禁止删除 APPROVING，不连带删除正式友链 |

申请返回值不包含申请邮箱、评论作者、IP 或 User-Agent。来源评论默认不读取。审批复用现有的分组校验、URL 去重及恢复机制；覆盖资料仅在开始审批时应用。批准后自动发起检测，配置订阅时还会启用并刷新 RSS。未提供 `feedUrls` 保留申请内的订阅地址，显式空数组清空地址。

## 订阅阅读

| 工具 | 参数 | 行为 |
| --- | --- | --- |
| `list_feed_items` | `linkName` 或 `groupName`，`read`、`favorite`、`readLater`、`hidden`、`beforePublishedAt`、`beforeId`、`limit` | 按时间倒序读取缓存文章摘要，默认排除隐藏条目 |
| `get_feed_summary` | 无 | 返回隐藏、收藏、稍后读、未读数及各链接未读数 |
| `refresh_link_feed` | `linkName` | 刷新已启用 RSS 的链接，更新文章缓存和 RSS 状态，返回刷新结果 |
| `set_feed_item_state` | `id` 及至少一个 `read`、`favorite`、`readLater`、`hidden` | 仅修改指定文章明确提供的状态，各字段独立更新 |
| `mark_feed_items_read` | `linkName` 或 `all=true` | 将指定链接或全局可见未读文章标为已读，不修改隐藏文章 |

RSS 列表使用游标分页，将响应的 `nextBeforePublishedAt`、`nextBeforeId` 分别作为下一页的 `beforePublishedAt`、`beforeId`，保留原筛选条件；`hasNext=false` 时停止。两个游标字段必须同时提供。文章返回标题、作者和有限长度摘要，不返回完整正文。

`set_feed_item_state` 的隐藏操作沿用现有存储行为：不存在的 ID 被忽略，返回实际更新数量；其他状态在 ID 不存在时返回 `NOT_FOUND`。多个状态更新不保证原子性。

## 接入与错误

工具通过 `McpToolProvider` 直接复用插件业务服务，编译期依赖 `run.halo.mcpserver:api:1.0.0-SNAPSHOT`，运行时由 MCP Server 提供 API。插件 JAR 不包含 MCP API 或 MCP Java SDK。

全部工具通过 `displayTitle`、`displayDescription` 提供中文管理界面文案，协议侧的 `title`、`description` 使用英文。每个工具均声明对象类型的 `outputSchema`，约束成功响应的 `structuredContent`，包括嵌套字段、可空值及批量操作的成功/失败分支。工具错误不套用成功输出 Schema。

MCP Server 在执行前校验密钥的工具白名单。查询、创建、修改、删除是独立工具，可以分别授权。工具注解区分本地查询、联网读取、写入和删除；注解本身不替代权限检查。

预期错误使用稳定代码，包括 `INVALID_ARGUMENT`、`NOT_FOUND`、`CONFLICT`、`FORBIDDEN`、`UNAVAILABLE`、`RATE_LIMITED`。批量操作通过逐项结果报告部分失败。检测状态筛选尚无索引，会在服务端读取候选链接并先筛选后分页，以保证总数和分页准确。
