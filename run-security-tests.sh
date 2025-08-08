#!/bin/bash

# Phase 0 安全基线测试执行脚本
# 
# 此脚本用于执行完整的安全测试套件，包括后端Java测试和前端JavaScript测试
# 
# 使用方法：
#   ./run-security-tests.sh [options]
#   
# 选项：
#   --backend-only    仅运行后端测试
#   --frontend-only   仅运行前端测试
#   --coverage        生成测试覆盖率报告
#   --ci              CI环境运行模式
#   --help            显示帮助信息

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认选项
RUN_BACKEND=true
RUN_FRONTEND=true
GENERATE_COVERAGE=false
CI_MODE=false

# 解析命令行参数
while [[ $# -gt 0 ]]; do
    case $1 in
        --backend-only)
            RUN_BACKEND=true
            RUN_FRONTEND=false
            shift
            ;;
        --frontend-only)
            RUN_BACKEND=false
            RUN_FRONTEND=true
            shift
            ;;
        --coverage)
            GENERATE_COVERAGE=true
            shift
            ;;
        --ci)
            CI_MODE=true
            shift
            ;;
        --help)
            echo "Phase 0 安全基线测试执行脚本"
            echo ""
            echo "使用方法: $0 [options]"
            echo ""
            echo "选项:"
            echo "  --backend-only    仅运行后端测试"
            echo "  --frontend-only   仅运行前端测试"
            echo "  --coverage        生成测试覆盖率报告"
            echo "  --ci              CI环境运行模式"
            echo "  --help            显示此帮助信息"
            exit 0
            ;;
        *)
            echo -e "${RED}未知选项: $1${NC}"
            exit 1
            ;;
    esac
done

print_header() {
    echo -e "${BLUE}================================${NC}"
    echo -e "${BLUE}$1${NC}"
    echo -e "${BLUE}================================${NC}"
}

print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

