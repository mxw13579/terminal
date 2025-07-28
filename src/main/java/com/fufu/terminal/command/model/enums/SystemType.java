package com.fufu.terminal.command.model.enums;

import lombok.Getter;

/**
 * 系统类型枚举
 * @author lizelin
 */
@Getter
public enum SystemType {
    UBUNTU,
    CENTOS,
    REDHAT,
    DEBIAN,
    ALPINE,
    SUSE,
    FEDORA,
    ARCH,
    UNKNOWN;

    public static SystemType fromId(String id) {
        if (id == null) {
            return UNKNOWN;
        }
        return switch (id.toLowerCase()) {
            // Mint 基于 Ubuntu
            case "ubuntu", "linuxmint" -> UBUNTU;
            // Oracle Linux
            case "centos", "rocky", "rocky-linux", "amazon", "amazon-linux", "oracle", "ol" ->
                // 归为 RHEL 体系
                    REDHAT;
            case "rhel", "redhat", "fedora" -> REDHAT;
            case "debian" -> DEBIAN;
            case "alpine" -> ALPINE;
            case "suse", "opensuse" -> SUSE;
            case "arch", "manjaro" -> ARCH;
            default -> UNKNOWN;
        };
    }
}

