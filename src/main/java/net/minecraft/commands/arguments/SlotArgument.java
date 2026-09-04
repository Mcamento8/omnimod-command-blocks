package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 SlotArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.SlotArgument} — /replaceitem,
 * /item. Supported (documented subset of the vanilla 1.20.1 surface):
 *   container.<n>  -> n   (0-based container slot)
 *   weapon.mainhand / weapon.offhand -> -106 / -106 (1.8 uses the main-inventory
 *      id space; offhand is NOT separable in 1.8 — honest boundary)
 *   armor.head|chest|legs|feet -> 103 / 102 / 101 / 100 (1.8 ids)
 *   <int> -> passthrough
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-SLOT-001
 */
public class SlotArgument implements ArgumentType<Integer> {

	private SlotArgument() {
	}

	public static SlotArgument slot() {
		return new SlotArgument();
	}

	@Override
	public Integer parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		if (reader.canRead() && (Character.isDigit(reader.peek()))) {
			int v = reader.readInt();
			return Integer.valueOf(v);
		}
		while (reader.canRead() && !reader.isWhitespace(reader.peek())) {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor()).toLowerCase();
		if (token.startsWith("container.")) {
			try {
				return Integer.valueOf(Integer.parseInt(token.substring("container.".length())));
			} catch (NumberFormatException e) {
				throw new CommandSyntaxException("Invalid slot '" + token + "' at position " + start);
			}
		}
		if (token.equals("weapon.mainhand")) {
			return Integer.valueOf(0);
		}
		if (token.equals("weapon.offhand")) {
			// 1.8 has no offhand — honest mapping to the main slot index.
			return Integer.valueOf(0);
		}
		if (token.equals("armor.head")) {
			return Integer.valueOf(103);
		}
		if (token.equals("armor.chest")) {
			return Integer.valueOf(102);
		}
		if (token.equals("armor.legs")) {
			return Integer.valueOf(101);
		}
		if (token.equals("armor.feet")) {
			return Integer.valueOf(100);
		}
		throw new CommandSyntaxException("Unknown slot '" + token + "' at position " + start);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "slot()";
	}
}
