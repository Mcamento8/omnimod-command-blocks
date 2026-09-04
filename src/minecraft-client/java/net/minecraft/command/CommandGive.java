package net.minecraft.command;

import java.util.List;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
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
public class CommandGive extends CommandBase {

	public String getCommandName() {
		return "give";
	}

	public int getRequiredPermissionLevel() {
		return 2;
	}

	public String getCommandUsage(ICommandSender var1) {
		return "commands.give.usage";
	}

	public void processCommand(ICommandSender parICommandSender, String[] parArrayOfString) throws CommandException {
		// [Agent Note 2026-09-04] MCBP — vanilla 1.20.1 /give SYNTAX parity
		// (dual-syntax, in-place translation): (a) modern command-block item ids
		// (repeating/chain_command_block) alias onto the real command_block item;
		// (b) modern item{NBT} inline components fold into the legacy trailing
		// datatag argument. Legacy arg order untouched — regression anchor.
		// Doc-ID MCBP-GIVE-001, GENERAL for every map/mod.
		if (parArrayOfString.length >= 2 && parArrayOfString[1] != null) {
			String token = parArrayOfString[1];
			String alias = net.lax1dude.eaglercraft.v1_8.forge.command.CommandBlockModernRuntime
					.giveAliasFor(token);
			if (alias == null && token.indexOf('{') >= 0) {
				int brace = token.indexOf('{');
				String idPart = token.substring(0, brace).trim();
				String nbtPart = token.substring(brace).trim();
				alias = net.lax1dude.eaglercraft.v1_8.forge.command.CommandBlockModernRuntime
						.giveAliasFor(idPart);
				String realId = alias != null ? alias : idPart;
				java.util.List<String> rebuilt = new java.util.ArrayList<String>();
				rebuilt.add(parArrayOfString[0]);
				rebuilt.add(realId);
				for (int gi = 2; gi < parArrayOfString.length; ++gi) {
					rebuilt.add(parArrayOfString[gi]);
				}
				// legacy give: <player> <item> [count] [data] [nbt]
				while (rebuilt.size() < 4) {
					rebuilt.add("1");
				}
				if (rebuilt.size() == 4) {
					rebuilt.add("0");
				}
				rebuilt.add(nbtPart);
				parArrayOfString = rebuilt.toArray(new String[rebuilt.size()]);
			} else if (alias != null) {
				parArrayOfString = parArrayOfString.clone();
				parArrayOfString[1] = alias;
			}
		}
		if (parArrayOfString.length < 2) {
			throw new WrongUsageException("commands.give.usage", new Object[0]);
		} else {
			EntityPlayerMP entityplayermp = getPlayer(parICommandSender, parArrayOfString[0]);
			Item item = getItemByText(parICommandSender, parArrayOfString[1]);
			int i = parArrayOfString.length >= 3 ? parseInt(parArrayOfString[2], 1, 64) : 1;
			int j = parArrayOfString.length >= 4 ? parseInt(parArrayOfString[3]) : 0;
			ItemStack itemstack = new ItemStack(item, i, j);
			if (parArrayOfString.length >= 5) {
				String s = getChatComponentFromNthArg(parICommandSender, parArrayOfString, 4).getUnformattedText();

				try {
					itemstack.setTagCompound(JsonToNBT.getTagFromJson(s));
				} catch (NBTException nbtexception) {
					throw new CommandException("commands.give.tagError", new Object[] { nbtexception.getMessage() });
				}
			}

			boolean flag = entityplayermp.inventory.addItemStackToInventory(itemstack);
			if (flag) {
				entityplayermp.worldObj.playSoundAtEntity(entityplayermp, "random.pop", 0.2F,
						((entityplayermp.getRNG().nextFloat() - entityplayermp.getRNG().nextFloat()) * 0.7F + 1.0F)
								* 2.0F);
				entityplayermp.inventoryContainer.detectAndSendChanges();
			}

			if (flag && itemstack.stackSize <= 0) {
				itemstack.stackSize = 1;
				parICommandSender.setCommandStat(CommandResultStats.Type.AFFECTED_ITEMS, i);
				EntityItem entityitem1 = entityplayermp.dropPlayerItemWithRandomChoice(itemstack, false);
				if (entityitem1 != null) {
					entityitem1.func_174870_v();
				}
			} else {
				parICommandSender.setCommandStat(CommandResultStats.Type.AFFECTED_ITEMS, i - itemstack.stackSize);
				EntityItem entityitem = entityplayermp.dropPlayerItemWithRandomChoice(itemstack, false);
				if (entityitem != null) {
					entityitem.setNoPickupDelay();
					entityitem.setOwner(entityplayermp.getName());
				}
			}

			notifyOperators(parICommandSender, this, "commands.give.success",
					new Object[] { itemstack.getChatComponent(), Integer.valueOf(i), entityplayermp.getName() });
		}
	}

	public List<String> addTabCompletionOptions(ICommandSender var1, String[] astring, BlockPos var3) {
		return astring.length == 1 ? getListOfStringsMatchingLastWord(astring, this.getPlayers())
				: (astring.length == 2 ? getListOfStringsMatchingLastWord(astring, Item.itemRegistry.getKeys()) : null);
	}

	protected String[] getPlayers() {
		return MinecraftServer.getServer().getAllUsernames();
	}

	public boolean isUsernameIndex(String[] var1, int i) {
		return i == 0;
	}
}