# Halo AI Foundation SDK 调用指南

本文档面向在 Halo 插件中调用模型能力的开发者。

## 快速开始

调用方插件需要依赖公开 API，并在运行时声明依赖本插件：

```gradle
repositories {
    mavenLocal()
}

dependencies {
    compileOnly platform('run.halo.tools.platform:plugin:2.23.0')
    compileOnly 'run.halo.app:api'
    compileOnly 'run.halo.aifoundation:api:1.0.0-SNAPSHOT'
}
```

```yaml
spec:
  pluginDependencies:
    ai-foundation: ">=1.0.0-SNAPSHOT"
```

开发本插件时，可先执行：

```bash
./gradlew :api:publishToMavenLocal
```

## 获取服务

`AiModelService` 是调用入口。调用方插件应通过 Halo 的 `ExtensionGetter` 获取启用的服务实现：

```java
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.AiModelService;
import run.halo.app.plugin.extensionpoint.ExtensionGetter;

@Service
@RequiredArgsConstructor
public class MyAiService {

    private final ExtensionGetter extensionGetter;

    private Mono<AiModelService> aiModelService() {
        return extensionGetter.getEnabledExtension(AiModelService.class);
    }
}
```

常用入口：

| 方法 | 说明 |
| --- | --- |
| `languageModel(String modelName)` | 获取指定语言模型 |
| `embeddingModel(String modelName)` | 获取指定嵌入模型 |
| `defaultLanguageModel()` | 获取默认语言模型 |
| `defaultEmbeddingModel()` | 获取默认嵌入模型 |
| `listModels()` | 列出可用模型 |
| `listProviders()` | 列出可用供应方 |

推荐在业务服务中保持响应式调用，不要在 WebFlux 请求线程里直接 `block()`。如果调用点本身是阻塞式任务或后台批处理，可以在调用方自己的调度边界内阻塞。

```java
public Mono<String> summarize(String modelName, String content) {
    return aiModelService()
        .flatMap(service -> service.languageModel(modelName))
        .flatMap(model -> model.generateText(GenerateTextRequest.builder()
            .system("你负责生成简短摘要。")
            .prompt(content)
            .maxOutputTokens(300)
            .build()))
        .map(GenerateTextResult::getText);
}
```

可以使用默认模型：

```java
return aiModelService()
    .flatMap(AiModelService::defaultLanguageModel)
    .flatMap(model -> model.generateText("生成一句站点欢迎语"))
    .map(GenerateTextResult::getText);
```

## 常用类型

公开 API 按能力分包。通常只需要下面这些类型：

| 类型 | 用途 |
| --- | --- |
| `AiModelService` | 获取语言模型、嵌入模型和模型列表 |
| `LanguageModel` | 文本生成和流式文本生成 |
| `GenerateTextRequest` | 文本生成请求 |
| `GenerateTextResult` | 文本生成结果 |
| `StreamTextResult` | 流式文本结果 |
| `ModelMessage` / `ModelMessagePart` | 多轮消息和消息内容 part |
| `OutputSpec` / `JsonSchema` | 结构化输出和工具入参 schema |
| `ToolDefinition` / `ToolChoice` | 工具定义和工具选择策略 |
| `StopCondition` / `PreparedStep` | 多步骤调用控制 |
| `ReasoningOptions` | 推理能力控制 |
| `GenerationTimeouts` / `CancellationSource` | 超时和取消 |
| `EmbeddingModel` / `EmbeddingRequest` / `EmbeddingResponse` | 嵌入调用 |
| `EmbeddingUtils` | 向量工具函数 |
| `ProviderOptions` | 高级 provider 原生选项 |

## 生成文本

最简单的调用方式：

```java
return aiModelService()
    .flatMap(service -> service.languageModel("deepseek-chat-prod"))
    .flatMap(model -> model.generateText("请用一句话介绍 Halo CMS"))
    .map(result -> result.getText());
```

