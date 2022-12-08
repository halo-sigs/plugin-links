# plugin-links

Halo 2.0 的链接管理插件，支持在 Console 进行管理以及为主题端提供 `/links` 页面路由。

## 开发环境

```bash
git clone git@github.com:halo-sigs/plugin-links.git

# 或者当你 fork 之后

git clone git@github.com:{your_github_id}/plugin-links.git
```

```bash
cd path/to/plugin-links
```

```bash
# macOS / Linux
./gradlew pnpmInstall

# Windows
./gradlew.bat pnpmInstall
```

```bash
# macOS / Linux
./gradlew build

# Windows
./gradlew.bat build
```

修改 Halo 配置文件：

```yaml
halo:
  plugin:
    runtime-mode: development
    classes-directories:
      - "build/classes"
      - "build/resources"
    lib-directories:
      - "libs"
    fixedPluginPath:
      - "/path/to/plugin-links"
```

## 使用方式

1. 在 [Releases](https://github.com/halo-sigs/plugin-links/releases) 下载最新的 JAR 文件。
2. 在 Halo 后台的插件管理上传 JAR 文件进行安装。

> 需要注意的是，此插件需要主题提供模板（links.html）才能访问 `/links`。

## 主题适配

目前此插件为主题端提供了 `/links` 路由，模板为 `links.html`，也提供了 [Finder API](https://docs.halo.run/developer-guide/theme/finder-apis)，可以将链接渲染到任何地方。

### 模板变量

#### 路由信息

- 模板路径：/templates/links.html
- 访问路径：/links

#### 变量

groups

##### 变量类型

List<[#LinkGroupVo](#linkgroupvo)>

##### 示例

```html
<th:block th:each="group : ${groups}">
    <h2 th:text="${group.spec.displayName}"></h2>
    <a th:each="link : ${group.links}" :key="i" th:href="${link.spec.url}" target="_blank">
        <div>
            <div>
                <img  th:src="${link.spec.logo}" th:alt="${link.spec.displayName}" />
            </div>
            <div >
                <div>
                    <p th:text="${link.spec.displayName}"></p>
                    <p th:text="${link.spec.description}"></p>
                </div>
            </div>
        </div>
    </a>
</th:block>
```

### Finder API

#### listBy(group)

##### 描述

根据 group 获取链接。

##### 参数

1. `group:string` - 分组（LinkGroup）的唯一标识 `metadata.name`。

##### 返回值

List<[#LinkVo](#linkvo)>

##### 示例

```html
<th:block th:each="link : ${linkFinder.listBy('friends')}">
    <a th:href="${link.spec.url}" target="_blank">
        <div>
            <div>
                <img  th:src="${link.spec.logo}" th:alt="${link.spec.displayName}" />
            </div>
            <div >
                <div>
                    <p th:text="${link.spec.displayName}"></p>
                    <p th:text="${link.spec.description}"></p>
                </div>
            </div>
        </div>
    </a>
</th:block>
```

#### groupBy()

##### 描述

获取所有分组，包含链接集合。

##### 参数

无

##### 返回值

List<[#LinkGroupVo](#linkgroupvo)>

##### 示例

```html
<th:block th:each="group : ${linkFinder.groupBy()}">
    <h2 th:text="${group.spec.displayName}"></h2>
    <a th:each="link : ${group.links}" :key="i" th:href="${link.spec.url}" target="_blank">
        <div>
            <div>
                <img  th:src="${link.spec.logo}" th:alt="${link.spec.displayName}" />
            </div>
            <div >
                <div>
                    <p th:text="${link.spec.displayName}"></p>
                    <p th:text="${link.spec.description}"></p>
                </div>
            </div>
        </div>
    </a>
</th:block>
```

### 类型定义

#### LinkVo

```json
{
  "metadata": {
    "name": "string",                                   // 唯一标识
    "labels": {
      "additionalProp1": "string"
    },
    "annotations": {
      "additionalProp1": "string"
    },
    "creationTimestamp": "2022-11-20T13:06:38.512Z",    // 创建时间
  },
  "spec": {
    "url": "string",                                    // 链接
    "displayName": "string",                            // 显示名称
    "description": "string",                            // 描述
    "logo": "string",                                   // Logo
    "priority": 0,                                      // 排序字段
  }
}
```

#### LinkGroupVo

```json
{
  "metadata": {
    "name": "string",                                   // 唯一标识
    "labels": {
      "additionalProp1": "string"
    },
    "annotations": {
      "additionalProp1": "string"
    },
    "creationTimestamp": "2022-11-20T13:06:38.512Z",    // 创建时间
  },
  "spec": {
    "displayName": "string",                            // 显示名称
    "priority": 0,                                      // 排序字段
    "links": [                                          // 链接集合，即 Link 的 metadata.name 的集合
      "string"
    ]
  },
  "links": "List<#LinkVo>"                              // 链接集合
}
```
