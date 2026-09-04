package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.PlayerNotFoundException;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /experience} and
 * modern {@code /xp} subcommand syntax — REAL 1.8 player-experience engine
 * underneath (EntityPlayerMP.addExperience / addExperienceLevel), every
 * map, every mod, zero hardcode.
 *
 * WHAT WAS BROKEN: 1.8 has ONLY {@code /xp <amount>[L] [player]} — no
 * subcommand surface. Every 1.13+ map writes
 * {@code /experience add|set|query <targets> <amount> [levels|points]}
 * (and {@code /xp add ...} via the modern alias) — parse-fails on 1.8.
 * Registered as the NEW command name {@code experience} (1.8 never
 * registered that name) + CommandXP forwards modern subcommand forms here.
 *
 * SEMANTICS (vanilla 1.20.1 → real 1.8 engine):
 * <pre>
 *  experience add <targets> <n> [levels|points] → addExperienceLevel(n) /
 *      addExperience(n) — the REAL 1.8 leveling math.
 *  experience set <targets> <n> levels         → addExperienceLevel(n - level)
 *  experience set <targets> <n> points         → addExperience(n - total)
 *      (approximation: vanilla "points" set vs 1.8 total-points engine —
 *      documented §19.8 boundary; levels-set is exact)
 *  experience query <targets> levels|points    → level / total feedback
 *      + CommandResultStats.QUERY_RESULT (same channel 1.8 /xp used)
 * </pre>
 *
 * Doc-ID: MCBP-EXP-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class ExperienceCommandParity implements ICommand {

        @Override
        public String getCommandName() {
                return "experience";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
                return "/experience add|set|query <targets> <amount> [levels|points]";
        }

        @Override
        public List<String> getCommandAliases() {
                return Collections.emptyList();
        }

        public int getRequiredPermissionLevel() {
                return 2; // vanilla 1.20.1 level
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
                return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), "experience");
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
                try {
                        execute(sender, args);
                } catch (IllegalArgumentException e) {
                        GapFixRuntimeLog.hit("experience", "ExperienceCommandParity", "parse", "syntax_fail",
                                        "reason=" + e.getMessage() + " args=" + Arrays.toString(args));
                        throw new CommandException(String.valueOf(e.getMessage()));
                }
        }

        /** Entry used by the modern-/xp forward in CommandXP (args keep sub). */
        public static void forward(ICommandSender sender, String[] args) throws CommandException {
                try {
                        execute(sender, args);
                } catch (IllegalArgumentException e) {
                        throw new CommandException(String.valueOf(e.getMessage()));
                }
        }

        private static void execute(ICommandSender sender, String[] args) throws CommandException {
                if (args == null || args.length == 0) {
                        throw new IllegalArgumentException("Expected: experience add|set|query <targets> <amount> [levels|points]");
                }
                String sub = args[0].toLowerCase();
                if ("add".equals(sub) || "set".equals(sub)) {
                        if (args.length < 3) {
                                throw new IllegalArgumentException("Expected: experience " + sub + " <targets> <amount> [levels|points]");
                        }
                        List<EntityPlayerMP> players = resolveTargets(sender, args[1]);
                        if (players.isEmpty()) {
                                throw new PlayerNotFoundException("commands.generic.player.notFound");
                        }
                        int amount;
                        try {
                                amount = Integer.parseInt(args[2]);
                        } catch (NumberFormatException e) {
                                throw new IllegalArgumentException("Invalid amount '" + args[2] + "' (expected an integer)");
                        }
                        boolean levels = args.length >= 4 && "levels".equalsIgnoreCase(args[3]);
                        // default unit = points (vanilla)
                        int changed = 0;
                        for (EntityPlayerMP p : players) {
                                if ("add".equals(sub)) {
                                        if (levels) {
                                                p.addExperienceLevel(amount);
                                        } else {
                                                if (amount < 0) {
                                                        // 1.8 engine path rejects withdrawing raw points —
                                                        // honest vanilla-1.8 behavior preserved
                                                        throw new IllegalArgumentException("Cannot withdraw experience points (use levels)");
                                                }
                                                p.addExperience(amount);
                                        }
                                } else { // set
                                        if (levels) {
                                                p.addExperienceLevel(amount - p.experienceLevel);
                                        } else {
                                                int delta = amount - p.experienceTotal;
                                                if (delta >= 0) {
                                                        p.addExperience(delta);
                                                } else {
                                                        // Real-API approximation for lowering total points:
                                                        // reset to level 0 with the real level API, then
                                                        // rebuild the requested points with the real point
                                                        // API (documented §19.8 boundary: vanilla "points"
                                                        // set targets the current-level bar, the 1.8
                                                        // engine only exposes total-points math).
                                                        p.addExperienceLevel(-p.experienceLevel);
                                                        if (amount > 0) {
                                                                p.addExperience(amount);
                                                        }
                                                }
                                        }
                                }
                                ++changed;
                        }
                        sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, changed);
                        sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, changed);
                        feedback(sender, "Gave " + changed + " player(s) " + (levels ? "levels" : "points") + " (" + sub + " " + amount + ")");
                        GapFixRuntimeLog.hit("experience", "ExperienceCommandParity", sub, "ok",
                                        "targets=" + changed + " amount=" + amount + " unit=" + (levels ? "levels" : "points"));
                        return;
                }
                if ("query".equals(sub)) {
                        if (args.length < 2) {
                                throw new IllegalArgumentException("Expected: experience query <targets> levels|points");
                        }
                        List<EntityPlayerMP> players = resolveTargets(sender, args[1]);
                        if (players.isEmpty()) {
                                throw new PlayerNotFoundException("commands.generic.player.notFound");
                        }
                        boolean levels = args.length >= 3 && "levels".equalsIgnoreCase(args[2]);
                        EntityPlayerMP first = players.get(0);
                        int value = levels ? first.experienceLevel : first.experienceTotal;
                        sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, value);
                        feedback(sender, first.getName() + " has " + value + " " + (levels ? "levels" : "points"));
                        GapFixRuntimeLog.hit("experience", "ExperienceCommandParity", "query", "ok",
                                        "target=" + first.getName() + " value=" + value);
                        return;
                }
                throw new IllegalArgumentException("Expected add, set or query, got: '" + sub + "'");
        }

        private static List<EntityPlayerMP> resolveTargets(ICommandSender sender, String token) {
                List<EntityPlayerMP> out = new ArrayList<EntityPlayerMP>();
                try {
                        if (token.startsWith("@")) {
                                out.addAll(new EntitySelector(token).findPlayers(sender));
                        } else {
                                EntityPlayerMP p = new EntitySelector(token).findPlayer(sender);
                                if (p != null) {
                                        out.add(p);
                                }
                        }
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("experience", "ExperienceCommandParity", "resolve", "fail",
                                        "token=" + token + " err=" + String.valueOf(t.getMessage()));
                }
                return out;
        }

        private static void feedback(ICommandSender sender, String message) {
                try {
                        sender.addChatMessage(new ChatComponentText(message));
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("experience", "ExperienceCommandParity", "feedback", "fail",
                                        "err=" + String.valueOf(t.getMessage()));
                }
        }

        // ------------------------------------------------------------------
        // ICommand plumbing
        // ------------------------------------------------------------------

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
                List<String> out = new ArrayList<String>();
                if (args.length == 1) {
                        match(out, args[0], Arrays.asList("add", "set", "query"));
                } else if (args.length == 4 || (args.length == 3 && "query".equals(args[0]))) {
                        match(out, args[args.length - 1], Arrays.asList("levels", "points"));
                }
                return out;
        }

        private static void match(List<String> out, String token, List<String> options) {
                String t = token == null ? "" : token.toLowerCase();
                for (String o : options) {
                        if (o.startsWith(t)) {
                                out.add(o);
                        }
                }
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
                return index == 1;
        }

        @Override
        public int compareTo(ICommand other) {
                return this.getCommandName().compareTo(other.getCommandName());
        }
}
