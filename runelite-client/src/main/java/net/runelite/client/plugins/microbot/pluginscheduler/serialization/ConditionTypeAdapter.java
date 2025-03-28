package net.runelite.client.plugins.microbot.pluginscheduler.serialization;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.npc.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.*;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Arrays;
import java.util.regex.Pattern;

@Slf4j
public class ConditionTypeAdapter implements JsonSerializer<Condition>, JsonDeserializer<Condition> {
    private static final String TYPE_FIELD = "type";
    private static final String DATA_FIELD = "data";

    @Override
    public JsonElement serialize(Condition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject result = new JsonObject();
        String className = src.getClass().getName();
        result.add(TYPE_FIELD, new JsonPrimitive(className));
        
        JsonObject data = new JsonObject();
        
        // Handle specific condition types
        if (src instanceof LogicalCondition) {
            LogicalCondition logical = (LogicalCondition) src;
            JsonArray conditions = new JsonArray();
            for (Condition condition : logical.getConditions()) {
                conditions.add(context.serialize(condition));
            }
            data.add("conditions", conditions);
        } 
        else if (src instanceof NotCondition) {
            NotCondition not = (NotCondition) src;
            data.add("condition", context.serialize(not.getCondition()));
        }
        else if (src instanceof IntervalCondition) {
            IntervalCondition interval = (IntervalCondition) src;
            data.addProperty("intervalSeconds", interval.getInterval().getSeconds());
            data.addProperty("randomize", interval.isRandomize());
            if (interval.isRandomize()) {
                data.addProperty("randomFactor", interval.getRandomFactor());
            }
            if (interval.getNextTriggerTime() != null) {
                data.addProperty("nextTriggerTimeMillis", interval.getNextTriggerTime().toInstant().toEpochMilli());
            }
        }
        else if (src instanceof DayOfWeekCondition) {
            DayOfWeekCondition dayCondition = (DayOfWeekCondition) src;
            JsonArray days = new JsonArray();
            for (DayOfWeek day : dayCondition.getActiveDays()) {
                days.add(day.name());
            }
            data.add("activeDays", days);
        }
        else if (src instanceof TimeWindowCondition) {
            TimeWindowCondition window = (TimeWindowCondition) src;
            // Current values
            data.addProperty("currentStartTime", window.getCurrentStartTime().toString());
            data.addProperty("currentEndTime", window.getCurrentEndTime().toString());
            
            // Min/Max bounds
            data.addProperty("startTimeMin", window.getStartTimeMin().toString());
            data.addProperty("startTimeMax", window.getStartTimeMax().toString());
            data.addProperty("endTimeMin", window.getEndTimeMin().toString());
            data.addProperty("endTimeMax", window.getEndTimeMax().toString());
        }
        else if (src instanceof SkillLevelCondition) {
            SkillLevelCondition skillLevel = (SkillLevelCondition) src;
            data.addProperty("skill", skillLevel.getSkill().name());
            data.addProperty("targetLevelMin", skillLevel.getTargetLevelMin());
            data.addProperty("targetLevelMax", skillLevel.getTargetLevelMax());
            data.addProperty("currentTargetLevel", skillLevel.getCurrentTargetLevel());
            data.addProperty("startLevel", skillLevel.getStartLevel());
        }
        else if (src instanceof SkillXpCondition) {
            SkillXpCondition skillXp = (SkillXpCondition) src;
            data.addProperty("skill", skillXp.getSkill().name());
            data.addProperty("targetXpMin", skillXp.getTargetXpMin());
            data.addProperty("targetXpMax", skillXp.getTargetXpMax());
            data.addProperty("currentTargetXp", skillXp.getCurrentTargetXp());
            data.addProperty("startXp", skillXp.getStartXp());
        }
        else if (src instanceof LootItemCondition) {
            LootItemCondition item = (LootItemCondition) src;
            data.addProperty("itemName", item.getItemName());
            data.addProperty("targetAmountMin", item.getTargetAmountMin());                                   
            data.addProperty("targetAmountMax", item.getTargetAmountMax());
            data.addProperty("currentTargetAmount", item.getCurrentTargetAmount());
            data.addProperty("currentTrackedCount", item.getCurrentTrackedCount());
        }
        else if (src instanceof InventoryItemCountCondition) {
            InventoryItemCountCondition item = (InventoryItemCountCondition) src;
            data.addProperty("itemName", item.getItemName());
            data.addProperty("targetCountMin", item.getTargetCountMin());
            data.addProperty("targetCountMax", item.getTargetCountMax());
            data.addProperty("includeNoted", item.isIncludeNoted());
            data.addProperty("currentTargetCount", item.getCurrentTargetCount());
            data.addProperty("currentItemCount", item.getCurrentItemCount());
        }
        else if (src instanceof BankItemCountCondition) {
            BankItemCountCondition item = (BankItemCountCondition) src;
            data.addProperty("itemName", item.getItemName());
            data.addProperty("targetCountMin", item.getTargetCountMin());
            data.addProperty("targetCountMax", item.getTargetCountMax());
            data.addProperty("currentTargetCount", item.getCurrentTargetCount());
            data.addProperty("currentItemCount", item.getCurrentItemCount());
        }
        else if (src instanceof PositionCondition) {
            PositionCondition pos = (PositionCondition) src;
            WorldPoint point = pos.getTargetPosition();
            data.addProperty("x", point.getX());
            data.addProperty("y", point.getY());
            data.addProperty("plane", point.getPlane());
            data.addProperty("maxDistance", pos.getMaxDistance());
        }
        else if (src instanceof AreaCondition) {
            AreaCondition areaCondition = (AreaCondition) src;
            WorldArea area = areaCondition.getArea();
            data.addProperty("x", area.getX());
            data.addProperty("y", area.getY());
            data.addProperty("width", area.getWidth());
            data.addProperty("height", area.getHeight());
            data.addProperty("plane", area.getPlane());
        }
        else if (src instanceof RegionCondition) {
            RegionCondition region = (RegionCondition) src;
            JsonArray regionIds = new JsonArray();
            for (Integer regionId : region.getTargetRegions()) {
                regionIds.add(regionId);
            }
            data.add("regionIds", regionIds);
        }
        else if (src instanceof NpcKillCountCondition) {
            NpcKillCountCondition npc = (NpcKillCountCondition) src;
            data.addProperty("npcName", npc.getNpcName());
            data.addProperty("targetCountMin", npc.getTargetCountMin());
            data.addProperty("targetCountMax", npc.getTargetCountMax());
            data.addProperty("currentTargetCount", npc.getCurrentTargetCount());
            data.addProperty("currentKillCount", npc.getCurrentKillCount());
        }
        
        result.add(DATA_FIELD, data);
        return result;
    }

