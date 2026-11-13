package com.bucky.mentor;

import org.springframework.ai.chat.metadata.Usage;

/**
 * /api/mentor/hello 的返回值。
 * record：一行声明字段，构造器、访问方法、equals、hashCode、toString 由编译器生成；
 * 字段都是 final，创建后不能修改。Jackson 会把它转成和以前 Map 一样的 JSON。
 *
 * @param answer 模型的回答文本
 * @param model  服务端返回的实际模型名（不是配置里的值，见第 02 课练习 2）
 * @param usage  本次调用的 token 用量
 */
public record HelloResponse(String answer, String model, Usage usage) {
}
