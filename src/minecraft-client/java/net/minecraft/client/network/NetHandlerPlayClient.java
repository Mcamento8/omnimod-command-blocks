package net.minecraft.client.network;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.lax1dude.eaglercraft.v1_8.ClientUUIDLoadingCache;
import net.lax1dude.eaglercraft.v1_8.EaglercraftRandom;
import net.lax1dude.eaglercraft.v1_8.EaglercraftUUID;

import com.carrotsearch.hppc.cursors.ObjectIntCursor;
import com.google.common.collect.Maps;

import net.lax1dude.eaglercraft.v1_8.netty.Unpooled;
import net.lax1dude.eaglercraft.v1_8.notifications.ServerNotificationManager;
import net.lax1dude.eaglercraft.v1_8.skin_cache.ServerTextureCache;
import net.lax1dude.eaglercraft.v1_8.socket.EaglercraftNetworkManager;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageConstants;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.GamePluginMessageProtocol;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.client.ClientMessageHandler;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.handshake.StandardCaps;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.message.InjectedMessageController;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.message.LegacyMessageController;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.message.MessageController;
import net.lax1dude.eaglercraft.v1_8.socket.protocol.pkt.GameMessagePacket;
import net.lax1dude.eaglercraft.v1_8.sp.lan.LANClientNetworkManager;
import net.lax1dude.eaglercraft.v1_8.sp.socket.ClientIntegratedServerNetworkManager;
import net.lax1dude.eaglercraft.v1_8.voice.VoiceClientController;
import net.lax1dude.eaglercraft.v1_8.webview.WebViewOverlayController;
import net.lax1dude.eaglercraft.v1_8.log4j.LogManager;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.lax1dude.eaglercraft.v1_8.forge.CompatEntityDescriptor;
import net.lax1dude.eaglercraft.v1_8.forge.DynamicEntityRegistry;
import net.lax1dude.eaglercraft.v1_8.forge.PortalLifecycleRuntime;
import net.lax1dude.eaglercraft.v1_8.minecraft.EaglerFolderResourcePack;
import net.lax1dude.eaglercraft.v1_8.opengl.GlStateManager;
import net.lax1dude.eaglercraft.v1_8.mojang.authlib.GameProfile;
import net.minecraft.block.Block;
import net.minecraft.client.ClientBrandRetriever;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.GuardianSound;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiDisconnected;
import net.minecraft.client.gui.GuiDownloadTerrain;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiMerchant;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenBook;
import net.minecraft.client.gui.GuiWinGame;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.gui.GuiYesNoCallback;
import net.minecraft.client.gui.IProgressMeter;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.multiplayer.PlayerControllerMP;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.EntityPickupFX;
import net.minecraft.client.player.inventory.ContainerLocalMenu;
import net.minecraft.client.player.inventory.LocalBlockIntercommunication;
import net.minecraft.client.resources.I18n;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import net.lax1dude.eaglercraft.v1_8.forge.DynamicChestTileEntity;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLeashKnot;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IMerchant;
import net.minecraft.entity.NpcMerchant;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.BaseAttributeMap;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.ai.attributes.RangedAttribute;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.item.EntityBoat;
import net.minecraft.entity.item.EntityEnderCrystal;
import net.minecraft.entity.item.EntityEnderEye;
import net.minecraft.entity.item.EntityEnderPearl;
import net.minecraft.entity.item.EntityExpBottle;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityFireworkRocket;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityItemFrame;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.item.EntityPainting;
import net.minecraft.entity.item.EntityTNTPrimed;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.EntityGuardian;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntityFishHook;
import net.minecraft.entity.projectile.EntityLargeFireball;
import net.minecraft.entity.projectile.EntityPotion;
import net.minecraft.entity.projectile.EntitySmallFireball;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityWitherSkull;
import net.minecraft.init.Items;
import net.minecraft.inventory.AnimalChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryBasic;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMap;
import net.minecraft.item.ItemStack;
import net.minecraft.network.Packet;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayClient;
import net.minecraft.network.play.client.C00PacketKeepAlive;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C0FPacketConfirmTransaction;
import net.minecraft.network.play.client.C17PacketCustomPayload;
import net.minecraft.network.play.client.C19PacketResourcePackStatus;
import net.minecraft.network.play.server.S00PacketKeepAlive;
import net.minecraft.network.play.server.S01PacketJoinGame;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S03PacketTimeUpdate;
import net.minecraft.network.play.server.S04PacketEntityEquipment;
import net.minecraft.network.play.server.S05PacketSpawnPosition;
import net.minecraft.network.play.server.S06PacketUpdateHealth;
import net.minecraft.network.play.server.S07PacketRespawn;
import net.minecraft.network.play.server.S08PacketPlayerPosLook;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraft.network.play.server.S0APacketUseBed;
import net.minecraft.network.play.server.S0BPacketAnimation;
import net.minecraft.network.play.server.S0CPacketSpawnPlayer;
import net.minecraft.network.play.server.S0DPacketCollectItem;
import net.minecraft.network.play.server.S0EPacketSpawnObject;
import net.minecraft.network.play.server.S0FPacketSpawnMob;
import net.minecraft.network.play.server.S10PacketSpawnPainting;
import net.minecraft.network.play.server.S11PacketSpawnExperienceOrb;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S13PacketDestroyEntities;
import net.minecraft.network.play.server.S14PacketEntity;
import net.minecraft.network.play.server.S18PacketEntityTeleport;
import net.minecraft.network.play.server.S19PacketEntityHeadLook;
import net.minecraft.network.play.server.S19PacketEntityStatus;
import net.minecraft.network.play.server.S1BPacketEntityAttach;
import net.minecraft.network.play.server.S1CPacketEntityMetadata;
import net.minecraft.network.play.server.S1DPacketEntityEffect;
import net.minecraft.network.play.server.S1EPacketRemoveEntityEffect;
import net.minecraft.network.play.server.S1FPacketSetExperience;
import net.minecraft.network.play.server.S20PacketEntityProperties;
import net.minecraft.network.play.server.S20PacketEntityProperties.Snapshot;
import net.minecraft.network.play.server.S21PacketChunkData;
import net.minecraft.network.play.server.S22PacketMultiBlockChange;
import net.minecraft.network.play.server.S22PacketMultiBlockChange.BlockUpdateData;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S24PacketBlockAction;
import net.minecraft.network.play.server.S25PacketBlockBreakAnim;
import net.minecraft.network.play.server.S26PacketMapChunkBulk;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.network.play.server.S28PacketEffect;
import net.minecraft.network.play.server.S29PacketSoundEffect;
import net.minecraft.network.play.server.S2APacketParticles;
import net.minecraft.network.play.server.S2BPacketChangeGameState;
import net.minecraft.network.play.server.S2CPacketSpawnGlobalEntity;
import net.minecraft.network.play.server.S2DPacketOpenWindow;
import net.minecraft.network.play.server.S2EPacketCloseWindow;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.network.play.server.S30PacketWindowItems;
import net.minecraft.network.play.server.S31PacketWindowProperty;
import net.minecraft.network.play.server.S32PacketConfirmTransaction;
import net.minecraft.network.play.server.S33PacketUpdateSign;
import net.minecraft.network.play.server.S34PacketMaps;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.network.play.server.S36PacketSignEditorOpen;
import net.minecraft.network.play.server.S37PacketStatistics;
import net.minecraft.network.play.server.S38PacketPlayerListItem;
import net.minecraft.network.play.server.S38PacketPlayerListItem.AddPlayerData;
import net.minecraft.network.play.server.S39PacketPlayerAbilities;
import net.minecraft.network.play.server.S3APacketTabComplete;
import net.minecraft.network.play.server.S3BPacketScoreboardObjective;
import net.minecraft.network.play.server.S3CPacketUpdateScore;
import net.minecraft.network.play.server.S3DPacketDisplayScoreboard;
import net.minecraft.network.play.server.S3EPacketTeams;
import net.minecraft.network.play.server.S3FPacketCustomPayload;
import net.minecraft.network.play.server.S40PacketDisconnect;
import net.minecraft.network.play.server.S41PacketServerDifficulty;
import net.minecraft.network.play.server.S42PacketCombatEvent;
import net.minecraft.network.play.server.S43PacketCamera;
import net.minecraft.network.play.server.S44PacketWorldBorder;
import net.minecraft.network.play.server.S45PacketTitle;
import net.minecraft.network.play.server.S46PacketSetCompressionLevel;
import net.minecraft.network.play.server.S47PacketPlayerListHeaderFooter;
import net.minecraft.network.play.server.S48PacketResourcePackSend;
import net.minecraft.network.play.server.S49PacketUpdateEntityNBT;
import net.minecraft.potion.PotionEffect;
import net.minecraft.scoreboard.IScoreObjectiveCriteria;
import net.minecraft.scoreboard.Score;
import net.minecraft.scoreboard.ScoreObjective;
import net.minecraft.scoreboard.ScorePlayerTeam;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.Team;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.AchievementList;
import net.minecraft.stats.StatBase;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBanner;
import net.minecraft.tileentity.TileEntityBeacon;
import net.minecraft.tileentity.TileEntityCommandBlock;
import net.minecraft.tileentity.TileEntityFlowerPot;
import net.minecraft.tileentity.TileEntityMobSpawner;
import net.minecraft.tileentity.TileEntitySign;
import net.minecraft.tileentity.TileEntitySkull;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.StringUtils;
import net.minecraft.village.MerchantRecipeList;
import net.minecraft.world.Explosion;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.storage.MapData;

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
public class NetHandlerPlayClient implements INetHandlerPlayClient {
	private static final Logger logger = LogManager.getLogger();
	private final EaglercraftNetworkManager netManager;
	private final GameProfile profile;
	private final GuiScreen guiScreenServer;
	private Minecraft gameController;
	// [Agent Note] General compatibility — timestamp of the last container GUI opened
	// by handleOpenWindow. Used to dedupe a duplicate open-window packet (see guard at
	// the top of handleOpenWindow) so a correct custom chest screen is not replaced by
	// a generic one.
	private long lastOpenedScreenTime = 0L;
	private WorldClient clientWorldController;
	private boolean doneLoadingTerrain;
	private final Map<EaglercraftUUID, NetworkPlayerInfo> playerInfoMap = Maps.newHashMap();
	public int currentServerMaxPlayers = 20;
	private boolean field_147308_k = false;
	private boolean isIntegratedServer = false;
	private final EaglercraftRandom avRandomizer = new EaglercraftRandom();
	private final ServerTextureCache textureCache;
	private final ServerNotificationManager notifManager;
	public boolean currentFNAWSkinAllowedState = true;
	public boolean currentFNAWSkinForcedState = true;
	private final MessageController eaglerMessageController;
	public byte[] cachedServerInfoHash = null;
	public byte[] cachedServerInfoData = null;
	public boolean allowedDisplayWebview = false;
	public boolean allowedDisplayWebviewYes = false;

	public NetHandlerPlayClient(Minecraft mcIn, GuiScreen parGuiScreen, EaglercraftNetworkManager parNetworkManager,
			GameProfile parGameProfile, GamePluginMessageProtocol eaglerProtocol) {
		this.gameController = mcIn;
		this.guiScreenServer = parGuiScreen;
		this.netManager = parNetworkManager;
		this.netManager.setNetHandler(this);
		this.profile = parGameProfile;
		this.isIntegratedServer = (parNetworkManager instanceof ClientIntegratedServerNetworkManager)
				|| (parNetworkManager instanceof LANClientNetworkManager);
		ClientMessageHandler handler = ClientMessageHandler.createClientHandler(eaglerProtocol.ver, this);
		if (eaglerProtocol.ver >= 5) {
			this.eaglerMessageController = new InjectedMessageController(eaglerProtocol, handler,
					GamePluginMessageConstants.CLIENT_TO_SERVER, parNetworkManager::injectRawFrame);
			parNetworkManager.setInjectedMessageController((InjectedMessageController) eaglerMessageController);
		} else {
			this.eaglerMessageController = new LegacyMessageController(eaglerProtocol, handler,
					GamePluginMessageConstants.CLIENT_TO_SERVER,
					(ch, msg) -> addToSendQueue(new C17PacketCustomPayload(ch, msg)));
		}
		this.textureCache = ServerTextureCache.create(this, mcIn.getTextureManager());
		this.notifManager = new ServerNotificationManager(mcIn.getTextureManager());
	}

	public void cleanup() {
		this.clientWorldController = null;
		this.textureCache.destroy();
		this.notifManager.destroy();
	}

	public ServerTextureCache getTextureCache() {
		return this.textureCache;
	}

	public ServerNotificationManager getNotifManager() {
		return this.notifManager;
	}

	public MessageController getEaglerMessageController() {
		return eaglerMessageController;
	}

	public GamePluginMessageProtocol getEaglerMessageProtocol() {
		return eaglerMessageController != null ? eaglerMessageController.getProtocol() : null;
	}

	public void sendEaglerMessage(GameMessagePacket packet) {
		eaglerMessageController.sendPacket(packet);
	}

	public boolean webViewSendHandler(GameMessagePacket pkt) {
		if (eaglerMessageController == null) {
			return false;
		}
		if (this.gameController.thePlayer == null || this.gameController.thePlayer.sendQueue != this) {
			logger.error("WebView sent message on a dead handler!");
			return false;
		}
		if (eaglerMessageController.getProtocol().ver >= 4) {
			sendEaglerMessage(pkt);
			return true;
		} else {
			return false;
		}
	}

	public void handleJoinGame(S01PacketJoinGame packetIn) {
		this.gameController.playerController = new PlayerControllerMP(this.gameController, this);
		this.clientWorldController = new WorldClient(this, new WorldSettings(0L, packetIn.getGameType(), false,
				packetIn.isHardcoreMode(), packetIn.getWorldType()), packetIn.getDimension(), packetIn.getDifficulty());
		this.gameController.gameSettings.difficulty = packetIn.getDifficulty();
		this.gameController.loadWorld(this.clientWorldController);
		this.gameController.thePlayer.dimension = packetIn.getDimension();
		this.gameController.displayGuiScreen(new GuiDownloadTerrain(this));
		this.gameController.thePlayer.setEntityId(packetIn.getEntityId());
		this.currentServerMaxPlayers = packetIn.getMaxPlayers();
		this.gameController.thePlayer.setReducedDebug(packetIn.isReducedDebugInfo());
		this.gameController.playerController.setGameType(packetIn.getGameType());
		this.gameController.gameSettings.sendSettingsToServer();
		this.netManager.sendPacket(new C17PacketCustomPayload("MC|Brand",
				(new PacketBuffer(Unpooled.buffer())).writeString(ClientBrandRetriever.getClientModName())));
		if (VoiceClientController.isClientSupported()) {
			if (netManager.getServerCapabilities().hasCapability(StandardCaps.VOICE, 0)) {
				VoiceClientController.initializeVoiceClient(this::sendEaglerMessage,
						eaglerMessageController.getProtocol().ver);
			} else {
				VoiceClientController.initializeVoiceClient(null, -1);
			}
		}
		if (netManager.getServerCapabilities().hasCapability(StandardCaps.WEBVIEW, 0)) {
			WebViewOverlayController.setPacketSendCallback(this::webViewSendHandler);
		} else {
			WebViewOverlayController.setPacketSendCallback(null);
		}
	}

