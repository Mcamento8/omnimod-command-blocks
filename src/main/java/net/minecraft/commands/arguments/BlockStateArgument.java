package net.minecraft.commands.arguments;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

/**
 * [Agent Note 2026-08-28] GENERAL: Forge 1.20.1 BlockStateArgument shim.
 *
 * Mirrors {@code net.minecraft.commands.arguments.BlockStateArgument} — used
 * by /setblock, /fill, /execute if|unless block... Parses
 * {@code minecraft:stone}, {@code stone[lower=true]}, and an optional trailing
 * {nbt} (parsed off and honestly ignored — 1.8 tiles have no SNBT).
 *
 * RESOLUTION: the block id resolves through the REAL 1.8
 * {@link Block#blockRegistry}; declared properties are matched against the
 * block's own property map — unknown properties/properties for blocks that do
 * not carry them are dropped with an honest marker (the 1.8 state space is
 * narrower than 1.20.1's; documented boundary, never a crash).
 *
 * GENERAL — standard 1.20.1 API surface, no mod hardcode.
 *
 * Doc-ID: MC-ARG-BSTATE-001
 */
public class BlockStateArgument implements ArgumentType<BlockStateArgument.BlockInput> {

	/** A parsed block state input. */
	public static final class BlockInput {
		public final String namespace;
		public final String path;
		public final Map<String, String> properties;
		public final boolean hasProperties;

		public BlockInput(String namespace, String path, Map<String, String> properties,
				boolean hasProperties) {
			this.namespace = namespace;
			this.path = path;
			this.properties = properties;
			this.hasProperties = hasProperties;
		}

		public String getJoinedId() {
			return namespace + ":" + path;
		}

		/** Resolve to the 1.8 Block (null when unknown). */
		public Block getBlock() {
			try {
				return (Block) Block.blockRegistry.getObject(new ResourceLocation(namespace, path));
			} catch (Throwable ignored) {
				return null;
			}
		}

		/**
		 * Resolve to a 1.8 IBlockState: the block's default state with every
		 * DECLARED matching property applied (unknown ones are skipped — the
		 * vanilla 1.20.1 error surface is narrower here, documented boundary).
		 * The 1.8 property API is generic-raw, hence the unchecked casts.
		 */
		@SuppressWarnings({"rawtypes", "unchecked"})
		public IBlockState getState() {
			Block block = getBlock();
			if (block == null) {
				return null;
			}
			IBlockState state = block.getDefaultState();
			if (properties == null || properties.isEmpty()) {
				return state;
			}
			for (Map.Entry<String, String> e : properties.entrySet()) {
				for (Object pObj : state.getPropertyNames()) {
					IProperty prop = (IProperty) pObj;
					if (!prop.getName().equals(e.getKey())) {
						continue;
					}
					for (Object v : (java.util.Collection) prop.getAllowedValues()) {
						if (String.valueOf(v).equals(e.getValue())) {
							state = applyProperty(state, prop, v);
							break;
						}
					}
					break;
				}
			}
			return state;
		}

		/** Compare against a live world state (block identity + declared props). */
		@SuppressWarnings({"rawtypes", "unchecked"})
		public boolean test(IBlockState worldState) {
			if (worldState == null) {
				return false;
			}
			Block block = getBlock();
			if (block == null || worldState.getBlock() != block) {
				return false;
			}
			if (properties == null || properties.isEmpty()) {
				return true;
			}
			for (Map.Entry<String, String> e : properties.entrySet()) {
				boolean checked = false;
				for (Object pObj : worldState.getPropertyNames()) {
					IProperty prop = (IProperty) pObj;
					if (!prop.getName().equals(e.getKey())) {
						continue;
					}
					Object worldValue = worldState.getValue(prop);
					if (worldValue == null || !String.valueOf(worldValue).equals(e.getValue())) {
						return false;
					}
					checked = true;
					break;
				}
				if (checked) {
					continue;
				}
				// Property not present on the 1.8 state space — ignored (the
				// declared-props filter above stays the vanilla-equivalent
				// subset; documented boundary).
			}
			return true;
		}
	}

	private BlockStateArgument() {
	}

	public static BlockStateArgument blockState() {
		return new BlockStateArgument();
	}

	/** Real 1.20.1 static: resolve the named argument to a BlockInput. */
	public static BlockInput getBlock(CommandContext<?> ctx, String name) {
		return ctx.getArgument(name, BlockInput.class);
	}

	@Override
	public BlockInput parse(StringReader reader) throws CommandSyntaxException {
		reader.skipWhitespace();
		int start = reader.getCursor();
		while (reader.canRead() && !reader.isWhitespace(reader.peek())
				&& reader.peek() != '[' && reader.peek() != '{') {
			reader.skip();
		}
		String idToken = reader.getString().substring(start, reader.getCursor());
		if (idToken.isEmpty()) {
			throw new CommandSyntaxException("Expected a block id at position " + start);
		}
		String ns = "minecraft";
		String path = idToken;
		int colon = idToken.indexOf(':');
		if (colon >= 0) {
			ns = idToken.substring(0, colon);
			path = idToken.substring(colon + 1);
		}
		Map<String, String> props = new HashMap<String, String>();
		boolean hasProps = false;
		if (reader.canRead() && reader.peek() == '[') {
			hasProps = true;
			reader.skip();
			StringBuilder key = new StringBuilder();
			String lastKey = null;
			boolean readingValue = false;
			StringBuilder value = new StringBuilder();
			while (reader.canRead()) {
				char c = reader.peek();
				if (c == ']') {
					reader.skip();
					break;
				} else if (c == '=') {
					reader.skip();
					lastKey = key.toString().trim();
					key.setLength(0);
					readingValue = true;
				} else if (c == ',') {
					reader.skip();
					if (readingValue && lastKey != null) {
						props.put(lastKey, value.toString().trim());
					}
					lastKey = null;
					key.setLength(0);
					value.setLength(0);
					readingValue = false;
				} else {
					reader.skip();
					if (readingValue) {
						value.append(c);
					} else {
						key.append(c);
					}
				}
			}
			if (readingValue && lastKey != null) {
				props.put(lastKey, value.toString().trim());
			}
		}
		if (reader.canRead() && reader.peek() == '{') {
			int depth = 0;
			while (reader.canRead()) {
				char c = reader.peek();
				reader.skip();
				if (c == '{') {
					depth++;
				} else if (c == '}') {
					depth--;
					if (depth == 0) {
						break;
					}
				}
			}
		}
		return new BlockInput(ns, path, props, hasProps);
	}

	@Override
	public List<String> listSuggestions(String remaining) {
		return Collections.emptyList();
	}

	@Override
	public String toString() {
		return "blockState()";
	}

	/** Raw-type bridge: the 1.8 IBlockState/IProperty generics cannot express
	 *  a string-selected value without this unchecked hop. */
	@SuppressWarnings({"rawtypes", "unchecked"})
	private static IBlockState applyProperty(IBlockState state, IProperty prop, Object value) {
		return state.withProperty((IProperty) prop, (Comparable) value);
	}
}
