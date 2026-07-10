# ADR-001: 引入 JavaParser 用于代码静态分析工具

- **Status**: Accepted (2026-07-09)
- **Decider**: AgentOps Platform 维护者
- **Supersedes**: 无

## Context

`route-lookup` 工具需要扫描 Spring Web 注解(`@RequestMapping` / `@GetMapping` / `@PostMapping` 等)构建路由表,实现「接口路径 → Controller 方法源码位置」的反查。该注解组合存在以下复杂场景:

- class-level `@RequestMapping` 定义前缀,method-level 注解定义后缀,需要组合
- 注解的 `value` / `path` 属性可互换
- 路径可写成数组形式 `@GetMapping({"/a", "/b"})`
- 注解参数可跨多行,正则匹配不可靠

LLM 在诊断时,日志和访问日志中最常见的线索是接口路径(如 `POST /api/orders`),但当前 `read-source` 工具要求 LLM 已知 `filePath`,缺少从路径到源码的反向定位能力。

## Decision

引入 `com.github.javaparser:javaparser-core` 作为 `agent-tools` 模块的代码静态分析依赖,用于 AST 解析 Java 源码。

**范围限定**:
- 仅引入 `javaparser-core`,**不引入** `javaparser-symbol-solver-core`(后者依赖 guava,体积较大且超出当前需求)
- 仅用于 AST 解析(注解扫描、方法定位),不做符号引用解析
- 仅在 `agent-tools` 模块使用,不扩散到其他模块

## Consequences

**正面**:
- `route-lookup` 可靠解析 Spring Web 注解组合,正确构建路由表
- 为后续 `search-symbol`、`find-callers` 等代码分析工具奠定基础
- JavaParser API 成熟稳定,社区活跃

**负面**:
- 增加 ~700KB 依赖体积
- 首次解析全仓库 `.java` 文件需 1-3 秒(已通过实例缓存规避,仅构建一次)
- 多了一个外部依赖需要版本管理

**风险缓解**:
- 依赖版本在根 pom.xml 的 `dependencyManagement` 集中管理
- 缓存通过 `volatile` 字段 + 双检锁实现,线程安全
- 不监听文件变更(v1 限制),进程重启即刷新缓存

## Alternatives Considered

1. **纯正则扫描注解** — 不可靠。注解可跨行、参数形式多变(`value=` / `path=` / 直接字符串 / 数组),正则无法稳定解析。已排除。

2. **ripggrep 子进程** — 无法理解注解语义,只能匹配字面量。无法区分 class-level 和 method-level 注解,无法组合 prefix。已排除。

3. **Spring Boot 内置 `RequestMappingHandlerMapping`** — 需要启动目标应用,本平台诊断的是外部项目的异常,无法启动其 Spring 上下文。不适合静态分析场景。已排除。

4. **引入完整 JavaParser(含 symbol-solver)** — symbol-solver 依赖 guava 且需要 classpath 解析,超出了「注解扫描 + 方法定位」的需求。引入不必要的传递依赖违反 AGENTS.md「Unplanned dependency additions」原则。已排除。

## References

- JavaParser: https://javaparser.org/
- 计划文件: `.trae/documents/add-route-lookup-and-search-code-tools.md`
