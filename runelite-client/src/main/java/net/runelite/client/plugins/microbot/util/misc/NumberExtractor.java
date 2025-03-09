package net.runelite.client.plugins.microbot.util.misc;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NumberExtractor {
    /**
     * Extracts the first integer number from a given string.
     * For example: "+1%", "123abc", "Price: 99 dollars" will result in 1, 123, 99
     *
     * @param input The input string containing a number.
     * @return The extracted number, or -1 if no number is found.
     */
    public static int extractNumber(String input) {
        // Define a regex pattern to match one or more digits
        Pattern pattern = Pattern.compile("\\d+");
        Matcher matcher = pattern.matcher(input);

        // Find the first match and parse it as an integer
        if (matcher.find()) {
            return Integer.parseInt(matcher.group());
        }

        // Return -1 if no number is found
        return -1;
    }
}
