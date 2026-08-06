package xyz.w4ve.mankedomath.math;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Turning a value back into something a person reads.
 *
 * <p>Whole numbers print with no decimal point, decimals are cut to the configured
 * number of places, and scientific notation only shows up when the number does not
 * fit any other way. Nothing here uses the platform locale, so the result is always
 * copy pasteable into another calculator.
 */
public final class Formatter {
	private static final double TICKS_PER_HOUR = 20 * 60 * 60;

	private Formatter() {}

	/** A bare number with the default four decimals, for error messages. */
	public static String plain(double value) {
		return number(value, 4);
	}

	public static String number(double value, int decimals) {
		if (value == 0) return "0";
		double magnitude = Math.abs(value);
		// Outside this band there is no honest way to write it out longhand.
		if (magnitude >= 1e15 || magnitude < 1e-6) {
			return String.format(Locale.ROOT, "%." + Math.max(1, decimals) + "e", value);
		}
		BigDecimal rounded = BigDecimal.valueOf(value)
				.setScale(Math.max(0, decimals), RoundingMode.HALF_UP)
				.stripTrailingZeros();
		if (rounded.scale() < 0) rounded = rounded.setScale(0);
		// A value that rounds away to nothing is a lie; fall back to significant digits.
		if (rounded.compareTo(BigDecimal.ZERO) == 0) {
			return BigDecimal.valueOf(value).round(new MathContext(Math.max(1, decimals)))
					.stripTrailingZeros().toPlainString();
		}
		return rounded.toPlainString();
	}

	/**
	 * The full answer line: the number, its unit, and where it helps, the same
	 * amount said a second way in brackets.
	 */
	public static String describe(MathParser.Result result, int decimals) {
		Value value = result.value();
		Units.Unit target = result.target();
		if (target != null) {
			String amount = number(value.amount() / target.factor(), decimals);
			return amount + " " + singularise(target.name(), amount);
		}

		Dim dim = value.dim();
		if (dim.isNone()) return number(value.amount(), decimals);

		if (dim.equals(Dim.ITEM)) {
			String main = number(value.amount(), decimals) + " items";
			String hint = itemHint(value.amount(), decimals);
			return hint == null ? main : main + " (" + hint + ")";
		}
		if (dim.equals(Dim.TIME)) {
			String main = number(value.amount(), decimals) + " ticks";
			String hint = duration(value.amount(), decimals);
			return hint == null ? main : main + " (" + hint + ")";
		}
		if (dim.equals(Dim.DIST)) {
			String main = number(value.amount(), decimals) + " blocks";
			double chunks = value.amount() / 16.0;
			if (Math.abs(value.amount()) < 16) return main;
			return main + " (" + number(chunks, decimals) + " chunks)";
		}
		// Farm rates are the one compound worth naming: nobody thinks in items per tick.
		if (dim.equals(Dim.RATE)) {
			double perHour = value.amount() * TICKS_PER_HOUR;
			String main = number(perHour, decimals) + " items/hour";
			double perHourShulkers = perHour / (27 * 64);
			if (Math.abs(perHourShulkers) >= 0.1) {
				return main + " (" + number(perHourShulkers, decimals) + " sh/hour)";
			}
			return main;
		}
		return number(value.amount(), decimals) + " " + dim.raw();
	}

	/**
	 * Drops the plural when there is exactly one of something, so it reads
	 * "1 double chest" and not "1 double chests". Every unit name here is a plain
	 * plural ending in s, so there is nothing cleverer to do.
	 */
	private static String singularise(String name, String amount) {
		if (!amount.equals("1") || !name.endsWith("s")) return name;
		return name.substring(0, name.length() - 1);
	}

	/** Big item counts said in the containers people actually carry. */
	private static String itemHint(double items, int decimals) {
		double magnitude = Math.abs(items);
		if (magnitude >= 27 * 64) return number(items / (27 * 64), decimals) + " sh";
		if (magnitude >= 64) return number(items / 64.0, decimals) + " st";
		return null;
	}

	/** Ticks said as hours, minutes and seconds, dropping the parts that are zero. */
	private static String duration(double ticks, int decimals) {
		double seconds = ticks / 20.0;
		if (Math.abs(seconds) < 1) return null;
		boolean negative = seconds < 0;
		long total = (long) Math.floor(Math.abs(seconds));
		long hours = total / 3600;
		long minutes = (total % 3600) / 60;
		double secs = Math.abs(seconds) - hours * 3600 - minutes * 60;

		// Parts that are zero are dropped, so one hour reads "1h" and not "1h 0m 0s".
		StringBuilder out = new StringBuilder();
		if (negative) out.append("-");
		if (hours > 0) out.append(hours).append("h ");
		if (minutes > 0) out.append(minutes).append("m ");
		if (secs > 0 || (hours == 0 && minutes == 0)) {
			out.append(number(secs, Math.min(decimals, 2))).append("s");
		}
		return out.toString().trim();
	}
}
