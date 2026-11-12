package com.bucky.common;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 一个最简单的接口，用来确认“应用启动了、Web 层通了”。
 * 后面每一课出问题时，先访问它，能排除一半的可能性。
 */
@RestController
public class HealthController {

    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of(
                "status", "ok",
                "time", LocalDateTime.now().toString(),
                "javaVersion", System.getProperty("java.version"));
    }
}
