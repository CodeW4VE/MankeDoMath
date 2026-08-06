package xyz.w4ve.mankedomath.math;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The expression parser. Hand written, no dependencies, and above all no
 * {@code eval} and no scripting engine.
 *
 * <p>Putting an interpreter on a public server so people can add two numbers is
 * opening the door to whoever walks by. This reads exactly the grammar below and
 * nothing else, so there is no shape of input that turns into code:
 *
 * <pre>
 *   whole   := expr [ 'in' UNIT ]
 *   expr    := term (('+' | '-') term)*
 *   term    := unary (('*' | '/' | '%') unary)*
 *   unary   := ('-' | '+') unary | power
 *   power   := atom [ '^' unary ]
 *   atom    := NUMBER [UNIT] | NAME '(' args ')' | 'ans' | '(' expr ')'
 * </pre>
 *
 * <p>Precedence puts the prefix minus outside the power on purpose, so
 * {@code -2^2} is -4, which is what every calculator does and what people expect.
 * The power itself is right associative, so {@code 9^9^9} is 9^(9^9), which is
 * also the case that has to bounce off the exponent limit rather than be computed.
 *
 * <p>Values are evaluated as they are parsed. There is no tree, because there is
 * nothing to optimise and one pass makes the error positions honest.
 */
public final class MathParser {
	/** What came out: the value, plus the unit the player asked to see it in. */
	public record Result(Value value, Units.Unit target) {}

	private final List<Token> tokens;
	private final Limits limits;
	private final Value ans;
	private int index;
	private int operations;
	private int depth;

	private MathParser(List<Token> tokens, Limits limits, Value ans) {
		this.tokens = tokens;
		this.limits = limits;
		this.ans = ans;
	}

	/**
	 * Evaluate an expression.
	 *
	 * @param ans the asking player's previous result, or null when they have none
	 */
	public static Result evaluate(String expression, Limits limits, Value ans) throws MathError {
		if (expression == null || expression.isBlank()) {
			throw new MathError("Nothing to calculate");
		}
		// Length first, before anything walks the string even once.
		if (expression.length() > limits.maxLength()) {
			throw new MathError("Expression is too long: " + expression.length()
					+ " characters, the limit is " + limits.maxLength(), MathError.Kind.TOO_BIG);
		}

		List<Token> all = Lexer.tokenize(expression, limits);
		int ops = 0;
		for (Token token : all) {
			if (token.type == Type.OPERATOR || token.type == Type.FUNCTION) ops++;
		}
		if (ops > limits.maxOperations()) {
			throw new MathError("Expression does too much: " + ops
					+ " operations, the limit is " + limits.maxOperations(), MathError.Kind.TOO_BIG);
		}

		// `in` is split off here instead of being an operator, because its right
		// hand side is a unit name and not a value, and mixing the two into one
		// precedence table buys nothing but confusion.
		int split = -1;
		int nesting = 0;
		for (int i = 0; i < all.size(); i++) {
			Token token = all.get(i);
			if (token.type == Type.LPAREN) nesting++;
			else if (token.type == Type.RPAREN) nesting--;
			else if (token.type == Type.IN && nesting == 0) {
				if (split >= 0) throw new MathError("Only one 'in' per expression", token.position);
				split = i;
			}
		}

		Units.Unit target = null;
		List<Token> body = all;
		if (split >= 0) {
			body = all.subList(0, split);
			List<Token> tail = all.subList(split + 1, all.size());
			if (tail.isEmpty()) {
				throw new MathError("'in' needs a unit after it, like 'in sb'", all.get(split).position);
			}
			Token unit = tail.get(0);
			if (tail.size() > 1 || unit.type != Type.NAME) {
				throw new MathError("'in' takes a single unit, like 'in sb' or 'in stacks'", unit.position);
			}
			target = Units.find(unit.text);
			if (target == null) {
				throw new MathError("Unknown unit '" + unit.text + "'", unit.position,
						MathError.Kind.UNKNOWN_NAME);
			}
		}
		if (body.isEmpty()) throw new MathError("Nothing to calculate");

		MathParser parser = new MathParser(body, limits, ans);
		Value value = parser.expr();
		if (parser.index < body.size()) {
			Token extra = body.get(parser.index);
			throw new MathError("Unexpected '" + extra.text + "'", extra.position);
		}
		// A plain number converts into anything: `(16*16*64) in st` is the whole
		// point of the feature, and nobody is going to type "items" after a volume
		// they just worked out. A number that already has a unit is another story.
		if (target != null && !value.dim().isNone() && !value.dim().equals(target.dim())) {
			throw new MathError("Cannot show " + value.dim().describe() + " in "
					+ target.name() + ", those are different kinds of thing", -1,
					MathError.Kind.MIXED_UNITS);
		}
		check(value.amount(), -1);
		return new Result(value, target);
	}

