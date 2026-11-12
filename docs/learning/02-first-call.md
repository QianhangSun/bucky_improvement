# 第 02 课 · 第一次调用大模型

## 这一课的目标
加一个 starter、写几行配置、注入 `ChatModel`，完成第一次大模型调用：

```bash
curl -G localhost:8080/api/mentor/hello --data-urlencode "q=用一句话介绍你自己"
# {"model":"deepseek-flash","answer":"我是一个乐于助人……的AI助手。",
#  "usage":{"promptTokens":34,"completionTokens":133,"totalTokens":167,...}}
```

## 改了哪些文件
```
pom.xml                                             + spring-ai-starter-model-deepseek
src/main/resources/application.yml                  + spring.ai.deepseek.* 配置
src/main/java/com/bucky/mentor/MentorController.java  新建：/api/mentor/hello
```

## 为什么用 DeepSeek，不是 PRD 里写的 Claude
本来按 PRD 接 Anthropic，结果碰到两个问题：
- 终端里的 `ANTHROPIC_API_KEY` 是公司 Claude Code 网关的 Key，网关只放行 Claude Code，Spring AI 的请求会被拒：`400 Client not allowed (detected: spring-ai-anthropic-sdk)`。
- 公司业务项目（truman-ai / truman-speaker）的 Key 是线上业务凭证，不能拿来跑个人项目。

所以自己申请了 DeepSeek 的 Key。**代码只依赖 `ChatModel` 接口**，以后要换回 Claude，只需要改依赖和配置，`MentorController` 一行不动。这一课从 Anthropic 切到 DeepSeek 时，已经实际验证过一次了。

## 概念 1：一个依赖 = 三层模块
```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-deepseek</artifactId>   <!-- 不写版本，BOM 管 -->
</dependency>
```
它会带进第 01 课在 BOM 里看到的三层：

| 模块 | 作用 |
|---|---|
| `spring-ai-starter-model-deepseek` | 空壳，负责把下面两个带进来 |
| `spring-ai-autoconfigure-model-deepseek` | 读取 `spring.ai.deepseek.*`，创建 `ChatModel` Bean |
| `spring-ai-deepseek` | `DeepSeekChatModel`、`DeepSeekApi`，真正发 HTTP 请求 |

## 概念 2：配置
```yaml
spring:
  ai:
    deepseek:
      api-key: ${DEEPSEEK_API_KEY}     # 从环境变量读，Key 不进代码仓库
      chat:
        model: deepseek-v4-flash
        max-tokens: 1024
```
- `${DEEPSEEK_API_KEY}` 是 Spring 的占位符，启动时从环境变量取值。`${A:默认值}` 的写法可以给默认值。
- `base-url` 不写，默认就是 `https://api.deepseek.com`。
- **配置名以 jar 包为准**：每个 autoconfigure jar 里都有 `META-INF/spring-configuration-metadata.json`，列出所有合法配置和废弃情况。比如 Anthropic 的 `chat.options.model` 在 2.0 已经废弃，新写法是 `chat.model`，网上很多 1.x 教程还在用旧写法。IDEA 里输入配置时的自动补全，读的也是这个文件。
- 模型名也以源码为准：2.0.2 推荐 `deepseek-v4-flash` / `deepseek-v4-pro`，`deepseek-chat`、`deepseek-reasoner` 已标为不推荐。

## 概念 3：ChatModel、Prompt、ChatResponse
```java
ChatResponse response = chatModel.call(new Prompt(q));
```
| 类 | 是什么 |
|---|---|
| `ChatModel` | 接口，所有对话模型的统一入口。这里注入的实际是 `DeepSeekChatModel` |
| `Prompt` | 一次请求的完整内容：消息列表 + 可选的调用参数。这里只有一条用户消息 |
| `ChatResponse` | 返回结果：`getResult().getOutput().getText()` 是回答文本，`getMetadata()` 里有模型名、token 用量 |

`ChatModel` 还有一个偷懒写法 `chatModel.call("你好")`，直接返回 String，但拿不到元数据。
第 03 课的 `ChatClient` 会在 `ChatModel` 外面再包一层更好用的链式 API，实际项目里大多用它。

## 概念 4：一次调用经过了什么
出错时的堆栈从下往上读，就是调用路径：
```
MentorController.hello                 你的代码
  DeepSeekChatModel.call               ChatModel 的实现
    Observation.observe                可观测性埋点（第 15 课）
      RetryTemplate                    自动重试
        DeepSeekApi.chatCompletionEntity  拼 OpenAI 格式的请求
          RestClient                   发 HTTP
```
重试只针对**临时错误**：
- `TransientAiException`（429 限流、5xx 服务繁忙）→ 自动重试
- `NonTransientAiException`（401 Key 错、402 余额不足、400 参数错）→ 直接抛出，重试也没用

## 这次踩到的坑
| 现象 | 原因 |
|---|---|
| `400 Client not allowed (detected: spring-ai-anthropic-sdk)` | 公司网关只放行 Claude Code |
| `402 Insufficient Balance` | DeepSeek 新账号余额为 0，要先充值。注意是 402 不是 401，说明 Key 本身是对的 |
| `${DEEPSEEK_API_KEY}` 读不到 | 环境变量要写进 `~/.zshrc` 并 `source`；IDEA 要完全退出重开才能读到新变量 |

## 课后练习
1. 看返回里的 `usage`：回答只有一句话（约 30 个字），`completionTokens` 却有 133。多出来的 token 去哪了？提示：DeepSeek V4 默认会先"思考"再回答。在 `application.yml` 里加上
   ```yaml
   spring.ai.deepseek.chat.thinking.type: disabled
   ```
   重启后再调一次，对比 `completionTokens`。
2. 返回里的 `model` 是 `deepseek-flash`，而你配置的是 `deepseek-v4-flash`。这个值是谁给的？（提示：`getMetadata().getModel()` 读的是服务端响应。）
3. 把 `max-tokens` 改成 `10`，问一个需要长回答的问题，看看回答变成什么样。
4. 故意把环境变量里的 Key 改错一位，看看报什么错、是哪种异常、有没有重试。

## 下一课预告
第 03 课：`ChatClient`。用链式 API 加上 system 消息，给导师设定"人设"，并用 `PromptTemplate` 往提示词里填变量。
