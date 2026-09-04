package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.network.chat.Component;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 MessageArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.MessageArgument} — used by
 * /tell, /me, /say in 1.20.1. Consumes the GREEDY rest of the input; mods
 * extract it via {@code getMessage(ctx, name)} which returns a
 * {@link Component} (rendered through the engine's legacy chat conversion).
 *
 * HONEST BOUNDARY (§19.8): selector expansion inside the message
 * ({@code @p said hi}) is not performed — the token text is delivered as-is.
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-MSG-001
 */
public class MessageArgument implements ArgumentType<MessageArgument.Message> {

	/** The greedy message value. */
	public static final class Message {
		private final String text;

		public Message(String text) {
			this.text = text != null ? text : "";
		}

		/** Vanilla-shaped accessor — returns the message as a Component. */
		public Component getMessage() {
			return Component.literal(text);
		}

		public String getText() {
			return text;
		}
	}

	private MessageArgument() {
	}

	public static MessageArgument message() {
		return new MessageArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a Message. */
	public static Message getMessage(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, Message.class);
	}

	@Override
	public Message parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		// readRemaining() CONSUMES (getRemaining() only peeks — the node would
		// reject the value as "consumed nothing"; proven by harness H12b).
		return new Message(reader.readRemaining());
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "message()";
	}
}
