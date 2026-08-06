package xyz.w4ve.mankedomath.math;

/**
 * A problem with the expression the player typed, never a bug in the mod.
 *
 * <p>Carries the offset into the expression so the command layer can hand it to
 * Brigadier, which already knows how to underline the exact spot. A generic
 * "invalid expression" is useless when you mistyped one character out of forty.
 */
public class MathError extends Exception {
	/**
	 * Roughly what went wrong, so the message can be dressed up appropriately.
	 * Only the cases worth their own joke are named; everything else is GENERAL.
	 */
	public enum Kind { GENERAL, DIVIDE_BY_ZERO, MIXED_UNITS, TOO_BIG, UNKNOWN_NAME }

	/** Index into the expression where things went wrong, or -1 when it is about the whole thing. */
	private final int position;
	private final Kind kind;

	public MathError(String message, int position, Kind kind) {
		super(message);
		this.position = position;
		this.kind = kind;
	}

	public MathError(String message, int position) {
		this(message, position, Kind.GENERAL);
	}

	public MathError(String message) {
		this(message, -1, Kind.GENERAL);
	}

	public MathError(String message, Kind kind) {
		this(message, -1, kind);
	}

	public int position() {
		return position;
	}

	public Kind kind() {
		return kind;
	}
}
