package com.fufu.terminal.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 责任链，用于组织和执行一系列命令
 */
@Slf4j
public class CommandChain {

    private final List<Command> commands = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void addCommand(Command command) {
        this.commands.add(command);
    }

    public void execute(CommandContext context) throws Exception {
        for (Command command : commands) {
            if (command.shouldExecute(context)) {
                sendProgress(context, command.getName(), "in_progress", "Starting...");
                try {
                    command.execute(context);
                    sendProgress(context, command.getName(), "success", "Completed successfully.");
                } catch (Exception e) {
                    log.error("Command '{}' failed with error: {}", command.getName(), e.getMessage(), e);
                    sendProgress(context, command.getName(), "error", e.getMessage());
                    // 出现错误时停止执行链
                    throw new RuntimeException("Execution failed at command: " + command.getName(), e);
                }
            } else {
                sendProgress(context, command.getName(), "skipped", "Skipped.");
            }
        }
    }

    private void sendProgress(CommandContext context, String step, String status, String message) {
        WebSocketSession session = context.getWebSocketSession();
        if (session != null && session.isOpen()) {
            try {
                Map<String, Object> progressUpdate = Map.of(
                        "type", "task_progress",
                        "step", step,
                        "status", status,
                        "log", message
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(progressUpdate)));
            } catch (IOException e) {
                log.error("Failed to send progress update to session {}: {}", session.getId(), e.getMessage(), e);
            }
        }
    }
}
