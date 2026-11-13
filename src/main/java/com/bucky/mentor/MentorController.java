package com.bucky.mentor;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第 02 课：第一次调用大模型。
 * ChatModel 不是我们创建的，是 spring-ai-starter-model-anthropic 的自动配置
 * 读取 application.yml 后放进容器的，这里直接通过构造器注入。
 */
@RestController
public class MentorController {

    private final ChatModel chatModel;

    public MentorController(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    @GetMapping("/api/mentor/hello")
    public HelloResponse hello(@RequestParam(defaultValue = "用一句话介绍你自己") String q) {
        // Prompt 是一次请求的完整内容；这里只有一条用户消息
        ChatResponse response = chatModel.call(new Prompt(q));

        // ChatResponse 除了回答文本，还带着元数据：实际用的模型、消耗的 token
        // 以前用 Map.of("answer", ...) 返回，键名写错编译器发现不了；换成 record 后字段由类型保证
        return new HelloResponse(
                response.getResult().getOutput().getText(),
                response.getMetadata().getModel(),
                response.getMetadata().getUsage());
    }
}
