package net.runelite.client.rs.auth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class JagexAuthServiceTest
{
	private Path dir;

	@After
	public void cleanUp() throws IOException
	{
		if (dir != null)
		{
			Files.deleteIfExists(dir.resolve("held"));
			Files.deleteIfExists(dir);
		}
	}

	@Test
	public void deleteTempFileRemovesFile() throws IOException
	{
		Path temp = Files.createTempFile("runelite_jagexauth", "");

		JagexAuthService.deleteTempFile(temp);

		Assert.assertFalse(Files.exists(temp));
	}

	@Test
	public void deleteTempFileDoesNotThrowWhenDeleteFails() throws IOException
	{
		dir = Files.createTempDirectory("runelite_jagexauth");
		Files.createFile(dir.resolve("held"));

		JagexAuthService.deleteTempFile(dir);

		Assert.assertTrue(Files.exists(dir));
	}
}
