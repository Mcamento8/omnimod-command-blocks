package net.lax1dude.eaglercraft.v1_8.sp;

import java.nio.charset.StandardCharsets;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.netty.Unpooled;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject;
import net.lax1dude.eaglercraft.v1_8.sp.server.WorldsDB;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.server.MinecraftServer;

/**
 * OmniMod Map Builder — the Dual-Mode runtime (DEV &#8646; PLAY/preview).
 *
 * <p>[Agent Note 2026-09-04] GENERAL — the map-development system's two
 * working modes, for every map, every mod, zero hardcode:
 * <ul>
 *   <li><b>DEV mode (وضع التطوير)</b> — the developer and the external AI
 *       agents build the map: command blocks are visible, editable (GUI),
 *       breakable, and the whole {@code _dev/} workspace pipeline is
 *       live. This is the historical default and stays the default.</li>
 *   <li><b>PLAY mode (وضع المعاينة)</b> — the developer (or an agent) flips
 *       the map into its "as published" shape to TEST it: command blocks
 *       render fully invisible, cannot be targeted (no outline, no pick),
 *       cannot be opened and cannot be destroyed — while their LOGIC keeps
 *       running (repeating/chain/impulse all keep executing, redstone still
 *       arms them) exactly like a released public map.</li>
 * </ul>
 *
 * <p><b>Where the state lives:</b> the authoritative mode is a per-world
 * property persisted at {@code worlds/<map>/mapmode.json} (NOT inside
 * {@code _dev/} — the mode is a core map concept that must exist even when
 * the agent workspace was never enabled). File format:
 * <pre>
 * {"format":"omnimod-mapmode-1","mode":"dev"|"play",
 *  "setAt":&lt;epochMillis&gt;,"setBy":"&lt;who&gt;","map":"&lt;mapName&gt;"}
 * </pre>
 * A missing/corrupt file means DEV (safe default — never surprises a
 * developer with hidden blocks).
 *
 * <p><b>How the client learns the mode:</b> the server broadcasts a tiny
 * JSON payload on the proven custom payload channel
 * {@link #CHANNEL} — the same transport as {@code OMNIMOD|BossBar} and
 * {@code OMNIMOD|OpenScreen}. The client half is
 * {@link ClientMapModeRuntime} (NetHandlerPlayClient routes payloads
 * there). In worker-thread mode the client isolate and the server isolate
 * do NOT share statics, so the flag that matters for RENDERING is the
 * client-side mirror; the flag that matters for GUARDS is the
 * server-side one. {@link #isPlayModeAnywhere()} unions both so the shared
 * {@code BlockCommandBlock} class can gate from either side safely.
 *
 * <p><b>Wired from:</b> {@code EaglerMinecraftServer.startServer} (world
 * load), {@code EaglerMinecraftServer.updateTimeLightAndEntities} (tick
 * safety-net rebroadcast), {@code ServerConfigurationManager.initialize
 * ConnectionToPlayer} (join push), {@code CommandOmniDev} ({@code /omni_dev
 * mode dev|play}), {@code GuiMapDevFolder} (in-game buttons),
 * {@code AgentLinkRuntime} ({@code POST /omni/mapdev/mode} for external
 * agents), {@code BlockCommandBlock}/{@code CommandBlockLogic}/
 * {@code ItemInWorldManager}/{@code NetHandlerPlayServer} (the four
 * guards + rendering gates).
 *
 * <p>Honest boundaries (documented, §19.8 style):
 * <ul>
 *   <li>Command-block MINECARTS: their editor is guarded (same
 *       {@code tryOpenEditCommandBlock} choke point) but they still RENDER
 *       (entity rendering is a separate path — future work).</li>
 *   <li>Light: switching PLAY mid-session flips client mesh face-culling
 *       (command blocks stop occluding neighbors); vanilla light values are
 *       only recomputed on block updates, so no recalc fires on the switch
 *       itself. Block updates near a hidden command block in PLAY mode
 *       recalc light treating it as transparent — a subtle, documented
 *       visual difference in preview only.</li>
 *   <li>Ray traces (arrows, item frames' F5 camera...) pass through hidden
 *       command blocks in PLAY mode — consistent with what the player
 *       sees (thin air).</li>
 * </ul>
 *
 * <p>GENERAL — no map id, no mod id, no per-agent branching anywhere.
 *
 * Doc-ID: MAP-MODE-RT-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class MapModeRuntime {

        /** Custom payload channel (server &#8594; client mode sync). */
        public static final String CHANNEL = "OMNIMOD|MapMode";

        /** Payload format marker (single source of truth with the client half). */
        public static final String PAYLOAD_FORMAT = "omnimod-mapmode-1";

        /** Per-world persistence file (worlds/&lt;map&gt;/mapmode.json). */
        public static final String MODE_FILE = "mapmode.json";

        /** Development mode — command blocks visible/editable (default). */
        public static final String MODE_DEV = "dev";

        /** Preview mode — command blocks hidden + protected, map "as published". */
        public static final String MODE_PLAY = "play";

        /** Safety-net rebroadcast cadence (server ticks ≈ 5s). */
        static final int REBROADCAST_INTERVAL_TICKS = 100;

        private static final String MOD_ID = "map_mode";

        // ------------------------------------------------------------------
        // Cross-thread state (volatile — read from server AND client threads)
        // ------------------------------------------------------------------

        /** Server-authoritative flag (set on the server thread / server isolate). */
        private static volatile boolean serverPlay = false;

        /** Client mirror (set ONLY by the client custom-payload handler). */
        private static volatile boolean clientPlay = false;

        /** Monotonic mode version — bumped by every change, sent with payloads. */
        private static volatile long modeVersion = 1L;

        /** True while the current mode still needs a client rebroadcast. */
        private static volatile boolean broadcastDirty = true;

        /** Map whose mode is loaded (null before the first world load). */
        private static volatile String currentMapName = null;

        // Server-thread-only bookkeeping
        private static int tickCounter = 0;
        private static String setBy = "system";
        private static long setAt = 0L;

        private MapModeRuntime() {
        }

        // ------------------------------------------------------------------
        // Lifecycle hooks (wired in EaglerMinecraftServer)
        // ------------------------------------------------------------------

        /**
         * World-load hook: reload the persisted mode for the map that just
         * opened, flag a rebroadcast, and honestly tell the developer (blue
         * chat) when the map opens directly into preview mode.
         */
        public static void onWorldLoaded(MinecraftServer server) {
                try {
                        if (server == null || server.worldServers == null || server.worldServers.length == 0) {
                                return;
                        }
                        String map = server.getFolderName();
                        currentMapName = map;
                        String mode = loadModeFromFile(map);
                        boolean wantPlay = MODE_PLAY.equals(mode);
                        if (wantPlay != serverPlay) {
                                serverPlay = wantPlay;
                                ++modeVersion;
                                setBy = "load";
                                setAt = System.currentTimeMillis();
                        }
                        broadcastDirty = true;
                        if (wantPlay) {
                                MapDevSyncRuntime.sendBlueChat(server,
                                                "[MapMode] الماب محفوظ في وضع المعاينة — كتل الأوامر مخفية الآن"
                                                                + " (بدّل عبر /omni_dev mode dev)");
                        }
                        GapFixRuntimeLog.hit(MOD_ID, "MapModeRuntime", "on_world_loaded",
                                        "ok", "map=" + map + " mode=" + getMode());
                } catch (Throwable t) {
                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "on_world_loaded", "fail",
                                        t.toString(), "map=" + String.valueOf(currentMapName));
                }
        }

        /**
         * Server-tick hook: cheap cadenced safety net. If the mode was changed
         * (or the world just loaded) and nobody has received the payload yet
         * (e.g. the player was still joining), rebroadcast every ~5s until a
         * delivery succeeds. Free on every other tick.
         */
        public static void onServerTick(MinecraftServer server) {
                try {
                        ++tickCounter;
                        if (tickCounter % REBROADCAST_INTERVAL_TICKS != 0) {
                                return;
                        }
                        if (!broadcastDirty || server == null) {
                                return;
                        }
                        List<EntityPlayerMP> players = onlinePlayers(server);
                        if (players.isEmpty()) {
                                return; // nobody to deliver to yet — stay dirty
                        }
                        broadcast(server);
                        broadcastDirty = false;
                        GapFixRuntimeLog.hit(MOD_ID, "MapModeRuntime", "rebroadcast", "ok",
                                        "mode=" + getMode() + " players=" + players.size());
                } catch (Throwable t) {
                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "on_server_tick", "fail",
                                        t.toString(), "");
                }
        }

        // ------------------------------------------------------------------
        // Mode switching (server-authoritative)
        // ------------------------------------------------------------------

        /** Simple result record for the command/GUI/agent-link callers. */
        public static final class ModeResult {
                public final boolean ok;
                public final String message;

                ModeResult(boolean ok, String message) {
                        this.ok = ok;
                        this.message = message;
                }
        }

        /**
         * Switch the running map between DEV and PLAY. Persists the mode for
         * future loads, pushes the payload to every online player, posts the
         * blue confirmation, and logs the transition honestly.
         */
        public static ModeResult setMode(MinecraftServer server, String modeArg, String byWho) {
                if (server == null || server.worldServers == null || server.worldServers.length == 0) {
                        return new ModeResult(false, "[MapMode] لا يوجد عالم قيد التشغيل — افتح الماب أولاً");
                }
                String mode = normalizeMode(modeArg);
                if (mode == null) {
                        return new ModeResult(false, "[MapMode] وضع غير معروف: '" + modeArg
                                        + "' — المتاح: dev (تطوير) أو play/preview (معاينة)");
                }
                String map = server.getFolderName();
                currentMapName = map;
                boolean wantPlay = MODE_PLAY.equals(mode);
                if (wantPlay == serverPlay) {
                        return new ModeResult(true, "[MapMode] الوضع الحالي هو نفسه بالفعل: " + modeLabelAr()
                                        + " — لا تغيير");
                }
                serverPlay = wantPlay;
                ++modeVersion;
                setBy = byWho == null || byWho.length() == 0 ? "unknown" : byWho;
                setAt = System.currentTimeMillis();
                boolean saved = persist(map, mode, setBy, setAt);
                broadcast(server);
                broadcastDirty = false;
                String note = wantPlay
                                ? "[MapMode] تم تفعيل وضع المعاينة — كتل الأوامر مخفية الآن ولا يمكن فتحها أو تحطيمها، لكن منطقها يعمل"
                                                + (saved ? " (الوضع محفوظ مع الماب)" : " (تحذير: فشل حفظ الوضع — راجع world_runtime.log)")
                                : "[MapMode] تم تفعيل وضع التطوير — كتل الأوامر ظاهرة وقابلة للبرمجة والتعديل"
                                                + (saved ? " (الوضع محفوظ مع الماب)" : " (تحذير: فشل حفظ الوضع — راجع world_runtime.log)");
                MapDevSyncRuntime.sendBlueChat(server, note);
                GapFixRuntimeLog.hit(MOD_ID, "MapModeRuntime", "set_mode", "ok",
                                "map=" + map + " mode=" + mode + " by=" + setBy + " saved=" + saved);
                return new ModeResult(true, note);
        }

        /** Accepts the aliases the three front-ends (chat/GUI/agents) may send. */
        public static String normalizeMode(String raw) {
                if (raw == null) {
                        return null;
                }
                String s = raw.trim().toLowerCase();
                if (MODE_DEV.equals(s) || "development".equals(s) || "تطوير".equals(s)) {
                        return MODE_DEV;
                }
                if (MODE_PLAY.equals(s) || "preview".equals(s) || "معاينة".equals(s)) {
                        return MODE_PLAY;
                }
                return null;
        }

        // ------------------------------------------------------------------
        // Queries (usable from BOTH sides of the process boundary)
        // ------------------------------------------------------------------

        /** Current authoritative mode string ("dev" or "play"). */
        public static String getMode() {
                return serverPlay ? MODE_PLAY : MODE_DEV;
        }

        /** True when the server half is in preview mode (guards). */
        public static boolean isServerPlay() {
                return serverPlay;
        }

        /** True when the client half is in preview mode (rendering). */
        public static boolean isClientPlay() {
                return clientPlay;
        }

        /**
         * Union check — the one safe call from SHARED engine classes
         * (BlockCommandBlock) that execute on either side of the process
         * boundary: in worker mode each isolate only sees its own half, and in
         * single-thread mode both halves share this JVM.
         */
        public static boolean isPlayModeAnywhere() {
                return serverPlay || clientPlay;
        }

        /** Map whose mode is loaded (null before the first world load). */
        public static String currentMap() {
                return currentMapName;
        }

        /** Monotonic mode version (sent with every payload). */
        public static long version() {
                return modeVersion;
        }

        /**
         * Client-half setter — called ONLY by ClientMapModeRuntime when the
         * custom payload arrives (never from server code).
         */
        public static void setClientPlay(boolean play, long payloadVersion) {
                clientPlay = play;
        }

        /** Arabic label for status lines. */
        public static String modeLabelAr() {
                return serverPlay ? "معاينة (كتل الأوامر مخفية)" : "تطوير (كتل الأوامر ظاهرة)";
        }

        /** English label for status lines. */
        public static String modeLabelEn() {
                return serverPlay ? "PLAY (preview — command blocks hidden)" : "DEV (command blocks visible)";
        }

        // ------------------------------------------------------------------
        // Transport (server -> client)
        // ------------------------------------------------------------------

        /** Push the current mode to ONE player (join-time sync). */
        public static void pushToPlayer(EntityPlayerMP player) {
                if (player == null) {
                        return;
                }
                try {
                        byte[] bytes = toJsonPayload().getBytes(StandardCharsets.UTF_8);
                        net.minecraft.network.PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
                        buf.writeBytes(bytes);
                        if (player.playerNetServerHandler != null) {
                                player.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(CHANNEL, buf));
                        }
                        GapFixRuntimeLog.hit(MOD_ID, "MapModeRuntime", "push_to_player", "ok",
                                        "player=" + player.getName() + " mode=" + getMode() + " bytes=" + bytes.length);
                } catch (Throwable t) {
                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "push_to_player", "fail",
                                        t.toString(), "player=" + String.valueOf(player.getName()));
                }
        }

        /** Broadcast the mode payload to every online player. */
        public static void broadcast(MinecraftServer server) {
                try {
                        if (server == null || server.getConfigurationManager() == null) {
                                return;
                        }
                        List<EntityPlayerMP> players = onlinePlayers(server);
                        if (players.isEmpty()) {
                                broadcastDirty = true; // retry on a later tick
                                return;
                        }
                        byte[] bytes = toJsonPayload().getBytes(StandardCharsets.UTF_8);
                        int sent = 0;
                        for (EntityPlayerMP p : players) {
                                if (p == null) {
                                        continue;
                                }
                                try {
                                        net.minecraft.network.PacketBuffer buf = new PacketBuffer(Unpooled.buffer());
                                        buf.writeBytes(bytes);
                                        if (p.playerNetServerHandler != null) {
                                                p.playerNetServerHandler.sendPacket(new S3FPacketCustomPayload(CHANNEL, buf));
                                                ++sent;
                                        }
                                } catch (Throwable t) {
                                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "broadcast_player", "fail",
                                                        t.toString(), "player=" + (p.getName() == null ? "?" : p.getName()));
                                }
                        }
                        GapFixRuntimeLog.hit(MOD_ID, "MapModeRuntime", "broadcast", "ok",
                                        "mode=" + getMode() + " sent=" + sent + " bytes=" + bytes.length
                                                        + " version=" + modeVersion);
                } catch (Throwable t) {
                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "broadcast", "fail",
                                        t.toString(), "");
                }
        }

        private static List<EntityPlayerMP> onlinePlayers(MinecraftServer server) {
                try {
                        List<EntityPlayerMP> players = server.getConfigurationManager().func_181057_v();
                        return players == null ? java.util.Collections.<EntityPlayerMP>emptyList() : players;
                } catch (Throwable t) {
                        return java.util.Collections.emptyList();
                }
        }

        /** Full state as the client payload JSON (idempotent by version). */
        public static String toJsonPayload() {
                JSONObject root = new JSONObject();
                root.put("format", PAYLOAD_FORMAT);
                root.put("mode", getMode());
                root.put("version", modeVersion);
                if (currentMapName != null) {
                        root.put("map", currentMapName);
                }
                if (setBy != null) {
                        root.put("setBy", setBy);
                }
                root.put("setAt", setAt);
                return root.toString();
        }

        // ------------------------------------------------------------------
        // Persistence (worlds/<map>/mapmode.json — VFS, world-scoped)
        // ------------------------------------------------------------------

        static boolean persist(String mapName, String mode, String by, long at) {
                if (mapName == null || mapName.length() == 0) {
                        return false;
                }
                try {
                        VFile2 f = WorldsDB.newVFile("worlds", mapName, MODE_FILE);
                        if (f == null) {
                                return false;
                        }
                        JSONObject root = new JSONObject();
                        root.put("format", PAYLOAD_FORMAT);
                        root.put("mode", mode);
                        root.put("setAt", at);
                        root.put("setBy", by == null ? "unknown" : by);
                        root.put("map", mapName);
                        f.setAllBytes(root.toString().getBytes(StandardCharsets.UTF_8));
                        return true;
                } catch (Throwable t) {
                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "persist", "fail",
                                        t.toString(), "map=" + String.valueOf(mapName));
                        return false;
                }
        }

        /** Returns "dev"/"play", or null when no file/corrupt (caller defaults to dev). */
        static String loadModeFromFile(String mapName) {
                if (mapName == null || mapName.length() == 0) {
                        return null;
                }
                try {
                        VFile2 f = WorldsDB.newVFile("worlds", mapName, MODE_FILE);
                        if (f == null || !f.exists()) {
                                return null;
                        }
                        byte[] b = f.getAllBytes();
                        if (b == null || b.length == 0) {
                                return null;
                        }
                        JSONObject root = new JSONObject(new String(b, StandardCharsets.UTF_8));
                        String fmt = root.optString("format", "");
                        if (!PAYLOAD_FORMAT.equals(fmt)) {
                                GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "load", "fail",
                                                "unknown format: " + fmt, "map=" + mapName);
                                return null;
                        }
                        String mode = root.optString("mode", MODE_DEV);
                        return MODE_PLAY.equals(mode) ? MODE_PLAY : MODE_DEV;
                } catch (Throwable t) {
                        GapFixRuntimeLog.warn(MOD_ID, "MapModeRuntime", "load", "fail",
                                        t.toString(), "map=" + mapName);
                        return null;
                }
        }

        // ------------------------------------------------------------------
        // Status (used by /omni_dev, the GUI and the agent link)
        // ------------------------------------------------------------------

        /** One-line Arabic status ("?" before the first world load). */
        public static String statusLineAr() {
                if (currentMapName == null) {
                        return "?";
                }
                return "الوضع: " + modeLabelAr();
        }
}
