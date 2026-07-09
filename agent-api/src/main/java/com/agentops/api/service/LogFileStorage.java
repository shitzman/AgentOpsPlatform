package com.agentops.api.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 日志文件存储 — 将上传的日志文件保存到本地文件系统。
 *
 * <p>文件保存到 {@code agentops.upload.dir} 配置的目录（默认 {@code ./data/uploads}），
 * 文件名为 {@code {UUID}.log} 以避免冲突和路径遍历。
 *
 * <p>删除日志源时调用 {@link #delete(String)} 清理对应的上传文件。
 */
@Service
public class LogFileStorage {

    private final Path uploadDir;

    public LogFileStorage(@Value("${agentops.upload.dir:./data/uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * 保存上传的日志文件。
     *
     * @param file 上传的文件
     * @return 保存后的文件绝对路径
     * @throws IOException              文件写入失败
     * @throws IllegalArgumentException 文件为空
     */
    public String save(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        Files.createDirectories(uploadDir);

        String filename = UUID.randomUUID() + ".log";
        Path target = uploadDir.resolve(filename).normalize();

        // 防止路径遍历：确保目标路径在上传目录内
        if (!target.startsWith(uploadDir)) {
            throw new SecurityException("非法的文件路径");
        }

        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }

        return target.toString();
    }

    /**
     * 删除上传的日志文件（文件不存在时静默忽略）。
     *
     * @param filePath 文件绝对路径
     */
    public void delete(String filePath) {
        if (filePath == null || filePath.isBlank()) return;

        Path path = Paths.get(filePath);
        if (!path.toAbsolutePath().normalize().startsWith(uploadDir)) {
            // 只清理上传目录内的文件，避免误删
            return;
        }

        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 删除失败不阻断主流程
        }
    }

    /** 获取上传目录路径（供测试使用） */
    Path getUploadDir() {
        return uploadDir;
    }
}
