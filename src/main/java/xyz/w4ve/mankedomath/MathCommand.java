package xyz.w4ve.mankedomath;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

import xyz.w4ve.mankedomath.math.Formatter;
import xyz.w4ve.mankedomath.math.MathError;
import xyz.w4ve.mankedomath.math.MathParser;
import xyz.w4ve.mankedomath.math.Value;

import java.util.UUID;

/**
 * The command tree.
 *
 * <p>Everything hangs off {@code /w4ve math}, which is the canonical spelling, and
 * the short {@code /math} is a configurable extra so it can be turned off when
 * another mod already owns that name.
 *
 * <p>Results are private by default. A calculator that shouts into public chat is
 * a calculator people turn off, so only {@code /math share} reaches everyone.
 */
public final class MathCommand {
	/** Carries the parser's own message through Brigadier, which underlines the spot. */
	private static final DynamicCommandExceptionType MATH_ERROR =
			new DynamicCommandExceptionType(message -> Component.literal(String.valueOf(message)));

	private MathCommand() {}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, MathConfig config) {
		// Registering "w4ve" merges into whatever other W4VE mods already put there,
		// so the pieces share one root without knowing about each other.
		dispatcher.register(Commands.literal("w4ve").then(tree("math")));
		if (config.shortAlias()) {
			String alias = config.alias();
			if (!alias.equals("w4ve")) dispatcher.register(tree(alias));
		}
	}

	private static LiteralArgumentBuilder<CommandSourceStack> tree(String name) {
		return Commands.literal(name)
				// Bare /math is a question, so answer it with the examples.
				.executes(MathCommand::help)
				.then(Commands.literal("help").executes(MathCommand::help))
				// There is deliberately no `ans` literal. Brigadier stops looking once a
				// literal matches the word, so `/math ans` would work and `/math ans / 64`
				// would not. The parser already knows the name, so it goes through the
				// expression argument like everything else.
				.then(Commands.literal("reload")
						.requires(source -> source.hasPermission(2))
						.executes(MathCommand::reload))
				.then(Commands.literal("share")
						// Read from the live config rather than captured at startup, so
						// a reload takes effect even though the tree itself does not change.
						.requires(source -> !MankeDoMath.INSTANCE.config().shareNeedsPermission()
								|| source.hasPermission(2))
						.then(Commands.argument("expression", StringArgumentType.greedyString())
								.executes(context -> run(context, true))))
				// Literals win over arguments in Brigadier, so "help" still reaches the
				// literal above and only real expressions land here.
				.then(Commands.argument("expression", StringArgumentType.greedyString())
						.executes(context -> run(context, false)));
	}

	private static int run(CommandContext<CommandSourceStack> context, boolean share)
			throws CommandSyntaxException {
		String expression = StringArgumentType.getString(context, "expression");
		CommandSourceStack source = context.getSource();
		MathConfig config = MankeDoMath.INSTANCE.config();
		ServerPlayer player = source.getPlayer();
		UUID key = player == null ? null : player.getUUID();

		MathParser.Result result;
		try {
			result = MathParser.evaluate(expression, config.limits(),
					MankeDoMath.INSTANCE.lastResult(key));
		} catch (MathError error) {
			StringReader reader = new StringReader(expression);
			if (error.position() >= 0) {
				reader.setCursor(Math.min(error.position(), expression.length()));
			} else {
				reader.setCursor(expression.length());
			}
			throw MATH_ERROR.createWithContext(reader, error.getMessage());
		}

		Value value = result.value();
		MankeDoMath.INSTANCE.remember(key, value);
		String answer = Formatter.describe(result, config.decimals());

		if (share) {
			String who = player == null ? source.getTextName() : player.getGameProfile().getName();
			Component line = prefix()
					.append(Component.literal(who).withStyle(ChatFormatting.WHITE))
					.append(Component.literal(": ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(expression.trim()).withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" = ").withStyle(ChatFormatting.DARK_GRAY))
					.append(answerText(answer, config));
			// This already echoes to the server console on its own, the same way
			// anything else said in public chat does.
			source.getServer().getPlayerList().broadcastSystemMessage(line, false);
		} else {
			Component line = prefix()
					.append(Component.literal(expression.trim()).withStyle(ChatFormatting.GRAY))
					.append(Component.literal(" = ").withStyle(ChatFormatting.DARK_GRAY))
					.append(answerText(answer, config));
			source.sendSuccess(() -> line, false);
		}

		// Brigadier wants an int. Whole answers report themselves, and anything
		// with a fraction reports 1 so command blocks can still see it succeeded.
		double amount = value.amount();
		if (Math.abs(amount) < Integer.MAX_VALUE && amount == Math.rint(amount)) {
			return (int) amount;
		}
		return 1;
	}

	private static int reload(CommandContext<CommandSourceStack> context) {
		String problem = MankeDoMath.INSTANCE.reload();
		if (problem != null) {
			context.getSource().sendFailure(Component.literal("Could not reload the config: " + problem));
			return 0;
		}
		context.getSource().sendSuccess(() -> prefix()
				.append(Component.literal("Config reloaded. The command names need a restart; "
						+ "everything else is live now.").withStyle(ChatFormatting.GRAY)), true);
		return 1;
	}

	private static int help(CommandContext<CommandSourceStack> context) {
		MathConfig config = MankeDoMath.INSTANCE.config();
		String name = config.shortAlias() ? "/" + config.alias() : "/w4ve math";
		CommandSourceStack source = context.getSource();

		source.sendSuccess(() -> prefix()
				.append(Component.literal("A calculator that knows what a shulker is.")
						.withStyle(ChatFormatting.GRAY)), false);
		example(source, name, "3456 items in sh", "how many shulkers that is");
		example(source, name, "5sh / 2h", "items per hour");
		example(source, name, "(16*16*64) in st", "stacks in a chunk section");
		example(source, name, "20st in sh", "part of a shulker");
		source.sendSuccess(() -> Component.literal("  units: ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("st sh dc items · t s m h · b c r")
						.withStyle(ChatFormatting.WHITE)), false);
		source.sendSuccess(() -> Component.literal("  functions: ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(String.join(" ", MathParser.functionNames()))
						.withStyle(ChatFormatting.WHITE)), false);
		source.sendSuccess(() -> Component.literal("  ").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal(name + " ans").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" reuses your last result. Results are only visible to you; ")
						.withStyle(ChatFormatting.DARK_GRAY))
				.append(Component.literal(name + " share <expr>").withStyle(ChatFormatting.WHITE))
				.append(Component.literal(" tells everyone.").withStyle(ChatFormatting.DARK_GRAY)), false);
		return 1;
	}

	private static void example(CommandSourceStack source, String name, String expression, String what) {
		String command = name + " " + expression;
		source.sendSuccess(() -> Component.literal("  ").append(
				Component.literal(command).withStyle(style -> style
						.withColor(ChatFormatting.YELLOW)
						.withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, command))
						.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
								Component.literal("Click to put it in your chat box")))))
				.append(Component.literal("  " + what).withStyle(ChatFormatting.DARK_GRAY)), false);
	}

	/** The answer itself, clickable to copy when the config says so. */
	private static Component answerText(String answer, MathConfig config) {
		MutableComponent text = Component.literal(answer).withStyle(ChatFormatting.GOLD);
		if (!config.clickToCopy()) return text;
		return text.withStyle(style -> style
				.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, answer))
				.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
						Component.literal("Click to copy"))));
	}

	private static MutableComponent prefix() {
		return Component.literal("[").withStyle(ChatFormatting.DARK_GRAY)
				.append(Component.literal("math").withStyle(ChatFormatting.GOLD))
				.append(Component.literal("] ").withStyle(ChatFormatting.DARK_GRAY));
	}
}
