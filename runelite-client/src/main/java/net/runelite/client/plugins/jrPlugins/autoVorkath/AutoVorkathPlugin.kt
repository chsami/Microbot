package net.runelite.client.plugins.jrPlugins.autoVorkath

import com.google.inject.Provides
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import net.runelite.api.Client
import net.runelite.api.ObjectID
import net.runelite.api.Skill
import net.runelite.api.Varbits
import net.runelite.api.coords.LocalPoint
import net.runelite.api.coords.WorldPoint
import net.runelite.api.widgets.Widget
import net.runelite.client.callback.ClientThread
import net.runelite.client.config.ConfigManager
import net.runelite.client.plugins.Plugin
import net.runelite.client.plugins.PluginDescriptor
import net.runelite.client.plugins.PluginDescriptor.JR
import net.runelite.client.plugins.microbot.Microbot
import net.runelite.client.plugins.microbot.Script
import net.runelite.client.plugins.microbot.util.Global.sleep
import net.runelite.client.plugins.microbot.util.MicrobotInventorySetup
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject
import net.runelite.client.plugins.microbot.util.inventory.Inventory
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic
import net.runelite.client.plugins.microbot.util.mouse.VirtualMouse
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc
import net.runelite.client.plugins.microbot.util.player.Rs2Player
import net.runelite.client.plugins.microbot.util.prayer.Prayer
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer
import net.runelite.client.plugins.microbot.util.walker.Walker
import net.runelite.client.plugins.skillcalculator.skills.MagicAction
import javax.inject.Inject


@PluginDescriptor(
    name = JR + "Auto Vorkath",
    description = "JR - Auto vorkath",
    tags = ["vorkath", "microbot", "auto", "auto prayer"],
    enabledByDefault = false
)
class AutoVorkathPlugin : Plugin() {
    @Inject
    private lateinit var client: Client

    @Inject
    private lateinit var clientThread: ClientThread

    @Inject
    private lateinit var config: AutoVorkathConfig

    @Provides
    fun getConfig(configManager: ConfigManager): AutoVorkathConfig {
        return configManager.getConfig(AutoVorkathConfig::class.java)
    }

    private var botState: State? = null
    private var previousBotState: State? = null
    private var running = false
    private val rangeProjectileId = 1477
    private val magicProjectileId = 393
    private val purpleProjectileId = 1471
    private val blueProjectileId = 1479
    private val whiteProjectileId = 395
    private val redProjectileId = 1481
    private val acidProjectileId = 1483
    private val acidRedProjectileId = 1482

    private lateinit var centerTile: WorldPoint
    private lateinit var rightTile: WorldPoint
    private lateinit var leftTile: WorldPoint

    private var foods: Array<Widget>? = null
    private var needsToBank: Boolean = true

    private enum class State {
        RANGE,
        ZOMBIFIED_SPAWN,
        RED_BALL,
        EAT,
        PRAYER,
        RANGE_POTION,
        ANTIFIRE_POTION,
        ANTIVENOM,
        ACID,
        NONE
    }

    override fun startUp() {
        println("Auto Vorkath Plugin Activated")
        botState = State.RANGE
        previousBotState = State.NONE
        running = if(Microbot.isLoggedIn()) true else false
        GlobalScope.launch {
            run()
        }
    }

    override fun shutDown() {
        println("Auto Vorkath Plugin Deactivated")
        running = false
        botState = null
        previousBotState = null
        needsToBank = true
    }