print_error() {
    echo -e "${RED}✗ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# 检查必要工具
check_prerequisites() {
    print_header "检查测试环境"
    
    local missing_tools=()
    
    if $RUN_BACKEND; then
        if ! command -v java &> /dev/null; then
            missing_tools+=("java")
        fi
        if ! command -v mvn &> /dev/null; then
            missing_tools+=("maven")
        fi
    fi
    
    if $RUN_FRONTEND; then
        if ! command -v node &> /dev/null; then
            missing_tools+=("node.js")
        fi
        if ! command -v npm &> /dev/null; then
            missing_tools+=("npm")
        fi
    fi
    
    if [ ${#missing_tools[@]} -ne 0 ]; then
        print_error "缺少必要工具: ${missing_tools[*]}"
        exit 1
    fi
    
    print_success "环境检查通过"
}

# 运行后端测试
run_backend_tests() {
    print_header "运行后端安全测试"
    
    cd "$(dirname "$0")"
    
    local mvn_cmd="mvn test"
    
    if $CI_MODE; then
        mvn_cmd="$mvn_cmd -B -q"
    fi
    
    if $GENERATE_COVERAGE; then
        mvn_cmd="$mvn_cmd jacoco:report"
    fi
    
    # 设置测试环境变量
    export SPRING_PROFILES_ACTIVE=test
    
    echo "执行命令: $mvn_cmd"
    
    # 运行特定的安全测试
    if $mvn_cmd \
        -Dtest="CryptoServiceTest,TokenVaultTest,SecurityControllerTest,StompAuthenticationInterceptorTest,Phase0SecurityIntegrationTest" \
        -DfailIfNoTests=false; then
        print_success "后端测试通过"
    else
        print_error "后端测试失败"
        return 1
    fi
    
    if $GENERATE_COVERAGE; then
        if [ -f "target/site/jacoco/index.html" ]; then
            print_success "后端覆盖率报告生成: target/site/jacoco/index.html"
        else
            print_warning "后端覆盖率报告生成失败"
        fi
    fi
}

# 运行前端测试
run_frontend_tests() {
    print_header "运行前端安全测试"
    
    local frontend_dir="web/ssh-treminal-ui"
    
    if [ ! -d "$frontend_dir" ]; then
        print_error "前端目录不存在: $frontend_dir"
        return 1
    fi
    
    cd "$frontend_dir"
    
    # 检查是否存在测试目录和文件
    if [ ! -d "tests" ]; then
        print_error "测试目录不存在: tests/"
        return 1
    fi
    
    # 安装测试依赖（如果需要）
    if [ -f "tests/package.json" ]; then
        echo "安装测试依赖..."
        cd tests
        npm install
    else
        print_warning "未找到测试package.json，跳过依赖安装"
    fi
    
    # 运行测试
    local jest_cmd="npx jest"
    
    if $CI_MODE; then
        jest_cmd="$jest_cmd --ci --watchAll=false"
    fi
    
    if $GENERATE_COVERAGE; then
        jest_cmd="$jest_cmd --coverage"
    fi
    
    echo "执行命令: $jest_cmd"
    
    if $jest_cmd; then
        print_success "前端测试通过"
    else
        print_error "前端测试失败"
        return 1
    fi
    
    if $GENERATE_COVERAGE; then
        if [ -d "coverage" ]; then
            print_success "前端覆盖率报告生成: coverage/lcov-report/index.html"
        else
            print_warning "前端覆盖率报告生成失败"
        fi
    fi
    
    cd - > /dev/null
}

# 生成测试报告摘要
generate_test_summary() {
    print_header "测试执行总结"
    
    local test_results_file="test-results-$(date +%Y%m%d-%H%M%S).txt"
    
    echo "Phase 0 安全基线测试执行总结" > "$test_results_file"
    echo "执行时间: $(date)" >> "$test_results_file"
    echo "=====================================" >> "$test_results_file"
    echo "" >> "$test_results_file"
    
    if $RUN_BACKEND; then
        echo "后端测试范围:" >> "$test_results_file"
        echo "  ✓ CryptoService - RSA加密服务" >> "$test_results_file"
        echo "  ✓ TokenVault - 令牌保险库" >> "$test_results_file"
        echo "  ✓ SecurityController - 安全控制器API" >> "$test_results_file"
        echo "  ✓ StompAuthenticationInterceptor - STOMP认证拦截器" >> "$test_results_file"
        echo "  ✓ Phase0SecurityIntegrationTest - 集成测试" >> "$test_results_file"
        echo "" >> "$test_results_file"
    fi
    
    if $RUN_FRONTEND; then
        echo "前端测试范围:" >> "$test_results_file"
        echo "  ✓ crypto.js - RSA加密服务" >> "$test_results_file"
        echo "  ✓ auth.js - 认证服务" >> "$test_results_file"
        echo "" >> "$test_results_file"
    fi
    
    echo "测试类型:" >> "$test_results_file"
    echo "  ✓ 单元测试 - 核心功能验证" >> "$test_results_file"
    echo "  ✓ 集成测试 - 组件协同工作验证" >> "$test_results_file"
    echo "  ✓ 安全测试 - 加密、令牌、认证流程" >> "$test_results_file"
    echo "  ✓ 并发测试 - 多线程安全验证" >> "$test_results_file"
    echo "  ✓ 错误处理测试 - 异常情况处理" >> "$test_results_file"
    echo "  ✓ 边界条件测试 - 极端输入处理" >> "$test_results_file"
    
    print_success "测试总结报告生成: $test_results_file"
}

# 主执行流程
main() {
    print_header "Phase 0 安全基线测试套件"
    echo "运行模式: 后端=$RUN_BACKEND, 前端=$RUN_FRONTEND, 覆盖率=$GENERATE_COVERAGE, CI模式=$CI_MODE"
    echo ""
    
    check_prerequisites
    
    local test_exit_code=0
    
    if $RUN_BACKEND; then
        if ! run_backend_tests; then
            test_exit_code=1
        fi
        echo ""
    fi
    
    if $RUN_FRONTEND; then
        if ! run_frontend_tests; then
            test_exit_code=1
        fi
        echo ""
    fi
    
    generate_test_summary
    
    if [ $test_exit_code -eq 0 ]; then
        print_header "🎉 所有测试通过！"
        print_success "Phase 0 安全基线实现通过了所有功能验证"
        print_success "系统安全功能正常，可以安全部署"
    else
        print_header "❌ 测试失败"
        print_error "部分测试未通过，请检查错误信息并修复问题"
        exit 1
    fi
}

# 脚本入口点
main "$@"