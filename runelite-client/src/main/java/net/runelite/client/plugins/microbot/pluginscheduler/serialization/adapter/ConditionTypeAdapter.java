package net.runelite.client.plugins.microbot.pluginscheduler.serialization.adapter;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.location.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.npc.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.enums.RepeatCycle;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalTime;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

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
        
        if (src instanceof LogicalCondition) {            
            data = (JsonObject) context.serialize(src, LogicalCondition.class);
        }
        else if (src instanceof NotCondition) {            
            data = (JsonObject) context.serialize(src, NotCondition.class);
        }
        else if (src instanceof IntervalCondition) {            
            data = (JsonObject) context.serialize(src, IntervalCondition.class);     
        }
        else if (src instanceof DayOfWeekCondition) {
            
            data = (JsonObject) context.serialize(src, DayOfWeekCondition.class);     
        }
        else if (src instanceof TimeWindowCondition) {
            // Defer to the specialized adapter for TimeWindowCondition
                                   
            data = (JsonObject) context.serialize(src, TimeWindowCondition.class);     
        }
        else if (src instanceof SkillLevelCondition) {
            SkillLevelCondition skillLevel = (SkillLevelCondition) src;
            Skill skill = skillLevel.getSkill();
            if (skill == null || skill == Skill.OVERALL) {
                data.addProperty("skill", "OVERALL");    
                
            }else {
                data.addProperty("skill", skill.name());
            }
            
            data.addProperty("targetLevelMin", skillLevel.getTargetLevelMin());
            data.addProperty("targetLevelMax", skillLevel.getTargetLevelMax());
            data.addProperty("currentTargetLevel", skillLevel.getCurrentTargetLevel());
            data.addProperty("startLevel", skillLevel.getStartLevel());
        }
        else if (src instanceof SkillXpCondition) {
            SkillXpCondition skillXp = (SkillXpCondition) src;
            Skill skill = skillXp.getSkill();
            if (skill == null || skill == Skill.OVERALL) {
                data.addProperty("skill", "OVERALL");    
                
            }else {
                data.addProperty("skill", skill.name());
            }            
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
            data.addProperty("includeNoneOwner", item.isIncludeNoneOwner());
            data.addProperty("includeNoted", item.isIncludeNoted());
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
            data.addProperty("name", pos.getName());
            data.addProperty("x", point.getX());
            data.addProperty("y", point.getY());
            data.addProperty("plane", point.getPlane());
            data.addProperty("maxDistance", pos.getMaxDistance());
        }
        else if (src instanceof AreaCondition) {
            AreaCondition areaCondition = (AreaCondition) src;
            WorldArea area = areaCondition.getArea();
            data.addProperty("name", areaCondition.getName());
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
            data.addProperty("name", region.getName());
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
        else if (src instanceof SingleTriggerTimeCondition) {
            assert(1==0); // This should never be serialized here, sperate adapter for this

       
        }else if (src instanceof LockCondition) {
            LockCondition lock = (LockCondition) src;
            data.addProperty("reason", lock.getReason());
        }
        else {
            log.warn("Unknown condition type: {}", src.getClass().getName());
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
                return context.deserialize(data, LogicalCondition.class);
            } 
            else if (OrCondition.class.isAssignableFrom(clazz)) {                
                return context.deserialize(data, LogicalCondition.class);
            }
            else if (NotCondition.class.isAssignableFrom(clazz)) {
                
                return context.deserialize(data, NotCondition.class);
            }
            else if (IntervalCondition.class.isAssignableFrom(clazz)) {                
                return context.deserialize(data, IntervalCondition.class);
            }
            else if (DayOfWeekCondition.class.isAssignableFrom(clazz)) {                
                return context.deserialize(data, DayOfWeekCondition.class);
            }
            else if (TimeWindowCondition.class.isAssignableFrom(clazz)) {                
                return context.deserialize(data, TimeWindowCondition.class);
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
            else if (SingleTriggerTimeCondition.class.isAssignableFrom(clazz)) {
                return deserializeSingleTriggerTimeCondition(data);
            }
            else if (LockCondition.class.isAssignableFrom(clazz)) {
                if (data.has("data")){
                    data = data.getAsJsonObject("data");
                }
                return new LockCondition(data.get("reason").getAsString());
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
        if (data.has("data")) {
            data = data.getAsJsonObject("data");
        }
        log.info("Deserializing IntervalCondition: {}", data.toString());
        long intervalSeconds = data.get("intervalSeconds").getAsLong();
        boolean randomize = data.has("randomize") && data.get("randomize").getAsBoolean();
        double randomFactor = randomize && data.has("randomFactor") ? 
                             data.get("randomFactor").getAsDouble() : 0.0;
        
        Duration interval = Duration.ofSeconds(intervalSeconds);
        int maximumNumberOfRepeats = data.has("maximumNumberOfRepeats") ? 
                data.get("maximumNumberOfRepeats").getAsInt() : 0;
        return new IntervalCondition(interval, randomize, randomFactor,maximumNumberOfRepeats);
    }
    
    private DayOfWeekCondition deserializeDayOfWeekCondition(JsonObject data) {
        if (data.has("data")){
            data = data.getAsJsonObject("data");
        }
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        JsonArray activeDays = data.getAsJsonArray("activeDays");
        for (JsonElement day : activeDays) {
            days.add(DayOfWeek.valueOf(day.getAsString()));
        }
        // Handle maximum number of repeats if present
        int maximumNumberOfRepeats = data.has("maximumNumberOfRepeats") ? 
                data.get("maximumNumberOfRepeats").getAsInt() : 0;
        return new DayOfWeekCondition(maximumNumberOfRepeats,days);
    }
    
    private TimeWindowCondition deserializeTimeWindowCondition(JsonObject data) {
        try {
            if (data.has("data")){
                data = data.getAsJsonObject("data");
            }
            // Check for older format with min/max values (backward compatibility)
            if (data.has("startTimeMin") && data.has("endTimeMin")) {
                // Handle legacy format - convert to new format
                LocalTime startTime = LocalTime.parse(data.get("currentStartTime").getAsString());
                LocalTime endTime = LocalTime.parse(data.get("currentEndTime").getAsString());
                
                // Create a basic TimeWindowCondition with daily repeat cycle
                return new TimeWindowCondition(startTime, endTime);
            }
            
            // Handle new format
            LocalTime startTime = LocalTime.parse(data.get("startTime").getAsString());
            LocalTime endTime = LocalTime.parse(data.get("endTime").getAsString());
            
            // Parse dates if present, otherwise use defaults
            LocalDate startDate = data.has("startDate") ? 
                    LocalDate.parse(data.get("startDate").getAsString()) : 
                    LocalDate.now();
            
            LocalDate endDate = data.has("endDate") ? 
                    LocalDate.parse(data.get("endDate").getAsString()) : 
                    LocalDate.now().plusMonths(1);
            
            // Parse repeat cycle information
            RepeatCycle repeatCycle = data.has("repeatCycle") ? 
            RepeatCycle.valueOf(data.get("repeatCycle").getAsString()) : 
                    RepeatCycle.DAYS;
            
            int repeatInterval = data.has("repeatInterval") ? 
                    data.get("repeatInterval").getAsInt() : 1;
            
            int maximumNumberOfRepeats = data.has("maximumNumberOfRepeats") ? 
                    data.get("maximumNumberOfRepeats").getAsInt() : 0;
            // Create the condition
            TimeWindowCondition condition = new TimeWindowCondition(
                    startTime, endTime, startDate, endDate, repeatCycle, repeatInterval,maximumNumberOfRepeats);
            
            // Set randomization if present
            if (data.has("useRandomization") && data.has("randomizeMinutes")) {
                boolean useRandomization = data.get("useRandomization").getAsBoolean();
                int randomizeMinutes = data.get("randomizeMinutes").getAsInt();
                condition.setRandomization(useRandomization, randomizeMinutes);
            }
                   
            
            return condition;
        } catch (Exception e) {
            log.error("Error deserializing TimeWindowCondition, returning default", e);
            throw new JsonParseException("Error deserializing TimeWindowCondition", e);
        }
    }
    
    private SkillLevelCondition deserializeSkillLevelCondition(JsonObject data) {
        if (data.has("data")){
            data = data.getAsJsonObject("data");
        }
        Skill skill;
        if (data.get("skill").getAsString() == "OVERALL") {
            skill = Skill.OVERALL;
        }else {
            skill = Skill.valueOf(data.get("skill").getAsString());
        }
        int targetLevelMin = data.get("targetLevelMin").getAsInt();
        int targetLevelMax = data.get("targetLevelMax").getAsInt();
        return new SkillLevelCondition(skill, targetLevelMin, targetLevelMax);
    }
    
    private SkillXpCondition deserializeSkillXpCondition(JsonObject data) {
        if (data.has("data")){
            data = data.getAsJsonObject("data");
        }
        Skill skill;
        if (data.get("skill").getAsString() == "OVERALL") {
            skill = Skill.OVERALL;
        }else {
            skill = Skill.valueOf(data.get("skill").getAsString());
        }
        
        int targetXpMin = data.get("targetXpMin").getAsInt();
        int targetXpMax = data.get("targetXpMax").getAsInt();
        return new SkillXpCondition(skill, targetXpMin, targetXpMax);
    }
    
    private LootItemCondition deserializeLootItemCondition(JsonObject data) {
        if (data.has("data")){
            data = data.getAsJsonObject("data");
        }
        String itemName = data.get("itemName").getAsString();
        int targetAmountMin = data.get("targetAmountMin").getAsInt();
        int targetAmountMax = data.get("targetAmountMax").getAsInt();
        boolean includeNoted = false;
        if (data.has("includeNoted")){
            includeNoted= data.get("includeNoted").getAsBoolean();
        }
        boolean includeNoneOwner = false;
        if (data.has("includeNoneOwner")) {
            includeNoneOwner = data.get("includeNoneOwner").getAsBoolean();    
        }
        
        return LootItemCondition.builder()
                .itemName(itemName)
                .targetAmountMin(targetAmountMin)
                .targetAmountMax(targetAmountMax)
                .includeNoneOwner(includeNoneOwner)
                .includeNoted(includeNoted)
                .build();
    }
    
    private InventoryItemCountCondition deserializeInventoryItemCountCondition(JsonObject data) {
        if (data.has("data")){
            data = data.getAsJsonObject("data");
        }
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
        if (data.has("data")){
            data = data.getAsJsonObject("data");
        }
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
        String name = data.get("name").getAsString();
        int x = data.get("x").getAsInt();
        int y = data.get("y").getAsInt();
        int plane = data.get("plane").getAsInt();
        int maxDistance = data.get("maxDistance").getAsInt();
        
        return new PositionCondition(name,x, y, plane, maxDistance);
    }
    
    private AreaCondition deserializeAreaCondition(JsonObject data) {
        String name = data.get("name").getAsString();
        int x = data.get("x").getAsInt();
        int y = data.get("y").getAsInt();
        int width = data.get("width").getAsInt();
        int height = data.get("height").getAsInt();
        int plane = data.get("plane").getAsInt();
        
        WorldArea area = new WorldArea(x, y, width, height, plane);
        return new AreaCondition(name,area);
    }
    
    private RegionCondition deserializeRegionCondition(JsonObject data) {
        String name = data.get("name").getAsString();
        JsonArray regionIdsArray = data.getAsJsonArray("regionIds");
        int[] regionIds = new int[regionIdsArray.size()];
        
        for (int i = 0; i < regionIdsArray.size(); i++) {
            regionIds[i] = regionIdsArray.get(i).getAsInt();
        }
        
        return new RegionCondition(name,regionIds);
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

    private SingleTriggerTimeCondition deserializeSingleTriggerTimeCondition(JsonObject data) {
        try {
            // Extract the target time epoch millis
            long targetTimeMillis = data.get("targetTimeMillis").getAsLong();
            
            // Extract timezone ID if present, otherwise use system default
            ZoneId zoneId = ZoneId.systemDefault();
            if (data.has("timeZoneId")) {
                try {
                    zoneId = ZoneId.of(data.get("timeZoneId").getAsString());
                } catch (Exception e) {
                    log.warn("Invalid timezone ID in SingleTriggerTimeCondition, using system default", e);
                }
            }
            
            // Create the ZonedDateTime from the epoch millis and zone ID
            ZonedDateTime targetTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(targetTimeMillis), zoneId);
            
            // Create the condition
            SingleTriggerTimeCondition condition = new SingleTriggerTimeCondition(targetTime);
            
            // Set triggered status if present
            if (data.has("hasTriggered")) {
                boolean hasTriggered = data.get("hasTriggered").getAsBoolean();
                if (hasTriggered) {
                    // We can't directly set hasTriggered since it's private
                    // But we can call reset which sets hasResetAfterTrigger
                    condition.reset(false);
                }
            }
            
            return condition;
        } catch (Exception e) {
            log.error("Error deserializing SingleTriggerTimeCondition, returning default", e);
            // Return a condition that triggers after 24 hours
            return SingleTriggerTimeCondition.afterDelay(24 * 60 * 60);
        }
    }
}