    private fun run() {
        while (running) {
            if (Microbot.pauseAllScripts){ return }
            val vorkath = Rs2Npc.getNpc("vorkath")
            // Check if player is in Vorkath Area
            if (vorkath != null && vorkath.isInteracting) {
                Script.toggleRunEnergy(false)
                centerTile = WorldPoint(vorkath.worldLocation.x + 3, vorkath.worldLocation.y - 5, vorkath.worldLocation.plane)
                rightTile = WorldPoint(centerTile.x + 2, centerTile.y - 3, centerTile.plane)
                leftTile = WorldPoint(centerTile.x - 2, centerTile.y - 3, centerTile.plane)
                // Check what projectile is coming
                if (doesProjectileExistById(redProjectileId)) {
                    botState = State.RED_BALL
                }else if (doesProjectileExistById(acidProjectileId) || doesProjectileExistById(acidRedProjectileId)) {
                    botState = State.ACID
                    //println("Acid")
                } else if (doesProjectileExistById(rangeProjectileId) || doesProjectileExistById(magicProjectileId) || doesProjectileExistById(purpleProjectileId) || doesProjectileExistById(blueProjectileId)) {
                    botState = State.RANGE
                } else if (doesProjectileExistById(whiteProjectileId) || Rs2Npc.getNpc("Zombified Spawn") != null) {
                    botState = State.ZOMBIFIED_SPAWN
                } else if (doesProjectileExistById(redProjectileId)) {
                    botState = State.RED_BALL
                }

                // Check if player needs to eat
                if (clientThread.runOnClientThread { client.getBoostedSkillLevel(Skill.HITPOINTS) } < 40 && botState != State.ACID && botState != State.RED_BALL) {
                    foods = clientThread.runOnClientThread { Inventory.getInventoryFood() }
                    botState = State.EAT
                }

                // Check if player needs to drink prayer potion
                if (clientThread.runOnClientThread { client.getBoostedSkillLevel(Skill.PRAYER) } < 20 && botState != State.ACID && botState != State.RED_BALL) {
                    botState = State.PRAYER
                }

                // Check if player needs to drink range potion
                if(!Rs2Player.hasDivineBastionActive() && !Rs2Player.hasDivineRangedActive() && botState != State.ACID && botState != State.RED_BALL){
                    botState = State.RANGE_POTION
                }

                // Check if player needs to drink antifire potion
                if(!Rs2Player.hasAntiFireActive() && !Rs2Player.hasSuperAntiFireActive() && botState != State.ACID && botState != State.RED_BALL){
                    botState = State.ANTIFIRE_POTION
                }

                // Check if player needs to drink antivenom potion
                if(!Rs2Player.hasAntiVenomActive() && botState != State.ACID && botState != State.RED_BALL){
                    botState = State.ANTIVENOM
                }

                // Handle bot state
                when (botState) {
                    State.RANGE -> if ((clientThread.runOnClientThread { client.getVarbitValue(Varbits.PRAYER_PROTECT_FROM_MISSILES) == 0 }) || previousBotState != State.RANGE) {
                        previousBotState = State.RANGE
                        Rs2Prayer.fastPray(Prayer.PROTECT_RANGE, true)
                        if (config.ACTIVATERIGOUR()){ Rs2Prayer.fastPray(Prayer.RIGOUR, true) }
                        walkToCenterLocation(isPlayerInCenterLocation())
                    }
                    State.ZOMBIFIED_SPAWN -> if (previousBotState != State.ZOMBIFIED_SPAWN) {
                        previousBotState = State.ZOMBIFIED_SPAWN
                        Rs2Prayer.fastPray(Prayer.PROTECT_RANGE, false)
                        if (config.ACTIVATERIGOUR()){ Rs2Prayer.fastPray(Prayer.RIGOUR, false) }
                        eatAt(75)
                        while (Rs2Npc.getNpc("Zombified Spawn") == null) {
                            sleep(100, 200)
                        }
                        if (config.SLAYERSTAFF().toString() == "Cast") {
                            Rs2Magic.castOn(MagicAction.CRUMBLE_UNDEAD, Rs2Npc.getNpc("Zombified Spawn"));
                        } else {
                            Inventory.useItem(config.SLAYERSTAFF().toString())
                        }
                        Rs2Npc.attack("Zombified Spawn")
                        sleep(2300, 2500)
                        Inventory.useItem(config.CROSSBOW().toString())
                        eatAt(75)
                        sleep(600, 1000)
                        Rs2Npc.attack("Vorkath")
                    }
                    // If the player is not walking
                    State.RED_BALL -> if (client.localPlayer.idlePoseAnimation == 1 || doesProjectileExistById(redProjectileId)){
                        previousBotState = State.RED_BALL
                        redBallWalk()
                        sleep(2100, 2200)
                        Rs2Npc.attack("Vorkath")
                    }
                    State.ACID -> if (doesProjectileExistById(acidProjectileId) || doesProjectileExistById(acidRedProjectileId) || Rs2GameObject.findObject(ObjectID.ACID_POOL) != null) {
                        previousBotState = State.ACID
                        acidWalk()
                    }
                    State.EAT -> if (foods?.size!! > 0) {
                        VirtualMouse().click(foods!![0].getBounds())
                        botState = previousBotState
                    } else {
                        println("No food found")
                        // Teleport
                        Inventory.useItem(config.TELEPORT().toString())
                        needsToBank = true
                    }
                    State.PRAYER -> if (Inventory.findItemContains("prayer") != null) {
                        Inventory.useItemContains("prayer")
                        botState = previousBotState
                    } else {
                        println("No prayer potions found")
                        // Teleport
                        Inventory.useItem(config.TELEPORT().toString())
                        needsToBank = true
                    }
                    State.RANGE_POTION -> if (Inventory.findItemContains(config.RANGEPOTION().toString()) != null) {
                        Inventory.useItemContains(config.RANGEPOTION().toString())
                        botState = previousBotState
                    } else {
                        println("No range potions found")
                        // Teleport
                        Inventory.useItem(config.TELEPORT().toString())
                        needsToBank = true
                    }
                    State.ANTIFIRE_POTION -> if (Inventory.findItemContains("antifire") != null) {
                        Inventory.useItemContains("super antifire")
                        botState = previousBotState
                    } else {
                        println("No antifire potions found")
                        // Teleport
                        Inventory.useItem(config.TELEPORT().toString())
                        needsToBank = true
                    }
                    State.ANTIVENOM -> if (Inventory.findItemContains("venom") != null){
                        Inventory.useItemContains("venom")
                        Rs2Player.antiVenomTime = -64; //set this immediatly because the antivenom timer takes a while before it gets triggered
                        botState = previousBotState
                    } else {
                        println("No antivenom potions found")
                        // Teleport
                        Inventory.useItem(config.TELEPORT().toString())
                        needsToBank = true
                    }
                    State.NONE -> println("TODO")
                    else -> botState = State.NONE
                }
            } else if(Rs2Npc.getNpc("vorkath") == null || needsToBank || vorkath.isDead || !vorkath.isInteracting){
                Rs2Prayer.fastPray(Prayer.PROTECT_RANGE, false)
                if (config.ACTIVATERIGOUR()){ Rs2Prayer.fastPray(Prayer.RIGOUR, false) }
                Script.toggleRunEnergy(true)
                // Bank
                if (needsToBank && Rs2Bank.getNearestBank() != null) {
                    Rs2Bank.openBank()
                    Rs2Bank.depositEquipment()
                    Rs2Bank.depositAll()
                    MicrobotInventorySetup.loadEquipment(config.GEAR())
                    MicrobotInventorySetup.loadInventory(config.GEAR())
                    needsToBank = false
                    Rs2Bank.closeBank()
                }
            }
        }
    }

