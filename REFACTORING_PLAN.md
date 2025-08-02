# SSH终端管理系统 - 渐进式重构方案

## 🎯 重构目标与需求确认

### 核心需求审查
本重构方案旨在实现以下4种原子脚本类型的完整支持：

1. **✅ 静态内置脚本** - 无参数，有输出变量（如服务器地址检测）
2. **✅ 可配置内置脚本** - 有输入参数，智能判断（如Docker安装，根据地址切换镜像源）
3. **🔧 交互内置脚本** - 需要用户实时交互确认
4. **✅ 用户脚本** - 管理员在管理端配置的Shell脚本

### 核心特性确认
- ✅ **变量传递**: 脚本间传递变量（地址检测 → Docker安装）
- ✅ **智能判断**: 根据变量值自动做出决策（国内服务器自动推荐镜像源）
- ✅ **用户交互**: 实时用户确认和输入
- ✅ **自定义分组**: 管理员创建分组，用户端动态显示
- ✅ **混合管理**: 内置脚本代码管理，用户脚本配置管理

---

## 📋 重构计划概览

### Phase 1: 后端类型系统增强 (1天)
- 扩展 `ScriptSourceType` 添加交互类型
- 创建交互脚本执行策略
- 实现示例交互脚本

### Phase 2: 变量传递机制完善 (1天)  
- 增强 `CommandContext` 变量管理
- 更新内置脚本支持变量传递
- 完善 `AggregateAtomicRelation` 变量映射

### Phase 3: 前端交互增强 (1天)
- 扩展脚本类型支持
- 增强交互模态框
- 实现变量传递可视化

### Phase 4: 控制器层简化 (1天)
- 合并重复控制器
- 统一脚本执行接口
- 保留管理端控制器

### Phase 5: 用户脚本完善 (1天)
- 完善用户脚本执行策略
- 管理端界面增强
- 数据清理和测试

---

## 🔧 Phase 1: 后端类型系统增强

### 1.1 扩展 ScriptSourceType 枚举

**文件**: `src/main/java/com/fufu/terminal/service/script/strategy/ScriptSourceType.java`

**修改内容**:
```java
public enum ScriptSourceType {
    BUILT_IN_STATIC("内置静态脚本", "系统预定义的无参数脚本"),
    BUILT_IN_DYNAMIC("内置动态脚本", "系统预定义的参数化脚本"),
    BUILT_IN_INTERACTIVE("内置交互脚本", "需要用户实时交互的脚本"), // 新增
    USER_DEFINED("用户定义脚本", "用户自定义的数据库存储脚本");
    
    // 现有代码保持不变...
    
    /**
     * 判断是否需要用户交互
     */
    public boolean requiresInteraction() {
        return this == BUILT_IN_INTERACTIVE;
    }
}
```

### 1.2 扩展 BuiltInScriptType 枚举

**文件**: `src/main/java/com/fufu/terminal/service/script/strategy/BuiltInScriptType.java`

**修改内容**:
```java
public enum BuiltInScriptType {
    STATIC,    // 静态脚本 - 系统信息查看
    DYNAMIC,   // 动态脚本 - Docker安装等
    INTERACTIVE // 交互脚本 - 用户确认等 (新增)
}
```

### 1.3 创建交互脚本执行策略

**新建文件**: `src/main/java/com/fufu/terminal/service/script/strategy/impl/InteractiveBuiltInScriptStrategy.java`

**创建内容**:
```java
package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.command.AtomicScriptCommand;
import com.fufu.terminal.command.CommandResult;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.BuiltInScriptMetadata;
import com.fufu.terminal.service.script.strategy.ScriptExecutionStrategy;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.strategy.registry.ScriptTypeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 交互内置脚本执行策略
 * 处理需要用户实时交互的内置脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveBuiltInScriptStrategy implements ScriptExecutionStrategy {

    private final ScriptTypeRegistry scriptTypeRegistry;

    @Override
    public boolean canHandle(String scriptId, ScriptSourceType sourceType) {
        return ScriptSourceType.BUILT_IN_INTERACTIVE == sourceType && 
               scriptTypeRegistry.isInteractiveBuiltInScript(scriptId);
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        log.info("开始执行交互内置脚本: {}", request.getScriptId());
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 获取内置脚本命令
            AtomicScriptCommand command = scriptTypeRegistry.getBuiltInScriptCommand(request.getScriptId());
            if (command == null) {
                String errorMsg = "未找到内置脚本命令: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }

            // 执行交互脚本命令
            CommandResult commandResult = command.execute(request.getCommandContext());
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();

            if (commandResult.isSuccess()) {
                log.info("交互内置脚本执行成功: {}, 耗时: {}ms", request.getScriptId(), duration);
                
                ScriptExecutionResult result = new ScriptExecutionResult();
                result.setSuccess(true);
                result.setMessage("脚本执行成功");
                result.setStartTime(startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                result.setRequiresInteraction(true); // 标记需要交互
                result.setOutputData(Map.of(
                    "scriptId", request.getScriptId(),
                    "executionTime", duration,
                    "output", commandResult.getOutput(),
                    "interactionData", commandResult.getInteractionData() // 交互数据
                ));
                
                return result;
            } else {
                String errorMsg = "脚本执行失败: " + commandResult.getErrorMessage();
                log.error("交互内置脚本执行失败: {}, 错误: {}", request.getScriptId(), errorMsg);
                
                ScriptExecutionResult result = createFailureResult(errorMsg, startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                
                return result;
            }
            
        } catch (Exception e) {
            String errorMsg = "脚本执行异常: " + e.getMessage();
            log.error("交互内置脚本执行异常: {}", request.getScriptId(), e);
            return createFailureResult(errorMsg, startTime);
        }
    }

    @Override
    public List<ScriptParameter> getRequiredParameters(String scriptId) {
        BuiltInScriptMetadata metadata = scriptTypeRegistry.getBuiltInScriptMetadata(scriptId);
        return metadata != null ? metadata.getParameters() : Collections.emptyList();
    }

    @Override
    public ScriptSourceType getSupportedSourceType() {
        return ScriptSourceType.BUILT_IN_INTERACTIVE;
    }

    private ScriptExecutionResult createFailureResult(String errorMessage, LocalDateTime startTime) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setStartTime(startTime);
        result.setEndTime(LocalDateTime.now());
        result.setDuration(java.time.Duration.between(startTime, result.getEndTime()).toMillis());
        result.setDisplayToUser(true);
        
        return result;
    }
}
```

