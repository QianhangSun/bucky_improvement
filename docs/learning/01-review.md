# 第 01 课 · 课后练习回顾

> 我的回答原样保留，点评写在后面。

## 练习 1：在 BOM 里搜 anthropic，有哪几个模块？

**我的回答**

一共三个：spring-ai-anthropic、spring-ai-autoconfigure-model-anthropic 和 spring-ai-starter-model-anthropic。这个问题有什么意义？

**点评** ✅ 正确。意义在于认识 Spring AI 接一个模型的固定**三层结构**：

| 模块 | 干什么 | 类比 |
|---|---|---|
| `spring-ai-anthropic` | 核心代码，真正发 HTTP 请求 | 发动机 |
| `spring-ai-autoconfigure-model-anthropic` | 读取配置，按条件自动创建 `ChatModel` Bean | 点火装置 |
| `spring-ai-starter-model-anthropic` | 空壳，把上面两个一起带进来 | 整车套餐 |

- 只需加 starter 一个依赖，且不写版本
- 排查方向：Bean 没创建看 autoconfigure；调用出错看核心模块
- 换 OpenAI、DeepSeek 都是同样三件套（第 02 课实际换成了 DeepSeek）

## 练习 2：CONDITIONS EVALUATION REPORT

**我的回答**

（贴了一段 Positive matches 日志）生效是 matched，失效是 did not find any beans 吗？

**点评** ❌ 不对。判断生效与否**看它在报告的哪一段**，不看原因里的措辞：
- **Positive matches**：生效，条目写 `XXX matched:`
- **Negative matches**：没生效，条目写 `XXX: Did not match:`

贴的全是 matched，全部生效。"did not find any beans" 出现在 `@ConditionalOnMissingBean` 上，意思是"容器里还没有这个 Bean，我才创建"，没找到 = 条件满足 = 生效。这就是"自己定义一个 Bean 就能覆盖默认配置"的机制。

## 练习 3：不开虚拟线程，100 个用户同时问、每个回答 20 秒，Tomcat 会怎样？

**我的回答**

Tomcat 还剩 100 个可以连接，但问问题的线程就需要等待，可能使用率 50%？

**点评** ⚠️ 一半对。题目数字出得不严谨：Tomcat 默认 200 个线程，100 人还扛得住。
- "线程池用了 50%"对：100/200
- 但 **CPU 几乎为 0**，线程都在干等网络；代价在内存，每个平台线程约 1MB 栈
- 换成 1000 人才暴露问题：前 200 个占满线程各 20 秒，剩下 800 个连接被接收但只能排队，连 `/api/ping` 都要等，网站像卡死，而 CPU 是闲的
- 开虚拟线程：每请求一个虚拟线程，等待时只占几 KB，没有 200 上限；瓶颈转移到模型 API 限流

一句话：平台线程的问题不是"算不过来"，是"等的时候占着位置不放"。

## 课外问答

**Q：AOP 是什么？**
A：把很多方法都要做的"杂事"（日志、事务、权限）抽出来统一写一次，自动套到这些方法上，业务代码一行不改。像机场安检：每个旅客去的登机口不同，但都要先过统一的安检口。Spring 靠**代理对象**实现：调用方 → 代理（前置逻辑）→ 真实方法 → 代理（后置逻辑）。`spring.aop.proxy-target-class=true` 表示用继承类的方式（CGLIB）生成代理。常见：`@Transactional`、`@Async`、`@Cacheable`；Spring AI 的 **Advisor** 是同一思路（第 05 课）。

**Q：BOM 文件在 IDEA 里怎么打开？**
A：BOM 不在 External Libraries 里，IDEA 不给它建索引，⌘ 点击和 ⌘⇧O 都找不到。直接按路径打开：`open -a "IntelliJ IDEA" ~/.m2/repository/org/springframework/ai/spring-ai-bom/2.0.2/spring-ai-bom-2.0.2.pom`。
