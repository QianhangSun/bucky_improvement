-- 第 06 课加餐：自己实现的 ChatMemory 用这张表（AppendOnlyChatMemory）。
-- 和 SPRING_AI_CHAT_MEMORY 的区别：这里只追加、不删除，完整历史都保留；取的时候只取最近 N 条。
CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,                -- 自增主键，同时代表消息先后顺序
    conversation_id VARCHAR(36) NOT NULL,                 -- 会话 ID，和 Controller 的校验保持一致（最长 36）
    type            VARCHAR(10) NOT NULL CHECK (type IN ('USER', 'ASSISTANT')),
    content         TEXT        NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- “取某个会话最近 N 条”就是 WHERE conversation_id = ? ORDER BY id DESC LIMIT N，这个联合索引正好覆盖
CREATE INDEX idx_chat_message_cid_id ON chat_message (conversation_id, id);
