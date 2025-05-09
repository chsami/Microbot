package net.runelite.client.plugins.microbot.pluginscheduler.condition.time.serialization;

import com.google.gson.*;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.enums.RepeatCycle;

import java.lang.reflect.Type;
import java.time.*;
import java.time.format.DateTimeFormatter;

/**
 * Custom serializer/deserializer for TimeWindowCondition that handles timezone conversion
 */
@Slf4j
public class TimeWindowConditionAdapter implements JsonSerializer<TimeWindowCondition>, JsonDeserializer<TimeWindowCondition> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ISO_TIME;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE;
    
    @Override
    public JsonElement serialize(TimeWindowCondition src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        
        // Add type information
        json.addProperty("type", TimeWindowCondition.class.getName());
        
        // Create data object
        JsonObject data = new JsonObject();
        
        // Get source timezone
        ZoneId sourceZone = src.getZoneId() != null ? src.getZoneId() : ZoneId.systemDefault();
        data.addProperty("version", src.getVersion());
        // Store the timezone ID for deserialization
        data.addProperty("zoneId", sourceZone.getId());
        
        // Store times in UTC, converting from source timezone
        LocalDate today = LocalDate.now();
        
        // Convert start time to UTC
        ZonedDateTime startZoned = ZonedDateTime.of(today, src.getStartTime(), sourceZone);
        ZonedDateTime startUtc = startZoned.withZoneSameInstant(ZoneId.of("UTC"));
        
        // Convert end time to UTC
        ZonedDateTime endZoned = ZonedDateTime.of(today, src.getEndTime(), sourceZone);
        ZonedDateTime endUtc = endZoned.withZoneSameInstant(ZoneId.of("UTC"));
        
        // Convert dates to UTC (using noon to avoid DST issues)
        ZonedDateTime startDateZoned = ZonedDateTime.of(src.getStartDate(), LocalTime.NOON, sourceZone);
        ZonedDateTime startDateUtc = startDateZoned.withZoneSameInstant(ZoneId.of("UTC"));
        
        ZonedDateTime endDateZoned = ZonedDateTime.of(src.getEndDate(), LocalTime.NOON, sourceZone);
        ZonedDateTime endDateUtc = endDateZoned.withZoneSameInstant(ZoneId.of("UTC"));
        
        // Store UTC times
        data.addProperty("startTime", startUtc.toLocalTime().format(TIME_FORMAT));
        data.addProperty("endTime", endUtc.toLocalTime().format(TIME_FORMAT));
        data.addProperty("startDate", startDateUtc.toLocalDate().format(DATE_FORMAT));
        data.addProperty("endDate", endDateUtc.toLocalDate().format(DATE_FORMAT));
        
        // Mark that these are UTC times for future compatibility
        data.addProperty("timeFormat", "UTC");
        
        // Repeat cycle information
        data.addProperty("repeatCycle", src.getRepeatCycle().name());
        data.addProperty("repeatInterval", src.getRepeatIntervalUnit());
        
        // Randomization settings
        data.addProperty("useRandomization", src.isUseRandomization());
        data.addProperty("randomizeMinutes", src.getRandomizeMinutes());
        data.addProperty("maximumNumberOfRepeats", src.getMaximumNumberOfRepeats());
        
        // Add data to wrapper
        json.add("data", data);
        
        return json;
    }
    
    @Override
    public TimeWindowCondition deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) 
            throws JsonParseException {
       
        JsonObject jsonObject = json.getAsJsonObject();
        
        // Check if this is a typed format or direct format
        JsonObject dataObj;
        

        if (jsonObject.has("type") && jsonObject.has("data")) {
            dataObj = jsonObject.getAsJsonObject("data");
        } else {
            // Legacy format - use the object directly
            dataObj = jsonObject;
        }
        if (dataObj.has("version")) {
            if (!dataObj.get("version").getAsString().equals(TimeWindowCondition.getVersion())) {
                throw new JsonParseException("Version mismatch: expected " + TimeWindowCondition.getVersion() + 
                        ", got " + dataObj.get("version").getAsString());
            }
        }
        // Get the target timezone for conversion (default to system)
        ZoneId targetZone = ZoneId.systemDefault();
        if (dataObj.has("zoneId")) {
            try {
                targetZone = ZoneId.of(dataObj.get("zoneId").getAsString());
            } catch (Exception e) {
                log.warn("Invalid zoneId in serialized TimeWindowCondition", e);
            }
        }
        
        // Check if times are stored in UTC format
        boolean isUtcFormat = dataObj.has("timeFormat") && 
                                "UTC".equals(dataObj.get("timeFormat").getAsString());
        
        // Parse time values
        LocalTime serializedStartTime = LocalTime.parse(dataObj.get("startTime").getAsString(), TIME_FORMAT);
        LocalTime serializedEndTime = LocalTime.parse(dataObj.get("endTime").getAsString(), TIME_FORMAT);
        
        // Parse date values
        LocalDate serializedStartDate = LocalDate.parse(dataObj.get("startDate").getAsString(), DATE_FORMAT);
        LocalDate serializedEndDate = LocalDate.parse(dataObj.get("endDate").getAsString(), DATE_FORMAT);
        
        LocalTime startTime;
        LocalTime endTime;
        LocalDate startDate;
        LocalDate endDate;
        
        if (isUtcFormat) {
            // If stored in UTC format, convert back to target timezone
            LocalDate today = LocalDate.now();
            
            // Convert start time from UTC to target zone
            ZonedDateTime startUtc = ZonedDateTime.of(today, serializedStartTime, ZoneId.of("UTC"));
            ZonedDateTime startTargetZone = startUtc.withZoneSameInstant(targetZone);
            startTime = startTargetZone.toLocalTime();
            
            // Convert end time from UTC to target zone
            ZonedDateTime endUtc = ZonedDateTime.of(today, serializedEndTime, ZoneId.of("UTC"));
            ZonedDateTime endTargetZone = endUtc.withZoneSameInstant(targetZone);
            endTime = endTargetZone.toLocalTime();
            
            // Convert dates from UTC to target zone
            ZonedDateTime startDateUtc = ZonedDateTime.of(serializedStartDate, LocalTime.NOON, ZoneId.of("UTC"));
            ZonedDateTime startDateTarget = startDateUtc.withZoneSameInstant(targetZone);
            startDate = startDateTarget.toLocalDate();
            
            ZonedDateTime endDateUtc = ZonedDateTime.of(serializedEndDate, LocalTime.NOON, ZoneId.of("UTC"));
            ZonedDateTime endDateTarget = endDateUtc.withZoneSameInstant(targetZone);
            endDate = endDateTarget.toLocalDate();
        } else {
            // Legacy format - use times as-is
            startTime = serializedStartTime;
            endTime = serializedEndTime;
            startDate = serializedStartDate;
            endDate = serializedEndDate;
        }
        
        // Parse repeat cycle
        RepeatCycle repeatCycle = RepeatCycle.valueOf(
                dataObj.get("repeatCycle").getAsString());
        int repeatInterval = dataObj.get("repeatInterval").getAsInt();
        long maximumNumberOfRepeats = dataObj.get("maximumNumberOfRepeats").getAsLong();
        // Create the condition with the parsed values
        TimeWindowCondition condition = new TimeWindowCondition(
                startTime, endTime, startDate, endDate, repeatCycle, repeatInterval, maximumNumberOfRepeats);
        
        // Set timezone
        condition.setZoneId(targetZone);
        
        // Set randomization if present
        if (dataObj.has("useRandomization") && dataObj.has("randomizeMinutes")) {
            boolean useRandomization = dataObj.get("useRandomization").getAsBoolean();
            int randomizeMinutes = dataObj.get("randomizeMinutes").getAsInt();
            condition.setRandomization(useRandomization, randomizeMinutes);
        }                        
        return condition;
       
    }
}