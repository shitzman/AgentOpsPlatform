package com.agentops.api.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LogFileStorage 单元测试 — 验证文件保存、删除和路径安全。
 */
class LogFileStorageTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("保存上传文件到指定目录")
    void saveFile() throws Exception {
        LogFileStorage storage = new LogFileStorage(tempDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "app.log", "text/plain", "ERROR line1\nWARN line2".getBytes());

        String savedPath = storage.save(file);

        Path saved = Path.of(savedPath);
        assertTrue(Files.exists(saved));
        assertTrue(saved.startsWith(tempDir));
        assertTrue(saved.getFileName().toString().endsWith(".log"));
        assertEquals("ERROR line1\nWARN line2", Files.readString(saved));
    }

    @Test
    @DisplayName("空文件抛出异常")
    void saveEmptyFileThrows() {
        LogFileStorage storage = new LogFileStorage(tempDir.toString());

        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.log", "text/plain", new byte[0]);

        assertThrows(IllegalArgumentException.class, () -> storage.save(emptyFile));
    }

    @Test
    @DisplayName("删除已保存的文件")
    void deleteFile() throws Exception {
        LogFileStorage storage = new LogFileStorage(tempDir.toString());

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.log", "text/plain", "log content".getBytes());
        String savedPath = storage.save(file);
        assertTrue(Files.exists(Path.of(savedPath)));

        storage.delete(savedPath);
        assertFalse(Files.exists(Path.of(savedPath)));
    }

    @Test
    @DisplayName("删除不存在的文件不报错")
    void deleteNonExistentFile() {
        LogFileStorage storage = new LogFileStorage(tempDir.toString());
        assertDoesNotThrow(() -> storage.delete(tempDir.resolve("nonexistent.log").toString()));
    }

    @Test
    @DisplayName("只允许删除上传目录内的文件")
    void deleteOutsideUploadDirIgnored() throws Exception {
        LogFileStorage storage = new LogFileStorage(tempDir.toString());

        // 在上传目录外创建文件
        Path outsideFile = tempDir.getParent().resolve("outside-test.log");
        Files.writeString(outsideFile, "outside content");
        assertTrue(Files.exists(outsideFile));

        storage.delete(outsideFile.toString());
        assertTrue(Files.exists(outsideFile), "上传目录外的文件不应被删除");

        Files.deleteIfExists(outsideFile);
    }
}
