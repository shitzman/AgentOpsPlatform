package com.agentops.tools.env;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;

/**
 * 环境信息采集器 — 收集当前 JVM 和操作系统的运行环境数据。
 *
 * <p>通过 Java ManagementFactory 获取 JVM 参数、内存、CPU 和 OS 信息，
 * 生成 {@link EnvironmentInfo} 结构，注入诊断上下文帮助 LLM 判断环境相关因素。
 *
 * <p>使用示例：
 * <pre>{@code
 *   EnvironmentInfo env = EnvironmentCollector.collect();
 *   System.out.println(env.toPromptText());
 * }</pre>
 */
public final class EnvironmentCollector {

    private EnvironmentCollector() {}

    /** 采集当前 JVM 的完整运行环境信息 */
    public static EnvironmentInfo collect() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();

        Runtime rt = Runtime.getRuntime();
        long maxMemoryMB = rt.maxMemory() / (1024 * 1024);
        long totalMemoryMB = rt.totalMemory() / (1024 * 1024);
        long freeMemoryMB = rt.freeMemory() / (1024 * 1024);

        String jvmArgs = String.join(" ", runtime.getInputArguments());

        return new EnvironmentInfo(
                System.getProperty("java.version"),
                System.getProperty("java.vendor"),
                System.getProperty("java.home"),
                os.getName(),
                os.getArch(),
                os.getVersion(),
                jvmArgs.isEmpty() ? null : jvmArgs,
                maxMemoryMB,
                totalMemoryMB,
                freeMemoryMB,
                rt.availableProcessors()
        );
    }
}
