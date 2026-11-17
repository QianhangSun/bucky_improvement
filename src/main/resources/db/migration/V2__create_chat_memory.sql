-- 第 06 课：会话记忆表。内容照抄 Spring AI 自带的 schema-postgresql.sql
-- （在 spring-ai-model-chat-memory-repository-jdbc 的 jar 里），改由 Flyway 统一管理。
-- 表名、列名不能改：JdbcChatMemoryRepository 的 SQL 里写死了这些名字。
CREATE TABLE IF NOT EXISTS SPRING_AI_CHAT_MEMORY (
    conversation_id VARCHAR(36) NOT NULL,          -- 会话 ID，最长 36 个字符（正好放一个 UUID）
    content TEXT NOT NULL,                         -- 消息内容
    type VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT', 'SYSTEM', 'TOOL')),
    "timestamp" TIMESTAMP NOT NULL,
    sequence_id BIGINT NOT NULL                    -- 同一会话里消息的先后顺序
);

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_TIMESTAMP_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, "timestamp");

CREATE INDEX IF NOT EXISTS SPRING_AI_CHAT_MEMORY_CONVERSATION_ID_SEQUENCE_ID_IDX
    ON SPRING_AI_CHAT_MEMORY (conversation_id, sequence_id);
