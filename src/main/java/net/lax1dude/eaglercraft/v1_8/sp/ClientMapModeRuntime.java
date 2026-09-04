package net.lax1dude.eaglercraft.v1_8.sp;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;

/**
 * OmniMod Map Builder — the CLIENT half of the Dual-Mode runtime
 * (DEV &#8646; PLAY/preview).
 *
 * <p>[Agent Note 2026-09-04] GENERAL: receives the tiny mode payload the
 * server pushes on {@link MapModeRuntime#CHANNEL} (routed here by
 * {@code NetHandlerPlayClient.handleCustomPayload} — the exact proven
 * transport of {@code OMNIMOD|BossBar}), mirrors the mode into
 * {@link MapModeRuntime#setClientPlay(boolean, long)}, and forces the
 * affected chunk meshes to rebuild so the visual flip is INSTANT and
 * complete:
 * <ul>
 *   <li><b>DEV &#8594; PLAY:</b> every loaded
 *       {@link TileEntityCommandBlock} position (plus its 6 neighbors —
 *       needed because the hidden block stops occluding neighbor faces)
 *       is marked for a render update, so command blocks vanish with no
 *       X-ray holes (the classic "invisible-but-culling" artifact).</li>
 *   <li><b>PLAY &#8594; DEV:</b> the same marks bring the command blocks
 *       and the culled faces back.</li>
 * </ul>
 *
 * <p>Idempotent by version (re-applying the same payload is a no-op) —
 * the join-time push, the switch broadcast and the periodic safety-net
 * rebroadcast can all arrive in any order safely. Parse failures keep the
 * previous visual state and are logged honestly (never silent, §18.2).
 *
 * <p>Thread note: runs on the CLIENT thread only (the custom-payload
 * handler thread). All state flips are volatile writes in
 * {@code MapModeRuntime} — safe from either side of the process boundary
 * (single-thread mode shares the JVM; worker mode keeps this isolate's
 * mirror in sync via packets only).
 *
 * <p>GENERAL — no map id, no mod id, no per-agent branching anywhere.
 *
 * Doc-ID: MAP-MODE-CLI-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class ClientMapModeRuntime {

	private static final String MOD_ID = "map_mode";

	/** Version of the last payload APPLIED (idempotence guard). */
	private static volatile long appliedVersion = 0L;

	/** True once any payload has been applied (join before/after guard). */
	private static volatile boolean everApplied = false;

	private ClientMapModeRuntime() {
	}

	/**
	 * Apply one mode payload (called from the client packet handler).
	 * Format/parse failures keep the previous state and log honestly.
	 *
	 * @return true when the payload was applied (new version).
	 */
	public static boolean applyJson(String json) {
		try {
			if (json == null || json.length() == 0) {
				GapFixRuntimeLog.warn(MOD_ID, "ClientMapModeRuntime", "apply", "fail",
						"empty payload", "");
				return false;
			}
			JSONObject root = new JSONObject(json);
			String fmt = root.optString("format", "");
			if (!MapModeRuntime.PAYLOAD_FORMAT.equals(fmt)) {
				GapFixRuntimeLog.warn(MOD_ID, "ClientMapModeRuntime", "apply", "fail",
						"unknown format: " + fmt, "len=" + json.length());
				return false;
			}
			long version = root.optLong("version", 0L);
			boolean play = MapModeRuntime.MODE_PLAY.equals(root.optString("mode", MapModeRuntime.MODE_DEV));
			if (everApplied && version == appliedVersion) {
				return false; // idempotent: same state already applied
			}
			MapModeRuntime.setClientPlay(play, version);
			appliedVersion = version;
			everApplied = true;
			int marked = resyncNow();
			GapFixRuntimeLog.hit(MOD_ID, "ClientMapModeRuntime", "apply", "ok",
					"mode=" + (play ? "play" : "dev") + " version=" + version
							+ " reRenderedBlocks=" + marked);
			return true;
		} catch (Throwable t) {
			GapFixRuntimeLog.warn(MOD_ID, "ClientMapModeRuntime", "apply", "fail",
					t.toString(), "");
			return false;
		}
	}

	/**
	 * Force chunk-mesh rebuild for every loaded command-block position plus
	 * its 6 neighbors (the neighbor marks matter because the block's
	 * opacity/face-culling changes with the mode). Safe to call at any
	 * moment (no world / no render global &#8594; no-op). Public so the
	 * join flow can call it directly after world set when needed.
	 *
	 * @return the number of block positions marked for re-render.
	 */
	public static int resyncNow() {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null || mc.renderGlobal == null) {
				return 0;
			}
			WorldClient world = mc.theWorld;
			if (world == null || world.loadedTileEntityList == null
					|| world.loadedTileEntityList.isEmpty()) {
				return 0;
			}
			List<BlockPos> targets = new ArrayList<BlockPos>();
			for (Object o : world.loadedTileEntityList.toArray()) {
				if (o instanceof TileEntityCommandBlock) {
					TileEntity te = (TileEntity) o;
					if (te == null || te.isInvalid()) {
						continue;
					}
					BlockPos pos = te.getPos();
					if (pos == null) {
						continue;
					}
					targets.add(pos);
					EnumFacing[] faces = EnumFacing.values();
					for (int i = 0; i < faces.length; ++i) {
						targets.add(pos.offset(faces[i]));
					}
				}
			}
			for (BlockPos pos : targets) {
				mc.renderGlobal.markBlockForUpdate(pos);
			}
			return targets.size();
		} catch (Throwable t) {
			GapFixRuntimeLog.warn(MOD_ID, "ClientMapModeRuntime", "resync", "fail",
					t.toString(), "");
			return 0;
		}
	}

	/**
	 * Reset the client mirror (called when the client leaves the world so a
	 * stale play flag never leaks into the next world/dev session before
	 * its first payload arrives).
	 */
	public static void reset() {
		appliedVersion = 0L;
		everApplied = false;
		MapModeRuntime.setClientPlay(false, 0L);
		GapFixRuntimeLog.hit(MOD_ID, "ClientMapModeRuntime", "reset", "ok", "");
	}

	/** Client-side play check (mirror of the server-authoritative flag). */
	public static boolean isPlayMode() {
		return MapModeRuntime.isClientPlay();
	}

	/** Payload builder mirror for tests/debugging (never used on wire). */
	static String debugState() {
		return "appliedVersion=" + appliedVersion + " everApplied=" + everApplied
				+ " play=" + MapModeRuntime.isClientPlay();
	}
}
