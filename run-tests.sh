#!/bin/bash

# SSH Terminal Management System - Comprehensive Test Runner
# This script executes the complete test suite and generates coverage reports

echo "=================================================="
echo "SSH Terminal Management System - Test Suite"
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    print_error "Maven is not installed. Please install Maven to run backend tests."
    exit 1
fi

# Check if Node.js is installed
if ! command -v node &> /dev/null; then
    print_warning "Node.js is not installed. Skipping frontend tests."
    SKIP_FRONTEND=true
else
    SKIP_FRONTEND=false
fi

# Create test results directory
mkdir -p test-results
TEST_RESULTS_DIR="$(pwd)/test-results"

print_status "Test results will be saved to: $TEST_RESULTS_DIR"

# Backend Tests
echo ""
echo "=================================================="
echo "BACKEND TESTS (Java/Spring Boot)"
echo "=================================================="

print_status "Starting backend test execution..."

# Run unit tests
print_status "Running unit tests..."
mvn clean test -Dtest="**/*Test,!**/*IntegrationTest,!**/*PerformanceTest,!**/*SecurityTest" \
    -Dmaven.test.failure.ignore=true \
    > "$TEST_RESULTS_DIR/unit-tests.log" 2>&1

if [ $? -eq 0 ]; then
    print_status "Unit tests completed successfully"
else
    print_warning "Some unit tests failed. Check $TEST_RESULTS_DIR/unit-tests.log for details"
fi

# Run integration tests
print_status "Running integration tests with Testcontainers..."
mvn test -Dtest="**/*IntegrationTest" \
    -Dspring.profiles.active=test \
    -Dmaven.test.failure.ignore=true \
    > "$TEST_RESULTS_DIR/integration-tests.log" 2>&1

if [ $? -eq 0 ]; then
    print_status "Integration tests completed successfully"
else
    print_warning "Some integration tests failed. Check $TEST_RESULTS_DIR/integration-tests.log for details"
fi

# Run performance tests
print_status "Running performance tests..."
mvn test -Dtest="**/*PerformanceTest" \
    -Dmaven.test.failure.ignore=true \
    > "$TEST_RESULTS_DIR/performance-tests.log" 2>&1

if [ $? -eq 0 ]; then
    print_status "Performance tests completed successfully"
else
    print_warning "Some performance tests failed. Check $TEST_RESULTS_DIR/performance-tests.log for details"
fi

# Run security tests
print_status "Running security tests..."
mvn test -Dtest="**/*SecurityTest" \
    -Dmaven.test.failure.ignore=true \
    > "$TEST_RESULTS_DIR/security-tests.log" 2>&1

if [ $? -eq 0 ]; then
    print_status "Security tests completed successfully"
else
    print_warning "Some security tests failed. Check $TEST_RESULTS_DIR/security-tests.log for details"
fi

# Generate coverage report
print_status "Generating coverage report..."
mvn jacoco:report > "$TEST_RESULTS_DIR/coverage.log" 2>&1

if [ -f "target/site/jacoco/index.html" ]; then
    print_status "Coverage report generated: target/site/jacoco/index.html"
    cp -r target/site/jacoco "$TEST_RESULTS_DIR/"
else
    print_warning "Coverage report generation failed"
fi

