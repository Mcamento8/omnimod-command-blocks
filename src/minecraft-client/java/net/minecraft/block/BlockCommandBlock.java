package net.minecraft.block;

import net.lax1dude.eaglercraft.v1_8.EaglercraftRandom;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.material.Material;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.properties.PropertyBool;
import net.minecraft.block.state.BlockState;
import net.minecraft.block.state.IBlockState;
import net.minecraft.command.server.CommandBlockLogic;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
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
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
 * PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * 
 */
public class BlockCommandBlock extends BlockContainer {
	public static final PropertyBool TRIGGERED = PropertyBool.create("triggered");

	public BlockCommandBlock() {
		super(Material.iron, MapColor.adobeColor);
		this.setDefaultState(this.blockState.getBaseState().withProperty(TRIGGERED, Boolean.valueOf(false)));
	}

	public TileEntity createNewTileEntity(World var1, int var2) {
		return new TileEntityCommandBlock();
	}

	public void onNeighborBlockChange(World world, BlockPos blockpos, IBlockState iblockstate, Block var4) {
		if (!world.isRemote) {
			boolean flag = world.isBlockPowered(blockpos);
			boolean flag1 = ((Boolean) iblockstate.getValue(TRIGGERED)).booleanValue();
			if (flag && !flag1) {
				world.setBlockState(blockpos, iblockstate.withProperty(TRIGGERED, Boolean.valueOf(true)), 4);
				world.scheduleUpdate(blockpos, this, this.tickRate(world));
			} else if (!flag && flag1) {
				world.setBlockState(blockpos, iblockstate.withProperty(TRIGGERED, Boolean.valueOf(false)), 4);
			}
		}
	}

	public void updateTick(World world, BlockPos blockpos, IBlockState var3, EaglercraftRandom var4) {
		TileEntity tileentity = world.getTileEntity(blockpos);
		if (tileentity instanceof TileEntityCommandBlock) {
			((TileEntityCommandBlock) tileentity).getCommandBlockLogic().trigger(world);
			world.updateComparatorOutputLevel(blockpos, this);
		}

	}

	public int tickRate(World var1) {
		return 1;
	}

	public boolean onBlockActivated(World world, BlockPos blockpos, IBlockState var3, EntityPlayer entityplayer,
			EnumFacing var5, float var6, float var7, float var8) {
		TileEntity tileentity = world.getTileEntity(blockpos);
		return tileentity instanceof TileEntityCommandBlock
				? ((TileEntityCommandBlock) tileentity).getCommandBlockLogic().tryOpenEditCommandBlock(entityplayer)
				: false;
	}

	public boolean hasComparatorInputOverride() {
		return true;
	}

	public int getComparatorInputOverride(World world, BlockPos blockpos) {
		TileEntity tileentity = world.getTileEntity(blockpos);
		return tileentity instanceof TileEntityCommandBlock
				? ((TileEntityCommandBlock) tileentity).getCommandBlockLogic().getSuccessCount()
				: 0;
	}

	public void onBlockPlacedBy(World world, BlockPos blockpos, IBlockState var3, EntityLivingBase var4,
			ItemStack itemstack) {
		TileEntity tileentity = world.getTileEntity(blockpos);
		if (tileentity instanceof TileEntityCommandBlock) {
			CommandBlockLogic commandblocklogic = ((TileEntityCommandBlock) tileentity).getCommandBlockLogic();
			if (itemstack.hasDisplayName()) {
				commandblocklogic.setName(itemstack.getDisplayName());
			}

			if (!world.isRemote) {
				commandblocklogic.setTrackOutput(world.getGameRules().getBoolean("sendCommandFeedback"));
			}
		}
	}

	public int quantityDropped(EaglercraftRandom var1) {
		return 0;
	}

	// [Agent Note 2026-09-04] MAP-MODE (MAP-MODE-BLK-001) — dual-mode
	// rendering gate: in PLAY (preview) mode command blocks render fully
	// invisible (renderType -1), exactly like a published public map.
	// ClientMapModeRuntime re-meshes the affected chunks on every switch,
	// so the flip is instant and hole-free. GENERAL for every map/mod.
	public int getRenderType() {
		return net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.isPlayModeAnywhere() ? -1 : 3;
	}

	// [Agent Note 2026-09-04] MAP-MODE — opacity gate: hidden command
	// blocks must stop occluding neighbor faces, otherwise X-ray holes
	// appear where the invisible block still culls faces behind it.
	// Redstone wiring logic is untouched (isNormalCube has its own
	// formula and stays true); collision boxes stay solid (published
	// maps keep solid command-block walls). Honest boundary: light
	// recompute near hidden blocks treats them as transparent.
	public boolean isOpaqueCube() {
		return !net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.isPlayModeAnywhere();
	}

	// [Agent Note 2026-09-04] MAP-MODE — targeting gate: ray traces
	// (mouse-over, pick-block, arrows) skip hidden command blocks in
	// PLAY mode so the player can never select, open or pick them.
	// Server guards (CommandBlockLogic / ItemInWorldManager) are the
	// defense-in-depth.
	public boolean canCollideCheck(IBlockState state, boolean stopOnLiquid) {
		return !net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.isPlayModeAnywhere()
				&& super.canCollideCheck(state, stopOnLiquid);
	}

	public IBlockState getStateFromMeta(int i) {
		return this.getDefaultState().withProperty(TRIGGERED, Boolean.valueOf((i & 1) > 0));
	}

	public int getMetaFromState(IBlockState iblockstate) {
		int i = 0;
		if (((Boolean) iblockstate.getValue(TRIGGERED)).booleanValue()) {
			i |= 1;
		}

		return i;
	}

	protected BlockState createBlockState() {
		return new BlockState(this, new IProperty[] { TRIGGERED });
	}

	public IBlockState onBlockPlaced(World var1, BlockPos var2, EnumFacing var3, float var4, float var5, float var6,
			int var7, EntityLivingBase var8) {
		return this.getDefaultState().withProperty(TRIGGERED, Boolean.valueOf(false));
	}
}