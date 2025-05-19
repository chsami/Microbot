package net.runelite.client.plugins.microbot.rsagent.agent;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.rsagent.annotations.RsAgentTool;
import net.runelite.client.plugins.microbot.rsagent.annotations.ToolParameter;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry for RsAgent tool methods that handles tool execution via reflection
 */
@Slf4j
public class ToolRegistry {
    private static final Map<String, Method> toolMethods = new HashMap<>();

    /**
     * Initialize the tool registry by scanning for annotated methods
     */
    public static void initialize() {
        log.info("Initializing RsAgent Tool Registry...");

        Method[] methods = RsAgentTools.class.getDeclaredMethods();
        for (Method method : methods) {
            RsAgentTool annotation = method.getAnnotation(RsAgentTool.class);
            if (annotation != null) {
                String toolName = annotation.name();
                toolMethods.put(toolName, method);
                log.debug("Registered tool: {}", toolName);
            }
        }

        log.info("RsAgent Tool Registry initialized with {} tools", toolMethods.size());
    }

    /**
     * Execute a tool by its name with the provided parameters
     * 
     * @param toolName The name of the tool to execute
     * @param params   The parameters for the tool as a JsonObject
     * @return The result of the tool execution as a String
     */
    public static String executeTool(String toolName, JsonObject params) {
        try {
            Method method = toolMethods.get(toolName);
            if (method == null) {
                return "Unknown tool requested: " + toolName;
            }

            Object[] args = parseParameters(method, params);
            Object result = method.invoke(null, args);

            return formatResult(result);
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return "Error executing tool: " + e.getMessage();
        }
    }

    /**
     * Parse parameters for a method from a JsonObject
     */
    private static Object[] parseParameters(Method method, JsonObject params) throws Exception {
        Parameter[] methodParams = method.getParameters();
        Object[] args = new Object[methodParams.length];

        for (int i = 0; i < methodParams.length; i++) {
            Parameter param = methodParams[i];
            ToolParameter annotation = param.getAnnotation(ToolParameter.class);

            if (annotation == null) {
                throw new IllegalArgumentException(
                        "Parameter " + param.getName() + " is missing ToolParameter annotation");
            }

            String paramName = annotation.name();
            boolean optional = annotation.optional();

            if (!params.has(paramName)) {
                if (!optional) {
                    throw new IllegalArgumentException("Required parameter " + paramName + " not provided");
                }
                args[i] = null;
                continue;
            }

            JsonElement element = params.get(paramName);
            Class<?> paramType = param.getType();

            if (element == null || element instanceof JsonNull) {
                if (!optional) {
                    throw new IllegalArgumentException("Required parameter " + paramName + " is null");
                }
                args[i] = null;
                continue;
            }

            args[i] = convertJsonToType(element, paramType);
        }

        return args;
    }

    /**
     * Convert a JSON element to the required Java type
     */
    private static Object convertJsonToType(JsonElement element, Class<?> type) {
        if (element == null || element instanceof JsonNull) {
            return null;
        }

        if (type == String.class) {
            return element.getAsString();
        } else if (type == int.class || type == Integer.class) {
            return element.getAsInt();
        } else if (type == boolean.class || type == Boolean.class) {
            return element.getAsBoolean();
        } else if (type == double.class || type == Double.class) {
            return element.getAsDouble();
        } else if (type == long.class || type == Long.class) {
            return element.getAsLong();
        } else if (type == float.class || type == Float.class) {
            return element.getAsFloat();
        } else {
            throw new IllegalArgumentException("Unsupported parameter type: " + type.getName());
        }
    }

    /**
     * Format a tool result into a string representation
     */
    private static String formatResult(Object result) {
        if (result == null) {
            return "No result (null)";
        }

        return result.toString();
    }
}