package com.agentops.workflow;

import java.util.Map;

/**
 * Workflow 共享上下文 — 在工作流步骤之间传递数据的可变键值存储。
 *
 * <p>每个步骤可以从上下文中读取上游步骤写入的数据，也可以写入新数据供下游步骤使用。
 * 上下文是线程不安全的有状态对象，仅在单次 Workflow 执行期间存活。
 *
 * <p>典型数据包括：
 * <ul>
 *   <li>原始输入（如异常堆栈文本）</li>
 *   <li>中间产物（如日志查询结果、Git blame 信息）</li>
 *   <li>最终产物（如诊断报告）</li>
 * </ul>
 */
public interface WorkflowContext {

    /**
     * 向上下文中写入一个键值对。
     *
     * @param key   键名，不能为空
     * @param value 值，可以为 null（视为删除该键）
     */
    void put(String key, Object value);

    /**
     * 从上下文中读取指定键的值。
     *
     * @param key 键名
     * @return 对应的值，不存在时返回 {@code null}
     */
    Object get(String key);

    /**
     * 读取指定键的值，并尝试转换为目标类型。
     *
     * @param key   键名
     * @param type  目标类型
     * @param <T>   类型参数
     * @return 转换后的值
     * @throws ClassCastException 如果值存在但类型不匹配
     */
    @SuppressWarnings("unchecked")
    default <T> T get(String key, Class<T> type) {
        Object value = get(key);
        if (value == null) {
            return null;
        }
        return (T) value;
    }

    /**
     * 检查上下文是否包含指定键。
     *
     * @param key 键名
     * @return 存在返回 {@code true}
     */
    boolean containsKey(String key);

    /**
     * 返回上下文中的所有键值对（快照）。
     *
     * @return 不可修改的 Map
     */
    Map<String, Object> toMap();
}
