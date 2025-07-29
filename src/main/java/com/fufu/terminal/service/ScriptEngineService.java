package com.fufu.terminal.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.fufu.terminal.entity.ExecutionLog;
import com.fufu.terminal.entity.Script;
import com.fufu.terminal.entity.ScriptExecution;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 脚本引擎服务类，负责解析和执行脚本配置中的各类步骤。
 * @author lizelin
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScriptEngineService {

    private final ScriptExecutionService executionService;
    private final ScriptService scriptService;

    /**
     * 异步执行指定脚本，记录每一步日志，并处理异常。
     *
     * @param scriptId  脚本ID
     * @param userId    用户ID
     * @param sessionId 会话ID
     * @return 脚本执行对象的CompletableFuture
     */
    public CompletableFuture<ScriptExecution> executeScript(Long scriptId, Long userId, String sessionId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 开始执行，创建执行记录
                ScriptExecution execution = executionService.startExecution(scriptId, userId, sessionId);

                // 获取脚本配置
                Script script = scriptService.getScriptById(scriptId);
                executionService.logStep(execution.getId(), "初始化", ExecutionLog.LogType.INFO,
                        "开始执行脚本: " + script.getName(), 1);

                // 解析脚本配置
                JSONObject config = JSON.parseObject(script.getConfig());
                JSONArray steps = config.getJSONArray("steps");

                // 配置校验
                if (steps == null || steps.isEmpty()) {
                    executionService.logStep(execution.getId(), "配置检查", ExecutionLog.LogType.ERROR,
                            "脚本配置为空或格式错误", 2);
                    executionService.completeExecution(execution.getId(), ScriptExecution.Status.FAILED, "脚本配置错误");
                    return execution;
                }

                executionService.logStep(execution.getId(), "配置解析", ExecutionLog.LogType.SUCCESS,
                        "成功解析脚本配置，共 " + steps.size() + " 个步骤", 2);

                // 逐步执行脚本步骤
                for (int i = 0; i < steps.size(); i++) {
                    JSONObject step = steps.getJSONObject(i);
                    String stepType = step.getString("type");
                    String stepName = step.getString("name");

                    executionService.logStep(execution.getId(), stepName, ExecutionLog.LogType.INFO,
                            "正在执行步骤: " + stepName, i + 3);

                    boolean success = executeStep(execution.getId(), step, i + 3);

                    if (!success) {
                        executionService.logStep(execution.getId(), stepName, ExecutionLog.LogType.ERROR,
                                "步骤执行失败: " + stepName, i + 3);
                        executionService.completeExecution(execution.getId(), ScriptExecution.Status.FAILED,
                                "步骤执行失败: " + stepName);
                        return execution;
                    }

                    executionService.logStep(execution.getId(), stepName, ExecutionLog.LogType.SUCCESS,
                            "步骤执行成功: " + stepName, i + 3);
                }

                // 所有步骤执行完成
                executionService.logStep(execution.getId(), "完成", ExecutionLog.LogType.SUCCESS,
                        "脚本执行完成", steps.size() + 3);
                executionService.completeExecution(execution.getId(), ScriptExecution.Status.SUCCESS, null);

                return execution;

            } catch (Exception e) {
                log.error("Script execution failed", e);
                ScriptExecution execution = executionService.getExecutionBySessionId(sessionId);
                if (execution != null) {
                    executionService.logStep(execution.getId(), "异常", ExecutionLog.LogType.ERROR,
                            "执行异常: " + e.getMessage(), 999);
                    executionService.completeExecution(execution.getId(), ScriptExecution.Status.FAILED, e.getMessage());
                }
                throw new RuntimeException("脚本执行失败", e);
            }
        });
    }

    /**
     * 执行单个脚本步骤，根据类型分发到不同的处理方法。
     *
     * @param executionId 执行记录ID
     * @param step        步骤配置
     * @param stepOrder   步骤顺序号
     * @return 步骤是否执行成功
     */
    private boolean executeStep(Long executionId, JSONObject step, int stepOrder) {
        try {
            String type = step.getString("type");
            String name = step.getString("name");

            switch (type) {
                case "system_check":
                    return executeSystemCheck(executionId, step, stepOrder);
                case "command":
                    return executeCommand(executionId, step, stepOrder);
                case "file_operation":
                    return executeFileOperation(executionId, step, stepOrder);
                case "condition":
                    return executeCondition(executionId, step, stepOrder);
                default:
                    // 未知类型，记录警告但不中断流程
                    executionService.logStep(executionId, name, ExecutionLog.LogType.WARN,
                            "未知的步骤类型: " + type, stepOrder);
                    return true;
            }
        } catch (Exception e) {
            executionService.logStep(executionId, step.getString("name"), ExecutionLog.LogType.ERROR,
                    "步骤执行异常: " + e.getMessage(), stepOrder);
            return false;
        }
    }

    /**
     * 执行系统检查步骤，模拟获取操作系统和Java版本信息。
     *
     * @param executionId 执行记录ID
     * @param step        步骤配置
     * @param stepOrder   步骤顺序号
     * @return 是否成功
     */
    private boolean executeSystemCheck(Long executionId, JSONObject step, int stepOrder) {
        String name = step.getString("name");

        // 模拟系统检查过程
        executionService.logStep(executionId, name, ExecutionLog.LogType.INFO,
                "正在检测操作系统...", stepOrder);

        try {
            Thread.sleep(1000); // 模拟检测耗时

            String osName = System.getProperty("os.name");
            String osVersion = System.getProperty("os.version");
            String javaVersion = System.getProperty("java.version");

            executionService.logStep(executionId, name, ExecutionLog.LogType.SUCCESS,
                    "操作系统: " + osName + " " + osVersion, stepOrder);
            executionService.logStep(executionId, name, ExecutionLog.LogType.INFO,
                    "Java版本: " + javaVersion, stepOrder);

            return true;
        } catch (Exception e) {
            executionService.logStep(executionId, name, ExecutionLog.LogType.ERROR,
                    "系统检测失败: " + e.getMessage(), stepOrder);
            return false;
        }
    }

    /**
     * 执行命令步骤，模拟命令执行过程。
     *
     * @param executionId 执行记录ID
     * @param step        步骤配置
     * @param stepOrder   步骤顺序号
     * @return 是否成功
     */
    private boolean executeCommand(Long executionId, JSONObject step, int stepOrder) {
        String name = step.getString("name");
        String command = step.getString("command");

        executionService.logStep(executionId, name, ExecutionLog.LogType.INFO,
                "执行命令: " + command, stepOrder);

        try {
            Thread.sleep(500); // 模拟命令执行耗时

            // 实际命令执行可在此实现
            executionService.logStep(executionId, name, ExecutionLog.LogType.SUCCESS,
                    "命令执行成功: " + command, stepOrder);

            return true;
        } catch (Exception e) {
            executionService.logStep(executionId, name, ExecutionLog.LogType.ERROR,
                    "命令执行失败: " + e.getMessage(), stepOrder);
            return false;
        }
    }

    /**
     * 执行文件操作步骤，模拟文件相关操作。
     *
     * @param executionId 执行记录ID
     * @param step        步骤配置
     * @param stepOrder   步骤顺序号
     * @return 是否成功
     */
    private boolean executeFileOperation(Long executionId, JSONObject step, int stepOrder) {
        String name = step.getString("name");
        String operation = step.getString("operation");
        String filePath = step.getString("filePath");

        executionService.logStep(executionId, name, ExecutionLog.LogType.INFO,
                "执行文件操作: " + operation + " -> " + filePath, stepOrder);

        try {
            Thread.sleep(300); // 模拟文件操作耗时

            executionService.logStep(executionId, name, ExecutionLog.LogType.SUCCESS,
                    "文件操作成功: " + operation, stepOrder);

            return true;
        } catch (Exception e) {
            executionService.logStep(executionId, name, ExecutionLog.LogType.ERROR,
                    "文件操作失败: " + e.getMessage(), stepOrder);
            return false;
        }
    }

    /**
     * 执行条件判断步骤，模拟条件检查。
     *
     * @param executionId 执行记录ID
     * @param step        步骤配置
     * @param stepOrder   步骤顺序号
     * @return 条件是否通过
     */
    private boolean executeCondition(Long executionId, JSONObject step, int stepOrder) {
        String name = step.getString("name");
        String condition = step.getString("condition");

        executionService.logStep(executionId, name, ExecutionLog.LogType.INFO,
                "检查条件: " + condition, stepOrder);

        try {
            // 模拟条件检查耗时
            Thread.sleep(200);

            // 随机模拟条件检查结果，80%概率通过
            boolean result = Math.random() > 0.2;

            if (result) {
                executionService.logStep(executionId, name, ExecutionLog.LogType.SUCCESS,
                        "条件检查通过: " + condition, stepOrder);
            } else {
                executionService.logStep(executionId, name, ExecutionLog.LogType.WARN,
                        "条件检查失败: " + condition, stepOrder);
            }

            return result;
        } catch (Exception e) {
            executionService.logStep(executionId, name, ExecutionLog.LogType.ERROR,
                    "条件检查异常: " + e.getMessage(), stepOrder);
            return false;
        }
    }
}
