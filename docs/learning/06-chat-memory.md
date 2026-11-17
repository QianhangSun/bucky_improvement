# 第 06 课 · 会话记忆

## 这一课的目标
大模型本身**没有记忆**：每次调用都是独立的，上一次说过什么它完全不知道。这一课让导师记住同一段对话里说过的话，并且存进数据库，重启也不会丢。

```
[alice] 问：我叫小王，目标是两周后面试 Java 后端
[alice] 问：我刚才说我叫什么？目标是什么？
     答：**你叫小王，目标是两周后面试 Java 后端。**
[bob]   问：我刚才说我叫什么？
     答：**你没说过，我这边没有你的名字。**
```

## 改了哪些文件
```
pom.xml                                              + spring-ai-starter-model-chat-memory-repository-jdbc
src/main/resources/db/migration/V2__create_chat_memory.sql   记忆表
src/main/resources/application.yml                   initialize-schema: never
src/main/java/com/bucky/mentor/MentorConfig.java     ChatMemory、MessageChatMemoryAdvisor 两个 Bean
src/main/java/com/bucky/mentor/MentorChatController.java   /chat、/chat/stream 加 cid；查看、清空会话
```

## 概念 1：记忆的本质 = 每次把历史一起发过去
模型不会记东西，所谓"记忆"是**我们替它记**：把之前的问答存起来，下次调用时一起塞进请求。
用 `SimpleLoggerAdvisor` 的日志能直接看到，alice 第二次提问时真正发给模型的是：
```
System（人设） → User（我叫小王…） → Assistant（先别急着刷八股…） → User（我刚才说我叫什么？）
```
推论：
- **对话越长，每次请求越贵**：历史的 token 每次都要重新算钱（第 04、05 课的 promptTokens）
- 所以必须限制条数，见概念 3

## 概念 2：三个角色
| 类 | 职责 | 这里用的 |
|---|---|---|
| `ChatMemoryRepository` | **存在哪** | `JdbcChatMemoryRepository`：存 PostgreSQL。不加 jdbc starter 时自动配置给的是 `InMemoryChatMemoryRepository`，重启就没了 |
| `ChatMemory` | **取多少** | `MessageWindowChatMemory`：每个会话只保留最近 20 条 |
| `MessageChatMemoryAdvisor` | **什么时候存、什么时候取** | before 取历史塞进请求，并存下用户这句；after 存下模型的回答 |

```java
@Bean
public ChatMemory chatMemory(ChatMemoryRepository repository) {
    return MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(20).build();
}

@Bean
public MessageChatMemoryAdvisor chatMemoryAdvisor(ChatMemory chatMemory) {
    return MessageChatMemoryAdvisor.builder(chatMemory).build();
}
```
自动配置其实已经给了一个默认 20 条的 `ChatMemory`，自己写一遍是为了看清楚这两层。

## 概念 3：滑动窗口
`maxMessages(20)` 的意思是每个会话最多保留 20 条消息（问和答各算一条，即约 10 轮），再早的会被丢掉。
- 窗口太小：导师很快忘了你开头说的目标
- 窗口太大：每次请求都很贵，还可能超出模型的上下文长度
- 读源码时注意到：System 消息不会被挤掉（`MessageWindowChatMemory` 裁剪时只裁非 System 消息）

更聪明的做法（PRD 里的导师以后会用到）：把早期对话**总结成一段话**留着，而不是直接丢掉。Spring AI 没有现成实现，要自己写。

## 概念 4：会话 ID
```java
chatClient.prompt()
        .user(q)
        .advisors(a -> a.advisors(memoryAdvisor)                 // 这次调用挂上记忆 Advisor
                .param(ChatMemory.CONVERSATION_ID, cid))        // 放进请求 context，告诉它是哪个会话
        .call()
```
- 同一个 `cid` 共享记忆，换一个 `cid` 就是新对话（alice 和 bob）
- `.param(...)` 放进的就是第 05 课讲的请求 `context`，Advisor 在 before / after 里从 context 取出来
- **2.0.2 里会话 ID 是必填的**：不传直接报 `conversationId cannot be null`（1.x 会默认用 `"default"`，网上很多教程是旧写法）

所以记忆 Advisor **没有**放进 `defaultAdvisors`：`/explain`、`/plan` 是一次性问答，不传 cid，挂上就会报错。只在 `/chat`、`/chat/stream` 里按次加，用的是第 05 课概念 5 的"单次 advisors"。