### 1.4 更新 ScriptTypeRegistry 支持交互脚本

**文件**: `src/main/java/com/fufu/terminal/service/script/strategy/registry/ScriptTypeRegistry.java`

**添加方法**:
```java
/**
 * 检查是否为交互内置脚本
 * 
 * @param scriptId 脚本ID
 * @return 是否为交互内置脚本
 */
public boolean isInteractiveBuiltInScript(String scriptId) {
    BuiltInScriptMetadata metadata = builtInScriptsMetadata.get(scriptId);
    return metadata != null && metadata.getType() == BuiltInScriptType.INTERACTIVE;
}
```

### 1.5 实现示例交互脚本

**新建文件**: `src/main/java/com/fufu/terminal/command/impl/builtin/UserConfirmCommand.java`

**创建内容**:
```java
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
```

---

## 🔧 Phase 2: 变量传递机制完善

### 2.1 增强 CommandContext 变量管理

**文件**: `src/main/java/com/fufu/terminal/command/CommandContext.java`

**添加变量管理方法** (如果不存在则添加):
```java
/**
 * 脚本间变量传递存储
 */
private Map<String, Object> scriptVariables = new ConcurrentHashMap<>();

/**
 * 设置脚本变量（用于脚本间传递）
 * @param name 变量名
 * @param value 变量值
 */
public void setScriptVariable(String name, Object value) {
    scriptVariables.put(name, value);
    log.info("设置脚本变量: {} = {}", name, value);
}

/**
 * 获取脚本变量
 * @param name 变量名
 * @param type 变量类型
 * @return 变量值
 */
public <T> T getScriptVariable(String name, Class<T> type) {
    Object value = scriptVariables.get(name);
    if (value == null) {
        log.warn("未找到脚本变量: {}", name);
        return null;
    }
    try {
        return type.cast(value);
    } catch (ClassCastException e) {
        log.error("脚本变量类型转换失败: {} -> {}", name, type.getSimpleName());
        return null;
    }
}

/**
 * 获取所有脚本变量
 * @return 变量映射表
 */
public Map<String, Object> getAllScriptVariables() {
    return new HashMap<>(scriptVariables);
}

/**
 * 清除脚本变量
 */
public void clearScriptVariables() {
    scriptVariables.clear();
    log.info("清除所有脚本变量");
}
```

### 2.2 增强 CommandResult 支持交互数据

**文件**: `src/main/java/com/fufu/terminal/command/CommandResult.java`

**添加交互相关字段** (如果不存在则添加):
```java
/**
 * 是否需要用户交互
 */
private boolean requiresUserInteraction = false;

/**
 * 交互数据（用于前端显示交互界面）
 */
private Map<String, Object> interactionData;

public boolean isRequiresUserInteraction() {
    return requiresUserInteraction;
}

public void setRequiresUserInteraction(boolean requiresUserInteraction) {
    this.requiresUserInteraction = requiresUserInteraction;
}

public Map<String, Object> getInteractionData() {
    return interactionData;
}

public void setInteractionData(Map<String, Object> interactionData) {
    this.interactionData = interactionData;
    this.requiresUserInteraction = true;
}
```

### 2.3 更新内置脚本支持变量传递

**文件**: `src/main/java/com/fufu/terminal/command/impl/builtin/SystemInfoCommand.java`

**修改 execute 方法添加变量输出**:
```java
@Override
public CommandResult execute(CommandContext context) {
    log.info("开始执行系统信息查看命令");
    
    try {
        // ... 现有执行逻辑保持不变 ...
        
        CommandResult result = context.executeScript(infoScript);
        
        if (result.isSuccess()) {
            // 新增：检测服务器地址和操作系统
            String serverLocation = detectServerLocation();
            String osType = detectOperatingSystem();
            
            // 设置输出变量供后续脚本使用
            context.setScriptVariable("SERVER_LOCATION", serverLocation);
            context.setScriptVariable("OS_TYPE", osType);
            
            log.info("系统信息查看成功，设置变量: SERVER_LOCATION={}, OS_TYPE={}", 
                serverLocation, osType);
            return CommandResult.success("系统信息收集完成，检测到服务器位置: " + serverLocation);
        } else {
            log.error("系统信息查看失败: {}", result.getErrorMessage());
            return CommandResult.failure("系统信息查看失败: " + result.getErrorMessage());
        }
        
    } catch (Exception e) {
        log.error("执行系统信息查看命令异常", e);
        return CommandResult.failure("执行异常: " + e.getMessage());
    }
}

/**
 * 检测服务器地址
 */
private String detectServerLocation() {
    try {
        // 通过IP地址检测服务器地理位置
        // 这里简化实现，实际可以调用IP地址库
        String publicIP = getPublicIP();
        if (isChineseIP(publicIP)) {
            return "China";
        }
        return "Global";
    } catch (Exception e) {
        log.warn("服务器地址检测失败，默认为Global", e);
        return "Global";
    }
}

/**
 * 检测操作系统类型
 */
private String detectOperatingSystem() {
    String osName = System.getProperty("os.name").toLowerCase();
    if (osName.contains("ubuntu")) return "Ubuntu";
    if (osName.contains("debian")) return "Debian";
    if (osName.contains("centos")) return "CentOS";
    if (osName.contains("redhat")) return "RedHat";
    return "Unknown";
}

// 辅助方法实现...
private String getPublicIP() {
    // 实现获取公网IP的逻辑
    return "127.0.0.1"; // 占位符
}

private boolean isChineseIP(String ip) {
    // 实现判断是否为中国IP的逻辑
    return false; // 占位符
}
```

