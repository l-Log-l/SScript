package ai.log.sscript;

import ai.log.sscript.command.SScriptCommand;
import ai.log.sscript.event.EventManager;
import ai.log.sscript.event.MixinManager;
import ai.log.sscript.global.GlobalVariables;
import ai.log.sscript.runtime.ProcessScheduler;
import ai.log.sscript.util.ScriptLoader;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.storage.NbtWriteView;
import net.minecraft.util.ErrorReporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SScript implements ModInitializer {
	public static final String MOD_ID = "sscript";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static MinecraftServer serverInstance;
	private static ScriptLoader scriptLoader;
	private static volatile boolean debugEnabled = true;

	@Override
	public void onInitialize() {
		LOGGER.info("[SScript] Initializing SScript mod...");

		// Register commands
		SScriptCommand.register();

		// Initialize tick-based process scheduler
		ProcessScheduler.init();

		// Initialize event manager
		EventManager.init();

		// Initialize mixin manager
		MixinManager.init();

		// When server starts, capture reference and init all subsystems
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			serverInstance = server;
			scriptLoader = new ScriptLoader(server.getRunDirectory());
			scriptLoader.ensureDirectory();

			// Initialize global variables
			GlobalVariables.init(server.getRunDirectory());

			// Load all script event handlers + fire 'load' event
			MixinManager.getInstance().loadAllScripts(server);
			EventManager.getInstance().loadAllScripts(server);

			LOGGER.info("[SScript] Ready! Scripts directory: {}", scriptLoader.getScriptsDir());
		});

		// Fire player_join event via Fabric API
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			EventManager manager = EventManager.getInstance();
			if (manager != null) {
				manager.fire("player_join", server, buildPlayerPayload(handler.getPlayer()));
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			EventManager manager = EventManager.getInstance();
			if (manager != null) {
				manager.fire("player_leave", server, buildPlayerPayload(handler.getPlayer()));
			}
		});

		// Fire player_connect event as soon as connection is initialized
		ServerPlayConnectionEvents.INIT.register((handler, server) -> {
			EventManager manager = EventManager.getInstance();
			if (manager != null) {
				manager.fire("player_connect", server, buildPlayerPayload(handler.getPlayer()));
			}
		});

		// Fire player_chat event via Fabric API (no mixin needed)
		ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
			MixinManager manager = MixinManager.getInstance();
			if (manager != null) {
				String content = message.getContent().getString();
				return !manager.fireCancelable("player_chat", sender.getEntityWorld().getServer(), buildPlayerPayload(sender), content);
			}
			return true;
		});

		// Fire player_chat event after broadcast for optimized .event.ss scripts
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			EventManager manager = EventManager.getInstance();
			if (manager != null) {
				String content = message.getContent().getString();
				manager.fire("player_chat", sender.getEntityWorld().getServer(), buildPlayerPayload(sender), content);
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			GlobalVariables globals = GlobalVariables.getInstance();
			if (globals != null) {
				globals.forceSave();
			}
			serverInstance = null;
			scriptLoader = null;
		});
	}

	public static MinecraftServer getServer() {
		return serverInstance;
	}

	public static ScriptLoader getScriptLoader() {
		return scriptLoader;
	}

	public static boolean isDebugEnabled() {
		return debugEnabled;
	}

	public static void setDebugEnabled(boolean enabled) {
		debugEnabled = enabled;
	}

	public static Map<String, Object> buildPlayerPayload(ServerPlayerEntity player) {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", player.getName().getString());
		payload.put("uuid", player.getUuidAsString());
		payload.put("type", player.getType().toString());
		payload.put("x", player.getX());
		payload.put("y", player.getY());
		payload.put("z", player.getZ());
		payload.put("pos", player.getX() + " " + player.getY() + " " + player.getZ());
		payload.put("dimension", player.getEntityWorld().getRegistryKey().getValue().toString());
		payload.put("health", (double) player.getHealth());
		payload.put("gamemode", player.interactionManager.getGameMode().asString());
		List<String> tags = new ArrayList<>(player.getCommandTags());
		payload.put("tags", tags);

		try {
			MinecraftServer server = getServer();
			if (server != null) {
				NbtWriteView writeView = NbtWriteView.create(ErrorReporter.EMPTY, server.getRegistryManager());
				player.writeData(writeView);
				NbtCompound nbt = writeView.getNbt();
				payload.put("nbt", nbt != null ? nbt.toString() : "{}");
			} else {
				payload.put("nbt", "{}");
			}
		} catch (Exception e) {
			payload.put("nbt", "{}");
		}
		return payload;
	}
}