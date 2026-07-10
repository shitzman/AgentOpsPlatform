package com.agentops.tools.log;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 日志提取器 — 从原始日志文本中自动提取异常堆栈及其上下文行。
 *
 * <p>支持以下提取模式：
 * <ul>
 *   <li><b>完整异常堆栈</b>：匹配 Java 异常格式（Exception/Error 类名后跟行首空白缩进的行）</li>
 *   <li><b>日志上下文</b>：提取异常发生前后 N 行的关联日志</li>
 *   <li><b>Caused by 链</b>：识别嵌套异常的链式结构</li>
 * </ul>
 */
public final class LogExtractor {

    /** 匹配 Java 异常格式（可选时间戳前缀） */
    private static final Pattern EXCEPTION_START = Pattern.compile(
            "^(?:\\d{4}[-/]\\d{2}[-/]\\d{2}[\\sT]\\d{2}:\\d{2}:\\d{2}[.,]\\d{3}\\s+)?"
                    + "([\\w.$]+(?:Exception|Error|Throwable))"
                    + "(?::\\s*(.*))?$");

    /** 缩进行（堆栈帧） */
    private static final Pattern STACK_FRAME = Pattern.compile(
            "^\\s+at\\s+([\\w.$]+)\\.([\\w<>$]+)\\(([^)]+)\\)");

    /** Caused by 链 */
    private static final Pattern CAUSED_BY = Pattern.compile(
            "^Caused by:\\s+([\\w.$]+(?:Exception|Error|Throwable))(?::\\s*(.*))?$");

    private static final int CONTEXT_BEFORE_LINES = 3;
    private static final int CONTEXT_AFTER_LINES = 3;

    /** 匹配 ERROR / WARN 级别日志行 */
    private static final Pattern ERROR_WARN_LINE = Pattern.compile(
            "(?i)(ERROR|WARN|FATAL|SEVERE|CRITICAL)\\b");

    /** 匹配带时间戳的一般日志行（用于行数统计） */
    private static final Pattern LOG_LINE = Pattern.compile(
            "^(?:\\d{4}[-/]\\d{2}[-/]\\d{2}[\\sT]\\d{2}:\\d{2}:\\d{2})");

    private LogExtractor() {}

    /**
     * 从日志文本中提取第一个完整异常堆栈。
     *
     * @param logContent 原始日志内容（可包含多行、多个异常）
     * @return 提取的堆栈文本，未找到时返回 null
     */
    public static String extractStackTrace(String logContent) {
        List<String> all = extractAllStackTraces(logContent);
        return all.isEmpty() ? null : all.get(0);
    }

    /**
     * 从日志文本中提取所有异常堆栈。
     *
     * @param logContent 原始日志内容
     * @return 提取到的所有堆栈文本列表（每个元素是一个完整的异常堆栈）
     */
    public static List<String> extractAllStackTraces(String logContent) {
        List<String> traces = new ArrayList<>();
        String[] lines = logContent.split("\\r?\\n");
        StringBuilder current = null;
        boolean inTrace = false;

        for (String line : lines) {
            Matcher exStart = EXCEPTION_START.matcher(line);
            Matcher caused = CAUSED_BY.matcher(line);
            Matcher frame = STACK_FRAME.matcher(line);

            if (exStart.find() && !inTrace) {
                // 新的异常堆栈开始
                if (current != null && !current.isEmpty()) {
                    traces.add(current.toString().trim());
                }
                current = new StringBuilder();
                current.append(line).append("\n");
                inTrace = true;
            } else if (caused.find() && inTrace) {
                // Caused by 链
                current.append(line).append("\n");
            } else if (frame.find() && inTrace) {
                // 堆栈帧行
                current.append(line).append("\n");
            } else if (inTrace && line.trim().isEmpty()) {
                // 空行 — 堆栈结束
                if (current != null && !current.isEmpty()) {
                    traces.add(current.toString().trim());
                }
                current = null;
                inTrace = false;
            } else if (inTrace && !line.trim().isEmpty()
                    && !line.startsWith("\t") && !line.startsWith("    ")
                    && !line.startsWith("... ")) {
                // 非堆栈行 — 堆栈结束
                if (current != null && !current.isEmpty()) {
                    traces.add(current.toString().trim());
                }
                current = null;
                inTrace = false;
            }
        }

        // 末尾残留
        if (current != null && !current.isEmpty()) {
            traces.add(current.toString().trim());
        }

        return traces;
    }

    /**
     * 提取异常堆栈所在位置的前后上下文行（不含异常行本身）。
     *
     * @param logContent 原始日志内容
     * @param stackTrace 要查找的堆栈文本（用于定位）
     * @return 上下文日志行文本，未找到时返回空字符串
     */
    public static String extractLogContext(String logContent, String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) return "";