**文件**: `src/main/java/com/fufu/terminal/command/impl/builtin/DockerInstallCommand.java`

**修改 execute 方法使用输入变量**:
```java
@Override
public CommandResult execute(CommandContext context) {
    log.info("开始执行 Docker 安装命令");

    try {
        // 获取参数
        String registryMirror = context.getVariable("registry_mirror", String.class);
        Boolean installCompose = context.getVariable("install_compose", Boolean.class);
        Boolean enableNonRootAccess = context.getVariable("enable_non_root_access", Boolean.class);

        // 新增：获取前置脚本传递的变量
        String serverLocation = context.getScriptVariable("SERVER_LOCATION", String.class);
        String osType = context.getScriptVariable("OS_TYPE", String.class);
        
        log.info("检测到服务器位置: {}, 操作系统: {}", serverLocation, osType);

        SystemType systemType = context.getSystemType();

        // 智能选择镜像源
        if (registryMirror == null || "default".equals(registryMirror)) {
            if ("China".equals(serverLocation)) {
                registryMirror = "https://mirror.aliyun.com/docker-ce";
                log.info("检测到中国服务器，自动使用阿里云镜像源");
            }
        }

        // 根据系统类型和参数生成安装脚本
        String installScript = generateInstallScript(systemType, registryMirror, installCompose, enableNonRootAccess);

        // 执行脚本
        CommandResult result = context.executeScript(installScript);

        if (result.isSuccess()) {
            log.info("Docker 安装成功");
            // 设置输出变量
            context.setScriptVariable("DOCKER_INSTALLED", true);
            context.setScriptVariable("DOCKER_MIRROR", registryMirror);
            return CommandResult.success("Docker 安装成功，使用镜像源: " + registryMirror);
        } else {
            log.error("Docker 安装失败: {}", result.getErrorMessage());
            return CommandResult.failure("Docker 安装失败: " + result.getErrorMessage());
        }

    } catch (Exception e) {
        log.error("执行 Docker 安装命令异常", e);
        return CommandResult.failure("执行异常: " + e.getMessage());
    }
}
```

### 2.4 增强 ScriptExecutionResult 支持交互

**文件**: `src/main/java/com/fufu/terminal/service/script/ScriptExecutionResult.java`

**添加交互相关字段** (如果不存在则添加):
```java
/**
 * 是否需要用户交互
 */
private boolean requiresInteraction = false;

/**
 * 交互数据
 */
private Map<String, Object> interactionData;

public boolean isRequiresInteraction() {
    return requiresInteraction;
}

public void setRequiresInteraction(boolean requiresInteraction) {
    this.requiresInteraction = requiresInteraction;
}

public Map<String, Object> getInteractionData() {
    return interactionData;
}

public void setInteractionData(Map<String, Object> interactionData) {
    this.interactionData = interactionData;
}
```

---

## 🔧 Phase 3: 前端交互增强

### 3.1 扩展前端脚本类型支持

**文件**: `web/ssh-treminal-ui/src/views/user/ScriptExecution.vue`

**修改脚本类型处理逻辑**:

在 `<script setup>` 部分添加交互脚本支持:
```javascript
// 新增：处理交互脚本类型
const getScriptTypeText = (sourceType) => {
  const typeMap = {
    'BUILT_IN_STATIC': '静态',
    'BUILT_IN_DYNAMIC': '动态',
    'BUILT_IN_INTERACTIVE': '交互', // 新增
    'USER_DEFINED': '自定义'
  }
  return typeMap[sourceType] || '未知'
}

const getScriptTypeColor = (sourceType) => {
  const colorMap = {
    'BUILT_IN_STATIC': 'success',
    'BUILT_IN_DYNAMIC': 'warning', 
    'BUILT_IN_INTERACTIVE': 'danger', // 新增，用红色表示需要交互
    'USER_DEFINED': 'info'
  }
  return colorMap[sourceType] || 'info'
}

// 新增：交互脚本执行方法
const executeInteractiveScript = async (script) => {
  if (isExecuting.value) return

  try {
    isExecuting.value = true
    executionLogs.value = []

    // 使用新的统一API
    const response = await http.post(`/api/user/scripts/execute/${script.id}`, {
      sshConfig: getSshConfig(),
      parameters: {},
      async: true, // 交互脚本需要异步执行
      userId: getCurrentUserId(),
      sessionId: generateSessionId()
    })
    
    currentExecution.value = response.data
    
    // 检查是否需要交互
    if (response.data.requiresInteraction) {
      // 显示交互界面
      interactionRequest.value = response.data.interactionData
    }
    
    // 连接WebSocket监听执行状态
    connectToExecutionLogs(response.data.id)
    
    ElMessage.success('交互脚本开始执行')
    
  } catch (error) {
    console.error('交互脚本执行失败:', error)
    ElMessage.error('脚本执行失败')
    isExecuting.value = false
  }
}
```

**修改模板部分添加交互脚本按钮**:
```vue
<!-- 在现有按钮组中添加交互脚本按钮 -->
<!-- 交互脚本：交互执行按钮 -->
<el-button
  v-else-if="selectedScript?.id === script.id && script.sourceType === 'BUILT_IN_INTERACTIVE'"
  type="danger"
  size="small"
  @click.stop="executeInteractiveScript(script)"
  :loading="isExecuting"
>
  {{ isExecuting ? '执行中...' : '交互执行' }}
</el-button>
```

