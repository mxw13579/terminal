# 2. 配置系统级软件包管理器的镜像源（不支持时直接退出）
configure_system_mirrors() {
    # 需依赖: $USE_CHINA_MIRROR, $OS, $OS_VERSION_ID, $OS_VERSION_CODENAME
    if [ "$USE_CHINA_MIRROR" = false ]; then
        echo "跳过系统镜像源配置（不在中国大陆）。"
        return
    fi
    echo "==> 正在配置系统镜像源..."
    case $OS in
        debian|ubuntu)
            if grep -q -E "aliyun|tuna|ustc|163" /etc/apt/sources.list; then
                echo "检测到 /etc/apt/sources.list 已使用国内镜像，跳过替换。"
                sudo apt-get update
                return
            fi
            sudo cp /etc/apt/sources.list /etc/apt/sources.list.bak
            if [ "$OS" = "debian" ]; then
                MIRROR_URL="https://mirrors.aliyun.com/debian"
                SECURITY_MIRROR_URL="https://mirrors.aliyun.com/debian-security"
                sudo tee /etc/apt/sources.list > /dev/null <<EOF
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME} main contrib non-free
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME} main contrib non-free
deb ${SECURITY_MIRROR_URL}/ ${OS_VERSION_CODENAME}-security main contrib non-free
deb-src ${SECURITY_MIRROR_URL}/ ${OS_VERSION_CODENAME}-security main contrib non-free
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-updates main contrib non-free
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-updates main contrib non-free
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-backports main contrib non-free
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-backports main contrib non-free
EOF
            elif [ "$OS" = "ubuntu" ]; then
                MIRROR_URL="https://mirrors.aliyun.com/ubuntu"
                sudo tee /etc/apt/sources.list > /dev/null <<EOF
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME} main restricted universe multiverse
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME} main restricted universe multiverse
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-updates main restricted universe multiverse
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-updates main restricted universe multiverse
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-backports main restricted universe multiverse
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-backports main restricted universe multiverse
deb ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-security main restricted universe multiverse
deb-src ${MIRROR_URL}/ ${OS_VERSION_CODENAME}-security main restricted universe multiverse
EOF
            fi
            echo "系统源已替换为阿里云镜像。正在刷新..."
            sudo apt-get update
            ;;
        centos|rhel|fedora)
            if [ "$OS" = "fedora" ]; then
                PKG_MANAGER="dnf"
            else
                PKG_MANAGER="yum"
            fi
            if grep -q -E "aliyun|tuna|ustc|163" /etc/yum.repos.d/*.repo; then
                echo "检测到 /etc/yum.repos.d/ 已使用国内镜像，跳过替换。"
                sudo ${PKG_MANAGER} clean all && sudo ${PKG_MANAGER} makecache
                return
            fi
            sudo mkdir -p /etc/yum.repos.d/bak
            sudo mv /etc/yum.repos.d/*.repo /etc/yum.repos.d/bak/ || true
            if [ "$OS" = "fedora" ]; then
                REPO_URL="https://mirrors.aliyun.com/fedora/fedora-$(rpm -E %fedora).repo"
            else
                REPO_URL="https://mirrors.aliyun.com/repo/Centos-${OS_VERSION_ID}.repo"
            fi
            sudo curl -o /etc/yum.repos.d/aliyun-mirror.repo ${REPO_URL}
            echo "系统源已替换为阿里云镜像。正在刷新..."
            sudo ${PKG_MANAGER} clean all && sudo ${PKG_MANAGER} makecache
            ;;
        arch)
            if grep -q "tuna.tsinghua.edu.cn" /etc/pacman.d/mirrorlist; then
                echo "检测到 pacman mirrorlist 已包含清华大学镜像，跳过。"
                sudo pacman -Syy --noconfirm
                return
            fi
            sudo cp /etc/pacman.d/mirrorlist /etc/pacman.d/mirrorlist.bak
            sudo sed -i '1s|^|Server = https://mirrors.tuna.tsinghua.edu.cn/archlinux/\$repo/os/\$arch\n|' /etc/pacman.d/mirrorlist
            sudo pacman -Syy --noconfirm
            ;;
        alpine)
            if grep -q "aliyun" /etc/apk/repositories; then
                echo "检测到 apk repositories 已使用国内镜像，跳过。"
                sudo apk update
                return
            fi
            sudo cp /etc/apk/repositories /etc/apk/repositories.bak
            sudo sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories
            sudo apk update
            ;;
        *)
            echo "当前操作系统 $OS 的系统镜像源自动配置暂不支持。"
            ;;
    esac
}