需要更多控制时使用 `GenerateTextRequest`。`prompt` 和 `messages` 二选一，系统提示词使用顶层 `system`。

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .system("你是一个回答简洁的助手。")
    .messages(List.of(
        ModelMessage.user("请介绍 Halo CMS")
    ))
    .temperature(0.7)
    .topP(0.9)
    .maxOutputTokens(1024)
    .seed(42)
    .maxRetries(2)
    .reasoning(ReasoningOptions.disabled())
    .build();

return model.generateText(request)
    .map(result -> result.getText());
```

`GenerateTextResult` 中常用字段：

| 字段 | 说明 |
| --- | --- |
| `text` | 汇总后的文本 |
| `content` | 标准化后的内容 part |
| `reasoningText` | 模型返回的推理文本，如果存在 |
| `reasoning` | 推理内容 part 列表，可读取每段推理文本和 provider 附加信息 |
| `output` | 结构化输出解析结果 |
| `usage` / `totalUsage` | Token 使用量 |
| `warnings` | 可恢复问题或供应方能力差异提示 |
| `request` / `response` | 本次调用的标准化请求和响应元数据 |
| `providerMetadata` | provider 原生元数据，不包含标准化的模型 ID、响应 ID 等字段 |
| `steps` | 多步骤调用明细 |

### 输入方式

`GenerateTextRequest` 有两种输入方式：

| 输入 | 适用场景 |
| --- | --- |
| `prompt` | 单轮用户输入，最简单 |
| `messages` | 多轮上下文，需要保留用户、助手、工具响应等历史 |

`prompt` 和 `messages` 不能同时传。`system` 是顶层系统提示词，可与两种输入方式搭配。

```java
GenerateTextRequest singleTurn = GenerateTextRequest.builder()
    .system("你是站点运营助手。")
    .prompt("为这篇文章生成 SEO 描述")
    .build();

GenerateTextRequest conversation = GenerateTextRequest.builder()
    .system("你是客服助手。")
    .messages(List.of(
        ModelMessage.user("Halo 支持插件吗？"),
        ModelMessage.assistant("支持，Halo 提供插件机制。"),
        ModelMessage.user("那我如何调用模型能力？")
    ))
    .build();
```

### 结果处理

普通业务通常读取 `getText()`。如果要保存完整上下文，可以保存 `getResponse().getMessages()` 或每一步的 `getSteps()`。

```java
return model.generateText(request)
    .map(result -> {
        if (!result.getWarnings().isEmpty()) {
            log.warn("Generation warnings: {}", result.getWarnings());
        }
        return result.getText();
    });
```

### 推理结果

如果模型返回推理内容，SDK 会把推理和最终回答分开。调用方正常读取：

```java
return model.generateText(request)
    .map(result -> {
        String answer = result.getText();
        String thinking = result.getReasoningText();
        return answer;
    });
```

`getText()` 始终表示最终回答；已识别的推理内容不会混在回答文本中。需要更细粒度信息时读取 `getReasoning()`，其中每个 `ReasoningPart` 都有 `getText()`。不要依赖 provider 原生字段名读取推理内容，provider 原生字段只用于底层适配和必要的续写上下文。

### 结果元数据

结果中的元数据分为两层：

| 字段 | 使用方式 |
| --- | --- |
| `getRequest()` | 标准化请求元数据，例如本次调用使用的模型信息和 SDK 诊断信息 |
| `getResponse()` | 标准化响应元数据，例如响应 ID、模型 ID、响应消息、headers/body |
| `getProviderMetadata()` | provider 原生附加信息，字段形态由 provider 决定 |

业务代码需要响应 ID 或模型 ID 时优先读取 `result.getResponse().getId()` 和 `result.getResponse().getModel()`。`providerMetadata` 只适合调试或 provider 特有能力，不应作为标准字段来源。

如果请求包含工具调用，`steps` 会记录每一步模型输出、工具调用、工具结果和 usage。最终 `text` 是最后汇总后的文本。

## 流式文本

`streamText` 返回 `StreamTextResult`，可按需消费完整 part 流、纯文本流或结构化片段流：

```java
StreamTextResult result = model.streamText(GenerateTextRequest.builder()
    .prompt("写一段 Halo 插件开发简介")
    .build());

