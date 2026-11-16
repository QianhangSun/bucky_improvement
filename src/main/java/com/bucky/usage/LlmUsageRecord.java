package com.bucky.usage;

/**
 * 一次模型调用的用量，对应 llm_usage 表的一行（不含自增 id 和 created_at，这两个由数据库生成）。
 *
 * @param model            服务端返回的实际模型名
 * @param responseId       厂商的请求 id，排查问题用
 * @param promptTokens     输入 token
 * @param completionTokens 输出 token
 * @param cachedTokens     输入里命中缓存的 token，厂商不返回时为 null
 * @param durationMs       耗时（毫秒）
 * @param streaming        是否流式调用
 */
public record LlmUsageRecord(String model, String responseId, int promptTokens, int completionTokens,
                             Integer cachedTokens, long durationMs, boolean streaming) {
}
