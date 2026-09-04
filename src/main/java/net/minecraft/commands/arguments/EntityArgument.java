package net.minecraft.commands.arguments;

import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.arguments.selector.EntitySelector;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 EntityArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.EntityArgument} — the
 * MOST-USED argument type in the real mod corpus (39 imports). Mods write:
 *   Commands.argument("target", EntityArgument.player())
 *   ... EntityArgument.getPlayer(ctx, "target")
 *   ... EntityArgument.getEntities(ctx, "targets")
 * or hold the selector directly: ctx.getArgument("target", EntitySelector.class)
 *
 * HOW IT RESOLVES: parse() produces an {@link EntitySelector} holding the raw
 * token; resolution against the live world happens at execution time through
 * {@code PlayerSelector} (selectors) / player-name lookup — the same deferral
 * real Brigadier uses (selector evaluation is lazy, at getEntities()).
 *
 * GENERAL — serves every Forge 1.20.1 command mod. No mod id hardcode.
 *
 * Doc-ID: MC-ARG-ENTITY-001
 */
public class EntityArgument implements ArgumentType<EntitySelector> {

	private final boolean single;
	private final boolean playersOnly;

	private EntityArgument(boolean single, boolean playersOnly) {
		this.single = single;
		this.playersOnly = playersOnly;
	}

	public static EntityArgument entity() {
		return new EntityArgument(true, false);
	}

	public static EntityArgument entities() {
		return new EntityArgument(false, false);
	}

	public static EntityArgument player() {
		return new EntityArgument(true, true);
	}

	public static EntityArgument players() {
		return new EntityArgument(false, true);
	}

	@Override
	public EntitySelector parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected entity selector at position " + start);
		}
		return new EntitySelector(token);
	}

	/** Real 1.20.1 static: resolve the named argument to one entity (or null). */
	public static net.minecraft.entity.Entity getEntity(CommandContext<?> ctx, String name) {
		EntitySelector sel = ctx.getArgument(name, EntitySelector.class);
		return sel == null ? null : sel.findEntity(sourceOf(ctx));
	}

	/** Real 1.20.1 static: resolve the named argument to a player (or null). */
	public static net.minecraft.entity.player.EntityPlayerMP getPlayer(CommandContext<?> ctx, String name) {
		EntitySelector sel = ctx.getArgument(name, EntitySelector.class);
		return sel == null ? null : sel.findPlayer(sourceOf(ctx));
	}

	/** Real 1.20.1 static: resolve the named argument to all matched players. */
	public static java.util.List<net.minecraft.entity.player.EntityPlayerMP> getPlayers(CommandContext<?> ctx,
			String name) {
		EntitySelector sel = ctx.getArgument(name, EntitySelector.class);
		return sel == null ? java.util.Collections.<net.minecraft.entity.player.EntityPlayerMP>emptyList()
				: sel.findPlayers(sourceOf(ctx));
	}

	/** Real 1.20.1 static: resolve the named argument to all matched entities. */
	public static java.util.List<net.minecraft.entity.Entity> getEntities(CommandContext<?> ctx, String name) {
		EntitySelector sel = ctx.getArgument(name, EntitySelector.class);
		return sel == null ? java.util.Collections.<net.minecraft.entity.Entity>emptyList()
				: sel.getEntities(sourceOf(ctx));
	}

	/**
	 * Extract the 1.8 ICommandSender behind the context's source. Works for any
	 * CommandContext<S> whose source is (or wraps) an ICommandSender —
	 * CommandSourceStack.getRawSender() unwraps it.
	 */
	static net.minecraft.command.ICommandSender sourceOf(CommandContext<?> ctx) {
		Object src = ctx.getSource();
		if (src instanceof net.minecraft.commands.CommandSourceStack) {
			return ((net.minecraft.commands.CommandSourceStack) src).getRawSender();
		}
		if (src instanceof net.minecraft.command.ICommandSender) {
			return (net.minecraft.command.ICommandSender) src;
		}
		return null;
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return java.util.Collections.emptyList();
	}

	@Override
	public String toString() {
		return "entityArg(single=" + single + ", playersOnly=" + playersOnly + ")";
	}
}