	public void handleSpawnObject(S0EPacketSpawnObject packetIn) {
		double d0 = (double) packetIn.getX() / 32.0D;
		double d1 = (double) packetIn.getY() / 32.0D;
		double d2 = (double) packetIn.getZ() / 32.0D;
		Entity object = null;
		boolean b = false;
		switch (packetIn.getType()) {
		case 10:
			object = EntityMinecart.func_180458_a(this.clientWorldController, d0, d1, d2,
					EntityMinecart.EnumMinecartType.byNetworkID(packetIn.func_149009_m()));
			break;
		case 90:
			b = true;
			Entity entity = this.clientWorldController.getEntityByID(packetIn.func_149009_m());
			if (entity instanceof EntityPlayer) {
				object = new EntityFishHook(this.clientWorldController, d0, d1, d2, (EntityPlayer) entity);
			}
			break;
		case 60:
			object = new EntityArrow(this.clientWorldController, d0, d1, d2);
			break;
		case 61:
			object = new EntitySnowball(this.clientWorldController, d0, d1, d2);
			break;
		case 71:
			b = true;
			object = new EntityItemFrame(this.clientWorldController,
					new BlockPos(MathHelper.floor_double(d0), MathHelper.floor_double(d1), MathHelper.floor_double(d2)),
					EnumFacing.getHorizontal(packetIn.func_149009_m()));
			break;
		case 77:
			b = true;
			object = new EntityLeashKnot(this.clientWorldController, new BlockPos(MathHelper.floor_double(d0),
					MathHelper.floor_double(d1), MathHelper.floor_double(d2)));
			break;
		case 65:
			object = new EntityEnderPearl(this.clientWorldController, d0, d1, d2);
			break;
		case 72:
			object = new EntityEnderEye(this.clientWorldController, d0, d1, d2);
			if (packetIn.func_149009_m() > 0) {
				object.setVelocity((double) packetIn.getSpeedX() / 8000.0D,
						(double) packetIn.getSpeedY() / 8000.0D,
						(double) packetIn.getSpeedZ() / 8000.0D);
			}
			if (packetIn.hasTarget()) {
				((EntityEnderEye) object).setTarget((double) packetIn.getTargetX() / 32.0D,
						(double) packetIn.getTargetY() / 32.0D,
						(double) packetIn.getTargetZ() / 32.0D);
			}
			break;
		case 76:
			object = new EntityFireworkRocket(this.clientWorldController, d0, d1, d2, (ItemStack) null);
			break;
		case 63:
			b = true;
			object = new EntityLargeFireball(this.clientWorldController, d0, d1, d2,
					(double) packetIn.getSpeedX() / 8000.0D, (double) packetIn.getSpeedY() / 8000.0D,
					(double) packetIn.getSpeedZ() / 8000.0D);
			break;
		case 64:
			b = true;
			object = new EntitySmallFireball(this.clientWorldController, d0, d1, d2,
					(double) packetIn.getSpeedX() / 8000.0D, (double) packetIn.getSpeedY() / 8000.0D,
					(double) packetIn.getSpeedZ() / 8000.0D);
			break;
		case 66:
			b = true;
			object = new EntityWitherSkull(this.clientWorldController, d0, d1, d2,
					(double) packetIn.getSpeedX() / 8000.0D, (double) packetIn.getSpeedY() / 8000.0D,
					(double) packetIn.getSpeedZ() / 8000.0D);
			break;
		case 62:
			object = new EntityEgg(this.clientWorldController, d0, d1, d2);
			break;
		case 73:
			b = true;
			object = new EntityPotion(this.clientWorldController, d0, d1, d2, packetIn.func_149009_m());
			break;
		case 75:
			b = true;
			object = new EntityExpBottle(this.clientWorldController, d0, d1, d2);
			break;
		case 1:
			object = new EntityBoat(this.clientWorldController, d0, d1, d2);
			break;
		case 50:
			object = new EntityTNTPrimed(this.clientWorldController, d0, d1, d2, (EntityLivingBase) null);
			break;
		case 78:
			object = new EntityArmorStand(this.clientWorldController, d0, d1, d2);
			break;
		case 51:
			object = new EntityEnderCrystal(this.clientWorldController, d0, d1, d2);
			break;
		case 2:
			object = new EntityItem(this.clientWorldController, d0, d1, d2);
			break;
		case 70:
			b = true;
			object = new EntityFallingBlock(this.clientWorldController, d0, d1, d2,
					Block.getStateById(packetIn.func_149009_m() & '\uffff'));
			break;
		}

		if (b) {
			// fix for compiler bug
			packetIn.func_149002_g(0);
		}

		if (object != null) {
			object.serverPosX = packetIn.getX();
			object.serverPosY = packetIn.getY();
			object.serverPosZ = packetIn.getZ();
			object.rotationPitch = (float) (packetIn.getPitch() * 360) / 256.0F;
			object.rotationYaw = (float) (packetIn.getYaw() * 360) / 256.0F;
			Entity[] aentity = object.getParts();
			if (aentity != null) {
				int i = packetIn.getEntityID() - object.getEntityId();

				for (int j = 0; j < aentity.length; ++j) {
					aentity[j].setEntityId(aentity[j].getEntityId() + i);
				}
			}

			object.setEntityId(packetIn.getEntityID());
			this.clientWorldController.addEntityToWorld(packetIn.getEntityID(), object);
			if (packetIn.func_149009_m() > 0) {
				if (packetIn.getType() == 60) {
					Entity entity1 = this.clientWorldController.getEntityByID(packetIn.func_149009_m());
					if (entity1 instanceof EntityLivingBase && object instanceof EntityArrow) {
						((EntityArrow) object).shootingEntity = entity1;
					}
				}

				object.setVelocity((double) packetIn.getSpeedX() / 8000.0D, (double) packetIn.getSpeedY() / 8000.0D,
						(double) packetIn.getSpeedZ() / 8000.0D);
			}
		}

	}

	public void handleSpawnExperienceOrb(S11PacketSpawnExperienceOrb packetIn) {
		EntityXPOrb entityxporb = new EntityXPOrb(this.clientWorldController, (double) packetIn.getX() / 32.0D,
				(double) packetIn.getY() / 32.0D, (double) packetIn.getZ() / 32.0D, packetIn.getXPValue());
		entityxporb.serverPosX = packetIn.getX();
		entityxporb.serverPosY = packetIn.getY();
		entityxporb.serverPosZ = packetIn.getZ();
		entityxporb.rotationYaw = 0.0F;
		entityxporb.rotationPitch = 0.0F;
		entityxporb.setEntityId(packetIn.getEntityID());
		this.clientWorldController.addEntityToWorld(packetIn.getEntityID(), entityxporb);
	}

	public void handleSpawnGlobalEntity(S2CPacketSpawnGlobalEntity packetIn) {
		double d0 = (double) packetIn.func_149051_d() / 32.0D;
		double d1 = (double) packetIn.func_149050_e() / 32.0D;
		double d2 = (double) packetIn.func_149049_f() / 32.0D;
		EntityLightningBolt entitylightningbolt = null;
		if (packetIn.func_149053_g() == 1) {
			entitylightningbolt = new EntityLightningBolt(this.clientWorldController, d0, d1, d2);
		}

		if (entitylightningbolt != null) {
			entitylightningbolt.serverPosX = packetIn.func_149051_d();
			entitylightningbolt.serverPosY = packetIn.func_149050_e();
			entitylightningbolt.serverPosZ = packetIn.func_149049_f();
			entitylightningbolt.rotationYaw = 0.0F;
			entitylightningbolt.rotationPitch = 0.0F;
			entitylightningbolt.setEntityId(packetIn.func_149052_c());
			this.clientWorldController.addWeatherEffect(entitylightningbolt);
		}

	}

	public void handleSpawnPainting(S10PacketSpawnPainting packetIn) {
		EntityPainting entitypainting = new EntityPainting(this.clientWorldController, packetIn.getPosition(),
				packetIn.getFacing(), packetIn.getTitle());
		this.clientWorldController.addEntityToWorld(packetIn.getEntityID(), entitypainting);
	}

