# Hal's Utility & Plugins Framework - Personal Reference Guide

Disclaimer: This is my personal reference guide for the modular plugin framework I built for Microbot. It's designed to be a comprehensive reminder of how everything works, the design decisions I made, and the deep technical implementation details.
This is based on my own interpretations of how things work/should work, it is almost certainly full of falsehoods as I am incredibly ignorant. Hopefully these falsehoods will be rectified as I try my best to learn. Please be patient with me as I walk this path. - Hal

## Core Architecture Philosophy

The framework is built around the concept of **modular independence with centralized control**. Each module is completely self-contained in its own folder, but they all integrate through a central plugin that manages their lifecycle, configuration, and UI presentation.

### Why This Design?

1. **Single Plugin Entry Point**: RuneLite only sees one plugin in the plugin list, but internally it manages multiple sub-plugins
2. **Independent Module Lifecycles**: Each module can be started/stopped independently without affecting others
3. **Separated Configuration**: Each module has its own config file, preventing config conflicts and making maintenance easier
4. **Category-Based Organization**: Modules are organized by function (Skilling, Money, Activity, Bossing, Utility) for logical grouping
5. **Programmatic Control**: The main plugin provides a comprehensive API for controlling modules from anywhere in the codebase

## Deep Dive: Module System Architecture

### The Module Interface (`HalSubPlugin`)

Every module implements the `HalSubPlugin` interface, which extends Guice's `Module` interface. This means each module can define its own dependency injection bindings:

```java
public interface HalSubPlugin extends Module {
    void start();
    void stop();
    void setInjector(Injector injector);
    Injector getInjector();
    void setPlugin(HalsUtilityandPluginsPlugin plugin);
    Config getConfig();
    Class<? extends Config> getConfigClass();
    HalModuleCategory getCategory();
    boolean isRunning();
    String getDisplayName();
}
```

**Key Insight**: By extending `Module`, each sub-plugin can have its own Guice injector with custom bindings. This allows for complex dependency injection scenarios within each module.

### Abstract Base Class (`AbstractHalModule<T>`)

The `AbstractHalModule<T>` provides a concrete implementation that handles the boilerplate:

```java
public abstract class AbstractHalModule<T extends Config> implements HalSubPlugin {
    protected T config;
    protected Injector injector;
    protected HalsUtilityandPluginsPlugin plugin;
    protected boolean running = false;
    protected final HalModuleCategory category;
    protected final String displayName;
    protected final Class<T> configClass;
    
    // Template methods for subclasses
    protected abstract void onStart();
    protected abstract void onStop();
}
```

**Why Generic Config Type?**: The `<T extends Config>` generic ensures type safety when accessing config values. No more casting or reflection needed.

### Module Enum (`HalModule`)

The `HalModule` enum serves as the registry of all available modules:

```java
public enum HalModule {
    EXAMPLE_SKILLING(new ExampleSkillingModule(), HalModuleCategory.SKILLING, "Example Skilling"),
    BLESSED_WINE(new BlessedWineModule(), HalModuleCategory.SKILLING, "Blessed Wine"),
    // ... more modules
}
```

**Registry Pattern**: This enum acts as a registry, making it easy to add new modules without touching the main plugin code.

## Configuration System Deep Dive

### Config Routing Architecture

The configuration system uses a sophisticated routing mechanism:

1. **Main Plugin Config**: `HalUtilityConfig` handles global settings
2. **Module Configs**: Each module has its own config interface
3. **Config Change Events**: The main plugin listens for config changes and routes them to the appropriate module
4. **Programmatic Control**: Config values can be set programmatically via the API

### Config Change Handling Flow

```java
@Subscribe
public void onConfigChanged(ConfigChanged event) {
    // Handle main plugin config changes
    if (Objects.equals(event.getGroup(), "halsutility")) {
        // Global config changes
        return;
    }
    
    // Route to appropriate module
    for (HalSubPlugin module : loadedModules.values()) {
        if (module instanceof AbstractHalModule) {
            AbstractHalModule<?> halModule = (AbstractHalModule<?>) module;
            if (halModule.getConfigClass() != null) {
                String moduleConfigGroup = halModule.getConfigClass()
                    .getAnnotation(ConfigGroup.class).value();
                if (Objects.equals(event.getGroup(), moduleConfigGroup)) {
                    halModule.onConfigChanged(event);
                    break;
                }
            }
        }
    }
}
```

