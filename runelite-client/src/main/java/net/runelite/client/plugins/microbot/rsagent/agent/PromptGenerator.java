package net.runelite.client.plugins.microbot.rsagent.agent;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.rsagent.annotations.RsAgentTool;
import net.runelite.client.plugins.microbot.rsagent.annotations.ToolParameter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
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
    
    /**
     * Load content from a resource file
     * 
     * @param resourcePath The path to the resource
     * @return The content of the resource file as a string
     */
    private static String loadResourceFile(String resourcePath) {
        try (InputStream inputStream = PromptGenerator.class.getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                log.error("Resource not found: {}", resourcePath);
                return "";
            }
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error("Error reading resource file {}: {}", resourcePath, e.getMessage(), e);
            return "";
        }
    }
    
    /**
     * Load the prompt header from the resource file
     * 
     * @return The content of the prompt header
     */
    private static String loadPromptHeader() {
        return loadResourceFile("/rsagent/PromptHeader.txt");
    }
    
    /**
     * Load the prompt footer from the resource file
     * 
     * @return The content of the prompt footer
     */
    private static String loadPromptFooter() {
        return loadResourceFile("/rsagent/PromptFooter.txt");
    }

    /**
     * Generate the SystemPrompt.txt content from annotated methods
     * 
     * @return The complete SystemPrompt.txt content as a string
     */
    public static String generateToolDocumentation() {
        StringBuilder sb = new StringBuilder();
        
        // Load the header
        String header = loadPromptHeader();
        sb.append(header).append("\n");

        // Generate the tool descriptions
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

        // Load the footer
        String footer = loadPromptFooter();
        sb.append(footer);
        
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