return result.textStream()
    .doOnNext(delta -> log.info("delta={}", delta))
    .then(result.result());
```

完整 part 流使用 `fullStream()`，其中 `text-start`、`text-delta`、`text-end`、`reasoning-start`、`reasoning-delta`、`reasoning-end` 等块会保持独立闭合。

常见消费方式：

| 方法 | 用途 |
| --- | --- |
| `fullStream()` | 接收所有标准化事件，包括文本、推理、工具、source、file、finish、error |
| `textStream()` | 只接收文本 delta，适合直接推给前端 |
| `partialOutputStream()` | 结构化对象的中间状态 |
| `elementStream()` | 结构化数组的元素流 |
| `result()` | 流结束后的完整 `GenerateTextResult` |

如果要把完整事件透传给前端，可以直接订阅 `fullStream()`：

```java
StreamTextResult stream = model.streamText(request);

return stream.fullStream()
    .doOnNext(part -> log.debug("part={}", part.getType()))
    .then(stream.result());
```

常见 part 类型：

| 类型 | 含义 |
| --- | --- |
| `start` / `finish` | 整体开始和结束 |
| `start-step` / `finish-step` | 单个模型步骤开始和结束 |
| `text-start` / `text-delta` / `text-end` | 文本块 |
| `reasoning-start` / `reasoning-delta` / `reasoning-end` | 推理块 |
| `tool-call` / `tool-result` / `tool-error` | 工具调用、结果和错误 |
| `source` / `file` | 模型返回的引用来源或文件 |
| `error` / `abort` | 错误或取消 |

流式调用一旦已经向调用方发出事件，就不适合在 SDK 层自动重试；需要重试的场景建议使用非流式 `generateText`。

## 结构化输出

结构化输出通过 `OutputSpec` 声明。调用方优先使用 `JsonSchema` 与 `OutputSpec`，不需要手写提示词解析 JSON。

```java
Map<String, Object> schema = JsonSchema.object()
    .property("title", JsonSchema.string())
    .property("summary", JsonSchema.string())
    .property("tags", JsonSchema.array(JsonSchema.string().build()))
    .required("title", "summary")
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("总结 Halo CMS，并输出标题、摘要和标签")
    .output(OutputSpec.object(schema))
    .build();

return model.generateText(request)
    .map(result -> (Map<?, ?>) result.getOutput());
```

可用输出类型：

| 工厂方法 | 说明 |
| --- | --- |
| `OutputSpec.object(schema)` | 输出 JSON 对象 |
| `OutputSpec.array(elementSchema)` | 输出 JSON 数组 |
| `OutputSpec.choice(values)` | 输出枚举字符串 |
| `OutputSpec.json()` | 输出任意 JSON 值 |

如果结构化输出校验失败，会抛出 `StructuredOutputValidationException`，异常中包含输出类型、原始文本、校验路径和步骤信息。

### 使用 Java 类型生成 Schema

如果你的输出结构可以用 record 或简单 POJO 描述，可以直接使用 Java 类型生成 schema：

```java
record ArticleSummary(String title, String summary, List<String> tags) {
}

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("总结这篇文章")
    .output(OutputSpec.object(ArticleSummary.class))
    .build();

return model.generateText(request)
    .map(result -> (ArticleSummary) result.getOutput());
```

数组输出：

```java
record TodoItem(String title, String priority) {
}

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("从会议纪要里提取待办事项")
    .output(OutputSpec.array(TodoItem.class))
    .build();
```

枚举输出：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("判断这条评论情绪：内容很好")
    .output(OutputSpec.choice(List.of("positive", "neutral", "negative")))
    .build();
```

### 校验失败处理

