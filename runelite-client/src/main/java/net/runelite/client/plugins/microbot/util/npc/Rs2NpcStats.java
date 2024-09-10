package net.runelite.client.plugins.microbot.util.npc;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public final class Rs2NpcStats {
    public static final TypeAdapter<Rs2NpcStats> NPC_STATS_TYPE_ADAPTER = new TypeAdapter<Rs2NpcStats>() {
        public void write(JsonWriter out, Rs2NpcStats value) {
            throw new UnsupportedOperationException("Not supported");
        }

        public Rs2NpcStats read(JsonReader in) throws IOException {
            in.beginObject();
            Builder builder = Rs2NpcStats.builder();

            while (in.hasNext()) {
                switch (in.nextName()) {
                    case "id":
                        builder.id(in.nextInt());
                        break;
                    case "name":
                        builder.name(in.nextString());
                        break;
                    case "last_updated":
                        builder.lastUpdated(in.nextString());
                        break;
                    case "incomplete":
                        builder.incomplete(in.nextBoolean());
                        break;
                    case "members":
                        builder.members(in.nextBoolean());
                        break;
                    case "release_date":
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            builder.releaseDate(null);
                        } else
                            builder.releaseDate(in.nextString());
                        break;
                    case "combat_level":
                        builder.combatLevel(in.nextInt());
                        break;
                    case "size":
                        builder.size(in.nextInt());
                        break;
                    case "hitpoints":
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            builder.hitpoints(null);
                        } else
                            builder.hitpoints(in.nextInt());
                        break;
                    case "max_hit":
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            builder.maxHit(null);
                        } else
                            builder.maxHit(in.nextInt());
                        break;
                    case "attack_type":
                        in.beginArray();
                        while (in.hasNext()) {
                            builder.attackType(Collections.singletonList(in.nextString()));
                        }
                        in.endArray();
                        break;
                    case "attack_speed":
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            builder.attackSpeed(null);
                        } else
                            builder.attackSpeed(in.nextInt());
                        break;
                    case "aggressive":
                        builder.aggressive(in.nextBoolean());
                        break;
                    case "poisonous":
                        builder.poisonous(in.nextBoolean());
                        break;
                    case "venomous":
                        builder.venomous(in.nextBoolean());
                        break;
                    case "immune_poison":
                        builder.immunePoison(in.nextBoolean());
                        break;
                    case "immune_venom":
                        builder.immuneVenom(in.nextBoolean());
                        break;
                    case "attributes":
                        in.beginArray();
                        while (in.hasNext()) {
                            builder.attributes(Collections.singletonList(in.nextString()));
                        }
                        in.endArray();
                        break;
                    case "category":
                        in.beginArray();
                        while (in.hasNext()) {
                            builder.category(Collections.singletonList(in.nextString()));
                        }
                        in.endArray();
                        break;
                    case "slayer_monster":
                        builder.slayerMonster(in.nextBoolean());
                        break;
                    case "slayer_level":
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            builder.slayerLevel(null);
                        } else {
                            builder.slayerLevel(in.nextInt());
                        }
                        //builder.slayerLevel(in.nextInt());
                        break;
                    case "slayer_xp":
                        if (in.peek() == JsonToken.NULL) {
                            in.nextNull();
                            builder.slayerXp(null);
                        } else {
                            builder.slayerXp((float) in.nextDouble());
                        }
                        break;
                    case "slayer_masters":
                        in.beginArray();
                        while (in.hasNext()) {
                            builder.slayerMasters(Collections.singletonList(in.nextString()));
                        }
                        in.endArray();
                        break;
                    case "duplicate":
                        builder.duplicate(in.nextBoolean());
                        break;
                    case "examine":
                        builder.examine(in.nextString());
                        break;
                    case "wiki_name":
                        builder.wikiName(in.nextString());
                        break;
                    case "wiki_url":
                        builder.wikiUrl(in.nextString());
                        break;
                    case "attack_level":
                        builder.attackLevel(in.nextInt());
                        break;
                    case "strength_level":
                        builder.strengthLevel(in.nextInt());
                        break;
                    case "defence_level":
                        builder.defenceLevel(in.nextInt());
                        break;
                    case "magic_level":
                        builder.magicLevel(in.nextInt());
                        break;
                    case "ranged_level":
                        builder.rangedLevel(in.nextInt());
                        break;
                    case "attack_bonus":
                        builder.attackBonus(in.nextInt());
                        break;
                    case "strength_bonus":
                        builder.strengthBonus(in.nextInt());
                        break;
                    case "attack_magic":
                        builder.attackMagic(in.nextInt());
                        break;
                    case "magic_bonus":
                        builder.magicBonus(in.nextInt());
                        break;
                    case "attack_ranged":
                        builder.attackRanged(in.nextInt());
                        break;
                    case "ranged_bonus":
                        builder.rangedBonus(in.nextInt());
                        break;
                    case "defence_stab":
                        builder.defenceStab(in.nextInt());
                        break;
                    case "defence_slash":
                        builder.defenceSlash(in.nextInt());
                        break;
                    case "defence_crush":
                        builder.defenceCrush(in.nextInt());
                        break;
                    case "defence_magic":
                        builder.defenceMagic(in.nextInt());
                        break;
                    case "defence_ranged":
                        builder.defenceRanged(in.nextInt());
                        break;
                    case "drops":
                        in.beginArray();
                        while (in.hasNext()) {
                            in.beginObject();
                            int dropId = -1;
                            String dropName = null;
                            boolean dropMembers = false;
                            String dropQuantity = null;
                            boolean dropNoted = false;
                            float dropRarity = -1;
                            int dropRolls = -1;
                            while (in.hasNext()) {
                                switch (in.nextName()) {
                                    case "id":
                                        dropId = in.nextInt();
                                        break;
                                    case "name":
                                        dropName = in.nextString();
                                        break;
                                    case "members":
                                        dropMembers = in.nextBoolean();
                                        break;
                                    case "quantity":
                                        if (in.peek() == JsonToken.NULL) {
                                            in.nextNull();
                                            dropQuantity = null;
                                        } else
                                            dropQuantity = in.nextString();
                                        break;
                                    case "noted":
                                        dropNoted = in.nextBoolean();
                                        break;
                                    case "rarity":
                                        dropRarity = (float) in.nextDouble();
                                        break;
                                    case "rolls":
                                        dropRolls = in.nextInt();
                                        break;
                                    default:
                                        in.skipValue();
                                }
                            }
                            in.endObject();
                            builder.drops(Collections.singletonList(new Drop(dropId, dropName, dropMembers, dropQuantity, dropNoted, dropRarity, dropRolls)));
                        }
                        in.endArray();
                        break;
                    default:
                        in.skipValue();
                }
            }

            in.endObject();
            return builder.build();
        }
    };
    private final int id;
    private final String name;
    private final String lastUpdated;
    private final boolean incomplete;
    private final boolean members;
    private final String releaseDate;
    private final int combatLevel;
    private final int size;
    private final Integer hitpoints;
    private final Integer maxHit;
    private final List<String> attackType;
    private final Integer attackSpeed;
    private final boolean aggressive;
    private final boolean poisonous;
    private final boolean venomous;
    private final boolean immunePoison;
    private final boolean immuneVenom;
    private final List<String> attributes;
    private final List<String> category;
    private final boolean slayerMonster;
    private final Integer slayerLevel;
    private final Float slayerXp;
    private final List<String> slayerMasters;
    private final boolean duplicate;
    private final String examine;
    private final String wikiName;
    private final String wikiUrl;
    private final int attackLevel;
    private final int strengthLevel;
    private final int defenceLevel;
    private final int magicLevel;
    private final int rangedLevel;
    private final int attackBonus;
    private final int strengthBonus;
    private final int attackMagic;
    private final int magicBonus;
    private final int attackRanged;
    private final int rangedBonus;
    private final int defenceStab;
    private final int defenceSlash;
    private final int defenceCrush;
    private final int defenceMagic;
    private final int defenceRanged;
    private final List<Drop> drops;

    public Rs2NpcStats(
            int id, String name, String lastUpdated, boolean incomplete, boolean members,
            String releaseDate, int combatLevel, int size, Integer hitpoints, Integer maxHit,
            List<String> attackType, Integer attackSpeed, boolean aggressive, boolean poisonous,
            boolean venomous, boolean immunePoison, boolean immuneVenom, List<String> attributes,
            List<String> category, boolean slayerMonster, Integer slayerLevel, Float slayerXp,
            List<String> slayerMasters, boolean duplicate, String examine, String wikiName, String wikiUrl,
            int attackLevel, int strengthLevel, int defenceLevel, int magicLevel, int rangedLevel,
            int attackBonus, int strengthBonus, int attackMagic, int magicBonus, int attackRanged,
            int rangedBonus, int defenceStab, int defenceSlash, int defenceCrush, int defenceMagic,
            int defenceRanged, List<Drop> drops) {

        this.id = id;
        this.name = name;
        this.lastUpdated = lastUpdated;
        this.incomplete = incomplete;
        this.members = members;
        this.releaseDate = releaseDate;
        this.combatLevel = combatLevel;
        this.size = size;
        this.hitpoints = hitpoints;
        this.maxHit = maxHit;
        this.attackType = attackType;
        this.attackSpeed = attackSpeed;
        this.aggressive = aggressive;
        this.poisonous = poisonous;
        this.venomous = venomous;
        this.immunePoison = immunePoison;
        this.immuneVenom = immuneVenom;
        this.attributes = attributes;
        this.category = category;
        this.slayerMonster = slayerMonster;
        this.slayerLevel = slayerLevel;
        this.slayerXp = slayerXp;
        this.slayerMasters = slayerMasters;
        this.duplicate = duplicate;
        this.examine = examine;
        this.wikiName = wikiName;
        this.wikiUrl = wikiUrl;
        this.attackLevel = attackLevel;
        this.strengthLevel = strengthLevel;
        this.defenceLevel = defenceLevel;
        this.magicLevel = magicLevel;
        this.rangedLevel = rangedLevel;
        this.attackBonus = attackBonus;
        this.strengthBonus = strengthBonus;
        this.attackMagic = attackMagic;
        this.magicBonus = magicBonus;
        this.attackRanged = attackRanged;
        this.rangedBonus = rangedBonus;
        this.defenceStab = defenceStab;
        this.defenceSlash = defenceSlash;
        this.defenceCrush = defenceCrush;
        this.defenceMagic = defenceMagic;
        this.defenceRanged = defenceRanged;
        this.drops = drops;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "Rs2NpcStats{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", lastUpdated='" + lastUpdated + '\'' +
                ", incomplete=" + incomplete +
                ", members=" + members +
                ", releaseDate='" + releaseDate + '\'' +
                ", combatLevel=" + combatLevel +
                ", size=" + size +
                ", hitpoints=" + hitpoints +
                ", maxHit=" + maxHit +
                ", attackType=" + attackType +
                ", attackSpeed=" + attackSpeed +
                ", aggressive=" + aggressive +
                ", poisonous=" + poisonous +
                ", venomous=" + venomous +
                ", immunePoison=" + immunePoison +
                ", immuneVenom=" + immuneVenom +
                ", attributes=" + attributes +
                ", category=" + category +
                ", slayerMonster=" + slayerMonster +
                ", slayerLevel=" + slayerLevel +
                ", slayerXp=" + slayerXp +
                ", slayerMasters=" + slayerMasters +
                ", duplicate=" + duplicate +
                ", examine='" + examine + '\'' +
                ", wikiName='" + wikiName + '\'' +
                ", wikiUrl='" + wikiUrl + '\'' +
                ", attackLevel=" + attackLevel +
                ", strengthLevel=" + strengthLevel +
                ", defenceLevel=" + defenceLevel +
                ", magicLevel=" + magicLevel +
                ", rangedLevel=" + rangedLevel +
                ", attackBonus=" + attackBonus +
                ", strengthBonus=" + strengthBonus +
                ", attackMagic=" + attackMagic +
                ", magicBonus=" + magicBonus +
                ", attackRanged=" + attackRanged +
                ", rangedBonus=" + rangedBonus +
                ", defenceStab=" + defenceStab +
                ", defenceSlash=" + defenceSlash +
                ", defenceCrush=" + defenceCrush +
                ", defenceMagic=" + defenceMagic +
                ", defenceRanged=" + defenceRanged +
                ", drops=" + drops +
                '}';
    }

    public boolean isMembers() {
        return members;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLastUpdated() {
        return lastUpdated;
    }

    public boolean isIncomplete() {
        return incomplete;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public int getCombatLevel() {
        return combatLevel;
    }

    public int getSize() {
        return size;
    }

    public Integer getHitpoints() {
        return hitpoints;
    }

    public Integer getMaxHit() {
        return maxHit;
    }

    public List<String> getAttackType() {
        return attackType;
    }

    public Integer getAttackSpeed() {
        return attackSpeed;
    }

    public boolean isAggressive() {
        return aggressive;
    }

    public boolean isPoisonous() {
        return poisonous;
    }

    public boolean isVenomous() {
        return venomous;
    }

    public boolean isImmunePoison() {
        return immunePoison;
    }

    public boolean isImmuneVenom() {
        return immuneVenom;
    }

    public List<String> getAttributes() {
        return attributes;
    }

    public List<String> getCategory() {
        return category;
    }

    public boolean isSlayerMonster() {
        return slayerMonster;
    }

    public Integer getSlayerLevel() {
        return slayerLevel;
    }

    public Float getSlayerXp() {
        return slayerXp;
    }

    public List<String> getSlayerMasters() {
        return slayerMasters;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public String getExamine() {
        return examine;
    }

    public String getWikiName() {
        return wikiName;
    }

    public String getWikiUrl() {
        return wikiUrl;
    }

    public int getAttackLevel() {
        return attackLevel;
    }

    public int getStrengthLevel() {
        return strengthLevel;
    }

    public int getDefenceLevel() {
        return defenceLevel;
    }

    public int getMagicLevel() {
        return magicLevel;
    }

    public int getRangedLevel() {
        return rangedLevel;
    }

    public int getAttackBonus() {
        return attackBonus;
    }

    public int getStrengthBonus() {
        return strengthBonus;
    }

    public int getAttackMagic() {
        return attackMagic;
    }

    public int getMagicBonus() {
        return magicBonus;
    }

    public int getAttackRanged() {
        return attackRanged;
    }

    public int getRangedBonus() {
        return rangedBonus;
    }

    public int getDefenceStab() {
        return defenceStab;
    }

    public int getDefenceSlash() {
        return defenceSlash;
    }

    public int getDefenceCrush() {
        return defenceCrush;
    }

    public int getDefenceMagic() {
        return defenceMagic;
    }

    public int getDefenceRanged() {
        return defenceRanged;
    }

    public List<Drop> getDrops() {
        return drops;
    }

    public double calculateXpModifier() {
        double averageLevel = Math.floor((this.attackLevel + this.strengthLevel + this.defenceLevel + this.hitpoints) / 4);
        double averageDefBonus = Math.floor((this.defenceStab + this.defenceSlash + this.defenceCrush) / 3);
        return 1.0 + Math.floor(averageLevel * (averageDefBonus + (double) this.strengthBonus + (double) this.attackBonus) / 5120.0) / 40.0;
    }

    // Define the Drop class similarly to the original
    public static final class Drop {
        private final int id;
        private final String name;
        private final boolean members;
        private final String quantity;
        private final boolean noted;
        private final float rarity;
        private final int rolls;

        public Drop(int id, String name, boolean members, String quantity, boolean noted, float rarity, int rolls) {
            this.id = id;
            this.name = name;
            this.members = members;
            this.quantity = quantity;
            this.noted = noted;
            this.rarity = rarity;
            this.rolls = rolls;
        }

        // Add getter methods for Drop fields here
    }

    public static final class Builder {
        private int id;
        private String name;
        private String lastUpdated;
        private boolean incomplete;
        private boolean members;
        private String releaseDate;
        private int combatLevel;
        private int size;
        private Integer hitpoints;
        private Integer maxHit;
        private List<String> attackType;
        private Integer attackSpeed;
        private boolean aggressive;
        private boolean poisonous;
        private boolean venomous;
        private boolean immunePoison;
        private boolean immuneVenom;
        private List<String> attributes;
        private List<String> category;
        private boolean slayerMonster;
        private Integer slayerLevel;
        private Float slayerXp;
        private List<String> slayerMasters;
        private boolean duplicate;
        private String examine;
        private String wikiName;
        private String wikiUrl;
        private int attackLevel;
        private int strengthLevel;
        private int defenceLevel;
        private int magicLevel;
        private int rangedLevel;
        private int attackBonus;
        private int strengthBonus;
        private int attackMagic;
        private int magicBonus;
        private int attackRanged;
        private int rangedBonus;
        private int defenceStab;
        private int defenceSlash;
        private int defenceCrush;
        private int defenceMagic;
        private int defenceRanged;
        private List<Drop> drops;

        // Builder methods for all fields
        public Builder id(int id) {
            this.id = id;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lastUpdated(String lastUpdated) {
            this.lastUpdated = lastUpdated;
            return this;
        }

        public Builder incomplete(boolean incomplete) {
            this.incomplete = incomplete;
            return this;
        }

        public Builder members(boolean members) {
            this.members = members;
            return this;
        }

        public Builder releaseDate(String releaseDate) {
            this.releaseDate = releaseDate;
            return this;
        }

        public Builder combatLevel(int combatLevel) {
            this.combatLevel = combatLevel;
            return this;
        }

        public Builder size(int size) {
            this.size = size;
            return this;
        }

        public Builder hitpoints(Integer hitpoints) {
            this.hitpoints = hitpoints;
            return this;
        }

        public Builder maxHit(Integer maxHit) {
            this.maxHit = maxHit;
            return this;
        }

        public Builder attackType(List<String> attackType) {
            this.attackType = attackType;
            return this;
        }

        public Builder attackSpeed(Integer attackSpeed) {
            this.attackSpeed = attackSpeed;
            return this;
        }

        public Builder aggressive(boolean aggressive) {
            this.aggressive = aggressive;
            return this;
        }

        public Builder poisonous(boolean poisonous) {
            this.poisonous = poisonous;
            return this;
        }

        public Builder venomous(boolean venomous) {
            this.venomous = venomous;
            return this;
        }

        public Builder immunePoison(boolean immunePoison) {
            this.immunePoison = immunePoison;
            return this;
        }

        public Builder immuneVenom(boolean immuneVenom) {
            this.immuneVenom = immuneVenom;
            return this;
        }

        public Builder attributes(List<String> attributes) {
            this.attributes = attributes;
            return this;
        }

        public Builder category(List<String> category) {
            this.category = category;
            return this;
        }

        public Builder slayerMonster(boolean slayerMonster) {
            this.slayerMonster = slayerMonster;
            return this;
        }

        public Builder slayerLevel(Integer slayerLevel) {
            this.slayerLevel = slayerLevel;
            return this;
        }

        public Builder slayerXp(Float slayerXp) {
            this.slayerXp = slayerXp;
            return this;
        }

        public Builder slayerMasters(List<String> slayerMasters) {
            this.slayerMasters = slayerMasters;
            return this;
        }

        public Builder duplicate(boolean duplicate) {
            this.duplicate = duplicate;
            return this;
        }

        public Builder examine(String examine) {
            this.examine = examine;
            return this;
        }

        public Builder wikiName(String wikiName) {
            this.wikiName = wikiName;
            return this;
        }

        public Builder wikiUrl(String wikiUrl) {
            this.wikiUrl = wikiUrl;
            return this;
        }

        public Builder attackLevel(int attackLevel) {
            this.attackLevel = attackLevel;
            return this;
        }

        public Builder strengthLevel(int strengthLevel) {
            this.strengthLevel = strengthLevel;
            return this;
        }

        public Builder defenceLevel(int defenceLevel) {
            this.defenceLevel = defenceLevel;
            return this;
        }

        public Builder magicLevel(int magicLevel) {
            this.magicLevel = magicLevel;
            return this;
        }

        public Builder rangedLevel(int rangedLevel) {
            this.rangedLevel = rangedLevel;
            return this;
        }

        public Builder attackBonus(int attackBonus) {
            this.attackBonus = attackBonus;
            return this;
        }

        public Builder strengthBonus(int strengthBonus) {
            this.strengthBonus = strengthBonus;
            return this;
        }

        public Builder attackMagic(int attackMagic) {
            this.attackMagic = attackMagic;
            return this;
        }

        public Builder magicBonus(int magicBonus) {
            this.magicBonus = magicBonus;
            return this;
        }

        public Builder attackRanged(int attackRanged) {
            this.attackRanged = attackRanged;
            return this;
        }

        public Builder rangedBonus(int rangedBonus) {
            this.rangedBonus = rangedBonus;
            return this;
        }

        public Builder defenceStab(int defenceStab) {
            this.defenceStab = defenceStab;
            return this;
        }

        public Builder defenceSlash(int defenceSlash) {
            this.defenceSlash = defenceSlash;
            return this;
        }

        public Builder defenceCrush(int defenceCrush) {
            this.defenceCrush = defenceCrush;
            return this;
        }

        public Builder defenceMagic(int defenceMagic) {
            this.defenceMagic = defenceMagic;
            return this;
        }

        public Builder defenceRanged(int defenceRanged) {
            this.defenceRanged = defenceRanged;
            return this;
        }

        public Builder drops(List<Drop> drops) {
            this.drops = drops;
            return this;
        }

        public Rs2NpcStats build() {
            return new Rs2NpcStats(
                    id, name, lastUpdated, incomplete, members, releaseDate, combatLevel, size, hitpoints, maxHit,
                    attackType, attackSpeed, aggressive, poisonous, venomous, immunePoison, immuneVenom, attributes,
                    category, slayerMonster, slayerLevel, slayerXp, slayerMasters, duplicate, examine, wikiName, wikiUrl,
                    attackLevel, strengthLevel, defenceLevel, magicLevel, rangedLevel, attackBonus, strengthBonus,
                    attackMagic, magicBonus, attackRanged, rangedBonus, defenceStab, defenceSlash, defenceCrush,
                    defenceMagic, defenceRanged, drops
            );
        }
    }

}