**Why This Approach?**: Instead of each module listening for its own config changes, the main plugin routes them. This prevents multiple listeners and ensures proper module lifecycle management.

### ConfigInformation Support

Modules can use `@ConfigInformation` to provide setup instructions:

```java
@ConfigInformation("This plugin will handle prayer using wines.\n" +
        "Ensure you have enough blessed bone shards, \n" +
        "Ensure you have  1 wine per 400 shards, \n" +
        "Ensure you have 1 calcified moth per 10,400 shards, \n" +
        "You must have access to Cam Torum. \n" +
        "- Hal")
@ConfigGroup("blessedwine")
public interface BlessedWineConfig extends Config {
}
```

The config panel automatically detects and displays this information in a formatted panel.

## Dependency Injection Architecture

### Child Injector Pattern

Each module gets its own child injector:

```java
private void instantiate(HalModule module) {
    HalSubPlugin helper = module.getPlugin();

    Module halModule = (Binder binder) -> {
        binder.bind(HalSubPlugin.class).toInstance(helper);
        binder.install(helper); // This installs the module's own bindings
    };
    Injector halInjector = Microbot.getInjector().createChildInjector(halModule);
    injector.injectMembers(helper);
    helper.setInjector(halInjector);
    helper.setPlugin(this);
}
```

**Child Injector Benefits**:
- Modules can have their own dependency bindings
- Modules inherit the parent injector's bindings
- Clean separation of concerns
- No dependency conflicts between modules

### Module-Specific Bindings

Each module can define its own bindings in the `configure()` method:

```java
@Override
public void configure(Binder binder) {
    // Module-specific bindings
    binder.bind(MyCustomService.class).to(MyCustomServiceImpl.class);
    binder.bind(MyCustomConfig.class).toInstance(config);
}
```

## UI Architecture Deep Dive

### Panel System

The UI uses a card layout system:

```java
public class HalPanel extends PluginPanel {
    private CardLayout cardLayout;
    private JPanel mainPanel;
    private JPanel configPanelContainer;
    
    public HalPanel(HalsUtilityandPluginsPlugin plugin) {
        cardLayout = new CardLayout();
        setLayout(cardLayout);
        
        initializeMainPanel();
        initializeConfigPanelContainer();
        
        add(mainPanel, "main");
        add(configPanelContainer, "config");
        
        cardLayout.show(this, "main");
    }
}
```

**Card Layout Benefits**:
- Clean navigation between main panel and config panels
- No complex panel management
- Easy to extend with additional panels

### Search and Filtering

The main panel includes sophisticated search functionality:

```java
private void onSearchBarChanged() {
    final String text = searchBar.getText().toLowerCase();
    modulesPanel.removeAll();
    
    moduleList.stream()
        .filter(item -> text.isEmpty() || 
            item.getKeywords().stream().anyMatch(keyword -> keyword.contains(text)) ||
            item.getSearchableName().toLowerCase().contains(text))
        .forEach(modulesPanel::add);
    
    revalidate();
}
```

**Search Features**:
- Real-time filtering as you type
- Searches both module names and keywords
- Case-insensitive matching
- Preserves scroll position during filtering

### Config Panel Generation

The config panel uses RuneLite's `ConfigDescriptor` system for automatic UI generation:

```java
private void rebuild() {
    Config config = selectedModule.getConfig();
    ConfigDescriptor cd = configManager.getConfigDescriptor(config);
    
    // Show ConfigInformation if present
    if (cd.getInformation() != null) {
        buildInformationPanel(cd.getInformation());
    }
    
    // Group config items by sections
    for (ConfigSectionDescriptor csd : cd.getSections()) {
        // Create collapsible sections
    }
    
    // Add config items
    for (ConfigItemDescriptor cid : cd.getItems()) {
        JPanel item = createConfigItemPanel(cd, cid);
        // Add to appropriate section
    }
}
```

**Automatic UI Generation Benefits**:
- No manual UI code needed for config items
- Automatically handles all config types (boolean, string, int, enum, etc.)
- Supports config sections and positioning
- Handles config warnings and validation

## Overlay System Architecture

### Module-Specific Overlays

Each module has its own overlay that extends `OverlayPanel`:

```java
public class ExampleSkillingOverlay extends OverlayPanel {
    private final ExampleSkillingModule skillingModule;

    @Inject
    public ExampleSkillingOverlay(ExampleSkillingModule skillingModule) {
        this.skillingModule = skillingModule;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty(); // Makes it draggable
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(220, 150));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("Example Skilling Module")
                .color(Color.GREEN) // Category-specific color
                .build());
        
        // Add status information
        if (skillingModule.isRunning()) {
            // Show running status
        } else {
            // Show stopped status
        }
        
        return super.render(graphics);
    }
}
```

**Overlay Lifecycle Management**:
- Overlays are created when modules start
- Overlays are removed when modules stop
- Each overlay is injected with its module instance
- Color coding by category for easy identification

## Programmatic Control API

### Comprehensive Module Management

The main plugin provides a complete API for module control:

```java
// Module lookup
Optional<HalSubPlugin> getModuleByName(String displayName)
Optional<HalSubPlugin> getModuleByEnum(HalModule moduleEnum)

// Module control with config injection
void enableModule(String displayName, Map<String, String> configOptions)
void enableModule(HalModule moduleEnum, Map<String, String> configOptions)
void enableModule(HalSubPlugin module, Map<String, String> configOptions)

// Status checking
boolean isModuleEnabled(String displayName)
boolean isModuleEnabled(HalModule moduleEnum)
boolean isModuleEnabled(HalSubPlugin module)

// Module listing
List<HalSubPlugin> getRunningModules()
List<HalSubPlugin> getStoppedModules()
List<HalSubPlugin> getRunningModulesByCategory(HalModuleCategory category)
```

### Config Injection Mechanism

The `enableModule` methods support config injection:

```java
public void enableModule(HalSubPlugin module, Map<String, String> configOptions) {
    // Set config options if provided
    if (configOptions != null && !configOptions.isEmpty()) {
        if (module instanceof AbstractHalModule) {
            AbstractHalModule<?> halModule = (AbstractHalModule<?>) module;
            if (halModule.getConfigClass() != null) {
                String configGroup = halModule.getConfigClass()
                    .getAnnotation(ConfigGroup.class).value();
                
                for (Map.Entry<String, String> entry : configOptions.entrySet()) {
                    configManager.setConfiguration(configGroup, entry.getKey(), entry.getValue());
                }
            }
        }
    }
    
    // Start the module
    if (!module.isRunning()) {
        module.start();
        refreshPanel();
    }
}
```

**Why Config Injection?**: This allows modules to be started with specific configurations programmatically, enabling complex automation scenarios.

## Module Categories and Organization

### Category System

Modules are organized into categories for logical grouping:

```java
public enum HalModuleCategory {
    ALL,
    SKILLING,    // Skill training modules
    MONEY,       // Money making modules
    ACTIVITY,    // General activities (clues, minigames, etc.)
    BOSSING,     // Boss-related modules
    UTILITY      // Utility/tool modules
}
```

### Folder Structure Philosophy

The folder structure mirrors the category system:

```
modules/
├── skilling/           # SKILLING category
│   ├── example/
│   └── blessedwine/
├── moneymaking/        # MONEY category
│   └── example/
├── activity/           # ACTIVITY category
│   └── example/
├── bossing/            # BOSSING category
│   └── example/
└── utility/            # UTILITY category
    └── example/
```

**Benefits of This Structure**:
- Easy to find modules by function
- Clear separation of concerns
- Scalable as more modules are added
- Intuitive for developers

## Error Handling and Logging

### Comprehensive Error Handling

The framework includes robust error handling:

```java
public void enableModule(HalSubPlugin module, Map<String, String> configOptions) {
    try {
        // Module enabling logic
    } catch (Exception e) {
        log.error("Failed to enable module: {}", module.getDisplayName(), e);
    }
}
```

### Logging Strategy

- **Debug Level**: Detailed module operations
- **Info Level**: Module start/stop events
- **Warn Level**: Non-critical issues (module not found, etc.)
- **Error Level**: Critical failures with full stack traces

## Performance Considerations

### Lazy Loading

Modules are instantiated on plugin startup but not started until explicitly enabled:

```java
private void scanAndInstantiate() {
    for (HalModule module : HalModule.values()) {
        instantiate(module); // Creates but doesn't start
    }
}
```

### Efficient UI Updates

The UI uses efficient update mechanisms:

```java
public void refreshPanel() {
    if (navButton != null && navButton.getPanel() instanceof HalPanel) {
        ((HalPanel) navButton.getPanel()).rebuildModuleList();
    }
}
```