### 3.2 增强交互模态框

**文件**: `web/ssh-treminal-ui/src/components/InteractionModal.vue`

**完全替换文件内容**:
```vue
<template>
  <el-dialog
    v-model="dialogVisible"
    :title="getDialogTitle()"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    width="500px"
    :before-close="handleClose"
  >
    <!-- 确认类型交互 -->
    <div v-if="interactionRequest.type === 'CONFIRMATION'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#E6A23C"><Warning /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <!-- 显示相关变量信息 -->
        <div v-if="interactionRequest.contextInfo" class="context-info">
          <el-tag 
            v-for="(value, key) in interactionRequest.contextInfo" 
            :key="key" 
            size="small" 
            class="context-tag"
          >
            {{ key }}: {{ value }}
          </el-tag>
        </div>
      </div>
    </div>
    
    <!-- 文本输入类型交互 -->
    <div v-else-if="interactionRequest.type === 'TEXT_INPUT'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#409EFF"><Edit /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <el-input 
          v-model="responseText" 
          :placeholder="interactionRequest.placeholder || '请输入内容'"
          maxlength="200"
          show-word-limit
          class="input-field"
        />
      </div>
    </div>
    
    <!-- 密码输入类型交互 -->
    <div v-else-if="interactionRequest.type === 'PASSWORD'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#F56C6C"><Lock /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <el-input 
          v-model="responseText" 
          :placeholder="interactionRequest.placeholder || '请输入密码'"
          type="password" 
          show-password
          class="input-field"
        />
      </div>
    </div>

    <!-- 智能推荐类型交互 -->
    <div v-else-if="interactionRequest.type === 'SMART_RECOMMENDATION'" class="interaction-content">
      <div class="interaction-icon">
        <el-icon size="48" color="#67C23A"><Star /></el-icon>
      </div>
      <div class="interaction-message">
        <p>{{ interactionRequest.prompt }}</p>
        <div class="recommendation-info">
          <el-alert
            :title="interactionRequest.recommendationTitle"
            :description="interactionRequest.recommendationDesc"
            type="success"
            :closable="false"
            show-icon
          />
        </div>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <!-- 确认类型按钮 -->
        <template v-if="interactionRequest.type === 'CONFIRMATION' || interactionRequest.type === 'SMART_RECOMMENDATION'">
          <el-button @click="handleResponse(false)" :disabled="responseLoading">
            {{ interactionRequest.cancelText || '否' }}
          </el-button>
          <el-button type="primary" @click="handleResponse(true)" :loading="responseLoading">
            {{ interactionRequest.confirmText || '是' }}
          </el-button>
        </template>
        
        <!-- 输入类型按钮 -->
        <template v-else>
          <el-button @click="handleResponse(null)" :disabled="responseLoading">取消</el-button>
          <el-button 
            type="primary" 
            @click="handleResponse(responseText)" 
            :loading="responseLoading"
            :disabled="!responseText.trim()"
          >
            提交
          </el-button>
        </template>
        
        <!-- 超时提示 -->
        <div v-if="timeoutSeconds > 0" class="timeout-info">
          <el-text size="small" type="warning">
            {{ timeoutSeconds }}秒后超时
          </el-text>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, defineProps, defineEmits, onMounted, onUnmounted } from 'vue'
import { ElDialog, ElButton, ElInput, ElIcon, ElTag, ElAlert, ElText } from 'element-plus'
import { Warning, Edit, Lock, Star } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Object,
    default: null
  }
})

const emit = defineEmits(['update:modelValue', 'submit'])

const dialogVisible = ref(false)
const interactionRequest = ref({})
const responseText = ref('')
const responseLoading = ref(false)
const timeoutSeconds = ref(0)

let timeoutTimer = null
let countdownTimer = null

// 监听交互请求变化
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    interactionRequest.value = newVal
    dialogVisible.value = true
    responseText.value = ''
    
    // 启动超时计时器
    if (newVal.timeout) {
      startTimeout(newVal.timeout)
    }
  } else {
    dialogVisible.value = false
    clearTimers()
  }
}, { immediate: true })

// 获取对话框标题
const getDialogTitle = () => {
  const titleMap = {
    'CONFIRMATION': '确认执行',
    'TEXT_INPUT': '输入信息', 
    'PASSWORD': '密码输入',
    'SMART_RECOMMENDATION': '智能推荐'
  }
  return titleMap[interactionRequest.value.type] || '用户交互'
}

// 处理用户响应
const handleResponse = async (response) => {
  responseLoading.value = true
  
  try {
    const responseData = {
      requestId: interactionRequest.value.id,
      response: response,
      timestamp: new Date().toISOString()
    }
    
    emit('submit', responseData)
    
    // 关闭对话框
    emit('update:modelValue', null)
    
  } catch (error) {
    console.error('提交交互响应失败:', error)
  } finally {
    responseLoading.value = false
  }
}

// 处理对话框关闭
const handleClose = (done) => {
  // 交互对话框不允许直接关闭，必须响应
  return false
}

// 启动超时计时器
const startTimeout = (timeout) => {
  timeoutSeconds.value = Math.floor(timeout / 1000)
  
  // 超时处理
  timeoutTimer = setTimeout(() => {
    handleResponse(null) // 超时自动取消
  }, timeout)
  
  // 倒计时显示
  countdownTimer = setInterval(() => {
    timeoutSeconds.value--
    if (timeoutSeconds.value <= 0) {
      clearInterval(countdownTimer)
    }
  }, 1000)
}

// 清除计时器
const clearTimers = () => {
  if (timeoutTimer) {
    clearTimeout(timeoutTimer)
    timeoutTimer = null
  }
  if (countdownTimer) {
    clearInterval(countdownTimer)
    countdownTimer = null
  }
  timeoutSeconds.value = 0
}

onUnmounted(() => {
  clearTimers()
})
</script>

<style scoped>
.interaction-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  padding: 20px 0;
}

.interaction-icon {
  margin-bottom: 16px;
}

.interaction-message {
  width: 100%;
}

.interaction-message p {
  font-size: 16px;
  color: #606266;
  margin-bottom: 16px;
  line-height: 1.5;
}

.context-info {
  margin: 12px 0;
}

.context-tag {
  margin: 4px;
}

.input-field {
  margin-top: 12px;
}

.recommendation-info {
  margin: 16px 0;
  text-align: left;
}

.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.timeout-info {
  flex: 1;
  text-align: left;
}
</style>
```

