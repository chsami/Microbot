# VS Code Configuration for Microbot

This directory contains VS Code workspace configuration files for the Microbot/RuneLite project.

## 📁 Files Overview

### `launch.json` - Debug & Run Configurations

- **🚀 Launch Microbot/RuneLite** - Run the application in development mode
- **🐛 Debug Microbot/RuneLite** - Debug the application with breakpoints
- **🏃 Run JAR (Production)** - Run the packaged JAR file
- **🔧 Current Java File** - Run any individual Java file

### `tasks.json` - Build Tasks

- **build-microbot** - Compile the project (`mvn clean compile`)
- **package-microbot** - Create JAR file (`mvn package`)
- **clean-microbot** - Clean build artifacts (`mvn clean`)
- **full-build** - Complete build process (clean + compile + package)
- **run-jar** - Run the JAR directly from terminal

### `settings.json` - Project Settings

- Java configuration and classpath setup
- Code formatting using `checkstyle.xml`
- File associations for `.rs2asm`, `.g4` files
- Build and debug preferences
- Auto-save and import organization

### `extensions.json` - Recommended Extensions

Essential extensions for Java development and this project

## 🚀 Getting Started

1. **Install Recommended Extensions**

   - VS Code will prompt you to install recommended extensions
   - Or run: `Ctrl+Shift+P` → "Extensions: Show Recommended Extensions"

2. **Open Project**

   - Open the root directory `/Microbot` in VS Code
   - VS Code will automatically detect the Maven project structure

3. **Run/Debug**

   - Press `F5` to start debugging
   - Or use `Ctrl+Shift+P` → "Debug: Select and Start Debugging"
   - Choose "🚀 Launch Microbot/RuneLite"

4. **Build Project**
   - `Ctrl+Shift+P` → "Tasks: Run Task"
   - Choose "full-build" for complete build
   - Or use `Ctrl+Shift+B` for default build task

## 🔧 Troubleshooting

### Java Path Issues

- Ensure Java 17+ is installed and in PATH
- Check `JAVA_HOME` environment variable
- VS Code should auto-detect Java installation

### Maven Issues

- Ensure Maven is installed and in PATH
- Run `mvn -v` in terminal to verify
- Reload VS Code window if needed

### Build Failures

- Check console output for specific errors
- Try `clean-microbot` task first
- Ensure all dependencies are available

### Debugging Issues

- Verify the main class `net.runelite.client.RuneLite` exists
- Check that JVM arguments are correct
- Ensure project compiles successfully first

## 🎯 Quick Commands

| Action               | Shortcut       | Command                   |
| -------------------- | -------------- | ------------------------- |
| **🚀 Quick Start**   | `Cmd+Shift+S`  | Start Microbot (no build) |
| **⚡ Build & Start** | `Cmd+Shift+B`  | Full build and start      |
| **🧹 Clean Project** | `Cmd+Shift+C`  | Clean build artifacts     |
| Start Debugging      | `F5`           | Launch/Debug Microbot     |
| Default Build        | `Ctrl+Shift+B` | Run default build task    |
| Run JAR              | `Cmd+Shift+J`  | Run existing JAR          |
| Run Task             | `Ctrl+Shift+P` | Tasks: Run Task           |
| Open Terminal        | `Ctrl+\``      | Open integrated terminal  |
| Command Palette      | `Ctrl+Shift+P` | Access all commands       |

## 📝 Notes

- The configuration assumes Maven is installed and available
- JVM arguments include macOS-specific flags for GUI compatibility
- Build tasks use quiet mode (`-q`) for cleaner output
- Debug configurations automatically build before launching