	// ---- grammar ----------------------------------------------------------

	private Value expr() throws MathError {
		Value left = term();
		while (true) {
			Token token = peek();
			if (token == null || token.type != Type.OPERATOR) return left;
			if (token.text.equals("+")) {
				index++;
				left = left.add(term());
			} else if (token.text.equals("-")) {
				index++;
				left = left.subtract(term());
			} else {
				return left;
			}
			check(left.amount(), token.position);
		}
	}

	private Value term() throws MathError {
		Value left = unary();
		while (true) {
			Token token = peek();
			if (token == null || token.type != Type.OPERATOR) return left;
			switch (token.text) {
				case "*" -> {
					index++;
					left = left.multiply(unary());
				}
				case "/" -> {
					index++;
					left = left.divide(unary());
				}
				case "%" -> {
					index++;
					left = left.modulo(unary());
				}
				default -> {
					return left;
				}
			}
			check(left.amount(), token.position);
		}
	}

	private Value unary() throws MathError {
		Token token = peek();
		if (token != null && token.type == Type.OPERATOR) {
			if (token.text.equals("-")) {
				index++;
				return enter(() -> unary().negate());
			}
			if (token.text.equals("+")) {
				index++;
				return enter(this::unary);
			}
		}
		return power();
	}

	private Value power() throws MathError {
		Value base = atom();
		Token token = peek();
		if (token == null || token.type != Type.OPERATOR || !token.text.equals("^")) return base;
		index++;
		Value exponent = enter(this::unary);
		return pow(base, exponent, token.position);
	}

	private Value atom() throws MathError {
		Token token = peek();
		if (token == null) throw new MathError("Expression ends too early", endPosition());
		switch (token.type) {
			case NUMBER -> {
				index++;
				return new Value(token.number, token.dim);
			}
			case LPAREN -> {
				index++;
				Value inner = enter(this::expr);
				expect(Type.RPAREN, ")");
				return inner;
			}
			case FUNCTION -> {
				return function();
			}
			case NAME -> {
				index++;
				if (token.text.equalsIgnoreCase("ans")) {
					if (ans == null) throw new MathError("No previous result yet", token.position);
					return ans;
				}
				if (Units.isUnit(token.text)) {
					throw new MathError("'" + token.text + "' is a unit, it needs a number in front of it"
							+ " (try '1" + token.text + "')", token.position);
				}
				throw new MathError("Unknown name '" + token.text + "'", token.position,
						MathError.Kind.UNKNOWN_NAME);
			}
			default -> throw new MathError("Unexpected '" + token.text + "'", token.position);
		}
	}

	private Value function() throws MathError {
		Token name = tokens.get(index++);
		expect(Type.LPAREN, "(");
		List<Value> args = new ArrayList<>();
		Token next = peek();
		if (next != null && next.type == Type.RPAREN) {
			index++;
		} else {
			while (true) {
				args.add(enter(this::expr));
				Token separator = peek();
				if (separator != null && separator.type == Type.COMMA) {
					index++;
					continue;
				}
				expect(Type.RPAREN, ")");
				break;
			}
		}
		return apply(name, args);
	}

	// ---- operations -------------------------------------------------------

	private Value pow(Value base, Value exponent, int position) throws MathError {
		if (!exponent.isPlain()) {
			throw new MathError("An exponent cannot have a unit", position);
		}
		double e = exponent.amount();
		// Checked before Math.pow runs, so an absurd exponent costs nothing.
		if (Math.abs(e) > limits.maxExponent()) {
			throw new MathError("Exponent " + Formatter.plain(e) + " is over the limit of "
					+ Formatter.plain(limits.maxExponent()), position, MathError.Kind.TOO_BIG);
		}
		if (!base.isPlain()) {
			if (e != Math.rint(e)) {
				throw new MathError("A value with a unit can only be raised to a whole power", position);
			}
			if (base.amount() == 0 && e < 0) throw new MathError("Cannot divide by zero", position);
			return check(new Value(Math.pow(base.amount(), e), base.dim().times((int) e)), position);
		}
		if (base.amount() == 0 && e < 0) throw new MathError("Cannot divide by zero", position);
		if (base.amount() < 0 && e != Math.rint(e)) {
			throw new MathError("Cannot raise a negative number to a fractional power", position);
		}
		return check(Value.of(Math.pow(base.amount(), e)), position);
	}

