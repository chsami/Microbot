package net.runelite.client.plugins.microbot.util.magic;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Runes
{
	AIR(1, ItemID.AIR_RUNE),
	WATER(2, ItemID.WATER_RUNE),
	EARTH(3, ItemID.EARTH_RUNE),
	FIRE(4, ItemID.FIRE_RUNE),
	MIND(5, ItemID.MIND_RUNE),
	CHAOS(6, ItemID.CHAOS_RUNE),
	DEATH(7, ItemID.DEATH_RUNE),
	BLOOD(8, ItemID.BLOOD_RUNE),
	COSMIC(9, ItemID.COSMIC_RUNE),
	NATURE(10, ItemID.NATURE_RUNE),
	LAW(11, ItemID.LAW_RUNE),
	BODY(12, ItemID.BODY_RUNE),
	SOUL(13, ItemID.SOUL_RUNE),
	ASTRAL(14, ItemID.ASTRAL_RUNE),
	MIST(15, ItemID.MIST_RUNE),
	MUD(16, ItemID.MUD_RUNE),
	DUST(17, ItemID.DUST_RUNE),
	LAVA(18, ItemID.LAVA_RUNE),
	STEAM(19, ItemID.STEAM_RUNE),
	SMOKE(20, ItemID.SMOKE_RUNE),
	WRATH(21, ItemID.WRATH_RUNE),
	SUNFIRE(22, ItemID.SUNFIRE_RUNE);

	@Getter(AccessLevel.PUBLIC)
	private final int id;
	@Getter(AccessLevel.PUBLIC)
	private final int itemId;

	public Set<Runes> getBaseRunes() {
		switch (this) {
			case MIST: return Set.of(AIR, WATER);
			case DUST: return Set.of(AIR, EARTH);
			case MUD: return Set.of(WATER, EARTH);
			case SMOKE: return Set.of(AIR, FIRE);
			case STEAM: return Set.of(WATER, FIRE);
			case LAVA: return Set.of(EARTH, FIRE);
			default: return Set.of(this);
		}
	}

	private static final Map<Integer, Runes> BY_VARBIT_ID = Arrays.stream(values()).collect(Collectors.toMap(Runes::getId, Function.identity()));

	public static Runes byVarbitId(int varbitValue)
	{
		return BY_VARBIT_ID.get(varbitValue);
	}

	public static Runes byItemId(int itemId) {
		return Arrays.stream(values()).filter(r -> r.getItemId() == itemId).findFirst().orElse(null);
	}
}
