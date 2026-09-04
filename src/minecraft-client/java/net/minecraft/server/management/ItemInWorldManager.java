package net.minecraft.server.management;

import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.ILockableContainer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;

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
public class ItemInWorldManager {
	public World theWorld;
	public EntityPlayerMP thisPlayerMP;
	private WorldSettings.GameType gameType = WorldSettings.GameType.NOT_SET;
	private boolean isDestroyingBlock;
	private int initialDamage;
	private BlockPos field_180240_f = BlockPos.ORIGIN;
	private int curblockDamage;
	private boolean receivedFinishDiggingPacket;
	private BlockPos field_180241_i = BlockPos.ORIGIN;
	private int initialBlockDamage;
	private int durabilityRemainingOnBlock = -1;
	private EnumFacing currentBreakFace = EnumFacing.DOWN;

	public ItemInWorldManager(World worldIn) {
		this.theWorld = worldIn;
	}

	public void setGameType(WorldSettings.GameType type) {
		this.gameType = type;
		type.configurePlayerCapabilities(this.thisPlayerMP.capabilities);
		this.thisPlayerMP.sendPlayerAbilities();
		this.thisPlayerMP.mcServer.getConfigurationManager().sendPacketToAllPlayers(new S38PacketPlayerListItem(
				S38PacketPlayerListItem.Action.UPDATE_GAME_MODE, new EntityPlayerMP[] { this.thisPlayerMP }));
	}

	public WorldSettings.GameType getGameType() {
		return this.gameType;
	}

	public boolean survivalOrAdventure() {
		return this.gameType.isSurvivalOrAdventure();
	}

	public boolean isCreative() {
		return this.gameType.isCreative();
	}

	public void initializeGameType(WorldSettings.GameType type) {
		if (this.gameType == WorldSettings.GameType.NOT_SET) {
			this.gameType = type;
		}

		this.setGameType(this.gameType);
	}

	public void updateBlockRemoving() {
		++this.curblockDamage;
		if (this.receivedFinishDiggingPacket) {
			int i = this.curblockDamage - this.initialBlockDamage;
			Block block = this.theWorld.getBlockState(this.field_180241_i).getBlock();
			if (block.getMaterial() == Material.air) {
				this.receivedFinishDiggingPacket = false;
			} else {
				float f = block.getPlayerRelativeBlockHardness(this.thisPlayerMP, this.thisPlayerMP.worldObj,
						this.field_180241_i) * (float) (i + 1);
				int j = (int) (f * 10.0F);
				if (j != this.durabilityRemainingOnBlock) {
					this.theWorld.sendBlockBreakProgress(this.thisPlayerMP.getEntityId(), this.field_180241_i, j);
					this.durabilityRemainingOnBlock = j;
				}

				if (f >= 1.0F) {
					this.receivedFinishDiggingPacket = false;
					this.tryHarvestBlock(this.field_180241_i);
				}
			}
		} else if (this.isDestroyingBlock) {
			Block block1 = this.theWorld.getBlockState(this.field_180240_f).getBlock();
			if (block1.getMaterial() == Material.air) {
				this.theWorld.sendBlockBreakProgress(this.thisPlayerMP.getEntityId(), this.field_180240_f, -1);
				this.durabilityRemainingOnBlock = -1;
				this.isDestroyingBlock = false;
			} else {
				int k = this.curblockDamage - this.initialDamage;
				float f1 = block1.getPlayerRelativeBlockHardness(this.thisPlayerMP, this.thisPlayerMP.worldObj,
						this.field_180241_i) * (float) (k + 1);
				int l = (int) (f1 * 10.0F);
				if (l != this.durabilityRemainingOnBlock) {
					this.theWorld.sendBlockBreakProgress(this.thisPlayerMP.getEntityId(), this.field_180240_f, l);
					this.durabilityRemainingOnBlock = l;
				}
			}
		}

	}

