# 3. 配置Docker镜像加速器
configure_docker_mirror() {
    # 需依赖: $USE_CHINA_MIRROR
    if [ "$USE_CHINA_MIRROR" = true ]; then
        echo "配置 Docker 国内镜像加速器..."
        sudo mkdir -p /etc/docker
        sudo tee /etc/docker/daemon.json <<-'EOF'
{
  "registry-mirrors": [
    "https://hub-mirror.c.163.com",
    "https://mirror.baidubce.com",
    "https://registry.docker-cn.com"
  ]
}
EOF
        echo "重启Docker服务以应用镜像加速配置..."
        sudo systemctl daemon-reload
        sudo systemctl restart docker
    fi
}
