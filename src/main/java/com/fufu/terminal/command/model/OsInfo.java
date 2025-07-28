package com.fufu.terminal.command.model;

import com.fufu.terminal.command.model.enums.SystemType;
import lombok.Data;

/**
 * 操作系统信息实体类，封装了常见的 Linux 发行版识别字段。
 * <p>
 * 字段说明：
 * <ul>
 *     <li>id：操作系统标识（如 ubuntu、centos、alpine 等）</li>
 *     <li>name：操作系统名称（如 Ubuntu、CentOS、Alpine Linux 等）</li>
 *     <li>prettyName：完整操作系统描述（如 Ubuntu 22.04.1 LTS）</li>
 *     <li>versionId：操作系统版本号（如 22.04、7.9.2009 等）</li>
 *     <li>versionCodename：操作系统代号（如 jammy、bionic 等）</li>
 *     <li>arch：系统架构（如 x86_64、arm64 等）</li>
 * </ul>
 * 所有字段均允许为 "unknown"，表示未能识别。
 */
@Data
public class OsInfo {
    /**
     * 操作系统标识（如 ubuntu、centos、alpine 等）
     */
    private String id = "unknown";
    /**
     * 操作系统名称（如 Ubuntu、CentOS、Alpine Linux 等）
     */
    private String name = "unknown";
    /**
     * 完整操作系统描述（如 Ubuntu 22.04.1 LTS）
     */
    private String prettyName = "unknown";
    /**
     * 操作系统版本号（如 22.04、7.9.2009 等）
     */
    private String versionId = "unknown";
    /**
     * 操作系统代号（如 jammy、bionic 等）
     */
    private String versionCodename = "unknown";
    /**
     * 系统架构（如 x86_64、arm64 等）
     */
    private String arch = "unknown";
    /**
     * 系统枚举
     */
    private SystemType systemType = SystemType.UNKNOWN;

}
