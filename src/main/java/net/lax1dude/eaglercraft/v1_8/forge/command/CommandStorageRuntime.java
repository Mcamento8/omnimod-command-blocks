package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.nbt.NBTTagCompound;
import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;

/**
 * [Agent Note 2026-08-29] GENERAL: vanilla 1.20.1 Command NBT Storage runtime
 * backing {@code /data get|merge|remove|modify storage <id> …} and
 * {@code execute store … storage <id> <path>}.
 *
 * Mirrors vanilla's per-world "command storage" containers
 * ({@code data/<namespace>/<path>}); identifiers MUST be namespaced
 * ({@code namespace:path}) exactly like vanilla.
 *
 * WHY THIS EXISTS: map makers and command blocks need persistent-per-session
 * NBT scratch space; OmniMod had none (grep CommandStorage = 0 before this
 * file — see tmp_command_bridge_harness/PHASE_1_AUDIT_P3.md G6).
 *
 * HONEST BOUNDARY (§19.8): vanilla persists storage in the world save
 * (level.dat data/containers); OmniMod keeps it in-memory for the session and
 * clears it on world unload via ModernRegistry.clearDynamicRegistrations —
 * the same lifecycle every dynamic registry in this project uses. Persistence
 * across world reloads is a documented future step, never a silent loss.
 *
 * GENERAL — no mod id, no command name, no hardcode.
 *
 * Doc-ID: UCBPP-P3-CMDSTORAGE-001
 * Status: active
 * Last-Verified: 2026-08-29
 */
public final class CommandStorageRuntime {

	private static final Map<String, NBTTagCompound> storages = new LinkedHashMap<String, NBTTagCompound>();

	private CommandStorageRuntime() {
	}

	/** True when the id is a valid namespaced storage id ({@code ns:path}). */
	public static boolean isValidId(String id) {
		if (id == null || id.isEmpty()) {
			return false;
		}
		int colon = id.indexOf(':');
		if (colon <= 0 || colon >= id.length() - 1 || id.indexOf(':', colon + 1) >= 0) {
			return false;
		}
		return id.chars().allMatch(c -> Character.isLetterOrDigit(c) || c == ':' || c == '_' || c == '-'
				|| c == '.' || c == '/');
	}

	/** Gets (creating on demand — vanilla creates empty storage on write paths). */
	public static synchronized NBTTagCompound getStorageCompound(String id) {
		NBTTagCompound compound = storages.get(id);
		if (compound == null) {
			compound = new NBTTagCompound();
			storages.put(id, compound);
		}
		return compound;
	}

	public static synchronized boolean hasStorage(String id) {
		return storages.containsKey(id);
	}

	public static synchronized Map<String, NBTTagCompound> viewAll() {
		return new LinkedHashMap<String, NBTTagCompound>(storages);
	}

	/** World-unload teardown (ModernRegistry.clearDynamicRegistrations cascade). */
	public static synchronized void clear() {
		if (!storages.isEmpty()) {
			GapFixRuntimeLog.hit("data", "CommandStorageRuntime", "clear", "ok",
					"cleared=" + storages.size() + " storage container(s)");
		}
		storages.clear();
	}
}
