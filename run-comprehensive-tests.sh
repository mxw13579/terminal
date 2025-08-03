#!/bin/bash

# SSH Terminal System - Comprehensive Test Suite Execution Script
# This script runs all test categories for the refactored SSH terminal system

echo "========================================"
echo "SSH Terminal System - Test Suite Runner"
echo "========================================"
echo ""

# Set environment variables for testing
export SPRING_PROFILES_ACTIVE=test
export MAVEN_OPTS="-Xmx2g -XX:MaxPermSize=256m"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Test categories
UNIT_TESTS=(
    "com.fufu.terminal.entity.enums.ScriptTypeTest"
    "com.fufu.terminal.script.registry.BuiltinScriptRegistryTest"
    "com.fufu.terminal.script.context.ExecutionContextTest"
    "com.fufu.terminal.websocket.WebSocketProgressReporterTest"
)

INTEGRATION_TESTS=(
    "com.fufu.terminal.integration.SshTerminalIntegrationTest"
    "com.fufu.terminal.websocket.StompWebSocketIntegrationTest"
)

SERVICE_TESTS=(
    "com.fufu.terminal.service.refactored.RefactoredScriptExecutionServiceTest"
)

PERFORMANCE_TESTS=(
    "com.fufu.terminal.performance.SshTerminalPerformanceTest"
)

# Function to run test category
run_test_category() {
    local category_name=$1
    local tests=("${!2}")
    
    print_status "Running $category_name..."
    
    for test in "${tests[@]}"; do
        print_status "  Executing: $test"
        
        if mvn -Dtest="$test" test -q; then
            print_success "    ✓ $test PASSED"
        else
            print_error "    ✗ $test FAILED"
            return 1
        fi
    done
    
    print_success "$category_name completed successfully!"
    return 0
}

# Function to generate coverage report
generate_coverage_report() {
    print_status "Generating test coverage report..."
    
    mvn jacoco:prepare-agent test jacoco:report -q
    
    if [ $? -eq 0 ]; then
        print_success "Coverage report generated: target/site/jacoco/index.html"
    else
        print_warning "Failed to generate coverage report"
    fi
}

# Main execution
main() {
    print_status "Starting comprehensive test suite execution..."
    echo ""
    
    # Clean and compile
    print_status "Cleaning and compiling project..."
    mvn clean compile test-compile -q
    
    if [ $? -ne 0 ]; then
        print_error "Compilation failed. Aborting test execution."
        exit 1
    fi
    
    print_success "Compilation successful!"
    echo ""
    
    # Track test results
    local total_categories=4
    local passed_categories=0
    
    # Run Unit Tests
    echo "========================================"
    echo "1. UNIT TESTS"
    echo "========================================"
    if run_test_category "Unit Tests" UNIT_TESTS[@]; then
        ((passed_categories++))
    fi
    echo ""
    
    # Run Service Tests
    echo "========================================"
    echo "2. SERVICE TESTS"
    echo "========================================"
    if run_test_category "Service Tests" SERVICE_TESTS[@]; then
        ((passed_categories++))
    fi
    echo ""
    
    # Run Integration Tests
    echo "========================================"
    echo "3. INTEGRATION TESTS"
    echo "========================================"
    if run_test_category "Integration Tests" INTEGRATION_TESTS[@]; then
        ((passed_categories++))
    fi
    echo ""
    
    # Run Performance Tests
    echo "========================================"
    echo "4. PERFORMANCE TESTS"
    echo "========================================"
    if run_test_category "Performance Tests" PERFORMANCE_TESTS[@]; then
        ((passed_categories++))
    fi
    echo ""
    
    # Generate coverage report
    echo "========================================"
    echo "COVERAGE REPORT"
    echo "========================================"
    generate_coverage_report
    echo ""
    
    # Final results
    echo "========================================"
    echo "TEST EXECUTION SUMMARY"
    echo "========================================"
    echo "Total Test Categories: $total_categories"
    echo "Passed Categories: $passed_categories"
    echo "Failed Categories: $((total_categories - passed_categories))"
    echo ""
    
    if [ $passed_categories -eq $total_categories ]; then
        print_success "🎉 ALL TEST CATEGORIES PASSED! 🎉"
        echo ""
        echo "Test Coverage Achievements:"
        echo "• Unit Tests: 4 test classes covering core components"
        echo "• Service Tests: 1 test class covering consolidated service layer"
        echo "• Integration Tests: 2 test classes covering end-to-end workflows"
        echo "• Performance Tests: 1 test class covering scalability and load"
        echo ""
        echo "Key Features Validated:"
        echo "✓ 4-Type Script Classification System"
        echo "✓ Variable Scoping System (SCRIPT > SESSION > GLOBAL)"
        echo "✓ WebSocket STOMP Real-time Communication"
        echo "✓ Built-in Script Registry with Code Management"
        echo "✓ Consolidated Service Layer"
        echo "✓ SSH Connection Management"
        echo "✓ Geographic Intelligence (placeholder testing)"
        echo "✓ Concurrent Script Execution"
        echo "✓ Error Handling and Recovery"
        echo "✓ Performance and Scalability"
        echo ""
        return 0
    else
        print_error "Some test categories failed. Please review the output above."
        return 1
    fi
}

# Execute main function
main "$@"
exit $?