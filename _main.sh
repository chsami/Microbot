#!/bin/bash

export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
export PATH="/usr/local/opt/openjdk@11/bin:$PATH"

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
    echo "  --build                   Build the project before starting (default: false)"
    echo "  --stop                    Stop running RuneLite instances and exit"
    echo "  --profile <num>           Switch to profile number before starting"
    echo "  --local-plugins <path>    Use local plugins from specified folder"
    echo "  --help                    Show this help message"
    echo ""
    echo "Examples:"
    echo "  $0                                    # Just start RuneLite (no build)"
    echo "  $0 --build                           # Build and start RuneLite"
    echo "  $0 --stop                            # Stop all RuneLite instances"
    echo "  $0 --build --profile 1               # Build, switch to profile 1, and start"
    echo "  $0 --local-plugins /path/to/plugins  # Load plugins from local folder"
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

# Default flags
BUILD_PROJECT=false
STOP_ONLY=false
PROFILE_NUM=""
LOCAL_PLUGINS_PATH=""

# Parse arguments
while [[ $# -gt 0 ]]; do
    case $1 in
        --build)
            BUILD_PROJECT=true
            shift
            ;;
        --stop)
            STOP_ONLY=true
            shift
            ;;
        --profile)
            if [[ -n $2 ]]; then
                PROFILE_NUM=$2
                shift 2
            else
                print_error "Profile number required. Usage: --profile <number>"
                exit 1
            fi
            ;;
        --local-plugins)
            if [[ -n $2 ]]; then
                LOCAL_PLUGINS_PATH=$2
                shift 2
            else
                print_error "Local plugins path required. Usage: --local-plugins <path>"
                exit 1
            fi
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

# Handle --stop flag
if [ "$STOP_ONLY" = true ]; then
    stop_runelite
    print_status "Stopped all RuneLite instances."
    exit 0
fi

# Build RuneLite (only if --build flag is provided)
if [ "$BUILD_PROJECT" = true ]; then
    print_status "Building RuneLite..."
    if ! mvn clean compile -pl '!runelite-maven-plugin' -Dmaven.plugin.validation=false -q; then
        print_error "Build failed during compile phase"
        exit 1
    fi

    print_status "Packaging RuneLite..."
    if ! mvn package -pl '!runelite-maven-plugin' -Dmaven.plugin.validation=false -DskipTests -q; then
        print_error "Build failed during package phase"
        exit 1
    fi
else
    print_status "Skipping build (use --build to enable building)"
fi

# Find the jar file dynamically - try microbot jar first, then client jar
JAR_PATH=$(find runelite-client/target -name "microbot-*.jar" -type f 2>/dev/null | head -n1)

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    # Fallback to client jar
    JAR_PATH=$(find runelite-client/target -name "client-*.jar" -type f 2>/dev/null | head -n1)
fi

if [ -z "$JAR_PATH" ] || [ ! -f "$JAR_PATH" ]; then
    print_error "No executable jar file found in runelite-client/target/"
    print_error "Expected: microbot-*.jar or client-*.jar"
    ls -la runelite-client/target/ || true
    exit 1
fi

print_status "Found jar: $JAR_PATH"

# Stop existing instances
# stop_runelite

# Handle profile switching if --profile was specified
if [ -n "$PROFILE_NUM" ]; then
    CREDENTIALS_FILE=~/.runelite/credentials.properties
    PROFILE_FILE=~/.runelite/credentials.properties.$PROFILE_NUM
    
    print_status "Switching to profile $PROFILE_NUM"
    
    # Check if profile file exists
    if [ -f "$PROFILE_FILE" ]; then
        # Switch to the requested profile
        cp "$PROFILE_FILE" "$CREDENTIALS_FILE"
        print_status "Switched to profile $PROFILE_NUM"
        
        # After 10 seconds, restore the backup (optional - remove if you want profile to persist)
        (sleep 10 && rm "$CREDENTIALS_FILE") &
    else
        print_warning "Profile file $PROFILE_FILE does not exist"
    fi
fi

# Start RuneLite
print_status "Starting RuneLite..."

# Build Java command with optional local plugins path
JAVA_ARGS=(
    "--add-opens" "java.desktop/com.apple.eawt=ALL-UNNAMED"
    "--add-exports" "java.desktop/com.apple.eawt=ALL-UNNAMED"
)

# Add local plugins system property if specified
if [ -n "$LOCAL_PLUGINS_PATH" ]; then
    if [ ! -d "$LOCAL_PLUGINS_PATH" ]; then
        print_error "Local plugins path does not exist: $LOCAL_PLUGINS_PATH"
        exit 1
    fi
    
    if [ ! -f "$LOCAL_PLUGINS_PATH/plugins.json" ]; then
        print_warning "plugins.json not found in $LOCAL_PLUGINS_PATH - plugins may not load correctly"
    fi
    
    print_status "Using local plugins from: $LOCAL_PLUGINS_PATH"
    JAVA_ARGS+=("-Dmicrobot.local.plugins=$LOCAL_PLUGINS_PATH")
fi

JAVA_ARGS+=("-jar" "$JAR_PATH")

exec java "${JAVA_ARGS[@]}"