        String firstLine = stackTrace.split("\\r?\\n")[0].trim();
        String[] lines = logContent.split("\\r?\\n");
        int traceStart = -1;

        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals(firstLine)
                    || lines[i].contains(firstLine)) {
                traceStart = i;
                break;
            }
        }

        if (traceStart < 0) return "";

        // 收集前后各 N 行（排除堆栈本身）
        StringBuilder ctx = new StringBuilder();
        int before = Math.max(0, traceStart - CONTEXT_BEFORE_LINES);
        for (int i = before; i < traceStart; i++) {
            if (!lines[i].trim().isEmpty()) {
                ctx.append(lines[i]).append("\n");
            }
        }

        // 标记异常位置
        ctx.append(">>> [异常发生位置] <<<\n");

        // 找到堆栈结束行
        int traceEnd = traceStart;
        for (int i = traceStart + 1; i < lines.length; i++) {
            if (STACK_FRAME.matcher(lines[i]).find()
                    || CAUSED_BY.matcher(lines[i]).find()
                    || lines[i].startsWith("\t")
                    || lines[i].startsWith("    ")
                    || lines[i].startsWith("... ")) {
                traceEnd = i;
            } else {
                break;
            }
        }

        int after = Math.min(lines.length, traceEnd + 1 + CONTEXT_AFTER_LINES);
        for (int i = traceEnd + 1; i < after; i++) {
            if (!lines[i].trim().isEmpty()) {
                ctx.append(lines[i]).append("\n");
            }
        }

        return ctx.toString().trim();
    }

    /**
     * 分析日志内容模式 — 当没有异常堆栈时，提取关键信息供 LLM 分析。
     *
     * @param logContent 原始日志内容
     * @return 日志分析摘要（错误/警告行、统计信息、代表性样本）
     */
    public static LogAnalysis analyzeLogContent(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return new LogAnalysis(0, 0, 0, 0, List.of(), List.of(), "");
        }

        String[] lines = logContent.split("\\r?\\n");
        int totalLines = lines.length;
        int errorCount = 0;
        int warnCount = 0;
        int logLineCount = 0;
        List<String> errorLines = new ArrayList<>();
        List<String> warnLines = new ArrayList<>();

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (LOG_LINE.matcher(trimmed).find()) {
                logLineCount++;
            }

            Matcher m = ERROR_WARN_LINE.matcher(trimmed);
            if (m.find()) {
                String level = m.group(1).toUpperCase();
                if (level.equals("ERROR") || level.equals("FATAL") || level.equals("SEVERE") || level.equals("CRITICAL")) {
                    errorCount++;
                    if (errorLines.size() < 20) {  // 最多保留 20 条样本
                        errorLines.add(trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed);
                    }
                } else if (level.equals("WARN")) {
                    warnCount++;
                    if (warnLines.size() < 10) {
                        warnLines.add(trimmed.length() > 300 ? trimmed.substring(0, 300) + "..." : trimmed);
                    }
                }
            }
        }

        // 构建摘要文本：取首尾各 20 行作为上下文样本
        StringBuilder sample = new StringBuilder();
        int sampleSize = Math.min(20, totalLines);
        for (int i = 0; i < sampleSize && i < totalLines; i++) {
            if (!lines[i].trim().isEmpty()) {
                sample.append(lines[i]).append("\n");
            }
        }
        if (totalLines > sampleSize * 2) {
            sample.append("... (省略 ").append(totalLines - sampleSize * 2).append(" 行) ...\n");
        }
        for (int i = Math.max(sampleSize, totalLines - sampleSize); i < totalLines; i++) {
            if (!lines[i].trim().isEmpty()) {
                sample.append(lines[i]).append("\n");
            }
        }

        return new LogAnalysis(totalLines, errorCount, warnCount, logLineCount,
                List.copyOf(errorLines), List.copyOf(warnLines), sample.toString().trim());
    }

    /**
     * 日志分析结果 — 无堆栈时的日志模式摘要。
     */
    public record LogAnalysis(
            int totalLines,
            int errorCount,
            int warnCount,
            int logLineCount,
            java.util.List<String> errorSamples,
            java.util.List<String> warnSamples,
            String contextSample) {

        /** 生成可注入 LLM Prompt 的 Markdown 文本 */
        public String toPromptText() {
            StringBuilder sb = new StringBuilder();
            sb.append("## 日志内容分析\n\n");
            sb.append("- 总行数: ").append(totalLines).append("\n");
            sb.append("- 错误行: ").append(errorCount).append("\n");
            sb.append("- 警告行: ").append(warnCount).append("\n");
            sb.append("- 标准日志行: ").append(logLineCount).append("\n\n");

            if (!errorSamples.isEmpty()) {
                sb.append("### 错误行样本\n```\n");
                for (String e : errorSamples) {
                    sb.append(e).append("\n");
                }
                sb.append("```\n\n");
            }

            if (!warnSamples.isEmpty()) {
                sb.append("### 警告行样本\n```\n");
                for (String w : warnSamples) {
                    sb.append(w).append("\n");
                }
                sb.append("```\n\n");
            }

            sb.append("### 日志样本（首尾各 20 行）\n```\n");
            sb.append(contextSample).append("\n```\n");

            return sb.toString();
        }
    }
}
