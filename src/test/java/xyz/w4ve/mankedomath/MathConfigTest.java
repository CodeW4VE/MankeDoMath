package xyz.w4ve.mankedomath;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The config is plain text a person edits by hand, so it has to survive being edited by hand. */
class MathConfigTest {
	private static MathConfig write(Path dir, String body) throws IOException {
		Path file = dir.resolve("mankedomath.conf");
		Files.writeString(file, body);
		return MathConfig.loadOrCreate(file);
	}

	@Test
	void defaultsGiveBothShortCommands(@TempDir Path dir) throws IOException {
		MathConfig config = MathConfig.loadOrCreate(dir.resolve("mankedomath.conf"));
		assertEquals(List.of("math", "m"), config.aliases());
		assertEquals("math", config.alias());
		assertTrue(config.funnyErrors());
	}

	@Test
	void aliasesAreCleanedAndDeduplicated(@TempDir Path dir) throws IOException {
		MathConfig config = write(dir, "aliases = Math, m , math, /calc!, , w4ve\n");
		// Case and punctuation are dropped, repeats collapse, and w4ve is refused
		// because it would collide with the root command this hangs off.
		assertEquals(List.of("math", "m", "calc"), config.aliases());
	}

	@Test
	void anEmptyListStillLeavesOneCommand(@TempDir Path dir) throws IOException {
		assertEquals(List.of("math"), write(dir, "aliases = , ,\n").aliases());
	}

	@Test
	void theOldSingleAliasKeyIsStillHonoured(@TempDir Path dir) throws IOException {
		// A config written before 1.1.0. Ignoring a setting somebody chose is worse
		// than carrying the extra line of code that reads it.
		assertEquals(List.of("calc"), write(dir, "alias = calc\n").aliases());
	}

	@Test
	void funnyErrorsCanBeSwitchedOff(@TempDir Path dir) throws IOException {
		assertTrue(!write(dir, "funny_errors = false\n").funnyErrors());
	}

	@Test
	void nonsenseLimitsAreClampedRatherThanObeyed(@TempDir Path dir) throws IOException {
		MathConfig config = write(dir, "max_length = 0\nmax_depth = 99999\nmax_operations = -5\n");
		assertTrue(config.limits().maxLength() >= 8);
		assertTrue(config.limits().maxDepth() <= 64);
		assertTrue(config.limits().maxOperations() >= 4);
	}

	@Test
	void keysFromANewerVersionSurviveARewrite(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("mankedomath.conf");
		Files.writeString(file, "alias = calc\nsomething_from_the_future = 7\n");
		MathConfig.loadOrCreate(file);
		assertTrue(Files.readString(file).contains("something_from_the_future=7"));
	}
}
