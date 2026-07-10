package com.agentops.tools.git;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Git 上下文提供器 — 自动检测 Git 仓库状态并生成诊断用的 Git 上下文。
 *
 * <p>基于本地 Git 仓库路径，通过 {@code git} 命令收集：
 * <ul>
 *   <li>当前分支名</li>
 *   <li>远程仓库地址</li>
 *   <li>最近 N 次提交记录</li>
 *   <li>指定文件行的 git blame 信息（谁最后修改了相关代码行）</li>
 *   <li>是否有未提交的变更</li>
 * </ul>
 *
 * <p>blameWithFiles 接受 {@code List<FileLine>} 参数，不依赖任何业务模块，
 * 调用方可将堆栈帧转换为 {@code FileLine} 后传入。
 */
public class GitContextProvider {

    private static final int RECENT_COMMIT_COUNT = 5;
    private static final int COMMAND_TIMEOUT_SECONDS = 10;

    private final String repoPath;

    public GitContextProvider(String repoPath) {
        this.repoPath = repoPath;
    }

    /**
     * Blame 目标 — 文件路径 + 行号对。
     *
     * <p>避免直接依赖 business-exception-agent 模块的 StackTraceFrame 类型。
     */
    public record FileLine(String fileName, int lineNumber) {
        public static FileLine of(String fileName, int lineNumber) {
            return new FileLine(fileName, lineNumber);
        }
    }

    /**
     * 收集完整的 Git 上下文，包括 blame 信息。
     *
     * @param blameTargets 要执行 git blame 的文件行列表（可为空）
     * @return Git 上下文快照，执行失败时返回空上下文
     */
    public GitContext collect(List<FileLine> blameTargets) {
        try {
            String branch = exec("git rev-parse --abbrev-ref HEAD");
            String remoteUrl = exec("git config --get remote.origin.url");
            boolean dirty = !exec("git status --porcelain").isEmpty();

            List<GitContext.CommitInfo> commits = collectRecentCommits();
            List<GitContext.BlameInfo> blames = collectBlameInfo(blameTargets);

            return new GitContext(repoPath, branch.trim(), remoteUrl.trim(),
                    dirty, commits, blames);
        } catch (Exception e) {
            // Git 不可用时返回空上下文，不阻断诊断流程
            return new GitContext(repoPath, "unknown", "", false,
                    List.of(), List.of());
        }
    }

    /** 收集最近 N 次提交 */
    private List<GitContext.CommitInfo> collectRecentCommits() {
        List<GitContext.CommitInfo> commits = new ArrayList<>();
        try {
            String output = exec("git log --oneline -" + RECENT_COMMIT_COUNT
                    + " --format=%H|%an|%ai|%s");
            for (String line : output.split("\n")) {
                String[] parts = line.split("\\|", 4);
                if (parts.length >= 4) {
                    commits.add(new GitContext.CommitInfo(
                            parts[0].substring(0, Math.min(8, parts[0].length())),
                            parts[1], parts[2], parts[3]));
                }
            }
        } catch (Exception ignored) {}
        return commits;
    }

    /** 对每个文件行执行 git blame */
    private List<GitContext.BlameInfo> collectBlameInfo(List<FileLine> targets) {
        List<GitContext.BlameInfo> blames = new ArrayList<>();
        if (targets == null) return blames;

        for (FileLine fl : targets) {
            if (fl.fileName() == null || fl.lineNumber() <= 0) continue;
            try {
                String cmd = String.format("git blame -L %d,%d %s --porcelain",
                        fl.lineNumber(), fl.lineNumber(), fl.fileName());
                String output = exec(cmd);
                blames.add(parseBlameOutput(output, fl.fileName(), fl.lineNumber()));
            } catch (Exception ignored) {}
        }
        return blames;
    }

    /** 解析 git blame --porcelain 输出 */
    private GitContext.BlameInfo parseBlameOutput(String output,
                                                   String fileName, int lineNumber) {
        String author = "unknown";
        String timestamp = "";
        String commitHash = "";
        String summary = "";

        for (String line : output.split("\n")) {
            if (line.startsWith("author ")) {
                author = line.substring(7);
            } else if (line.startsWith("author-time ")) {
                timestamp = line.substring(12);
            } else if (line.startsWith("summary ")) {
                summary = line.substring(8);
            } else if (line.matches("^[0-9a-f]{40}.*")) {
                commitHash = line.split(" ")[0].substring(0, 8);
            }
        }

        return new GitContext.BlameInfo(
                fileName, lineNumber, author, timestamp, commitHash, summary);
    }

    private String exec(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("cmd", "/c", command);
            pb.directory(java.nio.file.Path.of(repoPath).toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append("\n");
                }
            }
            process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return out.toString();
        } catch (Exception e) {
            throw new RuntimeException("git 命令执行失败: " + command, e);
        }
    }
}