## 概念 5：表结构交给 Flyway
Spring AI 的 jar 里自带建表脚本 `schema-postgresql.sql`，但**默认只对内嵌数据库（H2 等）自动执行**，PostgreSQL 不会自动建表。
我们把它抄进 `V2__create_chat_memory.sql`，并设置 `initialize-schema: never`，表结构统一由 Flyway 管（第 05 课加餐）。
注意表名、列名不能改，`JdbcChatMemoryRepository` 的 SQL 里写死了。

```
 cid   |   type    | content                         | sequence_id
 alice | USER      | 我叫小王，目标是两周后面试 Java 后端 |           0
 alice | ASSISTANT | **先别急着刷八股……               |           1
 alice | USER      | 我刚才说我叫什么？目标是什么？      |           2
 alice | ASSISTANT | **你叫小王，目标是两周后面试……     |           3
```

## 概念 6：Advisor 顺序
记忆 Advisor 的默认 order 是 `HIGHEST_PRECEDENCE + 200`，比日志（0）和用量（100）都靠外：
```
记忆 before（塞历史） → 日志 before（打印） → 用量 before → 模型
```
所以日志打印出的请求**已经带着历史**，用量统计的 promptTokens 也包含历史的 token。如果记忆排在日志里面，日志就看不到历史了。

## 新接口
| 接口 | 作用 |
|---|---|
| `GET /api/mentor/chat?q=...&cid=alice` | 带记忆的聊天，cid 不传默认 `default` |
| `GET /api/mentor/chat/stream?q=...&cid=alice` | 流式版，同样带记忆 |
| `GET /api/mentor/conversations/alice` | 看 alice 这个会话存了哪些消息 |
| `DELETE /api/mentor/conversations/alice` | 清空 |

## 运行
数据库要先启动（第 05 课）：
```bash
~/local/pgsql-17/bin/pg_ctl -D ~/local/pgdata-17 -l ~/local/pgdata-17/server.log start
mvn spring-boot:run
curl -G localhost:8080/api/mentor/chat --data-urlencode "q=我叫小王，目标是两周后面试 Java 后端" --data-urlencode "cid=alice"
curl -G localhost:8080/api/mentor/chat --data-urlencode "q=我刚才说我叫什么？" --data-urlencode "cid=alice"
curl localhost:8080/api/mentor/conversations/alice
```
我在 8081 上验证过：alice 能记住、bob 记不住、流式也能记住、`/explain` 不带 cid 正常、清空后为空。

## 课后练习
1. 用 bob 问"我刚才说我叫什么？"，导师答"对话里你只问了 Spring Bean 的问题"。bob 明明一句话都没说过，这个"Spring Bean"是从哪来的？（提示：看看人设文件）
2. 用同一个 cid 连续聊 15 轮，然后问它第 1 轮你说了什么。它还记得吗？为什么？同时观察 `/api/mentor/usage` 里每次的 promptTokens 是怎么变化的。
3. 传一个 40 个字符的 cid（比如 40 个 x），会发生什么？怎么修比较好？
4. 现在任何人只要知道 `alice` 这个 cid，就能看到、甚至清空 alice 的聊天记录。真正上线时，cid 应该怎么生成、怎么校验？

## 加餐：自己实现 ChatMemory（只追加，取最近 N 条）
自带的 `MessageWindowChatMemory` 每次 add 都要：读出整个会话 → 裁剪到 20 条 → 在事务里 DELETE 全部 + INSERT 20 条。被裁掉的旧消息永久丢失。
原因是 `ChatMemoryRepository` 只有 `saveAll(整个列表)`，没有“追加一条”，只能整体替换。

`AppendOnlyChatMemory` 直接实现 `ChatMemory` 接口：
- `add()`：只 INSERT 新消息（Advisor 的 before 存用户消息，after 存回答）
- `get()`：`ORDER BY id DESC LIMIT 20` 取最新的，翻转成正序，再跳到第一条 USER，保证从完整的一轮开始
- `history()`：完整历史，给“聊天记录”页面用
- 表是 `V3__create_chat_message.sql` 里的 `chat_message`，原来的 `SPRING_AI_CHAT_MEMORY` 不再使用

`MessageChatMemoryAdvisor` 只依赖 `ChatMemory` 接口，所以换掉实现后，Advisor 和聊天接口的代码都不用改。

```bash
curl localhost:8080/api/mentor/conversations/alice              # 完整历史
curl "localhost:8080/api/mentor/conversations/alice?window=true" # 下次发给模型的最近 20 条
```

## 下一课预告
第 07 课：结构化输出。让模型直接返回 Java 对象（record），比如一份学习建议列表，而不是一段需要自己解析的文字。
