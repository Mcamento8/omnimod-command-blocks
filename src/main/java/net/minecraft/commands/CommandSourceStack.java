package net.minecraft.commands;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

/**
 * [Agent Note 2026-08-02] GENERAL: Forge 1.20.1 CommandSourceStack shim.
 *
 * Mirrors {@code net.minecraft.commands.CommandSourceStack} from Forge 1.20.1.
 * This is the {@code S} type a mod's Brigadier command tree is parameterised
 * over. Mods call, e.g.:
 *   context.getSource().hasPermission(2)
 *   context.getSource().getPlayer()
 *   context.getSource().getServer()
 *   context.getSource().sendSuccess(() -> Component.literal("..."), bool)
 *   context.getSource().sendFailure(Component.literal("..."))
 *
 * HOW IT WORKS (OmniMod bridge):
 * This shim wraps a 1.8 {@link ICommandSender} (the server console, a command
 * block, or a {@link EntityPlayerMP}). It translates the modern 1.20.1 API
 * into 1.8 calls: {@code getPlayer()} returns the sender's underlying player
 * entity, {@code getServer()} returns the static server, and
 * {@code sendSuccess/sendFailure} convert the {@link Component} into a 1.8
 * {@link IChatComponent} and deliver it via {@code addChatMessage}.
 *
 * [Agent Note 2026-08-28] PARITY UPGRADE (UCBPP CRITICAL-3) — modified-source
 * architecture, as vanilla CSS carries it for /execute:
 *   - {@code position} (Vec3) — set by execute at|positioned; overrides the
 *     sender position for {@code getPosition()} and relative coordinates.
 *   - {@code rotation} (Vec2, x=yaw y=pitch) — set by rotated|facing; drives
 *     local (^) coordinates via {@code WorldCoordinates}.
 *   - {@code level} (1.8 World) — set by execute in; returned by getLevel().
 *   - {@code anchor} ("eyes"|"feet") — set by anchored; drives local-axis eye
 *     offset semantics.
 *   All fields are immutable-with-builders: every {@code with*} returns a NEW
 *   stack (vanilla pattern) — the original is never mutated.
 *   Unset fields fall back to the wrapped {@link ICommandSender} exactly as
 *   before this change (zero behavior delta for existing callers).
 *
 * {@code hasPermission(n)} maps to the 1.8 op-level check
 * ({@code canCommandSenderUseCommand}). This is what the mod's
 * {@code .requires(s -> s.hasPermission(2))} uses to gate op-only commands.
 *
 * GENERAL — serves every Forge 1.20.1 command mod. No mod id/name hardcode.
 *
 * Doc-ID: MC-CSS-002
 */
public class CommandSourceStack {
	private final ICommandSender sender;
	private final Vec3 position;
	private final Vec2 rotation;
	private final net.minecraft.world.World level;
	private final String anchor;

	public CommandSourceStack(ICommandSender sender) {
		this(sender, null, null, null, null);
	}

	/**
	 * [UCBPP CRITICAL-3] Full builder constructor — used by {@code with*}
	 * (immutable copy semantics) and by the execute engine.
	 */
	public CommandSourceStack(ICommandSender sender, Vec3 position, Vec2 rotation,
			net.minecraft.world.World level, String anchor) {
		this.sender = sender;
		this.position = position;
		this.rotation = rotation;
		this.level = level;
		this.anchor = anchor;
	}

	public ICommandSender getRawSender() {
		return sender;
	}

	public boolean hasPermission(int level) {
		if (sender == null) {
			return false;
		}
		try {
			return sender.canCommandSenderUseCommand(level, "omnimod.command");
		} catch (Throwable t) {
			return false;
		}
	}

	public EntityPlayer getPlayer() {
		if (sender == null) {
			return null;
		}
		try {
			net.minecraft.entity.Entity e = sender.getCommandSenderEntity();
			if (e instanceof EntityPlayer) {
				return (EntityPlayer) e;
			}
		} catch (Throwable ignored) {
		}
		return null;
	}

