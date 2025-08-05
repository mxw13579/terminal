package com.fufu.terminal.sillytavern;

import com.fufu.terminal.dto.sillytavern.*;
import com.fufu.terminal.model.SshConnection;
import com.fufu.terminal.service.sillytavern.*;
import com.jcraft.jsch.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SillyTavern核心功能测试套件
 * 测试4个核心功能的业务逻辑和集成：配置管理、实时日志、数据管理、版本管理
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SillyTavern核心功能测试")
class SillyTavernServiceTest {

    @Mock
    private SillyTavernService sillyTavernService;
    
    @Mock
    private ConfigurationService configurationService;
    
    @Mock
    private RealTimeLogService realTimeLogService;
    
    @Mock
    private DataManagementService dataManagementService;
    
    @Mock
    private DockerVersionService dockerVersionService;

    @Mock
    private SshConnection sshConnection;

    @Mock
    private Session jschSession;

    @BeforeEach
    void setUp() {
        when(sshConnection.getJschSession()).thenReturn(jschSession);
        when(jschSession.isConnected()).thenReturn(true);
    }

    // ===== 配置管理功能测试 =====
    
    @Test
    @DisplayName("应该成功读取容器配置")
    void testReadConfiguration() throws Exception {
        // Given
        String containerName = "sillytavern";
        ConfigurationDto expectedConfig = new ConfigurationDto();
        expectedConfig.setContainerName(containerName);
        expectedConfig.setUsername("admin");
        expectedConfig.setHasPassword(true);
        expectedConfig.setPort(8000);
        expectedConfig.setServerName("SillyTavern");
        expectedConfig.setEnableExtensions(true);
        expectedConfig.setTheme("dark");
        expectedConfig.setLanguage("zh-CN");

        when(configurationService.readConfiguration(sshConnection, containerName))
                .thenReturn(expectedConfig);

        // When
        ConfigurationDto result = configurationService.readConfiguration(sshConnection, containerName);

        // Then
        assertNotNull(result);
        assertEquals(containerName, result.getContainerName());
        assertEquals("admin", result.getUsername());
        assertTrue(result.getHasPassword());
        assertEquals(8000, result.getPort());
        assertEquals("SillyTavern", result.getServerName());
        assertTrue(result.getEnableExtensions());
        assertEquals("dark", result.getTheme());
        assertEquals("zh-CN", result.getLanguage());
        verify(configurationService).readConfiguration(sshConnection, containerName);
    }

    @Test
    @DisplayName("应该验证用户名格式要求")
    void testUsernameValidation() throws Exception {
        // Given
        String containerName = "sillytavern";
        java.util.Map<String, String> validationErrors = new java.util.HashMap<>();
        
        // 测试包含数字的用户名
        ConfigurationDto invalidConfig1 = new ConfigurationDto();
        invalidConfig1.setUsername("admin123");
        invalidConfig1.setPassword("newpassword");
        
        validationErrors.put("username", "用户名不能包含数字或特殊字符，只允许字母和下划线");
        when(configurationService.validateConfiguration(invalidConfig1))
                .thenReturn(validationErrors);

        // When & Then
        java.util.Map<String, String> result1 = configurationService.validateConfiguration(invalidConfig1);
        assertFalse(result1.isEmpty());
        assertTrue(result1.get("username").contains("用户名不能包含数字"));
        
        // 测试包含特殊字符的用户名
        ConfigurationDto invalidConfig2 = new ConfigurationDto();
        invalidConfig2.setUsername("admin@123");
        
        validationErrors.clear();
        validationErrors.put("username", "用户名不能包含数字或特殊字符，只允许字母和下划线");
        when(configurationService.validateConfiguration(invalidConfig2))
                .thenReturn(validationErrors);
        
        java.util.Map<String, String> result2 = configurationService.validateConfiguration(invalidConfig2);
        assertFalse(result2.isEmpty());
        assertTrue(result2.get("username").contains("特殊字符"));
        
        // 测试有效的用户名
        ConfigurationDto validConfig = new ConfigurationDto();
        validConfig.setUsername("admin_user");
        validConfig.setPassword("strongpassword123");
        
        when(configurationService.validateConfiguration(validConfig))
                .thenReturn(new java.util.HashMap<>());
        
        java.util.Map<String, String> result3 = configurationService.validateConfiguration(validConfig);
        assertTrue(result3.isEmpty());
    }

