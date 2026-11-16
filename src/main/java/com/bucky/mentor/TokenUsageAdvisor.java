package com.bucky.mentor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Flux;

import com.bucky.usage.LlmUsageRecord;
import com.bucky.usage.LlmUsageRepository;

/**
 * 第 05 课：自定义 Advisor，统计每次调用的耗时和 token 用量，并写入数据库。
 *
 * BaseAdvisor 把一次调用拆成 before / after 两步，和 AOP 的“前置 + 后置”一样：
 *   before：请求发给模型之前，记下开始时间
 *   after ：模型返回之后，读 usage、算耗时、交给后台线程写库
 * 同一个类同时支持 .call() 和 .stream()：流式时 after 只在带 finishReason 的最后一片上调用一次。
 */
@Component
public class TokenUsageAdvisor implements BaseAdvisor, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(TokenUsageAdvisor.class);

    /** 在请求上下文里存开始时间用的 key。上下文会沿着 Advisor 链一路传下去，after 里能取回 */
    private static final String START_NANOS = "bucky.usage.startNanos";

    /** 标记这次是不是流式调用。不能用 chain instanceof StreamAdvisorChain 判断，见下面 adviseCall 的注释 */
    private static final String STREAMING = "bucky.usage.streaming";

    private final LlmUsageRepository repository;

    /**
     * 写库用的线程池：每个任务一个虚拟线程。
     * 写库放到这里，用户拿到回答不用等数据库；数据库慢了或挂了，也不影响回答。
     */
    private final ExecutorService writer = Executors.newVirtualThreadPerTaskExecutor();

    public TokenUsageAdvisor(LlmUsageRepository repository) {
        this.repository = repository;
    }

    /**
     * 区分同步和流式：只有入口方法知道自己是哪一种。
     * 不能在 after 里用 chain instanceof StreamAdvisorChain 判断——Spring AI 的链对象（DefaultAroundAdvisorChain）
     * 同时实现了 CallAdvisorChain 和 StreamAdvisorChain，instanceof 永远为 true（第 05 课实测踩过）。
     * 所以在入口处往上下文里放一个标记，再交给 BaseAdvisor 原来的实现。
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        return BaseAdvisor.super.adviseCall(request.mutate().context(STREAMING, false).build(), chain);
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain chain) {
        return BaseAdvisor.super.adviseStream(request.mutate().context(STREAMING, true).build(), chain);
    }

    @Override
    public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
        return request.mutate()
                .context(START_NANOS, System.nanoTime())
                .build();
    }

    @Override
    public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
        ChatResponse chatResponse = response.chatResponse();
        Usage usage = chatResponse == null ? null : chatResponse.getMetadata().getUsage();
        if (usage == null || usage.getTotalTokens() == 0) {
            return response;   // 没有用量信息就不记
        }

        Object start = response.context().get(START_NANOS);
        long costMs = start == null ? -1 : (System.nanoTime() - (long) start) / 1_000_000;

        LlmUsageRecord record = new LlmUsageRecord(
                chatResponse.getMetadata().getModel(),
                chatResponse.getMetadata().getId(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                cachedTokens(usage),
                costMs,
                Boolean.TRUE.equals(response.context().get(STREAMING)));

        log.info("调用完成：耗时 {}ms，输入 {}（缓存命中 {}），输出 {} token",
                costMs, record.promptTokens(), record.cachedTokens(), record.completionTokens());

        writer.submit(() -> {
            try {
                repository.insert(record);
            } catch (Exception e) {
                // 记账失败只打日志，不能让用户的回答因为数据库问题失败
                log.warn("用量写入数据库失败：{}", record, e);
            }
        });
        return response;
    }

    /**
     * 命中缓存的输入 token。通用的 Usage 接口里有 getCacheReadInputTokens()，但 DeepSeek 模块没有填它，
     * 只能从厂商原始数据 nativeUsage 里取（见第 05 课笔记）。
     */
    private static Integer cachedTokens(Usage usage) {
        if (usage.getCacheReadInputTokens() != null) {
            return usage.getCacheReadInputTokens().intValue();
        }
        if (usage.getNativeUsage() instanceof DeepSeekApi.Usage ds && ds.promptTokensDetails() != null) {
            return ds.promptTokensDetails().cachedTokens();
        }
        return null;
    }

    /** 数字越小越先执行 before、越后执行 after。见第 05 课笔记“执行顺序” */
    @Override
    public int getOrder() {
        return 100;
    }

    /** 应用关闭时，等还没写完的记录写完再退出 */
    @Override
    public void destroy() {
        writer.close();
    }
}