	public void handleEntityVelocity(S12PacketEntityVelocity packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityID());
		if (entity != null) {
			entity.setVelocity((double) packetIn.getMotionX() / 8000.0D, (double) packetIn.getMotionY() / 8000.0D,
					(double) packetIn.getMotionZ() / 8000.0D);
		}
	}

	public void handleEntityMetadata(S1CPacketEntityMetadata packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityId());
		if (entity != null && packetIn.func_149376_c() != null) {
			entity.getDataWatcher().updateWatchedObjectsFromList(packetIn.func_149376_c());
		}

	}

	public void handleSpawnPlayer(S0CPacketSpawnPlayer packetIn) {
		double d0 = (double) packetIn.getX() / 32.0D;
		double d1 = (double) packetIn.getY() / 32.0D;
		double d2 = (double) packetIn.getZ() / 32.0D;
		float f = (float) (packetIn.getYaw() * 360) / 256.0F;
		float f1 = (float) (packetIn.getPitch() * 360) / 256.0F;
		EntityOtherPlayerMP entityotherplayermp = new EntityOtherPlayerMP(this.gameController.theWorld,
				this.getPlayerInfo(packetIn.getPlayer()).getGameProfile());
		entityotherplayermp.prevPosX = entityotherplayermp.lastTickPosX = (double) (entityotherplayermp.serverPosX = packetIn
				.getX());
		entityotherplayermp.prevPosY = entityotherplayermp.lastTickPosY = (double) (entityotherplayermp.serverPosY = packetIn
				.getY());
		entityotherplayermp.prevPosZ = entityotherplayermp.lastTickPosZ = (double) (entityotherplayermp.serverPosZ = packetIn
				.getZ());
		int i = packetIn.getCurrentItemID();
		if (i == 0) {
			entityotherplayermp.inventory.mainInventory[entityotherplayermp.inventory.currentItem] = null;
		} else {
			entityotherplayermp.inventory.mainInventory[entityotherplayermp.inventory.currentItem] = new ItemStack(
					Item.getItemById(i), 1, 0);
		}

		entityotherplayermp.setPositionAndRotation(d0, d1, d2, f, f1);
		this.clientWorldController.addEntityToWorld(packetIn.getEntityID(), entityotherplayermp);
		List list = packetIn.func_148944_c();
		if (list != null) {
			entityotherplayermp.getDataWatcher().updateWatchedObjectsFromList(list);
		}

	}

	public void handleEntityTeleport(S18PacketEntityTeleport packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityId());
		if (entity != null) {
			entity.serverPosX = packetIn.getX();
			entity.serverPosY = packetIn.getY();
			entity.serverPosZ = packetIn.getZ();
			double d0 = (double) entity.serverPosX / 32.0D;
			double d1 = (double) entity.serverPosY / 32.0D;
			double d2 = (double) entity.serverPosZ / 32.0D;
			float f = (float) (packetIn.getYaw() * 360) / 256.0F;
			float f1 = (float) (packetIn.getPitch() * 360) / 256.0F;
			if (Math.abs(entity.posX - d0) < 0.03125D && Math.abs(entity.posY - d1) < 0.015625D
					&& Math.abs(entity.posZ - d2) < 0.03125D) {
				entity.setPositionAndRotation2(entity.posX, entity.posY, entity.posZ, f, f1, 3, true);
			} else {
				entity.setPositionAndRotation2(d0, d1, d2, f, f1, 3, true);
			}

			entity.onGround = packetIn.getOnGround();
		}
	}

	public void handleHeldItemChange(S09PacketHeldItemChange packetIn) {
		if (packetIn.getHeldItemHotbarIndex() >= 0
				&& packetIn.getHeldItemHotbarIndex() < InventoryPlayer.getHotbarSize()) {
			this.gameController.thePlayer.inventory.currentItem = packetIn.getHeldItemHotbarIndex();
		}

	}

	public void handleEntityMovement(S14PacketEntity packetIn) {
		Entity entity = packetIn.getEntity(this.clientWorldController);
		if (entity != null) {
			entity.serverPosX += packetIn.func_149062_c();
			entity.serverPosY += packetIn.func_149061_d();
			entity.serverPosZ += packetIn.func_149064_e();
			double d0 = (double) entity.serverPosX / 32.0D;
			double d1 = (double) entity.serverPosY / 32.0D;
			double d2 = (double) entity.serverPosZ / 32.0D;
			float f = packetIn.func_149060_h() ? (float) (packetIn.func_149066_f() * 360) / 256.0F : entity.rotationYaw;
			float f1 = packetIn.func_149060_h() ? (float) (packetIn.func_149063_g() * 360) / 256.0F
					: entity.rotationPitch;
			entity.setPositionAndRotation2(d0, d1, d2, f, f1, 3, false);
			entity.onGround = packetIn.getOnGround();
		}
	}

	public void handleEntityHeadLook(S19PacketEntityHeadLook packetIn) {
		Entity entity = packetIn.getEntity(this.clientWorldController);
		if (entity != null) {
			float f = (float) (packetIn.getYaw() * 360) / 256.0F;
			entity.setRotationYawHead(f);
		}
	}

	public void handleDestroyEntities(S13PacketDestroyEntities packetIn) {
		for (int i = 0; i < packetIn.getEntityIDs().length; ++i) {
			this.clientWorldController.removeEntityFromWorld(packetIn.getEntityIDs()[i]);
		}

	}

	public void handlePlayerPosLook(S08PacketPlayerPosLook packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		double d0 = packetIn.getX();
		double d1 = packetIn.getY();
		double d2 = packetIn.getZ();
		float f = packetIn.getYaw();
		float f1 = packetIn.getPitch();
		if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X)) {
			d0 += entityplayersp.posX;
		} else {
			entityplayersp.motionX = 0.0D;
		}

		if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y)) {
			d1 += entityplayersp.posY;
		} else {
			entityplayersp.motionY = 0.0D;
		}

		if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Z)) {
			d2 += entityplayersp.posZ;
		} else {
			entityplayersp.motionZ = 0.0D;
		}

		if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.X_ROT)) {
			f1 += entityplayersp.rotationPitch;
		}

		if (packetIn.func_179834_f().contains(S08PacketPlayerPosLook.EnumFlags.Y_ROT)) {
			f += entityplayersp.rotationYaw;
		}

		entityplayersp.setPositionAndRotation(d0, d1, d2, f, f1);
		this.netManager.sendPacket(new C03PacketPlayer.C06PacketPlayerPosLook(entityplayersp.posX,
				entityplayersp.getEntityBoundingBox().minY, entityplayersp.posZ, entityplayersp.rotationYaw,
				entityplayersp.rotationPitch, false));
		if (!this.doneLoadingTerrain) {
			this.gameController.thePlayer.prevPosX = this.gameController.thePlayer.posX;
			this.gameController.thePlayer.prevPosY = this.gameController.thePlayer.posY;
			this.gameController.thePlayer.prevPosZ = this.gameController.thePlayer.posZ;
			this.doneLoadingTerrain = true;
			this.gameController.displayGuiScreen((GuiScreen) null);
		}

	}

	public void handleMultiBlockChange(S22PacketMultiBlockChange packetIn) {
		BlockUpdateData[] dat = packetIn.getChangedBlocks();
		for (int i = 0; i < dat.length; ++i) {
			BlockUpdateData s22packetmultiblockchange$blockupdatedata = dat[i];
			this.clientWorldController.invalidateRegionAndSetBlock(s22packetmultiblockchange$blockupdatedata.getPos(),
					s22packetmultiblockchange$blockupdatedata.getBlockState());
		}

	}

	public void handleChunkData(S21PacketChunkData packetIn) {
		if (packetIn.func_149274_i()) {
			if (packetIn.getExtractedSize() == 0) {
				this.clientWorldController.doPreChunk(packetIn.getChunkX(), packetIn.getChunkZ(), false);
				return;
			}

			this.clientWorldController.doPreChunk(packetIn.getChunkX(), packetIn.getChunkZ(), true);
		}

		this.clientWorldController.invalidateBlockReceiveRegion(packetIn.getChunkX() << 4, 0, packetIn.getChunkZ() << 4,
				(packetIn.getChunkX() << 4) + 15, 256, (packetIn.getChunkZ() << 4) + 15);
		Chunk chunk = this.clientWorldController.getChunkFromChunkCoords(packetIn.getChunkX(), packetIn.getChunkZ());
		chunk.fillChunk(packetIn.func_149272_d(), packetIn.getExtractedSize(), packetIn.func_149274_i());
		this.clientWorldController.markBlockRangeForRenderUpdate(packetIn.getChunkX() << 4, 0,
				packetIn.getChunkZ() << 4, (packetIn.getChunkX() << 4) + 15, 256, (packetIn.getChunkZ() << 4) + 15);
		chunk.alfheim$getLightingEngine().processLightUpdates();
		if (!packetIn.func_149274_i() || !(this.clientWorldController.provider instanceof WorldProviderSurface)) {
			chunk.resetRelightChecks();
		}
		ScheduleLightUpdateOnNeighborChunks(packetIn.getChunkX(), packetIn.getChunkZ());
	}

	private void ScheduleLightUpdateOnNeighborChunks(int cx, int cz) {
		for (int x = -1; x <= 1; x++) {
			for (int z = -1; z <= 1; z++) {
				Chunk c = this.clientWorldController.getChunkProvider().getLoadedChunk(cx + x, cz + z);
				if (c != null) {
					c.alfheim$getLightingEngine().processLightUpdates();
				}
			}
		}
	}

	public void handleBlockChange(S23PacketBlockChange packetIn) {
		this.clientWorldController.invalidateRegionAndSetBlock(packetIn.getBlockPosition(), packetIn.getBlockState());
	}

	public void handleDisconnect(S40PacketDisconnect packetIn) {
		this.netManager.closeChannel(packetIn.getReason());
	}

	public void onDisconnect(IChatComponent ichatcomponent) {
		VoiceClientController.handleServerDisconnect();
		Minecraft.getMinecraft().getRenderManager()
				.setEnableFNAWSkins(this.gameController.gameSettings.enableFNAWSkins);
		if (this.gameController.theWorld != null) {
			this.gameController.loadWorld((WorldClient) null);
		}
		if (this.guiScreenServer != null) {
			this.gameController.shutdownIntegratedServer(
					new GuiDisconnected(this.guiScreenServer, "disconnect.lost", ichatcomponent));
		} else {
			this.gameController.shutdownIntegratedServer(
					new GuiDisconnected(new GuiMultiplayer(new GuiMainMenu()), "disconnect.lost", ichatcomponent));
		}
	}

	public void addToSendQueue(Packet parPacket) {
		this.netManager.sendPacket(parPacket);
	}

	public void handleCollectItem(S0DPacketCollectItem packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getCollectedItemEntityID());
		Object object = (EntityLivingBase) this.clientWorldController.getEntityByID(packetIn.getEntityID());
		if (object == null) {
			object = this.gameController.thePlayer;
		}

		if (entity != null) {
			if (entity instanceof EntityXPOrb) {
				this.clientWorldController.playSoundAtEntity(entity, "random.orb", 0.2F,
						((this.avRandomizer.nextFloat() - this.avRandomizer.nextFloat()) * 0.7F + 1.0F) * 2.0F);
			} else {
				this.clientWorldController.playSoundAtEntity(entity, "random.pop", 0.2F,
						((this.avRandomizer.nextFloat() - this.avRandomizer.nextFloat()) * 0.7F + 1.0F) * 2.0F);
			}

			this.gameController.effectRenderer
					.addEffect(new EntityPickupFX(this.clientWorldController, entity, (Entity) object, 0.5F));
			this.clientWorldController.removeEntityFromWorld(packetIn.getCollectedItemEntityID());
		}

	}

	public void handleChat(S02PacketChat packetIn) {
		if (packetIn.getType() == 2) {
			this.gameController.ingameGUI.setRecordPlaying(packetIn.getChatComponent(), false);
		} else {
			this.gameController.ingameGUI.getChatGUI().printChatMessage(packetIn.getChatComponent());
		}

	}

	public void handleAnimation(S0BPacketAnimation packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityID());
		if (entity != null) {
			if (packetIn.getAnimationType() == 0) {
				EntityLivingBase entitylivingbase = (EntityLivingBase) entity;
				entitylivingbase.swingItem();
			} else if (packetIn.getAnimationType() == 1) {
				entity.performHurtAnimation();
			} else if (packetIn.getAnimationType() == 2) {
				EntityPlayer entityplayer = (EntityPlayer) entity;
				entityplayer.wakeUpPlayer(false, false, false);
			} else if (packetIn.getAnimationType() == 4) {
				this.gameController.effectRenderer.emitParticleAtEntity(entity, EnumParticleTypes.CRIT);
			} else if (packetIn.getAnimationType() == 5) {
				this.gameController.effectRenderer.emitParticleAtEntity(entity, EnumParticleTypes.CRIT_MAGIC);
			}

		}
	}

	public void handleUseBed(S0APacketUseBed packetIn) {
		packetIn.getPlayer(this.clientWorldController).trySleep(packetIn.getBedPosition());
	}

	public void handleSpawnMob(S0FPacketSpawnMob packetIn) {
		double d0 = (double) packetIn.getX() / 32.0D;
		double d1 = (double) packetIn.getY() / 32.0D;
		double d2 = (double) packetIn.getZ() / 32.0D;
		float f = (float) (packetIn.getYaw() * 360) / 256.0F;
		float f1 = (float) (packetIn.getPitch() * 360) / 256.0F;
		String dynamicEntityType = DynamicEntityRegistry.tryGetName(packetIn.getEntityType());
		CompatEntityDescriptor descriptor = DynamicEntityRegistry.getDescriptor(dynamicEntityType);
		if (dynamicEntityType != null) {
			logger.info(
					"modId={} phase=runtime subsystem=network action=client_spawn_received result=started errorCause=none resourcePath={} entityType={} itemId={} side=client entityNumericId={}",
					safe(modId(descriptor, dynamicEntityType)), safe(resourcePath(descriptor)), safe(dynamicEntityType),
					safe(spawnEggItemId(descriptor)), Integer.valueOf(packetIn.getEntityType()));
		}
		Entity entity = EntityList.createEntityByID(packetIn.getEntityType(), this.gameController.theWorld);
		if (!(entity instanceof EntityLivingBase)) {
			logger.warn(
					"modId={} phase=runtime subsystem=network action=client_spawn_rejected result=failed errorCause=client_constructor_missing_or_not_living resourcePath={} entityType={} itemId={} side=client entityNumericId={}",
					safe(modId(descriptor, dynamicEntityType)), safe(resourcePath(descriptor)),
					safe(dynamicEntityType != null ? dynamicEntityType : String.valueOf(packetIn.getEntityType())),
					safe(spawnEggItemId(descriptor)), Integer.valueOf(packetIn.getEntityType()));
			return;
		}
		EntityLivingBase entitylivingbase = (EntityLivingBase) entity;
		entitylivingbase.serverPosX = packetIn.getX();
		entitylivingbase.serverPosY = packetIn.getY();
		entitylivingbase.serverPosZ = packetIn.getZ();
		entitylivingbase.renderYawOffset = entitylivingbase.rotationYawHead = (float) (packetIn.getHeadPitch() * 360)
				/ 256.0F;
		Entity[] aentity = entitylivingbase.getParts();
		if (aentity != null) {
			int i = packetIn.getEntityID() - entitylivingbase.getEntityId();

			for (int j = 0; j < aentity.length; ++j) {
				aentity[j].setEntityId(aentity[j].getEntityId() + i);
			}
		}

		entitylivingbase.setEntityId(packetIn.getEntityID());
		entitylivingbase.setPositionAndRotation(d0, d1, d2, f, f1);
		entitylivingbase.motionX = (double) ((float) packetIn.getVelocityX() / 8000.0F);
		entitylivingbase.motionY = (double) ((float) packetIn.getVelocityY() / 8000.0F);
		entitylivingbase.motionZ = (double) ((float) packetIn.getVelocityZ() / 8000.0F);
		this.clientWorldController.addEntityToWorld(packetIn.getEntityID(), entitylivingbase);
		List list = packetIn.func_149027_c();
		if (list != null) {
			entitylivingbase.getDataWatcher().updateWatchedObjectsFromList(list);
		}
		if (dynamicEntityType != null) {
			logger.info(
					"modId={} phase=runtime subsystem=network action=client_spawn_committed result=degraded errorCause=generic_compat_entity_fallback resourcePath={} entityType={} itemId={} side=client entityClass={}",
					safe(modId(descriptor, dynamicEntityType)), safe(resourcePath(descriptor)), safe(dynamicEntityType),
					safe(spawnEggItemId(descriptor)), entitylivingbase.getClass().getName());
		}

	}

	private static String modId(CompatEntityDescriptor descriptor, String entityType) {
		if (descriptor != null && descriptor.modId != null && descriptor.modId.length() > 0) {
			return descriptor.modId;
		}
		if (entityType != null) {
			int colon = entityType.indexOf(':');
			if (colon > 0) {
				return entityType.substring(0, colon);
			}
		}
		return "unknown";
	}

	private static String resourcePath(CompatEntityDescriptor descriptor) {
		return descriptor != null && descriptor.texturePath != null ? descriptor.texturePath : "unknown";
	}

	private static String spawnEggItemId(CompatEntityDescriptor descriptor) {
		return descriptor != null && descriptor.spawnEggItemId != null ? descriptor.spawnEggItemId : "unknown";
	}

	private static String safe(Object value) {
		return value != null ? String.valueOf(value) : "unknown";
	}

	public void handleTimeUpdate(S03PacketTimeUpdate packetIn) {
		this.gameController.theWorld.setTotalWorldTime(packetIn.getTotalWorldTime());
		this.gameController.theWorld.setWorldTime(packetIn.getWorldTime());
	}

	public void handleSpawnPosition(S05PacketSpawnPosition packetIn) {
		this.gameController.thePlayer.setSpawnPoint(packetIn.getSpawnPos(), true);
		this.gameController.theWorld.getWorldInfo().setSpawn(packetIn.getSpawnPos());
	}

	public void handleEntityAttach(S1BPacketEntityAttach packetIn) {
		Object object = this.clientWorldController.getEntityByID(packetIn.getEntityId());
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getVehicleEntityId());
		if (packetIn.getLeash() == 0) {
			boolean flag = false;
			if (packetIn.getEntityId() == this.gameController.thePlayer.getEntityId()) {
				object = this.gameController.thePlayer;
				if (entity instanceof EntityBoat) {
					((EntityBoat) entity).setIsBoatEmpty(false);
				}

				flag = ((Entity) object).ridingEntity == null && entity != null;
			} else if (entity instanceof EntityBoat) {
				((EntityBoat) entity).setIsBoatEmpty(true);
			}

			if (object == null) {
				return;
			}

			((Entity) object).mountEntity(entity);
			if (flag) {
				GameSettings gamesettings = this.gameController.gameSettings;
				this.gameController.ingameGUI.setRecordPlaying(
						I18n.format("mount.onboard",
								new Object[] {
										GameSettings.getKeyDisplayString(gamesettings.keyBindSneak.getKeyCode()) }),
						false);
			}
		} else if (packetIn.getLeash() == 1 && object instanceof EntityLiving) {
			if (entity != null) {
				((EntityLiving) object).setLeashedToEntity(entity, false);
			} else {
				((EntityLiving) object).clearLeashed(false, false);
			}
		}

	}

	public void handleEntityStatus(S19PacketEntityStatus packetIn) {
		Entity entity = packetIn.getEntity(this.clientWorldController);
		if (entity != null) {
			if (packetIn.getOpCode() == 21) {
				this.gameController.getSoundHandler().playSound(new GuardianSound((EntityGuardian) entity));
			} else {
				entity.handleStatusUpdate(packetIn.getOpCode());
			}
		}

	}

	public void handleUpdateHealth(S06PacketUpdateHealth packetIn) {
		this.gameController.thePlayer.setPlayerSPHealth(packetIn.getHealth());
		this.gameController.thePlayer.getFoodStats().setFoodLevel(packetIn.getFoodLevel());
		this.gameController.thePlayer.getFoodStats().setFoodSaturationLevel(packetIn.getSaturationLevel());
	}

	public void handleSetExperience(S1FPacketSetExperience packetIn) {
		this.gameController.thePlayer.setXPStats(packetIn.func_149397_c(), packetIn.getTotalExperience(),
				packetIn.getLevel());
	}

	public void handleRespawn(S07PacketRespawn packetIn) {
		if (packetIn.getDimensionID() != this.gameController.thePlayer.dimension) {
			this.doneLoadingTerrain = false;
			Scoreboard scoreboard = this.clientWorldController.getScoreboard();
			this.clientWorldController = new WorldClient(this, new WorldSettings(0L, packetIn.getGameType(), false,
					this.gameController.theWorld.getWorldInfo().isHardcoreModeEnabled(), packetIn.getWorldType()),
					packetIn.getDimensionID(), packetIn.getDifficulty());
			this.clientWorldController.setWorldScoreboard(scoreboard);
			this.gameController.loadWorld(this.clientWorldController);
			this.gameController.thePlayer.dimension = packetIn.getDimensionID();
			this.gameController.displayGuiScreen(new GuiDownloadTerrain(this));
		}

		this.gameController.setDimensionAndSpawnPlayer(packetIn.getDimensionID());
		this.gameController.playerController.setGameType(packetIn.getGameType());
	}

	public void handleExplosion(S27PacketExplosion packetIn) {
		Explosion explosion = new Explosion(this.gameController.theWorld, (Entity) null, packetIn.getX(),
				packetIn.getY(), packetIn.getZ(), packetIn.getStrength(), packetIn.getAffectedBlockPositions());
		explosion.doExplosionB(true);
		this.gameController.thePlayer.motionX += (double) packetIn.func_149149_c();
		this.gameController.thePlayer.motionY += (double) packetIn.func_149144_d();
		this.gameController.thePlayer.motionZ += (double) packetIn.func_149147_e();
	}

	public void handleOpenWindow(S2DPacketOpenWindow packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		String guiId = packetIn.getGuiId();
		int windowId = packetIn.getWindowId();
		int slotCount = packetIn.getSlotCount();
		net.minecraft.util.IChatComponent title = packetIn.getWindowTitle();

		// [Agent Note] GENERAL FIX — Bug 3 (chest GUI changes by player direction).
		// Parse encoded block position from guiId BEFORE any guiId comparisons.
		// The server (both openMenu() and displayGUIChest()) may encode the block
		// position as "guiId|x,y,z" in the guiId string. We extract this FIRST so
		// that guiId comparisons below (isChestGui, "minecraft:container", etc.)
		// work correctly with the CLEAN guiId (without the position suffix).
		// GENERAL — any path that encodes BlockPos in guiId benefits from this.
		int bx = 0, by = 0, bz = 0;
		boolean hasEncodedPos = false;
		int pipeIdx = guiId.indexOf('|');
		if (pipeIdx > 0) {
			String guiIdClean = guiId.substring(0, pipeIdx);
			String posStr = guiId.substring(pipeIdx + 1);
			String[] parts = posStr.split(",");
			if (parts.length == 3) {
				try {
					bx = Integer.parseInt(parts[0]);
					by = Integer.parseInt(parts[1]);
					bz = Integer.parseInt(parts[2]);
					hasEncodedPos = true;
					guiId = guiIdClean; // Replace guiId with clean version for all comparisons below
				} catch (NumberFormatException e) {
					// Not a valid position encoding, keep original guiId unchanged
				}
			}
		}

		// [Agent Note] GENERAL FIX — Bug 4 (GUI contamination between chests).
		// The OLD guard blocked EVERY S2DPacketOpenWindow if any GuiContainer had
		// been opened within the previous 800ms. That wrongly dropped a LEGITIMATE
		// open of a DIFFERENT chest (e.g. open normal chest, then quickly open a
		// diamond chest) — the old chest's GUI simply stayed on screen, so the new
		// chest appeared with the wrong (previous) GUI.
		//
		// Fix: only suppress a packet that is a TRUE duplicate of the screen we
		// just opened — i.e. it targets the SAME windowId we already have open.
		// A genuinely different chest carries a different windowId from the server
		// and must always be allowed through (even within 800ms), so each chest
		// opens its own correct GUI. This is fully general — works for any chest
		// mod and any open sequence.
		if (this.gameController.currentScreen instanceof net.minecraft.client.gui.inventory.GuiContainer) {
			net.minecraft.inventory.Container curOpen = entityplayersp.openContainer;
			if (curOpen != null && curOpen.windowId == windowId) {
				long sinceOpen = System.currentTimeMillis() - lastOpenedScreenTime;
				if (sinceOpen >= 0L && sinceOpen <= 800L) {
					System.out.println("[...][handleOpenWindow] IGNORED duplicate open-window packet guiId=" + guiId
							+ " windowId=" + windowId + " (same windowId already open " + sinceOpen + "ms ago)");
					return;
				}
			}
		}

		// [Agent Note] OMNIMOD FIX — render the REAL chest screen (with the correct
		// DynamicChestTileEntity contents/size) instead of a generic one. The server
		// sends guiId "minecraft:chest" (or a mod chest id) for chest-like blocks.
		// The generic branch below would open a ContainerLocalMenu/InventoryBasic that
		// is NOT linked to the actual TileEntity, so contents/size would be wrong.
		// Instead, resolve the TileEntity the player is looking at (objectMouseOver)
		// and open GuiChest against the real DynamicChestTileEntity. This keeps the
		// custom, size-accurate chest screen and binds it to the server windowId.
		boolean isChestGui = "minecraft:chest".equals(guiId)
				|| net.lax1dude.eaglercraft.v1_8.forge.DynamicChestProfileRegistry.isChestGuiId(guiId);
		if (isChestGui) {
			// [Agent Note] OMNIMOD FIX — guarantee exactly ONE chest GUI per physical
			// open. A single chest activation can reach the client through several
			// packets (the server's displayGUIChest S2DPacketOpenWindow, the custom
			// OMNIMOD|OpenScreen path, and possible duplicate open-window packets for
			// both halves of a double chest). Without a hard dedupe the player sees
			// 2–3 stacked chest GUIs. Dedupe rules:
			//  1) if a chest GUI is already open for THIS windowId, ignore (true dup);
			//  2) if the custom OMNIMOD path just opened a screen for this windowId,
			//     ignore (the custom screen is authoritative and would be replaced).
			// This is general: it applies to any chest mod and both single/double chests.
			net.minecraft.inventory.Container openC = entityplayersp.openContainer;
			net.minecraft.client.gui.GuiScreen curScreen = this.gameController.currentScreen;
			boolean chestGuiOpen = curScreen instanceof net.minecraft.client.gui.inventory.GuiChest
					|| curScreen instanceof net.lax1dude.eaglercraft.v1_8.forge.GuiChestWide
					|| curScreen instanceof net.lax1dude.eaglercraft.v1_8.forge.GuiChestGrid;
			if (chestGuiOpen && openC != null && openC.windowId == windowId) {
				System.out.println("[...][handleOpenWindow] IGNORED duplicate chest open guiId=" + guiId
						+ " windowId=" + windowId + " (chest GUI already open for this windowId)");
				return;
			}
			if (net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat.consumeCustomScreenOpenedRecently(windowId)) {
				System.out.println("[...][handleOpenWindow] IGNORED duplicate chest open guiId=" + guiId
						+ " windowId=" + windowId + " (custom OMNIMOD screen already opened)");
				return;
			}
				boolean skipChestGrid = false;
				try {
					net.minecraft.world.World world = this.clientWorldController;
					TileEntity te = null;
					// [Agent Note] GENERAL FIX — Bug 3 (GUI changes based on player
					// direction). The previous code resolved the chest TileEntity
					// purely from gameController.objectMouseOver — the block the
					// player is CURRENTLY looking at when the open-window packet
					// arrives. Because objectMouseOver is a per-frame ray trace, it
					// can point at a DIFFERENT block/face than the one the player
					// actually right-clicked (the player may have turned/moved in
					// the latency between click and packet). That made the SAME
					// chest open with a different GUI depending on look angle
					// (and for double chests, picked a different half).
					//
					// Fix: prefer the AUTHORITATIVE block position sent by the
					// server via the OMNIMOD|OpenScreen channel (cached in
					// NetworkHooksCompat extraData as BlockPosX/Y/Z). This is the
					// real position the server opened, independent of where the
					// client happens to be looking. Only fall back to
					// objectMouseOver if no cached position exists (e.g. a vanilla
					// or non-custom-path chest). This keeps the opened GUI stable
					// for any chest mod and any view angle.
					//
					// [Agent Note] GENERAL FIX (Bug 3 — GUI changes by player direction).
					// ALWAYS prefer the windowId-only lookup FIRST. The guiId-keyed
					// lookup (getCachedExtraData) CONSUMES the cache entry immediately,
					// so if the guiId doesn't match the cached menuTypeName key, the
					// authoritative server position is LOST and we wrongly fall back to
					// objectMouseOver (which is angle-dependent). Using the windowId-only
					// lookup first guarantees we recover the authoritative BlockPos for
					// ANY chest mod regardless of how the server encoded the guiId.
					net.minecraft.nbt.NBTTagCompound cachedExtra = net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat
							.getCachedExtraDataByWindowId(windowId);
					if (cachedExtra == null) {
						// Secondary attempt: exact guiId key (for mods that send
						// matching guiId + menuTypeName). Still general.
						cachedExtra = net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat
								.getCachedExtraData(windowId, guiId);
					}
					net.minecraft.util.BlockPos serverPos = null;
					if (cachedExtra != null && cachedExtra.hasKey("BlockPosX")) {
						try {
							bx = cachedExtra.getInteger("BlockPosX");
							by = cachedExtra.getInteger("BlockPosY");
							bz = cachedExtra.getInteger("BlockPosZ");
							serverPos = new net.minecraft.util.BlockPos(bx, by, bz);
						} catch (Throwable ignored) {
							serverPos = null;
						}
					}
					if (serverPos != null) {
						TileEntity serverTe = world.getTileEntity(serverPos);
						if (serverTe instanceof DynamicChestTileEntity) {
							te = serverTe;
						}
					}
					if (te == null) {
						// [Agent Note] GENERAL FIX (Bug 3) — Fallback ONLY when NO cached
						// server position exists at all. Even then, validate that the
						// look-at target is reasonably close to where the player is
						// standing (within 6 blocks) so a stale/angle-dependent ray trace
						// doesn't resolve to a DIFFERENT chest the player isn't actually
						// interacting with. This keeps the GUI stable for any chest mod.
						MovingObjectPosition mop = this.gameController.objectMouseOver;
						if (mop != null && mop.getBlockPos() != null) {
							net.minecraft.util.BlockPos mopPos = mop.getBlockPos();
							double dx = mopPos.getX() + 0.5 - entityplayersp.posX;
							double dy = mopPos.getY() + 0.5 - entityplayersp.posY;
							double dz = mopPos.getZ() + 0.5 - entityplayersp.posZ;
							double distSq = dx * dx + dy * dy + dz * dz;
							if (distSq <= 36.0) { // 6 blocks max reach
								TileEntity mopTe = world.getTileEntity(mopPos);
								if (mopTe instanceof DynamicChestTileEntity) {
									te = mopTe;
								}
							}
						}
					}
					if (te instanceof DynamicChestTileEntity) {
						DynamicChestTileEntity dte = (DynamicChestTileEntity) te;
							// [Agent Note] v3 — REFUSE to enter the chest-grid branch for
							// non-grid-compatible mods. Storage Drawers, Functional Storage,
							// Waystones, BiblioCraft, etc. have their own MenuType + screen
							// factory and MUST reach the dynamic MenuType branch — entering
							// the grid branch for them would produce distorted non-grid slots
							// at row-major positions (SD's per-slot coords / FS overlay / etc.).
							// We set skipChestGrid=true so the outer flow falls through to the
							// dynamic MenuType branch below (which honours the mod's own
							// screen factory for those mods).
							net.minecraft.util.ResourceLocation teBlockLoc =
									(net.minecraft.util.ResourceLocation) net.minecraft.block.Block.blockRegistry
											.getNameForObject(dte.getBlockType());
							if (teBlockLoc != null) {
								String teModId = teBlockLoc.getResourceDomain();
								String tePath = teBlockLoc.getResourcePath();
								if (!net.lax1dude.eaglercraft.v1_8.forge.ArchetypeRegistry
										.isChestGridCompatible(teModId, tePath)) {
									System.out.println("[...][handleOpenWindow] skipped chest-grid for non-grid mod teModId="
											+ teModId + " block=" + teBlockLoc + " — falling through to MenuType branch");
									skipChestGrid = true;
								}
							}
							if (!skipChestGrid) {
						// [Agent Note] GENERAL — for chests with >54 slots (IronChests
						// gold/diamond/crystal/obsidian, etc.) use the multi-column
						// ContainerChestWide + GuiChestWide so the GUI lays out side-by-side
						// panels instead of a single very tall column.
						// [Agent Note] GENERAL — double-chest support (Problem B "double chest
						// opens as two windows"). The server opens a double chest as ONE
						// S2DPacketOpenWindow carrying the COMBINED slot count (two dirt chests
						// = 54, two iron chests = 108) and a chest guiId. The TileEntity under the
						// cursor is only ONE half, so binding the GUI to that single half would
						// desync from the server and make the two halves behave as two independent
						// inventories. Reconstruct the vanilla-style InventoryLargeChest by merging
						// this half with its adjacent same-chestType neighbor, then size the
						// container/GUI from the combined count. This is fully general: it relies
						// only on TileEntityChest adjacency + getSizeInventory, so it works for any
						// chest mod (IronChests, Quark, ...) with zero per-mod code.
						net.minecraft.tileentity.TileEntityChest adjacent = null;
						dte.checkForAdjacentChests();
						if (dte.adjacentChestXPos instanceof net.lax1dude.eaglercraft.v1_8.forge.DynamicChestTileEntity) {
							adjacent = dte.adjacentChestXPos;
						} else if (dte.adjacentChestZPos instanceof net.lax1dude.eaglercraft.v1_8.forge.DynamicChestTileEntity) {
							adjacent = dte.adjacentChestZPos;
						} else if (dte.adjacentChestXNeg instanceof net.lax1dude.eaglercraft.v1_8.forge.DynamicChestTileEntity) {
							adjacent = dte.adjacentChestXNeg;
						} else if (dte.adjacentChestZNeg instanceof net.lax1dude.eaglercraft.v1_8.forge.DynamicChestTileEntity) {
							adjacent = dte.adjacentChestZNeg;
						}
						net.minecraft.inventory.IInventory chestInventory = dte;
						if (adjacent != null) {
							chestInventory = new net.minecraft.inventory.InventoryLargeChest(
									"container.chestDouble", dte, adjacent);
						}
						int dteSlotCount = chestInventory.getSizeInventory();
						if (entityplayersp.openContainer != null
								&& entityplayersp.openContainer != entityplayersp.inventoryContainer) {
							entityplayersp.openContainer.onContainerClosed(entityplayersp);
							// [Agent Note] GENERAL FIX — Bug 4 (GUI contamination).
							// Clear screen overlays and reset GL state from the previous
							// GUI session. Without this, overlays registered by chest A
							// persist into chest B's render, causing color/layout bleed.
							// Applies to ALL mod chests, not just IronChests.
							net.lax1dude.eaglercraft.v1_8.forge.ScreenOverlayRegistry.clear();
							GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
						}
						// [Agent Note] GENERAL — render the mod chest as a SINGLE grid of
						// columns x rows (the mod's REAL layout), not as side-by-side 9-wide
						// panels. The old ContainerChestWide split every >54-slot chest into
						// stacked 9-wide panels, which distorted 12-wide diamond/crystal/
						// obsidian chests and 9x9 gold chests (the reported "double chest"
						// distortion). Column count comes from the chest profile (resolved
						// from the tier convention: 12 for wide-tier, else 9). This single
						// path handles <=54, >54, wide, tall, and merged (double) chests for
						// ANY mod with zero per-mod code.
						int gridColumns = 9;
						net.lax1dude.eaglercraft.v1_8.forge.DynamicChestProfileRegistry.ChestProfile chestProfile =
								dte.getChestProfile();
						net.minecraft.util.ResourceLocation guiTexture = null;
						int guiXSize = 0;
						int guiYSize = 0;
						if (chestProfile != null) {
							if (chestProfile.getColumns() > 0) {
								gridColumns = chestProfile.getColumns();
							}
							// [Agent Note] GENERAL — use the MOD's own GUI texture so the
							// chest renders with the mod's real look, not a brown/generic box.
							guiTexture = chestProfile.getGuiTexture();
							guiXSize = chestProfile.getGuiXSize();
							guiYSize = chestProfile.getGuiYSize();
						}
						net.lax1dude.eaglercraft.v1_8.forge.ContainerChestGrid gridContainer =
								new net.lax1dude.eaglercraft.v1_8.forge.ContainerChestGrid(
										entityplayersp.inventory, chestInventory, entityplayersp, dteSlotCount, gridColumns,
										guiTexture, guiXSize, guiYSize);
						gridContainer.windowId = windowId;
						entityplayersp.openContainer = gridContainer;
						net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
								new net.lax1dude.eaglercraft.v1_8.forge.GuiChestGrid(
										gridContainer, entityplayersp.inventory, chestInventory, gridColumns, dteSlotCount,
										guiTexture, guiXSize, guiYSize));
						lastOpenedScreenTime = System.currentTimeMillis();
						System.out.println("[...][handleOpenWindow] opened GENERAL DynamicChestTileEntity GUI guiId=" + guiId
								+ " windowId=" + windowId + " slots=" + dteSlotCount + " cols=" + gridColumns
								+ " rows=" + ((dteSlotCount + gridColumns - 1) / gridColumns)
								+ " guiTexture=" + guiTexture);
							return;
							} // close if (!skipChestGrid)
						}
					} catch (Throwable t) {
					logger.error("Failed to open dynamic chest TileEntity GUI guiId={}", guiId, t);
				}
		}

		// [Agent Note] General compatibility — check for dynamically registered MenuTypes
		// first. This handles mod containers that use MenuType.register() or
		// MenuType.autoRegisterForBlock(). The GUI ID matches the MenuType registry name
		// (e.g., "waystones:waystone", "waystones:sandy_waystone").
		//
		// [Agent Note] GENERAL FIX — Bug 3. The position parsing (|x,y,z) is now done
		// at the TOP of handleOpenWindow, before any guiId comparisons. guiId has
		// already been cleaned (position stripped) and bx/by/bz/hasEncodedPos are
		// already set. guiIdRaw is kept as an alias for compatibility with the
		// MenuType resolution code below.
		String guiIdRaw = guiId;
		// [Agent Note 2026-08-09 GAP-C] GENERAL — reverse the namespace-stable
		// short-name transport form. When a mod's real registry name exceeds the
		// guiId transport budget, EntityPlayerMP.openMenu sends
		// "<namespace>:<20-char stable hash>" (MenuNameCodec.encode). Resolving it
		// here restores the TRUE registry name before any MenuType / descriptor /
		// texture lookup, so long-named menus route to their own GUI instead of a
		// generic chest. No-op for names that travelled verbatim (the common case).
		String guiIdResolved = net.lax1dude.eaglercraft.v1_8.forge.MenuNameCodec.resolve(guiIdRaw);
		if (guiIdResolved != null && !guiIdResolved.equals(guiIdRaw)) {
			System.out.println("[...][handleOpenWindow] resolved short guiId " + guiIdRaw + " -> " + guiIdResolved);
			guiIdRaw = guiIdResolved;
			guiId = guiIdResolved;
		}
		// [Agent Note] General compatibility — resolve SELECTION/SETTINGS MenuType variant.
		// Many mods register multiple MenuTypes per block (e.g., waystones:waystone_selection
		// for the teleport UI). The 32-char protocol limit truncates these names, losing
		// the _selection suffix. We use BlockMenuTypeRouter to find the SELECTION variant
		// so the mod's ORIGINAL screen factory is used instead of a generic fallback.
		// This is GENERAL: works for ANY mod that registers SELECTION/SETTINGS MenuTypes.
		String resolvedMenuName = guiIdRaw;
		net.lax1dude.eaglercraft.v1_8.forge.MenuType<?> dynamicMenuType =
				net.lax1dude.eaglercraft.v1_8.forge.MenuType.get(guiIdRaw);
		try {
			String selectionName = net.lax1dude.eaglercraft.v1_8.forge.BlockMenuTypeRouter.getSelectionMenuType(
					new net.minecraft.util.ResourceLocation(guiIdRaw));
			if (selectionName == null) {
				selectionName = net.lax1dude.eaglercraft.v1_8.forge.BlockMenuTypeRouter.getSettingsMenuType(
						new net.minecraft.util.ResourceLocation(guiIdRaw));
			}
			if (selectionName != null) {
				net.lax1dude.eaglercraft.v1_8.forge.MenuType<?> selectionType =
						net.lax1dude.eaglercraft.v1_8.forge.MenuType.get(selectionName);
				if (selectionType != null) {
					resolvedMenuName = selectionName;
					dynamicMenuType = selectionType;
					System.out.println("[...][handleOpenWindow] Resolved SELECTION MenuType: " + guiIdRaw + " -> " + selectionName);
				}
			}
		} catch (Throwable t) {
			// Ignore — fall back to exact name
		}
		if (dynamicMenuType != null) {
			// [Agent Note] General compatibility — if the custom OMNIMOD|OpenScreen path
			// already opened a screen for this windowId, do NOT re-open a vanilla/generic
			// GUI on top of it. Otherwise the custom screen (e.g. chest with correct
			// slot count/rows) flashes and is instantly replaced by a generic chest GUI.
			if (net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat.consumeCustomScreenOpenedRecently(windowId)) {
				System.out.println("[...][handleOpenWindow] windowId=" + windowId
						+ " already opened via custom OMNIMOD|OpenScreen path — skipping vanilla re-open");
				return;
			}
			try {
				net.minecraft.nbt.NBTTagCompound extraData = null;
				if (hasEncodedPos) {
					extraData = new net.minecraft.nbt.NBTTagCompound();
					extraData.setInteger("BlockPosX", bx);
					extraData.setInteger("BlockPosY", by);
					extraData.setInteger("BlockPosZ", bz);
				}
				net.minecraft.inventory.Container container = dynamicMenuType.create(
						windowId, entityplayersp.inventory,
						this.clientWorldController, extraData);
				if (container != null) {
					if (entityplayersp.openContainer != null
							&& entityplayersp.openContainer != entityplayersp.inventoryContainer) {
						entityplayersp.openContainer.onContainerClosed(entityplayersp);
						// [Agent Note] GENERAL FIX — Bug 4 (GUI contamination).
						// Same cleanup as the chest-grid path: clear overlays + GL state
						// from the previous GUI before opening the new one via MenuType.
						net.lax1dude.eaglercraft.v1_8.forge.ScreenOverlayRegistry.clear();
						GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
					}
					container.windowId = windowId;
					entityplayersp.openContainer = container;
					if (entityplayersp instanceof net.minecraft.inventory.ICrafting) {
						container.onCraftGuiOpened((net.minecraft.inventory.ICrafting) entityplayersp);
					}
					net.lax1dude.eaglercraft.v1_8.forge.ForgeHooks.onPlayerContainerOpen(entityplayersp, container);

				// [Agent Note] GENERAL declarative-screen bridge. Mods that show an entity
				// preview + interactive controls (character/pet/mob viewers, size/scaling mods)
				// cannot run their own Screen class in OmniMod. Instead a general
				// ScreenDescriptor may be registered (from the runtime manifest) for this menu
				// name; if so we render it with the generic GuiModComposite. This is 100%
				// data-driven — no mod-specific code — and takes precedence over the plain
				// screen-factory fallbacks below so declared UIs always win.
				net.lax1dude.eaglercraft.v1_8.forge.gui.ScreenDescriptor screenDescriptor =
						net.lax1dude.eaglercraft.v1_8.forge.gui.ModScreenRegistry.get(resolvedMenuName);
				if (screenDescriptor == null && !resolvedMenuName.equals(guiIdRaw)) {
					screenDescriptor = net.lax1dude.eaglercraft.v1_8.forge.gui.ModScreenRegistry.get(guiIdRaw);
				}
				if (screenDescriptor != null) {
					// GENERAL open-gate: some mods only permit opening while sneaking and show a
					// guidance message otherwise. Enforced here declaratively for any mod. If
					// gated, acknowledge+close the just-opened container to stay in sync, then
					// show the guidance instead of the window.
					if (screenDescriptor.requireSneak && !entityplayersp.isSneaking()) {
						if (screenDescriptor.guidanceMessage != null) {
							entityplayersp.addChatComponentMessage(new net.minecraft.util.ChatComponentText(
									net.minecraft.client.resources.I18n.format(screenDescriptor.guidanceMessage)));
						}
						entityplayersp.sendQueue.addToSendQueue(
								new net.minecraft.network.play.client.C0DPacketCloseWindow(windowId));
						lastOpenedScreenTime = System.currentTimeMillis();
						return;
					}
					net.minecraft.client.gui.GuiScreen descriptorScreen =
							net.lax1dude.eaglercraft.v1_8.forge.gui.GuiModComposite.create(
									container, entityplayersp, resolvedMenuName, screenDescriptor);
					if (descriptorScreen != null) {
						net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(descriptorScreen);
						lastOpenedScreenTime = System.currentTimeMillis();
						System.out.println("[...][NetworkHooksCompat] handleOpenWindow: declarative GuiModComposite opened guiId=" + guiId + " resolved=" + resolvedMenuName + " windowId=" + windowId);
						return;
					}
				}

				// [Agent Note 2026-08-01] GENERAL JSON declarative GUI — before the
				// generic screen-factory / chest fallbacks, try rendering the mod's OWN
				// declared control layout from assets/<modid>/gui/*.json. This is the
				// primary UI pattern for mods (e.g. WebDisplays) that ship JSON layouts
				// (labels, buttons, text fields, checkboxes, lists, control groups,
				// icons) instead of MenuType+AbstractContainerScreen. Fully data-driven
				// and mod-agnostic; never hardcodes a mod id.
				net.minecraft.client.gui.GuiScreen jsonGuiScreen =
						net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat.tryOpenJsonGui(
								container, entityplayersp, guiIdRaw, resolvedMenuName);
				if (jsonGuiScreen != null) {
					net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(jsonGuiScreen);
					lastOpenedScreenTime = System.currentTimeMillis();
					System.out.println("[...][NetworkHooksCompat] handleOpenWindow: JSON GUI opened guiId=" + guiId + " resolved=" + resolvedMenuName + " windowId=" + windowId);
					return;
				}

				@SuppressWarnings("rawtypes")
				net.lax1dude.eaglercraft.v1_8.forge.MenuType.IScreenFactory screenFactory =
						net.lax1dude.eaglercraft.v1_8.forge.MenuType.getScreenFactory(resolvedMenuName);
				if (screenFactory != null) {
					net.minecraft.client.gui.GuiScreen screen =
							(net.minecraft.client.gui.GuiScreen) screenFactory.create(container, entityplayersp);
					if (screen != null) {
						net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(screen);
						lastOpenedScreenTime = System.currentTimeMillis();
						System.out.println("[...][NetworkHooksCompat] handleOpenWindow: dynamic MenuType screen opened guiId=" + guiId + " resolved=" + resolvedMenuName + " windowId=" + windowId);
						return;
					}
				}
				// Fallback: try exact name screen factory if resolved name didn't have one
				if (!resolvedMenuName.equals(guiIdRaw)) {
					screenFactory = net.lax1dude.eaglercraft.v1_8.forge.MenuType.getScreenFactory(guiIdRaw);
					if (screenFactory != null) {
						net.minecraft.client.gui.GuiScreen screen =
								(net.minecraft.client.gui.GuiScreen) screenFactory.create(container, entityplayersp);
						if (screen != null) {
							net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(screen);
							lastOpenedScreenTime = System.currentTimeMillis();
							System.out.println("[...][NetworkHooksCompat] handleOpenWindow: exact name screen opened guiId=" + guiId + " windowId=" + windowId);
							return;
						}
					}
				}
				// Fallback: open via the GENERAL grid renderer (no brown box, no
				// generic_54.png clipping). For 9-wide vanilla chests this renders
				// identically to GuiChest; for mod-sized chests it scales correctly.
				lastOpenedScreenTime = System.currentTimeMillis();
				openGeneralGridChest(entityplayersp, windowId, title, slotCount, 9, null, 0, 0);
				System.out.println("[...][NetworkHooksCompat] handleOpenWindow: dynamic MenuType fallback grid chest guiId=" + guiId + " windowId=" + windowId);
				return;
				}
			} catch (Throwable t) {
				logger.error("Failed to open dynamic MenuType container guiId={}", guiId, t);
			}
		}

		if ("minecraft:container".equals(guiId)) {
			// [Agent Note] GENERAL — route through the grid renderer so any size renders
			// correctly (avoids vanilla GuiChest's generic_54.png clipping for >54-slot
			// mod chests that arrive via this generic fallback).
			lastOpenedScreenTime = System.currentTimeMillis();
			openGeneralGridChest(entityplayersp, windowId, title, slotCount, 9, null, 0, 0);
		} else if ("minecraft:villager".equals(guiId)) {
			lastOpenedScreenTime = System.currentTimeMillis();
			entityplayersp.displayVillagerTradeGui(new NpcMerchant(entityplayersp, title));
			entityplayersp.openContainer.windowId = windowId;
		} else if ("EntityHorse".equals(guiId)) {
			Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityId());
			if (entity instanceof EntityHorse) {
				lastOpenedScreenTime = System.currentTimeMillis();
				entityplayersp.displayGUIHorse((EntityHorse) entity,
						new AnimalChest(title, slotCount));
				entityplayersp.openContainer.windowId = windowId;
			}
		} else if (!packetIn.hasSlots()) {
			lastOpenedScreenTime = System.currentTimeMillis();
			entityplayersp.displayGui(new LocalBlockIntercommunication(guiId, title));
			entityplayersp.openContainer.windowId = windowId;
		} else {
			lastOpenedScreenTime = System.currentTimeMillis();
			entityplayersp.displayGUIChest(new ContainerLocalMenu(guiId, title, slotCount));
			entityplayersp.openContainer.windowId = windowId;
		}
	}

	/**
	 * [Agent Note] GENERAL — open a chest-sized container through the grid
	 * renderer. Used by the generic fallbacks (minecraft:container and the
	 * MenuType-no-screen-factory path) so a mod chest arriving via either path
	 * renders correctly (the previous code went through displayGUIChest ->
	 * InventoryBasic -> vanilla GuiChest, which clips any non-9-wide or >54-slot
	 * mod chest via the generic_54.png texture).
	 *
	 * Parameters:
	 *  - columns: grid column count (9 for vanilla chests; 12 for wide-tier mods).
	 *  - guiTexture/guiXSize/guiYSize: optional mod screen texture (null/0 = use
	 *    the vanilla chest skin fallback, correctly sized).
	 */
	private void openGeneralGridChest(net.minecraft.client.entity.EntityPlayerSP entityplayersp,
			int windowId, net.minecraft.util.IChatComponent title, int slotCount, int columns,
			net.minecraft.util.ResourceLocation guiTexture, int guiXSize, int guiYSize) {
		int safeColumns = columns > 0 ? columns : 9;
		// [Agent Note] GENERAL — S2DPacketOpenWindow slotCount INCLUDES the 36
		// vanilla player-inventory slots. The grid container needs ONLY the
		// chest's own slot count (e.g. iron=54, gold=81, diamond=108), so we
		// subtract the standard 36 here. Anything <36 falls back to the raw
		// packet count (no-op subtraction). Reusable for any mod chest arriving
		// through the generic path.
		int chestSlotCount = slotCount - 36;
		if (chestSlotCount < 0) {
			chestSlotCount = slotCount;
		}
		// Generic placeholder inventory — real contents sync via handleSetSlot /
		// handleWindowItems for any container that actually backs this window.
		net.minecraft.inventory.InventoryBasic placeholder =
				new net.minecraft.inventory.InventoryBasic(title, slotCount);
		net.lax1dude.eaglercraft.v1_8.forge.ContainerChestGrid gridContainer =
				new net.lax1dude.eaglercraft.v1_8.forge.ContainerChestGrid(
						entityplayersp.inventory, placeholder, entityplayersp, chestSlotCount, safeColumns,
						guiTexture, guiXSize, guiYSize);
		gridContainer.windowId = windowId;
		entityplayersp.openContainer = gridContainer;
		net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(
				new net.lax1dude.eaglercraft.v1_8.forge.GuiChestGrid(
						gridContainer, entityplayersp.inventory, placeholder, safeColumns, chestSlotCount,
						guiTexture, guiXSize, guiYSize));
	}

	public void handleSetSlot(S2FPacketSetSlot packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		if (packetIn.func_149175_c() == -1) {
			entityplayersp.inventory.setItemStack(packetIn.func_149174_e());
		} else {
			boolean flag = false;
			if (this.gameController.currentScreen instanceof GuiContainerCreative) {
				GuiContainerCreative guicontainercreative = (GuiContainerCreative) this.gameController.currentScreen;
				flag = guicontainercreative.getSelectedTabIndex() != CreativeTabs.tabInventory.getTabIndex();
			}

			if (packetIn.func_149175_c() == 0 && packetIn.func_149173_d() >= 36 && packetIn.func_149173_d() < 45) {
				ItemStack itemstack = entityplayersp.inventoryContainer.getSlot(packetIn.func_149173_d()).getStack();
				if (packetIn.func_149174_e() != null
						&& (itemstack == null || itemstack.stackSize < packetIn.func_149174_e().stackSize)) {
					packetIn.func_149174_e().animationsToGo = 5;
				}

				entityplayersp.inventoryContainer.putStackInSlot(packetIn.func_149173_d(), packetIn.func_149174_e());
			} else if (packetIn.func_149175_c() == entityplayersp.openContainer.windowId
					&& (packetIn.func_149175_c() != 0 || !flag)) {
				entityplayersp.openContainer.putStackInSlot(packetIn.func_149173_d(), packetIn.func_149174_e());
			}
		}

	}

	public void handleConfirmTransaction(S32PacketConfirmTransaction packetIn) {
		Container container = null;
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		if (packetIn.getWindowId() == 0) {
			container = entityplayersp.inventoryContainer;
		} else if (packetIn.getWindowId() == entityplayersp.openContainer.windowId) {
			container = entityplayersp.openContainer;
		}

		if (container != null && !packetIn.func_148888_e()) {
			this.addToSendQueue(
					new C0FPacketConfirmTransaction(packetIn.getWindowId(), packetIn.getActionNumber(), true));
		}

	}

	public void handleWindowItems(S30PacketWindowItems packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		if (packetIn.func_148911_c() == 0) {
			entityplayersp.inventoryContainer.putStacksInSlots(packetIn.getItemStacks());
		} else if (packetIn.func_148911_c() == entityplayersp.openContainer.windowId) {
			entityplayersp.openContainer.putStacksInSlots(packetIn.getItemStacks());
		}

	}

	public void handleSignEditorOpen(S36PacketSignEditorOpen packetIn) {
		Object object = this.clientWorldController.getTileEntity(packetIn.getSignPosition());
		if (!(object instanceof TileEntitySign)) {
			object = new TileEntitySign();
			((TileEntity) object).setWorldObj(this.clientWorldController);
			((TileEntity) object).setPos(packetIn.getSignPosition());
		}

		this.gameController.thePlayer.openEditSign((TileEntitySign) object);
	}

	public void handleUpdateSign(S33PacketUpdateSign packetIn) {
		boolean flag = false;
		if (this.gameController.theWorld.isBlockLoaded(packetIn.getPos())) {
			TileEntity tileentity = this.gameController.theWorld.getTileEntity(packetIn.getPos());
			if (tileentity instanceof TileEntitySign) {
				TileEntitySign tileentitysign = (TileEntitySign) tileentity;
				if (tileentitysign.getIsEditable()) {
					System.arraycopy(packetIn.getLines(), 0, tileentitysign.signText, 0, 4);
					tileentitysign.markDirty();
					tileentitysign.clearProfanityFilterCache();
				}

				flag = true;
			}
		}

		if (!flag && this.gameController.thePlayer != null) {
			this.gameController.thePlayer.addChatMessage(new ChatComponentText("Unable to locate sign at "
					+ packetIn.getPos().getX() + ", " + packetIn.getPos().getY() + ", " + packetIn.getPos().getZ()));
		}

	}

	public void handleUpdateTileEntity(S35PacketUpdateTileEntity packetIn) {
		if (this.gameController.theWorld.isBlockLoaded(packetIn.getPos())) {
			TileEntity tileentity = this.gameController.theWorld.getTileEntity(packetIn.getPos());
			if (tileentity != null
					&& net.lax1dude.eaglercraft.v1_8.forge.BlockEntitySyncManager.handleUpdatePacket(
							tileentity, packetIn.getNbtCompound())) {
				return;
			}
			int i = packetIn.getTileEntityType();
			if (i == 1 && tileentity instanceof TileEntityMobSpawner
					|| i == 2 && tileentity instanceof TileEntityCommandBlock
					|| i == 3 && tileentity instanceof TileEntityBeacon
					|| i == 4 && tileentity instanceof TileEntitySkull
					|| i == 5 && tileentity instanceof TileEntityFlowerPot
					|| i == 6 && tileentity instanceof TileEntityBanner) {
				tileentity.readFromNBT(packetIn.getNbtCompound());
			}
		}

	}

	public void handleWindowProperty(S31PacketWindowProperty packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		if (entityplayersp.openContainer != null && entityplayersp.openContainer.windowId == packetIn.getWindowId()) {
			entityplayersp.openContainer.updateProgressBar(packetIn.getVarIndex(), packetIn.getVarValue());
		}

	}

	public void handleEntityEquipment(S04PacketEntityEquipment packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityID());
		if (entity != null) {
			entity.setCurrentItemOrArmor(packetIn.getEquipmentSlot(), packetIn.getItemStack());
		}

	}

	public void handleCloseWindow(S2EPacketCloseWindow packetIn) {
		this.gameController.thePlayer.closeScreenAndDropStack();
	}

	public void handleBlockAction(S24PacketBlockAction packetIn) {
		this.gameController.theWorld.addBlockEvent(packetIn.getBlockPosition(), packetIn.getBlockType(),
				packetIn.getData1(), packetIn.getData2());
	}

	public void handleBlockBreakAnim(S25PacketBlockBreakAnim packetIn) {
		this.gameController.theWorld.sendBlockBreakProgress(packetIn.getBreakerId(), packetIn.getPosition(),
				packetIn.getProgress());
	}

	public void handleMapChunkBulk(S26PacketMapChunkBulk packetIn) {
		for (int i = 0; i < packetIn.getChunkCount(); ++i) {
			int j = packetIn.getChunkX(i);
			int k = packetIn.getChunkZ(i);
			this.clientWorldController.doPreChunk(j, k, true);
			this.clientWorldController.invalidateBlockReceiveRegion(j << 4, 0, k << 4, (j << 4) + 15, 256,
					(k << 4) + 15);
			Chunk chunk = this.clientWorldController.getChunkFromChunkCoords(j, k);
			chunk.fillChunk(packetIn.getChunkBytes(i), packetIn.getChunkSize(i), true);
			this.clientWorldController.markBlockRangeForRenderUpdate(j << 4, 0, k << 4, (j << 4) + 15, 256,
					(k << 4) + 15);
			if (!(this.clientWorldController.provider instanceof WorldProviderSurface)) {
				chunk.resetRelightChecks();
			}
		}

	}

	public void handleChangeGameState(S2BPacketChangeGameState packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		int i = packetIn.getGameState();
		float f = packetIn.func_149137_d();
		int j = MathHelper.floor_float(f + 0.5F);
		if (i >= 0 && i < S2BPacketChangeGameState.MESSAGE_NAMES.length
				&& S2BPacketChangeGameState.MESSAGE_NAMES[i] != null) {
			entityplayersp.addChatComponentMessage(
					new ChatComponentTranslation(S2BPacketChangeGameState.MESSAGE_NAMES[i], new Object[0]));
		}

		if (i == 1) {
			this.clientWorldController.getWorldInfo().setRaining(true);
			this.clientWorldController.setRainStrength(0.0F);
		} else if (i == 2) {
			this.clientWorldController.getWorldInfo().setRaining(false);
			this.clientWorldController.setRainStrength(1.0F);
		} else if (i == 3) {
			this.gameController.playerController.setGameType(WorldSettings.GameType.getByID(j));
		} else if (i == 4) {
			this.gameController.displayGuiScreen(new GuiWinGame());
		} else if (i == 5) {

			// minecraft demo screen

		} else if (i == 6) {
			this.clientWorldController.playSound(entityplayersp.posX,
					entityplayersp.posY + (double) entityplayersp.getEyeHeight(), entityplayersp.posZ,
					"random.successful_hit", 0.18F, 0.45F, false);
		} else if (i == 7) {
			this.clientWorldController.setRainStrength(f);
		} else if (i == 8) {
			this.clientWorldController.setThunderStrength(f);
		} else if (i == 10) {
			this.clientWorldController.spawnParticle(EnumParticleTypes.MOB_APPEARANCE, entityplayersp.posX,
					entityplayersp.posY, entityplayersp.posZ, 0.0D, 0.0D, 0.0D, new int[0]);
			this.clientWorldController.playSound(entityplayersp.posX, entityplayersp.posY, entityplayersp.posZ,
					"mob.guardian.curse", 1.0F, 1.0F, false);
		}

	}

	public void handleMaps(S34PacketMaps packetIn) {
		MapData mapdata = ItemMap.loadMapData(packetIn.getMapId(), this.gameController.theWorld);
		packetIn.setMapdataTo(mapdata);
		this.gameController.entityRenderer.getMapItemRenderer().updateMapTexture(mapdata);
	}

	public void handleEffect(S28PacketEffect packetIn) {
		if (packetIn.isSoundServerwide()) {
			this.gameController.theWorld.playBroadcastSound(packetIn.getSoundType(), packetIn.getSoundPos(),
					packetIn.getSoundData());
		} else {
			this.gameController.theWorld.playAuxSFX(packetIn.getSoundType(), packetIn.getSoundPos(),
					packetIn.getSoundData());
		}

	}

	public void handleStatistics(S37PacketStatistics packetIn) {
		boolean flag = false;

		for (ObjectIntCursor<StatBase> entry : packetIn.func_148974_c()) {
			StatBase statbase = entry.key;
			int i = entry.value;
			if (statbase.isAchievement() && i > 0) {
				if (this.field_147308_k && this.gameController.thePlayer.getStatFileWriter().readStat(statbase) == 0) {
					Achievement achievement = (Achievement) statbase;
					this.gameController.guiAchievement.displayAchievement(achievement);
					if (statbase == AchievementList.openInventory) {
						this.gameController.gameSettings.showInventoryAchievementHint = false;
						this.gameController.gameSettings.saveOptions();
					}
				}

				flag = true;
			}

			this.gameController.thePlayer.getStatFileWriter().unlockAchievement(this.gameController.thePlayer, statbase,
					i);
		}

		if (!this.field_147308_k && !flag && this.gameController.gameSettings.showInventoryAchievementHint) {
			this.gameController.guiAchievement.displayUnformattedAchievement(AchievementList.openInventory);
		}

		this.field_147308_k = true;
		if (this.gameController.currentScreen instanceof IProgressMeter) {
			((IProgressMeter) this.gameController.currentScreen).doneLoading();
		}

	}

	public void handleEntityEffect(S1DPacketEntityEffect packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityId());
		if (entity instanceof EntityLivingBase) {
			PotionEffect potioneffect = new PotionEffect(packetIn.getEffectId(), packetIn.getDuration(),
					packetIn.getAmplifier(), false, packetIn.func_179707_f());
			potioneffect.setPotionDurationMax(packetIn.func_149429_c());
			((EntityLivingBase) entity).addPotionEffect(potioneffect);
		}
	}

	public void handleCombatEvent(S42PacketCombatEvent packetIn) {

		// used by twitch stream

	}

	public void handleServerDifficulty(S41PacketServerDifficulty packetIn) {
		this.gameController.theWorld.getWorldInfo().setDifficulty(packetIn.getDifficulty());
		this.gameController.theWorld.getWorldInfo().setDifficultyLocked(packetIn.isDifficultyLocked());
	}

	public void handleCamera(S43PacketCamera packetIn) {

		Entity entity = packetIn.getEntity(this.clientWorldController);
		if (entity != null) {
			this.gameController.setRenderViewEntity(entity);
		}

	}

	public void handleWorldBorder(S44PacketWorldBorder packetIn) {
		packetIn.func_179788_a(this.clientWorldController.getWorldBorder());
	}

	public void handleTitle(S45PacketTitle packetIn) {
		S45PacketTitle.Type s45packettitle$type = packetIn.getType();
		String s = null;
		String s1 = null;
		String s2 = packetIn.getMessage() != null ? packetIn.getMessage().getFormattedText() : "";
		switch (s45packettitle$type) {
		case TITLE:
			s = s2;
			break;
		case SUBTITLE:
			s1 = s2;
			break;
		case RESET:
			this.gameController.ingameGUI.displayTitle("", "", -1, -1, -1);
			this.gameController.ingameGUI.func_175177_a();
			return;
		}

		this.gameController.ingameGUI.displayTitle(s, s1, packetIn.getFadeInTime(), packetIn.getDisplayTime(),
				packetIn.getFadeOutTime());
	}

	public void handleSetCompressionLevel(S46PacketSetCompressionLevel packetIn) {
		if (!this.netManager.isLocalChannel()) {
			this.netManager.setCompressionTreshold(packetIn.func_179760_a());
		}

	}

	public void handlePlayerListHeaderFooter(S47PacketPlayerListHeaderFooter packetIn) {
		this.gameController.ingameGUI.getTabList()
				.setHeader(packetIn.getHeader().getFormattedText().length() == 0 ? null : packetIn.getHeader());
		this.gameController.ingameGUI.getTabList()
				.setFooter(packetIn.getFooter().getFormattedText().length() == 0 ? null : packetIn.getFooter());
	}

	public void handleRemoveEntityEffect(S1EPacketRemoveEntityEffect packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityId());
		if (entity instanceof EntityLivingBase) {
			((EntityLivingBase) entity).removePotionEffectClient(packetIn.getEffectId());
		}

	}

	public void handlePlayerListItem(S38PacketPlayerListItem packetIn) {
		List<AddPlayerData> lst = packetIn.func_179767_a();
		for (int i = 0, l = lst.size(); i < l; ++i) {
			S38PacketPlayerListItem.AddPlayerData s38packetplayerlistitem$addplayerdata = lst.get(i);
			if (packetIn.func_179768_b() == S38PacketPlayerListItem.Action.REMOVE_PLAYER) {
				EaglercraftUUID uuid = s38packetplayerlistitem$addplayerdata.getProfile().getId();
				this.playerInfoMap.remove(uuid);
				this.textureCache.evictPlayer(uuid);
				ClientUUIDLoadingCache.evict(uuid);
			} else {
				NetworkPlayerInfo networkplayerinfo = (NetworkPlayerInfo) this.playerInfoMap
						.get(s38packetplayerlistitem$addplayerdata.getProfile().getId());
				if (packetIn.func_179768_b() == S38PacketPlayerListItem.Action.ADD_PLAYER) {
					networkplayerinfo = new NetworkPlayerInfo(s38packetplayerlistitem$addplayerdata);
					this.playerInfoMap.put(networkplayerinfo.getGameProfile().getId(), networkplayerinfo);
				}

				if (networkplayerinfo != null) {
					switch (packetIn.func_179768_b()) {
					case ADD_PLAYER:
						networkplayerinfo.setGameType(s38packetplayerlistitem$addplayerdata.getGameMode());
						networkplayerinfo.setResponseTime(s38packetplayerlistitem$addplayerdata.getPing());
						break;
					case UPDATE_GAME_MODE:
						networkplayerinfo.setGameType(s38packetplayerlistitem$addplayerdata.getGameMode());
						break;
					case UPDATE_LATENCY:
						networkplayerinfo.setResponseTime(s38packetplayerlistitem$addplayerdata.getPing());
						break;
					case UPDATE_DISPLAY_NAME:
						networkplayerinfo.setDisplayName(s38packetplayerlistitem$addplayerdata.getDisplayName());
					}
				}
			}
		}

	}

	public void handleKeepAlive(S00PacketKeepAlive packetIn) {
		this.addToSendQueue(new C00PacketKeepAlive(packetIn.func_149134_c()));
	}

	public void handlePlayerAbilities(S39PacketPlayerAbilities packetIn) {
		EntityPlayerSP entityplayersp = this.gameController.thePlayer;
		entityplayersp.capabilities.isFlying = packetIn.isFlying();
		entityplayersp.capabilities.isCreativeMode = packetIn.isCreativeMode();
		entityplayersp.capabilities.disableDamage = packetIn.isInvulnerable();
		entityplayersp.capabilities.allowFlying = packetIn.isAllowFlying();
		entityplayersp.capabilities.setFlySpeed(packetIn.getFlySpeed());
		entityplayersp.capabilities.setPlayerWalkSpeed(packetIn.getWalkSpeed());
	}

	public void handleTabComplete(S3APacketTabComplete packetIn) {
		String[] astring = packetIn.func_149630_c();
		if (this.gameController.currentScreen instanceof GuiChat) {
			GuiChat guichat = (GuiChat) this.gameController.currentScreen;
			guichat.onAutocompleteResponse(astring);
		}

	}

	public void handleSoundEffect(S29PacketSoundEffect packetIn) {
		this.gameController.theWorld.playSound(packetIn.getX(), packetIn.getY(), packetIn.getZ(),
				packetIn.getSoundName(), packetIn.getVolume(), packetIn.getPitch(), false);
	}

	public void handleResourcePack(S48PacketResourcePackSend packetIn) {
		final String s = packetIn.getURL();
		final String s1 = packetIn.getHash();
		if (!EaglerFolderResourcePack.isSupported() || s.startsWith("level://")) {
			this.netManager
					.sendPacket(new C19PacketResourcePackStatus(s1, C19PacketResourcePackStatus.Action.DECLINED));
			return;
		}
		if (this.gameController.getCurrentServerData() != null && this.gameController.getCurrentServerData()
				.getResourceMode() == ServerData.ServerResourceMode.ENABLED) {
			NetHandlerPlayClient.this.netManager
					.sendPacket(new C19PacketResourcePackStatus(s1, C19PacketResourcePackStatus.Action.ACCEPTED));
			NetHandlerPlayClient.this.gameController.getResourcePackRepository().downloadResourcePack(s, s1,
					success -> {
						if (success) {
							NetHandlerPlayClient.this.netManager.sendPacket(new C19PacketResourcePackStatus(s1,
									C19PacketResourcePackStatus.Action.SUCCESSFULLY_LOADED));
						} else {
							NetHandlerPlayClient.this.netManager.sendPacket(new C19PacketResourcePackStatus(s1,
									C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD));
						}
					});
		} else if (this.gameController.getCurrentServerData() != null && this.gameController.getCurrentServerData()
				.getResourceMode() != ServerData.ServerResourceMode.PROMPT) {
			this.netManager
					.sendPacket(new C19PacketResourcePackStatus(s1, C19PacketResourcePackStatus.Action.DECLINED));
		} else {
			NetHandlerPlayClient.this.gameController.displayGuiScreen(new GuiYesNo(new GuiYesNoCallback() {
				public void confirmClicked(boolean flag, int var2) {
					NetHandlerPlayClient.this.gameController = Minecraft.getMinecraft();
					if (flag) {
						if (NetHandlerPlayClient.this.gameController.getCurrentServerData() != null) {
							NetHandlerPlayClient.this.gameController.getCurrentServerData()
									.setResourceMode(ServerData.ServerResourceMode.ENABLED);
						}

						NetHandlerPlayClient.this.netManager.sendPacket(
								new C19PacketResourcePackStatus(s1, C19PacketResourcePackStatus.Action.ACCEPTED));
						NetHandlerPlayClient.this.gameController.getResourcePackRepository().downloadResourcePack(s, s1,
								success -> {
									if (success) {
										NetHandlerPlayClient.this.netManager.sendPacket(new C19PacketResourcePackStatus(
												s1, C19PacketResourcePackStatus.Action.SUCCESSFULLY_LOADED));
									} else {
										NetHandlerPlayClient.this.netManager.sendPacket(new C19PacketResourcePackStatus(
												s1, C19PacketResourcePackStatus.Action.FAILED_DOWNLOAD));
									}
								});
					} else {
						if (NetHandlerPlayClient.this.gameController.getCurrentServerData() != null) {
							NetHandlerPlayClient.this.gameController.getCurrentServerData()
									.setResourceMode(ServerData.ServerResourceMode.DISABLED);
						}

						NetHandlerPlayClient.this.netManager.sendPacket(
								new C19PacketResourcePackStatus(s1, C19PacketResourcePackStatus.Action.DECLINED));
					}

					ServerList.func_147414_b(NetHandlerPlayClient.this.gameController.getCurrentServerData());
					NetHandlerPlayClient.this.gameController.displayGuiScreen((GuiScreen) null);
				}
			}, I18n.format("multiplayer.texturePrompt.line1", new Object[0]),
					I18n.format("multiplayer.texturePrompt.line2", new Object[0]), 0));
		}
	}

	public void handleEntityNBT(S49PacketUpdateEntityNBT packetIn) {
		Entity entity = packetIn.getEntity(this.clientWorldController);
		if (entity != null) {
			entity.clientUpdateEntityNBT(packetIn.getTagCompound());
		}

	}

	public void handleCustomPayload(S3FPacketCustomPayload packetIn) {
		if ("MC|TrList".equals(packetIn.getChannelName())) {
			PacketBuffer packetbuffer = packetIn.getBufferData();
			try {
				int i = packetbuffer.readInt();
				GuiScreen guiscreen = this.gameController.currentScreen;
				if (guiscreen != null && guiscreen instanceof GuiMerchant
						&& i == this.gameController.thePlayer.openContainer.windowId) {
					IMerchant imerchant = ((GuiMerchant) guiscreen).getMerchant();
					MerchantRecipeList merchantrecipelist = MerchantRecipeList.readFromBuf(packetbuffer);
					imerchant.setRecipes(merchantrecipelist);
				}
			} catch (IOException ioexception) {
				logger.error("Couldn\'t load trade info", ioexception);
			}
		} else if ("MC|Brand".equals(packetIn.getChannelName())) {
			this.gameController.thePlayer.setClientBrand(packetIn.getBufferData().readStringFromBuffer(32767));
		} else if ("MC|BOpen".equals(packetIn.getChannelName())) {
			ItemStack itemstack = this.gameController.thePlayer.getCurrentEquippedItem();
			if (itemstack != null && itemstack.getItem() == Items.written_book) {
				this.gameController
						.displayGuiScreen(new GuiScreenBook(this.gameController.thePlayer, itemstack, false));
			}
		} else if (PortalLifecycleRuntime.PORTAL_STATE_CHANNEL.equals(packetIn.getChannelName())) {
			try {
				if (packetIn.getBufferData() == null || packetIn.getBufferData().readableBytes() <= 0) {
					return;
				}
				int portalDim = this.gameController != null && this.gameController.thePlayer != null
						? this.gameController.thePlayer.dimension : Integer.MIN_VALUE;
				// Capture aperture bounds BEFORE applying the sync so that a portal
				// which is being REMOVED still gets its wall chunk rebuilt (otherwise
				// the cut-out hole stays permanently). Combined with the post-sync
				// bounds this covers appear, move and disappear cases.
				java.util.List<int[]> boundsBefore = portalDim != Integer.MIN_VALUE
						? PortalLifecycleRuntime.collectPortalRenderBounds(portalDim)
						: java.util.Collections.<int[]>emptyList();

				PacketBuffer portalSyncData = new PacketBuffer(packetIn.getBufferData().copy());
				portalSyncData.readerIndex(0);
				PortalLifecycleRuntime.applyDimensionSync(portalSyncData);

				// Portals just changed: rebuild the chunk meshes around every
				// aperture so the wall cut-out (RenderChunk skips portal-wall
				// blocks via PortalCollisionAdapter.isPortalWallBlock) is applied
				// immediately. Without this the stale mesh keeps the solid wall
				// visible (full-screen wall) until the chunk rebuilds for another
				// reason, and removed portals leave a permanent hole. Generic for
				// any portal type registered through PortalLifecycleRuntime.
				if (this.gameController != null && this.gameController.renderGlobal != null
						&& portalDim != Integer.MIN_VALUE) {
					rebuildPortalApertureChunks(boundsBefore);
					rebuildPortalApertureChunks(PortalLifecycleRuntime.collectPortalRenderBounds(portalDim));
				}
			} catch (Exception e) {
				logger.warn("Couldn't apply portal lifecycle sync (channel={}, readableBytes={}): {}",
						packetIn.getChannelName(), Integer.valueOf(packetIn.getBufferData().readableBytes()),
						e.getMessage());
			}
		} else if (net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat.OPEN_SCREEN_CHANNEL
				.equals(packetIn.getChannelName())) {
			try {
				PacketBuffer openScreenData = new PacketBuffer(packetIn.getBufferData().copy());
				openScreenData.readerIndex(0);
				net.lax1dude.eaglercraft.v1_8.forge.NetworkHooksCompat.handleOpenScreenPacket(openScreenData);
			} catch (Exception e) {
				logger.error("Couldn't handle dynamic open-screen packet (channel={}, readableBytes={})",
						packetIn.getChannelName(), Integer.valueOf(packetIn.getBufferData().readableBytes()), e);
			}
		} else if (net.lax1dude.eaglercraft.v1_8.forge.command.BossBarRuntime.BOSSBAR_CHANNEL
				.equals(packetIn.getChannelName())) {
			// [Agent Note 2026-09-04] GMF — custom boss bar HUD sync (1.20.1 /bossbar).
			// Same transport pattern as OMNIMOD|OpenScreen: server writes the full
			// state as UTF-8 JSON bytes into the payload buffer; the client applies it
			// to ClientBossBarRuntime (idempotent by version). Parse failures keep the
			// previous HUD state and are logged honestly (never silent, §18.2).
			try {
				PacketBuffer bossbarData = new PacketBuffer(packetIn.getBufferData().copy());
				bossbarData.readerIndex(0);
				String json = bossbarData.readStringFromBuffer(bossbarData.readableBytes());
				net.lax1dude.eaglercraft.v1_8.forge.command.ClientBossBarRuntime.applyJson(json);
			} catch (Exception e) {
				logger.warn("Couldn't apply custom boss bar sync (channel={}, readableBytes={}): {}",
						packetIn.getChannelName(), Integer.valueOf(packetIn.getBufferData().readableBytes()),
						e.getMessage());
			}
		} else if (net.lax1dude.eaglercraft.v1_8.forge.command.SoundStopBridge.STOP_CHANNEL
				.equals(packetIn.getChannelName())) {
			// [Agent Note 2026-09-04] MCBP — /stopsound client half (1.20.1
			// parity): the server sends a filtered stop on the proven OMNIMOD
			// transport; we apply it to the REAL client sound engine. Parse
			// failures are logged honestly, never silent (§18.2).
			try {
				PacketBuffer stopData = new PacketBuffer(packetIn.getBufferData().copy());
				stopData.readerIndex(0);
				String json = stopData.readStringFromBuffer(stopData.readableBytes());
				net.lax1dude.eaglercraft.v1_8.forge.command.SoundStopBridge.handleClientJson(json);
			} catch (Exception e) {
				logger.warn("Couldn't apply stop-sound packet (channel={}, readableBytes={}): {}",
						packetIn.getChannelName(), Integer.valueOf(packetIn.getBufferData().readableBytes()),
						e.getMessage());
			}
		} else if (net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.CHANNEL
				.equals(packetIn.getChannelName())) {
			// [Agent Note 2026-09-04] MAP-MODE (MAP-MODE-CLI-002) — dual-mode
			// (dev/preview) sync: the server pushes the map's mode JSON; the
			// client mirrors it and re-meshes every loaded command-block
			// chunk (ClientMapModeRuntime) so the visual flip is instant and
			// hole-free. Same proven transport as OMNIMOD|BossBar. Parse
			// failures keep the previous visual state and are logged (never
			// silent, §18.2). GENERAL for every map/mod.
			try {
				PacketBuffer mapModeData = new PacketBuffer(packetIn.getBufferData().copy());
				mapModeData.readerIndex(0);
				String mapModeJson = mapModeData.readStringFromBuffer(mapModeData.readableBytes());
				net.lax1dude.eaglercraft.v1_8.sp.ClientMapModeRuntime.applyJson(mapModeJson);
			} catch (Exception e) {
				logger.warn("Couldn't apply map-mode sync (channel={}, readableBytes={}): {}",
						packetIn.getChannelName(),
						Integer.valueOf(packetIn.getBufferData().readableBytes()),
						e.getMessage());
			}
		} else if (eaglerMessageController instanceof LegacyMessageController) {
			try {
				((LegacyMessageController) eaglerMessageController).handlePacket(packetIn.getChannelName(),
						packetIn.getBufferData());
			} catch (IOException e) {
				logger.error("Couldn't read \"{}\" packet as an eaglercraft plugin message!",
						packetIn.getChannelName());
				logger.error(e);
			}
		}
	}

	/**
	 * Force a chunk-mesh rebuild for every portal aperture box. Uses
	 * {@link RenderGlobal#markBlockForUpdate(BlockPos)} on each corner (which
	 * routes to markBlocksForUpdate directly, bypassing the time-coalescing in
	 * markBlockRangeForRenderUpdate that could otherwise drop a second aperture
	 * updated in the same tick). Generic for any portal type.
	 */
	private void rebuildPortalApertureChunks(java.util.List<int[]> bounds) {
		if (this.gameController == null || this.gameController.renderGlobal == null || bounds == null) {
			return;
		}
		for (int bi = 0, bl = bounds.size(); bi < bl; ++bi) {
			int[] b = bounds.get(bi);
			if (b == null || b.length < 6) {
				continue;
			}
			this.gameController.renderGlobal.markBlockRangeForRenderUpdate(b[0], b[1], b[2], b[3], b[4], b[5]);
			// Also stamp the corners individually so the coalescing guard in
			// markBlockRangeForRenderUpdate never suppresses a second box.
			this.gameController.renderGlobal.markBlockForUpdate(new BlockPos(b[0], b[1], b[2]));
			this.gameController.renderGlobal.markBlockForUpdate(new BlockPos(b[3], b[4], b[5]));
		}
	}

	public void handleScoreboardObjective(S3BPacketScoreboardObjective packetIn) {
		Scoreboard scoreboard = this.clientWorldController.getScoreboard();
		if (packetIn.func_149338_e() == 0) {
			ScoreObjective scoreobjective = scoreboard.addScoreObjective(packetIn.func_149339_c(),
					IScoreObjectiveCriteria.DUMMY);
			scoreobjective.setDisplayName(packetIn.func_149337_d());
			scoreobjective.setRenderType(packetIn.func_179817_d());
		} else {
			ScoreObjective scoreobjective1 = scoreboard.getObjective(packetIn.func_149339_c());
			if (packetIn.func_149338_e() == 1) {
				scoreboard.removeObjective(scoreobjective1);
			} else if (packetIn.func_149338_e() == 2) {
				scoreobjective1.setDisplayName(packetIn.func_149337_d());
				scoreobjective1.setRenderType(packetIn.func_179817_d());
			}
		}

	}

	public void handleUpdateScore(S3CPacketUpdateScore packetIn) {
		Scoreboard scoreboard = this.clientWorldController.getScoreboard();
		ScoreObjective scoreobjective = scoreboard.getObjective(packetIn.getObjectiveName());
		if (packetIn.getScoreAction() == S3CPacketUpdateScore.Action.CHANGE) {
			Score score = scoreboard.getValueFromObjective(packetIn.getPlayerName(), scoreobjective);
			score.setScorePoints(packetIn.getScoreValue());
		} else if (packetIn.getScoreAction() == S3CPacketUpdateScore.Action.REMOVE) {
			if (StringUtils.isNullOrEmpty(packetIn.getObjectiveName())) {
				scoreboard.removeObjectiveFromEntity(packetIn.getPlayerName(), (ScoreObjective) null);
			} else if (scoreobjective != null) {
				scoreboard.removeObjectiveFromEntity(packetIn.getPlayerName(), scoreobjective);
			}
		}

	}

	public void handleDisplayScoreboard(S3DPacketDisplayScoreboard packetIn) {
		Scoreboard scoreboard = this.clientWorldController.getScoreboard();
		if (packetIn.func_149370_d().length() == 0) {
			scoreboard.setObjectiveInDisplaySlot(packetIn.func_149371_c(), (ScoreObjective) null);
		} else {
			ScoreObjective scoreobjective = scoreboard.getObjective(packetIn.func_149370_d());
			scoreboard.setObjectiveInDisplaySlot(packetIn.func_149371_c(), scoreobjective);
		}

	}

	public void handleTeams(S3EPacketTeams packetIn) {
		Scoreboard scoreboard = this.clientWorldController.getScoreboard();
		ScorePlayerTeam scoreplayerteam;
		if (packetIn.func_149307_h() == 0) {
			scoreplayerteam = scoreboard.createTeam(packetIn.func_149312_c());
		} else {
			scoreplayerteam = scoreboard.getTeam(packetIn.func_149312_c());
		}

		if (packetIn.func_149307_h() == 0 || packetIn.func_149307_h() == 2) {
			scoreplayerteam.setTeamName(packetIn.func_149306_d());
			scoreplayerteam.setNamePrefix(packetIn.func_149311_e());
			scoreplayerteam.setNameSuffix(packetIn.func_149309_f());
			scoreplayerteam.setChatFormat(EnumChatFormatting.func_175744_a(packetIn.func_179813_h()));
			scoreplayerteam.func_98298_a(packetIn.func_149308_i());
			Team.EnumVisible team$enumvisible = Team.EnumVisible.func_178824_a(packetIn.func_179814_i());
			if (team$enumvisible != null) {
				scoreplayerteam.setNameTagVisibility(team$enumvisible);
			}
		}

		if (packetIn.func_149307_h() == 0 || packetIn.func_149307_h() == 3) {
			for (String s : packetIn.func_149310_g()) {
				scoreboard.addPlayerToTeam(s, packetIn.func_149312_c());
			}
		}

		if (packetIn.func_149307_h() == 4) {
			for (String s1 : packetIn.func_149310_g()) {
				scoreboard.removePlayerFromTeam(s1, scoreplayerteam);
			}
		}

		if (packetIn.func_149307_h() == 1) {
			scoreboard.removeTeam(scoreplayerteam);
		}

	}

	public void handleParticles(S2APacketParticles packetIn) {
		if (packetIn.getParticleCount() == 0) {
			double d0 = (double) (packetIn.getParticleSpeed() * packetIn.getXOffset());
			double d2 = (double) (packetIn.getParticleSpeed() * packetIn.getYOffset());
			double d4 = (double) (packetIn.getParticleSpeed() * packetIn.getZOffset());

			try {
				this.clientWorldController.spawnParticle(packetIn.getParticleType(), packetIn.isLongDistance(),
						packetIn.getXCoordinate(), packetIn.getYCoordinate(), packetIn.getZCoordinate(), d0, d2, d4,
						packetIn.getParticleArgs());
			} catch (Throwable var17) {
				logger.warn("Could not spawn particle effect " + packetIn.getParticleType());
			}
		} else {
			for (int i = 0; i < packetIn.getParticleCount(); ++i) {
				double d1 = this.avRandomizer.nextGaussian() * (double) packetIn.getXOffset();
				double d3 = this.avRandomizer.nextGaussian() * (double) packetIn.getYOffset();
				double d5 = this.avRandomizer.nextGaussian() * (double) packetIn.getZOffset();
				double d6 = this.avRandomizer.nextGaussian() * (double) packetIn.getParticleSpeed();
				double d7 = this.avRandomizer.nextGaussian() * (double) packetIn.getParticleSpeed();
				double d8 = this.avRandomizer.nextGaussian() * (double) packetIn.getParticleSpeed();

				try {
					this.clientWorldController.spawnParticle(packetIn.getParticleType(), packetIn.isLongDistance(),
							packetIn.getXCoordinate() + d1, packetIn.getYCoordinate() + d3,
							packetIn.getZCoordinate() + d5, d6, d7, d8, packetIn.getParticleArgs());
				} catch (Throwable var16) {
					logger.warn("Could not spawn particle effect " + packetIn.getParticleType());
					return;
				}
			}
		}

	}

	public void handleEntityProperties(S20PacketEntityProperties packetIn) {
		Entity entity = this.clientWorldController.getEntityByID(packetIn.getEntityId());
		if (entity != null) {
			if (!(entity instanceof EntityLivingBase)) {
				throw new IllegalStateException(
						"Server tried to update attributes of a non-living entity (actually: " + entity + ")");
			} else {
				BaseAttributeMap baseattributemap = ((EntityLivingBase) entity).getAttributeMap();

				List<Snapshot> lst = packetIn.func_149441_d();
				for (int i = 0, l = lst.size(); i < l; ++i) {
					S20PacketEntityProperties.Snapshot s20packetentityproperties$snapshot = lst.get(i);
					IAttributeInstance iattributeinstance = baseattributemap
							.getAttributeInstanceByName(s20packetentityproperties$snapshot.func_151409_a());
					if (iattributeinstance == null) {
						iattributeinstance = baseattributemap.registerAttribute(new RangedAttribute((IAttribute) null,
								s20packetentityproperties$snapshot.func_151409_a(), 0.0D, 2.2250738585072014E-308D,
								Double.MAX_VALUE));
					}

					iattributeinstance.setBaseValue(s20packetentityproperties$snapshot.func_151410_b());
					iattributeinstance.removeAllModifiers();

					for (AttributeModifier attributemodifier : s20packetentityproperties$snapshot.func_151408_c()) {
						iattributeinstance.applyModifier(attributemodifier);
					}
				}

			}
		}
	}

	public EaglercraftNetworkManager getNetworkManager() {
		return this.netManager;
	}

	public Collection<NetworkPlayerInfo> getPlayerInfoMap() {
		return this.playerInfoMap.values();
	}

	public NetworkPlayerInfo getPlayerInfo(EaglercraftUUID parUUID) {
		return (NetworkPlayerInfo) this.playerInfoMap.get(parUUID);
	}

	public NetworkPlayerInfo getPlayerInfo(String parString1) {
		for (NetworkPlayerInfo networkplayerinfo : this.playerInfoMap.values()) {
			if (networkplayerinfo.getGameProfile().getName().equals(parString1)) {
				return networkplayerinfo;
			}
		}

		return null;
	}

	public GameProfile getGameProfile() {
		return this.profile;
	}

	public boolean isClientInEaglerSingleplayerOrLAN() {
		return isIntegratedServer;
	}
}
