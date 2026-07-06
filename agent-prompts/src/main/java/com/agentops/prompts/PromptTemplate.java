package com.agentops.prompts;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Prompt 模板 — 包含模板文本，支持通过变量占位符进行运行时渲染。
 *
 * <p>占位符语法为 {@code {{变量名}}}，与主流 LLM Prompt 模板风格一致。
 * 渲染时将占位符替换为对应变量的字符串值。
 *
 * <p>示例：
 * <pre>{@code
 *   PromptTemplate template = new PromptTemplate(
 *       "diagnosis",
 *       "你是一个 SRE 工程师。请分析以下异常堆栈：\n{{stackTrace}}"
 *   );
 *   String prompt = template.render(Map.of("stackTrace", stackTraceText));
 * }</pre>
 *
 * @param name     模板名称，全局唯一
 * @param template 模板文本，可包含 {@code {{变量名}}} 占位符
 */
public record PromptTemplate(String name, String template) {

    /** 占位符正则：匹配 {@code {{变量名}}} 格式 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{(\\w+)}}");

    public PromptTemplate {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("模板名称不能为空");
        }
        if (template == null) {
            throw new IllegalArgumentException("模板文本不能为 null");
        }
    }

    /**
     * 使用给定的变量渲染模板，将所有 {@code {{变量名}}} 占位符替换为对应值。
     *
     * @param variables 变量名到值的映射
     * @return 渲染后的 Prompt 文本
     * @throws IllegalArgumentException 如果某个占位符变量未在 variables 中提供
     */
    public String render(Map<String, Object> variables) {
        Map<String, Object> vars = variables != null ? variables : Collections.emptyMap();
        StringBuilder result = new StringBuilder();
        Matcher matcher = PLACEHOLDER.matcher(template);

        while (matcher.find()) {
            String key = matcher.group(1);
            Object value = vars.get(key);
            if (value == null && !vars.containsKey(key)) {
                throw new IllegalArgumentException(
                        "模板 [" + name + "] 中的占位符 {{" + key + "}} 缺少对应的变量值");
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    value != null ? value.toString() : ""));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * 返回模板中所有变量占位符的名称集合。
     *
     * @return 变量名列表（去重，按出现顺序）
     */
    public Map<String, Object> extractVariables() {
        Map<String, Object> names = new HashMap<>();
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            names.putIfAbsent(matcher.group(1), null);
        }
        return names;
    }
}
