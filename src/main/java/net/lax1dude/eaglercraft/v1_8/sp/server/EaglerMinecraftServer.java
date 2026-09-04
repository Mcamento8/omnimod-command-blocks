/*
 * Copyright (c) 2023-2025 lax1dude, ayunami2000. All Rights Reserved.
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

package net.lax1dude.eaglercraft.v1_8.sp.server;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

import net.lax1dude.eaglercraft.v1_8.EagRuntime;
import net.lax1dude.eaglercraft.v1_8.EagUtils;
import net.lax1dude.eaglercraft.v1_8.internal.vfs2.VFile2;
import net.lax1dude.eaglercraft.v1_8.log4j.Logger;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.EnumDifficulty;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldSettings.GameType;
import net.minecraft.world.storage.WorldInfo;
import net.lax1dude.eaglercraft.v1_8.sp.server.skins.IntegratedTextureService;
import net.lax1dude.eaglercraft.v1_8.sp.server.voice.IntegratedVoiceService;

public class EaglerMinecraftServer extends MinecraftServer {

	public static final Logger logger = EaglerIntegratedServerWorker.logger;

	public static final VFile2 savesDir = WorldsDB.newVFile("worlds");

	protected EnumDifficulty difficulty;
	protected GameType gamemode;
	protected WorldSettings newWorldSettings;
	protected boolean paused;
	protected EaglerSaveHandler saveHandler;
	protected IntegratedTextureService textureService;
	protected IntegratedVoiceService voiceService;

	private long lastTPSUpdate = 0l;

	public static int counterTicksPerSecond = 0;
	public static int counterChunkRead = 0;
	public static int counterChunkGenerate = 0;
	public static int counterChunkWrite = 0;
	public static int counterTileUpdate = 0;
	public static int counterLightUpdate = 0;

	// [Agent Note 2026-08-05 v13] REMOVED dead queue: the old private
	// 'scheduledTasks' LinkedList was written by addScheduledTask() but NEVER
	// drained anywhere, so every scheduled server task was silently dropped
	// (ScreenBlockBridge.persistScreenStateOnServer, BlockBeacon.updateColorAsync,
	// any WorldServer.addScheduledTask caller). addScheduledTask now enqueues into
	// the base class 'futureTaskQueue', which MinecraftServer.updateTimeLightAndEntities
	// drains every tick. See addScheduledTask below.

	public EaglerMinecraftServer(String world, String owner, int viewDistance, WorldSettings currentWorldSettings, boolean demo) {
		super(world);
		Bootstrap.register();
		this.saveHandler = new EaglerSaveHandler(savesDir, world);
		EaglerPlayerList playerList = new EaglerPlayerList(this, viewDistance);
		this.textureService = new IntegratedTextureService(playerList,
				WorldsDB.newVFile(saveHandler.getWorldDirectory(), "eagler/skulls"));
		this.voiceService = null;
		this.setServerOwner(owner);
		logger.info("server owner: " + owner);
		this.setDemo(demo);
		this.canCreateBonusChest(currentWorldSettings != null && currentWorldSettings.isBonusChestEnabled());
		this.setBuildLimit(256);
		this.setConfigManager(playerList);
		this.newWorldSettings = currentWorldSettings;
		this.paused = false;
	}

	public IntegratedTextureService getTextureService() {
		return textureService;
	}

	public IntegratedVoiceService getVoiceService() {
		return voiceService;
	}

	public void enableVoice(String[] iceServers) {
		if(iceServers != null) {
			if(voiceService != null) {
				voiceService.changeICEServers(iceServers);
			}else {
				voiceService = new IntegratedVoiceService(iceServers);
				for(EntityPlayerMP player : getConfigurationManager().func_181057_v()) {
					voiceService.handlePlayerLoggedIn(player);
				}
			}
		}
	}

	public void setBaseServerProperties(EnumDifficulty difficulty, GameType gamemode) {
		this.difficulty = difficulty;
		this.gamemode = gamemode;
		this.setCanSpawnAnimals(true);
		this.setCanSpawnNPCs(true);
		this.setAllowPvp(true);
		this.setAllowFlight(true);
	}

	// [Agent Note 2026-08-05 v13] GENERAL ENGINE FIX — dead scheduled-task queue.
	// Root cause (proven by code + live logs): this override used to enqueue into a
	// private LinkedList that was never drained, so every task scheduled on the
	// integrated server was silently dropped. Two known victims:
	//   1. ScreenBlockBridge.persistScreenStateOnServer — screen URL/resolution/GUI
	//      state was never written to the server TileEntity NBT (live logs showed
	//      "setUrl ok" but zero "persistState" lines), so mod screen GUIs lost all
	//      edits on reopen.
	//   2. BlockBeacon.updateColorAsync (vanilla) — beacon effect updates scheduled
	//      via WorldServer.addScheduledTask never ran.
	// Fix: enqueue a FutureTask into the BASE CLASS 'futureTaskQueue', which
	// MinecraftServer.updateTimeLightAndEntities drains every server tick
	// (MinecraftServer.java:468-471). Synchronize on the queue object exactly like
	// the drain side does. GENERAL — every current and future addScheduledTask
	// caller on the integrated server now actually executes.
	@Override
	public void addScheduledTask(Runnable var1) {
		if (var1 == null) {
			return;
		}
		synchronized (this.futureTaskQueue) {
			this.futureTaskQueue.add(new net.lax1dude.eaglercraft.v1_8.futures.FutureTask<Object>(() -> {
				var1.run();
				return null;
			}));
		}
	}

	@Override
	protected boolean startServer() throws IOException {
		logger.info("Starting integrated eaglercraft server version 1.8.8");
		this.disableSpawnChunkPreloadForModdedWorld();
		serverRunning = true;
		this.loadAllWorlds(saveHandler, this.getWorldName(), newWorldSettings);
		// [OmniMod Track A — Map Builder] Agent dev workspace: ensure it exists for
		// every map, refresh the linked platform folder (if any) and apply every
		// pending external change right at world load (blue chat feedback).
		net.lax1dude.eaglercraft.v1_8.sp.MapDevSyncRuntime.onWorldLoaded(this);
		// [Agent Note 2026-09-04] MAP-MODE — reload the map's persisted
		// dev/preview mode (worlds/<map>/mapmode.json) right at world load
		// so every reload opens in the shape the developer left it.
		net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.onWorldLoaded(this);
		return true;
	}

	private void disableSpawnChunkPreloadForModdedWorld() {
		if (!hasInstalledWorldMods()) {
			return;
		}
		try {
			WorldInfo worldInfo = saveHandler.loadWorldInfo();
			if (worldInfo != null && worldInfo.getGameRulesInstance().getBoolean("loadSpawnChunks")) {
				worldInfo.getGameRulesInstance().setOrCreateGameRule("loadSpawnChunks", "false");
				saveHandler.saveWorldInfo(worldInfo);
				logger.warn("Disabled loadSpawnChunks for modded world '{}' to avoid blocking world entry before ACK.",
						getFolderName());
			}
		} catch (Throwable t) {
			logger.warn("Could not update loadSpawnChunks for modded world '{}'", getFolderName(), t);
		}
	}

	public void deleteWorldAndStopServer() {
		super.deleteWorldAndStopServer();
		logger.info("Deleting world...");
		EaglerIntegratedServerWorker.saveFormat.deleteWorldDirectory(getFolderName());
	}

	@Override
	protected void initialWorldChunkLoad() {
		if (net.lax1dude.eaglercraft.v1_8.sp.server.internal.ServerPlatformSingleplayer.isSingleThreadMode()) {
			logger.info("Skipping initial spawn chunks generation for SingleThreadMode to keep UI responsive.");
			return;
		}
		if (hasInstalledWorldMods()) {
			logger.warn("Skipping initial spawn chunks generation for modded world '{}' to avoid blocking world entry.",
					getFolderName());
			EaglerIntegratedServerWorker.sendProgress("singleplayer.busy.startingIntegratedServer", 0.98f);
			return;
		}
		super.initialWorldChunkLoad();
	}

	private boolean hasInstalledWorldMods() {
		String world = getFolderName();
		if(world == null || world.length() == 0) {
			return false;
		}
		try {
			List<String> jarMods = WorldsDB.newVFile("mods", world).listFilenames(false);
			for(int i = 0, l = jarMods.size(); i < l; ++i) {
				String name = VFile2.getNameFromPath(jarMods.get(i));
				if(name != null && name.toLowerCase().endsWith(".jar")) {
					return true;
				}
			}
		}catch(Throwable t) {
			logger.warn("Could not scan jar mods for world '{}'", world, t);
		}
		try {
			if(!WorldsDB.newVFile("mods_folders", world).listFilenames(true).isEmpty()) {
				return true;
			}
		}catch(Throwable t) {
			logger.warn("Could not scan folder mods for world '{}'", world, t);
		}
		try {
			if(!WorldsDB.newVFile("mods_translated", world).listFilenames(true).isEmpty()) {
				return true;
			}
		}catch(Throwable t) {
			logger.warn("Could not scan translated mods for world '{}'", world, t);
		}
		return false;
	}

	public void mainLoop(boolean singleThreadMode) {
		long k = getCurrentTimeMillis();
		this.sendTPSToClient(k);
		if(paused && this.playersOnline.size() <= 1) {
			currentTime = k;
			return;
		}

		long j = k - this.currentTime;
		if (j > (singleThreadMode ? 500L : 2000L) && this.currentTime - this.timeOfLastWarning >= (singleThreadMode ? 5000L : 15000L)) {
			logger.warn(
					"Can\'t keep up! Did the system time change, or is the server overloaded? Running {}ms behind, skipping {} tick(s)",
					new Object[] { Long.valueOf(j), Long.valueOf(j / 50L) });
			j = 100L;
			this.currentTime = k - 100l;
			this.timeOfLastWarning = this.currentTime;
		}

		if (j < 0L) {
			logger.warn("Time ran backwards! Did the system time change?");
			j = 0L;
			this.currentTime = k;
		}

		if (this.worldServers[0].areAllPlayersAsleep()) {
			this.currentTime = k;
			this.tick();
			++counterTicksPerSecond;
		} else {
			if (j > 50L) {
				this.currentTime += 50l;
				this.tick();
				++counterTicksPerSecond;
			} else if (!singleThreadMode) {
				EagUtils.sleep(1);
			}
		}
	}

	public void updateTimeLightAndEntities() {
		this.textureService.flushCache();
		super.updateTimeLightAndEntities();
		// [OmniMod Track A — Map Builder] Live agent-change watcher: polls
		// worlds/<map>/_dev/build every ~2s (internally cadenced, ~free on other
		// ticks) and applies new/changed agent batches with blue chat feedback.
		net.lax1dude.eaglercraft.v1_8.sp.MapDevSyncRuntime.onServerTick(this);
		// [Agent Note 2026-09-04] MAP-MODE — cadenced safety net: if the
		// mode payload was never delivered (join race, lost packet) it is
		// re-broadcast here every ~5s until a player receives it. Free
		// when idle. GENERAL.
		net.lax1dude.eaglercraft.v1_8.sp.MapModeRuntime.onServerTick(this);
	}

	protected void sendTPSToClient(long millis) {
		if(millis - lastTPSUpdate > 1000l) {
			lastTPSUpdate = millis;
			if(serverRunning && this.worldServers != null) {
				List<String> lst = Lists.newArrayList(
						"TPS: " + counterTicksPerSecond + "/20",
						"Chunks: " + countChunksLoaded(this.worldServers) + "/" + countChunksTotal(this.worldServers),
						"Entities: " + countEntities(this.worldServers) + "+" + countTileEntities(this.worldServers),
						"R: " + counterChunkRead + ", G: " + counterChunkGenerate + ", W: " + counterChunkWrite,
						"TU: " + counterTileUpdate + ", LU: " + counterLightUpdate
				);
				int players = countPlayerEntities(this.worldServers);
				if(players > 1) {
					lst.add("Players: " + players);
				}
				counterTicksPerSecond = counterChunkRead = counterChunkGenerate = 0;
				counterChunkWrite = counterTileUpdate = counterLightUpdate = 0;
				EaglerIntegratedServerWorker.reportTPS(lst);
			}
		}
	}

	private static int countChunksLoaded(WorldServer[] worlds) {
		int i = 0;
		for(int j = 0; j < worlds.length; ++j) {
			if(worlds[j] != null) {
				i += worlds[j].theChunkProviderServer.getLoadedChunkCount();
			}
		}
		return i;
	}

	private static int countChunksTotal(WorldServer[] worlds) {
		int i = 0;
		for(int j = 0; j < worlds.length; ++j) {
			if(worlds[j] != null) {
				List<EntityPlayer> players = worlds[j].playerEntities;
				for(int l = 0, n = players.size(); l < n; ++l) {
					i += ((EntityPlayerMP)players.get(l)).loadedChunks.size();
				}
				i += worlds[j].theChunkProviderServer.getLoadedChunkCount();
			}
		}
		return i;
	}

	private static int countEntities(WorldServer[] worlds) {
		int i = 0;
		for(int j = 0; j < worlds.length; ++j) {
			if(worlds[j] != null) {
				i += worlds[j].loadedEntityList.size();
			}
		}
		return i;
	}

	private static int countTileEntities(WorldServer[] worlds) {
		int i = 0;
		for(int j = 0; j < worlds.length; ++j) {
			if(worlds[j] != null) {
				i += worlds[j].loadedTileEntityList.size();
			}
		}
		return i;
	}

	private static int countPlayerEntities(WorldServer[] worlds) {
		int i = 0;
		for(int j = 0; j < worlds.length; ++j) {
			if(worlds[j] != null) {
				i += worlds[j].playerEntities.size();
			}
		}
		return i;
	}

	public void setPaused(boolean p) {
		paused = p;
		if(!p) {
			currentTime = EagRuntime.steadyTimeMillis();
		}
	}
	
	public boolean getPaused() {
		return paused;
	}

	@Override
	public boolean canStructuresSpawn() {
		return worldServers != null ? worldServers[0].getWorldInfo().isMapFeaturesEnabled() : newWorldSettings.isMapFeaturesEnabled();
	}

	@Override
	public GameType getGameType() {
		return worldServers != null ? worldServers[0].getWorldInfo().getGameType() : newWorldSettings.getGameType();
	}

	@Override
	public EnumDifficulty getDifficulty() {
		return difficulty;
	}

	@Override
	public boolean isHardcore() {
		return worldServers != null ? worldServers[0].getWorldInfo().isHardcoreModeEnabled() : newWorldSettings.getHardcoreEnabled();
	}

	@Override
	public int getOpPermissionLevel() {
		return 4;
	}

	@Override
	public boolean func_181034_q() {
		return true;
	}

	@Override
	public boolean func_183002_r() {
		return true;
	}

	@Override
	public boolean isDedicatedServer() {
		return false;
	}

	@Override
	public boolean func_181035_ah() {
		return false;
	}

	@Override
	public boolean isCommandBlockEnabled() {
		return true;
	}

	@Override
	public String shareToLAN(GameType var1, boolean var2) {
		return null;
	}

}
