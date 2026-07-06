package com.agentops.workflow;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Workflow 上下文的简易实现 — 基于 {@link LinkedHashMap} 的内存键值存储。
 *
 * <p>线程不安全，仅在单次 Workflow 执行期间使用。
 */
public class SimpleWorkflowContext implements WorkflowContext {

    private final Map<String, Object> data = new LinkedHashMap<>();

    @Override
    public void put(String key, Object value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("上下文的 key 不能为空");
        }
        data.put(key, value);
    }

    @Override
    public Object get(String key) {
        return data.get(key);
    }

    @Override
    public boolean containsKey(String key) {
        return data.containsKey(key);
    }

    @Override
    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
