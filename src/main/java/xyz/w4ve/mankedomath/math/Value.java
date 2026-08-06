package xyz.w4ve.mankedomath.math;

/**
 * A number plus what it is a number of, always stored in base units.
 *
 * <p>Keeping everything in items, ticks and blocks means the arithmetic never has
 * to think about unit conversion: {@code 1sh + 3st} is 1728 + 192 and that is
 * that. Conversion only happens once, on the way out.
 */
public record Value(double amount, Dim dim) {
	public static final Value ZERO = new Value(0, Dim.NONE);

	public static Value of(double amount) {
		return new Value(amount, Dim.NONE);
	}

	public Value add(Value other) throws MathError {
		requireSame(other, "add");
		return new Value(amount + other.amount, dim);
	}

	public Value subtract(Value other) throws MathError {
		requireSame(other, "subtract");
		return new Value(amount - other.amount, dim);
	}

	public Value multiply(Value other) {
		return new Value(amount * other.amount, dim.plus(other.dim));
	}

	public Value divide(Value other) throws MathError {
		if (other.amount == 0) throw new MathError("Cannot divide by zero", MathError.Kind.DIVIDE_BY_ZERO);
		return new Value(amount / other.amount, dim.minus(other.dim));
	}

	public Value modulo(Value other) throws MathError {
		if (other.amount == 0) throw new MathError("Cannot take a remainder of zero",
				MathError.Kind.DIVIDE_BY_ZERO);
		requireSame(other, "take the remainder of");
		return new Value(amount % other.amount, dim);
	}

	public Value negate() {
		return new Value(-amount, dim);
	}

	private void requireSame(Value other, String verb) throws MathError {
		if (dim.equals(other.dim)) return;
		throw new MathError("Cannot " + verb + " " + other.dim.describe() + " and " + dim.describe(),
				MathError.Kind.MIXED_UNITS);
	}

	/** True when the value carries no unit and can be used as an exponent or an angle. */
	public boolean isPlain() {
		return dim.isNone();
	}
}
