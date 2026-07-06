package com.agentops.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * AgentOps Platform 启动入口。
 *
 * <p>基于 Spring Boot 3.x，自动扫描 {@code com.agentops} 包下的所有组件。
 *
 * <p>启动方式：
 * <pre>
 *   mvn spring-boot:run -pl agent-api
 * </pre>
 * 或设置环境变量后启动：
 * <pre>
 *   $env:AGENTOPS_LLM_API_KEY="sk-xxx"
 *   mvn spring-boot:run -pl agent-api
 * </pre>
 */
@SpringBootApplication(scanBasePackages = "com.agentops")
public class AgentOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentOpsApplication.class, args);
    }
}
