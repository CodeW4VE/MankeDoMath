package xyz.w4ve.mankedomath.math;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parser is the one place in this mod where a bug shows up as a wrong number
 * instead of a crash, which is worse, because nobody notices. So it gets tests.
 */
class MathParserTest {
	private static MathParser.Result eval(String expression) throws MathError {
		return MathParser.evaluate(expression, Limits.DEFAULT, null);
	}

	private static double amount(String expression) throws MathError {
		return eval(expression).value().amount();
	}

	private static String show(String expression) throws MathError {
		return Formatter.describe(eval(expression), 4);
	}

	// ---- arithmetic -------------------------------------------------------

	@Test
	void basicArithmetic() throws MathError {
		assertEquals(4, amount("2+2"));
		assertEquals(14, amount("2+3*4"));
		assertEquals(20, amount("(2+3)*4"));
		assertEquals(2.5, amount("5/2"));
		assertEquals(1, amount("7%3"));
		assertEquals(8, amount("2^3"));
	}

	@Test
	void powerIsRightAssociativeAndMinusBindsOutside() throws MathError {
		assertEquals(512, amount("2^3^2"));
		// Every calculator reads this as -(2^2), and so do people.
		assertEquals(-4, amount("-2^2"));
		assertEquals(4, amount("(-2)^2"));
		assertEquals(0.125, amount("2^-3"));
	}

	@Test
	void functions() throws MathError {
		assertEquals(4, amount("sqrt(16)"));
		assertEquals(3, amount("min(3,7)"));
		assertEquals(7, amount("max(3,7)"));
		assertEquals(7, amount("max(3,7,5)"));
		assertEquals(5, amount("abs(0-5)"));
		assertEquals(2, amount("floor(2.9)"));
		assertEquals(3, amount("ceil(2.1)"));
		// Half rounds away from zero, not to the nearest even number.
		assertEquals(3, amount("round(2.5)"));
		assertEquals(-3, amount("round(0-2.5)"));
		assertEquals(2, amount("round(2.4)"));
		assertEquals(2, amount("log(100)"));
		assertEquals(1, amount("ln(2.718281828459045)"), 1e-9);
	}

	@Test
	void whitespaceAndSeparatorsAreForgiving() throws MathError {
		assertEquals(4, amount("  2  +  2  "));
		assertEquals(1000000, amount("1_000_000"));
		// A stray x between numbers is how half the wiki writes multiplication.
		assertEquals(6, amount("2x3"));
		assertEquals(256, amount("16 x 16"));
		// But the comma stays an argument separator, always: this is min(1, 0).
		assertEquals(0, amount("min(1,000)"));
	}

	// ---- units ------------------------------------------------------------

	@Test
	void suffixesBecomeBaseUnits() throws MathError {
		assertEquals(192, amount("3st"));
		assertEquals(3456, amount("2sh"));
		assertEquals(3456, amount("1dc"));
		assertEquals(600, amount("30s"));
		assertEquals(72000, amount("1h"));
		assertEquals(32, amount("2c"));
	}

	@Test
	void unitsAddUpAndKeepTheirKind() throws MathError {
		assertEquals(1728 + 192, amount("1sh + 3st"));
		assertEquals(Dim.ITEM, eval("1sh + 3st").value().dim());
	}

	@Test
	void unitWordCanBeSeparatedBySpace() throws MathError {
		assertEquals(3456, amount("3456 items"));
		// The value stays in base units; the conversion happens on the way out.
		assertEquals(3456, amount("3456 items in sh"));
		assertEquals("2 shulkers", show("3456 items in sh"));
	}

	@Test
	void conversionWithIn() throws MathError {
		assertEquals("2 shulkers", show("3456 items in sh"));
		assertEquals("256 stacks", show("(16*16*64) in st"));
		assertEquals("30 seconds", show("600t in s"));
		// One of something is not plural.
		assertEquals("1 double chest", show("3456 items in dc"));
		assertEquals("2 shulkers", show("3456 in sh"));
		// 20 stacks is 1280 items, which is not a whole shulker.
		assertEquals(0.7407, eval("20st in sh").value().amount() / (27 * 64), 1e-4);
	}

	@Test
	void divisionByTimeGivesARate() throws MathError {
		MathParser.Result result = eval("5sh / 2h");
		assertEquals(Dim.RATE, result.value().dim());
		// 5 shulkers is 8640 items, spread over two hours.
		assertTrue(Formatter.describe(result, 4).startsWith("4320 items/hour"));
	}

