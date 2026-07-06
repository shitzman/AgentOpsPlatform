package com.agentops.tools;

import java.util.Map;

/**
 * 工具执行器 — 执行具体工具逻辑的函数式接口。
 *
 * <p>实现类与 {@link ToolDefinition} 成对注册到 {@link ToolRegistry}。
 * 注册表按工具名称查找对应的执行器，再将参数委托给它执行。
 *
 * <p>这是 {@link FunctionalInterface}，支持 Lambda 表达式和方法引用。
 * 例如：
 * <pre>{@code
 *   ToolExecutor logSearch = args -> {
 *       String keyword = (String) args.get("keyword");
 *       String result = logService.search(keyword);
 *       return ToolResult.success(result);
 *   };
 * }</pre>
 */
@FunctionalInterface
public interface ToolExecutor {

    /**
     * 执行工具逻辑。
     *
     * @param arguments 工具参数，key 为参数名，value 为参数值
     * @return 执行结果，不会返回 null
     */
    ToolResult execute(Map<String, Object> arguments);
}
