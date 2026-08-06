package xyz.w4ve.mankedomath;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import xyz.w4ve.mankedomath.math.Value;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MankeDoMath: a calculator inside Minecraft, for the sums a technical player
 * actually does. Farm rates, how many shulkers a number of items is, how long
 * something takes to fill.
 *
 * <p>Server side only, on purpose. The server registers the command in Brigadier
 * and ships the tree to a vanilla client, which then autocompletes it. Nobody
 * installs anything.
 */
public class MankeDoMath implements ModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger("mankedomath");
	public static MankeDoMath INSTANCE;

	/** Stands in for the console and command blocks, which have no UUID of their own. */
	private static final UUID CONSOLE = new UUID(0, 0);

	private final Path configFile = FabricLoader.getInstance().getConfigDir().resolve("mankedomath.conf");
	private volatile MathConfig config = new MathConfig();

	/** Last result per player, in memory only. Not a history, just `ans`. */
	private final Map<UUID, Value> lastResult = new ConcurrentHashMap<>();

	@Override
	public void onInitialize() {
		INSTANCE = this;
		reload();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				MathCommand.register(dispatcher, config));

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
				lastResult.remove(handler.player.getUUID()));

		LOGGER.info("MankeDoMath ready");
	}

	public MathConfig config() {
		return config;
	}

	/**
	 * Rereads the config file.
	 *
	 * @return null when it worked, or the reason it did not
	 */
	public String reload() {
		try {
			config = MathConfig.loadOrCreate(configFile);
			return null;
		} catch (IOException e) {
			// Keep running on whatever was already loaded rather than losing the
			// command over a bad file.
			LOGGER.warn("Could not read {}: {}", configFile, e.toString());
			return e.getMessage() == null ? e.toString() : e.getMessage();
		}
	}

	public Value lastResult(UUID player) {
		return lastResult.get(player == null ? CONSOLE : player);
	}

	public void remember(UUID player, Value value) {
		lastResult.put(player == null ? CONSOLE : player, value);
	}
}
