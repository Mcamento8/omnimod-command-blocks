package net.lax1dude.eaglercraft.v1_8.forge.command;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;

/**
 * [Agent Note 2026-09-04] GENERAL: shared real-handler delegation seam for
 * the vanilla-syntax parity commands (the RunDelegate pattern proven by
 * ExecuteCommandParity / FunctionRuntime / TeamCommandParity, extracted so
 * every new parity command (setblock/fill/clone/effect/experience/…) shares
 * ONE code path instead of nine copies).
 *
 * Executes the translated command through the REAL 1.8
 * {@code ServerCommandManager} so validation, feedback messages, permission
 * checks and result statistics all stay 100% engine-real. A seam
 * ({@link #setDelegate}) lets the JVM harness substitute a recording
 * delegate — never a mock of engine behavior, only of transport.
 *
 * GENERAL — no mod/map/command hardcode. Doc-ID: MCBP-RUNSHARED-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class RunShared {

	/** Real delegation point (ExecuteCommandParity RunDelegate seam pattern). */
	public interface RunDelegate {
		int executeCommand(ICommandSender sender, String command);
	}

	private static volatile RunDelegate delegate;

	public static void setDelegate(RunDelegate d) {
		delegate = d;
	}

	private static final RunDelegate DEFAULT = new RunDelegate() {
		@Override
		public int executeCommand(ICommandSender sender, String command) {
			try {
				MinecraftServer server = MinecraftServer.getServer();
				if (server != null) {
					return server.getCommandManager().executeCommand(sender, command);
				}
				GapFixRuntimeLog.warn("commandblock", "RunShared", "run", "fail", "server_unavailable",
						"no live server — delegation dropped: " + command);
			} catch (Throwable t) {
				GapFixRuntimeLog.error("commandblock", "RunShared", "run", "fail", t.getClass().getSimpleName(),
						"cmd=" + command + " err=" + String.valueOf(t.getMessage()));
			}
			return 0;
		}
	};

	public static int execute(ICommandSender sender, String command) {
		RunDelegate d = delegate != null ? delegate : DEFAULT;
		return d.executeCommand(sender, command);
	}

	private RunShared() {
	}
}