    @Override
    public Condition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
        if (!json.isJsonObject()) {
            return null; // Return null for non-object elements
        }
        
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Check if the type field exists
        if (!jsonObject.has(TYPE_FIELD)) {
            return null;
        }
        
        String typeStr = jsonObject.get(TYPE_FIELD).getAsString();
        JsonObject data = jsonObject.getAsJsonObject(DATA_FIELD);
        
        try {
            Class<?> clazz = Class.forName(typeStr);
            
            // Handle specific condition types based on their class
            if (AndCondition.class.isAssignableFrom(clazz)) {
                return deserializeAndCondition(data, context);
            } 
            else if (OrCondition.class.isAssignableFrom(clazz)) {
                return deserializeOrCondition(data, context);
            }
            else if (NotCondition.class.isAssignableFrom(clazz)) {
                return deserializeNotCondition(data, context);
            }
            else if (IntervalCondition.class.isAssignableFrom(clazz)) {
                return deserializeIntervalCondition(data);
            }
            else if (DayOfWeekCondition.class.isAssignableFrom(clazz)) {
                return deserializeDayOfWeekCondition(data);
            }
            else if (TimeWindowCondition.class.isAssignableFrom(clazz)) {
                return deserializeTimeWindowCondition(data);
            }
            else if (SkillLevelCondition.class.isAssignableFrom(clazz)) {
                return deserializeSkillLevelCondition(data);
            }
            else if (SkillXpCondition.class.isAssignableFrom(clazz)) {
                return deserializeSkillXpCondition(data);
            }
            else if (LootItemCondition.class.isAssignableFrom(clazz)) {
                return deserializeLootItemCondition(data);
            }
            else if (InventoryItemCountCondition.class.isAssignableFrom(clazz)) {
                return deserializeInventoryItemCountCondition(data);
            }
            else if (BankItemCountCondition.class.isAssignableFrom(clazz)) {
                return deserializeBankItemCountCondition(data);
            }
            else if (PositionCondition.class.isAssignableFrom(clazz)) {
                return deserializePositionCondition(data);
            }
            else if (AreaCondition.class.isAssignableFrom(clazz)) {
                return deserializeAreaCondition(data);
            }
            else if (RegionCondition.class.isAssignableFrom(clazz)) {
                return deserializeRegionCondition(data);
            }
            else if (NpcKillCountCondition.class.isAssignableFrom(clazz)) {
                return deserializeNpcKillCountCondition(data);
            }
            
            throw new JsonParseException("Unknown condition type: " + typeStr);
        } catch (ClassNotFoundException e) {
            throw new JsonParseException("Unknown element type: " + typeStr, e);
        }
    }
    
    // Helper methods for each condition type
    private AndCondition deserializeAndCondition(JsonObject data, JsonDeserializationContext context) {
        AndCondition and = new AndCondition();
        JsonArray conditions = data.getAsJsonArray("conditions");
        for (JsonElement element : conditions) {
            Condition condition = context.deserialize(element, Condition.class);
            if (condition != null) {
                and.addCondition(condition);
            }
        }
        return and;
    }
    
    private OrCondition deserializeOrCondition(JsonObject data, JsonDeserializationContext context) {
        OrCondition or = new OrCondition();
        JsonArray conditions = data.getAsJsonArray("conditions");
        for (JsonElement element : conditions) {
            Condition condition = context.deserialize(element, Condition.class);
            if (condition != null) {
                or.addCondition(condition);
            }
        }
        return or;
    }
    
    private NotCondition deserializeNotCondition(JsonObject data, JsonDeserializationContext context) {
        Condition inner = context.deserialize(data.get("condition"), Condition.class);
        return new NotCondition(inner);
    }
    
    private IntervalCondition deserializeIntervalCondition(JsonObject data) {
        long intervalSeconds = data.get("intervalSeconds").getAsLong();
        boolean randomize = data.has("randomize") && data.get("randomize").getAsBoolean();
        double randomFactor = randomize && data.has("randomFactor") ? 
                             data.get("randomFactor").getAsDouble() : 0.0;
        
        Duration interval = Duration.ofSeconds(intervalSeconds);
        return new IntervalCondition(interval, randomize, randomFactor);
    }
    
    private DayOfWeekCondition deserializeDayOfWeekCondition(JsonObject data) {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        JsonArray activeDays = data.getAsJsonArray("activeDays");
        for (JsonElement day : activeDays) {
            days.add(DayOfWeek.valueOf(day.getAsString()));
        }
        return new DayOfWeekCondition(days);
    }
    
    private TimeWindowCondition deserializeTimeWindowCondition(JsonObject data) {
        // Check if we have the new min/max format or the old simple format
        if (data.has("startTimeMin") && data.has("startTimeMax") && 
            data.has("endTimeMin") && data.has("endTimeMax")) {
            
            // Parse min/max times
            LocalTime startTimeMin = LocalTime.parse(data.get("startTimeMin").getAsString());
            LocalTime startTimeMax = LocalTime.parse(data.get("startTimeMax").getAsString());
            LocalTime endTimeMin = LocalTime.parse(data.get("endTimeMin").getAsString());
            LocalTime endTimeMax = LocalTime.parse(data.get("endTimeMax").getAsString());
            
            // Create a constructor for deserializing with min/max values
            // This will need to be adapted to match the actual constructor signature in TimeWindowCondition
            int startHourMin = startTimeMin.getHour();
            int startHourMax = startTimeMax.getHour();
            int startMinuteMin = startTimeMin.getMinute();
            int startMinuteMax = startTimeMax.getMinute();
            int endHourMin = endTimeMin.getHour();
            int endHourMax = endTimeMax.getHour();
            int endMinuteMin = endTimeMin.getMinute();
            int endMinuteMax = endTimeMax.getMinute();
            
            return new TimeWindowCondition(
                startHourMin, startHourMax, 
                startMinuteMin, startMinuteMax,
                endHourMin, endHourMax,
                endMinuteMin, endMinuteMax
            );
        } else {
            // Old format with simple start/end times
            LocalTime start = LocalTime.parse(data.get("startTime").getAsString());
            LocalTime end = LocalTime.parse(data.get("endTime").getAsString());
            return new TimeWindowCondition(start, end);
        }
    }
    
    private SkillLevelCondition deserializeSkillLevelCondition(JsonObject data) {
        Skill skill = Skill.valueOf(data.get("skill").getAsString());
        int targetLevelMin = data.get("targetLevelMin").getAsInt();
        int targetLevelMax = data.get("targetLevelMax").getAsInt();
        return new SkillLevelCondition(skill, targetLevelMin, targetLevelMax);
    }
    
    private SkillXpCondition deserializeSkillXpCondition(JsonObject data) {
        Skill skill = Skill.valueOf(data.get("skill").getAsString());
        int targetXpMin = data.get("targetXpMin").getAsInt();
        int targetXpMax = data.get("targetXpMax").getAsInt();
        return new SkillXpCondition(skill, targetXpMin, targetXpMax);
    }
    
    private LootItemCondition deserializeLootItemCondition(JsonObject data) {
        String itemName = data.get("itemName").getAsString();
        int targetAmountMin = data.get("targetAmountMin").getAsInt();
        int targetAmountMax = data.get("targetAmountMax").getAsInt();
        return LootItemCondition.builder()
                .itemName(itemName)
                .targetAmountMin(targetAmountMin)
                .targetAmountMax(targetAmountMax)
                .build();
    }
    
    private InventoryItemCountCondition deserializeInventoryItemCountCondition(JsonObject data) {
        String itemName = data.get("itemName").getAsString();
        int targetCountMin = data.get("targetCountMin").getAsInt();
        int targetCountMax = data.get("targetCountMax").getAsInt();
        boolean includeNoted = data.get("includeNoted").getAsBoolean();
        
        return InventoryItemCountCondition.builder()
                .itemName(itemName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .includeNoted(includeNoted)
                .build();
    }
    
    private BankItemCountCondition deserializeBankItemCountCondition(JsonObject data) {
        String itemName = data.get("itemName").getAsString();
        int targetCountMin = data.get("targetCountMin").getAsInt();
        int targetCountMax = data.get("targetCountMax").getAsInt();
        
        return BankItemCountCondition.builder()
                .itemName(itemName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .build();
    }
    
    private PositionCondition deserializePositionCondition(JsonObject data) {
        int x = data.get("x").getAsInt();
        int y = data.get("y").getAsInt();
        int plane = data.get("plane").getAsInt();
        int maxDistance = data.get("maxDistance").getAsInt();
        
        return new PositionCondition(x, y, plane, maxDistance);
    }
    
    private AreaCondition deserializeAreaCondition(JsonObject data) {
        int x = data.get("x").getAsInt();
        int y = data.get("y").getAsInt();
        int width = data.get("width").getAsInt();
        int height = data.get("height").getAsInt();
        int plane = data.get("plane").getAsInt();
        
        WorldArea area = new WorldArea(x, y, width, height, plane);
        return new AreaCondition(area);
    }
    
    private RegionCondition deserializeRegionCondition(JsonObject data) {
        JsonArray regionIdsArray = data.getAsJsonArray("regionIds");
        int[] regionIds = new int[regionIdsArray.size()];
        
        for (int i = 0; i < regionIdsArray.size(); i++) {
            regionIds[i] = regionIdsArray.get(i).getAsInt();
        }
        
        return new RegionCondition(regionIds);
    }
    
    private NpcKillCountCondition deserializeNpcKillCountCondition(JsonObject data) {
        String npcName = data.get("npcName").getAsString();
        int targetCountMin = data.get("targetCountMin").getAsInt();
        int targetCountMax = data.get("targetCountMax").getAsInt();
        
        return NpcKillCountCondition.builder()
                .npcName(npcName)
                .targetCountMin(targetCountMin)
                .targetCountMax(targetCountMax)
                .build();
    }
}