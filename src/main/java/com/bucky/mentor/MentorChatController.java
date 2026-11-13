package com.bucky.mentor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest.Thinking;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第 03 课：ChatClient 入门。对比 MentorController 里直接用 ChatModel 的写法。
 */
@RestController
public class MentorChatController {

    private final ChatClient chatClient;

    public MentorChatController(ChatClient mentorChatClient) {
        this.chatClient = mentorChatClient;
    }

    /** ① 最基本的链式调用：system 人设已经在 MentorConfig 里配好，这里只给 user 消息 */
    @GetMapping("/api/mentor/chat")
    public String chat(@RequestParam String q) {
        return chatClient.prompt()
                .user(q)
                .call()
                .content();   // 直接拿回答文本；想要元数据就用 .chatResponse()
    }

    /** ② 提示词模板：{topic}、{level} 是占位符，用 param 填进去 */
    @GetMapping("/api/mentor/explain")
    public String explain(@RequestParam String topic,
                          @RequestParam(defaultValue = "刚入门的初学者") String level) {
        return chatClient.prompt()
                .user(u -> u.text("请用{level}能听懂的方式解释：{topic}。最后给一个面试时可能被追问的问题。")
                        .param("topic", topic)
                        .param("level", level))
                .call()
                .content();
    }

    /** ③ 单次调用覆盖参数：yml 里默认关闭思考，这个接口单独打开，并放宽 max-tokens */
    @GetMapping("/api/mentor/plan")
    public ChatResponse plan(@RequestParam String goal) {
        return chatClient.prompt()
                .user(u -> u.text("""
                        我的目标是：{goal}。请帮我制定一个两周的学习计划。
                        格式要求：
                        - 第一行用一句话说明整体安排
                        - 然后按天列出 D1 到 D14，每天一行：主题 + 当天要做出的一个产出
                        - 最后最多 2 条提醒
                        """)
                        .param("goal", goal))
                .options(DeepSeekChatOptions.builder()
                        .thinking(Thinking.ENABLED)    // 制定计划需要“先打草稿”，打开思考
                        .maxTokens(4096))             // 思考也消耗 token，上限放宽，避免被截断
                .call()
                .chatResponse();
    }
}
