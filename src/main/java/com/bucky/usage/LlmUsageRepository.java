package com.bucky.usage;

import java.time.LocalDate;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * llm_usage 表的读写。用 Spring 6.1 起提供的 JdbcClient：直接写 SQL，用命名参数，结果自动映射到 record。
 */
@Repository
public class LlmUsageRepository {

    private final JdbcClient jdbc;

    public LlmUsageRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(LlmUsageRecord r) {
        jdbc.sql("""
                INSERT INTO llm_usage (model, response_id, prompt_tokens, completion_tokens,
                                       cached_tokens, duration_ms, streaming)
                VALUES (:model, :responseId, :promptTokens, :completionTokens,
                        :cachedTokens, :durationMs, :streaming)
                """)
                .paramSource(r)   // 按 record 的字段名填 :参数，不用一个个 .param(...)
                .update();
    }

    /** 全部历史的合计 */
    public UsageSummary total() {
        return jdbc.sql("""
                SELECT count(*)                               AS calls,
                       coalesce(sum(prompt_tokens), 0)        AS prompt_tokens,
                       coalesce(sum(completion_tokens), 0)    AS completion_tokens,
                       coalesce(sum(cached_tokens), 0)        AS cached_tokens
                FROM llm_usage
                """)
                .query(UsageSummary.class)
                .single();
    }

    /** 按天、按模型汇总，最近 days 天，日期按北京时间划分 */
    public List<DailyUsage> daily(int days) {
        return jdbc.sql("""
                SELECT (created_at AT TIME ZONE 'Asia/Shanghai')::date AS day,
                       model,
                       count(*)                                AS calls,
                       sum(prompt_tokens)                      AS prompt_tokens,
                       sum(completion_tokens)                  AS completion_tokens,
                       coalesce(sum(cached_tokens), 0)         AS cached_tokens,
                       round(avg(duration_ms))                 AS avg_duration_ms
                FROM llm_usage
                WHERE created_at >= now() - make_interval(days => :days)
                GROUP BY day, model
                ORDER BY day DESC, model
                """)
                .param("days", days)
                .query(DailyUsage.class)   // 列名 prompt_tokens 自动对应字段 promptTokens
                .list();
    }

    public record UsageSummary(long calls, long promptTokens, long completionTokens, long cachedTokens) {
    }

    public record DailyUsage(LocalDate day, String model, long calls, long promptTokens,
                             long completionTokens, long cachedTokens, long avgDurationMs) {
    }
}
