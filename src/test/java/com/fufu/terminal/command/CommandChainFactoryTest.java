package com.fufu.terminal.command;

import com.fufu.terminal.command.impl.preprocess.DetectOsCommand;
import com.fufu.terminal.command.impl.preprocess.DetectLocationCommand;
import com.fufu.terminal.command.impl.environment.CheckGitCommand;
import com.fufu.terminal.command.impl.environment.CheckDockerCommand;
import com.fufu.terminal.command.impl.environment.CheckCurlCommand;
import com.fufu.terminal.command.impl.environment.CheckUnzipCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureSystemMirrorsCommand;
import com.fufu.terminal.command.impl.enhancement.ConfigureDockerMirrorCommand;
import com.fufu.terminal.model.SshConnection;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CommandChainFactory 单元测试
 * 测试命令链工厂的各种任务类型构建功能
 */
@Slf4j
class CommandChainFactoryTest {

    private SshConnection sshConnection;
    private CommandContext context;
    private CommandChainFactory factory;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        context = new CommandContext(sshConnection, null);
        factory = new CommandChainFactory();
    }

    /**
     * 测试完整环境设置命令链构建
     */
    @Test
    void testCreateCommandChain_FullSetup() {
        // Act
        CommandChain chain = factory.createCommandChain("full_setup", context);

        // Assert
        assertNotNull(chain);
        List<Command> commands = chain.getCommands();
        assertNotNull(commands);
        assertEquals(8, commands.size()); // 2个前置 + 4个环境检查 + 2个增强

        // 验证命令顺序和类型
        assertTrue(commands.get(0) instanceof DetectOsCommand);
        assertTrue(commands.get(1) instanceof DetectLocationCommand);
        assertTrue(commands.get(2) instanceof CheckCurlCommand);
        assertTrue(commands.get(3) instanceof CheckUnzipCommand);
        assertTrue(commands.get(4) instanceof CheckGitCommand);
        assertTrue(commands.get(5) instanceof CheckDockerCommand);
        assertTrue(commands.get(6) instanceof ConfigureSystemMirrorsCommand);
        assertTrue(commands.get(7) instanceof ConfigureDockerMirrorCommand);
    }

    /**
     * 测试环境初始化命令链构建（兼容旧任务名称）
     */
    @Test
    void testCreateCommandChain_InitializeEnvironment() {
        // Act
        CommandChain chain = factory.createCommandChain("initialize_environment", context);

        // Assert
        assertNotNull(chain);
        List<Command> commands = chain.getCommands();
        assertNotNull(commands);
        assertEquals(8, commands.size()); // 应该和full_setup一样

        // 验证命令类型
        assertTrue(commands.get(0) instanceof DetectOsCommand);
        assertTrue(commands.get(1) instanceof DetectLocationCommand);
        assertTrue(commands.get(6) instanceof ConfigureSystemMirrorsCommand);
        assertTrue(commands.get(7) instanceof ConfigureDockerMirrorCommand);
    }

    /**
     * 测试环境检查命令链构建
     */
    @Test
    void testCreateCommandChain_CheckEnvironment() {
        // Act
        CommandChain chain = factory.createCommandChain("check_environment", context);

        // Assert
        assertNotNull(chain);
        List<Command> commands = chain.getCommands();
        assertNotNull(commands);
        assertEquals(6, commands.size()); // 2个前置 + 4个环境检查，没有增强

        // 验证命令顺序和类型
        assertTrue(commands.get(0) instanceof DetectOsCommand);
        assertTrue(commands.get(1) instanceof DetectLocationCommand);
        assertTrue(commands.get(2) instanceof CheckCurlCommand);
        assertTrue(commands.get(3) instanceof CheckUnzipCommand);
        assertTrue(commands.get(4) instanceof CheckGitCommand);
        assertTrue(commands.get(5) instanceof CheckDockerCommand);
    }

    /**
     * 测试镜像配置命令链构建
     */
    @Test
    void testCreateCommandChain_ConfigureMirrors() {
        // Act
        CommandChain chain = factory.createCommandChain("configure_mirrors", context);

        // Assert
        assertNotNull(chain);
        List<Command> commands = chain.getCommands();
        assertNotNull(commands);
        assertEquals(4, commands.size()); // 2个前置 + 2个镜像配置

        // 验证命令顺序和类型
        assertTrue(commands.get(0) instanceof DetectOsCommand);
        assertTrue(commands.get(1) instanceof DetectLocationCommand);
        assertTrue(commands.get(2) instanceof ConfigureSystemMirrorsCommand);
        assertTrue(commands.get(3) instanceof ConfigureDockerMirrorCommand);
    }

    /**
     * 测试未知任务名称的异常处理
     */
    @Test
    void testCreateCommandChain_UnknownTaskName() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.createCommandChain("unknown_task", context);
        });

        assertEquals("Unknown task name: unknown_task", exception.getMessage());
    }

    /**
     * 测试空任务名称的异常处理
     */
    @Test
    void testCreateCommandChain_NullTaskName() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.createCommandChain(null, context);
        });

        assertTrue(exception.getMessage().contains("Unknown task name: null"));
    }

    /**
     * 测试空字符串任务名称的异常处理
     */
    @Test
    void testCreateCommandChain_EmptyTaskName() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            factory.createCommandChain("", context);
        });

        assertEquals("Unknown task name: ", exception.getMessage());
    }

    /**
     * 测试所有支持的任务类型
     */
    @Test
    void testCreateCommandChain_AllSupportedTaskTypes() {
        String[] supportedTasks = {
            "full_setup",
            "initialize_environment",
            "check_environment",
            "configure_mirrors"
        };

        for (String taskName : supportedTasks) {
            // Act
            CommandChain chain = factory.createCommandChain(taskName, context);

            // Assert
            assertNotNull(chain, "任务 " + taskName + " 的命令链不应为null");
            assertNotNull(chain.getCommands(), "任务 " + taskName + " 的命令列表不应为null");
            assertFalse(chain.getCommands().isEmpty(), "任务 " + taskName + " 的命令列表不应为空");

            log.info("任务 {} 包含 {} 个命令", taskName, chain.getCommands().size());
        }
    }

    /**
     * 测试命令链执行顺序的逻辑正确性
     */
    @Test
    void testCommandChain_ExecutionOrder() {
        // Act
        CommandChain chain = factory.createCommandChain("full_setup", context);
        List<Command> commands = chain.getCommands();

        // Assert - 验证执行顺序的逻辑性

        // 1. 前置处理命令必须在最前面
        assertTrue(commands.get(0) instanceof DetectOsCommand, "操作系统检测应该是第一个命令");
        assertTrue(commands.get(1) instanceof DetectLocationCommand, "地理位置检测应该是第二个命令");

        // 2. 环境检查命令在前置处理之后
        int envCheckStartIndex = 2;
        assertTrue(commands.get(envCheckStartIndex) instanceof CheckCurlCommand);
        assertTrue(commands.get(envCheckStartIndex + 1) instanceof CheckUnzipCommand);
        assertTrue(commands.get(envCheckStartIndex + 2) instanceof CheckGitCommand);
        assertTrue(commands.get(envCheckStartIndex + 3) instanceof CheckDockerCommand);

        // 3. 增强命令在最后
        int enhancementStartIndex = 6;
        assertTrue(commands.get(enhancementStartIndex) instanceof ConfigureSystemMirrorsCommand);
        assertTrue(commands.get(enhancementStartIndex + 1) instanceof ConfigureDockerMirrorCommand);
    }

    /**
     * 测试上下文参数传递
     */
    @Test
    void testCreateCommandChain_ContextParameter() {
        // Act
        CommandChain chain1 = factory.createCommandChain("full_setup", context);
        CommandChain chain2 = factory.createCommandChain("full_setup", null);

        // Assert - 不同的context参数不应影响命令链构建
        assertNotNull(chain1);
        assertNotNull(chain2);
        assertEquals(chain1.getCommands().size(), chain2.getCommands().size());

        // 验证命令类型相同
        for (int i = 0; i < chain1.getCommands().size(); i++) {
            assertEquals(
                chain1.getCommands().get(i).getClass(),
                chain2.getCommands().get(i).getClass(),
                "索引 " + i + " 处的命令类型应该相同"
            );
        }
    }
}
