package com.agentops.api.config;

import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.memory.InMemoryMemoryStore;
import com.agentops.memory.MemoryStore;
import com.agentops.prompts.InMemoryPromptRegistry;
import com.agentops.prompts.PromptRegistry;
import com.agentops.prompts.PromptTemplate;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.OpenAIModelClient;
import com.agentops.tools.*;
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
 * AgentOps Platform Spring 配置 — 装配所有核心 Bean 并注册工具。
 */
@Configuration
public class AgentOpsConfig {

    /** Git 仓库路径（默认使用当前项目路径） */
    @Value("${agentops.git.repo-path:${user.dir}}")
    private String gitRepoPath;

    // ---- 基础设施 Bean ----

    @Bean
    ToolRegistry toolRegistry() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();

        // 注册 Git 工具（log / blame / show）
        GitTool git = new GitTool(gitRepoPath);
        registry.register(GitTool.logDefinition(), git.logExecutor());
        registry.register(GitTool.blameDefinition(), git.blameExecutor());
        registry.register(GitTool.showDefinition(), git.showExecutor());

        // 注册日志搜索工具（V0.4 模拟实现）
        LogTool logTool = new LogTool();
        registry.register(LogTool.definition(), logTool.executor());

        return registry;
    }

    @Bean
    MemoryStore memoryStore() {
        return new InMemoryMemoryStore();
    }

    @Bean
    WorkflowEngine workflowEngine() {
        return new SequentialWorkflowEngine();
    }

    @Bean
    PromptRegistry promptRegistry(ResourcePatternResolver resolver) throws IOException {
        InMemoryPromptRegistry registry = new InMemoryPromptRegistry();
        Resource[] resources = resolver.getResources("classpath*:prompts/*.txt");
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null) continue;
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
