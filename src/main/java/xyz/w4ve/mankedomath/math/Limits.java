package xyz.w4ve.mankedomath.math;

/**
 * The hard ceilings that keep this command incapable of touching MSPT.
 *
 * <p>Length, depth and operation count are checked before a single multiplication
 * happens. The exponent cap is checked as each {@code ^} is about to be applied,
 * because the exponent can itself be the result of a calculation, but since the
 * operation count is already bounded there is no way to spend real time getting
 * there. {@code 9^9^9} bounces with a message instead of eating a tick.
 */
public record Limits(int maxLength, int maxDepth, double maxExponent, int maxOperations) {
	public static final Limits DEFAULT = new Limits(256, 16, 64, 500);
}
