-- 第 05 课：每次调用模型记一行。Flyway 启动时自动执行，执行过的脚本不会再跑第二次。
-- 文件名规则：V<版本号>__<描述>.sql，两个下划线。已经执行过的脚本不要改，要改表结构就新建 V2__xxx.sql。
CREATE TABLE llm_usage (
    id                BIGSERIAL    PRIMARY KEY,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),   -- 调用完成的时间（带时区）
    model             TEXT         NOT NULL,                 -- 服务端返回的实际模型名
    response_id       TEXT,                                  -- 厂商的请求 id，排查问题时用
    prompt_tokens     INT          NOT NULL,
    completion_tokens INT          NOT NULL,
    cached_tokens     INT,                                   -- 命中缓存的输入 token（DeepSeek 才有）
    duration_ms       BIGINT       NOT NULL,                 -- 从发出请求到拿到 usage 的耗时
    streaming         BOOLEAN      NOT NULL                  -- 是不是流式调用
);

-- 按时间查询、按天汇总都要用 created_at
CREATE INDEX idx_llm_usage_created_at ON llm_usage (created_at);
