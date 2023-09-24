package net.runelite.client.plugins.jrPlugins.autoZMIAltar

import com.google.inject.Provides
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.runelite.api.Client
import net.runelite.api.ObjectID
import net.runelite.api.Skill
import net.runelite.api.coords.WorldPoint
import net.runelite.api.events.GameTick
import net.runelite.client.config.ConfigManager
import net.runelite.client.eventbus.Subscribe
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.plugins.PluginDescriptor.JR
import net.runelite.client.plugins.microbot.Microbot
import net.runelite.client.plugins.microbot.util.Global.sleep
import net.runelite.client.plugins.microbot.util.MicrobotInventorySetup
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank
import net.runelite.client.plugins.microbot.util.dialogues.Dialogue
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory
import net.runelite.client.plugins.microbot.util.keyboard.VirtualKeyboard
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget
import net.runelite.client.plugins.skillcalculator.skills.MagicAction
import net.runelite.client.ui.overlay.OverlayManager
import javax.inject.Inject

@PluginDescriptor(
    name = JR + "Auto ZMI Altar",
    description = "Auto Ourania (ZMI) Altar",
    tags = ["microbot", "jr", "auto", "runecrafting", "zmi", "ourania altar"],
    enabledByDefault = false
)
class AutoZMIAltar : Plugin() {

    companion object {
        @JvmField
        var xpGained: Long = 0

        @JvmField
        var lvlsGained: Long = 0

        @JvmField
        var totalRuns: Int = 0

        lateinit var version: String
        lateinit var currentState: State
        lateinit var time: String
        lateinit var xpHr: String
    }

    @Inject
    private lateinit var client: Client

    @Inject
    private lateinit var overlayManager: OverlayManager

    @Inject
    private lateinit var autoZMIAltarOverlay: AutoZMIAltarOverlay

    @Inject
    private lateinit var config: AutoZMIAltarConfig

    @Provides
    fun getConfig(configManager: ConfigManager): AutoZMIAltarConfig {
        return configManager.getConfig(AutoZMIAltarConfig::class.java)
    }

    @Subscribe
    fun onGameTick(gameTick: GameTick?) {
        if (config.overlay() && !overlayActive) {
            overlayManager.add(autoZMIAltarOverlay)
            overlayActive = true
        }
        if (!config.overlay() && overlayActive) {
            overlayManager.remove(autoZMIAltarOverlay)
            overlayActive = false
        }
        time = getElapsedTime()
        xpGained = client.getSkillExperience(Skill.RUNECRAFT) - startingXp.toLong()
        xpHr = ((xpGained * 3600000.0 / (System.currentTimeMillis() - startTime)).toInt() / 1000).toString().plus("k")
        lvlsGained = client.getRealSkillLevel(Skill.RUNECRAFT) - startingLvl.toLong()
    }

    enum class State {
        BANKING,
        WALKING,
        RUNECRAFTING
    }

    private var running = false
    private var startTime: Long = 0L
    private var startingXp: Int = 0
    private var startingLvl: Int = 0

    private var overlayActive = false

    private var giantPouchFilled = false
    private var largePouchFilled = false
    private var mediumPouchFilled = false
    private var smallPouchFilled = false

    @OptIn(DelicateCoroutinesApi::class)
    override fun startUp() {
        currentState = State.BANKING
        version = "1.0.3"
        startTime = System.currentTimeMillis()
        startingXp = client.getSkillExperience(Skill.RUNECRAFT)
        startingLvl = client.getRealSkillLevel(Skill.RUNECRAFT)

        if (client.getLocalPlayer() != null) {
            running = true
            if (config.overlay() && !overlayActive) {
                overlayManager.add(autoZMIAltarOverlay)
                overlayActive = true
            }
            GlobalScope.launch { run() }
        }
    }

    private fun run() {
        client.minimapZoom = .5
        while (running && !Microbot.pauseAllScripts) {
            when (currentState) {
                State.BANKING -> handleBankingState()
                State.WALKING -> handleWalkingState()
                State.RUNECRAFTING -> handleRunecraftingState()
            }
        }
    }