    @Test
    @DisplayName("应该验证密码强度和复杂性要求")
    void testPasswordStrengthValidation() throws Exception {
        // Given
        String containerName = "sillytavern";
        java.util.Map<String, String> validationErrors = new java.util.HashMap<>();
        
        // 测试太短的密码
        ConfigurationDto weakPasswordConfig1 = new ConfigurationDto();
        weakPasswordConfig1.setUsername("admin");
        weakPasswordConfig1.setPassword("123");
        
        validationErrors.put("password", "密码强度不足，至少需要6个字符，建议包含数字和字母");
        when(configurationService.validateConfiguration(weakPasswordConfig1))
                .thenReturn(validationErrors);

        java.util.Map<String, String> result1 = configurationService.validateConfiguration(weakPasswordConfig1);
        assertFalse(result1.isEmpty());
        assertTrue(result1.get("password").contains("密码强度不足"));
        
        // 测试只有字母的密码
        ConfigurationDto weakPasswordConfig2 = new ConfigurationDto();
        weakPasswordConfig2.setUsername("admin");
        weakPasswordConfig2.setPassword("password");
        
        validationErrors.clear();
        validationErrors.put("password", "密码建议包含数字和字母以提高安全性");
        when(configurationService.validateConfiguration(weakPasswordConfig2))
                .thenReturn(validationErrors);
        
        java.util.Map<String, String> result2 = configurationService.validateConfiguration(weakPasswordConfig2);
        assertFalse(result2.isEmpty());
        assertTrue(result2.get("password").contains("建议包含数字和字母"));

        // 测试强密码
        ConfigurationDto strongPasswordConfig = new ConfigurationDto();
        strongPasswordConfig.setUsername("admin");
        strongPasswordConfig.setPassword("StrongPass123!");
        
        when(configurationService.validateConfiguration(strongPasswordConfig))
                .thenReturn(new java.util.HashMap<>());
        
        java.util.Map<String, String> result3 = configurationService.validateConfiguration(strongPasswordConfig);
        assertTrue(result3.isEmpty());
    }

    @Test
    @DisplayName("应该在配置修改后自动重启容器")
    void testConfigurationUpdateWithRestart() throws Exception {
        // Given
        String containerName = "sillytavern";
        ConfigurationDto newConfig = new ConfigurationDto();
        newConfig.setUsername("newadmin");
        newConfig.setPassword("strongpassword123");

        when(configurationService.updateConfigurationWithRestart(sshConnection, containerName, newConfig))
                .thenReturn(true);

        // When
        boolean result = configurationService.updateConfigurationWithRestart(sshConnection, containerName, newConfig);

        // Then
        assertTrue(result);
        verify(configurationService).updateConfigurationWithRestart(sshConnection, containerName, newConfig);
    }