```java
return model.generateText(request)
    .onErrorResume(StructuredOutputValidationException.class, error -> {
        log.warn("Invalid structured output at {}: {}", error.getValidationPath(),
            error.getOutputText());
        return Mono.empty();
    });
```

结构化输出依赖模型遵循指令。本插件会尽量使用 provider 支持的结构化参数，并在本地解析和校验最终结果；如果 provider 不支持强约束，仍可能因为模型输出不合规而失败。

## 工具调用

工具通过 `ToolDefinition` 声明。建议使用 `ToolDefinition.builder()` 和 `ToolChoice`，这样 IDE 能提供可用枚举和方法提示。

```java
ToolDefinition weatherTool = ToolDefinition.builder()
    .name("get_weather")
    .description("查询城市天气")
    .inputSchema(JsonSchema.object()
        .property("city", JsonSchema.string())
        .required("city")
        .build())
    .executor(context -> {
        String city = (String) context.getInput().get("city");
        return Mono.just(Map.of("city", city, "temperature", 22));
    })
    .build();

GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("杭州今天适合出门吗？")
    .tools(List.of(weatherTool))
    .toolChoice(ToolChoice.auto())
    .stopWhen(StopCondition.maxSteps(3))
    .build();
```

`ToolChoice.required()` 表示模型必须选择某个工具；`ToolChoice.tool("get_weather")` 表示固定调用指定工具；`ToolChoice.none()` 表示本次请求禁用工具。

多步骤调用可通过 `stopWhen` 和 `prepareStep` 控制：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("先查天气，再给出建议")
    .tools(List.of(weatherTool))
    .stopWhen(StopCondition.maxSteps(3))
    .prepareStep(context -> context.getStepIndex() == 0
        ? PreparedStep.builder().toolChoice(ToolChoice.required()).build()
        : PreparedStep.builder().toolChoice(ToolChoice.none()).build())
    .build();
```

`PreparedStep` 可覆盖当前步骤的消息、工具选择、可用工具、采样设置、`seed`、`maxRetries`、停止序列和 provider options。

### 工具执行上下文

`ToolExecutionContext` 中常用字段：

| 字段 | 说明 |
| --- | --- |
| `toolCallId` | 当前工具调用 ID |
| `toolName` | 工具名称 |
| `input` | 模型生成并解析后的 JSON 参数 |
| `stepIndex` | 触发工具调用的模型步骤 |
| `messages` | 当前步骤发送给模型的消息 |
| `providerMetadata` | provider 返回的元数据 |

工具 executor 返回 `Mono<Object>`，返回值需要能被 JSON 序列化。抛出异常或返回失败 `Mono` 会被记录为工具错误，并以 tool error part 形式回传给模型和调用方。

### 多工具和强制工具

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("查询天气并推荐出行方式")
    .tools(List.of(weatherTool, trafficTool))
    .toolChoice(ToolChoice.required())
    .stopWhen(StopCondition.maxSteps(4))
    .build();
```

如果只允许某一步使用部分工具，可以用 `PreparedStep.activeTools`：

```java
.prepareStep(context -> {
    if (context.getStepIndex() == 0) {
        return PreparedStep.builder()
            .activeTools(List.of("get_weather"))
            .toolChoice(ToolChoice.required())
            .build();
    }
    return PreparedStep.builder()
        .activeTools(List.of("get_traffic"))
        .build();
})
```

工具调用是否可用取决于模型和 provider。调用方应为 `IllegalArgumentException` 做好处理，因为某些 provider 不支持工具、强制工具或指定工具模式。

## 设置

常用设置优先使用 `GenerateTextRequest` 的一等字段：

