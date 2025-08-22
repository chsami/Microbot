package net.runelite.client.plugins.microbot.GlassBlowing.tasks;

import net.runelite.client.plugins.microbot.GlassBlowing.GlassBlowingConfig;
import net.runelite.client.plugins.microbot.Task;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class BlowTask extends Task {

    public static int glassBlown = 0;
    GlassBlowingConfig config;

    public BlowTask(GlassBlowingConfig glassConfig) {
        super();
        config = glassConfig;
    }


    public boolean accept() {
        return Rs2Inventory.count("Unpowered orb") < 27;
    }

    public int execute() {
        Rs2Inventory.combine("Glassblowing pipe", "Molten glass");
        Rs2Widget.sleepUntilHasWidgetText("How many do you wish to make?", 270, 5, false, 5000);

        Rs2Keyboard.keyPress(config.glassProduct().getMenuEntry());
        sleepUntil(()-> !Rs2Inventory.contains("Molten Glass"), 100000);
        logOnceToChat("Finished blowing glass");
        glassBlown = glassBlown + 27;
        return (int) (Math.random() * 401) + 400;
    }

}