	@Test
	void mixingKindsIsRefused() {
		MathError error = assertThrows(MathError.class, () -> eval("1sh + 1h"));
		assertTrue(error.getMessage().contains("Cannot add"));
	}

	@Test
	void convertingToTheWrongKindIsRefused() {
		MathError error = assertThrows(MathError.class, () -> eval("1sh in s"));
		assertTrue(error.getMessage().contains("different kinds"));
	}

	@Test
	void aBareUnitSaysWhatIsMissing() {
		MathError error = assertThrows(MathError.class, () -> eval("sh + 1"));
		assertTrue(error.getMessage().contains("needs a number in front"));
	}

	// ---- limits -----------------------------------------------------------

	@Test
	void absurdExponentBouncesInsteadOfComputing() {
		MathError error = assertThrows(MathError.class, () -> eval("9^9^9"));
		assertTrue(error.getMessage().contains("over the limit"));
	}

	@Test
	void lengthLimit() {
		Limits tiny = new Limits(8, 16, 64, 500);
		MathError error = assertThrows(MathError.class,
				() -> MathParser.evaluate("1+1+1+1+1+1", tiny, null));
		assertTrue(error.getMessage().contains("too long"));
	}

	@Test
	void operationLimit() {
		Limits tiny = new Limits(256, 16, 64, 3);
		MathError error = assertThrows(MathError.class,
				() -> MathParser.evaluate("1+1+1+1+1", tiny, null));
		assertTrue(error.getMessage().contains("does too much"));
	}

	@Test
	void depthLimitStopsDeepNesting() {
		Limits tiny = new Limits(256, 4, 64, 500);
		MathError error = assertThrows(MathError.class,
				() -> MathParser.evaluate("((((((1))))))", tiny, null));
		assertTrue(error.getMessage().contains("nested"));
	}

	@Test
	void deepNestingDoesNotBlowTheStack() {
		// Well past any sane depth: it has to come back as an error, not an
		// StackOverflowError that takes the server thread with it.
		String expression = "(".repeat(120) + "1" + ")".repeat(120);
		assertThrows(MathError.class, () -> eval(expression));
	}

	// ---- errors -----------------------------------------------------------

	@Test
	void divisionByZero() {
		MathError error = assertThrows(MathError.class, () -> eval("1/0"));
		assertTrue(error.getMessage().contains("divide by zero"));
	}

	@Test
	void unbalancedBrackets() {
		assertThrows(MathError.class, () -> eval("(1+2"));
		assertThrows(MathError.class, () -> eval("1+2)"));
	}

	@Test
	void errorsPointAtTheRightSpot() {
		MathError error = assertThrows(MathError.class, () -> eval("1 + $"));
		assertEquals(4, error.position());
	}

	@Test
	void unknownNameAndUnknownUnit() {
		assertThrows(MathError.class, () -> eval("banana"));
		assertThrows(MathError.class, () -> eval("1 in banana"));
	}

	@Test
	void emptyInput() {
		assertThrows(MathError.class, () -> eval("   "));
	}

	@Test
	void ansIsRefusedUntilThereIsOne() {
		assertThrows(MathError.class, () -> eval("ans + 1"));
	}

	@Test
	void ansCarriesItsUnit() throws MathError {
		Value previous = new Value(1728, Dim.ITEM);
		MathParser.Result result = MathParser.evaluate("ans / 64", Limits.DEFAULT, previous);
		assertEquals(27, result.value().amount());
		assertEquals(Dim.ITEM, result.value().dim());
	}

	// ---- formatting -------------------------------------------------------

	@Test
	void wholeNumbersHaveNoDecimalPoint() {
		assertEquals("4", Formatter.number(4.0, 4));
		assertEquals("2.5", Formatter.number(2.5, 4));
		assertEquals("0.3333", Formatter.number(1.0 / 3.0, 4));
		assertEquals("0", Formatter.number(0, 4));
	}

	@Test
	void itemCountsGetAContainerHint() throws MathError {
		assertEquals("3456 items (2 sh)", show("3456 items"));
		assertEquals("192 items (3 st)", show("3st"));
		assertEquals("32 items", show("32 items"));
	}

	@Test
	void ticksGetAReadableDuration() throws MathError {
		assertEquals("600 ticks (30s)", show("600t"));
		assertEquals("72000 ticks (1h)", show("1h"));
		assertEquals("108000 ticks (1h 30m)", show("1h + 30m"));
	}
}
