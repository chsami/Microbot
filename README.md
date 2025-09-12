![image](https://github.com/user-attachments/assets/7c08e053-c84f-41f8-bc97-f55130100419)

## Getting the Project

First, download or clone the Microbot project to your local machine:

### Option 1: Clone with Git (Recommended)
```bash
git clone https://github.com/ReposUniversity/microbot.git
cd microbot
```

### Option 2: Download ZIP
1. Go to [https://github.com/ReposUniversity/microbot](https://github.com/ReposUniversity/microbot)
2. Click the green "Code" button
3. Select "Download ZIP"
4. Extract the ZIP file to your desired location
5. Open terminal/command prompt and navigate to the extracted folder

## Prerequisites Installation

Before building and running Microbot, you need to have Java 17+ and Maven installed on your system.

### Automated Setup

Use the provided setup scripts to automatically install dependencies:

#### macOS/Linux
```bash
# Navigate to the project directory
cd microbot

# Make the script executable
chmod +x setup.sh

# Run the setup script
./setup.sh
```

The macOS setup script will:
- Install Homebrew (if not present)
- Install Java 17 via Homebrew
- Install Maven via Homebrew
- Configure JAVA_HOME environment variable

#### Windows
```cmd
# Navigate to the project directory
cd microbot

# Run as Administrator for best results
setup.bat
```

The Windows setup script will:
- Install Chocolatey (if not present)
- Install Java 17 via Chocolatey
- Install Maven via Chocolatey
- Provide manual installation instructions if needed

### Manual Installation

If the automated setup fails, you can install the prerequisites manually:

**Java 17:**
- macOS: `brew install openjdk@17`
- Windows: Download from [Adoptium](https://adoptium.net/temurin/releases/)
- Linux: `sudo apt install openjdk-17-jdk` (Ubuntu/Debian)

**Maven:**
- macOS: `brew install maven`
- Windows: Download from [Maven.apache.org](https://maven.apache.org/download.cgi)
- Linux: `sudo apt install maven` (Ubuntu/Debian)

## Building and Running

After installing the prerequisites, use the platform-specific scripts:

### Complete Workflow Example

#### macOS/Linux
```bash
# 1. Download the project
git clone https://github.com/chsami/microbot.git
cd microbot

# 2. Install dependencies
chmod +x setup.sh
./setup.sh

# 3. Build and start (first time or after changes)
./_build_and_start.sh

# Or start only (if already built)
./_start.sh
```

#### Windows
```cmd
# 1. Download the project
git clone https://github.com/chsami/microbot.git
cd microbot

# 2. Install dependencies (run as Administrator)
setup.bat

# 3. Build and start (first time or after changes)
_build_and_start.bat

# Or start only (if already built)
_start.bat
```

## Microbot ChatGPT Chatbot

[![image](https://github.com/user-attachments/assets/92adb50f-1500-44c0-a069-ff976cccd317)](https://chatgpt.com/g/g-LM0fGeeXB-microbot-documentation)

Use this AI Chatbot to learn how to write scripts in [Microbot GPT](https://chatgpt.com/g/g-LM0fGeeXB-microbot-documentation)

## Project Layout

Under the Microbot Plugin you'll find a util folder that has all the utility classes which make it easier to interact with the game

Utility Classes are prefixed with Rs2. So for player it is Rs2Player. Npcs is Rs2Npc and so on...

If you can't find a specific thing in a utility class you can always call the Microbot object which has access to every object runelite exposes. So to get the location of a player you can do

```java 
Microbot.getClient().getLocalPlayer().getWorldLocation()
```

![img.png](img.png)

## ExampleScript

There is an example script which you can use to play around with the api.

![img_1.png](img_1.png)

How does the example script look like?

```java
public class ExampleScript extends Script {
public static double version = 1.0;

    public boolean run(ExampleConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {
                /*
                 * Important classes:
                 * Inventory
                 * Rs2GameObject
                 * Rs2GroundObject
                 * Rs2NPC
                 * Rs2Bank
                 * etc...
                 */

                long startTime = System.currentTimeMillis();
                
                //YOUR CODE COMES HERE
                Rs2Npc.attack("guard");
                
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 2000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
```

All our scripts exist of Config. This is the settings for a specific script
Overlay, this is a visual overlay for a specific script
Plugin which handles the code for starting and stopping the script
Script which handles all of the code that microbot has to execute.

Inside the startup of a plugin we can call the script code like this:

```java
@Override
protected void startUp() throws AWTException {
if (overlayManager != null) {
overlayManager.add(exampleOverlay);
}
//CALL YOUR SCRIPT.RUN
exampleScript.run(config);
}
```

Credits to runelite for making all of this possible <3

https://github.com/runelite/runelite

### License

RuneLite is licensed under the BSD 2-clause license. See the license header in the respective file to be sure.