    private fun acidWalk() {
        Rs2Prayer.fastPray(Prayer.PROTECT_RANGE, false)
        if (config.ACTIVATERIGOUR()){ Rs2Prayer.fastPray(Prayer.RIGOUR, false) }
        var clickedTile: WorldPoint
        var toggle = true
        while (botState == State.ACID && previousBotState == State.ACID && (doesProjectileExistById(acidProjectileId) || doesProjectileExistById(acidRedProjectileId))) {
            clickedTile = if (toggle) rightTile else leftTile

            // Check if player's location is equal to the clicked tile location or if it's within one tile of the clicked location.
            val currentPlayerLocation = client.localPlayer.worldLocation

            // Ensure player is at the clickedTile.y before toggling
            if(currentPlayerLocation.y != clickedTile.y) {
                // Walk player to clickedTile.y location
                Microbot.getWalkerForKotlin().walkFastLocal(
                    LocalPoint.fromWorld(
                        client,
                        WorldPoint(centerTile.x, clickedTile.y, currentPlayerLocation.plane)
                    )
                )
                while (client.localPlayer.worldLocation.y != clickedTile.y) {
                    sleep(1)
                }
            } else {
                if (currentPlayerLocation.distanceTo(clickedTile) <= 1) {
                    toggle = !toggle
                    clickedTile = if (toggle) rightTile else leftTile
                }

                Microbot.getWalkerForKotlin().walkFastLocal(LocalPoint.fromWorld(client, clickedTile))
                while (client.localPlayer.worldLocation != clickedTile && client.localPlayer.worldLocation.distanceTo(clickedTile) > 1 && client.localPlayer.worldLocation.y == clickedTile.y && Microbot.isWalking()) {
                    sleep(1)
                }
                toggle = !toggle
            }
        }
    }

    private fun eatAt(health: Int){
        if (clientThread.runOnClientThread { client.getBoostedSkillLevel(Skill.HITPOINTS) } < health && Rs2Npc.getNpc("Vorkath") != null){
            foods = clientThread.runOnClientThread { Inventory.getInventoryFood() }
            val food = if(foods?.size!! > 0) foods!![0] else null
            if(food != null){
                VirtualMouse().click(food.getBounds())
            }else{
                //println("No food found")
                // Teleport
                Inventory.useItem(config.TELEPORT().toString())
            }
        }
    }

    // Check if projectile exists by ID
    private fun doesProjectileExistById(id: Int): Boolean {
        for (projectile in client.projectiles) {
            if (projectile.id == id) {
                //println("Projectile $id found")
                return true
            }
        }
        return false
    }

    // Click 2 tiles west of the player's current location
    private fun redBallWalk() {
        val currentPlayerLocation = client.localPlayer.worldLocation
        val twoTilesEastFromCurrentLocation = WorldPoint(currentPlayerLocation.x + 2, currentPlayerLocation.y, 0)
        Microbot.getWalkerForKotlin().walkFastLocal(LocalPoint.fromWorld(client, twoTilesEastFromCurrentLocation))
    }

    // player location is center location
    private fun isPlayerInCenterLocation(): Boolean {
        val currentPlayerLocation = client.localPlayer.worldLocation
        return currentPlayerLocation.x == centerTile.x && currentPlayerLocation.y == centerTile.y
    }

    // walk to center location
    private fun walkToCenterLocation(isPlayerInCenterLocation: Boolean) {
        if (!isPlayerInCenterLocation) {
            Microbot.getWalkerForKotlin().walkFastLocal(LocalPoint.fromWorld(client, centerTile))
            sleep(2000, 2100)
            Rs2Npc.attack("Vorkath")
        }
    }
}
