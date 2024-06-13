# plugin-links

Halo 2.0 的链接管理插件，支持在 Console 进行管理以及为主题端提供 `/links` 页面路由。

## 使用方式

1. 下载，目前提供以下两个下载方式：
    - GitHub Releases：访问 [Releases](https://github.com/halo-sigs/plugin-links/releases) 下载 Assets 中的 JAR 文件。
    - Halo 应用市场：<https://halo.run/store/apps/app-hfbQg>
2. 安装，插件安装和更新方式可参考：<https://docs.halo.run/user-guide/plugins>
3. 安装完成之后，访问 Console 左侧的**链接**菜单项，即可进行管理。
4. 前台访问地址为 `/links`，需要注意的是，此插件需要主题提供模板（links.html）才能访问 `/links`。

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

#### 变量

linksTitle

##### 变量类型

String

##### 示例

```html
<h2 th:text="${linksTitle}"></h2>
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

### 评论适配

主题开发者可以参考 [自定义标签](https://docs.halo.run/developer-guide/theme/template-tag/#halocomment)，来为友情链接接入评论功能。

#### 参数值

group：plugin.halo.run

kind: Plugin

name: ${pluginName}

#### 示例

```html
<div th:if="${haloCommentEnabled}">
    <halo:comment
        group="plugin.halo.run"
        kind="Plugin"
        th:attr="name=${pluginName}"
    />
</div>
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

### Annotations 元数据适配

根据 Halo 的[元数据表单定义文档](https://docs.halo.run/developer-guide/annotations-form/)和[模型元数据文档](https://docs.halo.run/developer-guide/theme/annotations)，Halo 支持为部分模型的表单添加元数据表单，此插件同样适配了此功能，如果你作为主题开发者，需要为链接或者链接分组添加额外的字段，可以参考上述文档并结合下面的 TargetRef 列表进行适配。

| 对应模型   | group            | kind       |
| ---------- | ---------------- | ---------- |
| 链接       | core.halo.run | Link       |
| 链接分组 | core.halo.run | LinkGroup |
