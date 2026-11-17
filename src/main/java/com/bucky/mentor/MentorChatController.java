package com.bucky.mentor;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi.ChatCompletionRequest.Thinking;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Pattern;
import reactor.core.publisher.Flux;

/**
 * 第 03 课：ChatClient 入门。对比 MentorController 里直接用 ChatModel 的写法。
 * 第 04 课：在同一个类里加了流式版本（/chat/stream、/plan/stream），和非流式版本对比。
 * 第 06 课：/chat、/chat/stream 加上会话记忆，用 cid 区分不同的对话。
 */
@RestController
public class MentorChatController {

    /**
     * 第 06 课练习 3：cid 的格式规则。
     * 表里 conversation_id 是 VARCHAR(36)，超长会在存消息时报数据库错误（500）。
     * 在 Controller 入口就拦下来，返回 400，请求不会走到 Advisor 和数据库。
     * 只允许字母、数字、下划线、横线，最长 36 位（正好能放 UUID）。
     */
    private static final String CID_REGEX = "^[A-Za-z0-9_-]{1,36}$";
    private static final String CID_MESSAGE = "cid 只能包含字母、数字、_ 和 -，长度 1 到 36";

    private final ChatClient chatClient;
    private final MessageChatMemoryAdvisor memoryAdvisor;
    private final AppendOnlyChatMemory chatMemory;

    public MentorChatController(ChatClient mentorChatClient, MessageChatMemoryAdvisor chatMemoryAdvisor,
                                AppendOnlyChatMemory chatMemory) {
        this.chatClient = mentorChatClient;
        this.memoryAdvisor = chatMemoryAdvisor;
        this.chatMemory = chatMemory;
    }

    /**
     * ① 最基本的链式调用：system 人设已经在 MentorConfig 里配好，这里只给 user 消息。
     * 第 06 课：加上记忆。cid 是会话 ID，同一个 cid 的消息会被记住，换一个 cid 就是一段新对话。
     * 练习 3：cid 改成必填，去掉 defaultValue = "default"，否则不传 cid 的人会共用同一段对话。
     */
    @GetMapping("/api/mentor/chat")
    public String chat(@RequestParam String q,
                       @RequestParam @Pattern(regexp = CID_REGEX, message = CID_MESSAGE) String cid) {
        return chatClient.prompt()
                .user(q)
                .advisors(a -> a.advisors(memoryAdvisor)                    // 只给这次调用挂上记忆 Advisor
                        .param(ChatMemory.CONVERSATION_ID, cid))           // 告诉它是哪个会话，放进请求的 context
                .call()
                .content();   // 直接拿回答文本；想要元数据就用 .chatResponse()
    }

    /**
     * 第 06 课：看某个会话里存了哪些消息。
     * 加餐：window=true 时返回下次会塞给模型的最近 N 条（get），否则返回完整历史（history）。
     */
    @GetMapping("/api/mentor/conversations/{cid}")
    public List<MessageView> history(@PathVariable @Pattern(regexp = CID_REGEX, message = CID_MESSAGE) String cid,
                                     @RequestParam(defaultValue = "false") boolean window) {
        List<Message> messages = window ? chatMemory.get(cid) : chatMemory.history(cid);
        return messages.stream()
                .map(m -> new MessageView(m.getMessageType().name(), m.getText()))
                .toList();
    }

    /** 第 06 课：清空某个会话的记忆 */
    @DeleteMapping("/api/mentor/conversations/{cid}")
    public void clear(@PathVariable @Pattern(regexp = CID_REGEX, message = CID_MESSAGE) String cid) {
        chatMemory.clear(cid);
    }

    public record MessageView(String type, String text) {
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
        return planRequest(goal).call().chatResponse();
    }

    // ===================== 第 04 课：流式输出 =====================

    /**
     * ④ 流式版 /chat：把 .call() 换成 .stream()，返回值从 String 变成 Flux。
     * produces = text/event-stream 让 Spring MVC 用 SSE 格式，每来一小段就立刻推给客户端。
     * 每段包成 StreamChunk，原因见 StreamChunk 的注释（空格会被 SSE 吃掉）。
     */
    @GetMapping(value = "/api/mentor/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunk> chatStream(@RequestParam String q,
                                        @RequestParam @Pattern(regexp = CID_REGEX, message = CID_MESSAGE) String cid) {
        return chatClient.prompt()
                .user(q)
                .advisors(a -> a.advisors(memoryAdvisor).param(ChatMemory.CONVERSATION_ID, cid))
                .stream()
                .content()
                .map(StreamChunk::new);
    }

    /** ⑤ 流式版 /plan：请求的构造和 ③ 完全一样，只是最后一步不同 */
    @GetMapping(value = "/api/mentor/plan/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamChunk> planStream(@RequestParam String goal) {
        return planRequest(goal).stream().content().map(StreamChunk::new);
    }

    /** ③ 和 ⑤ 共用的请求：到 .call() / .stream() 之前，两种调用方式写法完全一样 */
    private ChatClient.ChatClientRequestSpec planRequest(String goal) {
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
                        .maxTokens(4096));            // 思考也消耗 token，上限放宽，避免被截断
    }
}