	public void onBlockClicked(BlockPos blockpos, EnumFacing enumfacing) {
		// [Agent Note]: GENERAL Forge 1.20.1 compat — fire PlayerInteractEvent.LeftClickBlock so
		// ANY mod can intercept the left-click on a block and cancel the default break
		// (e.g. FallingTree, VeinMiner, OreExcavation, protection/anti-grief plugins).
		// This hook is intentionally NOT tree/block-type specific — there is NO block-type check.
		// Fired server-side only (the embedded server is server-authoritative), which also
		// transparently covers touch/mobile clients and prevents client/server desync.
		// Fired here — the single official dig entry (START_DESTROY_BLOCK -> onBlockClicked) —
		// before ANY harvest/progress logic, so a cancel keeps the ORIGINAL block intact and
		// no break progress ever starts. Creative instant-break and survival slow-break both
		// flow through this method, so one hook covers every game mode (see notes below).
		// fail-open: if a mod listener throws, we MUST NOT cancel digging.
		boolean leftClickCanceled;
		try {
			leftClickCanceled = net.lax1dude.eaglercraft.v1_8.forge.ForgeHooks
					.onPlayerLeftClickBlock(this.thisPlayerMP, blockpos, enumfacing);
		} catch (Throwable modListenerError) {
			leftClickCanceled = false;
		}
		if (leftClickCanceled) {
			// Cancel any in-progress break crack animation and re-sync the REAL block state to
			// the client so it does not visually vanish then pop back (Ghost/desync). This is the
			// same S23PacketBlockChange re-sync NetHandlerPlayServer uses for protected blocks.
			this.cancelDestroyingBlock();
			this.thisPlayerMP.playerNetServerHandler.sendPacket(new S23PacketBlockChange(this.theWorld, blockpos));
			return;
		}

		this.currentBreakFace = enumfacing != null ? enumfacing : EnumFacing.DOWN;
		if (this.isCreative()) {
			if (!this.theWorld.extinguishFire((EntityPlayer) null, blockpos, enumfacing)) {
				this.tryHarvestBlock(blockpos);
			}

		} else {
			Block block = this.theWorld.getBlockState(blockpos).getBlock();
			if (this.gameType.isAdventure()) {
				if (this.gameType == WorldSettings.GameType.SPECTATOR) {
					return;
				}

				if (!this.thisPlayerMP.isAllowEdit()) {
					ItemStack itemstack = this.thisPlayerMP.getCurrentEquippedItem();
					if (itemstack == null) {
						return;
					}

					if (!itemstack.canDestroy(block)) {
						return;
					}
				}
			}

			this.theWorld.extinguishFire((EntityPlayer) null, blockpos, enumfacing);
			this.initialDamage = this.curblockDamage;
			float f = 1.0F;
			if (block.getMaterial() != Material.air) {
				ItemStack itemstack = this.thisPlayerMP.getCurrentEquippedItem();
				if (itemstack != null && itemstack.getItem().onBlockStartBreak(itemstack, blockpos, this.thisPlayerMP)) {
					return;
				}
				block.onBlockClicked(this.theWorld, blockpos, this.thisPlayerMP);
				f = block.getPlayerRelativeBlockHardness(this.thisPlayerMP, this.thisPlayerMP.worldObj, blockpos);
			}

			if (block.getMaterial() != Material.air && f >= 1.0F) {
				this.tryHarvestBlock(blockpos);
			} else {
				this.isDestroyingBlock = true;
				this.field_180240_f = blockpos;
				int i = (int) (f * 10.0F);
				this.theWorld.sendBlockBreakProgress(this.thisPlayerMP.getEntityId(), blockpos, i);
				this.durabilityRemainingOnBlock = i;
			}

		}
	}

	public void blockRemoving(BlockPos blockpos) {
		if (blockpos.equals(this.field_180240_f)) {
			int i = this.curblockDamage - this.initialDamage;
			Block block = this.theWorld.getBlockState(blockpos).getBlock();
			if (block.getMaterial() != Material.air) {
				float f = block.getPlayerRelativeBlockHardness(this.thisPlayerMP, this.thisPlayerMP.worldObj, blockpos)
						* (float) (i + 1);
				if (f >= 0.7F) {
					this.isDestroyingBlock = false;
					this.theWorld.sendBlockBreakProgress(this.thisPlayerMP.getEntityId(), blockpos, -1);
					this.tryHarvestBlock(blockpos);
				} else if (!this.receivedFinishDiggingPacket) {
					this.isDestroyingBlock = false;
					this.receivedFinishDiggingPacket = true;
					this.field_180241_i = blockpos;
					this.initialBlockDamage = this.initialDamage;
				}
			}
		}

	}

