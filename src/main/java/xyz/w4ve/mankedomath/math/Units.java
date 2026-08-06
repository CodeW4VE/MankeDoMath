package xyz.w4ve.mankedomath.math;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The suffixes that turn a calculator into a Minecraft calculator.
 *
 * <p>Everything is stored in base units: one item, one tick, one block. A unit is
 * just a factor and a dimension, so {@code 2sh} is 3456 items and the printer can
 * turn it back into whatever the player asked for.
 */
public final class Units {
	/** A named multiple of a base unit. */
	public record Unit(String name, double factor, Dim dim) {}

	private static final Map<String, Unit> BY_NAME = new LinkedHashMap<>();

	private static void put(Unit unit, String... names) {
		for (String name : names) BY_NAME.put(name, unit);
	}

	static {
		Unit item = new Unit("items", 1, Dim.ITEM);
		Unit stack = new Unit("stacks", 64, Dim.ITEM);
		// A shulker box is 27 slots, a double chest 54, both a full stack each.
		Unit shulker = new Unit("shulkers", 27 * 64, Dim.ITEM);
		Unit doubleChest = new Unit("double chests", 54 * 64, Dim.ITEM);
		put(item, "item", "items", "i");
		put(stack, "st", "stack", "stacks");
		put(shulker, "sh", "shulker", "shulkers", "box");
		put(doubleChest, "dc", "doublechest", "doublechests");

		Unit tick = new Unit("ticks", 1, Dim.TIME);
		Unit second = new Unit("seconds", 20, Dim.TIME);
		Unit minute = new Unit("minutes", 20 * 60, Dim.TIME);
		Unit hour = new Unit("hours", 20 * 60 * 60, Dim.TIME);
		put(tick, "t", "tick", "ticks");
		put(second, "s", "sec", "secs", "second", "seconds");
		put(minute, "m", "min", "mins", "minute", "minutes");
		put(hour, "h", "hr", "hrs", "hour", "hours");

		Unit block = new Unit("blocks", 1, Dim.DIST);
		Unit chunk = new Unit("chunks", 16, Dim.DIST);
		// Handy for perimeters: the distance a beacon or a spawn chunk range covers.
		Unit region = new Unit("regions", 512, Dim.DIST);
		put(block, "b", "block", "blocks");
		put(chunk, "c", "chunk", "chunks");
		put(region, "r", "region", "regions");
	}

	private Units() {}

	/** Case insensitive lookup, or null when the word is not a unit. */
	public static Unit find(String name) {
		return BY_NAME.get(name.toLowerCase(Locale.ROOT));
	}

	public static boolean isUnit(String name) {
		return find(name) != null;
	}

	/** Every spelling accepted, for command suggestions and for {@code /math help}. */
	public static Iterable<String> names() {
		return BY_NAME.keySet();
	}
}
