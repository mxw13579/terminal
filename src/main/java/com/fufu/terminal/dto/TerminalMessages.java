package com.fufu.terminal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 终端相关消息DTO
 * 
 * @author lizelin
 */
public class TerminalMessages {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionRequest {
        private String host;
        private Integer port = 22;
        private String user;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerminalInput {
        private String data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerminalResize {
        private Integer cols;
        private Integer rows;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TerminalOutput {
        private String type = "terminal_data";
        private String payload;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ErrorMessage {
        private String type = "error";
        private String payload;
    }
}