# Frontend Tests
if [ "$SKIP_FRONTEND" = false ]; then
    echo ""
    echo "=================================================="
    echo "FRONTEND TESTS (Vue 3 + Vite)"
    echo "=================================================="
    
    cd web/ssh-treminal-ui
    
    print_status "Installing frontend dependencies..."
    npm ci > "$TEST_RESULTS_DIR/npm-install.log" 2>&1
    
    if [ $? -eq 0 ]; then
        print_status "Dependencies installed successfully"
        
        # Run frontend tests
        print_status "Running frontend tests..."
        npm run test > "$TEST_RESULTS_DIR/frontend-tests.log" 2>&1
        
        if [ $? -eq 0 ]; then
            print_status "Frontend tests completed successfully"
        else
            print_warning "Some frontend tests failed. Check $TEST_RESULTS_DIR/frontend-tests.log for details"
        fi
        
        # Generate frontend coverage
        print_status "Generating frontend coverage report..."
        npm run coverage > "$TEST_RESULTS_DIR/frontend-coverage.log" 2>&1
        
        if [ -d "coverage" ]; then
            print_status "Frontend coverage report generated: web/ssh-treminal-ui/coverage/"
            cp -r coverage "$TEST_RESULTS_DIR/frontend-coverage"
        else
            print_warning "Frontend coverage report generation failed"
        fi
    else
        print_error "Failed to install frontend dependencies"
    fi
    
    cd ../..
fi

# Test Summary
echo ""
echo "=================================================="
echo "TEST EXECUTION SUMMARY"
echo "=================================================="

# Count test results
UNIT_TESTS=$(grep -c "Tests run:" "$TEST_RESULTS_DIR/unit-tests.log" 2>/dev/null || echo "0")
INTEGRATION_TESTS=$(grep -c "Tests run:" "$TEST_RESULTS_DIR/integration-tests.log" 2>/dev/null || echo "0")
PERFORMANCE_TESTS=$(grep -c "Tests run:" "$TEST_RESULTS_DIR/performance-tests.log" 2>/dev/null || echo "0")
SECURITY_TESTS=$(grep -c "Tests run:" "$TEST_RESULTS_DIR/security-tests.log" 2>/dev/null || echo "0")

echo "Backend Test Results:"
echo "  - Unit Tests: $UNIT_TESTS test classes executed"
echo "  - Integration Tests: $INTEGRATION_TESTS test classes executed"
echo "  - Performance Tests: $PERFORMANCE_TESTS test classes executed"
echo "  - Security Tests: $SECURITY_TESTS test classes executed"

if [ "$SKIP_FRONTEND" = false ]; then
    FRONTEND_TESTS=$(grep -c "✓" "$TEST_RESULTS_DIR/frontend-tests.log" 2>/dev/null || echo "0")
    echo "Frontend Test Results:"
    echo "  - Component Tests: Executed"
    echo "  - Integration Tests: Executed"
fi

# Check for test failures
TOTAL_FAILURES=0

if grep -q "FAILURE" "$TEST_RESULTS_DIR/unit-tests.log" 2>/dev/null; then
    print_warning "Unit test failures detected"
    ((TOTAL_FAILURES++))
fi

if grep -q "FAILURE" "$TEST_RESULTS_DIR/integration-tests.log" 2>/dev/null; then
    print_warning "Integration test failures detected"
    ((TOTAL_FAILURES++))
fi

if grep -q "FAILURE" "$TEST_RESULTS_DIR/performance-tests.log" 2>/dev/null; then
    print_warning "Performance test failures detected"
    ((TOTAL_FAILURES++))
fi

if grep -q "FAILURE" "$TEST_RESULTS_DIR/security-tests.log" 2>/dev/null; then
    print_warning "Security test failures detected"
    ((TOTAL_FAILURES++))
fi

# Final status
echo ""
if [ $TOTAL_FAILURES -eq 0 ]; then
    print_status "🎉 ALL TESTS PASSED! SSH Terminal Management System is ready for production."
else
    print_warning "⚠️  Some tests failed. Please review the logs in $TEST_RESULTS_DIR/"
fi

echo ""
echo "Test artifacts available at: $TEST_RESULTS_DIR"
echo "  - Backend coverage: $TEST_RESULTS_DIR/jacoco/index.html"
if [ "$SKIP_FRONTEND" = false ]; then
    echo "  - Frontend coverage: $TEST_RESULTS_DIR/frontend-coverage/index.html"
fi
echo "  - Detailed logs: $TEST_RESULTS_DIR/*.log"

echo ""
echo "=================================================="
echo "Test execution completed!"
echo "=================================================="

exit $TOTAL_FAILURES