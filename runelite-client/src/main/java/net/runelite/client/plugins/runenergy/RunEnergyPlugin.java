/*
 * Copyright (c) 2018, Sean Dewar <https://github.com/seandewar>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.runenergy;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;
import org.apache.commons.lang3.StringUtils;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
	name = "Run Energy",
	description = "Show various information related to run energy",
	tags = {"overlay", "stamina"},
	enabledByDefault = true,
	alwaysOn = true
)
@Slf4j
public class RunEnergyPlugin extends Plugin
{
	@Getter
	private enum GracefulEquipmentSlot
	{
		HEAD(EquipmentInventorySlot.HEAD.getSlotIdx(), 3, ItemID.GRACEFUL_HOOD),
		BODY(EquipmentInventorySlot.BODY.getSlotIdx(), 4, ItemID.GRACEFUL_TOP),
		LEGS(EquipmentInventorySlot.LEGS.getSlotIdx(), 4, ItemID.GRACEFUL_LEGS),
		GLOVES(EquipmentInventorySlot.GLOVES.getSlotIdx(), 3, ItemID.GRACEFUL_GLOVES),
		BOOTS(EquipmentInventorySlot.BOOTS.getSlotIdx(), 3, ItemID.GRACEFUL_BOOTS),
		// Agility skill capes and the non-cosmetic Max capes also count for the Graceful set effect
		CAPE(EquipmentInventorySlot.CAPE.getSlotIdx(), 3, ItemID.GRACEFUL_CAPE, ItemID.SKILLCAPE_AGILITY, ItemID.SKILLCAPE_MAX_WORN);

		private final int index;
		private final int boost;
		private final Set<Integer> items;

		GracefulEquipmentSlot(int index, int boost, int... baseItems)
		{
			this.index = index;
			this.boost = boost;

			final ImmutableSet.Builder<Integer> itemsBuilder = ImmutableSet.builder();
			for (int item : baseItems)
			{
				itemsBuilder.addAll(ItemVariationMapping.getVariations(item));
			}
			items = itemsBuilder.build();
		}

		private static final int TOTAL_BOOSTS = Arrays.stream(values()).mapToInt(GracefulEquipmentSlot::getBoost).sum();
	}

	// Full set grants an extra 10% boost to recovery rate
	private static final int GRACEFUL_FULL_SET_BOOST_BONUS = 10;
	// number of charges for roe passive effect
	private static final int RING_OF_ENDURANCE_PASSIVE_EFFECT = 500;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private Client client;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private RunEnergyOverlay energyOverlay;

	@Inject
	private RunEnergyConfig energyConfig;

	@Inject
	private ConfigManager configManager;

	private int lastCheckTick;
	private boolean roeWarningSent;
	private static boolean localPlayerRunningToDestination;
	private WorldPoint prevLocalPlayerLocation;

	@Provides
	RunEnergyConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RunEnergyConfig.class);
	}

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(energyOverlay);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(energyOverlay);
		localPlayerRunningToDestination = false;
		prevLocalPlayerLocation = null;
		lastCheckTick = -1;
		roeWarningSent = false;
		resetRunOrbText();
	}

	static Integer getRingOfEnduranceCharges() {
		return Microbot.getConfigManager().getRSProfileConfiguration(RunEnergyConfig.GROUP_NAME, "ringOfEnduranceCharges", Integer.class);
	}

	void setRingOfEnduranceCharges(int charges)
	{
		configManager.setRSProfileConfiguration(RunEnergyConfig.GROUP_NAME, "ringOfEnduranceCharges", charges);
	}

	static boolean isRingOfEnduranceEquipped()
	{
		final ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.WORN);
		return equipment != null && equipment.count(ItemID.RING_OF_ENDURANCE) == 1;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		localPlayerRunningToDestination =
			prevLocalPlayerLocation != null &&
			client.getLocalDestinationLocation() != null &&
			prevLocalPlayerLocation.distanceTo(client.getLocalPlayer().getWorldLocation()) > 1;

		prevLocalPlayerLocation = client.getLocalPlayer().getWorldLocation();
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired scriptPostFired)
	{
		if (scriptPostFired.getScriptId() == ScriptID.ORBS_UPDATE_RUNENERGY && energyConfig.replaceOrbText())
		{
			setRunOrbText(getEstimatedRunTimeRemaining(true));
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (event.getGroup().equals(RunEnergyConfig.GROUP_NAME) && !energyConfig.replaceOrbText())
		{
			resetRunOrbText();
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		String message = event.getMessage();

		if (message.equals("Your Ring of endurance doubles the duration of your stamina potion's effect."))
		{
			Integer charges = getRingOfEnduranceCharges();
			if (charges == null)
			{
				log.debug("Ring of endurance charge with no known charges");
				return;
			}

			// subtract the used charge
			charges--;
			setRingOfEnduranceCharges(charges);

			if (!roeWarningSent && charges < RING_OF_ENDURANCE_PASSIVE_EFFECT && energyConfig.ringOfEnduranceChargeMessage())
			{
				String chatMessage = new ChatMessageBuilder()
					.append(ChatColorType.HIGHLIGHT)
					.append("Your Ring of endurance now has less than " + RING_OF_ENDURANCE_PASSIVE_EFFECT + " charges. Add more charges to regain its passive stamina effect.")
					.build();

				chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.CONSOLE)
					.runeLiteFormattedMessage(chatMessage)
					.build());

				roeWarningSent = true;
			}
		}
		else if (message.startsWith("Your Ring of endurance is charged with") || message.startsWith("You load your Ring of endurance with"))
		{
			Matcher matcher = Pattern.compile("([0-9,]+)").matcher(message);
			int charges = -1;
			while (matcher.find())
			{
				charges = Integer.parseInt(matcher.group(1).replace(",", ""));
			}

			setRingOfEnduranceCharges(charges);
			if (charges >= RING_OF_ENDURANCE_PASSIVE_EFFECT)
			{
				roeWarningSent = false;
			}
		}
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		// ROE uncharge uses the same script as destroy
		if (!"destroyOnOpKey".equals(event.getEventName()))
		{
			return;
		}

		final int yesOption = client.getIntStack()[client.getIntStackSize() - 1];
		if (yesOption == 1)
		{
			checkDestroyWidget();
		}
	}

	private void setRunOrbText(String text)
	{
		Widget runOrbText = client.getWidget(InterfaceID.Orbs.RUNENERGY_TEXT);

		if (runOrbText != null)
		{
			runOrbText.setText(text);
		}
	}

	private void resetRunOrbText()
	{
		setRunOrbText(Integer.toString(client.getEnergy() / 100));
	}

	String getEstimatedRunTimeRemaining(boolean inSeconds) {
		// Calculate the amount of energy lost every tick.
		// Negative weight has the same depletion effect as 0 kg. >64kg counts as 64kg.
		final int weight = Math.min(Math.max(client.getWeight(), 0), 64);
		final int agilityLevel = client.getBoostedSkillLevel(Skill.AGILITY);

		// New drain rate formula
		double drainRate = (60 + (67 * weight / 64.0)) * (1 - (agilityLevel / 300.0));

		if (client.getVarbitValue(Varbits.RUN_SLOWED_DEPLETION_ACTIVE) != 0) {
			drainRate *= 0.3; // Stamina effect reduces drain rate to 30%
		} else if (isRingOfEnduranceEquipped()) {
			Integer charges = getRingOfEnduranceCharges();
			if (charges != null && charges >= RING_OF_ENDURANCE_PASSIVE_EFFECT) {
				drainRate *= 0.85; // Ring of Endurance passive effect reduces drain rate to 85%
			}
		}

		final double ticksLeft = Math.ceil(client.getEnergy() / drainRate);
		final double secondsLeft = ticksLeft * Constants.GAME_TICK_LENGTH / 1000.0;

		return formatTime(secondsLeft, inSeconds);
	}

	public static int getGracefulRecoveryBoost()
	{
		final ItemContainer equipment = Microbot.getClient().getItemContainer(InventoryID.WORN);

		if (equipment == null)
		{
			return 0;
		}

		final Item[] items = equipment.getItems();

		int boost = 0;

		for (final GracefulEquipmentSlot slot : GracefulEquipmentSlot.values())
		{
			if (items.length <= slot.getIndex())
			{
				continue;
			}

			final Item wornItem = items[slot.getIndex()];

			if (wornItem != null && slot.getItems().contains(wornItem.getId()))
			{
				boost += slot.getBoost();
			}
		}

		if (boost == GracefulEquipmentSlot.TOTAL_BOOSTS)
		{
			boost += GRACEFUL_FULL_SET_BOOST_BONUS;
		}

		return boost;
	}

	static int getEstimatedRecoverTimeRemaining() {
		final int agilityLevel = Microbot.getClient().getBoostedSkillLevel(Skill.AGILITY);

		// Calculate the amount of energy recovered every second
		double recoveryRate = 25 +  Microbot.getClient().getBoostedSkillLevel(Skill.AGILITY) / 6.0;
		recoveryRate *= 1.0 + getGracefulRecoveryBoost() / 100.0;

		final double secondsLeft = (10000 - Microbot.getClient().getEnergy()) / recoveryRate;
		return (int) Math.ceil(secondsLeft);
	}

	private void checkDestroyWidget()
	{
		final int currentTick = client.getTickCount();
		if (lastCheckTick == currentTick)
		{
			return;
		}
		lastCheckTick = currentTick;

		final Widget widgetDestroyItemName = client.getWidget(InterfaceID.Confirmdestroy.NAME);
		if (widgetDestroyItemName == null)
		{
			return;
		}

		if (widgetDestroyItemName.getText().equals("Ring of endurance"))
		{
			setRingOfEnduranceCharges(0);
		}
	}

	public static String calculateTravelTime(int pathLength, boolean inSeconds) {
		final double tickDurationInSeconds = Constants.GAME_TICK_LENGTH / 1000.0;
		final int tilesPerTickRunning = 2; // Running covers 2 tiles per tick
		final int tilesPerTickWalking = 1; // Walking covers 1 tile per tick

		// Weight clamping: Treat negative weight as 0 and weights above 64 as 64
		final int weight = Math.min(Math.max(Microbot.getClient().getWeight(), 0), 64);
		final int agilityLevel = Microbot.getClient().getBoostedSkillLevel(Skill.AGILITY);

		// Energy depletion rate per tick
		double drainRate = (60 + (67 * weight / 64.0)) * (1 - (agilityLevel / 300.0));
		if (Microbot.getClient().getVarbitValue(Varbits.RUN_SLOWED_DEPLETION_ACTIVE) != 0) {
			drainRate *= 0.3; // Stamina effect
		} else if (isRingOfEnduranceEquipped()) {
			Integer charges = getRingOfEnduranceCharges();
			if (charges != null && charges >= RING_OF_ENDURANCE_PASSIVE_EFFECT) {
				drainRate *= 0.85; // Ring of Endurance effect
			}
		}

		// Recovery rate (energy per second), factoring Graceful bonus
		double recoveryRate = 25 + agilityLevel / 6.0;
		recoveryRate *= 1.0 + (getGracefulRecoveryBoost() / 100.0);

		// Initial energy and ticks
		double currentEnergy = Microbot.getClient().getEnergy();
		int runningTicks = 0;
		int walkingTicks = 0;
		int remainingPath = pathLength;

		// Running with available energy
		double runningDistance = Math.min(currentEnergy / drainRate, (double) remainingPath / tilesPerTickRunning) * tilesPerTickRunning;
		if (runningDistance > 0) {
			runningTicks = (int) Math.ceil(runningDistance / tilesPerTickRunning);
			remainingPath -= runningDistance;
			currentEnergy -= runningTicks * drainRate;
		}

		// Walking and recovering
		if (remainingPath > 0) {
			double timeWalking = (double) remainingPath / tilesPerTickWalking * tickDurationInSeconds; // Time walking in seconds
			double recoveredEnergy = timeWalking * recoveryRate;

			// Energy recovered while walking
			double totalEnergyAfterRecovery = Math.min(10000, currentEnergy + recoveredEnergy);

			// Additional running after recovery
			double additionalRunningDistance = Math.min(totalEnergyAfterRecovery / drainRate, (double) remainingPath / tilesPerTickRunning) * tilesPerTickRunning;
			int additionalRunningTicks = (int) Math.ceil(additionalRunningDistance / tilesPerTickRunning);
			remainingPath -= additionalRunningDistance;

			// Final walking (if path is not fully completed by running)
			walkingTicks = (int) Math.ceil((double) remainingPath / tilesPerTickWalking);

			// Total ticks
			runningTicks += additionalRunningTicks;
		}

		int totalTicks = runningTicks + walkingTicks;
		double totalTimeInSeconds = totalTicks * tickDurationInSeconds;

		return formatTime(totalTimeInSeconds, inSeconds);
	}

	private static String formatTime(double secondsLeft, boolean inSeconds) {
		if (inSeconds) {
			return (int) Math.floor(secondsLeft) + "s";
		} else {
			final int minutes = (int) Math.floor(secondsLeft / 60.0);
			final int seconds = (int) Math.floor(secondsLeft - (minutes * 60.0));
			return minutes + ":" + StringUtils.leftPad(Integer.toString(seconds), 2, "0");
		}
	}
}
