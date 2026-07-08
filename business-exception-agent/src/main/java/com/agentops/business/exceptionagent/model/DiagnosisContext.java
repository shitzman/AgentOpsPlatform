package com.agentops.business.exceptionagent.model;

import com.agentops.tools.EnvironmentInfo;
import com.agentops.tools.GitContext;

/**
 * 诊断上下文 — 聚合多源数据，作为 LLM 诊断的完整输入。
 *
 * <p>将以下数据源聚合成一个对象：
 * <ul>
 *   <li>异常堆栈（原始文本 + 结构化解析）</li>
 *   <li>日志上下文（异常发生前后的关联日志行）</li>
 *   <li>Git 上下文（当前分支、最近提交、Blame 信息）</li>
 *   <li>运行环境（Java 版本、OS、JVM 参数、内存）</li>
 *   <li>项目信息（名称、描述）</li>
 * </ul>
 */
public record DiagnosisContext(
        String projectId,
        String projectName,
        String projectDescription,
        String rawStackTrace,
        StackTrace parsedStackTrace,
        String logContext,
        GitContext gitContext,
        EnvironmentInfo environment) {

    /**
     * 生成用于注入 LLM System Prompt 的完整上下文文本。
     *
     * <p>格式化为 Markdown，方便 LLM 理解多源信息之间的关联。
     */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();

        // 项目信息
        sb.append("## 项目信息\n");
        sb.append("- 项目名称: **").append(projectName).append("**\n");
        if (projectDescription != null && !projectDescription.isBlank()) {
            sb.append("- 项目描述: ").append(projectDescription).append("\n");
        }
        sb.append("\n");

        // 运行环境
        if (environment != null) {
            sb.append(environment.toPromptText()).append("\n");
        }

        // Git 上下文
        if (gitContext != null) {
            sb.append(gitContext.toPromptText()).append("\n");
        }

        // 日志上下文
        if (logContext != null && !logContext.isBlank()) {
            sb.append("## 关联日志上下文\n");
            sb.append("```\n").append(logContext).append("\n```\n");
        }

        return sb.toString();
    }
}
