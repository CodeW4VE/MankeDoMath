package xyz.w4ve.mankedomath;

import xyz.w4ve.mankedomath.math.Limits;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The settings, as plain {@code key=value} text.
 *
 * <p>Same shape as the other W4VE pieces: it can be edited over ssh without a
 * JSON library and without counting brackets, and a file written by an older
 * version gets topped up with the new keys at their defaults instead of hiding
 * them. A setting you cannot see is a setting you do not have.
 *
 * <p>Everything here reloads with {@code /math reload} except the command names,
 * which are baked into the command tree when the server starts. That split is on
 * purpose and the file says so: this is the same "what reloads and what waits for
 * a restart" rule the rest of W4VE runs on.
 */
public final class MathConfig {
	private static final String TEMPLATE = """
			# MankeDoMath: a calculator inside Minecraft.
			#
			# Change something here and run /math reload. No restart needed, EXCEPT for
			# short_alias and alias, which build the command tree at server start.
			#
			# short_alias: register /math as well as /w4ve math.
			# alias: the short command name. Change it if another mod already took it.
			# max_length: longest expression accepted, in characters.
			# max_depth: how deep brackets and nested calls may go.
			# max_exponent: biggest exponent allowed, so 9^9^9 bounces with a message
			#   instead of eating a tick. Checked before the power is computed.
			# max_operations: how many operators and function calls one expression may use.
			# decimals: decimal places in the result. Whole numbers never show a point.
			# click_to_copy: make the result clickable to copy it to the clipboard.
			# share_needs_permission: require operator level 2 to use /math share.
			#   Results are private by default; share is the only thing that reaches
			#   public chat.
			""";

	private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();

	static {
		DEFAULTS.put("short_alias", "true");
		DEFAULTS.put("alias", "math");
		DEFAULTS.put("max_length", "256");
		DEFAULTS.put("max_depth", "16");
		DEFAULTS.put("max_exponent", "64");
		DEFAULTS.put("max_operations", "500");
		DEFAULTS.put("decimals", "4");
		DEFAULTS.put("click_to_copy", "true");
		DEFAULTS.put("share_needs_permission", "false");
	}

	private final Map<String, String> values = new LinkedHashMap<>();
	private final List<String> unknown = new ArrayList<>();

	public boolean shortAlias() {
		return getBoolean("short_alias", true);
	}

	/** The short command name, sanitised: Brigadier only accepts word characters. */
	public String alias() {
		String raw = get("alias", "math").trim().toLowerCase(java.util.Locale.ROOT);
		String cleaned = raw.replaceAll("[^a-z0-9_-]", "");
		return cleaned.isEmpty() ? "math" : cleaned;
	}

	public int decimals() {
		return Math.max(0, Math.min(10, getInt("decimals", 4)));
	}

	public boolean clickToCopy() {
		return getBoolean("click_to_copy", true);
	}

	public boolean shareNeedsPermission() {
		return getBoolean("share_needs_permission", false);
	}

	/** The ceilings, clamped so a typo in the file cannot switch the safety off. */
	public Limits limits() {
		return new Limits(
				clamp(getInt("max_length", 256), 8, 4096),
				clamp(getInt("max_depth", 16), 2, 64),
				clamp(getInt("max_exponent", 64), 1, 1024),
				clamp(getInt("max_operations", 500), 4, 10_000));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(max, value));
	}

	private String get(String key, String fallback) {
		String value = values.get(key);
		return value == null ? fallback : value;
	}

	private int getInt(String key, int fallback) {
		try {
			return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private boolean getBoolean(String key, boolean fallback) {
		String value = get(key, String.valueOf(fallback)).trim();
		return value.equalsIgnoreCase("true") || value.equals("1");
	}

	/** Loads the config, writing a commented template first if there is not one yet. */
	public static MathConfig loadOrCreate(Path file) throws IOException {
		MathConfig config = new MathConfig();
		if (!Files.isRegularFile(file)) {
			config.values.putAll(DEFAULTS);
			config.save(file);
			return config;
		}
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			int equals = trimmed.indexOf('=');
			if (equals <= 0) continue;
			String key = trimmed.substring(0, equals).trim();
			String value = trimmed.substring(equals + 1).trim();
			config.values.put(key, value);
			// Keys from a newer version are kept as they are, never quietly dropped.
			if (!DEFAULTS.containsKey(key)) config.unknown.add(key);
		}

		boolean added = false;
		for (Map.Entry<String, String> fallback : DEFAULTS.entrySet()) {
			if (!config.values.containsKey(fallback.getKey())) {
				config.values.put(fallback.getKey(), fallback.getValue());
				added = true;
			}
		}
		if (added) config.save(file);
		return config;
	}

	public void save(Path file) throws IOException {
		Path parent = file.getParent();
		if (parent != null) Files.createDirectories(parent);
		StringBuilder text = new StringBuilder(TEMPLATE);
		for (String key : DEFAULTS.keySet()) {
			text.append(key).append('=').append(values.getOrDefault(key, DEFAULTS.get(key))).append('\n');
		}
		if (!unknown.isEmpty()) {
			text.append("\n# Kept from a newer version of the mod.\n");
			for (String key : unknown) {
				text.append(key).append('=').append(values.get(key)).append('\n');
			}
		}
		Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
	}
}
