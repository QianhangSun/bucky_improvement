package com.bucky.mentor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * 第 03 课：用自动配置提供的 ChatClient.Builder 造一个“导师专用”的 ChatClient。
 * defaultSystem 里的人设会自动加到这个 ChatClient 的每一次调用前面。
 */
@Configuration
public class MentorConfig {

    @Bean
    public ChatClient mentorChatClient(ChatClient.Builder builder,
                                       @Value("classpath:prompts/mentor-system.st") Resource systemPrompt) {
        return builder
                .defaultSystem(systemPrompt)
                .build();
    }
}
