package net.runelite.client.plugins.microbot.rsagent.util;

import org.junit.Test;
import static org.junit.Assert.*;

// Mocking Microbot.log or providing a test implementation would be ideal,
// but for now, we'll rely on manual inspection of logs if needed for deeper debugging.

public class WikiScraperTest {

    @Test
    public void fetchKnownWikiPageSuccessfully() {
        // This test performs a live HTTP request.
        // Using a common and relatively stable page like "Coins".
        String pageTitle = "Coins";
        String content = WikiScraper.fetchWikiPageContent(pageTitle);

        assertNotNull("Content should not be null for a known page.", content);
        assertFalse("Content for a known page should not start with an error message. Actual: " + content,
                content.startsWith("Error:"));
        assertTrue("Content should be prefixed correctly.", content.contains("Content for '" + pageTitle + "':"));
        // We expect some actual content beyond the prefix.
        assertTrue("Expected actual content from the page.",
                content.length() > ("Content for '" + pageTitle + "':\n").length());
        System.out.println("fetchKnownWikiPageSuccessfully: Fetched content for '" + pageTitle + "' (first 100 chars): "
                + (content.length() > 100 ? content.substring(0, 100) : content) + "...");
    }

    @Test
    public void fetchNonExistentPage() {
        // This test performs a live HTTP request.
        String pageTitle = "This Page Does Not Exist I Hope XYZZY"; // Added unique string to avoid accidental match
        String content = WikiScraper.fetchWikiPageContent(pageTitle);

        assertNotNull("Content should not be null, even for an error.", content);
        assertTrue("Expected a 'not found' error. Actual: " + content, content.startsWith("Error: Wiki page"));
        assertTrue("Error message should indicate 'not found'. Actual: " + content, content.contains("not found"));
        System.out.println("fetchNonExistentPage: Received (expected) error: " + content);
    }

    @Test
    public void fetchWithNullPageTitle() {
        String content = WikiScraper.fetchWikiPageContent(null);
        // The URLEncoder will throw a NullPointerException if pageTitle is null before
        // it makes a request.
        // The current implementation catches this as a generic Exception.
        assertNotNull("Content should not be null.", content);
        assertTrue("Expected an error for null page title. Actual: " + content,
                content.startsWith("Error: An unexpected error occurred"));
        System.out.println("fetchWithNullPageTitle: Received (expected) error: " + content);
    }

    @Test
    public void fetchWithEmptyPageTitle() {
        // This test performs a live HTTP request, though the wiki will likely return a
        // search/empty page.
        String pageTitle = "";
        String content = WikiScraper.fetchWikiPageContent(pageTitle);

        assertNotNull("Content should not be null.", content);
        // Depending on wiki behavior for empty title, it might be a 404 or some form of
        // error/search page.
        // The current scraper logic might return the generic fetch error if not 200 or
        // 404.
        // Or it might try to parse a search results page, which could be empty after
        // stripping.
        // Let's check for any error or an empty processed content error.
        assertTrue("Expected an error or empty content error for an empty page title. Actual: " + content,
                content.startsWith("Error:"));
        System.out.println("fetchWithEmptyPageTitle: Received (expected) error or specific handling: " + content);
    }

    @Test
    public void parseHtmlContent_BasicExtraction_ViaFetch() {
        // Since parseHtmlContent is private, we test its effects through
        // fetchWikiPageContent
        // by ensuring common HTML elements are stripped from a known page.
        String pageTitle = "Meat"; // Meat is a simple page, less likely to change drastically
        String content = WikiScraper.fetchWikiPageContent(pageTitle);

        assertNotNull("Content for '" + pageTitle + "' should not be null.", content);
        assertFalse("Output for '" + pageTitle + "' should not be an error for this test.",
                content.startsWith("Error:"));

        // Check that common HTML tags are not in the output string
        assertFalse("Output for '" + pageTitle + "' should not contain <p> tags.", content.contains("<p>"));
        assertFalse("Output for '" + pageTitle + "' should not contain <b> tags.", content.contains("<b>"));
        assertFalse("Output for '" + pageTitle + "' should not contain <a> tags.", content.contains("<a>"));
        assertFalse("Output for '" + pageTitle + "' should not contain <div> tags.", content.contains("<div>"));
        assertFalse("Output for '" + pageTitle + "' should not contain <span> tags.", content.contains("<span>"));

        System.out.println(
                "parseHtmlContent_BasicExtraction_ViaFetch: Checked for stripped tags on '" + pageTitle + "' page.");
    }
}