package net.minecraft.command;

import java.util.ArrayList;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.sp.MapDevSyncRuntime;
import net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime;
import net.lax1dude.eaglercraft.v1_8.sp.MapDevWorkspace;
import net.lax1dude.eaglercraft.v1_8.sp.MapDevWorkspaceDocs;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

/**
 * OmniMod Map Builder — Track A · Layer C (Agent Dev Bridge control command).
 *
 * <p>Unified control surface for the per-map agent development workspace
 * ({@code worlds/<map>/_dev/}). Registered once in {@code ServerCommandManager}
 * so it is reachable from chat, command blocks and external agent tooling
 * alike (UNIFIED_COMMAND_SYSTEM: one kernel, many front-ends).
 *
 * <pre>
 * /omni_dev                 — status (blue chat)
 * /omni_dev mode            — current map mode (dev / play-preview)
 * /omni_dev mode dev        — switch to DEV mode (command blocks visible+editable)
 * /omni_dev mode play       — switch to PREVIEW mode (command blocks hidden+protected,
 *                             map behaves exactly as published; logic keeps running)
 * /omni_dev enable          — create/enable the workspace for the running map
 * /omni_dev apply           — force an immediate apply pass over _dev/build/
 * /omni_dev linkset &lt;uri&gt; [root] — point the workspace at a platform folder link
 * /omni_dev where           — platform-specific folder location info
 * /omni_dev help            — the agent contract as chat lines
 * </pre>
 *
 * <p>All replies are BLUE (the project-wide MapDev confirmation color) and every
 * action is logged through {@code GapFixRuntimeLog} by the runtime layer.
 *
 * Doc-ID: MAP-BUILDER-C3
 */
public class CommandOmniDev extends CommandBase {

	public String getCommandName() {
		return "omni_dev";
	}

	public int getRequiredPermissionLevel() {
		return 0;
	}

	public String getCommandUsage(ICommandSender sender) {
		return "/omni_dev <status|mode|enable|apply|where|help> | /omni_dev mode <dev|play>"
				+ " | /omni_dev linkset <uri> [root]";
	}

