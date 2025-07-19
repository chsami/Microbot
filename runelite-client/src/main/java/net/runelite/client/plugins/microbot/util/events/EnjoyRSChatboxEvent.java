package net.runelite.client.plugins.microbot.util.events;

import net.runelite.api.annotations.Component;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.BlockingEventPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public class EnjoyRSChatboxEvent implements BlockingEvent
{

	@Component
	private final int ENJOY_RS_CHATBOX_CONTENTS_ID = InterfaceID.Nps.CONTENTS;
	@Component
	private final int ENJOY_RS_CHATBOX_QUESTION_ID = InterfaceID.Nps.QUESTION;
	@Component
	private final int[] ENJOY_RS_CHATBOX_OPTIONS_IDS = {
		InterfaceID.Nps._0,
		InterfaceID.Nps._1,
		InterfaceID.Nps._2,
		InterfaceID.Nps._3,
		InterfaceID.Nps._4,
		InterfaceID.Nps._5,
		InterfaceID.Nps._6,
		InterfaceID.Nps._7,
		InterfaceID.Nps._8,
		InterfaceID.Nps._9,
	};

	@Override
	public boolean validate()
	{
		if (!Microbot.isLoggedIn()) return false;

		boolean isContentsVisible = Rs2Widget.isWidgetVisible(ENJOY_RS_CHATBOX_CONTENTS_ID);
		if (!isContentsVisible) return false;

		return Rs2Widget.hasWidgetText("Old School RuneScape to a friend?", ENJOY_RS_CHATBOX_QUESTION_ID, false);
	}

	@Override
	public boolean execute()
	{
		int randomIndex = Rs2Random.nextInt(0, ENJOY_RS_CHATBOX_OPTIONS_IDS.length - 1, 1.0, true);
		int randomOptionId = ENJOY_RS_CHATBOX_OPTIONS_IDS[randomIndex];
		Widget optionWidget = Rs2Widget.getWidget(randomOptionId);
		Rs2Widget.clickWidget(optionWidget);
		return Global.sleepUntil(() -> !Rs2Widget.isWidgetVisible(ENJOY_RS_CHATBOX_CONTENTS_ID), 10000);
	}

	@Override
	public BlockingEventPriority priority()
	{
		return BlockingEventPriority.NORMAL;
	}
}
