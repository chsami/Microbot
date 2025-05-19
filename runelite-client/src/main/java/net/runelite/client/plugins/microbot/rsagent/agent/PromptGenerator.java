package net.runelite.client.plugins.microbot.rsagent.agent;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.rsagent.annotations.RsAgentTool;
import net.runelite.client.plugins.microbot.rsagent.annotations.ToolParameter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates SystemPrompt.txt content from annotated tool methods
 */
@Slf4j
public class PromptGenerator {
    // The template for the beginning of the SystemPrompt.txt file
    private static final String PROMPT_HEADER = "You are RSAgent, a RuneScape bot agent. Your job is to think step-by-step and decide which tool to use to accomplish the goal stated by the user.\n"
            +
            "The rules:\n" +
            "- Match the response format exactly, or you will fail\n" +
            "- DO NOT ATTEMPT MULTIPLE STEPS. You should always perform one loop iteration at a time.\n" +
            "- ALWAYS use the following format for all your responses:\n" +
            "{\n" +
            "  \"thought\": \"your reasoning about the next step\",\n" +
            "  \"action\": \"tool_name\",\n" +
            "  \"action_parameters\": {\n" +
            "    \"key\": \"value\"\n" +
            "  }\n" +
            "}\n" +
            "Do NOT explain anything outside the JSON. Available tools are:\n";

    // The template for the end of the SystemPrompt.txt file
    private static final String PROMPT_FOOTER =
            // Footer content would go here - can be extracted from existing file
            "Your thought process should be:\n" +
                    "1. Understand the user's task.\n" +
                    "2. Plan the sequence of actions to achieve the task.\n" +
                    "3. For each action, select the appropriate tool and specify its parameters.\n" +
                    "4. Output the JSON for the current action.\n" +
                    "5. Wait for the \"Tool result:\" which will be the observation from the game after your action. This result may also include \"Game Messages:\" captured during the tool's execution, which provide additional context from the game chat.\n"
                    +
                    "6. Based on the result, decide the next action. If the task is complete, use the \"finish\" action.\n"
                    +
                    "\nYou are now ready to receive your first task.";

    /**
     * Generate the SystemPrompt.txt content from annotated methods
     * 
     * @return The complete SystemPrompt.txt content as a string
     */
    public static String generateToolDocumentation() {
        StringBuilder sb = new StringBuilder();
        sb.append(PROMPT_HEADER);

        Method[] methods = RsAgentTools.class.getDeclaredMethods();
        for (Method method : methods) {
            RsAgentTool toolAnnotation = method.getAnnotation(RsAgentTool.class);
            if (toolAnnotation != null) {
                sb.append(toolAnnotation.name()).append(": ").append(toolAnnotation.description()).append("\n");

                // Build the parameter descriptions
                List<String> paramDescriptions = new ArrayList<>();
                for (Parameter param : method.getParameters()) {
                    ToolParameter paramAnnotation = param.getAnnotation(ToolParameter.class);
                    if (paramAnnotation != null) {
                        String typeName = param.getType().getSimpleName().toLowerCase();
                        String optionalText = paramAnnotation.optional() ? ", optional" : "";
                        paramDescriptions.add(paramAnnotation.name() + " (" + typeName + optionalText + ")");
                    }
                }

                if (!paramDescriptions.isEmpty()) {
                    sb.append("      Takes inputs: ").append(String.join(", ", paramDescriptions)).append("\n");
                } else {
                    sb.append("      Takes inputs: none\n");
                }

                // Add return type
                String returnType = method.getReturnType().getSimpleName();
                sb.append("      Returns an output of type: ").append(returnType).append("\n\n");
            }
        }

        sb.append(PROMPT_FOOTER);
        return sb.toString();
    }

    /**
     * Generate the prompt and optionally save it to a file
     * 
     * @param saveToDisk Whether to save the generated prompt to disk
     * @return The generated prompt
     */
    public static String generatePrompt(boolean saveToDisk) {
        String prompt = generateToolDocumentation();

        if (saveToDisk) {
            try {
                // Determine where to write the file (in resources)
                Path resourcesPath = Paths.get("runelite-client/src/main/resources/rsagent");
                if (!Files.exists(resourcesPath)) {
                    Files.createDirectories(resourcesPath);
                }

                Path promptPath = resourcesPath.resolve("SystemPrompt.txt");
                Files.write(promptPath, Arrays.asList(prompt.split("\n")));

                log.info("Successfully generated SystemPrompt.txt at {}", promptPath);
            } catch (IOException e) {
                log.error("Failed to write SystemPrompt.txt: {}", e.getMessage(), e);
            }
        }

        return prompt;
    }

    /**
     * Command line tool to generate and save the prompt file
     */
    public static void main(String[] args) {
        generatePrompt(true);
    }
}