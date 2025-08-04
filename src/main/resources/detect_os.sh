# ------ 操作系统识别，兼容所有 Linux 主要发行版 ------
detect_os() {
    msg_info "正在检测操作系统..."
    # 先默认全部未知（便于Fallback）
    OS_ID="unknown"
    OS_NAME="unknown"
    OS_PRETTY_NAME="unknown"
    OS_VERSION_ID="unknown"
    OS_VERSION_CODENAME="unknown"
    OS_ARCH=$(uname -m)

    # 优先读取标准 /etc/os-release
    if [ -f /etc/os-release ]; then
        . /etc/os-release
        OS_ID=${ID:-$OS_ID}
        OS_NAME=${NAME:-$OS_NAME}
        OS_PRETTY_NAME=${PRETTY_NAME:-$OS_PRETTY_NAME}
        OS_VERSION_ID=${VERSION_ID:-$OS_VERSION_ID}
        OS_VERSION_CODENAME=${VERSION_CODENAME:-${UBUNTU_CODENAME:-$OS_VERSION_CODENAME}}
    fi
    # 尝试 lsb_release （部分发行版或自定义系统）
    if command -v lsb_release &>/dev/null; then
        OS_ID=${OS_ID:-$(lsb_release -si | tr '[:upper:]' '[:lower:]')}
        OS_VERSION_ID=${OS_VERSION_ID:-$(lsb_release -sr)}
        OS_VERSION_CODENAME=${OS_VERSION_CODENAME:-$(lsb_release -sc 2>/dev/null)}
        OS_PRETTY_NAME=${OS_PRETTY_NAME:-$(lsb_release -sd)}
    fi
    # /etc/lsb-release
    if [ -f /etc/lsb-release ]; then
        . /etc/lsb-release
        OS_ID=${OS_ID:-$DISTRIB_ID}
        OS_VERSION_ID=${OS_VERSION_ID:-$DISTRIB_RELEASE}
        OS_VERSION_CODENAME=${OS_VERSION_CODENAME:-$DISTRIB_CODENAME}
        OS_PRETTY_NAME=${OS_PRETTY_NAME:-$DISTRIB_DESCRIPTION}
    fi
    # /etc/redhat-release 等特殊标志
    if [ -f /etc/redhat-release ]; then
        OS_PRETTY_NAME=$(cat /etc/redhat-release)
        OS_NAME=$(echo "$OS_PRETTY_NAME" | awk '{print $1}')
        OS_ID=$(echo "$OS_NAME" | tr '[:upper:]' '[:lower:]')
        OS_VERSION_ID=$(echo "$OS_PRETTY_NAME" | grep -oE "[0-9]+(\.[0-9]+)*" | head -1)
        OS_VERSION_CODENAME=""
    fi
    # SUSE 标志
    if [ -f /etc/SuSE-release ]; then
        OS_ID="suse"
        OS_NAME="SUSE"
        OS_PRETTY_NAME="SUSE $(cat /etc/SuSE-release)"
        OS_VERSION_ID=$(grep VERSION /etc/SuSE-release | awk '{print $3}' | head -1)
        OS_VERSION_CODENAME=""
    fi
    # Alpine Linux
    if [ -f /etc/alpine-release ]; then
        OS_ID="alpine"
        OS_NAME="Alpine Linux"
        OS_PRETTY_NAME="Alpine Linux $(cat /etc/alpine-release)"
        OS_VERSION_ID=$(cat /etc/alpine-release)
        OS_VERSION_CODENAME=""
    fi
    # Fallback: /etc/issue
    if [ "$OS_ID" = "unknown" ] && [ -f /etc/issue ]; then
        OS_PRETTY_NAME=$(head -1 /etc/issue | tr -d '\\l')
        OS_NAME=$(echo "$OS_PRETTY_NAME" | awk '{print $1}')
        OS_ID=$(echo "$OS_NAME" | tr '[:upper:]' '[:lower:]')
    fi
    # Fallback: uname
    if [ "$OS_ID" = "unknown" ]; then
        OS_ID=$(uname -s | tr '[:upper:]' '[:lower:]')
        OS_NAME=$(uname -s)
        OS_VERSION_ID=$(uname -r)
    fi

    # 最终输出
    msg_ok "检测到操作系统: $OS_PRETTY_NAME (ID: $OS_ID, Version: $OS_VERSION_ID, Codename: $OS_VERSION_CODENAME, Arch: $OS_ARCH)"
}

# 用法示例：
# detect_os
# echo "$OS_ID $OS_VERSION_ID $OS_VERSION_CODENAME"

