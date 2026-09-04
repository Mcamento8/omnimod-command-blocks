# Command Block / Brigadier→1.8 Bridge Pipeline

Doc-ID: PROJECT-MAP-29-COMMAND-BLOCK
Status: active (2026-08-28 — Phase 1 of Track-B)
Related-Files:
  - sources/minecraft-client/java/net/minecraft/command/ServerCommandManager.java:46
  - sources/minecraft-client/java/net/minecraft/command/CommandHandler.java:38
  - sources/minecraft-client/java/net/minecraft/command/server/CommandBlockLogic.java:101
  - sources/main/java/com/mojang/brigadier/CommandDispatcher.java:32
  - sources/main/java/com/mojang/brigadier/tree/CommandNode.java
  - sources/main/java/com/mojang/brigadier/tree/LiteralCommandNode.java
  - sources/main/java/com/mojang/brigadier/tree/ArgumentCommandNode.java
  - sources/main/java/com/mojang/brigadier/builder/LiteralArgumentBuilder.java
  - sources/main/java/com/mojang/brigadier/builder/RequiredArgumentBuilder.java
  - sources/main/java/com/mojang/brigadier/builder/ArgumentBuilder.java
  - sources/main/java/com/mojang/brigadier/arguments/*.java
  - sources/main/java/com/mojang/brigadier/suggestion/*.java
  - sources/main/java/com/mojang/brigadier/context/CommandContext.java
  - sources/main/java/com/mojang/brigadier/StringReader.java
  - sources/main/java/com/mojang/brigadier/Command.java
  - sources/main/java/net/minecraft/commands/Commands.java
  - sources/main/java/net/minecraft/commands/CommandSourceStack.java
  - sources/main/java/net/minecraft/commands/arguments/*.java
  - sources/main/java/net/minecraft/world/phys/Vec3.java
  - sources/main/java/net/minecraftforge/event/RegisterCommandsEvent.java
  - sources/main/java/net/lax1dude/eaglercraft/v1_8/forge/ForgeHooks.java:860
  - sources/main/java/net/lax1dude/eaglercraft/v1_8/forge/ModCommandSourceAnalyzer.java
  - sources/main/java/net/lax1dude/eaglercraft/v1_8/forge/ModCommandSourceBridge.java
  - sources/main/java/net/lax1dude/eaglercraft/v1_8/forge/ModCommandSourceRegistry.java
  - sources/main/java/net/lax1dude/eaglercraft/v1_8/minecraft/ModManager.java:4432
  - tmp_command_bridge_harness/CommandBridgeHarness.java (JVM harness)
  - mod_compat_lab/run_command_block_compat_check.py
  - PROMPT_COMMAND_BLOCK_1201_PARITY.md (Track-B spec)

> هذا الملف هو خريطة المسار الكامل لنظام الأوامر من المصدر 1.8.8 إلى 1.20.1 — ما
> يُحفظ، كيف يُنفَّذ، وكيف يُتحقَّق منه. كل ملف مرتبط بـ file:line + دوره
> بالتحديد. (القارئ الجديد يبدأ من §1 ثم §6).

---

## 1. نقطة الدخول الموحَّدة (1.8 unchanged)

`CommandHandler.executeCommand(ICommandSender, String)` — `CommandHandler.java:44`
تقبل النص، تقص الـ `/`، تقطع بالمسافة، تبحث في `commandMap`، تتحقق
`canCommandSenderUseCommand`، توسّع selectors عبر `PlayerSelector` (1.8 vanilla)
ثم تستدعي `tryExecute:92`. **هذه النقطة لم تتغير في الإصلاح** — هي الـ
`regression anchor` التي يضمن كل إصلاح عدم كسرها.

## 2. الـ 46 أمرًا المسجَّلة (1.8 vanilla + Forge)

`ServerCommandManager.ctor:46-95` يسجّل 46 أمرًا (time, gamemode, fill, clone,
scoreboard, execute, give, ...) ثم ينادي `ForgeHooks.onRegisterCommands(this):103`.
كل أمر منها موروث من `CommandBase.java:50` و`ICommand`. **هذه الطبقة الـ 1.8
حُفظت 100%** — لم يتغير توقيع أي منها.

## 3. سلسلة Brigadier (1.20.1 shim — معدَّلة Phase 1)

### 3.1 المكوّنات

| الملف | الدور |
|---|---|
| `com.mojang.brigadier.CommandDispatcher.java` | جذر الشجرة + `PermissionProbe` لاستنتاج `hasPermission(N)` من predicate حقيقي + `CommandPath` يحمل root+leaf nodes |
| `com.mojang.brigadier.tree.CommandNode.java` | العقدة الأساسية — حقول جديدة: `stubExecutor` و`dynamicName` |
| `com.mojang.brigadier.tree.LiteralCommandNode.java` | عقدة literal (اسم ثابت) |
| `com.mojang.brigadier.tree.ArgumentCommandNode.java` | **جديد Phase 1** — عقدة حجة (Integer/String/Entity/BlockPos...) تستهلك توكن واحد، تحلّ عبر ArgumentType، تخزّن قيمة مكتوبة على CommandContext |
| `com.mojang.brigadier.builder.ArgumentBuilder.java` | أساس الـ fluent DSL — يحمل `permissionLevel` + **`stubExecutor` flag** |
| `com.mojang.brigadier.builder.LiteralArgumentBuilder.java` | `Commands.literal(...)` — `build()` ينقل `dynamicName` للنود |
| `com.mojang.brigadier.builder.RequiredArgumentBuilder.java` | `Commands.argument(...)` — `build()` يصنع `ArgumentCommandNode` حقيقي عند نوع ArgumentType، وإلا literal مع `dynamicName=true` |
| `com.mojang.brigadier.arguments.ArgumentType.java` | **جديد** — واجهة parse + listSuggestions |
| `com.mojang.brigadier.arguments.{Integer,String,Bool,Float,Double,Long}ArgumentType.java` | **جديد 7 ملفات** — شيمات Forge 1.20.1 مع range-enforcement + greedy-string |
| `com.mojang.brigadier.StringReader.java` | **جديد** — parse + skipWhitespace + readInt/Long/Float/Double/Boolean/QuotedString |
| `com.mojang.brigadier.context.CommandContext.java` | `getArgument` + `putArgument` + **`removeArgument` (backtrack)** |
| `com.mojang.brigadier.suggestion.{SuggestionProvider,Suggestions,SuggestionsBuilder}.java` | **جديد 3 ملفات** — `suggests(...)` API |
| `com.mojang.brigadier.exceptions.CommandSyntaxException.java` | موجود سابقًا |
| `net.minecraft.commands.Commands.java` | `Commands.literal/argument(...)` factory |
| `net.minecraft.commands.CommandSourceStack.java` | `hasPermission` + `sendSuccess/Failure` + `getPlayer` + `getEntity` + `getPosition` (1.20.1 Vec3) — **أضيف `getPosition/getEntity` Phase 1** |
| `net.minecraft.world.phys.Vec3.java` | **جديد** — موقع 1.20.1 (نفس math لكن package مختلف لـ ClassLoading) |
| `net.minecraft.commands.arguments.EntityArgument.java` | **جديد** — player()/entity()/players()/entities() + getEntity/getPlayer/getEntities/getPlayers static (real 1.20.1 API) + `EntitySelector` resolution vs `PlayerSelector` + name lookup |
| `net.minecraft.commands.arguments.selector.EntitySelector.java` | **جديد** — يحلّ `@e[type=...]` واسماء اللاعبين من integrated server |
| `net.minecraft.commands.arguments.coordinates.{Coordinates,WorldCoordinates,BlockPosArgument}.java` | **جديد 3 ملفات** — `~` و`^` وabsolute mixing، `blockPos()` + `getBlockPos/getSpawnablePos` static |
| `net.minecraft.commands.arguments.{ResourceLocationArgument,UuidArgument,EntityAnchorArgument}.java` | **جديد 3 ملفات** — type-specific shims |
| `net.minecraftforge.event.RegisterCommandsEvent.java` | يحمل `CommandDispatcher<CommandSourceStack>` حقيقي (سابقًا كان Object) |

### 3.2 الـ JBR Surface للـ MCP + كوماند بلوك

كل `ICommand` مسجّل في `ServerCommandManager.commandMap` هو إما 1.8 vanilla أو
برمجي عبر `ModCommandSourceBridge` الذي يحوّل كل path من شجرة
`CommandDispatcher` لـ `ICommand` واحد. الـ `ICommand` الجديد يملك:
- `getCommandName()` = الجذر (مثل `"sniper"`)
- `processCommand` = parse على الشجرة الفعلية من `path.rootNode` → `walk` → `executeLeaf`
- `canCommandSenderUseCommand` = `maxLevelAlongPath` (root..leaf) فيغلّ بوابة op حتى لو الورقة بلا level
- `addTabCompletionOptions` = `tabComplete` من الشجرة (literals + argument suggestions + online player names)

## 4. المحلل (Source-analysis path)

`ModCommandSourceAnalyzer.analyze(modId, sourceText)` يبني `CommandDispatcher<CommandSourceStack>` من
نص Java الفعلي للمود (web/teavm لا يستطيعان تشغيل mod bytecode). المراحل:

1. **كشف نوايا التسجيل** (lowercase quick-gate: `commands.literal`/`commanddispatcher`/`registercommandsevent`)
2. **جمع ثوابت int** بـ `INT_CONSTANT_PATTERN` — `PERMISSION_LEVEL_OP = 3` يبقى مُحَلًّا عند `hasPermission(PERMISSION_LEVEL_OP)`
3. **تحليل call-tree** (string/comment aware + bare-paren depth `()`-aware لأقواس lambda + التعامل مع `var root = Commands.literal(...)` كنقطة بداية و`root.then(...)` كـ qualified call `"root.then"` للـ merge)
4. **resolution أنواع الحجج** إلى شيمات ArgumentType حقيقية: Integer/Range, String/Word/Greedy, Bool, Float, Double, Long, Entity, BlockPos, ResourceLocation, Uuid, EntityAnchor
5. **بناء specs** (SpecNode: name + isArgument + argumentType + permissionLevel + hasExecutes + isDynamic + children)
6. **تركيب dispatcher** (LiteralArgumentBuilder / RequiredArgumentBuilder + `setStubExecutor(true)` على كل leaf للـ honesty)
7. **إطلاق** عبر `ModCommandSourceRegistry.registerModDispatcher` → `ForgeHooks.onRegisterCommands:860` → `buildMergedDispatcher` → `RegisterCommandsEvent` post → `ModCommandSourceBridge.registerFromEvent`

## 5. الجسر (الطبقة الموحَّدة)

`ModCommandSourceBridge.registerDispatcher(modId, dispatcher, manager)` يمشي
الشجرة، لكل `CommandPath` يصنع `ICommand` واحد ويسجّله. ثم في
`processCommand` يستدعي:

- `parseAndDispatch(modId, rootNode, input, source, joinedPath)`:
  - يبني `CommandContext` ويملؤه بالحجج
  - `walk(rootNode, reader, ctx, source, 0, failure, matched, parseError)`:
    - 1) Exact literal (case-insensitive fallback)
    - 2) Argument children: `argNode.parse(reader, ctx)` مع backtracking عبر `ctx.removeArgument(name)` و `reader.setCursor(cursorAtArg)`
    - 3) Dynamic literal children: wildcard match (يقرأ token واحد فقط)
  - إذا فشل: `throw new CommandException(...)` (semantics vanilla 1.8 — success count 0)
  - إذا نجح: `executeLeaf(modId, leaf, ctx, source, actualPath)`:
    - `isStubExecutor()` → رسالة recognition صادقة + `GapFixRuntimeLog.hit action=execute result=stub cause=source_analysis_executor_unavailable` (لا تشغيل وهمي §18.2b)
    - غير ذلك → `executor.run(ctx)` حقيقي، كل catch يُسجَّل في `GapFixRuntimeLog.error errorCause=...`

- `tabComplete(modId, rootNode, sender, args)`:
  - يمشي tokens المستهلكة، يجمع الاقتراحات من child literals + argument nodes (نوع defaults + `suggestionProvider`) + online player names لـ entity arguments

## 6. المسارات (للتشخيص)

```
Mod .java source (SnipersCommands.java) ──┐
                                            │
ModManager.translateModData:4432 ──────────┤
  ↓                                         │
ModCommandSourceAnalyzer.analyze() ─────────┘
  ↓ (CommandDispatcher<CommandSourceStack>)
ModCommandSourceRegistry.registerModDispatcher()
  ↓
ForgeHooks.onRegisterCommands(dispatcher):860
  ↓ buildMergedDispatcher() + post(RegisterCommandsEvent)
  ↓
ModCommandSourceBridge.registerDispatcher()
  ↓ لكل path
  ↓   registerCommand(ICommand)  ─────────────────────►  CommandHandler.commandMap
  ↓                                                       ↓
  └─ processCommand() → parseAndDispatch() → walk() →   executeCommand()
       ↓                                                   ↓
       executeLeaf() → executor.run(ctx) ───►            tryExecute()
       ↓                                                   ↓
       sendSuccess/Failure ←── Component ←──  Bridge ←── Chat / Block UI
```

## 7. الـ boundaries الصادقة (§19.8)

| القيد | السبب | السلوك |
|---|---|---|
| Mod bytecode لا يُنفَّذ على web/teavm | ModClassLoader غير متاح | `isStubExecutor=true` → recognition-only صادق |
| Classloaded path (desktop) | ModClassLoader يستدعي register() حقيقي | `executor.run(ctx)` يُنفَّذ مع كل side effects |
| Var-builder registration متعدد dispatch | نمط AdvancedComputers المتقدم | discovery يلتقط الجذر + children + INFO يبقى إذا الـ pattern لم يُكتشف |
| Required<Permission> في predicate body | Predicates معقدة (world access, ...) | `PermissionProbe` يفشل صادق → level=0 → public command |
| Argument types بخلاف الـ 14 shim | mods تستخدم ItemInput, BlockStateParser, ResourceKey | wildcard dynamic literal (honest fallback) |

## 8. أدوات التحقق

| الأداة | الموقع | الفحص |
|---|---|---|
| JVM harness | `tmp_command_bridge_harness/CommandBridgeHarness.java` | 17/17 PASS — ينفّذ الـ .class الفعلية ويؤكد 7 عيوب + 3 حالات إيجابية (Integer range + BlockPos relative + Entity honest-null) |
| Tool | `mod_compat_lab/run_command_block_compat_check.py` | على 3 مودات: Snipers 27/0/0 + AdvancedComputers 20/0/0 + SlightlyMoreOres 20/0/0 (INFO negative control) |
| Orchestrator | `mod_compat_lab/run_all_mod_tests.py` | مُسجَّل كـ `command_block_compat_check` stage |
| Regression | `run_logic_mod_compat_check` على Snipers | 7 PASS / 2 GAP / 0 FAIL — لا انحدار |

## 9. الـ DoD (§18.6) — Phase 1 مكتمل

- [x] Root cause مؤكد بأدلة file:line (11 FAIL من harness baseline)
- [x] Reusable fix مطبّق (14 ملف جديد + تعديلات على 11 ملف — **0 hardcode** لـ mod id)
- [x] لا regression على `run_logic_mod_compat_check` (Snipers)
- [x] Honest boundaries موثّقة (§7 أعلاه)
- [x] `PROJECT_CONTEXT.md §14` محدَّث
- [x] `CHANGELOG.md` محدَّث
- [x] `COMPAT_MATRIX.md` محدَّث
- [x] `ROADMAP.md` فئة 6 محدَّثة
- [x] `system.saf.json` محدَّث
- [x] خريطة المشروع (هذا الملف) جديدة

## 10. ما تبقى (Track-B Phases 2-5)

| المرحلة | المهمة | الحالة |
|---|---|---|
| 2 | إعادة كتابة `/execute` كشجرة Brigadier كاملة (as/at/if/store/run) | **`done` (2026-08-28 — UCBPP Phase 2)** |
| 3 | `/data` + `/bossbar` + `/function` + `schedule/loot/attribute/damage/return/random/tick` | `planned` (بوابتها: حدود `if data`/`store bossbar|entity` الموثقة) |
| 4 | `omni_*` world-mutation commands + Bake & Strip في `export_map` | `planned` (Track-A فقط) |
| 5 | MCP↔kernel unification + Cross-platform (Web/Android/Desktop) | `planned` |

## 11. تحديث 2026-08-28 (PM) — UCBPP Phase 2: /execute 1.20.1 + السطح الواسع

**المنفِّذ:** وكيل UCBPP v1.0 (نفس اليوم). التفصيل: `tmp_command_bridge_harness/PHASE_0..6`.

### 11.1 الإصلاحات الحرجة (بأدلة file:line)

| الفجوة | الموقع | الحل |
|---|---|---|
| CRITICAL-1: الإحداثيات المطلقة تفقد Y/Z (مسبار ProbeAbs: `10 64 -20 → (10.5,0,0)`) | `WorldCoordinates.java` (مُعاد الكتابة، Doc-ID MC-ARG-WCOORD-002) | حلقة 3 محاور صارمة + أساس محلي vanilla (left=up×F, up=F×L) + رفض خلط `^`/`~` |
| CRITICAL-2: لا /execute 1.20.1 | `forge/ExecuteCommandParity.java` (جديد، GFR-EXECUTE-PARITY-001) + `ServerCommandManager.java:77` | 12 subcommand + forking + store score عبر Scoreboard:87/Score:78 + **legacy fallback** (حماية أوامر الكوماند بلوك القائمة) |
| CRITICAL-3: CSS بلا modified-source | `CommandSourceStack.java` (MC-CSS-002) | position/rotation/level/anchor + with* immutable + fallback للـ sender |
| CRITICAL-4: putArgument يستبدل | `CommandContext.java` (BRIG-CTX-002) | إلحاق مرتب + getArgument يرجع الأحدث + getArgumentEntries |
| MAJOR: ~38 ArgumentType ناقصة | 39 ملفاً جديداً تحت `net/minecraft/commands/arguments/` + `world/phys/Vec2.java` | one-shim-per-type + doc-comments + Doc-IDs + حدود موثقة |
| MAJOR: المحلل 11 نوعاً فقط | `ModCommandSourceAnalyzer.resolveArgumentType` | السطح الكامل (الأكثر تحديداً أولاً) |
| MAJOR: تعارض الجذور صامت | `ModCommandSourceBridge` (root_conflict check) | GapFixRuntimeLog.warn — additive |
| G12: readLong يفوض readInt | `StringReader.java` | تحليل 64-bit حقيقي |

### 11.2 عيوب كشفتها اختبارات TDD (وأصلحت)

1. `parseCondition` ينادي BlockPosArgument والمؤشر على مسافة → `skipWhitespace` قبل كل إحداثيات شرط.
2. `MessageArgument`/`ComponentArgument` استخدما `getRemaining()` (نظرة بلا استهلاك) → فشل السلاسل بـ `Expected argument <msg>` → `readRemaining()`.
3. `OperationArgument` رفض `<=`/`>=` → أضيفا.
4. `CommandContext.getArgument` على مفتاح مكرر → يعيد الأحدث (compat كامل).

### 11.3 التحقق (تنفيذ فعلي 2026-08-28)

| الفحص | النتيجة |
|---|---|
| `ExecuteParityHarness` (H8-H12، كلاسات مترجمة فعلية) | **65/65 PASS** |
| `CommandBridgeHarness` (H1-H7، نفس الكلاسات المعدلة) | **17/17 PASS** |
| `run_command_block_compat_check` (أداة موسعة بـ 15 فحص engine) | Snipers **41/0/0**، AdvancedComputers 34/0/0، SlightlyMoreOres (ضابط) 34/0/0 |
| `run_logic_mod_compat_check` (Snipers) | **7/2/0** — صفر انحدار |
| javac على الدفعة (39 جديد + 10 معدل) | **EXIT 0** |
| gradle رسمي | يتوقف عند الخطأ المسبق الوحيد `VoidWorldTemplate.java:252` (Track-A محظور — MASTER_GUIDE §3.4)؛ **لا خطأ في أي ملف UCBPP** |

### 11.4 حدود صادقة جديدة (§19.8)

1. دلالة الغلاف 1.8: نجاح processCommand هو ما يُعد (المجموع في `forks_total` log).
2. `if data` / `store bossbar|entity` → unsupported صريح (بوابة Phase 3).
3. فحوص World-positive داخل اللعبة = خطوة المستخدم (خطة إعادة إنتاج: PHASE_5 §4).
4. `GameProfileArgument` يعيد EntityPlayerMP (authlib خارج مسار الترجمة).
5. لا `redirect` في CommandNode → محلل تنازلي (قرار معماري موثق PHASE_3 §4).
6. `#tags` في predicates تُعلَّم honestly-false (لا طبقة tags).
7. أوامر 1.8 المحذوفة من 1.20.1 تبقى تعمل بـ semantics 1.8 (G10).
