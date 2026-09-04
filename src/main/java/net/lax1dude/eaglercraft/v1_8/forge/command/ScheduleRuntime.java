package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 /schedule runtime — pending
 * function executions keyed by function id with vanilla replace/append modes.
 *
 * Mirrors vanilla semantics (Minecraft Wiki /schedule, fetched 2026-08-29):
 *   - {@code schedule function <id> <time> [append|replace]} (default replace);
 *   - "replace" cancels the SAME function's pending task(s) (one per function);
 *   - "append" allows multiple pendings for the same function;
 *   - tasks fire with the SERVER's command source at the world spawn;
 *   - {@code /schedule clear <id>} removes all pendings of that function.
 *
 * Tick source: {@link ForgeHooks#onServerTick} (previously a dead hook — no
 * engine caller existed; wired into MinecraftServer.tick() in this round, see
 * PHASE_1_AUDIT_P3.md G4). The clock seam exists so the JVM harness drives
 * ticks deterministically — UCBPP §10-6 forbids sleeps; production reads the
 * real world total time.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-SCHEDULERT-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public final class ScheduleRuntime {

	/** Production tick clock seam (harness substitutes a deterministic counter). */
	public interface ClockSeam {
		long currentTick();
	}

	private static volatile ClockSeam clockSeam;

	public static void setClockSeam(ClockSeam seam) {
		clockSeam = seam;
	}

	private static final ClockSeam DEFAULT_CLOCK = new ClockSeam() {
		@Override
		public long currentTick() {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server != null && server.worldServers != null && server.worldServers.length > 0
						&& server.worldServers[0] != null) {
					return server.worldServers[0].getTotalWorldTime();
				}
			} catch (Throwable t) {
				GapFixRuntimeLog.warn("schedule", "ScheduleRuntime", "clock", "fail",
						t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
			}
			return 0L;
		}
	};

	private static long currentTick() {
		ClockSeam seam = clockSeam;
		if (seam != null) {
			return seam.currentTick();
		}
		return DEFAULT_CLOCK.currentTick();
	}

	/** One pending execution (vanilla keys pendings by FUNCTION id). */
	private static final class Pending {
		final String functionId;
		final long dueTick;
		final long scheduledTick;
		final long seq; // FIFO for same-tick fairness

		Pending(String functionId, long dueTick, long scheduledTick, long seq) {
			this.functionId = functionId;
			this.dueTick = dueTick;
			this.scheduledTick = scheduledTick;
			this.seq = seq;
		}
	}

	/** functionId → pending tasks (replace = clear the list; append = add). */
	private static final Map<String, List<Pending>> pendings = new LinkedHashMap<String, List<Pending>>();
	private static long seqCounter = 0;

	/**
	 * Vanilla /schedule function … — delay is in game ticks (already converted
	 * by TimeArgument from 1s/1d forms). Returns the due tick for feedback.
	 */
	public static synchronized long schedule(String functionId, long delayTicks, boolean append) {
		long now = currentTick();
		long due = now + Math.max(1L, delayTicks);
		List<Pending> list = pendings.get(functionId);
		if (!append || list == null) {
			list = new ArrayList<Pending>();
			pendings.put(functionId, list);
		}
		list.add(new Pending(functionId, due, now, ++seqCounter));
		return due;
	}

	/** Vanilla /schedule clear — returns the number of removed pendings. */
	public static synchronized int clear(String functionId) {
		List<Pending> removed = pendings.remove(functionId);
		return removed == null ? 0 : removed.size();
	}

	public static synchronized int pendingCount() {
		int n = 0;
		for (List<Pending> l : pendings.values()) {
			n += l.size();
		}
		return n;
	}

	public static synchronized boolean hasPending(String functionId) {
		List<Pending> l = pendings.get(functionId);
		return l != null && !l.isEmpty();
	}

	/**
	 * Fires every due pending, in due-tick order (FIFO within a tick), with the
	 * vanilla server command source. Called once per server tick from
	 * ForgeHooks.onServerTick (never on the client thread).
	 */
	public static synchronized void onServerTick() {
		if (pendings.isEmpty()) {
			return;
		}
		long now = currentTick();
		List<Pending> due = new ArrayList<Pending>();
		Iterator<Map.Entry<String, List<Pending>>> it = pendings.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, List<Pending>> entry = it.next();
			Iterator<Pending> taskIt = entry.getValue().iterator();
			while (taskIt.hasNext()) {
				Pending p = taskIt.next();
				if (p.dueTick <= now) {
					due.add(p);
					taskIt.remove();
				}
			}
			if (entry.getValue().isEmpty()) {
				it.remove();
			}
		}
		if (due.isEmpty()) {
			return;
		}
		java.util.Collections.sort(due, new Comparator<Pending>() {
			@Override
			public int compare(Pending a, Pending b) {
				if (a.dueTick != b.dueTick) {
					return a.dueTick < b.dueTick ? -1 : 1;
				}
				return a.seq < b.seq ? -1 : (a.seq > b.seq ? 1 : 0);
			}
		});
		PriorityQueue<Pending> ordered = new PriorityQueue<Pending>(due.size(), new Comparator<Pending>() {
			@Override
			public int compare(Pending a, Pending b) {
				if (a.dueTick != b.dueTick) {
					return a.dueTick < b.dueTick ? -1 : 1;
				}
				return a.seq < b.seq ? -1 : (a.seq > b.seq ? 1 : 0);
			}
		});
		ordered.addAll(due);
		while (!ordered.isEmpty()) {
			Pending p = ordered.poll();
			try {
				int executed = FunctionRuntime.runFunction(p.functionId, SERVER_SENDER);
				GapFixRuntimeLog.hit("schedule", "ScheduleRuntime", "fire", "ok",
						"function=" + p.functionId + " executed=" + executed
								+ " scheduled=" + p.scheduledTick + " fired=" + now);
			} catch (Throwable t) {
				GapFixRuntimeLog.error("schedule", "ScheduleRuntime", "fire", "fail",
						t.getClass().getSimpleName(),
						"function=" + p.functionId + " err=" + String.valueOf(t.getMessage()));
			}
		}
	}

	/** The vanilla server command source: level 2, at the world spawn. */
	private static final ICommandSender SERVER_SENDER = new ICommandSender() {
		@Override
		public String getName() {
			return "Server";
		}

		@Override
		public void addChatMessage(IChatComponent message) {
			// server console echo — routed to the game log like vanilla console output
			GapFixRuntimeLog.hit("schedule", "ScheduleRuntime", "server_output", "ok",
					String.valueOf(message.getUnformattedTextForChat()));
		}

		@Override
		public boolean canCommandSenderUseCommand(int permLevel, String commandName) {
			return permLevel <= 2; // vanilla scheduled functions run at server level
		}

		@Override
		public Vec3 getPositionVector() {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server != null && server.worldServers != null && server.worldServers.length > 0
						&& server.worldServers[0] != null) {
					net.minecraft.util.BlockPos spawn = server.worldServers[0].getSpawnPoint();
					return new Vec3(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D);
				}
			} catch (Throwable ignored) {
				// harness/no-server context — spawn position is only used by
				// position-taking subcommands; zero-vector is the honest fallback
			}
			return new Vec3(0.0D, 0.0D, 0.0D);
		}

		@Override
		public World getEntityWorld() {
			try {
				net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
				if (server != null && server.worldServers != null && server.worldServers.length > 0) {
					return server.worldServers[0];
				}
			} catch (Throwable ignored) {
				// no live server (harness) — commands fail honestly downstream
			}
			return null;
		}

		@Override
		public boolean sendCommandFeedback() {
			return true;
		}

	@Override
	public net.minecraft.entity.Entity getCommandSenderEntity() {
		return null; // the vanilla server source has no bound entity
	}

	@Override
	public net.minecraft.util.IChatComponent getDisplayName() {
		return new net.minecraft.util.ChatComponentText("Server");
	}

	@Override
	public net.minecraft.util.BlockPos getPosition() {
		try {
			net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
			if (server != null && server.worldServers != null && server.worldServers.length > 0
					&& server.worldServers[0] != null) {
				return server.worldServers[0].getSpawnPoint();
			}
		} catch (Throwable ignored) {
			// harness/no-server context — honest zero fallback
		}
		return net.minecraft.util.BlockPos.ORIGIN;
	}

	@Override
	public void setCommandStat(net.minecraft.command.CommandResultStats.Type type, int value) {
		// server source does not track command stats
	}
};

	/** Feedback helper for ScheduleCommandParity (vanilla message shape). */
	public static void sendScheduledFeedback(ICommandSender sender, String functionId, long dueInTicks) {
		sender.addChatMessage(new ChatComponentText(
				"Scheduled function '" + functionId + "' in " + dueInTicks + " ticks"));
	}

	/** Feedback helper for /schedule clear (vanilla message shape). */
	public static void sendClearedFeedback(ICommandSender sender, String functionId, int removed) {
		sender.addChatMessage(new ChatComponentText(
				"Removed " + removed + " scheduled function(s) for '" + functionId + "'"));
	}

	/** World-unload teardown (ModernRegistry.clearDynamicRegistrations cascade). */
	public static synchronized void clear() {
		if (!pendings.isEmpty()) {
			GapFixRuntimeLog.hit("schedule", "ScheduleRuntime", "clear", "ok",
					"cleared=" + pendingCount() + " pending task(s)");
		}
		pendings.clear();
	}
}
