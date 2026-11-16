# 导师模块 · Spring AI 学习路线

边做导师模块，边把 Spring AI 学一遍。一课只做一件事，做完、跑通、弄懂了再进入下一课。

| 课 | 主题 | 学到的 Spring AI 能力 | 导师模块得到什么 | 状态 |
|---|---|---|---|---|
| 01 | 项目骨架 | （还没用到 Spring AI）Maven BOM、自动配置是什么 | 能启动的空应用 | ✅ |
| 02 | 第一次调用大模型 | `ChatModel`、starter 与自动配置、`application.yml` 配模型 | 能回答一句话的接口 | ✅ |
| 03 | ChatClient 入门 | `ChatClient` 链式 API、system / user 消息、`PromptTemplate` | 有“导师人设”的回答 | ✅ |
| 04 | 流式输出 | `.stream()`、`Flux`、SSE | 回答逐字出现 | ✅ |
| 05 | Advisor | Advisor 拦截链、`SimpleLoggerAdvisor`、自定义 Advisor | 打印请求日志、统计 token 用量 | ⏳ |
| 06 | 会话记忆 | `ChatMemory`、`MessageChatMemoryAdvisor`、会话 ID | 导师记得你上一句话说了什么 | |
| 07 | 结构化输出 | `.entity(Class)`、`BeanOutputConverter` | 让模型返回 Java 对象（如学习建议列表） | |
| 08 | 上下文组装 | 动态 system prompt、模板变量 | 导师能看到你的学习进度（先用假数据） | |
| 09 | 工具调用 | `@Tool`、`ToolCallback`、工具调用循环 | 导师自己去查计划和技能 | |
| 10 | 人在回路 | 工具返回“提议”而不是直接执行 | 导师改数据前先问你 | |
| 11 | Embedding 与向量库 | `EmbeddingModel`、`VectorStore`、`Document` | 笔记能按“意思”被搜到 | |
| 12 | RAG | `QuestionAnswerAdvisor`、`RetrievalAugmentationAdvisor` | 导师引用你的笔记回答 | |
| 13 | MCP Client | MCP 协议、`spring-ai-starter-mcp-client` | 导师能读本地资料目录 | |
| 14 | MCP Server | `spring-ai-starter-mcp-server-webmvc` | 在 Claude Code 里查询成长台 | |
| 15 | 可观测与测试 | Micrometer 观测、mock 模型写测试 | 心里有底的导师 | |

岗位采集（PRD 3.9）只列在计划里，这个系列不实现。

## 版本
- Java 21.0.6 · Maven 3.9.14
- Spring Boot **4.1.2** · Spring AI **2.0.2**（2026-11-12 在 Maven Central 核对过）
- 模型：DeepSeek `deepseek-v4-flash`（Key 放在环境变量 `DEEPSEEK_API_KEY`）。PRD 写的是 Claude，原因见第 02 课

## 怎么跟
1. 先读当课笔记（`docs/learning/NN-*.md`）；做完的练习和点评在 `NN-review.md`
2. 对照笔记看代码，代码里的注释就是讲解
3. 自己跑一遍，做课后练习
4. 有问题直接问；懂了说“下一课”
