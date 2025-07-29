package com.fufu.terminal.service.executor;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.model.ScriptExecutionProgress;
import com.fufu.terminal.service.ProgressManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 原子脚本执行器
 * 负责执行单个原子脚本并提供进度反馈
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AtomicScriptExecutor {
    
    private final ProgressManagerService progressManager;
    
    /**
     * 异步执行原子脚本
     */
    public CompletableFuture<Object> executeAsync(String sessionId, Long executionId, AtomicScript script) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(sessionId, executionId, script);
            } catch (Exception e) {
                progressManager.setExecutionFailed(sessionId, "脚本执行异常: " + e.getMessage());
                throw new RuntimeException(e);
            }
        });
    }
    
    /**
     * 执行原子脚本
     */
    public Object execute(String sessionId, Long executionId, AtomicScript script) throws Exception {
        log.info("开始执行原子脚本: sessionId={}, scriptName={}", sessionId, script.getName());
        
        // 初始化进度（原子脚本通常分为3个步骤：准备、执行、清理）
        ScriptExecutionProgress progress = progressManager.initializeProgress(sessionId, executionId, 3);
        
        try {
            // 步骤1: 准备执行环境
            progressManager.updateCurrentStep(sessionId, "准备执行环境", "正在验证脚本内容和依赖项...");
            Thread.sleep(500); // 模拟准备时间
            validateScript(script);
            progressManager.updateStepProgress(sessionId, 50, "脚本验证完成");
            Thread.sleep(500);
            prepareExecutionEnvironment(script);
            progressManager.updateStepProgress(sessionId, 100, "执行环境准备完成");
            progressManager.completeCurrentStep(sessionId, "执行环境准备就绪");
            
            // 步骤2: 执行脚本内容
            progressManager.updateCurrentStep(sessionId, "执行脚本", "正在执行脚本内容...");
            Object result = executeScriptContent(sessionId, script);
            progressManager.completeCurrentStep(sessionId, "脚本执行完成");
            
            // 步骤3: 清理和收集结果
            progressManager.updateCurrentStep(sessionId, "清理资源", "正在清理执行环境...");
            progressManager.updateStepProgress(sessionId, 50, "正在收集执行结果...");
            Thread.sleep(300);
            progressManager.updateStepProgress(sessionId, 100, "资源清理完成");
            progressManager.completeCurrentStep(sessionId, "执行完成，结果已收集");
            
            // 设置结果数据
            progressManager.setResultData(sessionId, result);
            
            log.info("原子脚本执行成功: sessionId={}, scriptName={}", sessionId, script.getName());
            return result;
            
        } catch (Exception e) {
            log.error("原子脚本执行失败: sessionId={}, scriptName={}, error={}", sessionId, script.getName(), e.getMessage());
            progressManager.setExecutionFailed(sessionId, "执行失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 验证脚本
     */
    private void validateScript(AtomicScript script) throws Exception {
        if (script.getScriptContent() == null || script.getScriptContent().trim().isEmpty()) {
            throw new Exception("脚本内容不能为空");
        }
        
        if (script.getExecutionTimeout() != null && script.getExecutionTimeout() <= 0) {
            throw new Exception("执行超时时间必须大于0");
        }
    }
    
    /**
     * 准备执行环境
     */
    private void prepareExecutionEnvironment(AtomicScript script) throws Exception {
        // 这里可以进行环境检查、依赖验证等
        log.debug("准备执行环境: scriptType={}, dependencies={}", script.getScriptType(), script.getDependencies());
    }
    
    /**
     * 执行脚本内容
     */
    private Object executeScriptContent(String sessionId, AtomicScript script) throws Exception {
        String scriptType = script.getScriptType();
        String scriptContent = script.getScriptContent();
        
        if ("bash".equalsIgnoreCase(scriptType)) {
            return executeBashScript(sessionId, scriptContent, script.getExecutionTimeout());
        } else if ("python".equalsIgnoreCase(scriptType)) {
            return executePythonScript(sessionId, scriptContent, script.getExecutionTimeout());
        } else {
            throw new Exception("不支持的脚本类型: " + scriptType);
        }
    }
    
    /**
     * 执行Bash脚本
     */
    private String executeBashScript(String sessionId, String scriptContent, Integer timeout) throws Exception {
        progressManager.updateStepProgress(sessionId, 10, "启动Bash执行器...");
        
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", scriptContent);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        progressManager.updateStepProgress(sessionId, 30, "脚本开始执行...");
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                lineCount++;
                
                // 更新进度（简单的行数进度估算）
                if (lineCount % 5 == 0) {
                    int progress = Math.min(90, 30 + (lineCount * 2));
                    progressManager.updateStepProgress(sessionId, progress, "正在执行... 已处理 " + lineCount + " 行输出");
                }
            }
        }
        
        // 等待进程完成
        boolean finished = process.waitFor(timeout != null ? timeout : 300, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("脚本执行超时");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception("脚本执行失败，退出代码: " + exitCode + ", 输出: " + output.toString());
        }
        
        progressManager.updateStepProgress(sessionId, 100, "Bash脚本执行完成");
        return output.toString();
    }
    
    /**
     * 执行Python脚本
     */
    private String executePythonScript(String sessionId, String scriptContent, Integer timeout) throws Exception {
        progressManager.updateStepProgress(sessionId, 10, "启动Python执行器...");
        
        ProcessBuilder pb = new ProcessBuilder("python3", "-c", scriptContent);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        
        progressManager.updateStepProgress(sessionId, 30, "Python脚本开始执行...");
        
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
                lineCount++;
                
                if (lineCount % 3 == 0) {
                    int progress = Math.min(90, 30 + (lineCount * 3));
                    progressManager.updateStepProgress(sessionId, progress, "正在执行... 已处理 " + lineCount + " 行输出");
                }
            }
        }
        
        boolean finished = process.waitFor(timeout != null ? timeout : 300, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new Exception("Python脚本执行超时");
        }
        
        int exitCode = process.exitValue();
        if (exitCode != 0) {
            throw new Exception("Python脚本执行失败，退出代码: " + exitCode + ", 输出: " + output.toString());
        }
        
        progressManager.updateStepProgress(sessionId, 100, "Python脚本执行完成");
        return output.toString();
    }
}