# 第 05 课 · Advisor

## 这一课的目标
给导师的**每一次**调用自动加上两件事，业务代码（Controller）一行不改：
1. 打印完整的请求和响应日志（用 Spring AI 自带的 `SimpleLoggerAdvisor`）
2. 统计耗时和 token 用量，并能通过接口查看累计值（自己写 `TokenUsageAdvisor`）

## 改了哪些文件
```
src/main/java/com/bucky/mentor/TokenUsageAdvisor.java      新建：自定义 Advisor
src/main/java/com/bucky/mentor/MentorConfig.java           defaultAdvisors(...) 注册两个 Advisor
src/main/java/com/bucky/mentor/MentorChatController.java   + /api/mentor/usage 查看累计用量
src/main/resources/application.yml                         打开 SimpleLoggerAdvisor 的 DEBUG 日志
```

## 概念 1：Advisor 就是 ChatClient 的 AOP
第 01 课回顾里讲 AOP 用的是"机场安检"：每个旅客都要先过同一道安检。Advisor 是一样的思路：

```
chatClient.prompt().user(q).call()
   │
   ▼
SimpleLoggerAdvisor   before：打印请求
   ▼
TokenUsageAdvisor     before：记下开始时间
   ▼
ChatModelCallAdvisor  Spring AI 自己放在最里层，真正调用 ChatModel
   ▼  ……模型返回……
TokenUsageAdvisor     after：读 usage、算耗时
   ▼
SimpleLoggerAdvisor   after：打印响应
   ▼
.content()
```
请求一层层往里走，响应一层层往外走，像剥洋葱。后面的课里，**会话记忆（06）、RAG（12）都是 Advisor**：记忆 Advisor 在 before 里把历史消息塞进请求，after 里把这次的回答存起来。

## 概念 2：写一个 Advisor
最简单的方式是实现 `BaseAdvisor`，只写 before 和 after：

```java
@Override
public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
    return request.mutate().context(START_NANOS, System.nanoTime()).build();
}

@Override
public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
    Object start = response.context().get(START_NANOS);
    Usage usage = response.chatResponse().getMetadata().getUsage();
    // 记日志、累加……
    return response;
}
```
- `ChatClientRequest` = `Prompt` + `context`。`context` 是一个 Map，沿着整条链传下去，**before 里放的东西 after 里能取到**（源码里 `ChatModelCallAdvisor` 会把请求的 context 复制到响应上）
- 两个方法都要**返回**请求/响应。可以原样返回，也可以返回修改后的版本：改 Prompt 就能改发给模型的内容，这是 RAG 往请求里加资料的原理
- `ChatClientRequest` 是 record，不可变，所以用 `mutate()` 复制一份再改（第 03 课回顾讲的 record）

更底层的写法是直接实现 `CallAdvisor` 和 `StreamAdvisor`，自己调用 `chain.nextCall(request)`。`SimpleLoggerAdvisor` 就是这么写的。能完全控制流程（比如不调用下一层、直接返回缓存结果），但要分别处理同步和流式。

## 概念 3：同一个 Advisor 同时支持 call 和 stream
`BaseAdvisor` 已经替你写好了 `adviseCall` 和 `adviseStream`（源码 `BaseAdvisor` 第 47、57 行）：
- `.call()`：before → 下一层 → after，各一次
- `.stream()`：before 一次；after **只在带 `finishReason` 的那一片上调用一次**

第 04 课回顾里讲过，DeepSeek 只有最后一片有 `finishReason=STOP` 和 usage，所以 after 正好拿到完整的 usage。
另一个细节：流式时 before 跑在 `boundedElastic` 线程上（日志里能看到线程名 `ndedElastic-154`），不是 Tomcat 的请求线程。

## 概念 4：执行顺序
`getOrder()` 返回的数字决定顺序：**数字越小越靠外**，before 越早、after 越晚。

| Advisor | order |
|---|---|
| `SimpleLoggerAdvisor` | 0 |
| `TokenUsageAdvisor` | 100 |
| 会话记忆 Advisor（第 06 课） | `HIGHEST_PRECEDENCE + 200`，非常靠外 |
| `ChatModelCallAdvisor`（真正调模型） | `LOWEST_PRECEDENCE`，永远在最里面 |

