package com.agentops.tools.log;

/**
 * 日志源类型 — 定义支持的日志数据来源。
 *
 * <p>每种类型对应一个 {@link LogProvider} 实现，负责将配置参数
 * 转换为实际的日志搜索操作。
 *
 * <p>扩展新的日志源类型：
 * <ol>
 *   <li>在此枚举中添加新值（如 {@code LOKI}、{@code SLS}）</li>
 *   <li>创建对应的 {@link LogProvider} 实现</li>
 *   <li>在 {@code AgentOpsConfig} 中注册</li>
 * </ol>
 */
public enum LogSourceType {

    /** 直接在 UI 中粘贴的原始日志文本（最简单，适合快速测试） */
    TEXT_INPUT,

    /** 服务器本地日志文件路径（适合开发环境） */
    FILE_PATH,

    /** Elasticsearch 日志存储（适合生产环境） */
    ELASTICSEARCH
}
