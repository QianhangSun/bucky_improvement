package com.bucky.mentor;

/**
 * 流式输出的一个片段，第 04 课。
 * 为什么不直接返回 Flux<String>：SSE 规范规定客户端要去掉 "data:" 后面的第一个空格，
 * 模型的 token 经常以空格开头（如 " HashMap"），直接发字符串会把中英文之间的空格吃掉。
 * 包成 JSON {"text":" HashMap"} 后，空格在引号里面，不受影响。
 *
 * @param text 这一小段新生成的文字
 */
public record StreamChunk(String text) {
}
