package com.bucky.usage;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 第 05 课：用量查询，数据都来自数据库，重启后不会清零。
 */
@RestController
public class UsageController {

    private final LlmUsageRepository repository;

    public UsageController(LlmUsageRepository repository) {
        this.repository = repository;
    }

    /** 全部历史合计 */
    @GetMapping("/api/mentor/usage")
    public LlmUsageRepository.UsageSummary total() {
        return repository.total();
    }

    /** 按天、按模型汇总，默认最近 7 天 */
    @GetMapping("/api/mentor/usage/daily")
    public List<LlmUsageRepository.DailyUsage> daily(@RequestParam(defaultValue = "7") int days) {
        return repository.daily(days);
    }
}
