package net.runelite.client.plugins.microbot.SummerPies;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.plugins.microbot.aiofighter.AIOFighterConfig;

@ConfigGroup("SummerPies")
@ConfigInformation("Necesitas un nivel de Cooking en 95 para combinar Summer Pie. <br /><br />" +
        "Lista de ingredientes: <br />" +
        "1 Strawberry <br />" +
        "1 Watermelon <br />" +
        "1 Cooking apple <br />" +
        "1 Pie shell <br /><br />" +
        "El bot comprara automaticamente 1000 de cada uno se requiere invertir una cantidad de 1.5M aproximadamente <br />")
public interface SummerPiesConfig extends Config {
}


