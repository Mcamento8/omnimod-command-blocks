package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONArray;
import net.lax1dude.eaglercraft.v1_8.json.repack.org.json.JSONObject;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiIngame;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.util.ResourceLocation;

/**
 * [Agent Note 2026-09-04] GENERAL: client-side half of the vanilla 1.20.1
 * custom boss bar HUD. The server half ({@link BossBarRuntime}) always had
 * the full CustomBossEvents data model; the documented honest boundary
 * (UCBPP-P3-BOSSBARRT-001) was "no HUD is drawn". This class closes that
 * boundary for EVERY map and EVERY mod that uses {@code /bossbar}:
 *
 * <ul>
 *   <li>the server broadcasts the full bar state on the custom payload
 *       channel {@code OMNIMOD|BossBar} (same proven transport as
 *       {@code OMNIMOD|OpenScreen} and the portal lifecycle channel);</li>
 *   <li>{@code NetHandlerPlayClient.handleCustomPayload} routes payloads
 *       here via {@link #applyJson(String)};</li>
 *   <li>{@code GuiIngame.renderBossHealth} draws the custom stack (vanilla
 *       1.9+ placement: rows start at y=12, 19px apart) and shifts the
 *       vanilla dragon/wither bar below the stack so both can coexist.</li>
 * </ul>
 *
 * Vanilla semantics kept exactly:
 * <ul>
 *   <li>a bar with an empty {@code players} list is visible to NO ONE;</li>
 *   <li>{@code visible=false} hides the bar for everyone;</li>
 *   <li>value/max drives the progress; max<=0 renders an empty bar
 *       (vanilla floors at max=1 at the command layer).</li>
 * </ul>
 *
 * Rendering notes (1.8 texture reality, stated honestly):
 * <ul>
 *   <li>the 1.8 icons.png boss-bar strip (v=74 background / v=79 fill) is
 *       single-colored, so the seven vanilla colors are applied as a tint
 *       on the fill row only — the background row stays untinted;</li>
 *   <li>{@code notched_6/10/12/20} styles draw the fill as segments with a
 *       1px gap (approximation of the vanilla notched texture);</li>
 *   <li>{@code progress} is the plain fill.</li>
 * </ul>
 *
 * GENERAL — no mod id, no map name, no command name, no hardcode. Works for
 * every map type: wave counters, boss health, countdown timers, mana bars…
 *
 * Doc-ID: GMF-BOSSBAR-CLIENT-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public final class ClientBossBarRuntime {

	/** Rows are 19px apart (10px name + 5px bar + 4px gap), vanilla 1.9+ layout. */
	public static final int ROW_HEIGHT = 19;

	/** Vanilla 1.8 boss bar strip geometry (GuiIngame.renderBossHealth). */
	private static final int BAR_WIDTH = 182;
	private static final int BAR_HEIGHT = 5;
	private static final int TEX_BG_V = 74;
	private static final int TEX_FILL_V = 79;
	private static final int TOP_Y = 12;

	/** One client-side bar (plain data — parsed from the sync payload). */
	public static final class ClientBar {
		public final String id;
		public String name = "Boss Bar";
		public String color = "white";
		public String style = "progress";
		public float value = 0.0F;
		public float max = 100.0F;
		public boolean visible = true;
		public final Set<String> players = new LinkedHashSet<String>();

		ClientBar(String id) {
			this.id = id;
		}
	}

	/** Client-side bar state (client render thread + netty handler thread both touch it). */
	private static final List<ClientBar> bars = new ArrayList<ClientBar>();
	private static long appliedVersion = Long.MIN_VALUE;
	private static volatile boolean lastParseOk = true;

	private ClientBossBarRuntime() {
	}

	/**
	 * Apply one full-state sync payload. Payload shape (written by
	 * {@code BossBarRuntime.toJsonPayload}):
	 * {@code {"format":"omnimod-bossbar-1","version":N,"bars":[{...}]}}.
	 * A payload with the same version as the last applied one is a no-op
	 * (idempotent periodic re-broadcasts). Parse failures keep the previous
	 * state and are logged honestly (never silent, §18.2).
	 *
	 * @return the number of bars in the new state (-1 when the payload was
	 *         rejected as stale/corrupt).
	 */
	public static synchronized int applyJson(String json) {
		if (json == null || json.isEmpty()) {
			lastParseOk = false;
			GapFixRuntimeLog.warn("bossbar_client", "ClientBossBarRuntime", "apply", "fail", "empty_payload", "");
			return -1;
		}
		try {
			JSONObject root = new JSONObject(json);
			if (!"omnimod-bossbar-1".equals(root.optString("format", ""))) {
				lastParseOk = false;
				GapFixRuntimeLog.warn("bossbar_client", "ClientBossBarRuntime", "apply", "fail",
						"bad_format", root.optString("format", ""));
				return -1;
			}
			long version = root.optLong("version", 0L);
			if (version == appliedVersion) {
				return bars.size(); // idempotent periodic re-broadcast
			}
			JSONArray arr = root.optJSONArray("bars");
			if (arr == null) {
				lastParseOk = false;
				GapFixRuntimeLog.warn("bossbar_client", "ClientBossBarRuntime", "apply", "fail",
						"missing_bars", "");
				return -1;
			}
			List<ClientBar> parsed = new ArrayList<ClientBar>(arr.length());
			for (int i = 0; i < arr.length(); ++i) {
				JSONObject o = arr.optJSONObject(i);
				if (o == null) {
					continue;
				}
				String id = o.optString("id", "");
				if (id.isEmpty()) {
					continue;
				}
				ClientBar bar = new ClientBar(id);
				bar.name = o.optString("name", "Boss Bar");
				bar.color = o.optString("color", "white");
				bar.style = o.optString("style", "progress");
				bar.value = (float) o.optDouble("value", 0.0D);
				bar.max = (float) o.optDouble("max", 100.0D);
				bar.visible = o.optBoolean("visible", true);
				JSONArray pl = o.optJSONArray("players");
				if (pl != null) {
					for (int k = 0; k < pl.length(); ++k) {
						String n = pl.optString(k, "");
						if (!n.isEmpty()) {
							bar.players.add(n);
						}
					}
				}
				parsed.add(bar);
			}
			bars.clear();
			bars.addAll(parsed);
			appliedVersion = version;
			lastParseOk = true;
			GapFixRuntimeLog.hit("bossbar_client", "ClientBossBarRuntime", "apply", "ok",
					"bars=" + bars.size() + " version=" + version);
			return bars.size();
		} catch (Throwable t) {
			lastParseOk = false;
			GapFixRuntimeLog.warn("bossbar_client", "ClientBossBarRuntime", "apply", "fail",
					t.toString(), String.valueOf(t.getMessage()));
			return -1;
		}
	}

	/** Diagnostic: was the last payload accepted? */
	public static boolean lastParseOk() {
		return lastParseOk;
	}

	/** Diagnostic: how many bars does the client hold? */
	public static synchronized int barCount() {
		return bars.size();
	}

	/** Diagnostic: the ids of held bars (stable copy). */
	public static synchronized List<String> barIds() {
		List<String> out = new ArrayList<String>(bars.size());
		for (ClientBar b : bars) {
			out.add(b.id);
		}
		return out;
	}

	/** Vanilla visibility rule: empty player list = visible to no one. */
	public static boolean isVisibleTo(ClientBar bar, String playerName) {
		return bar != null && bar.visible && !bar.players.isEmpty() && bar.players.contains(playerName);
	}

	// ------------------------------------------------------------------
	// Rendering (client render thread only, called from GuiIngame)
	// ------------------------------------------------------------------

	/**
	 * Draw the custom boss bar stack. Returns the number of rows drawn so the
	 * caller can push the vanilla dragon/wither bar below the stack.
	 */
	public static synchronized int renderBars(GuiIngame gui) {
		try {
			Minecraft mc = Minecraft.getMinecraft();
			if (mc == null || mc.thePlayer == null) {
				return 0;
			}
			String selfName = mc.thePlayer.getName();
			List<ClientBar> visible = new ArrayList<ClientBar>(bars.size());
			for (ClientBar b : bars) {
				if (isVisibleTo(b, selfName)) {
					visible.add(b);
				}
			}
			if (visible.isEmpty()) {
				return 0;
			}
			ScaledResolution res = mc.scaledResolution;
			int width = res.getScaledWidth();
			FontRenderer fr = gui.getFontRenderer();
			int x = width / 2 - BAR_WIDTH / 2;
			ResourceLocation icons = Gui.icons;
			for (int row = 0; row < visible.size(); ++row) {
				ClientBar b = visible.get(row);
				int y = TOP_Y + row * ROW_HEIGHT;
				// font rendering rebinds the texture atlas — bind per row
				mc.getTextureManager().bindTexture(icons);
				gui.drawTexturedModalRect(x, y, 0, TEX_BG_V, BAR_WIDTH, BAR_HEIGHT);
				float progress = b.max > 0.0F ? b.value / b.max : 0.0F;
				if (progress < 0.0F) {
					progress = 0.0F;
				} else if (progress > 1.0F) {
					progress = 1.0F;
				}
				int filled = (int) (progress * (float) BAR_WIDTH);
				if (filled > 0) {
					float[] rgb = colorRgb(b.color);
					GlStateManager.color(rgb[0], rgb[1], rgb[2], 1.0F);
					int segs = notches(b.style);
					if (segs <= 1) {
						gui.drawTexturedModalRect(x, y, 0, TEX_FILL_V, filled, BAR_HEIGHT);
					} else {
						int segWidth = BAR_WIDTH / segs;
						for (int s = 0; s < segs; ++s) {
							int segStart = s * segWidth;
							if (filled <= segStart) {
								break;
							}
							int segEnd = (s == segs - 1) ? BAR_WIDTH : (s + 1) * segWidth - 1;
							int drawW = Math.min(filled, segEnd) - segStart;
							if (drawW > 0) {
								gui.drawTexturedModalRect(x + segStart, y, 0, TEX_FILL_V, drawW, BAR_HEIGHT);
							}
						}
					}
					GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
				}
				String s = b.name == null ? "" : b.name;
				if (!s.isEmpty() && fr != null) {
					fr.drawStringWithShadow(s,
							(float) (width / 2 - fr.getStringWidth(s) / 2), (float) (y - 10), 16777215);
				}
			}
			GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
			mc.getTextureManager().bindTexture(icons);
			return visible.size();
		} catch (Throwable t) {
			// Render-layer failure must never crash the HUD loop (§18.2).
			GapFixRuntimeLog.warn("bossbar_client", "ClientBossBarRuntime", "render", "fail",
					t.toString(), String.valueOf(t.getMessage()));
			return 0;
		}
	}

	// ------------------------------------------------------------------
	// Pure helpers (harness-tested without a live GL context)
	// ------------------------------------------------------------------

	/** Vanilla 1.20.1 color set mapped to tint RGB (approximation on the 1.8 strip). */
	public static float[] colorRgb(String color) {
		if (color == null) {
			color = "white";
		}
		switch (color) {
		case "pink":
			return new float[] { 1.0F, 0.334F, 1.0F };
		case "blue":
			return new float[] { 0.334F, 0.334F, 1.0F };
		case "red":
			return new float[] { 1.0F, 0.334F, 0.334F };
		case "green":
			return new float[] { 0.334F, 1.0F, 0.334F };
		case "yellow":
			return new float[] { 1.0F, 1.0F, 0.334F };
		case "purple":
			return new float[] { 0.667F, 0.0F, 0.667F };
		case "white":
		default:
			return new float[] { 1.0F, 1.0F, 1.0F };
		}
	}

	/** Segment count for the vanilla styles (0/1 = plain progress). */
	public static int notches(String style) {
		if (style == null) {
			return 1;
		}
		switch (style) {
		case "notched_6":
			return 6;
		case "notched_10":
			return 10;
		case "notched_12":
			return 12;
		case "notched_20":
			return 20;
		case "progress":
		default:
			return 1;
		}
	}
}
