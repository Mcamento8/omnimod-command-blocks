package net.minecraft.commands.arguments.selector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerSelector;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 EntitySelector shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.selector.EntitySelector}.
 * Real Brigadier's EntityArgument.parse returns one of these; mods may hold
 * it ({@code ctx.getArgument("x", EntitySelector.class)}) or resolve it
 * through {@code EntityArgument} static helpers.
 *
 * RESOLUTION (1.8 engine): selectors starting with '@' go through
 * {@link PlayerSelector#matchEntities} (the same expansion vanilla 1.8
 * commands use); plain tokens resolve as player names through the integrated
 * server's player list. No match → empty list (honest, never a fake entity).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-ESEL-001
 */
public class EntitySelector {

	private final String token;

	public EntitySelector(String token) {
		this.token = token != null ? token : "";
	}

	/** @return the raw selector/name token this selector was parsed from. */
	public String getRawToken() {
		return token;
	}

	/** Resolve to all matched entities (empty on no-match). */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public List<Entity> getEntities(ICommandSender sender) {
		if (token.isEmpty()) {
			return Collections.emptyList();
		}
		if (token.startsWith("@")) {
			try {
				List<Entity> matched = PlayerSelector.matchEntities(sender, token, Entity.class);
				return matched != null ? matched : Collections.<Entity>emptyList();
			} catch (Throwable t) {
				// Malformed selector: honest empty result — the caller decides.
				return Collections.emptyList();
			}
		}
		// Plain name: resolve against the integrated server's player list.
		try {
			EntityPlayerMP p = net.minecraft.server.MinecraftServer.getServer().getConfigurationManager()
					.getPlayerByUsername(token);
			if (p != null) {
				return Collections.<Entity>singletonList((Entity) p);
			}
		} catch (Throwable ignored) {
		}
		return Collections.emptyList();
	}

	/** Resolve to exactly one entity (first match) or null. */
	public Entity findEntity(ICommandSender sender) {
		List<Entity> l = getEntities(sender);
		return l.isEmpty() ? null : l.get(0);
	}

	/** Resolve to all matched players. */
	public List<EntityPlayerMP> findPlayers(ICommandSender sender) {
		List<Entity> all = getEntities(sender);
		List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
		for (Entity e : all) {
			if (e instanceof EntityPlayerMP) {
				out.add((EntityPlayerMP) e);
			}
		}
		return out;
	}

	/** Resolve to exactly one player or null. */
	public EntityPlayerMP findPlayer(ICommandSender sender) {
		for (EntityPlayerMP p : findPlayers(sender)) {
			return p;
		}
		return null;
	}

	@Override
	public String toString() {
		return "EntitySelector(" + token + ")";
	}
}
