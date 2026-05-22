# 开发文档

## 克隆仓库

```bash
git clone git@github.com:halo-sigs/plugin-links.git

# 或者当你 fork 之后
git clone git@github.com:{your_github_id}/plugin-links.git
```

## 启动开发环境

所需环境依赖：

1. JDK 21
2. Docker
3. Node.js 24
4. pnpm 10

```bash
# macOS / Linux
./gradlew haloServer

# Windows
./gradlew.bat haloServer
```

启动完成后，访问 `http://localhost:8090/console`，默认账号密码为 `admin` / `admin`。插件 Console 页面路径为 `/console/links`。

## 前端开发

```bash
cd console

pnpm install
pnpm dev          # 开发监听模式
pnpm build        # 生产构建
pnpm lint         # ESLint（Vue/TS）
pnpm type-check   # vue-tsc --noEmit
pnpm prettier     # 格式化代码
```

## 重新生成 API 客户端

后端 Endpoint、DTO 或 Extension 字段发生变更后，需要重新生成 TypeScript API 客户端：

```bash
./gradlew generateApiClient
```

生成的文件位于 `console/src/api/generated/`，**请勿手动编辑**。

如果新增了 API 路径，需要先确认 `build.gradle` 中 `haloPlugin.openApi.groupingRules.linksV1alpha1Api.pathsToMatch` 已包含对应前缀，否则生成器不会收录这些端点。

## 运行测试

```bash
./gradlew test
```

## 构建插件

```bash
# macOS / Linux
./gradlew build

# Windows
./gradlew.bat build
```

构建产物位于 `build/libs/`。