### 3.3 实现变量传递可视化

**文件**: `web/ssh-treminal-ui/src/views/user/ScriptExecution.vue`

**在日志容器上方添加变量显示区域**:

在模板中添加变量显示组件:
```vue
<!-- 在执行日志上方添加变量传递可视化 -->
<div class="variables-section" v-if="scriptVariables && Object.keys(scriptVariables).length > 0">
  <h4>脚本变量</h4>
  <div class="variables-grid">
    <div 
      v-for="(value, key) in scriptVariables" 
      :key="key" 
      class="variable-item"
    >
      <el-tag size="small" type="info">{{ key }}</el-tag>
      <span class="variable-value">{{ formatVariableValue(value) }}</span>
    </div>
  </div>
</div>
```

在脚本部分添加变量处理逻辑:
```javascript
// 添加脚本变量状态
const scriptVariables = ref({})

// 格式化变量值显示
const formatVariableValue = (value) => {
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

// 在WebSocket消息处理中添加变量更新
const connectToExecutionLogs = (executionId) => {
  const ws = connectWebSocket('/ws/stomp')
  
  ws.onConnect = () => {
    // 现有订阅...
    
    // 新增：订阅变量更新
    ws.subscribe(`/topic/execution/${executionId}/variables`, (variables) => {
      scriptVariables.value = variables
    })
  }
}
```

添加对应的CSS样式:
```css
.variables-section {
  background: #f8f9fa;
  border: 1px solid #e9ecef;
  border-radius: 6px;
  padding: 15px;
  margin-bottom: 15px;
}

.variables-section h4 {
  margin: 0 0 10px 0;
  color: #495057;
  font-size: 14px;
}

.variables-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.variable-item {
  display: flex;
  align-items: center;
  gap: 6px;
  background: white;
  padding: 4px 8px;
  border-radius: 4px;
  border: 1px solid #dee2e6;
}

.variable-value {
  font-family: 'Courier New', monospace;
  font-size: 12px;
  color: #6c757d;
}
```

---

## 🔧 Phase 4: 控制器层简化

### 4.1 创建统一脚本控制器

**新建文件**: `src/main/java/com/fufu/terminal/controller/user/UnifiedScriptController.java`