	/**
	 * [Agent Note 2026-08-28] Real 1.20.1 API: {@code source.getPosition()}
	 * returns the 1.20.1 Vec3. Modified position (execute at|positioned)
	 * takes precedence; otherwise the 1.8 sender's position vector.
	 */
	public Vec3 getPosition() {
		if (position != null) {
			return position;
		}
		if (sender == null) {
			return Vec3.ZERO;
		}
		try {
			net.minecraft.util.Vec3 v = sender.getPositionVector();
			if (v != null) {
				return new Vec3(v.xCoord, v.yCoord, v.zCoord);
			}
		} catch (Throwable ignored) {
		}
		return Vec3.ZERO;
	}

	/**
	 * [UCBPP CRITICAL-3] Real 1.20.1 API: modified rotation (execute
	 * rotated|facing) or the sender entity's rotation (x=yaw, y=pitch).
	 */
	public Vec2 getRotation() {
		if (rotation != null) {
			return rotation;
		}
		net.minecraft.entity.Entity e = getEntity();
		if (e != null) {
			return new Vec2(e.rotationYaw, e.rotationPitch);
		}
		return Vec2.ZERO;
	}

	/**
	 * [UCBPP CRITICAL-3] True when an explicit modified rotation was applied
	 * (execute rotated|facing) — lets local coordinates distinguish "use the
	 * entity rotation" from "use the modified rotation".
	 */
	public boolean hasModifiedRotation() {
		return rotation != null;
	}

	/**
	 * [UCBPP CRITICAL-3] Real 1.20.1 API: {@code source.getLevel()} — the
	 * modified level (execute in) or the sender's world.
	 */
	public net.minecraft.world.World getLevel() {
		if (level != null) {
			return level;
		}
		try {
			return sender != null ? sender.getEntityWorld() : null;
		} catch (Throwable ignored) {
			return null;
		}
	}

	/**
	 * [UCBPP CRITICAL-3] Real 1.20.1 API: {@code source.getAnchor()} — the
	 * local-coordinate anchor ("eyes"/"feet") or null when unmodified.
	 */
	public String getAnchor() {
		return anchor;
	}

	/**
	 * [Agent Note 2026-08-28] Real 1.20.1 API: block position of this source
	 * (command block position / player block position / console origin).
	 */
	public net.minecraft.util.BlockPos getPosition0() {
		try {
			return sender != null ? sender.getPosition() : net.minecraft.util.BlockPos.ORIGIN;
		} catch (Throwable t) {
			return net.minecraft.util.BlockPos.ORIGIN;
		}
	}

	/**
	 * [Agent Note 2026-08-28] Real 1.20.1 API: {@code source.getEntity()} —
	 * the underlying entity (player, command block minecart...), or null for
	 * the console. Used by ^local coordinates and mod lambdas.
	 */
	public net.minecraft.entity.Entity getEntity() {
		if (sender == null) {
			return null;
		}
		try {
			return sender.getCommandSenderEntity();
		} catch (Throwable ignored) {
			return null;
		}
	}

	// ── [UCBPP CRITICAL-3] Immutable with* builders (vanilla CSS pattern) ──

	public CommandSourceStack withPosition(Vec3 pos) {
		return new CommandSourceStack(sender, pos, rotation, level, anchor);
	}

	public CommandSourceStack withRotation(Vec2 rot) {
		return new CommandSourceStack(sender, position, rot, level, anchor);
	}

	public CommandSourceStack withLevel(net.minecraft.world.World world) {
		return new CommandSourceStack(sender, position, rotation, world, anchor);
	}

	public CommandSourceStack withAnchor(String newAnchor) {
		return new CommandSourceStack(sender, position, rotation, level, newAnchor);
	}

	public MinecraftServer getServer() {
		try {
			return MinecraftServer.getServer();
		} catch (Throwable t) {
			return null;
		}
	}

	public void sendSuccess(java.util.function.Supplier<Component> message, boolean broadcastToOps) {
		if (sender == null || message == null) {
			return;
		}
		try {
			Component c = message.get();
			if (c != null) {
				IChatComponent legacy = c.toLegacyChatComponent();
				if (legacy != null) {
					sender.addChatMessage(legacy);
				}
			}
		} catch (Throwable ignored) {
		}
	}

	public void sendFailure(Component message) {
		if (sender == null || message == null) {
			return;
		}
		try {
			IChatComponent legacy = message.toLegacyChatComponent();
			if (legacy != null) {
				sender.addChatMessage(legacy);
			}
		} catch (Throwable ignored) {
		}
	}

	public String getText() {
		return sender != null ? sender.getName() : "";
	}
}