| 字段 | 说明 |
| --- | --- |
| `maxOutputTokens` | 最大输出 token 数 |
| `temperature` | 采样温度 |
| `topP` / `topK` | 采样范围 |
| `presencePenalty` / `frequencyPenalty` | 重复惩罚 |
| `stopSequences` | 停止序列 |
| `seed` | 确定性采样种子，具体效果取决于供应方和模型 |
| `maxRetries` | 可重试非流式 provider 调用的最大重试次数，`0` 表示不重试 |
| `reasoning` | 推理能力控制 |
| `headers` | 请求级 HTTP header |
| `timeouts` | 总耗时、单步骤和工具执行超时 |
| `cancellationToken` | 调用方主动取消 |

推理控制示例：

```java
GenerateTextRequest fastRequest = GenerateTextRequest.builder()
    .prompt("快速生成一句摘要")
    .reasoning(ReasoningOptions.disabled())
    .build();

GenerateTextRequest carefulRequest = GenerateTextRequest.builder()
    .prompt("分析这段长文的风险")
    .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
    .build();
```

取消示例：

```java
CancellationSource source = CancellationSource.create();

Mono<GenerateTextResult> task = model.generateText(GenerateTextRequest.builder()
    .prompt("生成一篇长文")
    .cancellationToken(source.token())
    .build());

source.cancel("user stopped");
```

### 超时

`GenerationTimeouts` 可分别控制整体调用、单个 provider 步骤和工具执行：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("生成一篇长文")
    .timeouts(GenerationTimeouts.builder()
        .total(Duration.ofSeconds(30))
        .step(Duration.ofSeconds(15))
        .tool(Duration.ofSeconds(5))
        .build())
    .build();
```

超时不是可重试 provider 失败。需要更短响应时，优先降低 `maxOutputTokens`、禁用推理或选择更快模型。

### 请求 Header

`headers` 用于请求级 HTTP header，例如调用方链路 ID：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("生成摘要")
    .headers(Map.of("X-Request-Id", requestId))
    .build();
```

并非所有 provider adapter 都支持动态 header。不支持时会明确报错，调用方不应依赖静默忽略。

### Seed 和重试

`seed` 是确定性采样提示，不保证所有 provider 和模型都能完全复现结果。它已经作为一等字段暴露，普通调用不需要再放入 `providerOptions`。

`maxRetries` 只作用于可重试的非流式 provider 调用：

| 值 | 行为 |
| --- | --- |
| `null` | 使用默认重试预算 |
| `0` | 不重试 |
| `1` | 最多重试 1 次 |

参数校验错误、取消、超时、结构化输出校验失败不会被重试。

## 嵌入

简单查询向量：

```java
return aiModelService()
    .flatMap(service -> service.defaultEmbeddingModel())
    .flatMap(model -> model.embedQuery("Halo 插件开发"));
```

批量嵌入：

```java
EmbeddingRequest request = EmbeddingRequest.builder()
    .inputs(List.of("Halo", "插件", "模型能力"))
    .dimensions(1024)
    .maxBatchSize(8)
    .maxParallelCalls(2)
    .maxRetries(2)
    .build();

return embeddingModel.embed(request)
    .map(EmbeddingResponse::getEmbeddings);
```

`EmbeddingUtils.cosineSimilarity(a, b)` 可用于计算两个向量的余弦相似度。

`EmbeddingResponse` 常用字段：

| 字段 | 说明 |
| --- | --- |
| `embeddings` | 与输入顺序一致的向量列表 |
| `usage` | Token 使用量，如果 provider 返回 |
| `warnings` | 可恢复问题或能力差异提示 |
| `providerMetadata` | provider 元数据 |

语义相似度示例：

```java
return embeddingModel.embed(EmbeddingRequest.builder()
        .inputs(List.of("Halo 插件", "Halo 扩展能力"))
        .build())
    .map(response -> {
        float[] a = response.getEmbeddings().get(0);
        float[] b = response.getEmbeddings().get(1);
        return EmbeddingUtils.cosineSimilarity(a, b);
    });
```

嵌入 settings：

| 字段 | 说明 |
| --- | --- |
| `dimensions` | 期望向量维度，是否可用取决于模型 |
| `maxBatchSize` | 每批请求最多输入数 |
| `maxParallelCalls` | 最大并行批次数 |
| `maxRetries` | 可重试 provider 失败的重试次数 |
| `headers` | 请求级 header |
| `providerOptions` | 高级 provider 原生选项 |

