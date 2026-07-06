package com.agentops.memory;

import java.util.List;
import java.util.Optional;

/**
 * Memory 存储层接口 — 定义 Memory 条目的 CRUD 和搜索操作。
 *
 * <p>这是平台级抽象，不与具体数据库绑定。V0.1 的实现可以使用
 * PostgreSQL（通过 JDBC 或 Spring Data JDBC），后续可扩展支持
 * 向量数据库（如 pgvector）做语义搜索。
 *
 * <p>典型使用场景：
 * <ul>
 *   <li><b>对话记忆</b>：保存 Agent 与用户的每轮对话，type="conversation"</li>
 *   <li><b>诊断记录</b>：保存每次异常诊断的结果，type="diagnosis"</li>
 *   <li><b>工具调用日志</b>：保存每次工具调用的输入输出，type="tool_result"</li>
 * </ul>
 *
 * <p>约定：
 * <ul>
 *   <li>{@link #save(MemoryEntry)} 如果 entry.id 已存在则更新，否则插入</li>
 *   <li>搜索结果按创建时间倒序排列</li>
 *   <li>所有方法不应返回 null，应返回空集合或 {@link Optional#empty()}</li>
 * </ul>
 */
public interface MemoryStore {

    /**
     * 保存一条 Memory 记录（存在则更新，不存在则插入）。
     *
     * @param entry 要保存的 Memory 条目
     * @return 保存后的条目（可能包含存储层生成的 id）
     */
    MemoryEntry save(MemoryEntry entry);

    /**
     * 根据 ID 查找 Memory 记录。
     *
     * @param id 记录 ID
     * @return 找到的记录，不存在时返回 {@link Optional#empty()}
     */
    Optional<MemoryEntry> findById(String id);

    /**
     * 按类型查找 Memory 记录。
     *
     * @param type  记忆类型（如 "diagnosis", "conversation"）
     * @param limit 最大返回条数
     * @return 按创建时间倒序排列的记录列表（最多 limit 条）
     */
    List<MemoryEntry> findByType(String type, int limit);

    /**
     * 全文搜索 Memory 记录的内容。
     *
     * <p>V0.1 使用 SQL {@code LIKE} 或 PostgreSQL {@code tsvector} 实现，
     * 后续可升级为向量语义搜索。
     *
     * @param keyword 搜索关键词
     * @param limit   最大返回条数
     * @return 相关记录列表，按创建时间倒序排列
     */
    List<MemoryEntry> search(String keyword, int limit);

    /**
     * 根据 ID 删除一条 Memory 记录。
     *
     * @param id 记录 ID
     * @return 是否成功删除（记录不存在返回 {@code false}）
     */
    boolean deleteById(String id);
}