## Extension Points and Customization

### Adding New Modules

To add a new module:

1. **Create the module folder structure**:
   ```
   modules/skilling/mynewmodule/
   ├── MyNewModule.java
   ├── MyNewModuleConfig.java
   ├── MyNewModuleOverlay.java
   └── MyNewModuleScript.java
   ```

2. **Implement the module**:
   ```java
   public class MyNewModule extends AbstractHalModule<MyNewModuleConfig> {
       public MyNewModule() {
           super(HalModuleCategory.SKILLING, "My New Module", MyNewModuleConfig.class);
       }
       
       @Override
       protected void onStart() {
           // Module start logic
       }
       
       @Override
       protected void onStop() {
           // Module stop logic
       }
   }
   ```

3. **Add to the enum**:
   ```java
   public enum HalModule {
       // ... existing modules ...
       MY_NEW_MODULE(new MyNewModule(), HalModuleCategory.SKILLING, "My New Module");
   }
   ```

### Custom Config Types

The framework supports any config type that RuneLite supports:

- `boolean` → Checkbox
- `String` → Text field
- `int` → Spinner
- `double` → Spinner
- `Enum` → Combo box

### Custom Overlay Styles

Overlays can be customized with different styles:

```java
// Category-specific colors
Color.GREEN    // Skilling
Color.YELLOW   // Money
Color.BLUE     // Activity
Color.RED      // Bossing
Color.MAGENTA  // Utility
```

## Advanced Usage Patterns

### Module Coordination

Modules can coordinate with each other:

```java
// In one module
@Inject
private HalsUtilityandPluginsPlugin mainPlugin;

// Enable another module
mainPlugin.enableModule("Example Utility");

// Check if another module is running
if (mainPlugin.isModuleEnabled("Example Activity")) {
    mainPlugin.disableModule("Example Activity");
}
```

### Conditional Module Starting

```java
// Start bossing module only if skilling module is not running
if (!mainPlugin.isModuleEnabled("Example Skilling")) {
    mainPlugin.enableModule("Example Bossing");
}
```

### Batch Operations

```java
// Start all skilling modules
List<HalSubPlugin> skillingModules = mainPlugin.getLoadedModules().values().stream()
    .filter(module -> module.getCategory() == HalModuleCategory.SKILLING)
    .collect(Collectors.toList());

for (HalSubPlugin module : skillingModules) {
    mainPlugin.enableModule(module);
}
```

## Troubleshooting and Debugging

### Common Issues

1. **Module not starting**: Check if config is properly injected
2. **Overlay not showing**: Verify overlay is added to OverlayManager
3. **Config changes not working**: Ensure onConfigChanged is overridden
4. **Dependency injection issues**: Check module's configure() method

### Debug Techniques

1. **Enable debug logging**: Set log level to DEBUG
2. **Check module status**: Use `isModuleEnabled()` methods
3. **Verify config values**: Use ConfigManager directly
4. **Test module isolation**: Start modules individually

## Future Enhancements

### Potential Improvements

1. **Module Dependencies**: Allow modules to depend on other modules
2. **Module Profiles**: Save/load module combinations
3. **Advanced Filtering**: More sophisticated search and filtering
4. **Module Statistics**: Track module usage and performance
5. **Scheduler Support**: Allows cooperation with plugin schedular
6. **Extended Multi Module configuration**: Implement a method to configure start and stop conditions for smaller tasks to allow extensible use without entirely developing a new plugin.
7. **Account Automation**: Develop modules to handle back to back unmonitored questing, diaries, combat tasks, and/or skill levelling via seperate highly customizable and exstensive configurations. Build exactly what you want essentially.

### Scalability Considerations

The current architecture supports:
- Unlimited number of modules
- Complex dependency injection scenarios
- Sophisticated configuration management
- Real-time UI updates
- Programmatic control

## Conclusion

This framework represents a sophisticated approach to modular plugin development in RuneLite. It provides:

- **Clean Architecture**: Well-separated concerns with clear interfaces
- **Type Safety**: Generics and proper typing throughout
- **Extensibility**: Easy to add new modules and features
- **Maintainability**: Clear structure and comprehensive error handling
- **User Experience**: Intuitive UI with real-time feedback
- **Developer Experience**: Comprehensive API and clear documentation

The design decisions made here prioritize maintainability, extensibility, and user experience while following RuneLite best practices and patterns. 