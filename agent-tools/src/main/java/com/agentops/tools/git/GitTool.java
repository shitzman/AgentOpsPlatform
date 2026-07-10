package com.agentops.tools.git;

import com.agentops.tools.core.ToolDefinition;
import com.agentops.tools.core.ToolExecutor;
import com.agentops.tools.core.ToolResult;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Git 工具 — 通过本地 git 命令行提供代码仓库分析能力。
 *
 * <p>支持三种操作，每种注册为一个独立的 Tool：
 * <ul>
 *   <li><b>git-log</b>：查看文件的最近提交记录</li>
 *   <li><b>git-blame</b>：查看指定行号的 blame 信息</li>
 *   <li><b>git-show</b>：查看指定 commit 的详情</li>
 * </ul>
 *
 * <p>使用 ProcessBuilder 调用本地 git 命令，需要运行环境已安装 git
 * 且工作目录为 git 仓库。
 */
public class GitTool {

    private final String repoPath;

    public GitTool(String repoPath) {
        this.repoPath = repoPath;
    }

    // ---- Tool 定义 ----

    /** git-log 工具定义 */
    public static ToolDefinition logDefinition() {
        return new ToolDefinition("git-log", "查看指定文件最近的 Git 提交记录",
                Map.of("type", "object",
                        "properties", Map.of(
                                "filePath", Map.of("type", "string", "description", "文件路径（相对于仓库根目录）"),
                                "maxCount", Map.of("type", "integer", "description", "最多返回的提交数，默认 5")
                        ),
                        "required", List.of("filePath")));
    }

    /** git-blame 工具定义 */
    public static ToolDefinition blameDefinition() {
        return new ToolDefinition("git-blame", "查看指定文件某行的 Git blame 信息（谁最后修改了这行代码）",
                Map.of("type", "object",
                        "properties", Map.of(
                                "filePath", Map.of("type", "string", "description", "文件路径（相对于仓库根目录）"),
                                "lineNumber", Map.of("type", "integer", "description", "行号")
                        ),
                        "required", List.of("filePath", "lineNumber")));
    }

    /** git-show 工具定义 */
    public static ToolDefinition showDefinition() {
        return new ToolDefinition("git-show", "查看指定 commit 的详细信息（变更内容）",
                Map.of("type", "object",
                        "properties", Map.of(
                                "commitHash", Map.of("type", "string", "description", "commit 哈希值")
                        ),
                        "required", List.of("commitHash")));
    }

    // ---- 执行器 ----

    /** git-log 执行器 */
    public ToolExecutor logExecutor() {
        return args -> {
            String filePath = (String) args.get("filePath");
            int maxCount = args.containsKey("maxCount")
                    ? ((Number) args.get("maxCount")).intValue() : 5;

            try {
                List<String> output = runGit("log",
                        "--oneline", "--max-count=" + maxCount, "--", filePath);
                return ToolResult.success(String.join("\n", output));
            } catch (Exception e) {
                return ToolResult.failure("git log 失败: " + e.getMessage());
            }
        };
    }

    /** git-blame 执行器 */
    public ToolExecutor blameExecutor() {
        return args -> {
            String filePath = (String) args.get("filePath");
            int lineNumber = ((Number) args.get("lineNumber")).intValue();

            try {
                List<String> output = runGit("blame",
                        "-L", lineNumber + "," + lineNumber, "--", filePath);
                return ToolResult.success(String.join("\n", output));
            } catch (Exception e) {
                return ToolResult.failure("git blame 失败: " + e.getMessage());
            }
        };
    }

    /** git-show 执行器 */
    public ToolExecutor showExecutor() {
        return args -> {
            String commitHash = (String) args.get("commitHash");

            try {
                List<String> output = runGit("show",
                        "--stat", "--no-patch", commitHash);
                return ToolResult.success(String.join("\n", output));
            } catch (Exception e) {
                return ToolResult.failure("git show 失败: " + e.getMessage());
            }
        };
    }

    // ---- 底层 git 命令调用 ----

    private List<String> runGit(String... args) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(new File(repoPath));
        pb.redirectErrorStream(true);

        Process process = pb.start();
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }

        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return List.of("命令超时");
        }

        if (process.exitValue() != 0) {
            return List.of("git 命令退出码: " + process.exitValue());
        }

        return lines;
    }
}