	public void processCommand(ICommandSender sender, String[] args) throws CommandException {
		MinecraftServer server = MinecraftServer.getServer();
		if (server == null || server.worldServers == null || server.worldServers.length == 0) {
			blue(sender, "[MapDev] لا يوجد عالم قيد التشغيل — افتح الماب أولاً");
			return;
		}
		String sub = args.length == 0 ? "status" : args[0].toLowerCase();
		// [Agent Note 2026-09-04] MAP-MODE (MAP-MODE-CMD-001) — the
		// dual-mode switch: /omni_dev mode (query) | mode dev | mode
		// play|preview. One unified kernel path for chat, command
		// blocks, the GUI screen and external agents (the AgentLink
		// POST /omni/mapdev/mode endpoint reuses the same runtime).
		// GENERAL for every map/mod — zero map-id branching.
		if (sub.equals("mode")) {
			if (args.length < 2) {
				blue(sender, "[MapMode] الوضع الحالي: " + MapModeRuntime.modeLabelAr()
						+ " — الاستخدام: /omni_dev mode <dev|play|preview>");
			} else {
				MapModeRuntime.ModeResult r = MapModeRuntime.setMode(server, args[1], "command");
				blue(sender, r.message);
			}
			return;
		}
		if (sub.equals("status")) {
			String line = MapDevSyncRuntime.statusLine();
			if (line == null) {
				blue(sender, "[MapDev] الحالة: لا عالم نشط");
			} else {
				blue(sender, "[MapDev] الحالة: " + line
						+ " — مجلد الوكيل: worlds/" + server.getFolderName() + "/" + MapDevWorkspace.DEV_DIR);
			}
		} else if (sub.equals("enable")) {
			String map = server.getFolderName();
			boolean ok = MapDevSyncRuntime.ensureWorkspace(map);
			if (ok) {
				blue(sender, "[MapDev] تم إنشاء/تمكين مجلد تطوير الماب: worlds/" + map + "/"
						+ MapDevWorkspace.DEV_DIR + " — ضع ملفات ops-*.json داخل build/ وسيتم تطبيقها تلقائياً");
			} else {
				blue(sender, "[MapDev] فشل إنشاء مجلد التطوير — راجع world_runtime.log");
			}
		} else if (sub.equals("apply")) {
			MapDevSyncRuntime.refreshLinkedFolder(server);
			MapDevSyncRuntime.ApplySummary s = MapDevSyncRuntime.drainPending(server, Integer.MAX_VALUE, "command");
			if (s.batchesApplied == 0) {
				blue(sender, "[MapDev] لا تغييرات جديدة — الماب محدّث");
			} else {
				blue(sender, "[MapDev] تم تطبيق تغييرات على الماب: " + s.batchesApplied + " دفعة ("
						+ s.opsApplied + " عملية، " + s.blocksPlaced + " كتلة"
						+ (s.failedOps > 0 ? "، " + s.failedOps + " فشل)" : ")"));
			}
		} else if (sub.equals("where")) {
			MapDevSyncRuntime.sendWhereInfo(server);
			blue(sender, "[MapDev] ملف البداية للوكيل: worlds/" + server.getFolderName() + "/"
					+ MapDevWorkspace.DEV_DIR + "/" + MapDevWorkspaceDocs.START_HERE_FILE);
		} else if (sub.equals("linkset")) {
			if (args.length < 2 || args[1].length() < 8) {
				blue(sender, "[MapDev] الاستخدام: /omni_dev linkset <folderUri> [rootName]");
				return;
			}
			String root = args.length >= 3 ? args[2] : "";
			if (MapDevSyncRuntime.setLinkedFolder(args[1], root)) {
				int n = MapDevSyncRuntime.refreshLinkedFolder(server);
				MapDevSyncRuntime.ApplySummary s = MapDevSyncRuntime.drainPending(server, Integer.MAX_VALUE,
						"linkset");
				blue(sender, "[MapDev] تم ربط مجلد التطوير الخارجي (ملفات=" + n + ") — "
						+ s.batchesApplied + " دفعة جديدة مُطبقة");
			} else {
				blue(sender, "[MapDev] فشل حفظ رابط المجلد (URI غير صالح أو لا عالم نشط)");
			}
		} else if (sub.equals("help")) {
			blue(sender, "[MapDev] عقد مجلد تطوير الماب: worlds/" + server.getFolderName() + "/"
					+ MapDevWorkspace.DEV_DIR + "/build/ — ملف JSON بصيغة omnimod-dev-1 يحتوي ops[]");
			blue(sender, "[MapDev] ops: place_block | fill_area | replace_area | set_spawn | chat | command");
			blue(sender, "[MapDev] التطبيق تلقائي: عند تحميل الماب وكل ثانيتين أثناء اللعب + رسالة زرقاء هنا");
			blue(sender, "[MapDev] راجع README.md داخل مجلد التطوير للأمثلة الكاملة");
			blue(sender, "[MapDev] لوكلاء الذكاء الاصطناعي: ابدأ من "
					+ MapDevWorkspaceDocs.START_HERE_FILE + " ثم مجلد "
					+ MapDevWorkspaceDocs.AGENT_SUBDIR + "/ (القواعد، الإحداثيات، أسماء الكتل، الاختبارات)");
			blue(sender, "[MapDev] ملفات الماب الحيّة: " + MapDevWorkspaceDocs.OVERVIEW_FILE + " · "
					+ MapDevWorkspaceDocs.CHANGELOG_FILE + " · " + MapDevWorkspaceDocs.FILEMAP_FILE
					+ " — يملكها الوكيل ويجب تحديثها بعد كل تعديل");
		} else {
			blue(sender, "[MapDev] أمر غير معروف: " + sub + " — " + getCommandUsage(sender));
		}
	}

	private static void blue(ICommandSender sender, String text) {
		ChatComponentText c = new ChatComponentText(text);
		c.getChatStyle().setColor(EnumChatFormatting.BLUE);
		sender.addChatMessage(c);
	}

	public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
		if (args.length == 1) {
			List<String> opts = new ArrayList<>();
			String[] subs = { "status", "mode", "enable", "apply", "where", "help",
					"linkset", "dev", "play", "preview" };
			for (String s : subs) {
				if (s.startsWith(args[0].toLowerCase())) {
					opts.add(s);
				}
			}
			return opts;
		}
		return null;
	}
}