顺序会影响结果。比如日志 Advisor 如果排在记忆 Advisor 外面，它打印出来的请求就**还没有**历史消息；排在里面，才能看到真正发给模型的完整内容。

## 概念 5：defaultAdvisors 和单次 advisors
```java
// 这个 ChatClient 的每次调用都带上
builder.defaultAdvisors(new SimpleLoggerAdvisor(), tokenUsageAdvisor)

// 只对这一次调用加
chatClient.prompt().user(q).advisors(someAdvisor).call()
```
和第 03 课的 `defaultSystem` / `.system(...)`、`defaultOptions` / `.options(...)` 是同一套规则。

## 运行
```bash
mvn spring-boot:run
curl -G localhost:8080/api/mentor/chat --data-urlencode "q=Spring Bean 默认是单例吗"
curl localhost:8080/api/mentor/usage
```
我在 8081 上依次调了 `/chat`、`/chat/stream`、`/plan`、`/hello`，`/usage` 返回（这是改成数据库之前、内存计数器版本的结果）：
```json
{"calls":3,"promptTokens":1058,"completionTokens":823,"totalTokens":1881}
```
控制台日志：
```
TokenUsageAdvisor : 调用完成：耗时 1939ms，输入 326，输出 84，合计 410 token     ← /chat
TokenUsageAdvisor : 调用完成：耗时 1321ms，输入 326，输出 88，合计 414 token     ← /chat/stream
TokenUsageAdvisor : 调用完成：耗时 5670ms，输入 406，输出 651，合计 1057 token   ← /plan
SimpleLoggerAdvisor : request: ChatClientRequest[prompt=Prompt{messages=[SystemMessage{textContent='你是 Bucky 成长台里的 AI 导师……
```

## 加餐：用量存进数据库

练习 3 发现计数器在内存里，重启就清零。这一节把它改成写进 PostgreSQL。

### 数据库
本机没有 Docker，Homebrew 装在非默认路径（`~/homebrew`），预编译包都用不了，所以直接从源码编译：
- PostgreSQL 17.12 → `~/local/pgsql-17`，数据目录 `~/local/pgdata-17`，只监听 localhost
- pgvector 0.8.7（第 11-12 课 RAG 要用），已在 `bucky` 库里 `create extension vector`

常用命令：
```bash
~/local/pgsql-17/bin/pg_ctl -D ~/local/pgdata-17 -l ~/local/pgdata-17/server.log start   # 启动
~/local/pgsql-17/bin/pg_ctl -D ~/local/pgdata-17 stop                                      # 停止
~/local/pgsql-17/bin/psql -d bucky                                                         # 命令行连进去
```
它不是系统服务，**电脑重启后要手动启动**，否则应用启动时连不上数据库会直接报错。

### 改了哪些文件
```
pom.xml                                            + jdbc、flyway、flyway-database-postgresql、postgresql 驱动
src/main/resources/db/migration/V1__create_llm_usage.sql   建表脚本
src/main/java/com/bucky/usage/LlmUsageRecord.java          一行记录（record）
src/main/java/com/bucky/usage/LlmUsageRepository.java      JdbcClient 读写
src/main/java/com/bucky/usage/UsageController.java         /api/mentor/usage、/usage/daily
src/main/java/com/bucky/mentor/TokenUsageAdvisor.java      after 里异步写库，去掉内存计数器
src/main/resources/application.yml                         数据源配置
```

### 概念 6：Flyway 管理表结构
建表 SQL 放在 `db/migration/V1__create_llm_usage.sql`，应用启动时 Flyway 自动执行：
- 文件名 `V<版本>__<描述>.sql`，两个下划线
- 执行记录存在 `flyway_schema_history` 表里，**执行过的不会再跑**。第二次启动日志：`Schema "public" is up to date. No migration necessary.`
- **执行过的脚本不能改**（Flyway 会校验内容的校验和，改了启动报错）。要改表结构就新建 `V2__xxx.sql`
- 好处：表结构和代码一起进 git，任何人拉下代码、启动应用，就得到一样的表

Spring Boot 4 的变化：Flyway 拆成了单独的 `spring-boot-starter-flyway`。3.x 时只加 `flyway-core` 就会自动执行，4.x 只加 `flyway-core` **不会**。

