package xyz.w4ve.mankedomath.math;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The joke that goes in front of an error message.
 *
 * <p>It never replaces the real reason, it only introduces it. Someone who
 * mistyped one bracket out of forty still gets told which bracket, because a
 * funny error that leaves you stuck stops being funny the second time.
 *
 * <p>Switched off with {@code funny_errors=false} in the config, for anyone who
 * wants their calculator to have no opinions.
 */
public final class Quips {
	private static final List<String> GENERAL = List.of(
			"manke can do math but no magik",
			"this cant be possible kitten",
			"manke stared at this for a while",
			"that is not maths, that is just letters",
			"manke tried. manke failed");

	private static final Map<MathError.Kind, List<String>> BY_KIND = Map.of(
			MathError.Kind.DIVIDE_BY_ZERO, List.of(
					"manke can do math but no magik",
					"dividing by zero is how you summon things",
					"nothing goes into nothing, kitten"),
			MathError.Kind.MIXED_UNITS, List.of(
					"you cannot add cats and tuesdays",
					"manke can do math but not alchemy",
					"those two do not live in the same world"),
			MathError.Kind.TOO_BIG, List.of(
					"manke can do math but not THAT much math",
					"that number does not fit in minecraft",
					"put it down, kitten, it is too heavy"),
			MathError.Kind.UNKNOWN_NAME, List.of(
					"manke has never heard of that one",
					"manke can do math but no reading minds",
					"is that a mod thing? manke does not know it"));

	private Quips() {}

	/** A line to put in front of {@code error}'s own message. */
	public static String forError(MathError error) {
		List<String> pool = BY_KIND.getOrDefault(error.kind(), GENERAL);
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/** The full line: the joke, then the reason, then Brigadier underlines the spot. */
	public static String decorate(MathError error, boolean funny) {
		String reason = error.getMessage();
		if (!funny) return reason;
		return forError(error) + ". " + reason;
	}

	/** Every quip there is, so a test can check none of them is empty or shouting. */
	public static List<List<String>> all() {
		return List.of(GENERAL,
				BY_KIND.get(MathError.Kind.DIVIDE_BY_ZERO),
				BY_KIND.get(MathError.Kind.MIXED_UNITS),
				BY_KIND.get(MathError.Kind.TOO_BIG),
				BY_KIND.get(MathError.Kind.UNKNOWN_NAME));
	}
}