**创建内容**:
```java
package com.fufu.terminal.controller.user;

import com.fufu.terminal.entity.ScriptGroup;
import com.fufu.terminal.entity.ExecutionLog;
import com.fufu.terminal.service.ScriptGroupService;
import com.fufu.terminal.service.script.strategy.router.ScriptExecutionStrategyRouter;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.command.CommandContext;
import com.fufu.terminal.command.ProductionCommandContext;
import com.fufu.terminal.command.model.SshConnectionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 统一脚本控制器
 * 合并原有的多个脚本执行控制器功能
 */
@Slf4j
@RestController
@RequestMapping("/api/user/scripts")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UnifiedScriptController {
    
    private final ScriptGroupService scriptGroupService;
    private final ScriptExecutionStrategyRouter strategyRouter;
    
    /**
     * 获取脚本分组列表
     */
    @GetMapping("/groups")
    public ResponseEntity<List<ScriptGroup>> getScriptGroups() {
        try {
            List<ScriptGroup> groups = scriptGroupService.findAllActiveGroups();
            return ResponseEntity.ok(groups);
        } catch (Exception e) {
            log.error("获取脚本分组失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取分组内的脚本列表
     */
    @GetMapping("/groups/{groupId}/scripts")
    public ResponseEntity<List<Object>> getGroupScripts(@PathVariable Long groupId) {
        try {
            // 获取分组内的聚合脚本列表
            List<Object> scripts = scriptGroupService.getGroupScripts(groupId);
            return ResponseEntity.ok(scripts);
        } catch (Exception e) {
            log.error("获取分组脚本失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 统一脚本执行接口
     * 支持所有4种脚本类型：BUILT_IN_STATIC, BUILT_IN_DYNAMIC, BUILT_IN_INTERACTIVE, USER_DEFINED
     */
    @PostMapping("/execute/{scriptId}")
    public ResponseEntity<ScriptExecutionResult> executeScript(
            @PathVariable String scriptId,
            @RequestBody ScriptExecutionRequestDto requestDto) {
        
        log.info("开始执行脚本: {}, 类型: {}", scriptId, requestDto.getScriptType());
        
        try {
            // 创建命令上下文
            CommandContext context = createCommandContext(requestDto);
            
            // 创建脚本执行请求
            ScriptExecutionRequest request = ScriptExecutionRequest.builder()
                .scriptId(scriptId)
                .sourceType(requestDto.getScriptType())
                .parameters(requestDto.getParameters())
                .commandContext(context)
                .async(requestDto.isAsync())
                .userId(requestDto.getUserId())
                .sessionId(requestDto.getSessionId())
                .build();
                
            // 使用策略路由器执行脚本
            ScriptExecutionResult result = strategyRouter.executeScript(request);
            
            log.info("脚本执行完成: {}, 成功: {}", scriptId, result.isSuccess());
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            log.error("脚本执行失败: {}", scriptId, e);
            
            ScriptExecutionResult errorResult = new ScriptExecutionResult();
            errorResult.setSuccess(false);
            errorResult.setErrorMessage("脚本执行异常: " + e.getMessage());
            
            return ResponseEntity.ok(errorResult);
        }
    }
    
    /**
     * 获取脚本参数要求
     */
    @GetMapping("/parameters/{scriptId}")
    public ResponseEntity<List<ScriptParameter>> getScriptParameters(@PathVariable String scriptId) {
        try {
            List<ScriptParameter> parameters = strategyRouter.getRequiredParameters(scriptId);
            return ResponseEntity.ok(parameters);
        } catch (Exception e) {
            log.error("获取脚本参数失败: {}", scriptId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 获取执行日志
     */
    @GetMapping("/execution/{executionId}/logs")
    public ResponseEntity<List<ExecutionLog>> getExecutionLogs(@PathVariable String executionId) {
        try {
            // 获取执行日志的逻辑
            List<ExecutionLog> logs = getLogsFromService(executionId);
            return ResponseEntity.ok(logs);
        } catch (Exception e) {
            log.error("获取执行日志失败: {}", executionId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 提交交互响应
     */
    @PostMapping("/interaction/respond")
    public ResponseEntity<Void> submitInteractionResponse(@RequestBody InteractionResponseDto responseDto) {
        try {
            log.info("收到交互响应: requestId={}, response={}", 
                responseDto.getRequestId(), responseDto.getResponse());
            
            // 处理交互响应的逻辑
            handleInteractionResponse(responseDto);
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("处理交互响应失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    // 辅助方法
    private CommandContext createCommandContext(ScriptExecutionRequestDto requestDto) {
        SshConnectionConfig sshConfig = requestDto.getSshConfig();
        return new ProductionCommandContext(sshConfig);
    }
    
    private List<ExecutionLog> getLogsFromService(String executionId) {
        // 实现获取日志的逻辑
        return List.of(); // 占位符
    }
    
    private void handleInteractionResponse(InteractionResponseDto responseDto) {
        // 实现交互响应处理逻辑
    }
    
    // DTO 类定义
    @Data
    public static class ScriptExecutionRequestDto {
        private ScriptSourceType scriptType;
        private Map<String, Object> parameters;
        private SshConnectionConfig sshConfig;
        private boolean async = false;
        private String userId;
        private String sessionId;
    }
    
    @Data
    public static class InteractionResponseDto {
        private String requestId;
        private Object response;
        private String timestamp;
    }
}
```

### 4.2 删除冗余控制器

**删除以下控制器文件**:
- `src/main/java/com/fufu/terminal/controller/user/UserScriptExecutionController.java`
- `src/main/java/com/fufu/terminal/controller/user/SimplifiedScriptExecutionController.java`
- `src/main/java/com/fufu/terminal/controller/user/InteractiveScriptExecutionController.java`
- `src/main/java/com/fufu/terminal/controller/user/ScriptExecutionController.java`

**保留以下控制器**:
- ✅ `UnifiedScriptController.java` (新创建的统一控制器)
- ✅ `UserScriptGroupController.java` (脚本分组管理)
- ✅ `SshConnectionController.java` (SSH连接管理)

---

## 🔧 Phase 5: 用户脚本完善

### 5.1 完善用户脚本执行策略

**文件**: `src/main/java/com/fufu/terminal/service/script/strategy/impl/UserDefinedScriptStrategy.java`

