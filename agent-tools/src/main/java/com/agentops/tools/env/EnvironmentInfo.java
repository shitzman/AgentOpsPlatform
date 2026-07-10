package com.agentops.tools.env;

/**
 * 运行环境信息 — 描述目标项目的 JVM、OS 和硬件环境。
 *
 * <p>用于诊断时提供上下文：特定的异常可能与 JDK 版本、JVM 参数或系统资源相关。
 */
public record EnvironmentInfo(
        String javaVersion,
        String javaVendor,
        String javaHome,
        String osName,
        String osArch,
        String osVersion,
        String jvmArgs,
        long maxMemoryMB,
        long totalMemoryMB,
        long freeMemoryMB,
        int availableProcessors) {

    /** 生成当前运行环境的摘要文本（注入诊断 Prompt 用） */
    public String toPromptText() {
        return """
                ## 运行环境
                - Java: %s (%s, %s)
                - OS: %s %s (%s)
                - JVM 参数: %s
                - 内存: 最大 %d MB / 已分配 %d MB / 空闲 %d MB
                - CPU 核心数: %d
                """.formatted(
                javaVersion, javaVendor, javaHome,
                osName, osVersion, osArch,
                jvmArgs != null && !jvmArgs.isBlank() ? jvmArgs : "(默认)",
                maxMemoryMB, totalMemoryMB, freeMemoryMB,
                availableProcessors);
    }
}
