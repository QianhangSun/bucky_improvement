package com.bucky.mentor;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;

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

    /**
     * 第 06 课：会话记忆。
     * ChatMemoryRepository 负责“存在哪”：加了 jdbc starter 后，自动配置提供的是 JdbcChatMemoryRepository（存数据库）；
     * 没加时是 InMemoryChatMemoryRepository（存内存，重启就没了）。
     * MessageWindowChatMemory 负责“取多少”：每个会话只保留最近 maxMessages 条，太早的自动丢掉。
     *
     * 第 06 课加餐：换成自己写的 AppendOnlyChatMemory（只追加、取最近 N 条，完整历史存在 chat_message 表）。
     * 想换回 Spring AI 自带的实现，把下面的 return 换成注释里那段即可，其他代码都不用改：
     *   return MessageWindowChatMemory.builder().chatMemoryRepository(repository).maxMessages(20).build();
     */
    @Bean
    public AppendOnlyChatMemory chatMemory(JdbcClient jdbc) {
        return new AppendOnlyChatMemory(jdbc, 20);
    }

    /**
     * 记忆 Advisor：before 里把历史消息塞进请求，after 里把这一轮的回答存起来。
     * 没有放进上面的 defaultAdvisors：2.0.2 要求每次调用都带会话 ID，不带会直接报错，
     * 而 /explain、/plan 是一次性问答，不需要记忆。所以只在聊天接口里按次加上。
     */
    @Bean
    public MessageChatMemoryAdvisor chatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}
