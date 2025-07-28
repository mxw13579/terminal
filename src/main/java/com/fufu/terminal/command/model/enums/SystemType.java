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
            case "ubuntu" -> UBUNTU;
            case "centos", "rhel", "redhat" -> REDHAT;
            case "debian" -> DEBIAN;
            case "alpine" -> ALPINE;
            case "suse" -> SUSE;
            case "fedora" -> FEDORA;
            case "arch" -> ARCH;
            default -> UNKNOWN;
        };
    }
}
