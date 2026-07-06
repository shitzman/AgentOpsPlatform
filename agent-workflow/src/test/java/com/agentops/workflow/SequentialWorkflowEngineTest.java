package com.agentops.workflow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SequentialWorkflowEngine + SimpleWorkflowContext 单元测试。
 */
class SequentialWorkflowEngineTest {

    @Test
    @DisplayName("顺序执行多个步骤，上下文正确传递")
    void executeMultipleSteps() {
        SequentialWorkflowEngine engine = new SequentialWorkflowEngine();
        WorkflowStep step1 = ctx -> { ctx.put("a", 1); return ctx; };
        WorkflowStep step2 = ctx -> { ctx.put("b", (Integer) ctx.get("a") + 1); return ctx; };
        WorkflowStep step3 = ctx -> { ctx.put("c", (Integer) ctx.get("b") * 2); return ctx; };

        WorkflowDefinition def = new WorkflowDefinition("test", List.of(step1, step2, step3));
        WorkflowContext result = engine.execute(def, new SimpleWorkflowContext());

        assertEquals(1, result.get("a"));
        assertEquals(2, result.get("b"));
        assertEquals(4, result.get("c"));
    }

    @Test
    @DisplayName("通过名称注册并执行 Workflow")
    void executeByRegisteredName() {
        SequentialWorkflowEngine engine = new SequentialWorkflowEngine();
        WorkflowStep step = ctx -> { ctx.put("done", true); return ctx; };
        engine.register(new WorkflowDefinition("my-workflow", List.of(step)));

        WorkflowContext result = engine.execute("my-workflow", new SimpleWorkflowContext());
        assertEquals(true, result.get("done"));
    }

    @Test
    @DisplayName("执行未注册的 Workflow 抛出异常")
    void executeUnregisteredThrows() {
        SequentialWorkflowEngine engine = new SequentialWorkflowEngine();
        assertThrows(IllegalArgumentException.class,
                () -> engine.execute("not-exist", new SimpleWorkflowContext()));
    }

    @Test
    @DisplayName("步骤抛出异常时终止执行")
    void stepFailureStopsExecution() {
        SequentialWorkflowEngine engine = new SequentialWorkflowEngine();
        WorkflowStep failStep = ctx -> { throw new RuntimeException("模拟失败"); };
        WorkflowStep shouldNotRun = ctx -> { ctx.put("ran", true); return ctx; };

        WorkflowDefinition def = new WorkflowDefinition("test", List.of(failStep, shouldNotRun));
        assertThrows(WorkflowStepException.class,
                () -> engine.execute(def, new SimpleWorkflowContext()));
    }

    @Test
    @DisplayName("空上下文从步骤间正常传递")
    void initialContextNullDefaults() {
        SequentialWorkflowEngine engine = new SequentialWorkflowEngine();
        WorkflowStep step = ctx -> { ctx.put("key", "val"); return ctx; };
        WorkflowDefinition def = new WorkflowDefinition("test", List.of(step));

        // 不传 initialContext
        WorkflowContext result = engine.execute(def, null);
        assertEquals("val", result.get("key"));
    }

    @Test
    @DisplayName("SimpleWorkflowContext 的 toMap 返回快照")
    void contextToMapSnapshot() {
        SimpleWorkflowContext ctx = new SimpleWorkflowContext();
        ctx.put("x", 1);
        Map<String, Object> snapshot = ctx.toMap();
        ctx.put("y", 2);

        // 快照不应受后续修改影响
        assertEquals(1, snapshot.size());
    }
}