**如果文件不存在则创建，如果存在则更新**:
```java
package com.fufu.terminal.service.script.strategy.impl;

import com.fufu.terminal.entity.AtomicScript;
import com.fufu.terminal.service.AtomicScriptService;
import com.fufu.terminal.service.script.ScriptExecutionResult;
import com.fufu.terminal.service.script.ScriptParameter;
import com.fufu.terminal.service.script.strategy.ScriptExecutionStrategy;
import com.fufu.terminal.service.script.strategy.ScriptSourceType;
import com.fufu.terminal.service.script.strategy.model.ScriptExecutionRequest;
import com.fufu.terminal.command.CommandResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * 用户定义脚本执行策略
 * 处理管理员在管理端配置的用户自定义脚本
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDefinedScriptStrategy implements ScriptExecutionStrategy {

    private final AtomicScriptService atomicScriptService;
    
    // 参数替换模式：${参数名}
    private static final Pattern PARAMETER_PATTERN = Pattern.compile("\\$\\{([^}]+)\\}");

    @Override
    public boolean canHandle(String scriptId, ScriptSourceType sourceType) {
        return ScriptSourceType.USER_DEFINED == sourceType;
    }

    @Override
    public ScriptExecutionResult execute(ScriptExecutionRequest request) {
        log.info("开始执行用户定义脚本: {}", request.getScriptId());
        
        LocalDateTime startTime = LocalDateTime.now();
        
        try {
            // 从数据库加载用户脚本
            AtomicScript userScript = atomicScriptService.getById(Long.parseLong(request.getScriptId()));
            if (userScript == null) {
                String errorMsg = "未找到用户脚本: " + request.getScriptId();
                log.error(errorMsg);
                return createFailureResult(errorMsg, startTime);
            }
            
            // 处理脚本参数替换
            String processedScript = processScriptParameters(
                userScript.getScriptContent(), 
                request.getParameters(),
                request.getCommandContext().getAllScriptVariables()
            );
            
            log.info("用户脚本参数处理完成，准备执行");
            log.debug("处理后的脚本内容: {}", processedScript);
            
            // 执行处理后的脚本
            CommandResult commandResult = request.getCommandContext().executeScript(processedScript);
            
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            
            if (commandResult.isSuccess()) {
                log.info("用户脚本执行成功: {}, 耗时: {}ms", request.getScriptId(), duration);
                
                ScriptExecutionResult result = new ScriptExecutionResult();
                result.setSuccess(true);
                result.setMessage("用户脚本执行成功");
                result.setStartTime(startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                result.setOutputData(Map.of(
                    "scriptId", request.getScriptId(),
                    "scriptName", userScript.getName(),
                    "executionTime", duration,
                    "output", commandResult.getOutput()
                ));
                
                return result;
            } else {
                String errorMsg = "用户脚本执行失败: " + commandResult.getErrorMessage();
                log.error("用户脚本执行失败: {}, 错误: {}", request.getScriptId(), errorMsg);
                
                ScriptExecutionResult result = createFailureResult(errorMsg, startTime);
                result.setEndTime(endTime);
                result.setDuration(duration);
                result.setDisplayOutput(commandResult.getOutput());
                result.setDisplayToUser(true);
                
                return result;
            }
            
        } catch (Exception e) {
            String errorMsg = "用户脚本执行异常: " + e.getMessage();
            log.error("用户脚本执行异常: {}", request.getScriptId(), e);
            return createFailureResult(errorMsg, startTime);
        }
    }

    @Override
    public List<ScriptParameter> getRequiredParameters(String scriptId) {
        try {
            AtomicScript userScript = atomicScriptService.getById(Long.parseLong(scriptId));
            if (userScript != null && userScript.getInputVariables() != null) {
                // 解析输入变量JSON为ScriptParameter列表
                return parseInputVariables(userScript.getInputVariables());
            }
        } catch (Exception e) {
            log.error("获取用户脚本参数失败: {}", scriptId, e);
        }
        return List.of();
    }

    @Override
    public ScriptSourceType getSupportedSourceType() {
        return ScriptSourceType.USER_DEFINED;
    }
    
    /**
     * 处理脚本参数替换
     * 支持两种参数：
     * 1. 用户输入参数：${mysql_port}
     * 2. 脚本变量：${SERVER_LOCATION}
     */
    private String processScriptParameters(String scriptContent, 
                                         Map<String, Object> parameters,
                                         Map<String, Object> scriptVariables) {
        String result = scriptContent;
        
        Matcher matcher = PARAMETER_PATTERN.matcher(scriptContent);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String placeholder = "${" + paramName + "}";
            
            Object value = null;
            
            // 优先从用户参数中查找
            if (parameters != null && parameters.containsKey(paramName)) {
                value = parameters.get(paramName);
            }
            // 其次从脚本变量中查找
            else if (scriptVariables != null && scriptVariables.containsKey(paramName)) {
                value = scriptVariables.get(paramName);
            }
            
            if (value != null) {
                result = result.replace(placeholder, String.valueOf(value));
                log.debug("替换参数: {} -> {}", placeholder, value);
            } else {
                log.warn("未找到参数值: {}", paramName);
            }
        }
        
        return result;
    }
    
    /**
     * 解析输入变量JSON为ScriptParameter列表
     */
    private List<ScriptParameter> parseInputVariables(String inputVariablesJson) {
        // 实现JSON解析逻辑
        // 这里简化实现，实际应该使用JSON库解析
        return List.of(); // 占位符
    }
    
    private ScriptExecutionResult createFailureResult(String errorMessage, LocalDateTime startTime) {
        ScriptExecutionResult result = new ScriptExecutionResult();
        result.setSuccess(false);
        result.setErrorMessage(errorMessage);
        result.setStartTime(startTime);
        result.setEndTime(LocalDateTime.now());
        result.setDuration(java.time.Duration.between(startTime, result.getEndTime()).toMillis());
        result.setDisplayToUser(true);
        
        return result;
    }
}
```

### 5.2 管理端用户脚本界面增强

**文件**: `web/ssh-treminal-ui/src/views/admin/AtomicScripts.vue`

**在脚本类型标签中添加交互类型支持**:

在 `switchTab` 方法中添加交互脚本过滤:
```javascript
const switchTab = (tab) => {
  currentTab.value = tab
  switch (tab) {
    case 'all':
      filteredScripts.value = scripts.value
      break
    case 'builtin-no-vars':
      filteredScripts.value = scripts.value.filter(
        script => script.scriptType === 'BUILT_IN_STATIC'
      )
      break
    case 'builtin-with-vars':
      filteredScripts.value = scripts.value.filter(
        script => script.scriptType === 'BUILT_IN_DYNAMIC'
      )
      break
    case 'builtin-interactive': // 新增交互脚本过滤
      filteredScripts.value = scripts.value.filter(
        script => script.scriptType === 'BUILT_IN_INTERACTIVE'
      )
      break
    case 'user':
      filteredScripts.value = scripts.value.filter(
        script => script.scriptType === 'USER_DEFINED'
      )
      break
  }
}
```

在模板中添加交互脚本标签:
```vue
<button 
  :class="['tab-btn', { active: currentTab === 'builtin-interactive' }]"
  @click="switchTab('builtin-interactive')"
>
  内置脚本(交互)
</button>
```

### 5.3 数据清理和初始化

**执行数据库清理脚本**:
```sql
-- 清理数据库中的内置脚本记录
DELETE FROM atomic_scripts WHERE name IN (
    'system-info', 'docker-install', 'mysql-install', 'redis-install', 'user-confirm'
);

-- 确保内置脚本完全通过代码管理
-- 可以保留用户创建的脚本记录

-- 验证清理结果
SELECT COUNT(*) as remaining_builtin_scripts 
FROM atomic_scripts 
WHERE script_type_enum IN ('BUILT_IN_STATIC', 'BUILT_IN_DYNAMIC', 'BUILT_IN_INTERACTIVE');
-- 应该返回 0
```

