#!/bin/bash

set -e  # Exit on any error

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
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

print_header() {
    echo -e "${BLUE}[SETUP]${NC} $1"
}

# Detect OS
detect_os() {
    if [[ "$OSTYPE" == "darwin"* ]]; then
        OS="macos"
    elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
        OS="linux"
    else
        print_error "Unsupported OS: $OSTYPE"
        print_error "This script supports macOS and Linux. For Windows, use setup.bat"
        exit 1
    fi
}

# Check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Install Homebrew on macOS
install_homebrew() {
    if ! command_exists brew; then
        print_header "Installing Homebrew..."
        /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
        
        # Add Homebrew to PATH for Apple Silicon Macs
        if [[ $(uname -m) == "arm64" ]]; then
            echo 'eval "$(/opt/homebrew/bin/brew shellenv)"' >> ~/.zprofile
            eval "$(/opt/homebrew/bin/brew shellenv)"
        fi
    else
        print_status "Homebrew is already installed"
    fi
}

# Install Java
install_java() {
    print_header "Checking Java installation..."
    
    if command_exists java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        print_status "Java is already installed: $JAVA_VERSION"
        
        # Check if it's Java 11 or higher
        JAVA_MAJOR=$(echo $JAVA_VERSION | cut -d'.' -f1)
        if [[ $JAVA_MAJOR -ge 11 ]]; then
            print_status "Java version is compatible (11+)"
            return 0
        else
            print_warning "Java version is too old. Installing Java 17..."
        fi
    fi
    
    if [[ "$OS" == "macos" ]]; then
        print_header "Installing Java 17 via Homebrew..."
        brew install openjdk@17
        
        # Create symlink for system Java
        sudo ln -sfn $(brew --prefix)/opt/openjdk@17/libexec/openjdk.jdk /Library/Java/JavaVirtualMachines/openjdk-17.jdk
        
        # Set JAVA_HOME in shell profile
        JAVA_HOME_LINE='export JAVA_HOME=$(/usr/libexec/java_home -v17)'
        if ! grep -q "JAVA_HOME" ~/.zprofile 2>/dev/null; then
            echo "$JAVA_HOME_LINE" >> ~/.zprofile
        fi
        if ! grep -q "JAVA_HOME" ~/.bash_profile 2>/dev/null; then
            echo "$JAVA_HOME_LINE" >> ~/.bash_profile
        fi
        
        export JAVA_HOME=$(/usr/libexec/java_home -v17)
        
    elif [[ "$OS" == "linux" ]]; then
        print_header "Installing Java 17..."
        
        # Detect Linux distribution
        if command_exists apt-get; then
            # Ubuntu/Debian
            sudo apt-get update
            sudo apt-get install -y openjdk-17-jdk
        elif command_exists yum; then
            # CentOS/RHEL/Fedora
            sudo yum install -y java-17-openjdk-devel
        elif command_exists dnf; then
            # Fedora (newer)
            sudo dnf install -y java-17-openjdk-devel
        elif command_exists pacman; then
            # Arch Linux
            sudo pacman -S jdk17-openjdk
        else
            print_error "Unsupported Linux distribution. Please install Java 17 manually."
            exit 1
        fi
    fi
}

# Install Maven
install_maven() {
    print_header "Checking Maven installation..."
    
    if command_exists mvn; then
        MVN_VERSION=$(mvn -version | head -n1 | cut -d' ' -f3)
        print_status "Maven is already installed: $MVN_VERSION"
        return 0
    fi
    
    if [[ "$OS" == "macos" ]]; then
        print_header "Installing Maven via Homebrew..."
        brew install maven
        
    elif [[ "$OS" == "linux" ]]; then
        print_header "Installing Maven..."
        
        # Detect Linux distribution
        if command_exists apt-get; then
            # Ubuntu/Debian
            sudo apt-get update
            sudo apt-get install -y maven
        elif command_exists yum; then
            # CentOS/RHEL/Fedora
            sudo yum install -y maven
        elif command_exists dnf; then
            # Fedora (newer)
            sudo dnf install -y maven
        elif command_exists pacman; then
            # Arch Linux
            sudo pacman -S maven
        else
            print_error "Unsupported Linux distribution. Please install Maven manually."
            exit 1
        fi
    fi
}

# Verify installations
verify_installation() {
    print_header "Verifying installations..."
    
    if command_exists java; then
        JAVA_VERSION=$(java -version 2>&1 | head -n1 | cut -d'"' -f2)
        print_status "✓ Java: $JAVA_VERSION"
    else
        print_error "✗ Java installation failed"
        return 1
    fi
    
    if command_exists mvn; then
        MVN_VERSION=$(mvn -version | head -n1 | cut -d' ' -f3)
        print_status "✓ Maven: $MVN_VERSION"
    else
        print_error "✗ Maven installation failed"
        return 1
    fi
    
    print_status "✓ All dependencies are installed successfully!"
}

# Main setup function
main() {
    print_header "Microbot/RuneLite Dependency Setup"
    print_status "This script will install Java 17 and Maven"
    echo
    
    detect_os
    print_status "Detected OS: $OS"
    echo
    
    if [[ "$OS" == "macos" ]]; then
        install_homebrew
        echo
    fi
    
    install_java
    echo
    
    install_maven
    echo
    
    verify_installation
    echo
    
    print_header "Setup completed successfully!"
    print_status "You can now run './_build_and_start.sh' to build and start the application"
    print_status "Or run './_start.sh' to start if already built"
    echo
    print_warning "You may need to restart your terminal or run 'source ~/.zprofile' (macOS) to update PATH"
}

# Run main function
main "$@"
