package net.runelite.client.plugins.microbot.util.magic;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.Getter;
import net.runelite.api.gameval.ItemID;

@Getter
public enum Runes {
	AIR(1, ItemID.AIRRUNE),
	WATER(2, ItemID.WATERRUNE),
	EARTH(3, ItemID.EARTHRUNE),
	FIRE(4, ItemID.FIRERUNE),
	MIND(5, ItemID.MINDRUNE),
	CHAOS(6, ItemID.CHAOSRUNE),
	DEATH(7, ItemID.DEATHRUNE),
	BLOOD(8, ItemID.BLOODRUNE),
	COSMIC(9, ItemID.COSMICRUNE),
	NATURE(10, ItemID.NATURERUNE),
	LAW(11, ItemID.LAWRUNE),
	BODY(12, ItemID.BODYRUNE),
	SOUL(13, ItemID.SOULRUNE),
	ASTRAL(14, ItemID.ASTRALRUNE),
	MIST(15, ItemID.MISTRUNE, AIR, WATER),
	MUD(16, ItemID.MUDRUNE, WATER, EARTH),
	DUST(17, ItemID.DUSTRUNE, AIR, EARTH),
	LAVA(18, ItemID.LAVARUNE, EARTH, FIRE),
	STEAM(19, ItemID.STEAMRUNE, WATER, FIRE),
	SMOKE(20, ItemID.SMOKERUNE, AIR, FIRE),
	WRATH(21, ItemID.WRATHRUNE),
	SUNFIRE(22, ItemID.SUNFIRERUNE);

	@Getter(AccessLevel.PUBLIC)
	private final int id;
	@Getter(AccessLevel.PUBLIC)
	private final int itemId;
	@Getter
	private final Runes[] baseRunes;

	Runes(int id, int itemId, Runes... baseRunes) {
		this.id = id;
		this.itemId = itemId;
		this.baseRunes = baseRunes;
	}

	public boolean providesRune(Runes rune) {
		if (this == rune) return true;
		return Arrays.stream(getBaseRunes()).anyMatch(baseRune -> rune == baseRune);
	}

	private static final Runes[] EMPTY_RUNES_ARRAY = new Runes[0];
	private static final Map<Integer, Runes> BY_VARBIT_ID = Arrays.stream(values())
			.collect(Collectors.toMap(Runes::getId, Function.identity()));
	private static final Map<Integer, Runes> BY_ITEM_ID = Arrays.stream(values())
			.collect(Collectors.toMap(Runes::getItemId, Function.identity()));
	private static final Map<Runes, Runes[]> COMBO_RUNES = Arrays.stream(values())
			.collect(Collectors.toMap(Function.identity(), entry -> {
				final Runes[] comboRunes = Arrays.stream(values())
						.filter(rune -> rune.providesRune(entry))
						.filter(rune -> rune != entry)
						.toArray(Runes[]::new);
				if (comboRunes.length == 0) return EMPTY_RUNES_ARRAY;
				return comboRunes;
			}));

	public static Runes byVarbitId(int varbitValue) {
		return BY_VARBIT_ID.getOrDefault(varbitValue,null);
	}

	public static Runes byItemId(int itemId) {
		return BY_ITEM_ID.getOrDefault(itemId,null);
	}

	public static Runes[] getComboRunes(Runes rune) {
		return COMBO_RUNES.getOrDefault(rune, EMPTY_RUNES_ARRAY);
	}
}
