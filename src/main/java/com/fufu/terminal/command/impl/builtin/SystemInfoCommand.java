package com.fufu.terminal.command.impl.builtin;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.BuiltInScriptType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 系统信息查看命令
 * 内置原子脚本，不需要变量传递
 */
@Slf4j
@Component("system-info")
public class SystemInfoCommand implements AtomicScriptCommand, BuiltInScriptMetadata {

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("开始执行系统信息查看命令");
        
        try {
            String infoScript = """
                    #!/bin/bash
                    
                    echo "========== 系统信息 =========="
                    echo "操作系统: $(uname -s)"
                    echo "内核版本: $(uname -r)"
                    echo "架构: $(uname -m)"
                    echo "主机名: $(hostname)"
                    echo "系统运行时间: $(uptime -p 2>/dev/null || uptime)"
                    echo ""
                    
                    echo "========== 系统负载 =========="
                    echo "当前时间: $(date)"
                    echo "系统负载: $(uptime | awk -F'load average:' '{print $2}')"
                    echo ""
                    
                    echo "========== CPU 信息 =========="
                    if command -v lscpu >/dev/null 2>&1; then
                        lscpu | grep -E "Model name|CPU\\(s\\)|Thread|Core"
                    else
                        grep -E "model name|cpu cores|siblings" /proc/cpuinfo | head -6
                    fi
                    echo ""
                    
                    echo "========== 内存信息 =========="
                    free -h
                    echo ""
                    
                    echo "========== 磁盘信息 =========="
                    df -h | grep -vE "tmpfs|devtmpfs|udev"
                    echo ""
                    
                    echo "========== 网络接口 =========="
                    if command -v ip >/dev/null 2>&1; then
                        ip addr show | grep -E "inet.*scope global" | awk '{print $2, $NF}'
                    else
                        ifconfig | grep -E "inet.*netmask" | awk '{print $2, $NF}'
                    fi
                    echo ""
                    
                    echo "========== 进程统计 =========="
                    echo "总进程数: $(ps aux | wc -l)"
                    echo "运行中进程: $(ps aux | awk '$8 ~ /R/ {count++} END {print count+0}')"
                    echo "睡眠进程: $(ps aux | awk '$8 ~ /S/ {count++} END {print count+0}')"
                    echo ""
                    
                    echo "========== 系统服务 =========="
                    if command -v systemctl >/dev/null 2>&1; then
                        echo "运行中的服务数: $(systemctl list-units --type=service --state=running --no-pager --no-legend | wc -l)"
                        echo "失败的服务数: $(systemctl list-units --type=service --state=failed --no-pager --no-legend | wc -l)"
                    fi
                    
                    echo "========== 信息收集完成 =========="
                    """;
            
            CommandResult result = context.executeScript(infoScript);
            
            if (result.isSuccess()) {
                log.info("系统信息查看成功");
                return CommandResult.success("系统信息收集完成");
            } else {
                log.error("系统信息查看失败: {}", result.getErrorMessage());
                return CommandResult.failure("系统信息查看失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            log.error("执行系统信息查看命令异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "系统信息查看";
    }

    @Override
    public String getDescription() {
        return "查看系统基本信息，包括CPU、内存、磁盘、网络等";
    }

    // BuiltInScriptMetadata 接口实现
    @Override
    public String getScriptId() {
        return "system-info";
    }

    @Override
    public BuiltInScriptType getType() {
        return BuiltInScriptType.STATIC;
    }

    @Override
    public List<ScriptParameter> getParameters() {
        return Collections.emptyList(); // 静态脚本无参数
    }

    @Override
    public String[] getTags() {
        return new String[]{"系统", "监控", "诊断", "信息收集"};
    }
}