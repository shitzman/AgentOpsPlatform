package com.agentops.api.config;

import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.memory.InMemoryMemoryStore;
import com.agentops.memory.MemoryStore;
import com.agentops.prompts.InMemoryPromptRegistry;
import com.agentops.prompts.PromptRegistry;
import com.agentops.prompts.PromptTemplate;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.OpenAIModelClient;
import com.agentops.tools.InMemoryToolRegistry;
import com.agentops.tools.ToolRegistry;
import com.agentops.workflow.SequentialWorkflowEngine;
import com.agentops.workflow.WorkflowEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AgentOps Platform Spring 配置 — 装配所有核心 Bean。
 *
 * <p>按依赖顺序装配：
 * <ol>
 *   <li>基础设施：ToolRegistry / PromptRegistry / MemoryStore / WorkflowEngine</li>
 *   <li>模型调用：ModelClient</li>
 *   <li>领域 Agent：BusinessExceptionAgent</li>
 * </ol>
 */
@Configuration
public class AgentOpsConfig {

    // ---- 基础设施 Bean ----

    @Bean
    ToolRegistry toolRegistry() {
        return new InMemoryToolRegistry();
    }

    @Bean
    MemoryStore memoryStore() {
        return new InMemoryMemoryStore();
    }

    @Bean
    WorkflowEngine workflowEngine() {
        return new SequentialWorkflowEngine();
    }

    /**
     * PromptRegistry，启动时自动从 classpath:prompts/ 加载所有 .txt 文件。
     */
    @Bean
    PromptRegistry promptRegistry(ResourcePatternResolver resolver) throws IOException {
        InMemoryPromptRegistry registry = new InMemoryPromptRegistry();

        // 扫描所有模块的 prompts/ 目录
        Resource[] resources = resolver.getResources("classpath*:prompts/*.txt");
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;

            // 文件名去除 .txt 后缀作为模板名称
            String name = filename.replace(".txt", "");
            String content = resource.getContentAsString(StandardCharsets.UTF_8);

            registry.register(new PromptTemplate(name, content));
        }
        return registry;
    }

    // ---- 模型调用 Bean ----

    @Bean
    ModelClient modelClient(
            @Value("${agentops.llm.base-url:https://api.deepseek.com/v1}") String baseUrl,
            @Value("${agentops.llm.api-key:}") String apiKey,
            @Value("${agentops.llm.model:deepseek-chat}") String model) {
        return new OpenAIModelClient(baseUrl, apiKey, model);
    }

    // ---- 领域 Agent Bean ----

    @Bean
    BusinessExceptionAgent businessExceptionAgent(WorkflowEngine workflowEngine) {
        return new BusinessExceptionAgent(workflowEngine);
    }
}
