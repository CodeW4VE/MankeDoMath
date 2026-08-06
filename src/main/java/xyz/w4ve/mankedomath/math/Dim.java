package xyz.w4ve.mankedomath.math;

/**
 * What kind of quantity a number is, as exponents over the three base units the
 * mod knows: items, ticks and blocks.
 *
 * <p>A plain number is {@code (0,0,0)}. A shulker is {@code (1,0,0)}. Items per
 * hour is {@code (1,-1,0)}, and that is the whole reason this class exists: the
 * user writes {@code 5sh / 2h} and never says the word "rate", but the result
 * still has to print as items per hour instead of a naked number.
 */
public record Dim(int item, int time, int dist) {
	public static final Dim NONE = new Dim(0, 0, 0);
	public static final Dim ITEM = new Dim(1, 0, 0);
	public static final Dim TIME = new Dim(0, 1, 0);
	public static final Dim DIST = new Dim(0, 0, 1);
	/** Items per tick, which is how every farm rate ends up shaped. */
	public static final Dim RATE = new Dim(1, -1, 0);

	public boolean isNone() {
		return item == 0 && time == 0 && dist == 0;
	}

	public Dim plus(Dim other) {
		return new Dim(item + other.item, time + other.time, dist + other.dist);
	}

	public Dim minus(Dim other) {
		return new Dim(item - other.item, time - other.time, dist - other.dist);
	}

	public Dim times(int factor) {
		return new Dim(item * factor, time * factor, dist * factor);
	}

	/** Human name, used both in results and in "cannot add X to Y" errors. */
	public String describe() {
		if (isNone()) return "a plain number";
		if (equals(ITEM)) return "items";
		if (equals(TIME)) return "time";
		if (equals(DIST)) return "distance";
		if (equals(RATE)) return "items per time";
		return raw();
	}

	/** Last resort spelling for combinations with no nice name, like items squared. */
	public String raw() {
		StringBuilder top = new StringBuilder();
		StringBuilder bottom = new StringBuilder();
		append(top, bottom, item, "items");
		append(top, bottom, time, "ticks");
		append(top, bottom, dist, "blocks");
		if (top.isEmpty() && bottom.isEmpty()) return "";
		String num = top.isEmpty() ? "1" : top.toString();
		return bottom.isEmpty() ? num : num + "/" + bottom;
	}

	private static void append(StringBuilder top, StringBuilder bottom, int exp, String name) {
		if (exp == 0) return;
		StringBuilder target = exp > 0 ? top : bottom;
		int n = Math.abs(exp);
		if (!target.isEmpty()) target.append("*");
		target.append(name);
		if (n > 1) target.append("^").append(n);
	}
}
