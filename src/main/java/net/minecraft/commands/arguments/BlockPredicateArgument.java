package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.List;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 BlockPredicateArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.BlockPredicateArgument} —
 * used by /execute if block (tag form) and /locate-style predicates. Parses
 * {@code #tag} or a block id. HONEST BOUNDARY: block tag expansion is not
 * available in the 1.8 registry layer — a {@code #tag} token is stored but
 * {@code test} reports false with an {@code unsupported} semantics the caller
 * can log (never a fabricated match, §18.2b).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-BPRED-001
 */
public class BlockPredicateArgument implements ArgumentType<BlockPredicateArgument.BlockPredicate> {

	/** Predicate value: id-based via BlockStateArgument, or an honest tag stub. */
	public interface BlockPredicate {
		boolean test(net.minecraft.block.state.IBlockState state);

		boolean isTag();
	}

	private BlockPredicateArgument() {
	}

	public static BlockPredicateArgument blockPredicate() {
		return new BlockPredicateArgument();
	}

	@Override
	public BlockPredicate parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())
				&& reader.peek() != '[' && reader.peek() != '{') {
			reader.skip();
		}
		String token = reader.getString().substring(start, reader.getCursor());
		if (token.isEmpty()) {
			throw new CommandSyntaxException("Expected a block predicate at position " + start);
		}
		if (token.startsWith("#")) {
			final String tag = token.substring(1);
			return new BlockPredicate() {
				@Override
				public boolean test(net.minecraft.block.state.IBlockState state) {
					return false; // honest: 1.8 has no 1.20.1 block-tag expansion
				}

				@Override
				public boolean isTag() {
					return true;
				}
			};
		}
		// Non-tag: delegate the parse to the block-state machinery.
		reader.setCursor(start);
		final BlockStateArgument.BlockInput input = BlockStateArgument.blockState().parse(reader);
		return new BlockPredicate() {
			@Override
			public boolean test(net.minecraft.block.state.IBlockState state) {
				return input.test(state);
			}

			@Override
			public boolean isTag() {
				return false;
			}
		};
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "blockPredicate()";
	}
}