### 概念 7：JdbcClient
Spring 6.1 起的新 API，直接写 SQL，比 `JdbcTemplate` 简洁：
```java
jdbc.sql("INSERT INTO llm_usage (model, prompt_tokens, ...) VALUES (:model, :promptTokens, ...)")
    .paramSource(record)   // 按 record 字段名填 :参数
    .update();

jdbc.sql("SELECT count(*) AS calls, sum(prompt_tokens) AS prompt_tokens ... FROM llm_usage")
    .query(UsageSummary.class)   // 列名 prompt_tokens 自动对应字段 promptTokens
    .single();
```
没有用 JPA：表很简单，直接写 SQL 更直观，汇总查询（按天、按模型）本来就是 SQL 擅长的。

### 概念 8：记账不能拖慢回答
`after` 里只是把记录交给一个虚拟线程池，真正写库在后台：
```java
private final ExecutorService writer = Executors.newVirtualThreadPerTaskExecutor();
writer.submit(() -> { try { repository.insert(record); } catch (Exception e) { log.warn(...); } });
```
- 用户拿到回答不用等数据库
- 数据库挂了，只打一条警告日志，回答照常返回
- 应用关闭时 `destroy()` 里 `writer.close()`，等没写完的记录写完再退出
- 代价：极端情况下（进程被强杀）会丢几条记录。计费要求一条不能丢的话，就得同步写或者先写消息队列

### 踩坑：怎么判断是不是流式调用
第一版用 `chain instanceof StreamAdvisorChain` 判断，结果非流式的 `/chat` 也被记成了 `streaming = true`。
查源码：链对象 `DefaultAroundAdvisorChain` 实现的是 `BaseAdvisorChain`，而它**同时继承了** `CallAdvisorChain` 和 `StreamAdvisorChain`，所以 instanceof 永远为真。

改法：重写入口方法，在上下文里放标记，再交回 `BaseAdvisor` 原来的实现：
```java
@Override
public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
    return BaseAdvisor.super.adviseCall(request.mutate().context(STREAMING, false).build(), chain);
}
@Override
public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
    return BaseAdvisor.super.adviseStream(request.mutate().context(STREAMING, true).build(), chain);
}
```
`BaseAdvisor.super.xxx(...)` 是 Java 调用接口默认方法的写法。

### 踩坑：缓存命中数取不到
`Usage` 接口有通用的 `getCacheReadInputTokens()`，但 DeepSeek 模块没有填它（返回 null）。只能从厂商原始数据里取：
```java
if (usage.getNativeUsage() instanceof DeepSeekApi.Usage ds && ds.promptTokensDetails() != null) {
    return ds.promptTokensDetails().cachedTokens();
}
```
这段代码和 DeepSeek 绑定了。以后换厂商，这里要跟着改。

### 验证
依次调 `/chat`、`/chat/stream`、`/plan`，然后**重启应用**再查：
```
重启前 /usage → {"calls":3,"promptTokens":1058,"completionTokens":1134,"cachedTokens":512}
重启后 /usage → {"calls":3,"promptTokens":1058,"completionTokens":1134,"cachedTokens":512}
/usage/daily → [{"day":"2026-11-16","model":"deepseek-flash","calls":3,...,"avgDurationMs":2976}]
```
```
 id |  p  |  c  | cached | duration_ms | streaming
  1 | 326 |  77 |    128 |        1589 | f        ← /chat
  2 | 326 |  85 |    128 |         926 | t        ← /chat/stream
  3 | 406 | 972 |    256 |        6413 | f        ← /plan（开思考）
```

## 课后练习
1. 调了 4 个接口，`calls` 却是 3。少的是哪个？为什么？
2. 把 `TokenUsageAdvisor` 的 `getOrder()` 改成 `-100`，再调一次 `/chat`，对比改之前，两种日志谁先谁后有什么变化？
3. `/usage` 的数字在重启应用后会怎样？如果要"每天统计一次、按天保存"，你觉得应该改哪里？
4. 看 `SimpleLoggerAdvisor` 打出来的 request 日志，里面有没有 API Key？日志里可能有哪些敏感信息？

## 下一课预告
第 06 课：会话记忆。现在导师记不住你上一句话。加上 `MessageChatMemoryAdvisor` 后，它会在 before 里把历史消息塞进请求，after 里把新的一轮存起来。
