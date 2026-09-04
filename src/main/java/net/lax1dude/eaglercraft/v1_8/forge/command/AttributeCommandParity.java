package net.lax1dude.eaglercraft.v1_8.forge.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.lax1dude.eaglercraft.v1_8.forge.GapFixRuntimeLog;
import net.minecraft.command.CommandException;
import net.minecraft.command.CommandResultStats;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.lax1dude.eaglercraft.v1_8.EaglercraftUUID;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;

/**
 * [Agent Note 2026-09-04] GENERAL: vanilla 1.20.1 {@code /attribute} parity
 * on the REAL 1.8 attribute engine — boss stat design for every map, zero
 * hardcode.
 *
 * WHAT WAS BROKEN: /attribute does not exist at all (1.16+ command). Map
 * makers use it for boss HP/damage/movement tuning — the core tool of
 * every boss map. The 1.8 engine HAS the real subsystem
 * (SharedMonsterAttributes + IAttributeInstance + AttributeModifier with
 * UUID semantics) — it was simply never exposed to commands.
 *
 * SYNTAX (1.20.1 surface, real 1.8 semantics):
 * <pre>
 *  attribute <target> <attribute> get [scale]
 *  attribute <target> <attribute> base get [scale]
 *  attribute <target> <attribute> base set <value>
 *  attribute <target> <attribute> value get [scale]      (with modifiers)
 *  attribute <target> <attribute> modifier add <uuid> <name> <value> [add|multiply|multiply_base]
 *  attribute <target> <attribute> modifier remove <uuid>
 *  attribute <target> <attribute> modifier value get <uuid>
 * </pre>
 *
 * NAME MAPPING: modern {@code minecraft:generic.max_health} → strip
 * namespace → snake_case → the 1.8 registry name
 * {@code generic.maxHealth} (the real 1.8 attribute names are camelCase
 * strings on the IAttribute objects — verified
 * SharedMonsterAttributes.java:38-47). Entity-specific attributes resolve
 * through the entity's own live attribute map (1.8
 * getEntityAttribute), so attributes the entity doesn't carry fail with
 * the vanilla-style "entity has no attribute" error — honest boundary.
 *
 * Doc-ID: MCBP-ATTR-001
 * Status: active
 * Last-Verified: 2026-09-04
 */
public class AttributeCommandParity implements ICommand {

        @Override
        public String getCommandName() {
                return "attribute";
        }

        @Override
        public String getCommandUsage(ICommandSender sender) {
                return "/attribute <target> <attribute> (get|base set <v>|value get|modifier ...)";
        }

        @Override
        public List<String> getCommandAliases() {
                return Collections.emptyList();
        }

        public int getRequiredPermissionLevel() {
                return 2; // vanilla 1.20.1 level
        }

        @Override
        public boolean canCommandSenderUseCommand(ICommandSender sender) {
                return sender.canCommandSenderUseCommand(getRequiredPermissionLevel(), "attribute");
        }

