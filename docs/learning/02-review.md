# 第 02 课 · 课后练习回顾

> 我的回答原样保留，点评写在后面。以后回顾时先盖住点评，自己再答一遍。

## 练习 1：关掉 thinking，对比 completionTokens

**我的回答**

关掉后返回：
```json
{"model":"deepseek-flash","answer":"我是DeepSeek，一个由深度求索公司创造的AI助手，致力于用热情细腻的方式为你提供知识解答和创作灵感。",
 "usage":{"promptTokens":8,"completionTokens":28,"totalTokens":36}}
```
completionTokens 变少了。追问：什么时候应该开 thinking，什么时候不应该开？

**点评** ✅ 观察正确：133 → 28。多出来的 token 是模型的"内心草稿"，看不到但要付费，还要等它写完。

| 适合开（值得多花钱、多等一会儿） | 适合关（追求快和省钱） |
|---|---|
| 数学、逻辑推理、多步骤分析 | 打招呼、闲聊、一句话问答 |
| 写复杂代码、排查 bug | 分类、抽取字段（如从 JD 抽薪资） |
| 制定计划、权衡方案 | 翻译、改写、摘要 |
| 需要多次调用工具的任务 | 对首字速度要求高的流式聊天 |

判断方法：换成人来做，需要先打草稿吗？需要就开。

放到导师模块：日常答疑关，"针对这个 JD 帮我定两周复习计划"开。同一个 `ChatModel`，**每次调用传不同参数**即可（第 03 课演示）。

补充：`promptTokens` 也从 34 降到 8，推测是开思考时服务端额外加了提示词（未核实）。

## 练习 2：返回的 model 是 deepseek-flash，配置的是 deepseek-v4-flash，这个值是谁给的？

**我的回答**

不知道，请你告诉我答案。

**点评** 这个值是 **DeepSeek 服务端在响应里返回的**，不是配置的原样回显。
```
配置 deepseek-v4-flash → 请求体 {"model":"deepseek-v4-flash"}
服务端交给内部名为 deepseek-flash 的模型 → 响应体 {"model":"deepseek-flash"}
→ response.getMetadata().getModel()
```
`deepseek-v4-flash` 是对外的别名，响应里是实际干活的模型。用处：确认这次到底是谁回答的。线上排查、统计费用应记录这个实际值。

## 练习 3：max-tokens 改成 10

**我的回答**
```
curl -G localhost:8080/api/mentor/hello --data-urlencode "q=今天有哪些新闻， 列举10条"
{"answer":"我无法实时获取今天的新闻。建议你打开", "usage":{"promptTokens":11,"completionTokens":10,"totalTokens":21}}
```
返回内容不全了。追问：除此之外还有哪些影响？

**点评** ✅ 截断观察正确，completionTokens 刚好等于 10。其他影响：

| 影响 | 说明 |
|---|---|
| 硬切，不是缩写 | 模型不知道有上限，照常写长回答，到第 10 个 token 被切断，所以话说一半 |
| 结束原因变成 `length` | 正常写完是 `stop`。可用 `response.getResult().getMetadata().getFinishReason()` 判断回答是否完整 |
| 开思考时更危险 | 思考也算在 max-tokens 里，可能思考没写完就被切，answer 为空 |
| 结构化输出会解析失败 | 第 07 课返回 JSON，切一半就是残缺 JSON，解析报错 |
| 好处 | 控制费用和延迟，防止模型一口气写几千字 |

怎么设：正常所需长度的 2~3 倍，用来兜底，别卡在刚好够用。导师答疑 1024~2048。

额外发现：模型说"无法实时获取新闻"。大模型只知道训练时学过的东西，查不了实时信息。第 09 课的**工具调用**解决这个问题。

## 练习 4：Key 改错一位，报什么错？

**我的回答**

应该是 401 吧？

**点评** ✅ 正确。而且在 11-13 早上真实碰到过（环境变量没读到，Key 变成了字面量 `${DEEPSEEK_API_KEY}`，报错里显示 `****KEY}`）：
- 状态码 `401`，`authentication_error`
- 异常 `NonTransientAiException`，永久错误
- 没有重试，堆栈里只有一次调用

| | 401 | 402 |
|---|---|---|
| 含义 | Key 不对，不知道你是谁 | 知道你是谁，余额不足 |
| 重试有用吗 | 没用 | 没用 |

## 课外问答（同一课里问到的）

**Q：`spring-boot-starter-web` 为什么也不用写版本号？**
A：`<parent>spring-boot-starter-parent 4.1.2</parent>` → 它的父 `spring-boot-dependencies 4.1.2` 里有 650 多个依赖的版本清单（第 2838 行 `spring-boot-starter-web 4.1.2`），通过 parent 继承下来。Spring AI 的 BOM 用 `scope=import` 引入，因为 Maven 只允许一个 parent，而 import 可以叠加多个。

**Q：日志里 `Unable to load io.netty.resolver.dns.macos...` 的 ERROR 要紧吗？**
A：不要紧。Netty 在 Mac 上找不到原生 DNS 组件，退回系统默认解析，功能不受影响。想消掉就加 `netty-resolver-dns-native-macos` 依赖。

**Q：为什么昨天能调、今天 401？**
A：应用启动时没读到环境变量。环境变量只传给"读过 `.zshrc` 之后启动"的程序：旧终端要 `source ~/.zshrc`，IDEA 要 ⌘Q 彻底退出再打开。用 `echo ${#DEEPSEEK_API_KEY}` 检查长度。