---

## 🧪 测试验证计划

### 测试用例1: 静态脚本测试
```bash
# 测试系统信息查看脚本
curl -X POST http://localhost:8080/api/user/scripts/execute/system-info \
  -H "Content-Type: application/json" \
  -d '{
    "scriptType": "BUILT_IN_STATIC",
    "parameters": {},
    "sshConfig": {...}
  }'

# 验证：
# 1. 脚本成功执行
# 2. 返回系统信息
# 3. 设置 SERVER_LOCATION 和 OS_TYPE 变量
```

### 测试用例2: 变量传递测试
```bash
# 测试Docker安装脚本（使用系统信息脚本的变量）
curl -X POST http://localhost:8080/api/user/scripts/execute/docker-install \
  -H "Content-Type: application/json" \
  -d '{
    "scriptType": "BUILT_IN_DYNAMIC",
    "parameters": {
      "auto_mirror": true
    },
    "sshConfig": {...}
  }'

# 验证：
# 1. 能读取 SERVER_LOCATION 变量
# 2. 中国服务器自动使用阿里云镜像
# 3. 全球服务器使用默认镜像
```

### 测试用例3: 交互脚本测试
```bash
# 测试用户确认脚本
curl -X POST http://localhost:8080/api/user/scripts/execute/user-confirm \
  -H "Content-Type: application/json" \
  -d '{
    "scriptType": "BUILT_IN_INTERACTIVE",
    "parameters": {
      "confirm_prompt": "是否继续安装MySQL？"
    },
    "sshConfig": {...}
  }'

# 验证：
# 1. 返回交互数据
# 2. 前端显示确认对话框
# 3. 用户响应后继续执行
```

### 测试用例4: 用户脚本测试
```bash
# 测试用户自定义脚本（参数替换）
curl -X POST http://localhost:8080/api/user/scripts/execute/123 \
  -H "Content-Type: application/json" \
  -d '{
    "scriptType": "USER_DEFINED", 
    "parameters": {
      "mysql_port": 3306,
      "mysql_password": "secret"
    },
    "sshConfig": {...}
  }'

# 验证：
# 1. 正确替换脚本中的 ${mysql_port} 和 ${mysql_password}
# 2. 可以使用脚本变量 ${SERVER_LOCATION}
# 3. 成功执行用户脚本
```

---

## 📋 实施检查清单

### Phase 1 检查项
- [ ] `ScriptSourceType` 添加 `BUILT_IN_INTERACTIVE`
- [ ] `BuiltInScriptType` 添加 `INTERACTIVE`
- [ ] 创建 `InteractiveBuiltInScriptStrategy`
- [ ] 更新 `ScriptTypeRegistry` 支持交互脚本
- [ ] 实现 `UserConfirmCommand` 示例脚本

### Phase 2 检查项
- [ ] `CommandContext` 添加变量管理方法
- [ ] `CommandResult` 添加交互数据支持
- [ ] `SystemInfoCommand` 添加变量输出
- [ ] `DockerInstallCommand` 添加变量输入和智能判断
- [ ] `ScriptExecutionResult` 添加交互支持

### Phase 3 检查项  
- [ ] 前端添加 `BUILT_IN_INTERACTIVE` 类型支持
- [ ] 增强 `InteractionModal` 组件功能
- [ ] 实现变量传递可视化
- [ ] 添加智能推荐交互界面
- [ ] WebSocket支持交互消息

### Phase 4 检查项
- [ ] 创建 `UnifiedScriptController`
- [ ] 删除冗余控制器文件
- [ ] 统一脚本执行接口
- [ ] 交互响应处理接口

### Phase 5 检查项
- [ ] 完善 `UserDefinedScriptStrategy`
- [ ] 管理端界面添加交互脚本支持
- [ ] 数据库清理内置脚本记录
- [ ] 完成所有测试用例验证

---

## 🚀 部署与发布

### 开发环境验证
1. 启动后端服务：`mvn spring-boot:run`
2. 启动前端服务：`npm run dev`
3. 运行所有测试用例
4. 验证4种脚本类型功能

### 生产环境部署
1. 执行数据库清理脚本
2. 构建生产版本：`mvn clean package`
3. 部署新版本
4. 验证核心功能正常

---

## 📝 重构完成标准

当以下所有功能都能正常工作时，重构即为完成：

1. ✅ **4种脚本类型完整支持**
   - 静态内置脚本（系统信息查看）
   - 可配置内置脚本（Docker安装，智能镜像源）
   - 交互内置脚本（用户确认）
   - 用户脚本（管理端配置）

2. ✅ **变量传递机制**
   - 脚本间变量传递（地址检测 → Docker安装）
   - 前端变量可视化显示

3. ✅ **智能交互功能**
   - 地域检测自动推荐镜像源
   - 实时用户确认对话框
   - 参数输入和验证

4. ✅ **管理端功能**
   - 用户脚本创建和编辑
   - 脚本分组管理
   - 聚合脚本配置

5. ✅ **用户端功能**
   - 动态脚本分组显示
   - 一键脚本执行
   - 实时日志查看

---

## 🎯 重构成功指标

- **代码量减少**: 从50+类减少到30+类
- **功能完整性**: 满足所有4种脚本类型需求
- **用户体验**: 智能推荐和实时交互
- **维护性**: 清晰的代码结构和文档
- **扩展性**: 易于添加新的脚本类型

---

*本重构方案确保Claude Code可以按照此文档逐步实施所有修改，每个阶段都有明确的文件路径、代码内容和验证标准。*