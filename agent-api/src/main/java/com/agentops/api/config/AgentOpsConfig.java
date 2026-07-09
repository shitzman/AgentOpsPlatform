package com.agentops.api.config;

import com.agentops.business.exceptionagent.BusinessExceptionAgent;
import com.agentops.memory.MemoryStore;
import com.agentops.prompts.InMemoryPromptRegistry;
import com.agentops.prompts.PromptRegistry;
import com.agentops.prompts.PromptTemplate;
import com.agentops.repository.MySqlMemoryStore;
import com.agentops.repository.MySqlProjectManager;
import com.agentops.repository.mapper.LogSourceMapper;
import com.agentops.repository.mapper.MemoryEntryMapper;
import com.agentops.repository.mapper.ProjectMapper;
import com.agentops.runtime.model.ModelClient;
import com.agentops.runtime.OpenAIModelClient;
import com.agentops.tools.*;
import com.agentops.workflow.SequentialWorkflowEngine;
import com.agentops.workflow.WorkflowEngine;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * AgentOps Platform Spring 配置 — 装配所有核心 Bean 并注册工具。
 *
 * <p>V1 变更：MemoryStore 默认使用 MySqlMemoryStore（MySQL 持久化），
 * ProjectManager 替换为 MySqlProjectManager（基于 MyBatis-Plus Mapper）。
 */
@Configuration
@MapperScan("com.agentops.repository.mapper")
public class AgentOpsConfig {

    /** Git 仓库路径（默认使用当前项目路径） */
    @Value("${agentops.git.repo-path:${user.dir}}")
    private String gitRepoPath;

    // ---- 基础设施 Bean ----

    @Bean
    ToolRegistry toolRegistry() {
        InMemoryToolRegistry registry = new InMemoryToolRegistry();

        // 源码阅读工具（诊断核心工具 — 读取源码理解代码逻辑）
        SourceCodeTool sourceCodeTool = new SourceCodeTool(gitRepoPath);
        registry.register(SourceCodeTool.definition(), sourceCodeTool.executor());

        // Git 工具（log / blame / show — 辅助定位最近变更）
        GitTool git = new GitTool(gitRepoPath);
        registry.register(GitTool.logDefinition(), git.logExecutor());
        registry.register(GitTool.blameDefinition(), git.blameExecutor());
        registry.register(GitTool.showDefinition(), git.showExecutor());

        // 日志搜索工具（V0.4 默认实现，V1 支持项目级 LogProvider 替换）
        LogTool logTool = new LogTool();
        registry.register(LogTool.definition(), logTool.executor());

        return registry;
    }

    /** MemoryStore 的 MySQL 实现（V1 替换 InMemoryMemoryStore） */
    @Bean
    MemoryStore memoryStore(MemoryEntryMapper memoryEntryMapper) {
        return new MySqlMemoryStore(memoryEntryMapper);
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

    // ---- 日志提供者注册表 (V0.5) ----

    @Bean
    LogProviderRegistry logProviderRegistry() {
        InMemoryLogProviderRegistry registry = new InMemoryLogProviderRegistry();
        registry.register(new TextInputLogProvider());
        registry.register(new FileLogProvider());
        registry.register(new ElasticsearchLogProvider());
        return registry;
    }

    // ---- 项目管理服务（V1：MySQL 持久化版） ----

    @Bean
    MySqlProjectManager projectManager(ProjectMapper projectMapper,
                                       LogSourceMapper logSourceMapper,
                                       ToolRegistry toolRegistry,
                                       LogProviderRegistry logProviderRegistry) {
        return new MySqlProjectManager(projectMapper, logSourceMapper,
                toolRegistry, logProviderRegistry);
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
