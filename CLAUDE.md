# Microbot Plugin Development Guide

## Configuration System Enhancements

### Toggle Switch Support

Boolean configuration items can now display as toggle switches instead of checkboxes by including "toggle" in the keyName.

```java
@ConfigItem(
    keyName = "toggleFeatureEnabled", // "toggle" in keyName creates toggle switch
    name = "Enable feature",
    description = "Enable this feature"
)
default boolean toggleFeatureEnabled() { return false; }
```

### Multi-Column Layouts

Config sections can now display items in multiple columns using the `columns` parameter:

```java
@ConfigSection(
    name = "Settings", 
    description = "Configuration options",
    columns = 2 // Creates 2-column layout
)
String settingsSection = "settings";
```

For 2-column layouts, specify which column each item should appear in:

```java
@ConfigItem(
    keyName = "toggleOption1",
    name = "Option 1",
    section = settingsSection,
    columnSide = "left" // Places item in left column
)
default boolean toggleOption1() { return false; }

@ConfigItem(
    keyName = "toggleOption2", 
    name = "Option 2",
    section = settingsSection,
    columnSide = "right" // Places item in right column
)
default boolean toggleOption2() { return false; }
```

### Exclusive Selection

Boolean sections can be configured for exclusive selection (radio button behavior) where only one option can be selected at a time:

```java
@ConfigSection(
    name = "Mode selection",
    description = "Choose one mode",
    exclusive = true // Only one boolean can be selected
)
String modeSection = "mode";

@ConfigItem(
    keyName = "toggleMode1",
    name = "Mode 1", 
    section = modeSection
)
default boolean toggleMode1() { return false; }

@ConfigItem(
    keyName = "toggleMode2",
    name = "Mode 2",
    section = modeSection  
)
default boolean toggleMode2() { return false; }
```

### Complete Example

```java
@ConfigGroup("example")
public interface ExampleConfig extends Config {

    @ConfigSection(
        name = "Feature toggles",
        description = "Enable/disable features", 
        columns = 2
    )
    String featuresSection = "features";

    @ConfigSection(
        name = "Mode selection", 
        description = "Choose operating mode",
        exclusive = true
    )
    String modeSection = "mode";

    // 2-column toggle section
    @ConfigItem(
        keyName = "toggleFeature1",
        name = "Feature 1",
        section = featuresSection,
        columnSide = "left"
    )
    default boolean toggleFeature1() { return false; }

    @ConfigItem(
        keyName = "toggleFeature2", 
        name = "Feature 2",
        section = featuresSection,
        columnSide = "right"
    )
    default boolean toggleFeature2() { return false; }

    // Exclusive selection section  
    @ConfigItem(
        keyName = "toggleModeA",
        name = "Mode A",
        section = modeSection
    )
    default boolean toggleModeA() { return false; }

    @ConfigItem(
        keyName = "toggleModeB",
        name = "Mode B", 
        section = modeSection
    )
    default boolean toggleModeB() { return false; }
}
```

### Usage Guidelines

- Use toggle switches for boolean configs by including "toggle" in keyName
- Default to 2-column layouts for sections with multiple boolean options  
- Use exclusive selection for mutually exclusive boolean choices
- Column sides: "left" or "right" for 2-column layouts
- Items without columnSide default to "left"