package net.minecraft.command;

import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;

/**+
 * This portion of EaglercraftX contains deobfuscated Minecraft 1.8 source code.
 * 
 * Minecraft 1.8.8 bytecode is (c) 2015 Mojang AB. "Do not distribute!"
 * Mod Coder Pack v9.18 deobfuscation configs are (c) Copyright by the MCP Team
 * 
 * EaglercraftX 1.8 patch files (c) 2022-2025 lax1dude, ayunami2000. All Rights Reserved.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class CommandXP extends CommandBase {

	public String getCommandName() {
		return "xp";
	}

	public int getRequiredPermissionLevel() {
		return 2;
	}

	public String getCommandUsage(ICommandSender var1) {
		return "commands.xp.usage";
	}

	public void processCommand(ICommandSender parICommandSender, String[] parArrayOfString) throws CommandException {
		// [Agent Note 2026-09-04] MCBP — modern /xp add|set|query (the 1.20.1
		// alias surface) forwarded to the /experience parity implementation.
		// 1.8 amounts are purely numeric, so the subcommand words are never
		// ambiguous. Doc-ID MCBP-EXP-001, GENERAL for every map.
		if (parArrayOfString.length > 0) {
			String modernSub = parArrayOfString[0].toLowerCase();
			if ("add".equals(modernSub) || "set".equals(modernSub) || "query".equals(modernSub)) {
				net.lax1dude.eaglercraft.v1_8.forge.command.ExperienceCommandParity
						.forward(parICommandSender, parArrayOfString);
				return;
			}
		}
		if (parArrayOfString.length <= 0) {
			throw new WrongUsageException("commands.xp.usage", new Object[0]);
		} else {
			String s = parArrayOfString[0];
			boolean flag = s.endsWith("l") || s.endsWith("L");
			if (flag && s.length() > 1) {
				s = s.substring(0, s.length() - 1);
			}

			int i = parseInt(s);
			boolean flag1 = i < 0;
			if (flag1) {
				i *= -1;
			}

			EntityPlayerMP entityplayermp = parArrayOfString.length > 1
					? getPlayer(parICommandSender, parArrayOfString[1])
					: getCommandSenderAsPlayer(parICommandSender);
			if (flag) {
				parICommandSender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, entityplayermp.experienceLevel);
				if (flag1) {
					entityplayermp.addExperienceLevel(-i);
					notifyOperators(parICommandSender, this, "commands.xp.success.negative.levels",
							new Object[] { Integer.valueOf(i), entityplayermp.getName() });
				} else {
					entityplayermp.addExperienceLevel(i);
					notifyOperators(parICommandSender, this, "commands.xp.success.levels",
							new Object[] { Integer.valueOf(i), entityplayermp.getName() });
				}
			} else {
				parICommandSender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, entityplayermp.experienceTotal);
				if (flag1) {
					throw new CommandException("commands.xp.failure.widthdrawXp", new Object[0]);
				}

				entityplayermp.addExperience(i);
				notifyOperators(parICommandSender, this, "commands.xp.success",
						new Object[] { Integer.valueOf(i), entityplayermp.getName() });
			}

		}
	}

	public List<String> addTabCompletionOptions(ICommandSender var1, String[] astring, BlockPos var3) {
		return astring.length == 2 ? getListOfStringsMatchingLastWord(astring, this.getAllUsernames()) : null;
	}

	protected String[] getAllUsernames() {
		return MinecraftServer.getServer().getAllUsernames();
	}

	public boolean isUsernameIndex(String[] var1, int i) {
		return i == 1;
	}
}