#!/bin/bash

set -e  # Exit on any error

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

# Function to show usage
show_usage() {
    echo "Usage: $0 [OPTIONS]"
    echo "Options:"
    echo "  --clean           Perform Maven clean (default: true)"
    echo "  --no-clean        Skip Maven clean"
    echo "  --help            Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                # Clean the project (default)"
    echo "  $0 --clean        # Explicitly clean the project"
    echo "  $0 --no-clean     # Skip cleaning (useful for scripting)"
}

# Default flags
PERFORM_CLEAN=true

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --clean)
            PERFORM_CLEAN=true
            shift
            ;;
        --no-clean)
            PERFORM_CLEAN=false
            shift
            ;;
        --help)
            show_usage
            exit 0
            ;;
        *)
            print_error "Unknown option: $1"
            show_usage
            exit 1
            ;;
    esac
done

# Clean the project (conditionally)
if [ "$PERFORM_CLEAN" = true ]; then
    print_status "Cleaning Microbot project..."
    if ! mvn clean -q; then
        print_error "Clean failed"
        exit 1
    fi
    print_status "✅ Project cleaned successfully"
else
    print_status "Skipping clean (--no-clean flag provided)"
fi