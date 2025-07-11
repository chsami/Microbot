package net.runelite.client.plugins.microbot.thieving;

import com.google.inject.Inject;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.ObjectComposition;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.security.Login;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class ThievingScript extends Script {
    private final ThievingConfig config;
	private final ThievingPlugin plugin;
    private static final int DARKMEYER_REGION = 14388;

    private static final Map<String, EquipmentInventorySlot> VYRE_SET = Map.of(
        "Vyre noble shoes", EquipmentInventorySlot.BOOTS,
        "Vyre noble legs", EquipmentInventorySlot.LEGS,
        "Vyre noble top", EquipmentInventorySlot.BODY
    );
    private static final Map<String, EquipmentInventorySlot> ROGUE_SET = Map.of(
        "Rogue mask", EquipmentInventorySlot.HEAD,
        "Rogue top", EquipmentInventorySlot.BODY,
        "Rogue trousers", EquipmentInventorySlot.LEGS,
        "Rogue boots", EquipmentInventorySlot.BOOTS,
        "Rogue gloves", EquipmentInventorySlot.GLOVES,
        "Thieving cape(t)", EquipmentInventorySlot.CAPE
    );

    private static final Map<String, WorldPoint[]> VYRE_HOUSES = Map.of(
        "Vallessia von Pitt", new WorldPoint[]{
            new WorldPoint(3661, 3378, 0),
            new WorldPoint(3664, 3378, 0),
            new WorldPoint(3664, 3376, 0),
            new WorldPoint(3667, 3376, 0),
            new WorldPoint(3667, 3381, 0),
            new WorldPoint(3661, 3382, 0)
        },
        "Misdrievus Shadum", new WorldPoint[]{
            new WorldPoint(3612, 3347, 0),
            new WorldPoint(3607, 3347, 0),
            new WorldPoint(3607, 3343, 0),
            new WorldPoint(3612, 3343, 0)
        },
        "Natalidae Shadum", new WorldPoint[]{
            new WorldPoint(3612, 3343, 0),
            new WorldPoint(3607, 3343, 0),
            new WorldPoint(3607, 3336, 0),
            new WorldPoint(3612, 3336, 0)
        }
        // add more...
    );

	@Inject
	public ThievingScript(final ThievingConfig config, final ThievingPlugin plugin)
	{
		this.config = config;
		this.plugin = plugin;
	}

    public boolean run() {
        Microbot.isCantReachTargetDetectionEnabled = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) return;
                if (initialPlayerLocation == null) initialPlayerLocation = Rs2Player.getWorldLocation();
                if (Rs2Player.isStunned()) return;
                if (!autoEatAndDrop()) return;
                openCoinPouches();
                wearIfNot("dodgy necklace");

                switch (config.THIEVING_NPC()) {
                    case WEALTHY_CITIZEN:
                        pickpocketWealthyCitizen();
                        break;
                    case ELVES:
                        pickpocketElves();
                        break;
                    case VYRES:
                        pickpocketVyre();
                        break;
                    case ARDOUGNE_KNIGHT:
                        pickpocketArdougneKnight();
                        break;
                    default:
                        Rs2NpcModel npc = Rs2Npc.getNpc(config.THIEVING_NPC().getName());
                        pickpocketDefault(npc);
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private boolean isPointInPolygon(WorldPoint[] polygon, WorldPoint point) {
        // thank'u duck
        int n = polygon.length;
        if (n < 3) return false;

        int plane = polygon[0].getPlane();
        if (point.getPlane() != plane) return false;

        boolean inside = false;
        int px = point.getX(), py = point.getY();
        
        for (int i = 0, j = n - 1; i < n; j = i++) {
            int xi = polygon[i].getX(), yi = polygon[i].getY();
            int xj = polygon[j].getX(), yj = polygon[j].getY();
            boolean intersect = ((yi > py) != (yj > py)) && (px < (double)(xj - xi) * (py - yi) / (yj - yi) + xi);
            if (intersect) inside = !inside;
        }
        return inside;
    }

    private boolean autoEatAndDrop() {
        if (config.useFood()) {
            if (Rs2Inventory.getInventoryFood().isEmpty()) {
                openCoinPouches();
                bankAndEquip();
                return false;
            }
            Rs2Player.eatAt(config.hitpoints());
        }

        if (Rs2Inventory.isFull()) {
            Rs2Player.eatAt(99);
            dropAllExceptImportant();
        }
        return true;
    }

    private void castShadowVeil() {
        if (!Rs2Magic.isShadowVeilActive() && Rs2Magic.canCast(MagicAction.SHADOW_VEIL)) {
            Rs2Magic.cast(MagicAction.SHADOW_VEIL);
        }
    }

    private void openCoinPouches() {
        int threshold = Math.max(1, Math.min(plugin.getMaxCoinPouch(), config.coinPouchTreshHold() + (int)(Math.random() * 7 - 3)));
        if (Rs2Inventory.hasItemAmount("coin pouch", threshold, true)) {
            Rs2Inventory.interact("coin pouch", "Open-all");
        }
    }

    private void wearIfNot(String item) {
        if (!Rs2Equipment.isWearing(item)) {
            Rs2Inventory.wield(item);
        }
    }

    private void pickpocketDefault(Rs2NpcModel npc) {
        if (!pickpocketHighlighted()) {
            if (npc == null) {
                Rs2Walker.walkTo(initialPlayerLocation, 0);
                Rs2Player.waitForWalking();
            } else {
                equipSet(ROGUE_SET);
                if (config.shadowVeil()) castShadowVeil();
                if (Rs2Npc.pickpocket(npc)) {
                    Rs2Walker.setTarget(null);
                    sleep(50, 200);
                }
            }
        }
    }

    private void pickpocketDefault(Set<String> targets) {
        Rs2NpcModel npc = Rs2Npc.getNpcs().filter(x -> targets.contains(x.getName())).findFirst().orElse(null);
        pickpocketDefault(npc);
    }

    private boolean pickpocketHighlighted() {
        var highlighted = net.runelite.client.plugins.npchighlight.NpcIndicatorsPlugin.getHighlightedNpcs();
        if (highlighted.isEmpty()) return false;
        if (config.shadowVeil()) castShadowVeil();
        if (Rs2Npc.pickpocket(highlighted)) {
            sleep(50, 200);
            return true;
        }
        return false;
    }

    private void pickpocketElves() {
        Set<String> elfs = new HashSet<>(Arrays.asList(
            "Anaire","Aranwe","Aredhel","Caranthir","Celebrian","Celegorm","Cirdan","Curufin","Earwen","Edrahil",
            "Elenwe","Elladan","Enel","Erestor","Enerdhil","Enelye","Feanor","Findis","Finduilas","Fingolfin",
            "Fingon","Galathil","Gelmir","Glorfindel","Guilin","Hendor","Idril","Imin","Iminye","Indis","Ingwe",
            "Ingwion","Lenwe","Lindir","Maeglin","Mahtan","Miriel","Mithrellas","Nellas","Nerdanel","Nimloth",
            "Oropher","Orophin","Saeros","Salgant","Tatie","Thingol","Turgon","Vaire","Goreu"
        ));
        pickpocketDefault(elfs);
    }

    private void pickpocketVyre() {
        Set<String> vyres = new HashSet<>(Arrays.asList(
            "Natalidae Shadum", "Misdrievus Shadum", "Vallessia von Pitt" // add more...
        ));
        Rs2NpcModel vyre = Rs2Npc.getNpcs().filter(x -> vyres.contains(x.getName())).findFirst().orElse(null);
        if (vyre == null) {
            pickpocketDefault((Rs2NpcModel) null);
            return;
        }
        WorldPoint[] housePolygon = VYRE_HOUSES.get(vyre.getName());
        boolean npcInside = isPointInPolygon(housePolygon, vyre.getWorldLocation());
        boolean playerInside = isPointInPolygon(housePolygon, Rs2Player.getWorldLocation());

        if (!npcInside && playerInside) {
            boolean inside = waitUntilBothInPolygon(housePolygon, vyre, 8000 + (int)(Math.random() * 4000));
            if (!inside) {
                HopToWorld();
                return;
            }
        } else {
            closeNearbyDoor(3);
            pickpocketDefault(vyre);
        }
    }

    private void pickpocketArdougneKnight() {
        WorldArea ardougneArea = new WorldArea(2649, 3280, 7, 8, 0);
        Rs2NpcModel knight = Rs2Npc.getNpc("knight of ardougne");
        if (knight == null || config.ardougneAreaCheck() && !ardougneArea.contains(knight.getWorldLocation())) {
            Microbot.showMessage("Knight not in Ardougne area or not found. Shutting down");
            shutdown();
            return;
        }
        pickpocketDefault(knight);
    }

    private void pickpocketWealthyCitizen() {
        Rs2NpcModel npc = Rs2Npc.getNpcs("Wealthy citizen", true)
            .filter(x -> x != null && x.isInteracting() && x.getInteracting() != null)
            .findFirst().orElse(null);
        if (npc != null && !Rs2Player.isAnimating(3000)) {
            pickpocketDefault(npc);
        }
    }

    private void closeNearbyDoor(int radius) {
        Rs2GameObject.getAll(
            o -> {
                ObjectComposition comp = Rs2GameObject.convertToObjectComposition(o);
                return comp != null && Arrays.asList(comp.getActions()).contains("Close");
            },
            Rs2Player.getWorldLocation(),
            radius
        ).forEach(door -> {
            if (Rs2GameObject.interact(door, "Close")) {
                Rs2Player.waitForWalking();
            }
        });
    }

    private void equipSet(Map<String, EquipmentInventorySlot> set) {
        for (Map.Entry<String, EquipmentInventorySlot> entry : set.entrySet()) {
            String item = entry.getKey();
            EquipmentInventorySlot slot = entry.getValue();
            if (!Rs2Equipment.isEquipped(item, slot)) {
                if (Rs2Inventory.contains(item)) {
                    Rs2Inventory.wear(item);
                    Rs2Inventory.waitForInventoryChanges(3000);
                } else if (Rs2Bank.hasBankItem(item)) {
                    if (Rs2Player.getWorldLocation().getRegionID() == DARKMEYER_REGION) {
                        Rs2Bank.withdrawItem(item);
                    } else {
                        Rs2Bank.withdrawAndEquip(item);
                    }
                    Rs2Inventory.waitForInventoryChanges(3000);
                }
            }
        }
    }

    private void bankAndEquip() {
        Microbot.status = "BANKING";
        BankLocation bank = Rs2Bank.getNearestBank();
        if (bank == BankLocation.DARKMEYER) equipSet(VYRE_SET);
        boolean opened = Rs2Bank.isNearBank(bank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(bank);
        if (!opened || !Rs2Bank.isOpen()) return;
        Rs2Bank.depositAll();
        equipSet(ROGUE_SET);
        if (config.shadowVeil()) {
            if (!Rs2Equipment.isEquipped("Lava battlestaff", EquipmentInventorySlot.WEAPON)) {
                if (Rs2Bank.hasBankItem("Lava battlestaff")) {
                    Rs2Bank.withdrawItem("Lava battlestaff");
                    Rs2Inventory.waitForInventoryChanges(3000);
                    if (Rs2Inventory.contains("Lava battlestaff")) {
                        Rs2Inventory.wear("Lava battlestaff");
                        Rs2Inventory.waitForInventoryChanges(3000);
                    }
                } else {
                    Rs2Bank.withdrawAll(true, "Fire rune", true);
                    Rs2Inventory.waitForInventoryChanges(3000);
                    Rs2Bank.withdrawAll(true, "Earth rune", true);
                    Rs2Inventory.waitForInventoryChanges(3000);
                }
            }
            Rs2Bank.withdrawAll(true, "Cosmic rune", true);
            Rs2Inventory.waitForInventoryChanges(3000);
        }
        boolean successfullyWithdrawFood = Rs2Bank.withdrawX(true, config.food().getName(), config.foodAmount(), true);
        if (!successfullyWithdrawFood) {
            Microbot.showMessage(config.food().getName() + " not found in bank. Shutting down");
            shutdown();
        }
        Rs2Inventory.waitForInventoryChanges(3000);
        Rs2Bank.withdrawDeficit("dodgy necklace", config.dodgyNecklaceAmount());
        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
    }

    private void dropAllExceptImportant() {
        Set<String> keep = new HashSet<>();
        if (config.DoNotDropItemList() != null && !config.DoNotDropItemList().isEmpty())
            keep.addAll(Arrays.asList(config.DoNotDropItemList().split(",")));
        Rs2Inventory.getInventoryFood().forEach(food -> keep.add(food.getName()));
        keep.add("dodgy necklace"); keep.add("coins"); keep.add("book of the dead"); keep.add("drakan's medallion");
        if (config.shadowVeil()) Collections.addAll(keep, "Fire rune", "Earth rune", "Cosmic rune");
        keep.addAll(VYRE_SET.keySet()); keep.addAll(ROGUE_SET.keySet());
        Rs2Inventory.dropAllExcept(config.keepItemsAboveValue(), keep.toArray(new String[0]));
    }

    private boolean waitUntilBothInPolygon(WorldPoint[] polygon, Rs2NpcModel npc, long timeoutMs) {
        Microbot.status = "NPC NOT IN AREA.";
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!Microbot.isLoggedIn()) return false;
            boolean npcInside = isPointInPolygon(polygon, npc.getWorldLocation());
            boolean playerInside = isPointInPolygon(polygon, Rs2Player.getWorldLocation());
            if (npcInside && playerInside) {
                return true;
            }
            sleep(250, 350);
        }
        return false;
    }

    private void HopToWorld() {
        int attempts = 0;
        int maxtries = 5;
        Microbot.log("Hopping world, please wait...");
        while (attempts < maxtries) {
            int world = Login.getRandomWorld(true, null);
            Microbot.hopToWorld(world);
            boolean hopSuccess = sleepUntil(() -> Rs2Player.getWorld() == world, 10000);
            if (hopSuccess) break;
            sleep(250, 350);
            attempts++;
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Walker.setTarget(null);
        Microbot.isCantReachTargetDetectionEnabled = false;
    }
}