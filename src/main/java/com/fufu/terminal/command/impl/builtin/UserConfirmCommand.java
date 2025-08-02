package com.fufu.terminal.command.impl.builtin;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.BuiltInScriptType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 用户确认交互脚本
 * 示例交互内置脚本，用于需要用户确认的场景
 */
@Slf4j
@Component("user-confirm")
public class UserConfirmCommand implements AtomicScriptCommand, BuiltInScriptMetadata {

    @Override
    public CommandResult execute(CommandContext context) {
        log.info("开始执行用户确认交互脚本");
        
        try {
            // 获取交互参数
            String prompt = context.getVariable("confirm_prompt", String.class);
            if (prompt == null) {
                prompt = "是否继续执行？";
            }
            
            // 创建交互数据
            Map<String, Object> interactionData = Map.of(
                "type", "CONFIRMATION",
                "prompt", prompt,
                "options", Arrays.asList("是", "否"),
                "timeout", 30000 // 30秒超时
            );
            
            // 返回需要交互的结果
            CommandResult result = CommandResult.success("等待用户确认");
            result.setInteractionData(interactionData);
            result.setRequiresUserInteraction(true);
            
            return result;
            
        } catch (Exception e) {
            log.error("执行用户确认脚本异常", e);
            return CommandResult.failure("执行异常: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "用户确认";
    }

    @Override
    public String getDescription() {
        return "等待用户确认是否继续执行";
    }

    // BuiltInScriptMetadata 接口实现
    @Override
    public String getScriptId() {
        return "user-confirm";
    }

    @Override
    public BuiltInScriptType getType() {
        return BuiltInScriptType.INTERACTIVE;
    }

    @Override
    public List<ScriptParameter> getParameters() {
        return Arrays.asList(
            createParameter("confirm_prompt", ScriptParameter.ParameterType.STRING, 
                "确认提示信息", false, "是否继续执行？")
        );
    }

    private ScriptParameter createParameter(String name, ScriptParameter.ParameterType type, 
                                          String description, boolean required, Object defaultValue) {
        ScriptParameter param = new ScriptParameter();
        param.setName(name);
        param.setType(type);
        param.setDescription(description);
        param.setRequired(required);
        param.setDefaultValue(defaultValue);
        return param;
    }

    @Override
    public String[] getTags() {
        return new String[]{"交互", "确认", "用户输入"};
    }
}