	public void cancelDestroyingBlock() {
		this.isDestroyingBlock = false;
		this.theWorld.sendBlockBreakProgress(this.thisPlayerMP.getEntityId(), this.field_180240_f, -1);
	}

	private boolean removeBlock(BlockPos pos) {
		IBlockState iblockstate = this.theWorld.getBlockState(pos);
		// [Agent Note 2026-09-04] MAP-MODE (MAP-MODE-BRK-001) —
		// destruction guard: in PLAY (preview) mode command blocks are
		// unbreakable — the map is treated as published. Creative
		// instamine, survival digging and area mining ALL sink into
		// this method, so one honest choke point covers every path;
		// the S23PacketBlockChange re-sync in the callers then restores
		// the block for creative clients. GENERAL for every map/mod.
		if (net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.isServerPlay()
				&& iblockstate.getBlock() instanceof net.minecraft.block.BlockCommandBlock) {
			net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog.hit("map_mode",
					"ItemInWorldManager", "break_guard", "blocked", "pos=" + pos);
			return false;
		}
		if (net.lax1dude.eaglercraft.v1_8.forge.ForgeHooks.onBlockBreak(this.thisPlayerMP, this.theWorld, pos, iblockstate)) {
			// [Agent Note 2026-07-12]: break canceled by a listener (cascade/mod).
			// Log so smart_builder can see cancel decisions for any mod.
			try {
				net.lax1dude.eaglercraft.v1_8.forge.CompatInteractionLog.logBlockBreak(
						this.thisPlayerMP, this.theWorld, pos, iblockstate,
						"canceled", "BlockEvent.Break_canceled", null);
			} catch (Throwable ignored) {
			}
			return false;
		}
		iblockstate.getBlock().onBlockHarvested(this.theWorld, pos, iblockstate, this.thisPlayerMP);
		// [Agent Note]: GENERAL Forge 1.20.1 bridge — give IForgeBlock a chance to run
		// custom destruction logic / veto the removal (onDestroyedByPlayer). Returning
		// false here means the block's own code fully handled removal, matching Forge.
		// willHarvest=true because the survival harvest path drops items after removal.
		if (!net.lax1dude.eaglercraft.v1_8.forge.ForgeBlockHooksCompat.onDestroyedByPlayer(
				iblockstate, this.theWorld, pos, this.thisPlayerMP, true)) {
			return false;
		}
		boolean flag = this.theWorld.setBlockToAir(pos);
		if (flag) {
			iblockstate.getBlock().onBlockDestroyedByPlayer(this.theWorld, pos, iblockstate);
		}

		return flag;
	}

	public boolean tryHarvestBlock(BlockPos blockpos) {
		return this.tryHarvestBlock(blockpos, true);
	}

	public boolean tryHarvestBlock(BlockPos blockpos, boolean allowAreaMining) {
		if (this.gameType.isCreative() && this.thisPlayerMP.getHeldItem() != null
				&& this.thisPlayerMP.getHeldItem().getItem() instanceof ItemSword) {
			return false;
		} else {
			IBlockState iblockstate = this.theWorld.getBlockState(blockpos);
			TileEntity tileentity = this.theWorld.getTileEntity(blockpos);
			if (this.gameType.isAdventure()) {
				if (this.gameType == WorldSettings.GameType.SPECTATOR) {
					return false;
				}

				if (!this.thisPlayerMP.isAllowEdit()) {
					ItemStack itemstack = this.thisPlayerMP.getCurrentEquippedItem();
					if (itemstack == null) {
						return false;
					}

					if (!itemstack.canDestroy(iblockstate.getBlock())) {
						return false;
					}
				}
			}

			if (allowAreaMining && this.breakAreaBlock(blockpos)) {
				return true;
			}

			this.theWorld.playAuxSFXAtEntity(this.thisPlayerMP, 2001, blockpos, Block.getStateId(iblockstate));
			boolean flag1 = this.removeBlock(blockpos);
			if (this.isCreative()) {
				this.thisPlayerMP.playerNetServerHandler.sendPacket(new S23PacketBlockChange(this.theWorld, blockpos));
			} else {
				ItemStack itemstack1 = this.thisPlayerMP.getCurrentEquippedItem();
				boolean flag = this.thisPlayerMP.canHarvestBlock(iblockstate.getBlock());
				if (itemstack1 != null) {
					itemstack1.onBlockDestroyed(this.theWorld, iblockstate.getBlock(), blockpos, this.thisPlayerMP);
					if (itemstack1.stackSize == 0) {
						this.thisPlayerMP.destroyCurrentEquippedItem();
					}
				}

				if (flag1 && flag) {
					iblockstate.getBlock().harvestBlock(this.theWorld, this.thisPlayerMP, blockpos, iblockstate,
							tileentity);
				}
			}

			return flag1;
		}
	}

