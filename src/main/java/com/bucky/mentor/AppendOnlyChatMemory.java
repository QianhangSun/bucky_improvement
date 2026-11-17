package com.bucky.mentor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.util.Assert;

/**
 * 第 06 课加餐：自己实现 ChatMemory，“只追加、取最近 N 条”。
 *
 * 对比 Spring AI 自带的 MessageWindowChatMemory + JdbcChatMemoryRepository：
 * - 自带的：每次 add 都先读出整个会话，裁剪到 20 条，再 DELETE 全部 + INSERT 20 条。被裁掉的旧消息永久丢失。
 * - 这个类：add 只 INSERT 新消息；get 只 SELECT 最近 maxMessages 条。数据库里保留完整历史。
 *
 * 为什么实现 ChatMemory 而不是 ChatMemoryRepository：
 * ChatMemoryRepository 只有 saveAll(整个列表)，没有“追加一条”，要实现追加就得自己算出哪些是新消息。
 * ChatMemory 的 add 拿到的正好就是新消息，而 MessageChatMemoryAdvisor 只依赖 ChatMemory 接口，
 * 所以换掉实现以后，Advisor 和 Controller 的代码一行都不用改。
 */
public class AppendOnlyChatMemory implements ChatMemory {

    private final JdbcClient jdbc;
    private final int maxMessages;

    public AppendOnlyChatMemory(JdbcClient jdbc, int maxMessages) {
        Assert.isTrue(maxMessages > 0, "maxMessages 必须大于 0");
        this.jdbc = jdbc;
        this.maxMessages = maxMessages;
    }

    /**
     * Advisor 调用两次：before 里存这一轮的用户消息，after 里存模型的回答。
     * 只存 USER 和 ASSISTANT：人设（SYSTEM）每次由 defaultSystem 加上，不需要存。
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        for (Message m : messages) {
            MessageType type = m.getMessageType();
            if ((type != MessageType.USER && type != MessageType.ASSISTANT) || m.getText() == null) {
                continue;
            }
            jdbc.sql("INSERT INTO chat_message (conversation_id, type, content) VALUES (:cid, :type, :content)")
                    .param("cid", conversationId)
                    .param("type", type.name())
                    .param("content", m.getText())
                    .update();
        }
    }

    /** 给模型用的上下文：只取最近 maxMessages 条 */
    @Override
    public List<Message> get(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        // 倒序取最新的 N 条，再翻转回时间正序（模型要按先后顺序读）
        List<Message> latest = new ArrayList<>(jdbc.sql("""
                        SELECT type, content FROM chat_message
                        WHERE conversation_id = :cid
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .param("cid", conversationId)
                .param("limit", maxMessages)
                .query((rs, i) -> toMessage(rs.getString("type"), rs.getString("content")))
                .list());
        Collections.reverse(latest);

        // 截断的位置可能正好落在一轮中间，第一条是 ASSISTANT（它的问题被截掉了）。
        // 和 MessageWindowChatMemory 一样，往后跳到第一条 USER，保证窗口从完整的一轮开始。
        int start = 0;
        while (start < latest.size() && latest.get(start).getMessageType() != MessageType.USER) {
            start++;
        }
        return latest.subList(start, latest.size());
    }

    /** 完整历史：给“查看聊天记录”这类页面用，不受 maxMessages 限制。这是只追加方案多出来的能力 */
    public List<Message> history(String conversationId) {
        return jdbc.sql("SELECT type, content FROM chat_message WHERE conversation_id = :cid ORDER BY id")
                .param("cid", conversationId)
                .query((rs, i) -> toMessage(rs.getString("type"), rs.getString("content")))
                .list();
    }

    @Override
    public void clear(String conversationId) {
        Assert.hasText(conversationId, "conversationId cannot be null or empty");
        jdbc.sql("DELETE FROM chat_message WHERE conversation_id = :cid")
                .param("cid", conversationId)
                .update();
    }

    private static Message toMessage(String type, String content) {
        return MessageType.USER.name().equals(type) ? new UserMessage(content) : new AssistantMessage(content);
    }
}
