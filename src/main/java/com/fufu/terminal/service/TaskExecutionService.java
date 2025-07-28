package com.fufu.terminal.service;

import com.fufu.terminal.command.CommandChain;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandChain;
import com.fufu.terminal.command.CommandChainFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;

/**
 * 负责执行后台任务的服务
 * 它是责任链模式的协调者，将任务执行与WebSocket消息处理解耦
 */
@Slf4j
@Service
public class TaskExecutionService {

    private final ExecutorService executorService;
    private final CommandChainFactory commandChainFactory;

    public TaskExecutionService(@Qualifier("taskExecutor") ExecutorService executorService, CommandChainFactory commandChainFactory) {
        this.executorService = executorService;
        this.commandChainFactory = commandChainFactory;
    }

    /**
     * 异步执行一个任务
     * @param taskName 任务的名称, e.g., "initialize_environment"
     * @param context 执行该任务所需的上下文
     */
    public void executeTask(String taskName, CommandContext context) {
        executorService.submit(() -> {
            try {
                log.info("Starting task '{}' for session {}", taskName, context.getWebSocketSession().getId());
                CommandChain chain = commandChainFactory.createCommandChain(taskName, context);
                chain.execute(context);
                log.info("Task '{}' completed for session {}", taskName, context.getWebSocketSession().getId());
            } catch (Exception e) {
                log.error("Task '{}' failed for session {}: {}", taskName, context.getWebSocketSession().getId(), e.getMessage(), e);
                // 错误已在 CommandChain 内部发送给前端
            }
        });
    }
}