	private Value apply(Token name, List<Value> args) throws MathError {
		String fn = name.text.toLowerCase(Locale.ROOT);
		int position = name.position;
		switch (fn) {
			case "min", "max" -> {
				if (args.size() < 2) throw new MathError(fn + " needs at least two values", position);
				Value best = args.get(0);
				for (Value arg : args.subList(1, args.size())) {
					if (!arg.dim().equals(best.dim())) {
						throw new MathError("Cannot compare " + arg.dim().describe()
								+ " with " + best.dim().describe(), position);
					}
					boolean take = fn.equals("min") ? arg.amount() < best.amount() : arg.amount() > best.amount();
					if (take) best = arg;
				}
				return best;
			}
			default -> {
				if (args.size() != 1) {
					throw new MathError(fn + " takes exactly one value, got " + args.size(), position);
				}
				Value arg = args.get(0);
				return switch (fn) {
					// These keep the unit: half a shulker rounded down is still items.
					case "abs" -> check(new Value(Math.abs(arg.amount()), arg.dim()), position);
					case "floor" -> check(new Value(Math.floor(arg.amount()), arg.dim()), position);
					case "ceil" -> check(new Value(Math.ceil(arg.amount()), arg.dim()), position);
					// Half goes away from zero. Math.rint rounds 2.5 to 2 to keep the
					// last digit even, which is right for statistics and wrong for
					// someone working out how many shulkers to carry.
					case "round" -> check(new Value(
							Math.signum(arg.amount()) * Math.floor(Math.abs(arg.amount()) + 0.5),
							arg.dim()), position);
					case "sqrt" -> sqrt(arg, position);
					case "log" -> check(Value.of(Math.log10(positive(arg, "log", position))), position);
					case "ln" -> check(Value.of(Math.log(positive(arg, "ln", position))), position);
					default -> throw new MathError("Unknown function '" + name.text + "'", position,
							MathError.Kind.UNKNOWN_NAME);
				};
			}
		}
	}

	private Value sqrt(Value arg, int position) throws MathError {
		if (arg.amount() < 0) throw new MathError("Cannot take the square root of a negative number", position);
		Dim dim = arg.dim();
		// A square root halves the exponents, which only works when they are all even.
		if (dim.item() % 2 != 0 || dim.time() % 2 != 0 || dim.dist() % 2 != 0) {
			throw new MathError("Cannot take the square root of " + dim.describe(), position);
		}
		Dim half = new Dim(dim.item() / 2, dim.time() / 2, dim.dist() / 2);
		return check(new Value(Math.sqrt(arg.amount()), half), position);
	}

	private static double positive(Value arg, String fn, int position) throws MathError {
		if (!arg.isPlain()) throw new MathError(fn + " needs a plain number, without a unit", position);
		if (arg.amount() <= 0) throw new MathError(fn + " needs a number above zero", position);
		return arg.amount();
	}

	// ---- plumbing ---------------------------------------------------------

	/** Runs a nested piece of grammar while keeping the depth honest. */
	private Value enter(Step step) throws MathError {
		if (++depth > limits.maxDepth()) {
			depth--;
			throw new MathError("Expression is nested too deep, the limit is " + limits.maxDepth(),
					peek() == null ? endPosition() : peek().position);
		}
		try {
			return step.run();
		} finally {
			depth--;
		}
	}

	private interface Step {
		Value run() throws MathError;
	}

	private static Value check(Value value, int position) throws MathError {
		check(value.amount(), position);
		return value;
	}

	/** Infinity and NaN never leave the parser, so nothing downstream has to handle them. */
	private static void check(double amount, int position) throws MathError {
		if (Double.isNaN(amount)) throw new MathError("That is not a number", position);
		if (Double.isInfinite(amount)) throw new MathError("Result is too big to represent", position,
				MathError.Kind.TOO_BIG);
	}

	private Token peek() {
		return index < tokens.size() ? tokens.get(index) : null;
	}

	private void expect(Type type, String what) throws MathError {
		Token token = peek();
		if (token == null) throw new MathError("Missing '" + what + "'", endPosition());
		if (token.type != type) throw new MathError("Expected '" + what + "' but found '" + token.text + "'",
				token.position);
		index++;
	}

	private int endPosition() {
		return tokens.isEmpty() ? 0 : tokens.get(tokens.size() - 1).end;
	}

	// ---- tokens -----------------------------------------------------------

	enum Type { NUMBER, NAME, FUNCTION, OPERATOR, LPAREN, RPAREN, COMMA, IN }

	record Token(Type type, String text, double number, Dim dim, int position, int end) {
		static Token simple(Type type, String text, int position) {
			return new Token(type, text, 0, Dim.NONE, position, position + text.length());
		}
	}

	/** Names that are functions rather than units or variables. */
	private static final List<String> FUNCTIONS =
			List.of("sqrt", "min", "max", "abs", "floor", "ceil", "round", "log", "ln");

	public static List<String> functionNames() {
		return FUNCTIONS;
	}

