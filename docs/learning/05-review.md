# 第 05 课 · 课后练习回顾

> 我的回答原样保留，点评写在后面。

## 课后练习

### 练习 1：调了 4 个接口，calls 却是 3，少了哪个？为什么？

**我的回答**

因为 /hello 是 model 直接调用的，advisor 应该是 client 之后的流程，model 之前的部分。

**点评** ✅ 正确。Advisor 链挂在 `ChatClient` 上，最里层的 `ChatModelCallAdvisor` 才去调 `ChatModel`：
```
ChatClient → [Advisor 链] → ChatModel → DeepSeek
                              ↑
/hello 直接从这里进来，绕过了整条链
```
推论：想让统计、日志、记忆、RAG 对所有调用都生效，**业务代码统一走 ChatClient**，别直接用 ChatModel。/hello 留着只是为了和第 02 课对比。
（如果非要统计所有 ChatModel 调用，要用更底层的观测机制，第 15 课讲。）

### 练习 2：getOrder() 改成 -100，日志顺序有什么变化？

**我的回答**

改了 order 后，应该是 tokenUsageAdvisor 的 before 先调用，after 后调用了。

**点评** ✅ 正确。实测日志：
| order | 日志顺序 |
|---|---|
| 100（原来） | Logger request → **TokenUsage 调用完成** → Logger response |
| -100 | Logger request → Logger response → **TokenUsage 调用完成** |

-100 < 0，TokenUsageAdvisor 跑到了 SimpleLoggerAdvisor 外面：before 最先、after 最后。
日志里看不出 before 的顺序变化，是因为 TokenUsageAdvisor 的 before 只记时间、不打日志；能看出来的只有 after。
细节：排在外层时，它测的耗时把 Logger 打印日志的时间也算进去了，所以**计时类 Advisor 放在越里面，测得越接近模型本身的耗时**。
（练习改的 -100 已改回 100。）

### 练习 3：重启后 /usage 会怎样？要按天保存应改哪里？

**我的回答**

重启后清零。应该保存到磁盘中，比如数据库，或者文件中。

**点评** ✅ 正确，计数器在内存里（`LongAdder` 字段），进程一停就没了。补充：
- **改哪里**：`TokenUsageAdvisor.after` 里，除了累加，再按"日期 + 模型"写一条记录到数据库（如 `usage_daily(date, model, calls, prompt_tokens, completion_tokens)`，同一天累加更新）
- **别拖慢回答**：写库放到异步里（发事件或丢给线程池），after 只负责交出数据
- **多实例**：部署两台时，各自内存里的数字只是一半，必须汇总到共享存储
- **更标准的做法**：把用量作为 Micrometer 指标上报给 Prometheus 这类监控系统，按天、按模型查询都现成（第 15 课）
- **已实现**：按这个思路改成了写 PostgreSQL，见 `05-advisor.md`「加餐：用量存进数据库」。重启前后 `/usage` 数字一致

### 练习 4：日志里有 API Key 吗？哪些算敏感信息？

**我的回答**

好像没有 api key，id 算敏感信息吗？model，usage 算吗？

（贴了一段完整的 response 日志）

**点评** ✅ 没有 API Key 是对的：Key 在 HTTP 请求头里，由 `DeepSeekApi` 加上，而 Advisor 看到的只是 `Prompt`，根本接触不到请求头。
- `id`、`model`、`usage`：**不敏感**。`id` 反而应该记下来，找 DeepSeek 排查某次请求时要用它
- 真正敏感的是你没提到的：
  | 内容 | 为什么敏感 |
  |---|---|
  | 用户的问题和模型的回答（`text`） | 求职场景里会有简历、薪资、面试公司、个人经历，属于个人信息 |
  | system 人设全文 | 公司的提示词设计，属于业务资产；也方便攻击者研究怎么绕过 |
  | 以后的聊天历史、RAG 检索到的笔记、工具调用参数 | 第 06、09、12 课都会进到请求里，一起被打印 |
- 结论：`SimpleLoggerAdvisor` 适合**本地调试**；生产环境要么关掉 DEBUG，要么用它的 `requestToString` / `responseToString` 只打印 id、model、usage 这些元数据（脱敏）
- 日志量也是问题：一次请求就是几十行 JSON，人设每次都完整打印一遍

额外发现：你贴的日志里 `cached_tokens: 128`。326 个输入 token 里有 128 个**命中了 DeepSeek 的缓存**，就是每次都一样的人设开头，这部分按更低的价格计费（第 04 课回顾里提过）。人设放在最前面且保持不变，缓存才命中得了。

## 课外问答

**Q：`BaseAdvisor.adviseStream` 里这段代码怎么理解？**
```java
Flux<ChatClientResponse> chatClientResponseFlux = Mono.just(chatClientRequest)
    .publishOn(getScheduler())
    .map(request -> this.before(request, streamAdvisorChain))
    .flatMapMany(streamAdvisorChain::nextStream);
```
A：意思是"在另一个线程上先执行 before，再把处理后的请求交给下一层，拿回一串片段"。逐行：
| 代码 | 作用 | 类比 |
|---|---|---|
| `Mono.just(request)` | 把一个请求装进只有 1 个元素的 Mono | 把一份文件放进传送带 |
| `.publishOn(scheduler)` | 后面的步骤换到 `boundedElastic` 线程池执行 | 传送带转到另一个车间 |
| `.map(r -> before(r))` | 1 个进、1 个出：执行 before，得到修改后的请求 | 盖个章 |
| `.flatMapMany(nextStream)` | 1 个进、N 个出：交给下一层，它返回一个 Flux | 交给下一个车间，它送回一箱零件 |

三个要点（写了 RxDemo 实测）：
1. **懒执行**：这几行只是"搭好流水线"，before 一次都没执行；等 subscribe（Spring MVC 开始往浏览器写）时才真正跑
2. **换线程**：实测 subscribe 在 main 线程，before 和后续全在 `boundedElastic-1`；第 05 课日志里 `ndedElastic-154` 就是这个
3. **为什么换线程**：before 里可能有阻塞操作（第 06 课查数据库读历史消息、第 12 课查向量库），放在响应式的事件线程上会卡住别的请求；`boundedElastic` 是专门给阻塞任务用的线程池
- Mono 和 Flux：Mono 是 0~1 个元素，Flux 是 0~N 个。请求只有一个所以用 Mono，响应是一串片段所以用 Flux，`flatMapMany` 就是从 Mono 变成 Flux 的桥
- 和 Stream 的 `map` / `flatMap` 很像；区别是 Stream 拿到就执行，Reactor 要等订阅