如果 provider 不支持批量或并发，本插件会按 provider 能力拆分或降级执行。

## 能力支持说明

下表描述调用方可以依赖的公开能力，以及哪些地方会受 provider 或模型影响：

| 能力 | 当前状态 | 调用方注意事项 |
| --- | --- | --- |
| 普通文本生成 | 可用 | 使用 `LanguageModel.generateText` |
| 流式文本 | 可用 | 使用 `StreamTextResult`，事件块保持独立闭合 |
| 多轮消息 | 可用 | 使用 `ModelMessage` |
| 结构化输出 | 可用 | 本地会解析和校验，模型不合规时会失败 |
| 工具调用 | Provider 相关 | provider/model 不支持时会报错 |
| 多步骤工具循环 | 可用 | 使用 `stopWhen` 限制最大步骤 |
| 推理控制 | Provider 相关 | 使用 `ReasoningOptions`，不支持时会报错 |
| `seed` | Provider 相关 | OpenAI-compatible 和 Ollama 路径已映射；确定性取决于模型 |
| `maxRetries` | 可用 | 非流式 provider 调用生效 |
| 请求 header | Provider 相关 | 不支持动态 header 的 adapter 会报错 |
| source/file part | Provider 相关 | 只有 provider 返回时才会出现 |
| 嵌入 | 可用 | 维度、批量、并发能力取决于 provider |

当前 SDK 聚焦语言模型和嵌入。图像、视频、语音、转写、重排序、工具审批等暂不属于当前公开能力范围。

## 错误和告警

常见异常：

| 类型 | 说明 |
| --- | --- |
| `IllegalArgumentException` | 请求参数无效 |
| `AiGenerationTimeoutException` | 文本生成超时 |
| `AiGenerationCancelledException` | 文本生成被取消 |
| `StructuredOutputValidationException` | 结构化输出校验失败 |
| `EmbeddingTimeoutException` | 嵌入调用超时 |
| `EmbeddingCancelledException` | 嵌入调用被取消 |

`warnings` 表示请求已完成但存在能力差异、输出提示或可恢复问题。调用方应记录告警，并在面向用户的功能中给出适当提示。

## 测试和排查

后台模型测试页可用于手动验证调用方最关心的路径：

- 文本生成、流式文本、推理控制、工具调用和结构化输出。
- `temperature`、`topP`、`maxOutputTokens`、`seed`、`maxRetries` 等文本设置。
- 嵌入维度、批量大小、并发数、重试次数和 provider options。

调用方插件建议至少覆盖以下自动化用例：

- 正常文本生成。
- 流式文本能收到文本 delta 和最终结果。
- 结构化输出校验成功和失败。
- 工具调用成功、工具返回错误、工具异常。
- `maxRetries(0)` 不重试。
- 取消和超时。
- 嵌入批量输入与向量数量匹配。

排查问题时先确认模型资源名是否为 `AiModel.metadata.name`，再检查后台模型是否启用、provider 是否可用、请求字段是否被当前 provider 支持。对于 provider 相关能力，优先查看返回的异常和 `warnings`，不要假设不支持的设置会被静默忽略。

## 高级 Provider Options

`providerOptions` 用于在公开字段无法表达某个供应方原生能力时使用。它必须按供应方命名空间分组：

```java
GenerateTextRequest request = GenerateTextRequest.builder()
    .prompt("生成摘要")
    .providerOptions(ProviderOptions.of(
        ProviderOptions.namespace("openai")
            .option("response_format", Map.of("type", "json_object"))
            .build()
    ))
    .build();
```

如果公开字段与已知 provider 原生键冲突，请优先使用公开字段。例如推理能力使用 `reasoning`，确定性采样使用 `seed`，不要同时在 `providerOptions` 中传入含义相同的原生键。
