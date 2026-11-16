package com.bucky.mentor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * 第 03 课：用自动配置提供的 ChatClient.Builder 造一个“导师专用”的 ChatClient。
 * defaultSystem 里的人设会自动加到这个 ChatClient 的每一次调用前面。
 * 第 05 课：defaultAdvisors 给每一次调用都套上 Advisor，业务代码不用改。
 */
@Configuration
public class MentorConfig {

    @Bean
    public ChatClient mentorChatClient(ChatClient.Builder builder,
                                       @Value("classpath:prompts/mentor-system.st") Resource systemPrompt,
                                       TokenUsageAdvisor tokenUsageAdvisor) {
        return builder
                .defaultSystem(systemPrompt)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),   // Spring AI 自带：DEBUG 级别打印完整请求和响应，order = 0
                        tokenUsageAdvisor)           // 我们自己写的：统计耗时和 token，order = 100
                .build();
    }
}
