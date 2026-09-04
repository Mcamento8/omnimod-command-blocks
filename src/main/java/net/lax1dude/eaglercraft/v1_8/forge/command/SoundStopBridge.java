package net.lax1dude.eaglercraft.v1_8.forge.command;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.lax1dude.eaglercraft.v1_8.netty.Unpooled;

/**
 * [Agent Note 2026-09-04] GENERAL: transport for the vanilla 1.20.1
 * {@code /stopsound} command — server → client payload on the proven
 * OMNIMOD custom channel (same transport as OMNIMOD|BossBar and
 * OMNIMOD|OpenScreen), then a REAL filtered stop in the client sound
 * engine. Every map, every mod, zero hardcode.
 *
 * SERVER side: {@link #stopSounds} writes a tiny UTF-8 JSON payload
 * {"source": "...", "sound": "..."} (wildcard "*") to the target player.
 * CLIENT side: {@link #handleClientJson} (called from the
 * NetHandlerPlayClient OMNIMOD|StopSound branch) resolves the real
 * {@code SoundHandler} and performs the filtered stop through the real
 * {@code EaglercraftSoundManager} active/queued sound lists.
 *
 * The 1.8 protocol has NO stop-sound packet (added 1.9.3) — this channel
 * is the engine-honest bridge (documented §19.8: only affects the local
 * client's sound engine, exactly like vanilla).
 *
 * Doc-ID: MCBP-STOPSOUND-002
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class SoundStopBridge {

	/** Server → client stop-sound channel (OMNIMOD transport family). */
	public static final String STOP_CHANNEL = "OMNIMOD|StopSound";

	private SoundStopBridge() {
	}

	/**
	 * SERVER side: send a filtered stop to one player. {@code source} and
	 * {@code sound} accept "*" (all). Returns true when the packet was sent.
	 */
	public static boolean stopSounds(EntityPlayerMP player, String source, String sound) {
		if (player == null || player.playerNetServerHandler == null) {
			return false;
		}
		try {
			String json = payloadJson(source == null ? "*" : source, sound == null ? "*" : sound);
			byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
			PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
			buf.writeBytes(bytes);
			player.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(STOP_CHANNEL, buf));
			return true;
		} catch (Throwable t) {
			GapFixRuntimeLog.warn("stopsound", "SoundStopBridge", "send", "fail", t.getClass().getSimpleName(),
					"player=" + (player.getName() == null ? "?" : player.getName())
							+ " err=" + String.valueOf(t.getMessage()));
			return false;
		}
	}

	/**
	 * CLIENT side: apply a stop-sound payload JSON. Called from the
	 * NetHandlerPlayClient OMNIMOD|StopSound branch.
	 */
	public static void handleClientJson(String json) {
		try {
			String source = "*";
			String sound = "*";
			net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject obj =
					new net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject(json);
			if (obj.has("source")) {
				source = String.valueOf(obj.get("source"));
			}
			if (obj.has("sound")) {
				sound = String.valueOf(obj.get("sound"));
			}
			net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getMinecraft();
			if (mc != null && mc.getSoundHandler() != null) {
				mc.getSoundHandler().stopSoundsFiltered(source, sound);
			}
			GapFixRuntimeLog.hit("stopsound", "SoundStopBridge", "client_apply", "ok",
					"source=" + source + " sound=" + sound);
		} catch (Throwable t) {
			GapFixRuntimeLog.warn("stopsound", "SoundStopBridge", "client_apply", "fail",
					t.getClass().getSimpleName(), String.valueOf(t.getMessage()));
		}
	}

	private static String payloadJson(String source, String sound) {
		// minimal hand-built JSON (no user content — fixed enum/vocab inputs)
		return "{\"source\":\"" + safe(source) + "\",\"sound\":\"" + safe(sound) + "\"}";
	}

	private static String safe(String s) {
		return s == null ? "*" : s.replace("\"", "").replace("\\", "");
	}
}