        @Override
        public void processCommand(ICommandSender sender, String[] args) throws CommandException {
                try {
                        execute(sender, args);
                } catch (IllegalArgumentException e) {
                        GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "parse", "syntax_fail",
                                        "reason=" + e.getMessage() + " args=" + Arrays.toString(args));
                        throw new CommandException(String.valueOf(e.getMessage()));
                }
        }

        private static void execute(ICommandSender sender, String[] args) throws CommandException {
                if (args == null || args.length < 3) {
                        throw new IllegalArgumentException("Expected: attribute <target> <attribute> ...");
                }
                EntityLivingBase target = resolveSingleLiving(sender, args[0]);
                if (target == null) {
                        throw new IllegalArgumentException("No living entity matched '" + args[0] + "'");
                }
                IAttribute attribute = resolveAttribute(target, args[1]);
                if (attribute == null) {
                        throw new IllegalArgumentException("Entity has no attribute '" + args[1] + "'");
                }
                IAttributeInstance instance = target.getEntityAttribute(attribute);
                if (instance == null) {
                        throw new IllegalArgumentException("Entity has no attribute '" + args[1] + "'");
                }

                String sub = args[2].toLowerCase(Locale.ROOT);
                if ("get".equals(sub)) {
                        // vanilla: base value by default
                        double scale = readScale(args, 3);
                        report(sender, args[1], instance.getBaseValue() * scale);
                        return;
                }
                if ("base".equals(sub)) {
                        if (args.length >= 4 && "get".equals(args[3].toLowerCase(Locale.ROOT))) {
                                double scale = readScale(args, 4);
                                report(sender, args[1], instance.getBaseValue() * scale);
                                return;
                        }
                        if (args.length >= 5 && "set".equals(args[3].toLowerCase(Locale.ROOT))) {
                                double value = parseDouble(args[4]);
                                instance.setBaseValue(value);
                                sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, 1);
                                feedback(sender, "Base value for attribute " + args[1]
                                                + " for entity " + target.getName() + " set to " + trim(value));
                                GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "base_set", "ok",
                                                "attr=" + args[1] + " value=" + value);
                                return;
                        }
                        throw new IllegalArgumentException("Expected: base get [scale] | base set <value>");
                }
                if ("value".equals(sub) && args.length >= 4 && "get".equals(args[3].toLowerCase(Locale.ROOT))) {
                        // vanilla: total value WITH modifiers applied
                        double scale = readScale(args, 4);
                        report(sender, args[1], instance.getAttributeValue() * scale);
                        return;
                }
                if ("modifier".equals(sub)) {
                        if (args.length >= 5 && "add".equals(args[3].toLowerCase(Locale.ROOT))) {
                                EaglercraftUUID uuid = parseUuid(args[4]);
                                if (uuid == null) {
                                        throw new IllegalArgumentException("Invalid modifier UUID '" + args[4] + "'");
                                }
                                if (args.length < 7) {
                                        throw new IllegalArgumentException("Expected: modifier add <uuid> <name> <value> [add|multiply|multiply_base]");
                                }
                                String name = args[5];
                                double value = parseDouble(args[6]);
                                int op = 0;
                                if (args.length >= 8) {
                                        String o = args[7].toLowerCase(Locale.ROOT);
                                        if ("multiply".equals(o)) {
                                                op = 1;
                                        } else if ("multiply_base".equals(o)) {
                                                op = 2;
                                        } else if (!"add".equals(o)) {
                                                throw new IllegalArgumentException("Unknown operation '" + args[7] + "'");
                                        }
                                }
                                // REAL 1.8 modifier engine: remove-then-add for idempotence on
                                // the same UUID (vanilla errors on duplicate UUID; 1.8 replace
                                // is the general, safe equivalent — documented boundary)
                                removeModifier(instance, uuid);
                                instance.applyModifier(new AttributeModifier(uuid, name, value, op));
                                sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, 1);
                                feedback(sender, "Modifier " + uuid + " applied to attribute " + args[1]
                                                + " for entity " + target.getName());
                                GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "modifier_add", "ok",
                                                "attr=" + args[1] + " uuid=" + uuid + " op=" + op + " value=" + value);
                                return;
                        }
                        if (args.length >= 5 && "remove".equals(args[3].toLowerCase(Locale.ROOT))) {
                                EaglercraftUUID uuid = parseUuid(args[4]);
                                if (uuid == null || !removeModifier(instance, uuid)) {
                                        throw new IllegalArgumentException("No modifier with UUID " + args[4] + " exists");
                                }
                                sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, 1);
                                feedback(sender, "Modifier " + uuid + " removed from attribute " + args[1]);
                                GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "modifier_remove", "ok",
                                                "attr=" + args[1] + " uuid=" + uuid);
                                return;
                        }
                        if (args.length >= 6 && "value".equals(args[3].toLowerCase(Locale.ROOT))
                                        && "get".equals(args[4].toLowerCase(Locale.ROOT))) {
                                EaglercraftUUID uuid = parseUuid(args[5]);
                                AttributeModifier mod = findModifier(instance, uuid);
                                if (mod == null) {
                                        throw new IllegalArgumentException("No modifier with UUID " + args[5] + " exists");
                                }
                                report(sender, args[1], mod.getAmount());
                                return;
                        }
                        throw new IllegalArgumentException("Expected: modifier add|remove|value get ...");
                }
                throw new IllegalArgumentException("Expected get, base, value or modifier, got: '" + sub + "'");
        }

        // ------------------------------------------------------------------
        // Resolution helpers (REAL engine objects only)
        // ------------------------------------------------------------------

        private static EntityLivingBase resolveSingleLiving(ICommandSender sender, String token) {
                try {
                        EntitySelector selector = new EntitySelector(token);
                        if (token.startsWith("@")) {
                                List<net.minecraft.entity.Entity> entities = selector.getEntities(sender);
                                for (net.minecraft.entity.Entity e : entities) {
                                        if (e instanceof EntityLivingBase) {
                                                return (EntityLivingBase) e;
                                        }
                                }
                                return null;
                        }
                        net.minecraft.entity.Entity single = selector.findEntity(sender);
                        return single instanceof EntityLivingBase ? (EntityLivingBase) single : null;
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "resolve", "fail",
                                        "token=" + token + " err=" + String.valueOf(t.getMessage()));
                        return null;
                }
        }

        /** Modern name → real 1.8 IAttribute via the entity's attribute map. */
        private static IAttribute resolveAttribute(EntityLivingBase entity, String modernName) {
                String name = EffectCommandParity.stripNamespace(modernName).toLowerCase(Locale.ROOT);
                String camel = snakeToCamel(name);
                // generic core set resolved through the REAL registry objects
                IAttribute[] known = { SharedMonsterAttributes.maxHealth, SharedMonsterAttributes.followRange,
                                SharedMonsterAttributes.knockbackResistance, SharedMonsterAttributes.movementSpeed,
                                SharedMonsterAttributes.attackDamage };
                for (IAttribute a : known) {
                        if (a.getAttributeUnlocalizedName().equalsIgnoreCase(camel)) {
                                return a;
                        }
                }
                // entity-specific attributes (horse jump strength etc.) live on the
                // entity's own attribute map — resolve by scanning its live instances
                try {
                        for (Object o : entity.getAttributeMap().getAllAttributes()) {
                                if (o instanceof IAttributeInstance) {
                                        IAttributeInstance inst = (IAttributeInstance) o;
                                        if (inst.getAttribute().getAttributeUnlocalizedName().equalsIgnoreCase(camel)) {
                                                return inst.getAttribute();
                                        }
                                }
                        }
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "scan", "stub",
                                        "err=" + String.valueOf(t.getMessage()));
                }
                return null;
        }

        private static String snakeToCamel(String snake) {
                StringBuilder sb = new StringBuilder(snake.length());
                boolean upper = false;
                for (char c : snake.toCharArray()) {
                        if (c == '_') {
                                upper = true;
                        } else if (upper) {
                                sb.append(Character.toUpperCase(c));
                                upper = false;
                        } else {
                                sb.append(c);
                        }
                }
                return sb.toString();
        }

        private static AttributeModifier findModifier(IAttributeInstance instance, EaglercraftUUID uuid) {
                try {
                        // REAL 1.8 registry lookup (IAttributeInstance.getModifier)
                        return instance.getModifier(uuid);
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "find_modifier", "fail",
                                        "err=" + String.valueOf(t.getMessage()));
                        return null;
                }
        }

        private static boolean removeModifier(IAttributeInstance instance, EaglercraftUUID uuid) {
                AttributeModifier m = findModifier(instance, uuid);
                if (m != null) {
                        instance.removeModifier(m);
                        return true;
                }
                return false;
        }

        private static double readScale(String[] args, int index) {
                if (args.length > index) {
                        try {
                                return Double.parseDouble(args[index]);
                        } catch (NumberFormatException ignored) {
                                // vanilla rejects bad scale — treat as syntax error upstream
                                throw new IllegalArgumentException("Invalid scale '" + args[index] + "'");
                        }
                }
                return 1.0D;
        }

        private static double parseDouble(String s) {
                try {
                        return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid number '" + s + "'");
                }
        }

        private static EaglercraftUUID parseUuid(String s) {
                try {
                        return EaglercraftUUID.fromString(s);
                } catch (Throwable e) {
                        return null;
                }
        }

        private static void report(ICommandSender sender, String attr, double value) {
                sender.setCommandStat(CommandResultStats.Type.QUERY_RESULT, (int) value);
                sender.setCommandStat(CommandResultStats.Type.AFFECTED_ENTITIES, 1);
                feedback(sender, "Attribute " + attr + " has value " + trim(value));
        }

        private static String trim(double value) {
                if (value == Math.floor(value) && !Double.isInfinite(value)) {
                        return String.valueOf((long) value);
                }
                return String.valueOf(value);
        }

        private static void feedback(ICommandSender sender, String message) {
                try {
                        sender.addChatMessage(new ChatComponentText(message));
                } catch (Throwable t) {
                        GapFixRuntimeLog.hit("attribute", "AttributeCommandParity", "feedback", "fail",
                                        "err=" + String.valueOf(t.getMessage()));
                }
        }

        // ------------------------------------------------------------------
        // ICommand plumbing
        // ------------------------------------------------------------------

        @Override
        public List<String> addTabCompletionOptions(ICommandSender sender, String[] args, BlockPos pos) {
                List<String> out = new ArrayList<String>();
                if (args.length == 3) {
                        match(out, args[2], Arrays.asList("get", "base", "value", "modifier"));
                }
                return out;
        }

        private static void match(List<String> out, String token, List<String> options) {
                String t = token == null ? "" : token.toLowerCase();
                for (String o : options) {
                        if (o.startsWith(t)) {
                                out.add(o);
                        }
                }
        }

        @Override
        public boolean isUsernameIndex(String[] args, int index) {
                return false;
        }

        @Override
        public int compareTo(ICommand other) {
                return this.getCommandName().compareTo(other.getCommandName());
        }
}
