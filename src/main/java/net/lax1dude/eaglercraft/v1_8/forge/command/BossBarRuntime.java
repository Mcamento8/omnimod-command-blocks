package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.lax1dude.eaglercraft.v1_8.netty.Unpooled;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 CustomBossEvents runtime
 * backing the /bossbar command and {@code execute store … bossbar <id>}.
 *
 * Mirrors vanilla field defaults exactly (Minecraft Wiki /bossbar, fetched
 * 2026-08-29): color=white, max=100, name="Boss Bar", players=none,
 * style=progress, value=0, visible=true.
 *
 * WHY THIS EXISTS: OmniMod had NO bossbar data store (DynamicBossBar.java is
 * a 1.8 HUD adapter for dynamic boss ENTITIES, not the command runtime — see
 * tmp_command_bridge_harness/PHASE_1_AUDIT_P3.md G5).
 *
 * [Agent Note 2026-09-04] GENERAL — CLIENT HUD SYNC LAYER (GMF round): the
 * old honest boundary ("no HUD is drawn") is now closed for every map and
 * mod. Mutations call {@link #notifyChanged()}; the full state is broadcast
 * to every online player on the custom payload channel {@link #BOSSBAR_CHANNEL}
 * (the same proven transport as OMNIMOD|OpenScreen and the portal lifecycle
 * channel), and {@link #tickSync()} re-broadcasts periodically (default every
 * 100 server ticks ≈ 5s) so late-joining players converge without a login
 * hook. The client half is {@link ClientBossBarRuntime} (state + HUD render).
 *
 * HONEST BOUNDARY (§19.8, updated): integrated-server (singleplayer) targets
 * only — the broadcast rides the real player connection pipeline; there is
 * no dedicated-server fan-out in this engine. Bars are runtime state: they
 * are cleared on world unload (ModernRegistry cascade) and NOT persisted in
 * the level data — map authors re-create bars from their map's init batch or
 * {@code _dev/functions/load.mcfunction} (the vanilla datapack workflow).
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-BOSSBARRT-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class BossBarRuntime {

	/** Vanilla 1.20.1 color set (Minecraft Wiki /bossbar). */
	public static final String[] COLORS = { "pink", "blue", "red", "green", "yellow", "purple", "white" };

	/** Vanilla 1.20.1 style set (Minecraft Wiki /bossbar). */
	public static final String[] STYLES = { "progress", "notched_6", "notched_10", "notched_12", "notched_20" };

	/**
	 * Custom payload channel used to push the full boss bar state to clients.
	 * Payload: PacketBuffer.writeBytes(UTF-8 JSON) — read back with
	 * readStringFromBuffer (GMF-BOSSBAR-CLIENT-001 contract).
	 */
	public static final String BOSSBAR_CHANNEL = "OMNIMOD|BossBar";

	/** Payload format marker (single source of truth with the client half). */
	public static final String PAYLOAD_FORMAT = "omnimod-bossbar-1";

	/** Periodic re-broadcast cadence (server ticks) — late-joiner convergence. */
	public static final int PERIODIC_SYNC_TICKS = 100;

	/** One bossbar with the exact vanilla field set. */
	public static final class BossBar {
		public final String id;
		public String color = "white";
		public float max = 100.0F;
		public String name = "Boss Bar";
		public final Set<String> players = new LinkedHashSet<String>();
		public String style = "progress";
		public float value = 0.0F;
		public boolean visible = true;

		BossBar(String id) {
			this.id = id;
		}
	}

	private static final Map<String, BossBar> bars = new LinkedHashMap<String, BossBar>();

	/** Monotonic state version — bumped by every mutation, sent with payloads. */
	private static long versionCounter = 0L;

	/** Version of the last payload that was actually broadcast. */
	private static long lastBroadcastVersion = -1L;

	/** Server tick of the last periodic broadcast (late-joiner refresh). */
	private static long lastPeriodicTick = Long.MIN_VALUE;

	private BossBarRuntime() {
	}

	public static boolean isValidId(String id) {
		if (id == null || id.isEmpty()) {
			return false;
		}
		int colon = id.indexOf(':');
		if (colon <= 0 || colon >= id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
			return false;
		}
		return id.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-'
				|| c == '.' || c == '/');
	}

	/** Vanilla /bossbar add — creates with the exact vanilla defaults. */
	public static synchronized BossBar add(String id, String name) {
		BossBar bar = new BossBar(id);
		if (name != null && !name.isEmpty()) {
			bar.name = name;
		}
		bars.put(id, bar);
		return bar;
	}

	public static synchronized BossBar get(String id) {
		return bars.get(id);
	}

	public static synchronized boolean exists(String id) {
		return bars.containsKey(id);
	}

	public static synchronized List<BossBar> list() {
		return new ArrayList<BossBar>(bars.values());
	}

	/** Vanilla /bossbar remove — returns the removed bar (null when absent). */
	public static synchronized BossBar remove(String id) {
		return bars.remove(id);
	}

	/** World-unload teardown (ModernRegistry.clearDynamicRegistrations cascade). */
	public static synchronized void clear() {
		if (!bars.isEmpty()) {
			GapFixRuntimeLog.hit("bossbar", "BossBarRuntime", "clear", "ok",
					"cleared=" + bars.size() + " bossbar(s)");
		}
		bars.clear();
	}

	// ------------------------------------------------------------------
	// Client HUD sync (GMF round 2026-09-04)
	// ------------------------------------------------------------------

	/**
	 * Called after every successful mutation from the command layer. Bumps
	 * the state version and broadcasts the new state to all online players.
	 */
	public static synchronized void notifyChanged() {
		++versionCounter;
		broadcastNow("change");
	}

	/** Current state version (diagnostics / harness). */
	public static synchronized long stateVersion() {
		return versionCounter;
	}

	/**
	 * Serialize the full state as the client payload JSON (format marker,
	 * version, bars array). Pure — no server interaction, harness-testable.
	 */
	public static synchronized String toJsonPayload(long version) {
		net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject root =
				new net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject();
		root.put("format", PAYLOAD_FORMAT);
		root.put("version", version);
		net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONArray arr =
				new net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONArray();
		for (BossBar bar : bars.values()) {
			net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject o =
					new net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject();
			o.put("id", bar.id);
			o.put("name", bar.name == null ? "" : bar.name);
			o.put("color", bar.color);
			o.put("style", bar.style);
			o.put("value", (double) bar.value);
			o.put("max", (double) bar.max);
			o.put("visible", bar.visible);
			net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONArray pl =
					new net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONArray();
			for (String p : bar.players) {
				pl.put(p);
			}
			o.put("players", pl);
			arr.put(o);
		}
		root.put("bars", arr);
		return root.toString();
	}

	/**
	 * Broadcast the current state to every online player on
	 * {@link #BOSSBAR_CHANNEL}. Sends raw UTF-8 bytes in the packet buffer;
	 * the client reads them back as one string (readStringFromBuffer with the
	 * byte length prefix below). Failure of one player's send never aborts
	 * the others; every failure is logged with the original cause (§18.2).
	 */
	public static synchronized void broadcastNow(String trigger) {
		try {
			net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
			if (server == null || server.getConfigurationManager() == null) {
				return;
			}
			List<net.minecraft.entity.player.EntityPlayerMP> players =
					server.getConfigurationManager().func_181057_v();
			if (players.isEmpty()) {
				lastBroadcastVersion = versionCounter; // nothing to deliver; state is consistent
				return;
			}
			String payload = toJsonPayload(versionCounter);
			byte[] bytes = payload.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			int sent = 0;
			for (net.minecraft.entity.player.EntityPlayerMP p : players) {
				if (p == null) {
					continue;
				}
				try {
					net.minecraft.network.PacketBuffer buf =
							new net.minecraft.network.PacketBuffer(Unpooled.buffer());
					buf.writeBytes(bytes);
					if (p.playerNetServerHandler != null) {
						p.playerNetServerHandler.sendPacket(
								new net.minecraft.network.play.server.S3FPacketCustomPayload(BOSSBAR_CHANNEL, buf));
						++sent;
					}
				} catch (Throwable t) {
					GapFixRuntimeLog.warn("bossbar", "BossBarRuntime", "broadcast_player", "fail",
							t.toString(), "player=" + (p.getName() == null ? "?" : p.getName()));
				}
			}
			lastBroadcastVersion = versionCounter;
			if (sent > 0 || !bars.isEmpty()) {
				GapFixRuntimeLog.hit("bossbar", "BossBarRuntime", "broadcast", "ok",
						"trigger=" + trigger + " sent=" + sent + " bars=" + bars.size()
										+ " version=" + versionCounter + " bytes=" + bytes.length);
			}
		} catch (Throwable t) {
			GapFixRuntimeLog.warn("bossbar", "BossBarRuntime", "broadcast", "fail",
					t.toString(), String.valueOf(t.getMessage()));
		}
	}

	/**
	 * Per-server-tick sync driver (called from ForgeHooks.onServerTick END).
	 * Broadcasts immediately when the state is dirty, and re-broadcasts the
	 * full state every {@link #PERIODIC_SYNC_TICKS} ticks while bars exist so
	 * players joining mid-map converge without a dedicated login hook. Cheap
	 * when idle: one map-emptiness check per tick.
	 */
	public static synchronized void tickSync() {
		if (bars.isEmpty() && lastBroadcastVersion == versionCounter) {
			return;
		}
		boolean dirty = versionCounter != lastBroadcastVersion;
		try {
			net.minecraft.server.MinecraftServer server = net.minecraft.server.MinecraftServer.getServer();
			if (server == null) {
				return;
			}
			long now = server.getTickCounter();
			if (dirty || lastPeriodicTick == Long.MIN_VALUE || now - lastPeriodicTick >= PERIODIC_SYNC_TICKS) {
				lastPeriodicTick = now;
				broadcastNow(dirty ? "change" : "periodic");
			}
		} catch (Throwable t) {
			GapFixRuntimeLog.warn("bossbar", "BossBarRuntime", "tick_sync", "fail",
					t.toString(), String.valueOf(t.getMessage()));
		}
	}
}