    override fun shutDown() {
        running = false
        overlayManager.remove(autoZMIAltarOverlay)
        overlayActive = false
        totalRuns = 0
    }

    private fun handleBankingState() {
        if (config.BANK().bankName == "Edgeville") {
            val bankWorldPoint = WorldPoint(3094, 3491, 0)
            while (client.localPlayer.worldLocation.distanceTo(bankWorldPoint) >= 3 && running) {
                Microbot.getWalkerForKotlin().walkTo(bankWorldPoint)
                sleep(600, 1200)
            }
            try {
                if (!giantPouchFilled || !largePouchFilled || !mediumPouchFilled || !smallPouchFilled){
                    Rs2Npc.interact("banker", "bank")
                    while (!Rs2Bank.isOpen()) sleep(700, 800)
                    Rs2Bank.depositAll()
                    sleep(700, 800)
                }
                while (!giantPouchFilled || !largePouchFilled || !mediumPouchFilled || !smallPouchFilled) {
                    MicrobotInventorySetup.loadInventory(config.INVENTORY())
                    sleep(600, 700)
                    Rs2Bank.closeBank()
                    fillPouches()
                    sleep(300, 400)
                    Rs2Npc.interact("banker", "bank")
                    while (!Rs2Bank.isOpen()) sleep(700, 800)
                }
                MicrobotInventorySetup.loadInventory(config.INVENTORY())
                sleep(600, 700)
                Rs2Bank.closeBank()
                Rs2Magic.cast(MagicAction.OURANIA_TELEPORT)
                sleep(2600, 3000)
                currentState = State.WALKING
            } catch (e: Exception) {
                //e.printStackTrace()
                currentState = State.BANKING
            }
        } else {
            Rs2Magic.cast(MagicAction.OURANIA_TELEPORT)
            sleep(2600, 3000)
            currentState = State.WALKING
        }
    }

    private fun handleWalkingState() {
        val ladderWorldPoint = WorldPoint(2453, 3231, 0)
        val altarWorldPoint = WorldPoint(3056, 5579, 0)
        while (client.localPlayer.worldLocation.distanceTo(ladderWorldPoint) >= 3 && running) {
            Microbot.getWalkerForKotlin().walkTo(ladderWorldPoint)
            sleep(600, 1200)
        }
        Rs2GameObject.interact("Ladder", "Climb")
        sleep(3600, 4200)
        if (config.BANK().bankName == "ZMI Bank") {
            try {
                if (!giantPouchFilled || !largePouchFilled || !mediumPouchFilled || !smallPouchFilled){
                    Rs2Npc.interact("Eniola", "bank")
                    while (!Rs2Bank.isOpen()) sleep(700, 800)
                    Rs2Bank.depositAll()
                    sleep(700, 800)
                }
                while (!giantPouchFilled || !largePouchFilled || !mediumPouchFilled || !smallPouchFilled) {
                    MicrobotInventorySetup.loadInventory(config.INVENTORY())
                    sleep(600, 700)
                    Rs2Bank.closeBank()
                    fillPouches()
                    sleep(300, 400)
                    Rs2Npc.interact("Eniola", "bank")
                    while (!Rs2Bank.isOpen()) sleep(700, 800)
                }
                MicrobotInventorySetup.loadInventory(config.INVENTORY())
                sleep(600, 700)
                Rs2Bank.closeBank()
            } catch (e: Exception) {
                currentState = State.BANKING
            }
        }
        while (client.localPlayer.worldLocation.distanceTo(altarWorldPoint) >= 2 && running) {
            Microbot.getWalkerForKotlin().walkTo(altarWorldPoint)
            sleep(600, 1200)
        }
        currentState = State.RUNECRAFTING
    }