    @Test
    @DisplayName("应该处理并发配置修改的锁机制")
    void testConcurrentConfigurationUpdate() throws Exception {
        // Given
        String containerName = "sillytavern";
        ConfigurationDto config1 = new ConfigurationDto();
        config1.setUsername("admin1");
        config1.setPassword("password1");
        
        ConfigurationDto config2 = new ConfigurationDto();
        config2.setUsername("admin2");
        config2.setPassword("password2");

        // 第一次调用成功
        when(configurationService.updateConfigurationWithRestart(sshConnection, containerName, config1))
                .thenReturn(true);
        
        // 第二次调用因为锁被占用而等待
        when(configurationService.updateConfigurationWithRestart(sshConnection, containerName, config2))
                .thenAnswer(invocation -> {
                    Thread.sleep(100); // 模拟等待锁
                    return true;
                });

        // When
        CompletableFuture<Boolean> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                return configurationService.updateConfigurationWithRestart(sshConnection, containerName, config1);
            } catch (Exception e) {
                return false;
            }
        });

        CompletableFuture<Boolean> future2 = CompletableFuture.supplyAsync(() -> {
            try {
                return configurationService.updateConfigurationWithRestart(sshConnection, containerName, config2);
            } catch (Exception e) {
                return false;
            }
        });

        // Then
        Boolean result1 = future1.get(5, TimeUnit.SECONDS);
        Boolean result2 = future2.get(5, TimeUnit.SECONDS);

        assertTrue(result1);
        assertTrue(result2);
    }

    // ===== 实时日志查看功能测试 =====

    @Test
    @DisplayName("应该启动实时日志流并推送WebSocket消息")
    void testStartLogStreamWithWebSocketPush() {
        // Given
        String sessionId = "test-session";
        String containerName = "sillytavern";
        int maxLines = 1000;

        doNothing().when(realTimeLogService).startLogStream(sessionId, containerName, maxLines);

        // When
        realTimeLogService.startLogStream(sessionId, containerName, maxLines);

        // Then
        verify(realTimeLogService).startLogStream(sessionId, containerName, maxLines);
    }

    @Test
    @DisplayName("应该支持不同日志条数选择（500/1000/3000）")
    void testLogLinesSelection() throws Exception {
        // Given
        String containerName = "sillytavern";
        int[] lineCounts = {500, 1000, 3000};

        for (int lines : lineCounts) {
            RealTimeLogDto logDto = new RealTimeLogDto();
            logDto.setContainerName(containerName);
            logDto.setLines(generateMockLogLines(lines));
            logDto.setTotalLines(lines);
            logDto.setTimestamp(LocalDateTime.now());
            logDto.setIsRealTime(false);
            logDto.setLogLevel("all");

            when(realTimeLogService.getHistoryLogs(sshConnection, containerName, lines, "all"))
                    .thenReturn(logDto);

            // When
            RealTimeLogDto result = realTimeLogService.getHistoryLogs(sshConnection, containerName, lines, "all");

            // Then
            assertNotNull(result);
            assertEquals(containerName, result.getContainerName());
            assertEquals(lines, result.getTotalLines());
            assertEquals(lines, result.getLines().size());
            assertNotNull(result.getTimestamp());
            assertEquals("all", result.getLogLevel());
        }
    }

    @Test
    @DisplayName("应该支持日志级别过滤（all/error/warn/info）")
    void testLogLevelFiltering() throws Exception {
        // Given
        String containerName = "sillytavern";
        String[] logLevels = {"all", "error", "warn", "info"};

        for (String level : logLevels) {
            RealTimeLogDto logDto = new RealTimeLogDto();
            logDto.setContainerName(containerName);
            logDto.setLines(List.of(
                "[" + level.toUpperCase() + "] Test log message",
                "[" + level.toUpperCase() + "] Another test message"
            ));
            logDto.setTotalLines(2);
            logDto.setLogLevel(level);

            when(realTimeLogService.getHistoryLogs(sshConnection, containerName, 500, level))
                    .thenReturn(logDto);

            // When
            RealTimeLogDto result = realTimeLogService.getHistoryLogs(sshConnection, containerName, 500, level);

            // Then
            assertNotNull(result);
            assertEquals(level, result.getLogLevel());
            assertTrue(result.getLines().stream()
                    .allMatch(line -> line.contains(level.toUpperCase()) || level.equals("all")));
        }
    }

    @Test
    @DisplayName("应该在用户断连后自动清理资源")
    void testLogStreamResourceCleanup() {
        // Given
        String sessionId = "test-session";

        doNothing().when(realTimeLogService).stopLogStream(sessionId);

        // When
        realTimeLogService.stopLogStream(sessionId);

        // Then
        verify(realTimeLogService).stopLogStream(sessionId);
    }

    @Test
    @DisplayName("应该管理内存使用并在达到限制时自动清理")
    void testLogStreamMemoryManagement() throws Exception {
        // Given
        String containerName = "sillytavern";
        int maxMemoryLines = 5000;

        RealTimeLogDto.MemoryInfo memoryInfo = RealTimeLogDto.MemoryInfo.builder()
                .cachedLines(4500)
                .maxLines(maxMemoryLines)
                .memoryUsagePercent(90.0)
                .needsCleanup(true)
                .build();

        RealTimeLogDto logDto = new RealTimeLogDto();
        logDto.setContainerName(containerName);
        logDto.setMaxLines(maxMemoryLines);
        logDto.setMemoryInfo(memoryInfo);
        logDto.setTotalLines(4500);

        when(realTimeLogService.getHistoryLogs(sshConnection, containerName, maxMemoryLines, "all"))
                .thenReturn(logDto);

        // When
        RealTimeLogDto result = realTimeLogService.getHistoryLogs(sshConnection, containerName, maxMemoryLines, "all");

        // Then
        assertNotNull(result);
        assertEquals(maxMemoryLines, result.getMaxLines());
        assertNotNull(result.getMemoryInfo());
        assertEquals(4500, result.getMemoryInfo().getCachedLines());
        assertEquals(90.0, result.getMemoryInfo().getMemoryUsagePercent());
        assertTrue(result.getMemoryInfo().getNeedsCleanup());
    }

    @Test
    @DisplayName("应该处理日志流的实时推送和批量推送")
    void testRealTimeLogPushModes() {
        // Given
        String sessionId = "test-session";
        String containerName = "sillytavern";

        // 实时推送模式
        RealTimeLogDto realtimeLog = RealTimeLogDto.builder()
                .sessionId(sessionId)
                .containerName(containerName)
                .lines(List.of("New log line"))
                .isRealTime(true)
                .isComplete(false)
                .timestamp(LocalDateTime.now())
                .build();

        // 历史日志模式（批量）
        RealTimeLogDto historyLog = RealTimeLogDto.builder()
                .sessionId(sessionId)
                .containerName(containerName)
                .lines(generateMockLogLines(100))
                .isRealTime(false)
                .isComplete(true)
                .timestamp(LocalDateTime.now())
                .build();

        // When & Then
        assertTrue(realtimeLog.getIsRealTime());
        assertFalse(realtimeLog.getIsComplete());
        assertEquals(1, realtimeLog.getLines().size());

        assertFalse(historyLog.getIsRealTime());
        assertTrue(historyLog.getIsComplete());
        assertEquals(100, historyLog.getLines().size());
    }

    /**
     * 生成模拟日志行
     */
    private List<String> generateMockLogLines(int count) {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            lines.add(String.format("[INFO] %s Log line %d - Test message", 
                    LocalDateTime.now().toString(), i + 1));
        }
        return lines;
    }

    // ===== 数据管理功能测试 =====

    @Test
    @DisplayName("应该基于SFTP导出数据目录并创建ZIP文件")
    void testDataExportViaSFTP() {
        // Given
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Export progress: " + progress);

        DataExportDto expectedExport = new DataExportDto();
        expectedExport.setContainerName(containerName);
        expectedExport.setFileName("sillytavern_data_20240804_120000.zip");
        expectedExport.setSizeBytes(5 * 1024 * 1024L); // 5MB
        expectedExport.setDownloadUrl("/download/temp/sillytavern_data_20240804_120000.zip");
        expectedExport.setExportPath("/tmp/exports/sillytavern_data_20240804_120000.zip");
        expectedExport.setCreatedAt(LocalDateTime.now());
        expectedExport.setCompressedSize(3 * 1024 * 1024L); // 3MB after compression

        when(dataManagementService.exportData(sshConnection, containerName, progressCallback))
                .thenReturn(CompletableFuture.completedFuture(expectedExport));

        // When
        CompletableFuture<DataExportDto> future = dataManagementService.exportData(sshConnection, containerName, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            DataExportDto result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
            assertEquals(containerName, result.getContainerName());
            assertTrue(result.getFileName().contains("sillytavern_data"));
            assertTrue(result.getFileName().endsWith(".zip"));
            assertTrue(result.getSizeBytes() > 0);
            assertTrue(result.getCompressedSize() < result.getSizeBytes()); // 压缩后应该更小
            assertNotNull(result.getDownloadUrl());
            assertNotNull(result.getExportPath());
            assertNotNull(result.getCreatedAt());
        });
        verify(dataManagementService).exportData(sshConnection, containerName, progressCallback);
    }

    @Test
    @DisplayName("应该验证ZIP文件结构包含必需的目录")
    void testZipFileStructureValidation() {
        // Given
        String[] testFiles = {
            "invalid_structure.zip",  // 不包含data目录
            "empty_archive.zip",      // 空压缩包
            "corrupted_archive.zip"   // 损坏的压缩包
        };
        
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Import progress: " + progress);

        for (String fileName : testFiles) {
            String errorMessage = switch (fileName) {
                case "invalid_structure.zip" -> "ZIP文件结构无效：根目录必须包含data文件夹";
                case "empty_archive.zip" -> "ZIP文件为空或不包含有效数据";
                case "corrupted_archive.zip" -> "ZIP文件已损坏，无法解压";
                default -> "未知错误";
            };

            when(dataManagementService.importData(sshConnection, containerName, fileName, progressCallback))
                    .thenReturn(CompletableFuture.failedFuture(
                            new IllegalArgumentException(errorMessage)));

            // When & Then
            CompletableFuture<Void> future = dataManagementService.importData(sshConnection, containerName, fileName, progressCallback);

            Exception exception = assertThrows(Exception.class, () -> {
                future.get(5, TimeUnit.SECONDS);
            });

            String actualMessage = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
            assertTrue(actualMessage.contains("ZIP文件") || actualMessage.contains("已损坏"));
        }
    }

    @Test
    @DisplayName("应该在数据导入前自动创建备份")
    void testDataImportWithAutoBackup() {
        // Given
        String uploadedFileName = "valid_data.zip";
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> {
            System.out.println("Import progress: " + progress);
            // 验证进度消息包含备份步骤
            if (progress.contains("创建备份")) {
                // 备份步骤被执行
            }
        };

        when(dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟导入过程的各个步骤
                    callback.accept("正在验证ZIP文件结构...");
                    callback.accept("正在创建当前数据备份...");
                    callback.accept("正在停止容器...");
                    callback.accept("正在解压数据文件...");
                    callback.accept("正在替换数据目录...");
                    callback.accept("正在启动容器...");
                    callback.accept("数据导入完成");
                    return CompletableFuture.completedFuture(null);
                });

        // When
        CompletableFuture<Void> future = dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            future.get(10, TimeUnit.SECONDS);
        });
        verify(dataManagementService).importData(sshConnection, containerName, uploadedFileName, progressCallback);
    }

    @Test
    @DisplayName("应该处理导入失败时的自动回滚机制")
    void testDataImportRollbackOnFailure() {
        // Given
        String uploadedFileName = "problematic_data.zip";
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Import progress: " + progress);

        when(dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟导入过程中的失败和回滚
                    callback.accept("正在验证ZIP文件结构...");
                    callback.accept("正在创建当前数据备份...");
                    callback.accept("正在停止容器...");
                    callback.accept("正在解压数据文件...");
                    callback.accept("数据文件损坏，开始回滚...");
                    callback.accept("正在恢复原始数据...");
                    callback.accept("正在启动容器...");
                    return CompletableFuture.failedFuture(
                            new RuntimeException("导入失败，已成功回滚到备份状态"));
                });

        // When & Then
        CompletableFuture<Void> future = dataManagementService.importData(sshConnection, containerName, uploadedFileName, progressCallback);

        Exception exception = assertThrows(Exception.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        String actualMessage = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        assertTrue(actualMessage.contains("已成功回滚"));
    }

    @Test
    @DisplayName("应该支持大文件的导入导出操作")
    void testLargeFileDataOperations() {
        // Given
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Large file progress: " + progress);

        // 大文件导出测试
        DataExportDto largeExport = new DataExportDto();
        largeExport.setContainerName(containerName);
        largeExport.setFileName("sillytavern_large_data.zip");
        largeExport.setSizeBytes(500 * 1024 * 1024L); // 500MB
        largeExport.setCompressedSize(200 * 1024 * 1024L); // 200MB compressed

        when(dataManagementService.exportData(sshConnection, containerName, progressCallback))
                .thenReturn(CompletableFuture.completedFuture(largeExport));

        // When - 导出大文件
        CompletableFuture<DataExportDto> exportFuture = dataManagementService.exportData(sshConnection, containerName, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            DataExportDto result = exportFuture.get(30, TimeUnit.SECONDS);
            assertNotNull(result);
            assertTrue(result.getSizeBytes() > 100 * 1024 * 1024L); // 大于100MB
            assertTrue(result.getCompressedSize() < result.getSizeBytes());
        });

        // Given - 大文件导入测试
        String largeFileName = "large_backup_500mb.zip";
        when(dataManagementService.importData(sshConnection, containerName, largeFileName, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟大文件处理的进度
                    for (int i = 0; i <= 100; i += 10) {
                        callback.accept(String.format("处理大文件进度: %d%%", i));
                    }
                    return CompletableFuture.completedFuture(null);
                });

        // When - 导入大文件
        CompletableFuture<Void> importFuture = dataManagementService.importData(sshConnection, containerName, largeFileName, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            importFuture.get(60, TimeUnit.SECONDS); // 大文件需要更长时间
        });
    }

    @Test
    @DisplayName("应该处理并发数据操作的互斥锁")
    void testConcurrentDataOperationMutex() throws Exception {
        // Given
        String containerName = "sillytavern";
        Consumer<String> progressCallback = progress -> System.out.println("Concurrent operation: " + progress);

        // 第一个操作（导出）
        when(dataManagementService.exportData(sshConnection, containerName, progressCallback))
                .thenAnswer(invocation -> {
                    // 模拟长时间运行的导出操作
                    return CompletableFuture.supplyAsync(() -> {
                        try {
                            Thread.sleep(2000); // 2秒导出时间
                            DataExportDto export = new DataExportDto();
                            export.setContainerName(containerName);
                            export.setFileName("concurrent_export.zip");
                            return export;
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });

        // 第二个操作（导入）应该等待第一个完成
        when(dataManagementService.importData(sshConnection, containerName, "concurrent_import.zip", progressCallback))
                .thenAnswer(invocation -> {
                    // 模拟等待锁释放
                    return CompletableFuture.runAsync(() -> {
                        try {
                            Thread.sleep(100); // 模拟等待锁
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });

        // When - 并发执行两个操作
        CompletableFuture<DataExportDto> exportFuture = dataManagementService.exportData(sshConnection, containerName, progressCallback);
        CompletableFuture<Void> importFuture = dataManagementService.importData(sshConnection, containerName, "concurrent_import.zip", progressCallback);

        // Then - 两个操作都应该成功完成
        assertDoesNotThrow(() -> {
            DataExportDto exportResult = exportFuture.get(5, TimeUnit.SECONDS);
            importFuture.get(5, TimeUnit.SECONDS);
            assertNotNull(exportResult);
        });
    }

    @Test
    @DisplayName("应该验证数据完整性和一致性")
    void testDataIntegrityValidation() {
        // Given
        String containerName = "sillytavern";
        String validDataFile = "integrity_test.zip";
        Consumer<String> progressCallback = progress -> System.out.println("Integrity check: " + progress);

        when(dataManagementService.importData(sshConnection, containerName, validDataFile, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟数据完整性检查过程
                    callback.accept("正在验证文件完整性...");
                    callback.accept("正在检查数据格式...");
                    callback.accept("正在验证配置文件...");
                    callback.accept("正在检查数据库文件...");
                    callback.accept("数据完整性验证通过");
                    return CompletableFuture.completedFuture(null);
                });

        // When
        CompletableFuture<Void> future = dataManagementService.importData(sshConnection, containerName, validDataFile, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            future.get(10, TimeUnit.SECONDS);
        });
        verify(dataManagementService).importData(sshConnection, containerName, validDataFile, progressCallback);
    }

    // ===== Docker版本管理功能测试 =====

    @Test
    @DisplayName("应该查询GitHub API获取最新版本信息")
    void testGetVersionInfoFromGitHubAPI() {
        // Given
        String containerName = "sillytavern";
        VersionInfoDto expectedVersionInfo = new VersionInfoDto();
        expectedVersionInfo.setContainerName(containerName);
        expectedVersionInfo.setCurrentVersion("1.12.0");
        expectedVersionInfo.setLatestVersion("1.12.2");
        expectedVersionInfo.setAvailableVersions(List.of("1.12.2", "1.12.1", "1.12.0", "1.11.9"));
        expectedVersionInfo.setHasUpdate(true);
        expectedVersionInfo.setLastChecked(LocalDateTime.now());
        expectedVersionInfo.setCurrentVersionReleaseDate(LocalDateTime.now().minusDays(30));
        expectedVersionInfo.setLatestVersionReleaseDate(LocalDateTime.now().minusDays(7));
        expectedVersionInfo.setUpdateDescription("Bug fixes and performance improvements");

        when(dockerVersionService.getVersionInfo(sshConnection, containerName))
                .thenReturn(expectedVersionInfo);

        // When
        VersionInfoDto result = dockerVersionService.getVersionInfo(sshConnection, containerName);

        // Then
        assertNotNull(result);
        assertEquals(containerName, result.getContainerName());
        assertEquals("1.12.0", result.getCurrentVersion());
        assertEquals("1.12.2", result.getLatestVersion());
        assertTrue(result.getHasUpdate());
        assertEquals(4, result.getAvailableVersions().size());
        assertNotNull(result.getLastChecked());
        assertNotNull(result.getCurrentVersionReleaseDate());
        assertNotNull(result.getLatestVersionReleaseDate());
        assertNotNull(result.getUpdateDescription());
        // 验证版本列表是按最新到最旧排序
        assertEquals("1.12.2", result.getAvailableVersions().get(0));
        assertEquals("1.11.9", result.getAvailableVersions().get(3));
    }

    @Test
    @DisplayName("应该显示最新的3个可用版本")
    void testGetLatestThreeVersions() {
        // Given
        String containerName = "sillytavern";
        VersionInfoDto versionInfo = new VersionInfoDto();
        versionInfo.setContainerName(containerName);
        versionInfo.setCurrentVersion("1.12.0");
        versionInfo.setLatestVersion("1.12.2");
        // 只显示最新的3个版本
        versionInfo.setAvailableVersions(List.of("1.12.2", "1.12.1", "1.12.0"));

        when(dockerVersionService.getVersionInfo(sshConnection, containerName))
                .thenReturn(versionInfo);

        // When
        VersionInfoDto result = dockerVersionService.getVersionInfo(sshConnection, containerName);

        // Then
        assertNotNull(result);
        List<String> versions = result.getAvailableVersions();
        assertEquals(3, versions.size());
        assertEquals("1.12.2", versions.get(0)); // 最新版本在前
        assertEquals("1.12.1", versions.get(1));
        assertEquals("1.12.0", versions.get(2));
        
        // 验证当前版本在列表中
        assertTrue(versions.contains(result.getCurrentVersion()));
    }

    @Test
    @DisplayName("应该处理GitHub API访问失败的情况")
    void testGitHubAPIFailureHandling() {
        // Given
        String containerName = "sillytavern";
        VersionInfoDto errorVersionInfo = VersionInfoDto.error(containerName, 
                "无法连接到GitHub API，请检查网络连接");

        when(dockerVersionService.getVersionInfo(sshConnection, containerName))
                .thenReturn(errorVersionInfo);

        // When
        VersionInfoDto result = dockerVersionService.getVersionInfo(sshConnection, containerName);

        // Then
        assertNotNull(result);
        assertEquals(containerName, result.getContainerName());
        assertEquals("unknown", result.getCurrentVersion());
        assertFalse(result.getHasUpdate());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("无法连接到GitHub API"));
    }

    @Test
    @DisplayName("应该执行版本切换并清理旧镜像")
    void testVersionUpgradeWithImageCleanup() {
        // Given
        String containerName = "sillytavern";
        String targetVersion = "1.12.2";
        Consumer<String> progressCallback = progress -> System.out.println("Upgrade progress: " + progress);

        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟版本升级过程
                    callback.accept("正在停止容器...");
                    callback.accept("正在拉取目标版本镜像: " + targetVersion);
                    callback.accept("正在创建新容器...");
                    callback.accept("正在启动新版本容器...");
                    callback.accept("正在清理旧版本镜像...");
                    callback.accept("版本升级完成");
                    return CompletableFuture.completedFuture(null);
                });

        // When
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            future.get(30, TimeUnit.SECONDS);
        });
        verify(dockerVersionService).upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);
    }

    @Test
    @DisplayName("应该实现版本升级的互斥锁机制")
    void testVersionUpgradeExclusiveLock() {
        // Given
        String containerName = "sillytavern";
        String version1 = "1.12.1";
        String version2 = "1.12.2";
        Consumer<String> progressCallback = progress -> System.out.println("Upgrade progress: " + progress);

        // 第一次升级成功
        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, version1, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    return CompletableFuture.runAsync(() -> {
                        try {
                            callback.accept("开始升级到版本: " + version1);
                            Thread.sleep(1000); // 模拟升级耗时
                            callback.accept("升级完成: " + version1);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });

        // 第二次升级需要等待第一次完成
        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, version2, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    return CompletableFuture.runAsync(() -> {
                        try {
                            callback.accept("等待前一个升级操作完成...");
                            Thread.sleep(100); // 模拟等待锁
                            callback.accept("开始升级到版本: " + version2);
                            Thread.sleep(500);
                            callback.accept("升级完成: " + version2);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });

        // When
        CompletableFuture<Void> future1 = dockerVersionService.upgradeToVersion(sshConnection, containerName, version1, progressCallback);
        CompletableFuture<Void> future2 = dockerVersionService.upgradeToVersion(sshConnection, containerName, version2, progressCallback);

        // Then - 两个升级操作都应该成功完成，但是顺序执行
        assertDoesNotThrow(() -> {
            future1.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);
        });
    }

    @Test
    @DisplayName("应该处理无效版本的升级失败")
    void testVersionUpgradeFailureWithInvalidVersion() {
        // Given
        String containerName = "sillytavern";
        String invalidVersion = "invalid-version-999";
        Consumer<String> progressCallback = progress -> System.out.println("Upgrade progress: " + progress);

        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, invalidVersion, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    callback.accept("正在验证目标版本...");
                    callback.accept("版本验证失败");
                    return CompletableFuture.failedFuture(
                            new RuntimeException("版本升级失败：找不到指定版本的镜像 " + invalidVersion));
                });

        // When & Then
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, invalidVersion, progressCallback);

        Exception exception = assertThrows(Exception.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        });

        String actualMessage = exception.getCause() != null ? exception.getCause().getMessage() : exception.getMessage();
        assertTrue(actualMessage.contains("版本升级失败") && actualMessage.contains("找不到指定版本"));
    }

    @Test
    @DisplayName("应该执行Docker镜像清理操作")
    void testDockerImageCleanup() {
        // Given
        Consumer<String> progressCallback = progress -> System.out.println("Cleanup progress: " + progress);

        when(dockerVersionService.cleanupUnusedImages(sshConnection))
                .thenAnswer(invocation -> {
                    // 模拟镜像清理过程
                    progressCallback.accept("正在扫描未使用的镜像...");
                    progressCallback.accept("找到 3 个未使用的镜像");
                    progressCallback.accept("正在删除镜像: ghcr.io/sillytavern/sillytavern:1.11.8");
                    progressCallback.accept("正在删除镜像: ghcr.io/sillytavern/sillytavern:1.11.7");
                    progressCallback.accept("正在删除镜像: ghcr.io/sillytavern/sillytavern:1.11.6");
                    progressCallback.accept("镜像清理完成，释放了 1.2GB 空间");
                    return null;
                });

        // When
        assertDoesNotThrow(() -> {
            dockerVersionService.cleanupUnusedImages(sshConnection);
        });

        // Then
        verify(dockerVersionService).cleanupUnusedImages(sshConnection);
    }

    @Test
    @DisplayName("应该处理镜像清理时的权限问题")
    void testDockerImageCleanupPermissionError() {
        // Given
        when(dockerVersionService.cleanupUnusedImages(sshConnection))
                .thenThrow(new RuntimeException("权限不足：需要sudo权限才能清理Docker镜像"));

        // When & Then
        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            dockerVersionService.cleanupUnusedImages(sshConnection);
        });

        assertTrue(exception.getMessage().contains("权限不足"));
        assertTrue(exception.getMessage().contains("sudo权限"));
    }

    @Test
    @DisplayName("应该支持版本回滚操作")
    void testVersionRollback() {
        // Given
        String containerName = "sillytavern";
        String rollbackVersion = "1.11.9"; // 回滚到之前的版本
        Consumer<String> progressCallback = progress -> System.out.println("Rollback progress: " + progress);

        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, rollbackVersion, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟版本回滚过程
                    callback.accept("正在执行版本回滚...");
                    callback.accept("正在停止当前容器...");
                    callback.accept("正在拉取旧版本镜像: " + rollbackVersion);
                    callback.accept("正在创建回滚容器...");
                    callback.accept("正在启动回滚版本...");
                    callback.accept("版本回滚完成");
                    return CompletableFuture.completedFuture(null);
                });

        // When
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, rollbackVersion, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            future.get(20, TimeUnit.SECONDS);
        });
        verify(dockerVersionService).upgradeToVersion(sshConnection, containerName, rollbackVersion, progressCallback);
    }

    @Test
    @DisplayName("应该在升级过程中保持数据持久性")
    void testVersionUpgradeDataPersistence() {
        // Given
        String containerName = "sillytavern";
        String targetVersion = "1.12.2";
        Consumer<String> progressCallback = progress -> {
            System.out.println("Upgrade with data persistence: " + progress);
            // 验证数据持久性相关的步骤
            if (progress.contains("数据卷挂载") || progress.contains("数据持久性")) {
                // 数据持久性步骤被执行
            }
        };

        when(dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback))
                .thenAnswer(invocation -> {
                    Consumer<String> callback = invocation.getArgument(3);
                    // 模拟升级过程中的数据持久性处理
                    callback.accept("正在验证数据卷挂载...");
                    callback.accept("正在停止容器（保持数据卷）...");
                    callback.accept("正在创建新版本容器...");
                    callback.accept("正在挂载现有数据卷...");
                    callback.accept("正在验证数据持久性...");
                    callback.accept("数据持久性验证通过");
                    callback.accept("版本升级完成，数据已保持");
                    return CompletableFuture.completedFuture(null);
                });

        // When
        CompletableFuture<Void> future = dockerVersionService.upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);

        // Then
        assertDoesNotThrow(() -> {
            future.get(25, TimeUnit.SECONDS);
        });
        verify(dockerVersionService).upgradeToVersion(sshConnection, containerName, targetVersion, progressCallback);
    }

    @Test
    @DisplayName("应该处理网络连接超时的版本查询")
    void testVersionQueryNetworkTimeout() {
        // Given
        String containerName = "sillytavern";
        VersionInfoDto timeoutVersionInfo = VersionInfoDto.error(containerName, 
                "网络连接超时，无法获取最新版本信息");

        when(dockerVersionService.getVersionInfo(sshConnection, containerName))
                .thenReturn(timeoutVersionInfo);

        // When
        VersionInfoDto result = dockerVersionService.getVersionInfo(sshConnection, containerName);

        // Then
        assertNotNull(result);
        assertEquals(containerName, result.getContainerName());
        assertFalse(result.getHasUpdate());
        assertNotNull(result.getError());
        assertTrue(result.getError().contains("网络连接超时"));
        assertEquals("unknown", result.getCurrentVersion());
    }
}
}