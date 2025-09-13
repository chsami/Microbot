#!/bin/bash

set -e  # Exit on any error

export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
export PATH="/usr/local/opt/openjdk@11/bin:$PATH"

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

# Function to stop RuneLite specifically
stop_runelite() {
    print_status "Stopping existing RuneLite instances..."
    
    # Find and kill RuneLite/Microbot processes specifically
    RUNELITE_PIDS=$(pgrep -f "(microbot.*\.jar|client.*\.jar)" 2>/dev/null || true)
    
    if [ -n "$RUNELITE_PIDS" ]; then
        echo "$RUNELITE_PIDS" | while read pid; do
            print_warning "Killing RuneLite process $pid"
            kill "$pid" 2>/dev/null || true
        done
        sleep 2
    else
        print_status "No running RuneLite instances found"
    fi
}

# Find the jar file dynamically - try microbot jar first, then client jar
JAR_PATH=$(find runelite-client/target -name "microbot-*.jar" -type f 2>/dev/null | head -n1)

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    # Fallback to client jar
    JAR_PATH=$(find runelite-client/target -name "client-*.jar" -type f 2>/dev/null | head -n1)
fi

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    print_error "No executable jar file found in runelite-client/target/"
    print_error "Expected: microbot-*.jar or client-*.jar"
    print_error "Please run _build_and_start.sh first to build the project"
    ls -la runelite-client/target/ || true
    exit 1
fi

print_status "Found jar: $JAR_PATH"

# Stop existing instances
# stop_runelite

# Start RuneLite
print_status "Starting RuneLite..."
exec java --add-opens java.desktop/com.apple.eawt=ALL-UNNAMED --add-exports java.desktop/com.apple.eawt=ALL-UNNAMED -jar "$JAR_PATH"