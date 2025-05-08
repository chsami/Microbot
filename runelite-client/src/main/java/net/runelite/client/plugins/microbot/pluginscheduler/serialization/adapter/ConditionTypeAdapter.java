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
import net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit.VarbitCondition;

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
        else if (src instanceof VarbitCondition) {
            // Use the specialized adapter for VarbitCondition
            data = (JsonObject) context.serialize(src, VarbitCondition.class);
        }
        else if (src instanceof SkillLevelCondition) {
            // Use the specialized adapter for SkillLevelCondition
            data = (JsonObject) context.serialize(src, SkillLevelCondition.class);
        }
        else if (src instanceof SkillXpCondition) {
            // Use the specialized adapter for SkillXpCondition
            data = (JsonObject) context.serialize(src, SkillXpCondition.class);
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
            // Use the specialized adapter for PositionCondition
            data = (JsonObject) context.serialize(src, PositionCondition.class);
        }
        else if (src instanceof AreaCondition) {
            // Use the specialized adapter for AreaCondition
            data = (JsonObject) context.serialize(src, AreaCondition.class);
        }
        else if (src instanceof RegionCondition) {
            // Use the specialized adapter for RegionCondition
            data = (JsonObject) context.serialize(src, RegionCondition.class);
        }
        else if (src instanceof NpcKillCountCondition) {
            // Use the specialized adapter for NpcKillCountCondition
            data = (JsonObject) context.serialize(src, NpcKillCountCondition.class);
        }
        else if (src instanceof SingleTriggerTimeCondition) {
            assert(1==0); // This should never be serialized here, sperate adapter for this
        }
        else if (src instanceof LockCondition) {
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
            else if (VarbitCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, VarbitCondition.class);
            }
            else if (SkillLevelCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, SkillLevelCondition.class);
            }
            else if (SkillXpCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, SkillXpCondition.class);
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
                return context.deserialize(data, PositionCondition.class);
            }
            else if (AreaCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, AreaCondition.class);
            }
            else if (RegionCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, RegionCondition.class);
            }
            else if (NpcKillCountCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, NpcKillCountCondition.class);
            }
            else if (SingleTriggerTimeCondition.class.isAssignableFrom(clazz)) {
                return context.deserialize(data, SingleTriggerTimeCondition.class);                
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
}