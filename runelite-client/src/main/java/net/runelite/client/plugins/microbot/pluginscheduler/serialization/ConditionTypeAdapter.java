package net.runelite.client.plugins.microbot.pluginscheduler.serialization;
import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.resource.LootItemCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillLevelCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.skill.SkillXpCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
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
        
        // For simple serializatipn, place all the complete condition in the data field
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
            data.addProperty("endTimeMillis", interval.getEndTime().toInstant().toEpochMilli());
            data.addProperty("intervalSeconds", interval.getTotalDuration().getSeconds());
            data.addProperty("randomize", interval.isRandomize());
            if (interval.isRandomize()) {
                data.addProperty("randomFactor", interval.getRandomFactor());
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
            data.addProperty("startTime", window.getStartTime().toString());
            data.addProperty("endTime", window.getEndTime().toString());
        }
        else if (src instanceof SkillLevelCondition) {
            SkillLevelCondition skillLevel = (SkillLevelCondition) src;
            data.addProperty("skill", skillLevel.getSkill().name());
            data.addProperty("targetLevel", skillLevel.getTargetLevel());
        }
        else if (src instanceof SkillXpCondition) {
            SkillXpCondition skillXp = (SkillXpCondition) src;
            data.addProperty("skill", skillXp.getSkill().name());
            data.addProperty("targetXp", skillXp.getTargetXp());
        }
        else if (src instanceof LootItemCondition) {
            LootItemCondition item = (LootItemCondition) src;
            data.addProperty("itemName", item.getItemName());
            data.addProperty("targetAmountMin", item.getTargetAmountMin());                                   
            data.addProperty("targetAmountMax", item.getTargetAmountMax());
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
        if (!jsonObject.has("type")) {
            // For empty condition objects or ones without type, return null
            // This allows deserializing empty condition lists without error
            return null;
        }
        
        String typeStr = jsonObject.get("type").getAsString();
        ConditionType type;
        
        try {
            type = ConditionType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            throw new JsonParseException("Unknown condition type: " + typeStr);
        }
        
        JsonObject data = jsonObject.getAsJsonObject(DATA_FIELD);
        
        try {
            // Handle LogicalCondition subclasses
            if (LogicalCondition.class.isAssignableFrom(Class.forName(typeStr))) {
                // For LogicalCondition, we need to handle it based on the actual implementation
                if (typeStr.contains("AndCondition")) {
                    return deserializeAndCondition(data, context);
                } else if (typeStr.contains("OrCondition")) {
                    return deserializeOrCondition(data, context);
                }
            }
            
            // Handle based on class name for other types
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
            and.addCondition(condition);
        }
        return and;
    }
    
    private OrCondition deserializeOrCondition(JsonObject data, JsonDeserializationContext context) {
        OrCondition or = new OrCondition();
        JsonArray conditions = data.getAsJsonArray("conditions");
        for (JsonElement element : conditions) {
            Condition condition = context.deserialize(element, Condition.class);
            or.addCondition(condition);
        }
        return or;
    }
    
    private NotCondition deserializeNotCondition(JsonObject data, JsonDeserializationContext context) {
        Condition inner = context.deserialize(data.get("condition"), Condition.class);
        return new NotCondition(inner);
    }
    
    private IntervalCondition deserializeIntervalCondition(JsonObject data) {
        long endTimeMillis = data.get("endTimeMillis").getAsLong();
        long intervalSeconds = data.get("intervalSeconds").getAsLong();
        boolean randomize = data.has("randomize") && data.get("randomize").getAsBoolean();
        double randomFactor = randomize && data.has("randomFactor") ? 
                             data.get("randomFactor").getAsDouble() : 0.0;
        
        ZonedDateTime endTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(endTimeMillis),
                ZoneId.systemDefault());
                
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
        LocalTime start = LocalTime.parse(data.get("startTime").getAsString());
        LocalTime end = LocalTime.parse(data.get("endTime").getAsString());
        return new TimeWindowCondition(start, end);
    }
    
    private SkillLevelCondition deserializeSkillLevelCondition(JsonObject data) {
        Skill skill = Skill.valueOf(data.get("skill").getAsString());
        int targetLevel = data.get("targetLevel").getAsInt();
        return new SkillLevelCondition(skill, targetLevel);
    }
    
    private SkillXpCondition deserializeSkillXpCondition(JsonObject data) {
        Skill skill = Skill.valueOf(data.get("skill").getAsString());
        int targetXp = data.get("targetXp").getAsInt();
        return new SkillXpCondition(skill, targetXp);
    }
    
    private LootItemCondition deserializeLootItemCondition(JsonObject data) {
        String itemName = data.get("itemName").getAsString();
        int targetAmountMin = data.get("targetAmountMin").getAsInt();
        int targetAmountMax = data.get("targetAmountMax").getAsInt();
        
        LootItemCondition condition = new LootItemCondition(itemName, targetAmountMin, targetAmountMax);
             
        
        return condition;
    }
}