	private static final class Lexer {
		static List<Token> tokenize(String source, Limits limits) throws MathError {
			List<Token> out = new ArrayList<>();
			int i = 0;
			int nesting = 0;
			while (i < source.length()) {
				char c = source.charAt(i);
				if (Character.isWhitespace(c)) {
					i++;
					continue;
				}
				if (Character.isDigit(c) || (c == '.' && i + 1 < source.length()
						&& Character.isDigit(source.charAt(i + 1)))) {
					i = number(source, i, out);
					continue;
				}
				if (Character.isLetter(c) || c == '_') {
					// A lone 'x' where an operator belongs is multiplication, because
					// half the wiki writes "16x16". Anywhere else it is just a letter,
					// so `max(` and `x` as a name still lex normally.
					if ((c == 'x' || c == 'X') && infixPosition(out)) {
						out.add(Token.simple(Type.OPERATOR, "*", i));
						i++;
						continue;
					}
					i = word(source, i, out);
					continue;
				}
				switch (c) {
					case '(' -> {
						if (++nesting > limits.maxDepth()) {
							throw new MathError("Too many nested brackets, the limit is "
									+ limits.maxDepth(), i);
						}
						out.add(Token.simple(Type.LPAREN, "(", i));
					}
					case ')' -> {
						nesting--;
						if (nesting < 0) throw new MathError("Closing bracket with nothing open", i);
						out.add(Token.simple(Type.RPAREN, ")", i));
					}
					case ',' -> out.add(Token.simple(Type.COMMA, ",", i));
					case '+', '-', '*', '/', '%', '^' ->
							out.add(Token.simple(Type.OPERATOR, String.valueOf(c), i));
					default -> throw new MathError("Cannot use '" + c + "' here", i);
				}
				i++;
			}
			if (nesting > 0) throw new MathError("Missing " + nesting + " closing bracket"
					+ (nesting == 1 ? "" : "s"), source.length());
			return out;
		}

		/** True when the previous token was a value, so an operator is what comes next. */
		private static boolean infixPosition(List<Token> out) {
			if (out.isEmpty()) return false;
			Type last = out.get(out.size() - 1).type;
			return last == Type.NUMBER || last == Type.RPAREN;
		}

		/** A number, plus the unit glued to it or sitting right after it. */
		private static int number(String source, int start, List<Token> out) throws MathError {
			int i = start;
			boolean dot = false;
			while (i < source.length()) {
				char c = source.charAt(i);
				if (Character.isDigit(c)) {
					i++;
				} else if (c == '.' && !dot) {
					dot = true;
					i++;
				} else if (c == '_') {
					// Underscore groups digits, like 1_000_000. A comma cannot do this
					// job: it is the argument separator, and `min(1,000)` has to keep
					// meaning two arguments.
					if (i + 1 >= source.length() || !Character.isDigit(source.charAt(i + 1))) break;
					i++;
				} else {
					break;
				}
			}
			String text = source.substring(start, i).replace("_", "");
			double amount;
			try {
				amount = Double.parseDouble(text);
			} catch (NumberFormatException e) {
				throw new MathError("'" + text + "' is not a number", start);
			}
			if (Double.isInfinite(amount)) throw new MathError("Number is too big", start,
						MathError.Kind.TOO_BIG);

			// Look ahead for a unit, with or without a space: "3st" and "3456 items".
			int scan = i;
			while (scan < source.length() && source.charAt(scan) == ' ') scan++;
			int wordStart = scan;
			while (scan < source.length() && Character.isLetter(source.charAt(scan))) scan++;
			if (scan > wordStart) {
				String word = source.substring(wordStart, scan);
				int after = scan;
				while (after < source.length() && source.charAt(after) == ' ') after++;
				boolean isCall = after < source.length() && source.charAt(after) == '(';
				Units.Unit unit = isCall ? null : Units.find(word);
				if (unit != null) {
					out.add(new Token(Type.NUMBER, source.substring(start, scan),
							amount * unit.factor(), unit.dim(), start, scan));
					return scan;
				}
			}
			out.add(new Token(Type.NUMBER, text, amount, Dim.NONE, start, i));
			return i;
		}

		private static int word(String source, int start, List<Token> out) {
			int i = start;
			while (i < source.length() && (Character.isLetterOrDigit(source.charAt(i))
					|| source.charAt(i) == '_')) {
				i++;
			}
			String text = source.substring(start, i);
			String lower = text.toLowerCase(Locale.ROOT);
			if (lower.equals("in") || lower.equals("to")) {
				out.add(Token.simple(Type.IN, text, start));
			} else if (FUNCTIONS.contains(lower)) {
				out.add(Token.simple(Type.FUNCTION, text, start));
			} else {
				out.add(Token.simple(Type.NAME, text, start));
			}
			return i;
		}
	}
}
