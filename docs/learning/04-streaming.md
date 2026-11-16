# 第 04 课 · 流式输出

## 这一课的目标
`/plan` 要等 6 秒左右才一次性返回。换成流式后，模型生成一小段就推一小段，用户 3 秒左右就能看到第一个字。

## 改了哪些文件
```
src/main/java/com/bucky/mentor/MentorChatController.java   + /chat/stream、/plan/stream
src/main/java/com/bucky/mentor/StreamChunk.java            新建：流式片段 record
src/main/resources/static/stream.html                      新建：浏览器里对比流式和非流式
```

## 概念 1：只改最后一步
```java
// 非流式（第 03 课）
String text        = chatClient.prompt().user(q).call().content();

// 流式（第 04 课）
Flux<String> parts = chatClient.prompt().user(q).stream().content();
```
`.call()` 之前的部分（人设、模板、options）完全一样，所以 `/plan` 和 `/plan/stream` 共用了一个 `planRequest(goal)` 方法。

## 概念 2：Flux 是什么
`Flux<T>` 来自 Reactor 库，表示**会陆续到来的 0 到 N 个元素**。可以把它想成一根水管：
- `String`：一桶水，装满了才递给你
- `Flux<String>`：水管，水来一点流一点

常用操作和 Stream 很像：`.map(...)` 转换每个元素，`.filter(...)` 过滤。区别是 Stream 的数据已经在手里，Flux 的数据还在路上。

DeepSeek 的流式请求底层用的是 `WebClient`（`DeepSeekApi.chatCompletionStream`），所以 starter 已经带进了 `spring-webflux` 和 `reactor-core`。我们的应用仍然是 Spring MVC，**MVC 的接口也可以直接返回 Flux**。

## 概念 3：SSE
`produces = MediaType.TEXT_EVENT_STREAM_VALUE` 让 Spring 用 **SSE**（Server-Sent Events）格式往外写：
```
data:{"text":"核心"}

data:{"text":"区别"}

```
- 每个事件是一行 `data:` 加内容，空行表示这个事件结束
- HTTP 连接一直开着，服务端不断往里写，写完再断开
- 方向是服务端 → 客户端单向。浏览器自带客户端 `EventSource`，见 `stream.html`

和 WebSocket 比：SSE 只能服务端推，但就是普通 HTTP，简单，代理和网关都支持。聊天场景里用户发一句、模型回一段，SSE 正合适。

## 概念 4：为什么包一层 StreamChunk
一开始直接返回 `Flux<String>`，原始数据是：
```
data:HashMap

data: 

data:非
```
SSE 规范规定：**客户端要去掉 `data:` 后面的第一个空格**。模型的 token 经常以空格开头（`" HashMap"`、`" "`），浏览器按规范解析后，中英文之间的空格全没了：

| 返回类型 | 浏览器拼出来的结果 |
|---|---|
| `Flux<String>` | `HashMap不安全，ConcurrentHashMap线程安全` |
| `Flux<StreamChunk>` | `HashMap 不安全，ConcurrentHashMap 安全` |

包成 JSON 后空格在引号里，不受影响。回答里的换行同理：字符串里的 `\n` 在 SSE 里会被拆成多行 `data:`，JSON 里则是转义的 `\n`，更稳。
这也是很多大模型 API（包括 DeepSeek 自己）流式返回 JSON 而不是纯文本的原因。

## 概念 5：流式没让回答变快
实测（8081，同一个问题）：

| 接口 | 首字节 | 全部完成 |
|---|---|---|
| `/plan` | 5.9 秒 | 5.9 秒 |
| `/plan/stream` | 3.4 秒 | 6.0 秒 |
| `/chat` | 0.9 秒 | 0.9 秒 |
| `/chat/stream` | 0.7 秒 | 1.3 秒 |

- **总时间基本不变**：模型生成的速度没变，流式只是让用户更早看到开头
- `/plan/stream` 的首字也要 3 秒多：开着思考，模型先写草稿，草稿写完才开始输出正文（`.content()` 只取正文）
- 回答越长，流式的体验优势越明显；`/chat` 这种短回答差别不大

## 运行
```bash
mvn spring-boot:run
# -N 关掉 curl 的缓冲，才能看到逐段输出
curl -N -G localhost:8080/api/mentor/chat/stream --data-urlencode "q=HashMap 和 ConcurrentHashMap 有什么区别"
```
浏览器打开 http://localhost:8080/stream.html ，点"同时发送"，左右对比。

## 课后练习
1. 去掉 curl 的 `-N` 再跑一次流式接口，和加 `-N` 时有什么不同？为什么？
2. 在 `stream.html` 里点发送后，打开浏览器开发者工具的 Network 面板，看 `/chat/stream` 这个请求的 Type 和 EventStream 标签页。
3. 流式输出到一半，用户关掉了网页。服务端还会继续向 DeepSeek 要剩下的内容、继续付费吗？先猜，再想想怎么验证。
4. 第 03 课在 `/hello` 里能拿到 `usage`。流式时用 `.stream().chatResponse()` 代替 `.content()`，`usage` 会在哪个片段里出现？

## 下一课预告
第 05 课：Advisor。在每次调用前后自动插入逻辑：打印请求日志、统计 token 用量。思路和第 01 课回顾里讲的 AOP 一样。
