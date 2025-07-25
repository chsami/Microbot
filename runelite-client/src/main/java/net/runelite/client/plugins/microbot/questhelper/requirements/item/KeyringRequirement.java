/*
 * Copyright (c) 2022, Zoinkwiz <https://github.com/Zoinkwiz>
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
package net.runelite.client.plugins.microbot.questhelper.requirements.item;

import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.collections.KeyringCollection;
import net.runelite.client.plugins.microbot.questhelper.requirements.runelite.RuneliteRequirement;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.util.Text;

import java.awt.*;
import java.util.Set;
import java.util.stream.Collectors;

// TODO: Convert this to be a TrackedContainer instead?
public class KeyringRequirement extends ItemRequirement
{
	RuneliteRequirement runeliteRequirement;

	@Getter
	KeyringCollection keyringCollection;

	ConfigManager configManager;

	ItemRequirement keyring;

	public KeyringRequirement(String name, ConfigManager configManager, KeyringCollection key)
	{
		super(name, key.getItemID());
		keyring = new ItemRequirement("Steel key ring", ItemID.FAVOUR_KEY_RING);
		runeliteRequirement = new RuneliteRequirement(configManager, key.runeliteName(),
			"true", key.toChatText());
		this.keyringCollection = key;
		this.configManager = configManager;
	}

	public KeyringRequirement(ConfigManager configManager, KeyringCollection key)
	{
		super(key.toChatText(), key.getItemID());
		keyring = new ItemRequirement("Steel key ring", ItemID.FAVOUR_KEY_RING);
		runeliteRequirement = new RuneliteRequirement(configManager, key.runeliteName(),
			"true", key.toChatText());
		this.keyringCollection = key;
		this.configManager = configManager;
	}

	public String chatboxText()
	{
		return keyringCollection.toChatText();
	}

	public void setConfigValue(String value)
	{
		runeliteRequirement.setConfigValue(value);
	}

	@Override
	public ItemRequirement copy()
	{
		KeyringRequirement newItem = new KeyringRequirement(getName(), configManager, keyringCollection);
		newItem.addAlternates(alternateItems);
		newItem.setDisplayItemId(getDisplayItemId());
		newItem.setHighlightInInventory(highlightInInventory);
		newItem.setDisplayMatchedItemName(isDisplayMatchedItemName());
		newItem.setConditionToHide(getConditionToHide());
		newItem.setShouldCheckBank(isShouldCheckBank());
		newItem.setQuestBank(getQuestBank());
		newItem.setTooltip(getTooltip());
		newItem.setUrlSuffix(getUrlSuffix());

		return newItem;
	}

	@Override
	public boolean check(Client client)
	{;
		if (hasKeyOnKeyRing() && keyring.check(client))
		{
			return true;
		}

		return super.check(client);
	}

	public boolean hasKeyOnKeyRing()
	{
		return runeliteRequirement.check();
	}

	@Override
	public Color getColor(Client client, QuestHelperConfig config)
	{
		if (hasKeyOnKeyRing())
		{
			return keyring.getColor(client, config);
		}

		return this.check(client) ? config.passColour() : config.failColour();
	}

	protected String getTooltipFromEnumSet(Set<TrackedContainers> containers)
	{
		String basicTooltip = super.getTooltipFromEnumSet(containers);
		if (hasKeyOnKeyRing())
		{
			return basicTooltip + " on your key ring.";
		}
		return basicTooltip;
	}

	@Override
	protected KeyringRequirement copyOfClass()
	{
		return new KeyringRequirement(getName(), configManager, keyringCollection);
	}
}