    private fun handleRunecraftingState() {
        Rs2GameObject.interact(29631, "Craft-rune")
        sleep(2400, 3000)
        emptyPouches()
        totalRuns += 1
        Rs2Inventory.interact(config.TELEPORT().toString())
        sleep(5400, 6000)
        if (totalRuns % config.fixPouchesAt() == 0) fixPouches()
        if (config.STAMINA().toString() == "Ornate Pool") {
            Rs2GameObject.interact(ObjectID.ORNATE_POOL_OF_REJUVENATION, "Drink")
            sleep(3000, 3100)
        }
        if (config.BANK().bankName == "Edgeville") {
            Rs2GameObject.interact(29156, "Edgeville")
            sleep(2600, 2800)
            currentState = State.BANKING
        } else {
            currentState = State.BANKING
        }
    }

    private fun fixPouches() {
        Rs2Magic.cast(MagicAction.NPC_CONTACT)
        sleep(1000, 1200)
        Rs2Widget.clickWidget("Dark Mage")
        while (!Dialogue.isInDialogue()) sleep(200, 300)
        Dialogue.clickContinue()
        sleep(2000, 2200)
        VirtualKeyboard.typeString("2")
        sleep(1000, 1200)
        while (!Dialogue.isInDialogue()) sleep(200, 300)
        Dialogue.clickContinue()
        Dialogue.clickContinue()
        VirtualKeyboard.typeString("2")
        currentState = State.BANKING
    }

    private fun fillPouches() {
        if (Rs2Inventory.hasItem("Giant Pouch") && Rs2Inventory.hasItemAmount("Pure essence", 12) && !giantPouchFilled) {
            Rs2Inventory.interact("Giant Pouch", "fill")
            sleep(50, 100)
            giantPouchFilled = true
        }
        if (Rs2Inventory.hasItem("Small Pouch") && Rs2Inventory.hasItemAmount("Pure essence", 3) && !smallPouchFilled) {
            Rs2Inventory.interact("Small Pouch", "fill")
            sleep(50, 100)
            smallPouchFilled = true
        }
        if (Rs2Inventory.hasItem("Medium Pouch") && Rs2Inventory.hasItemAmount("Pure essence", 6) && !mediumPouchFilled) {
            Rs2Inventory.interact("Medium Pouch", "fill")
            sleep(50, 100)
            mediumPouchFilled = true
        }
        if (Rs2Inventory.hasItem("Large Pouch") && Rs2Inventory.hasItemAmount("Pure essence", 9) && !largePouchFilled) {
            Rs2Inventory.interact("Large Pouch", "fill")
            sleep(50, 100)
            largePouchFilled = true
        }
    }

    private fun emptyPouches() {
        if (Rs2Inventory.hasItem("Large Pouch")) {
            Rs2Inventory.useAllItemsFastContains("Large Pouch", "Empty")
            sleep(1200, 1300)
            largePouchFilled = false
            Rs2GameObject.interact(29631, "Craft-rune")
            sleep(600, 800)
        }
        if (Rs2Inventory.hasItem("Medium Pouch")) {
            Rs2Inventory.useAllItemsFastContains("Medium Pouch", "Empty")
            sleep(1200, 1300)
            mediumPouchFilled = false
            Rs2GameObject.interact(29631, "Craft-rune")
            sleep(600, 800)
        }
        if (Rs2Inventory.hasItem("Small Pouch")) {
            Rs2Inventory.useAllItemsFastContains("Small Pouch", "Empty")
            sleep(1200, 1300)
            smallPouchFilled = false
            Rs2GameObject.interact(29631, "Craft-rune")
            sleep(600, 800)
        }
        if (Rs2Inventory.hasItem("Giant Pouch")) {
            for (i in 0..1){
                Rs2Inventory.useAllItemsFastContains("Giant Pouch", "Empty")
                sleep(1200, 1300)
                Rs2GameObject.interact(29631, "Craft-rune")
                sleep(600, 800)
            }
            giantPouchFilled = false
        }
    }

    private fun getElapsedTime(): String {
        val elapsed = System.currentTimeMillis() - startTime
        val hours = elapsed / (1000 * 60 * 60)
        val minutes = (elapsed % (1000 * 60 * 60)) / (1000 * 60)
        val seconds = (elapsed % (1000 * 60)) / 1000
        return "%02d:%02d:%02d".format(hours, minutes, seconds)
    }
}