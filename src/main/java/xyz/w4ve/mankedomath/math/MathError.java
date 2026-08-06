package xyz.w4ve.mankedomath.math;

/**
 * A problem with the expression the player typed, never a bug in the mod.
 *
 * <p>Carries the offset into the expression so the command layer can hand it to
 * Brigadier, which already knows how to underline the exact spot. A generic
 * "invalid expression" is useless when you mistyped one character out of forty.
 */
public class MathError extends Exception {
	/** Index into the expression where things went wrong, or -1 when it is about the whole thing. */
	private final int position;

	public MathError(String message, int position) {
		super(message);
		this.position = position;
	}

	public MathError(String message) {
		this(message, -1);
	}

	public int position() {
		return position;
	}
}
