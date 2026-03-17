package net.runelite.client.plugins.microbot.commandcenter;

import org.junit.Test;
import static org.junit.Assert.*;

public class CredentialRedactorTest {
    @Test
    public void testRedactsPassword() {
        String input = "Login with password=secret123 on world 301";
        String result = CredentialRedactor.redact(input);
        assertEquals("Login with password=*** on world 301", result);
    }

    @Test
    public void testRedactsEmail() {
        String input = "email=user@example.com logged in";
        String result = CredentialRedactor.redact(input);
        assertEquals("email=*** logged in", result);
    }

    @Test
    public void testNoCredentials() {
        String input = "Normal log message";
        assertEquals("Normal log message", CredentialRedactor.redact(input));
    }

    @Test
    public void testNullInput() {
        assertNull(CredentialRedactor.redact(null));
    }
}
