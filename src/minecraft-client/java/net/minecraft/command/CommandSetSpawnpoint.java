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
public class CommandSetSpawnpoint extends CommandBase {

	public String getCommandName() {
		return "spawnpoint";
	}

	public int getRequiredPermissionLevel() {
		return 2;
	}

	public String getCommandUsage(ICommandSender var1) {
		return "commands.spawnpoint.usage";
	}

	public void processCommand(ICommandSender parICommandSender, String[] parArrayOfString) throws CommandException {
		if (parArrayOfString.length > 1 && parArrayOfString.length < 4) {
			throw new WrongUsageException("commands.spawnpoint.usage", new Object[0]);
		} else {
			EntityPlayerMP entityplayermp = parArrayOfString.length > 0
					? getPlayer(parICommandSender, parArrayOfString[0])
					: getCommandSenderAsPlayer(parICommandSender);
			BlockPos blockpos = parArrayOfString.length > 3
					? parseBlockPos(parICommandSender, parArrayOfString, 1, true)
					: entityplayermp.getPosition();
			if (entityplayermp.worldObj != null) {
				entityplayermp.setSpawnPoint(blockpos, true);
				notifyOperators(parICommandSender, this, "commands.spawnpoint.success",
						new Object[] { entityplayermp.getName(), Integer.valueOf(blockpos.getX()),
								Integer.valueOf(blockpos.getY()), Integer.valueOf(blockpos.getZ()) });
			}

		}
	}

	public List<String> addTabCompletionOptions(ICommandSender var1, String[] astring, BlockPos blockpos) {
		return astring.length == 1
				? getListOfStringsMatchingLastWord(astring, MinecraftServer.getServer().getAllUsernames())
				: (astring.length > 1 && astring.length <= 4 ? func_175771_a(astring, 1, blockpos) : null);
	}

	public boolean isUsernameIndex(String[] var1, int i) {
		return i == 0;
	}
}