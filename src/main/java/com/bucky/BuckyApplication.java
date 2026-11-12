package com.bucky;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 程序入口。
 *
 * @SpringBootApplication 是三个注解的合体：
 *   - @Configuration：这个类本身可以定义 Bean
 *   - @EnableAutoConfiguration：根据 classpath 上有哪些 jar，自动创建对应的 Bean
 *     （第 2 课加入 Spring AI 依赖后，ChatModel 就是这样被“自动”创建出来的）
 *   - @ComponentScan：扫描 com.bucky 包及其子包下的 @Component / @Service / @RestController
 */
@SpringBootApplication
public class BuckyApplication {

    public static void main(String[] args) {
        SpringApplication.run(BuckyApplication.class, args);
    }
}