	private boolean breakAreaBlock(BlockPos origin) {
		ItemStack stack = this.thisPlayerMP.getCurrentEquippedItem();
		if (stack == null || !(stack.getItem() instanceof net.lax1dude.eaglercraft.v1_8.forge.DynamicModItem)) {
			return false;
		}
		int radius = stack.getItem().areaMiningRadius;
		if (radius <= 0) {
			return false;
		}
		BlockPos[] positions = resolveAreaMiningPositions(origin, this.currentBreakFace, radius);
		boolean brokeAny = false;
		for (int i = 0; i < positions.length; i++) {
			BlockPos pos = positions[i];
			if (!pos.equals(origin)) {
				IBlockState state = this.theWorld.getBlockState(pos);
				Block block = state.getBlock();
				if (block.getMaterial() == net.minecraft.block.material.Material.air) {
					continue;
				}
				if (!this.thisPlayerMP.canHarvestBlock(block)) {
					continue;
				}
				if (stack.getItem().getStrVsBlock(stack, block) <= 1.0F) {
					continue;
				}
			}
			if (this.tryHarvestBlock(pos, false)) {
				brokeAny = true;
			}
		}
		return brokeAny;
	}

	private BlockPos[] resolveAreaMiningPositions(BlockPos origin, EnumFacing face, int radius) {
		java.util.ArrayList<BlockPos> positions = new java.util.ArrayList<BlockPos>();
		if (face == EnumFacing.UP || face == EnumFacing.DOWN) {
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					positions.add(origin.add(x, 0, z));
				}
			}
		} else if (face == EnumFacing.NORTH || face == EnumFacing.SOUTH) {
			for (int x = -radius; x <= radius; x++) {
				for (int y = -radius; y <= radius; y++) {
					positions.add(origin.add(x, y, 0));
				}
			}
		} else {
			for (int y = -radius; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					positions.add(origin.add(0, y, z));
				}
			}
		}
		return positions.toArray(new BlockPos[positions.size()]);
	}

	public boolean tryUseItem(EntityPlayer entityplayer, World world, ItemStack itemstack) {
		if (this.gameType == WorldSettings.GameType.SPECTATOR) {
			return false;
		} else {
			int i = itemstack.stackSize;
			int j = itemstack.getMetadata();
			ItemStack itemstack1 = itemstack.useItemRightClick(world, entityplayer);
			if (itemstack1 != itemstack || itemstack1 != null && (itemstack1.stackSize != i
					|| itemstack1.getMaxItemUseDuration() > 0 || itemstack1.getMetadata() != j)) {
				entityplayer.inventory.mainInventory[entityplayer.inventory.currentItem] = itemstack1;
				if (this.isCreative()) {
					itemstack1.stackSize = i;
					if (itemstack1.isItemStackDamageable()) {
						itemstack1.setItemDamage(j);
					}
				}

				if (itemstack1.stackSize == 0) {
					entityplayer.inventory.mainInventory[entityplayer.inventory.currentItem] = null;
				}

				if (!entityplayer.isUsingItem()) {
					((EntityPlayerMP) entityplayer).sendContainerToPlayer(entityplayer.inventoryContainer);
				}

				return true;
			} else {
				return false;
			}
		}
	}

	public boolean activateBlockOrUseItem(EntityPlayer entityplayer, World world, ItemStack itemstack,
			BlockPos blockpos, EnumFacing enumfacing, float f, float f1, float f2) {
		if (this.gameType == WorldSettings.GameType.SPECTATOR) {
			TileEntity tileentity = world.getTileEntity(blockpos);
			if (tileentity instanceof ILockableContainer) {
				Block block = world.getBlockState(blockpos).getBlock();
				ILockableContainer ilockablecontainer = (ILockableContainer) tileentity;
				if (ilockablecontainer instanceof TileEntityChest && block instanceof BlockChest) {
					ilockablecontainer = ((BlockChest) block).getLockableContainer(world, blockpos);
				}

				if (ilockablecontainer != null) {
					entityplayer.displayGUIChest(ilockablecontainer);
					return true;
				}
			} else if (tileentity instanceof IInventory) {
				entityplayer.displayGUIChest((IInventory) tileentity);
				return true;
			}

			return false;
		} else {
			IBlockState iblockstate = world.getBlockState(blockpos);
			if (net.lax1dude.eaglercraft.v1_8.forge.ForgeHooks.onRightClickBlock(entityplayer, world, blockpos, iblockstate, enumfacing, itemstack)) {
				return true;
			}
			// [Agent Note] General compatibility — check if the held item wants to
			// intercept the interaction before the block does (IForgeItem.onItemUseFirst).
			// [Agent Note 2026-08-02] GENERAL FIX — if the clicked block itself has a
			// registered interaction handler (BlockInteractionCompatRegistry), the block
			// consumes the right-click and must take priority over any item-level
			// onItemUseFirst. Otherwise a generic item config screen preempts the block's
			// own GUI. No per-mod hardcode — any block registered in the registry wins.
			boolean blockTakesPriority = false;
			try {
				Object blockName = net.minecraft.block.Block.blockRegistry.getNameForObject(iblockstate.getBlock());
				if (blockName instanceof net.minecraft.util.ResourceLocation) {
					blockTakesPriority = net.lax1dude.eaglercraft.v1_8.forge.BlockInteractionCompatRegistry
							.shouldBlockTakePriority((net.minecraft.util.ResourceLocation) blockName);
				}
			} catch (Throwable t) {
				// Ignore — fall through to normal item-first dispatch.
			}
			// [Agent Note 2026-08-03] GENERAL — link tools override block-first priority
			// so two-phase bind can write TE link NBT (see BlockLinkBridge).
			if (blockTakesPriority && net.lax1dude.eaglercraft.v1_8.forge.BlockLinkBridge
					.shouldItemLinkTakePriority(itemstack)) {
				blockTakesPriority = false;
			}
			if (itemstack != null && !blockTakesPriority && net.lax1dude.eaglercraft.v1_8.forge.ForgeItemHooksCompat
					.onItemUseFirst(itemstack, entityplayer, world, blockpos, enumfacing, f, f1, f2)) {
				return true;
			}
			// [Agent Note] General compatibility — check if the held item bypasses the
			// sneak-use restriction (IForgeItem.doesSneakBypassUse).
			boolean bypassSneak = itemstack != null && net.lax1dude.eaglercraft.v1_8.forge.ForgeItemHooksCompat
					.doesSneakBypassUse(itemstack, world, blockpos, entityplayer);
			if (bypassSneak || !entityplayer.isSneaking() || entityplayer.getHeldItem() == null) {
				// [Agent Note 2026-08-07 — GENERAL UNCAUGHT-EXCEPTION CRASH GUARD]
				// Server-side Block.use / onBlockActivated dispatch (screen formation,
				// peripheral session dock, linked-GUI, machine menus...). Any Throwable
				// from a dynamic-mod block is caught, fully logged (stack + modId +
				// phase), and the click safely not-claimed instead of crashing the
				// integrated server thread / ejecting the player with no trace.
				boolean activated = net.lax1dude.eaglercraft.v1_8.forge.CompatSafeInvoke
						.guardBlockActivate(world, blockpos,
								new net.lax1dude.eaglercraft.v1_8.forge.CompatSafeInvoke.BooleanInvoker() {
									@Override
									public boolean invoke() {
										return iblockstate.getBlock().onBlockActivated(world, blockpos, iblockstate,
												entityplayer, enumfacing, f, f1, f2);
									}
								});
				if (activated) {
					return true;
				}
			}

			if (itemstack == null) {
				return false;
			} else if (this.isCreative()) {
				int j = itemstack.getMetadata();
				int i = itemstack.stackSize;
				boolean flag = itemstack.onItemUse(entityplayer, world, blockpos, enumfacing, f, f1, f2);
				itemstack.setItemDamage(j);
				itemstack.stackSize = i;
				return flag;
			} else {
				return itemstack.onItemUse(entityplayer, world, blockpos, enumfacing, f, f1, f2);
			}
		}
	}

	public void setWorld(WorldServer serverWorld) {
		this.theWorld = serverWorld;
	}
}