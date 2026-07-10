package com.agentops.tools.git;

import java.util.List;

/**
 * Git 上下文 — 目标项目的 Git 仓库状态快照。
 *
 * <p>用于诊断时关联代码变更历史：某次异常是否与最近某次提交有关？
 */
public record GitContext(
        String repoPath,
        String currentBranch,
        String remoteUrl,
        boolean hasUncommittedChanges,
        List<CommitInfo> recentCommits,
        List<BlameInfo> blameHints) {

    /** 生成 Git 上下文的摘要文本（注入诊断 Prompt 用） */
    public String toPromptText() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Git 仓库上下文\n");
        sb.append("- 仓库路径: ").append(repoPath).append("\n");
        sb.append("- 当前分支: ").append(currentBranch).append("\n");
        if (remoteUrl != null && !remoteUrl.isBlank()) {
            sb.append("- 远程地址: ").append(remoteUrl).append("\n");
        }
        sb.append("- 未提交变更: ").append(hasUncommittedChanges ? "是" : "否").append("\n");

        if (!recentCommits.isEmpty()) {
            sb.append("\n### 最近提交\n");
            for (CommitInfo c : recentCommits) {
                sb.append("- `").append(c.commitHash()).append("` ")
                        .append(c.author()).append(": ")
                        .append(c.message()).append("\n");
            }
        }

        if (!blameHints.isEmpty()) {
            sb.append("\n### 相关代码修改记录 (Git Blame)\n");
            for (BlameInfo b : blameHints) {
                sb.append("- `").append(b.fileName()).append(":").append(b.lineNumber())
                        .append("` 最后修改: ").append(b.author())
                        .append(" (").append(b.timestamp()).append(") — ")
                        .append(b.commitMessage()).append("\n");
            }
        }

        return sb.toString();
    }

    /** 单条提交信息 */
    public record CommitInfo(
            String commitHash,
            String author,
            String timestamp,
            String message) {}

    /** 单行 Blame 信息 */
    public record BlameInfo(
            String fileName,
            int lineNumber,
            String author,
            String timestamp,
            String commitHash,
            String commitMessage) {}
}
