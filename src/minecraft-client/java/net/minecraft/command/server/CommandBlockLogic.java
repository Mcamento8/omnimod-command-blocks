package net.minecraft.command.server;

import net.lax1dude.eaglercraft.v1_8.netty.ByteBuf;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Callable;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.lax1dude.eaglercraft.v1_8.forge.command.CommandBlockModernRuntime;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommandManager;
import net.minecraft.command.ICommandSender;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ReportedException;
import net.minecraft.world.World;

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
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
 * OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public abstract class CommandBlockLogic implements ICommandSender {
	private static final SimpleDateFormat timestampFormat = new SimpleDateFormat("HH:mm:ss");
	private int successCount;
	private boolean trackOutput = true;
	private IChatComponent lastOutput = null;
	private String commandStored = "";
	private String customName = "@";
	private final CommandResultStats resultStats = new CommandResultStats();

	/**
	 * [Agent Note 2026-09-04] MCBP: vanilla 1.20.1 command-block MODE fields.
	 * The 1.8 engine has one impulse command block; every 1.9+ map relies on
	 * Repeating / Chain / Conditional / Always-Active. These fields carry the
	 * 1.20.1 semantics on the 1.8 tile; the scheduling/triggering engine is
	 * {@link CommandBlockModernRuntime} (GENERAL — no map/mod hardcode).
	 *
	 * NBT keys equal vanilla where vanilla has them (auto, conditionMet,
	 * LastExecution, UpdateLastExecution); Mode/Facing/Conditional are tile
	 * NBT because the 1.8 command block has no facing/conditional blockstate
	 * properties (documented §19.8 honest boundary, Doc-ID MCBP-RUNTIME-001).
	 */
	private String mode = CommandBlockModernRuntime.MODE_IMPULSE;
	private boolean auto = false; // vanilla: chain defaults true (see readDataFromNBT)
	private boolean conditional = false;
	private boolean conditionMet = true; // vanilla: "True if not a conditional block"
	private long lastExecution = -1L;
	private boolean updateLastExecution = true;
	private EnumFacing facing = EnumFacing.NORTH; // vanilla blockstate default

	public int getSuccessCount() {
		return this.successCount;
	}

	/** [MCBP] vanilla 1.20.1 mode (impulse|chain|repeating). */
	public String getMode() {
		return this.mode;
	}

	public void setMode(String newMode) {
		if (newMode == null) {
			newMode = CommandBlockModernRuntime.MODE_IMPULSE;
		}
		newMode = newMode.toLowerCase();
		if (!CommandBlockModernRuntime.MODE_IMPULSE.equals(newMode)
				&& !CommandBlockModernRuntime.MODE_CHAIN.equals(newMode)
				&& !CommandBlockModernRuntime.MODE_REPEATING.equals(newMode)) {
			newMode = CommandBlockModernRuntime.MODE_IMPULSE;
		}
		this.mode = newMode;
	}

	/** [MCBP] Always Active (vanilla {@code auto} NBT key). */
	public boolean isAuto() {
		return this.auto;
	}

	public void setAuto(boolean auto) {
		this.auto = auto;
	}

	/** [MCBP] Conditional mode (vanilla blockstate, stored in tile NBT here). */
	public boolean isConditional() {
		return this.conditional;
	}

	public void setConditional(boolean conditional) {
		this.conditional = conditional;
		if (!conditional) {
			this.conditionMet = true;
		}
	}

	/** [MCBP] vanilla {@code conditionMet} NBT key. */
	public boolean isConditionMet() {
		return this.conditionMet;
	}

	public void setConditionMet(boolean met) {
		this.conditionMet = met;
	}

	/** [MCBP] vanilla {@code LastExecution} NBT key (chain dedup per tick). */
	public long getLastExecution() {
		return this.lastExecution;
	}

	public void setLastExecution(long tick) {
		this.lastExecution = tick;
	}

	/** [MCBP] vanilla {@code UpdateLastExecution} NBT key. */
	public boolean isUpdateLastExecution() {
		return this.updateLastExecution;
	}

	public void setUpdateLastExecution(boolean update) {
		this.updateLastExecution = update;
	}

	/** [MCBP] facing of the command block (vanilla blockstate, tile NBT here). */
	public EnumFacing getFacing() {
		return this.facing;
	}

	public void setFacing(EnumFacing newFacing) {
		this.facing = newFacing == null ? EnumFacing.NORTH : newFacing;
	}

	public IChatComponent getLastOutput() {
		return this.lastOutput;
	}

	public void writeDataToNBT(NBTTagCompound tagCompound) {
		tagCompound.setString("Command", this.commandStored);
		tagCompound.setInteger("SuccessCount", this.successCount);
		tagCompound.setString("CustomName", this.customName);
		tagCompound.setBoolean("TrackOutput", this.trackOutput);
		if (this.lastOutput != null && this.trackOutput) {
			tagCompound.setString("LastOutput", IChatComponent.Serializer.componentToJson(this.lastOutput));
		}

		this.resultStats.writeStatsToNBT(tagCompound);
		// [MCBP] vanilla 1.20.1 command-block NBT keys.
		tagCompound.setString("Mode", this.mode);
		tagCompound.setBoolean("auto", this.auto);
		tagCompound.setBoolean("Conditional", this.conditional);
		tagCompound.setBoolean("conditionMet", this.conditionMet);
		tagCompound.setLong("LastExecution", this.lastExecution);
		tagCompound.setBoolean("UpdateLastExecution", this.updateLastExecution);
		tagCompound.setString("Facing", this.facing == null ? "north" : this.facing.getName());
	}

	public void readDataFromNBT(NBTTagCompound nbt) {
		this.commandStored = nbt.getString("Command");
		this.successCount = nbt.getInteger("SuccessCount");
		if (nbt.hasKey("CustomName", 8)) {
			this.customName = nbt.getString("CustomName");
		}

		if (nbt.hasKey("TrackOutput", 1)) {
			this.trackOutput = nbt.getBoolean("TrackOutput");
		}

		if (nbt.hasKey("LastOutput", 8) && this.trackOutput) {
			this.lastOutput = IChatComponent.Serializer.jsonToComponent(nbt.getString("LastOutput"));
		}

		this.resultStats.readStatsFromNBT(nbt);
		// [MCBP] vanilla 1.20.1 command-block NBT keys. NOTE (deliberate):
		// loading NBT never auto-fires an impulse block — vanilla activates
		// on block UPDATE, not on world load; the place-and-fire path is
		// SetBlockCommandParity / the GUI apply handler.
		if (nbt.hasKey("Mode", 8)) {
			setMode(nbt.getString("Mode"));
		} else {
			setMode(CommandBlockModernRuntime.MODE_IMPULSE);
		}
		if (nbt.hasKey("auto", 1)) {
			this.auto = nbt.getBoolean("auto");
		} else {
			this.auto = CommandBlockModernRuntime.defaultAutoForMode(this.mode);
		}
		if (nbt.hasKey("Conditional", 1)) {
			this.conditional = nbt.getBoolean("Conditional");
		} else {
			this.conditional = false;
		}
		if (nbt.hasKey("conditionMet", 1)) {
			this.conditionMet = nbt.getBoolean("conditionMet");
		} else {
			this.conditionMet = true;
		}
		if (nbt.hasKey("LastExecution", 4)) {
			this.lastExecution = nbt.getLong("LastExecution");
		} else {
			this.lastExecution = -1L;
		}
		if (nbt.hasKey("UpdateLastExecution", 1)) {
			this.updateLastExecution = nbt.getBoolean("UpdateLastExecution");
		} else {
			this.updateLastExecution = true;
		}
		if (nbt.hasKey("Facing", 8)) {
			EnumFacing f = EnumFacing.byName(nbt.getString("Facing"));
			setFacing(f);
		} else {
			setFacing(EnumFacing.NORTH);
		}
	}

	public boolean canCommandSenderUseCommand(int i, String var2) {
		return i <= 2;
	}

	public void setCommand(String command) {
		String old = this.commandStored;
		this.commandStored = command;
		this.successCount = 0;
		// [MCBP] 1.12+ semantics: an Always-Active IMPULSE command block fires
		// once when its command is updated (the GUI "Done" path). Repeating
		// blocks pick the new command up on their next world tick; chain
		// blocks wait for the next trigger — both vanilla behavior.
		if (this.auto && CommandBlockModernRuntime.MODE_IMPULSE.equals(this.mode)
				&& command != null && !command.equals(old) && !command.trim().isEmpty()
				&& this.getEntityWorld() != null && !this.getEntityWorld().isRemote) {
			MinecraftServer server = MinecraftServer.getServer();
			if (server != null && server.isCommandBlockEnabled()) {
				try {
					this.trigger(this.getEntityWorld());
				} catch (Throwable t) {
					GapFixRuntimeLog.hit("commandblock", "CommandBlockLogic", "auto_fire", "fail",
							"cmd=" + command + " err=" + String.valueOf(t.getMessage()));
				}
			}
		}
	}

	public String getCommand() {
		return this.commandStored;
	}

	public void trigger(World worldIn) {
		if (worldIn.isRemote) {
			this.successCount = 0;
			return;
		}

		MinecraftServer minecraftserver = MinecraftServer.getServer();
		if (minecraftserver != null && minecraftserver.isCommandBlockEnabled()) {
			ICommandManager icommandmanager = minecraftserver.getCommandManager();

			try {
				this.lastOutput = null;
				this.successCount = icommandmanager.executeCommand(this, this.commandStored);
			} catch (Throwable throwable) {
				CrashReport crashreport = CrashReport.makeCrashReport(throwable, "Executing command block");
				CrashReportCategory crashreportcategory = crashreport.makeCategory("Command to be executed");
				crashreportcategory.addCrashSectionCallable("Command", new Callable<String>() {
					public String call() throws Exception {
						return CommandBlockLogic.this.getCommand();
					}
				});
				crashreportcategory.addCrashSectionCallable("Name", new Callable<String>() {
					public String call() throws Exception {
						return CommandBlockLogic.this.getName();
					}
				});
				throw new ReportedException(crashreport);
			} finally {
				// [MCBP] vanilla "Trigger and chaining": after ANY command block
				// executes (success OR failure), the block it FACES gets
				// triggered. Chain blocks react; other modes ignore triggers.
				// GENERAL — every map, no hardcode (Doc-ID MCBP-RUNTIME-001).
				try {
					CommandBlockModernRuntime.onExecuted(worldIn, this.getPosition(), this);
				} catch (Throwable t) {
					GapFixRuntimeLog.hit("commandblock", "CommandBlockLogic", "chain_propagate", "fail",
							"err=" + String.valueOf(t.getMessage()));
				}
			}
		} else {
			this.successCount = 0;
		}

	}

	public String getName() {
		return this.customName;
	}

	public IChatComponent getDisplayName() {
		return new ChatComponentText(this.getName());
	}

	public void setName(String parString1) {
		this.customName = parString1;
	}

	public void addChatMessage(IChatComponent ichatcomponent) {
		if (this.trackOutput && this.getEntityWorld() != null && !this.getEntityWorld().isRemote) {
			this.lastOutput = (new ChatComponentText("[" + timestampFormat.format(new Date()) + "] "))
					.appendSibling(ichatcomponent);
			this.updateCommand();
		}

	}

	public boolean sendCommandFeedback() {
		MinecraftServer minecraftserver = MinecraftServer.getServer();
		return minecraftserver == null
				|| minecraftserver.worldServers[0].getGameRules().getBoolean("commandBlockOutput");
	}

	public void setCommandStat(CommandResultStats.Type commandresultstats$type, int i) {
		this.resultStats.func_179672_a(this, commandresultstats$type, i);
	}

	public abstract void updateCommand();

	public abstract int func_145751_f();

	public abstract void func_145757_a(ByteBuf var1);

	public void setLastOutput(IChatComponent lastOutputMessage) {
		this.lastOutput = lastOutputMessage;
	}

	public void setTrackOutput(boolean shouldTrackOutput) {
		this.trackOutput = shouldTrackOutput;
	}

	public boolean shouldTrackOutput() {
		return this.trackOutput;
	}

	public boolean tryOpenEditCommandBlock(EntityPlayer playerIn) {
		// [Agent Note 2026-09-04] MAP-MODE (MAP-MODE-EDT-001) — editor
		// guard: in PLAY (preview) mode the command-block GUI must never
		// open — the map behaves exactly as published. One choke point
		// covers tile AND minecart command blocks, both sides. GENERAL.
		if (net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.isPlayModeAnywhere()) {
			return false;
		}
		if (!playerIn.capabilities.isCreativeMode) {
			return false;
		} else {
			if (playerIn.getEntityWorld().isRemote) {
				playerIn.openEditCommandBlock(this);
			}

			return true;
		}
	}

	public CommandResultStats getCommandResultStats() {
		return this.resultStats;
	}

	/** Position of the command block (tile or minecart) — used by chaining. */
	public BlockPos getCommandBlockPosition() {
		return this.getPosition();
	}
}
