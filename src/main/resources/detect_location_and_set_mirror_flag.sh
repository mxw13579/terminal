detect_location_and_set_mirror_flag() {
    echo "==> 正在检测服务器位置..."

    # 尝试 1：ip-api.com
    COUNTRY_CODE=$(curl -s --connect-timeout 10 http://ip-api.com/json/?fields=country \
        | sed -n 's/.*"country"[[:space:]]*:[[:space:]]*"$[^"]*$".*/\1/p')
    # 尝试 2：ipinfo.io/country
    if [ -z "$COUNTRY_CODE" ]; then
        COUNTRY_CODE=$(curl -sS --connect-timeout 10 --max-time 10 -w "%{http_code}" ipinfo.io/country \
            | sed 's/200$//')
    fi
    # 尝试 3：ipinfo.io JSON
    if [ -z "$COUNTRY_CODE" ]; then
        COUNTRY_CODE=$(curl -s --connect-timeout 10 https://ipinfo.io/ \
            | sed -n 's/.*"country"[[:space:]]*:[[:space:]]*"$[^"]*$".*/\1/p')
    fi

    USE_CHINA_MIRROR=false
    if [ "$COUNTRY_CODE" = "China" ] || [ "$COUNTRY_CODE" = "CN" ]; then
        echo "检测到服务器位于中国 (Code: $COUNTRY_CODE)，将使用国内镜像。"
        USE_CHINA_MIRROR=true
    else
        echo "服务器不在中国 (Code: ${COUNTRY_CODE:-未知})，使用官方源。"
    fi
}
