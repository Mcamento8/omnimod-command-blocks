package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /teammsg} (+alias
 * {@code /tm}) parity — REAL 1.8 scoreboard teams underneath, every map,
 * every mod, zero hardcode.
 *
 * WHAT WAS BROKEN: /teammsg does not exist (1.14+ command). Team-based maps
 * use it for team coordination messages (factions, PvP arenas, co-op
 * objectives). The 1.8 engine has REAL scoreboard teams — only the command
 * surface was missing.
 *
 * SEMANTICS (vanilla): the message goes to every member of the SENDER's
 * team (including the sender) with a team-formatted prefix. Players with
 * no team fail with the vanilla-style error. Message visibility stays
 * team-only — never broadcast.
 *
 * Doc-ID: MCBP-TEAMMSG-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class TeammsgCommandParity implements ICommand {

        @Override
        public String getCommandName() {
                return "teammsg";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
                return "/teammsg <message...>";
        }

        @Override
        public List<String> getCommandAliases() {
                List<String> aliases = new ArrayList<String>();
                aliases.add("tm");
                return aliases;
        }

        public int getRequiredPermissionLevel() {
                return 0; // vanilla 1.20.1: every player (level 0)
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
                return sender.canCommandSenderUseCommand(0, "teammsg");
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
                if (args == null || args.length == 0) {
                        throw new CommandException("Expected: teammsg <message...>");
                }
                EntityPlayerMP self = requirePlayer(sender);
                StringBuilder msg = new StringBuilder();
                for (String a : args) {
                        if (msg.length() > 0) {
                                msg.append(' ');
                        }
                        msg.append(a);
                }
                ScorePlayerTeam team = getTeamOf(self);
                if (team == null) {
                        throw new CommandException("Team message failed: " + self.getName() + " is not on any team");
                }
                IChatComponent formatted = buildMessage(self, team, msg.toString());
                int delivered = 0;
                for (String member : team.getMembershipCollection()) {
                        EntityPlayerMP target = findPlayerByName(member);
                        if (target != null) {
                                target.addChatMessage(formatted);
                                if (!target.getName().equals(self.getName())) {
                                        ++delivered;
                                }
                        }
                }
                self.addChatMessage(formatted);
                GapFixRuntimeLog.hit("teammsg", "TeammsgCommandParity", "send", "ok",
                                "team=" + team.getRegisteredName() + " from=" + self.getName() + " delivered=" + delivered);
        }

        private static EntityPlayerMP requirePlayer(ICommandSender sender) throws CommandException {
                if (sender instanceof EntityPlayerMP) {
                        return (EntityPlayerMP) sender;
                }
                // console/command-block senders have no team — vanilla fails too
                throw new CommandException("Team message failed: sender is not a player");
        }

        private static ScorePlayerTeam getTeamOf(EntityPlayerMP player) {
                try {
                        MinecraftServer server = MinecraftServer.getServer();
                        if (server == null) {
                                return null;
                        }
                        return server.worldServers[0].getScoreboard().getPlayersTeam(player.getName());
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("teammsg", "TeammsgCommandParity", "team_lookup", "fail",
                                        "err=" + String.valueOf(t.getMessage()));
                        return null;
                }
        }

        private static EntityPlayerMP findPlayerByName(String name) {
                try {
                        MinecraftServer server = MinecraftServer.getServer();
                        if (server != null) {
                                return server.getConfigurationManager().getPlayerByUsername(name);
                        }
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("teammsg", "TeammsgCommandParity", "player_lookup", "fail",
                                        "name=" + name + " err=" + String.valueOf(t.getMessage()));
                }
                return null;
        }

        private static IChatComponent buildMessage(EntityPlayerMP self, ScorePlayerTeam team, String message) {
                // vanilla look: <player> whispers to <team>: <message> — team-colored
                try {
                        ChatComponentText base = new ChatComponentText("");
                        ChatComponentText who = new ChatComponentText(self.getName());
                        who.getChatStyle().setColor(team.getChatFormat());
                        base.appendSibling(who);
                        base.appendSibling(new ChatComponentText(" whispers to " + team.getRegisteredName() + ": "));
                        base.appendSibling(new ChatComponentText(message));
                        return base;
                } catch (Throwable t) {
                        return new ChatComponentText(self.getName() + " whispers to " + team.getRegisteredName() + ": " + message);
                }
        }

        // ------------------------------------------------------------------
        // ICommand plumbing
        // ------------------------------------------------------------------

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
                return Collections.emptyList();
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
                return false;
        }

        @Override
        public int compareTo(ICommand other) {
                return this.getCommandName().compareTo(other.getCommandName());
        }
}
