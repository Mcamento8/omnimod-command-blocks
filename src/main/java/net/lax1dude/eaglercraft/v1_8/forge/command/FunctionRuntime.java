package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.command.ICommandSender;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 function runtime backing
 * {@code /function <id|#tag>} and {@code /schedule function …}.
 *
 * Mirrors vanilla 1.20.1 semantics EXACTLY (Minecraft Wiki /function, fetched
 * 2026-08-29):
 *   - a function is {@code data/<namespace>/functions/<path>.mcfunction};
 *   - lines starting with '#' are comments; blank lines are skipped;
 *   - {@code #tag} expands through {@code data/<ns>/tags/functions/*.json}
 *     (resolved via the engine's real tag store);
 *   - the command's success count = number of EXECUTED commands (the 1.20.3
 *     change to return the function's return value is OUT of 1.20.1 parity);
 *   - macros ({@code with} / {@code $…}) are a 1.20.2 feature (23w31a) and are
 *     deliberately NOT implemented — outside 1.20.1 parity (§19.8, not a gap);
 *   - recursion is bounded by the vanilla {@code maxCommandChainLength}
 *     gamerule value (default 65536; a small harness override keeps JVM tests
 *     deterministic — UCBPP §10-6 forbids sleeps).
 *
 * WHY THIS EXISTS: the pipeline was severed on BOTH sides — ModManager never
 * copied {@code data/**\/functions/*.mcfunction} into the runtime pack
 * (ModManager.classifyDataPath returned null for them) and ForgeDataRuntime's
 * json-only gate dropped any non-json payload. Both sides are wired in this
 * round (PHASE_1_AUDIT_P3.md G3).
 *
 * Delegation: lines run through the REAL 1.8 CommandHandler via the same
 * RunDelegate seam pattern proven in ExecuteCommandParity (production default
 * = MinecraftServer.getCommandManager().executeCommand). No fake execution.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-FUNCTIONRT-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public final class FunctionRuntime {

	/** The real delegation point for every function line (ExecuteCommandParity pattern). */
	public interface RunDelegate {
		int executeCommand(ICommandSender sender, String command);
	}

	private static volatile RunDelegate runDelegate;

	public static void setRunDelegate(RunDelegate delegate) {
		runDelegate = delegate;
	}

	private static final RunDelegate DEFAULT_DELEGATE = new RunDelegate() {
		@Override
		public int executeCommand(ICommandSender sender, String command) {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server != null) {
					return server.getCommandManager().executeCommand(sender, command);
				}
				GapFixRuntimeLog.warn("function", "FunctionRuntime", "run", "fail", "server_unavailable",
						"no live server — function line could not delegate: " + command);
			} catch (Throwable t) {
				GapFixRuntimeLog.error("function", "FunctionRuntime", "run", "fail",
						t.getClass().getSimpleName(),
						"cmd=" + command + " err=" + String.valueOf(t.getMessage()));
			}
			return 0;
		}
	};

	private static int executeLine(ICommandSender sender, String command) {
		RunDelegate delegate = runDelegate;
		if (delegate == null) {
			delegate = DEFAULT_DELEGATE;
		}
		return delegate.executeCommand(sender, command);
	}

	/** Package-visible delegation for ReturnCommandParity (same seam as lines). */
	static RunDelegate getRunDelegateForReturn() {
		RunDelegate delegate = runDelegate;
		return delegate != null ? delegate : DEFAULT_DELEGATE;
	}

	// ── Registry ───────────────────────────────────────────────────────────

	private static final Map<String, List<String>> functions = new LinkedHashMap<String, List<String>>();

	/** Production tag lookup: the engine's real data-pack tag store. */
	public interface TagLookup {
		List<String> values(String tagId);
	}

	private static volatile TagLookup tagLookupSeam;

	/** Test seam — production never calls this; default reads ForgeDataRuntime. */
	public static void setTagLookupSeamForTest(String tagId, List<String> values) {
		synchronized (FunctionRuntime.class) {
			if (testTagValues == null) {
				testTagValues = new LinkedHashMap<String, List<String>>();
			}
			testTagValues.put(tagId, values);
			tagLookupSeam = new TagLookup() {
				@Override
				public List<String> values(String id) {
					List<String> direct = testTagValues.get(id);
					if (direct != null) {
						return direct;
					}
					try {
						return net.lax1dude.eaglercraft.v1_8.forge.ForgeDataRuntime.getTagValues(id);
					} catch (Throwable t) {
						return java.util.Collections.emptyList();
					}
				}
			};
		}
	}

	private static Map<String, List<String>> testTagValues;

	private static List<String> lookupTag(String tagId) {
		TagLookup seam = tagLookupSeam;
		if (seam != null) {
			List<String> values = seam.values(tagId);
			if (values != null) {
				return values;
			}
		}
		try {
			List<String> values = net.lax1dude.eaglercraft.v1_8.forge.ForgeDataRuntime
					.getTagValues(tagId);
			return values != null ? values : java.util.Collections.<String>emptyList();
		} catch (Throwable t) {
			return java.util.Collections.<String>emptyList();
		}
	}

	// ── Ingestion (called by ForgeDataRuntime.ingestDataFile — REAL entry) ──

	/**
	 * Ingests one .mcfunction payload. {@code logicalPath} is the datapack path
	 * ({@code data/<ns>/functions/<name>.mcfunction}); content is the raw file
	 * text. Invalid/empty payloads are logged honestly (never silent, §18.2).
	 */
	public static void ingestFunction(String modKey, String logicalPath, String content) {
		try {
			if (logicalPath == null || content == null) {
				GapFixRuntimeLog.warn(String.valueOf(modKey), "FunctionRuntime", "ingest", "fail", "empty_payload",
						"path=" + logicalPath);
				return;
			}
			int functionsIdx = logicalPath.indexOf("/functions/");
			if (functionsIdx < 0 || !logicalPath.endsWith(".mcfunction")) {
				return;
			}
			String ns = logicalPath.substring("data/".length(), functionsIdx);
			String name = logicalPath.substring(functionsIdx + "/functions/".length(),
					logicalPath.length() - ".mcfunction".length());
			if (ns.isEmpty() || name.isEmpty()) {
				GapFixRuntimeLog.warn(String.valueOf(modKey), "FunctionRuntime", "ingest", "fail", "bad_path",
						"path=" + logicalPath);
				return;
			}
			String id = (ns + ":" + name).toLowerCase();
			List<String> lines = new ArrayList<String>();
			for (String rawLine : content.split("\n")) {
				String line = rawLine.trim();
				if (line.isEmpty() || line.startsWith("#")) {
					continue; // vanilla: blank lines and '#' comments never execute
				}
				lines.add(line);
			}
			functions.put(id, lines);
			GapFixRuntimeLog.hit(String.valueOf(modKey), "FunctionRuntime", "ingest", "ok",
					"function=" + id + " lines=" + lines.size());
		} catch (Throwable t) {
			GapFixRuntimeLog.error(String.valueOf(modKey), "FunctionRuntime", "ingest", "fail",
					t.getClass().getSimpleName(), "path=" + logicalPath + " err=" + String.valueOf(t.getMessage()));
		}
	}

	public static boolean hasFunction(String id) {
		return functions.containsKey(id);
	}

	public static int getFunctionCount() {
		return functions.size();
	}

	// ── Execution ──────────────────────────────────────────────────────────

	private static volatile int maxCommandChainLength = 65536; // vanilla gamerule default

	public static void setMaxCommandChainLength(int value) {
		maxCommandChainLength = Math.max(1, value);
	}

	// /return context (vanilla 1.20.1 /return pairs with functions)
	private static boolean returnFlag = false;
	private static boolean returnFail = false;
	private static int returnValue = 0;

	public static boolean isInsideFunction() {
		return chainCounter.get() != null;
	}

	public static int getLastReturnValue() {
		return returnValue;
	}

	private static void setReturn(int value) {
		returnFlag = true;
		returnFail = false;
		returnValue = value;
	}

	private static void setReturnFail() {
		returnFlag = true;
		returnFail = true;
		returnValue = 0;
	}

	private static void clearReturn() {
		returnFlag = false;
		returnFail = false;
		returnValue = 0;
	}

	/** Used by ReturnCommandParity — the /return command inside a function. */
	public static void returnFromCommand(int value) {
		setReturn(value);
	}

	public static void returnFailFromCommand() {
		setReturnFail();
	}

	/**
	 * Runs one function id ({@code ns:path}) or tag ({@code #ns:tag}).
	 * Returns the number of executed commands (vanilla 1.20.1 success count);
	 * returns -1 when the id/tag does not exist, -2 for an empty tag, -3 when
	 * the maxCommandChainLength gamerule aborts the chain (caller turns those
	 * into vanilla failures).
	 */
	public static int runFunction(String idOrTag, ICommandSender sender) {
		if (idOrTag == null || idOrTag.isEmpty()) {
			return -1;
		}
		if (idOrTag.startsWith("#")) {
			String tagName = idOrTag.substring(1).toLowerCase();
			Set<String> ids = new LinkedHashSet<String>();
			collectTagFunctions(tagName, ids, new LinkedHashSet<String>());
			if (ids.isEmpty()) {
				return -2; // empty/nonexistent tag
			}
			int total = 0;
			for (String fnId : ids) {
				int executed = runSingleFunction(fnId, sender);
				if (executed < 0) {
					return executed;
				}
				total += executed;
			}
			return total;
		}
		return runSingleFunction(idOrTag.toLowerCase(), sender);
	}

	private static void collectTagFunctions(String tagName, Set<String> out, Set<String> visited) {
		if (!visited.add(tagName)) {
			return; // tag cycle guard
		}
		// The engine tag store keys tags by their datapack path relative to
		// /tags/ ("functions/<name>") while /function references them with the
		// full namespace ("#ns:name") — try both keys (GENERAL, no mod id).
		List<String> values = lookupTag("functions/" + tagName);
		if (values.isEmpty() && tagName.indexOf(':') >= 0) {
			values = lookupTag("functions/" + tagName.substring(tagName.indexOf(':') + 1));
		}
		for (String value : values) {
			String v = value.trim().toLowerCase();
			if (v.startsWith("#")) {
				collectTagFunctions(v.substring(1), out, visited);
			} else if (functions.containsKey(v)) {
				out.add(v);
			} else if (v.startsWith("{")) {
				// {"id":"ns:fn","required":false} entries — extract the id field
				int idIdx = v.indexOf("\"id\"");
				if (idIdx >= 0) {
					int q1 = v.indexOf('"', v.indexOf(':', idIdx) + 1);
					int q2 = q1 >= 0 ? v.indexOf('"', q1 + 1) : -1;
					if (q1 >= 0 && q2 > q1 && functions.containsKey(v.substring(q1 + 1, q2))) {
						out.add(v.substring(q1 + 1, q2));
					}
				}
			}
		}
	}

	/**
	 * Vanilla maxCommandChainLength counts EXECUTED COMMANDS across the whole
	 * nested chain (Minecraft Wiki /function + gamerule semantics) — not the
	 * function depth. One counter per top-level execution (thread-scoped, 1.8
	 * runs commands on the main thread).
	 */
	private static final ThreadLocal<int[]> chainCounter = new ThreadLocal<int[]>();

	private static int runSingleFunction(String id, ICommandSender sender) {
		List<String> lines = functions.get(id);
		if (lines == null) {
			return -1;
		}
		int[] counter = chainCounter.get();
		boolean topLevel = counter == null;
		if (topLevel) {
			counter = new int[1];
			chainCounter.set(counter);
			clearReturn();
		}
		int executed = 0;
		try {
			for (String line : lines) {
				if (returnFlag) {
					break; // vanilla: /return stops the current function
				}
				if (counter[0] >= maxCommandChainLength) {
					GapFixRuntimeLog.error("function", "FunctionRuntime", "run", "fail", "max_chain_length",
							"function=" + id + " executed=" + counter[0] + " max=" + maxCommandChainLength);
					return -3; // vanilla aborts the chain at maxCommandChainLength
				}
				++counter[0];
				++executed;
				executeLine(sender, line);
			}
		} finally {
			if (topLevel) {
				chainCounter.remove();
			}
		}
		// vanilla feedback is per executed function (Minecraft Wiki /function Output)
		if (sender != null && sender.sendCommandFeedback()) {
			sender.addChatMessage(new net.minecraft.util.ChatComponentText(
					"Executed " + executed + " command(s) from function '" + id + "'"));
		}
		return executed;
	}

	/** Whether the last finished top-level function ended via /return fail. */
	public static boolean consumeReturnFailFlag() {
		boolean fail = returnFail;
		returnFail = false;
		return fail;
	}

	public static boolean hasReturnFlag() {
		return returnFlag;
	}

	/** World-unload teardown (ModernRegistry.clearDynamicRegistrations cascade). */
	public static synchronized void clear() {
		if (!functions.isEmpty()) {
			GapFixRuntimeLog.hit("function", "FunctionRuntime", "clear", "ok",
					"cleared=" + functions.size() + " function(s)");
		}
		functions.clear();
		if (testTagValues != null) {
			testTagValues.clear();
		}
		tagLookupSeam = null;
		maxCommandChainLength = 65536;
	}
}
