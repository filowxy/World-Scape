# World Scape 项目综合理解报告

> 报告版本：v1.0（基于 2026-07-02 的代码库状态）  
> 目标：为从 C 盘迁移后的 World Scape 项目建立一份统一、完整、可检索的技术认知地图。  
> 约束：本报告为只读理解产物，未修改任何源代码。

---

## 1. 项目定位与设计哲学

**World Scape** 是一个 Minecraft NeoForge 1.21.1 地形生成模组，其核心定位是：

- **完全替换原版 `ChunkGenerator`**，提供一套基于数学函数的地形生成框架。
- **地形与生物群系解耦**：World Scape 只生成地形骨架（石头、深板岩、基岩、河流槽），生物群系、结构、植被、地表方块等交由原版或其他模组处理。
- **兼容性优先**：设计决策优先保证与 C2ME、Distant Horizons、TerraBlender、Biomes O' Plenty 等主流模组的共存能力。
- **地形即函数**：29 种地形类型各自拥有独立的、基于真实地质过程的数学函数签名，全部通过 JSON 数据包配置。

**当前版本**: `1.3.1-beta`（定义于 [`neoforge.mods.toml`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/META-INF/neoforge.mods.toml#L288)）。

---

## 2. 构建与运行配置

- **构建系统**: Gradle + NeoGradle (ModDev Gradle)。
- **关键依赖版本**:
  - NeoForge: `[21.1.219,)`
  - Minecraft: `[1.21.1,)`
  - Kotlin for Forge: `[5.10,)`
  - JUnit 5（Jupiter）用于测试。
- **JDK**: Java 21（Mixin `compatibilityLevel` 亦为 `JAVA_21`）。
- **运行命令**:
  - 完整构建: `gradlew.bat build`
  - 仅测试: `gradlew.bat test`
  - 客户端/服务端开发环境: `gradlew.bat runClient` / `gradlew.bat runServer`
- **Mixin 配置**: 仅一个通用 Mixin [`ServerChunkCacheMixin`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/worldscape.mixins.json)，在 `ServerChunkCache` 构造完成后输出诊断日志，不修改原版逻辑。
- **Access Transformer**: 暴露 `ChunkMap.worldGenContext` 与 `WorldGenContext.generator`。

> 注意：Windows 环境下项目只有 `gradlew.bat`，直接调用 `gradlew` 会报 `CommandNotFoundException`。

---

## 3. 宏观-微观两层架构

World Scape 的地形生成采用 **宏观-微观两层架构（Macro-Micro Architecture）**：

### 3.1 宏观层（Macro Layer）

- **核心类**: [`MacroVoronoiSystem`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/MacroVoronoiSystem.java)
- **单元尺寸**: 2048 格方块（`REGION_CELL_SIZE`）。
- **作用**:
  - 将世界划分为 Voronoi 单元，决定大陆框架。
  - 为每个单元分配海拔等级（Tier 0-5）。
  - 提供基准高度范围、构造类型（TectonicType）、气候带（ClimateZone）。
  - 计算单元之间的 `blendWeight`，用于边界平滑。
- **Tier 分布**（基于正态分布分位数）：
  - Tier 0（深海）: 8%
  - Tier 1（浅海）: 17%
  - Tier 2（海岸）: 25%
  - Tier 3（低地）: 30%
  - Tier 4（高地）: 15%
  - Tier 5（极高山）: 5%
- **基准高度范围**（可在 `config/worldscape/tier_heights.json` 覆盖）：
  - T0: -120 ~ -40
  - T1: -70 ~ 30
  - T2: 10 ~ 90
  - T3: 20 ~ 110
  - T4: 90 ~ 240
  - T5: 200 ~ 380

### 3.2 微观层（Micro Layer）

- **核心类**: [`ControlPointRegion`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/ControlPointRegion.java)
- **单元尺寸**: 512 格方块（`REGION_SIZE`）。
- **作用**:
  - 每个 512×512 区域内生成 3×3 = 9 个控制点。
  - 控制点类型和偏移量由 [`TerrainFieldSampler`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFieldSampler.java) 的连续噪声场驱动。
  - 通过 `energyToTier` 和 `selectTypeByMoisture` 确定地形类型。
  - 应用相邻约束迭代松弛，避免相邻区域高度差过大。

### 3.3 连续噪声场（TerrainFieldSampler）

- **能量场（Energy Field）**: 三层 NormalNoise 叠加（波长 ~256 / ~64 / ~512），驱动地形类型与偏移量。
- **湿度场（Moisture Field）**: NormalNoise，波长 ~2048，用于在同 tier 内选择具体地形类型。
- **附加函数**: `sampleFbm`、`sampleTurbulence`、`sampleDomainRotated`、`sampleEnergyStretched`，以及对应的缓存版本。
- **关键约束**: 不引入新的独立噪声场；所有地形生成必须复用已有噪声函数。

### 3.4 整合层（RegionController.calculateBlend）

- **核心类**: [`RegionController`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/RegionController.java)
- **作用**: 将微观控制点高度加权混合，并通过 `sqrt` 边界邻近度与 `tierGapFactor` 将微观高度拉回宏观基准。
- **修复历史**: 早期 `smoothstep` 在边界处影响范围过窄，后改为 `sqrt`；并引入 `tierGapFactor`（层级差距越大，宏观拉动力越强）以消除 Voronoi 边界悬崖。

---

## 4. 核心地形生成管线

地形生成的原版入口是 [`LandscapeChunkGenerator.fillFromNoise(...)`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/LandscapeChunkGenerator.java#L441)：

```text
fillFromNoise
  ├─ getRegionController()          → RegionController（按 world seed 单例）
  ├─ getNoiseSet()                  → NoiseSet（全局 LRU，按 seed 缓存）
  ├─ buildChunkBlendCache(...)      → BlendCache（单 chunk 3×3 控制点预收集）
  ├─ 对 chunk 内每列 (x, z):
  │    ├─ RegionController.getTerrainBlend(x, z, blendCache)
  │    ├─ TerrainCalculator.determineTerrainType(...)
  │    ├─ TerrainCalculator.calculateFinalHeight(...)
  │    │    └─ TerrainCalculator.calcHeightForType(...)  ← 核心数学签名
  │    ├─ 河流/侵蚀/冲积修正
  │    └─ fillColumn(...)           → 写入 BEDROCK / DEEPSLATE / STONE / 河流空气槽
  └─ （若启用）overrideTerrainBiomesInChunk(...)
```

地表构建入口是 [`LandscapeChunkGenerator.buildSurface(...)`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/LandscapeChunkGenerator.java#L355)：

- 通过 `SurfaceAdapterFactory` 创建 `SurfaceAdapter`（默认 `AUTO`，优先 `ReflectionSurfaceAdapter`，失败降级 `FallbackSurfaceAdapter`）。
- `buildSurface` 自行扫描 chunk 实际方块找到 `surfaceY`，河流/地形类型优先从 `riverCache`（`ThreadLocal`）读取。
- `SurfaceAdapter.buildSurface(context)` 放置地表/次表层方块。

---

## 5. 29 种地形类型与数学函数系统

### 5.1 类型分组

| 地质分组 | 数量 | 代表类型 |
|---------|------|---------|
| 构造力 | 7 | HIGH_MOUNTAINS, RIDGE, PEAK, HORN, CLIFF, PLATEAU, DOME |
| 风力 | 4 | DUNE, YARDANG, GOBI, SALT_FLAT |
| 水力 | 6 | CANYON, VALLEY, FLOODPLAIN, DELTA, ALLUVIAL_FAN, BASIN |
| 冰力 | 6 | FJORD, GLACIAL_VALLEY, CIRQUE, ICE_SHEET, SEA_CLIFF, BEACH |
| 特殊 | 6 | SINKHOLE, PEAK_FOREST, TRENCH, SEA_PLATEAU, HILLS, PLAINS |

### 5.2 关键机制

- **声明位置**: [`TerrainType.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainType.java) 使用 `public static final` 实例风格（非 enum）。
- **基础高度**: [`TerrainType.getBaseHeightForType(TerrainType)`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainType.java#L140) 是唯一数据源。
- **Tier 白名单**: 每种地形允许出现的 tier，在 `TerrainType.java` 静态块和 `defaults.json` 中保持一致。
- **控制点偏移**: `TerrainFieldSampler.getTypeModifier(...)` + `TerrainControlPoint.clampOffset(...)` 共同决定局部高度偏移；减法地形（峡谷、山谷等）使用负修饰符。
- **海岸验证**: `BEACH`/`DELTA` 必须通过 `ControlPointRegion.isNearOcean(...)` 验证海洋邻近性，否则替换为 `FLOODPLAIN`；有海洋时可能升级为 `SEA_CLIFF` 或 `FJORD`。

### 5.3 JSON 函数系统

- **默认定义文件**: [`src/main/resources/data/worldscape/worldscape/terrain_type/defaults.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/data/worldscape/worldscape/terrain_type/defaults.json)
- **解析器**: [`TerrainFunctionSchema`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFunctionSchema.java)
- **解释器**: [`TerrainFunctionInterpreter`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFunctionInterpreter.java)
- **Schema 字段**: `id`、`min_height`、`max_height`、`tier_whitelist`、`height_cap`、`coordinate_transform`、`functions`、`combinator`、`final`、`climate`。
- **支持的噪声原语**: `fbm`、`turbulence`、`domain_rotated`、`sine`、`gradient`、`gaussian`、`sigmoid`、`tanh_scaled`、`abs`、`negate`、`constant`、`math`、`gradient_constrained_sine`、`fm_sine`、`contributing_point_distance`。
- **核心结论**: `TerrainCalculator.calcHeightForType(...)` 不再包含任何 29 分支硬编码，所有地形的高度函数 100% 由 JSON 数据驱动。

### 5.4 不可变更的数学签名

```java
public static double calcHeightForType(int worldX, int worldZ,
                                        double baseHeight,
                                        TerrainType type,
                                        TerrainFieldSampler fs,
                                        RegionController.TerrainBlendResult blend)
```

- 位置: [`TerrainCalculator.java:113`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainCalculator.java#L113)
- 项目宪章将其列为硬性约束：签名不得变更，以保证 29 种地形的 JSON 配置和解释器兼容性。

---

## 6. 生物群系、地表方块与兼容性

### 6.1 生物群系覆盖（默认关闭）

- 配置项: `ConfigManager.Config.enableBiomeOverride`，默认值 `false`。
- 触发位置: [`LandscapeChunkGenerator.fillFromNoise`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/LandscapeChunkGenerator.java#L511-L522)。
- 机制: 开启后通过反射修改 `ProtoChunk` 的 `LevelChunkSection.biomes`（`PalettedContainer`），按 `TerrainBiomeRules` 为每个 4×4×4 生物群系细胞分配允许的生物群系。
- **设计原则**: 默认关闭，优先保证与 TerraBlender、Biomes O' Plenty 等生物群系模组的兼容性。

### 6.2 地形-生物群系规则（TerrainBiomeRules）

- 单例入口: [`TerrainBiomeRules.getInstance()`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/biome/TerrainBiomeRules.java#L47)
- 默认规则: 启动时为全部 29 种地形硬编码默认规则。
- 用户配置: 运行时读取 `config/worldscape/terrain_biome_rules.json`（首次不存在则生成）。
- 规则模式: 白名单或黑名单，支持生物群系 ID 和标签（如 `#minecraft:is_ocean`）。
- 回退: 若规则未加载或缓存为空，回退到 `minecraft:plains`。

### 6.3 地表适配器（Surface Adapter）

- **工厂**: [`SurfaceAdapterFactory`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/SurfaceAdapterFactory.java)
- **类型**:
  - `ReflectionSurfaceAdapter`: 反射调用原版 `SurfaceSystem.buildSurface()`，再叠加 World Scape 地表覆盖。主路径，但依赖内部 API 反射。
  - `FallbackSurfaceAdapter`: 完全自主实现，不依赖原版 `SurfaceSystem`，安全降级路径。
  - `AUTO`: 优先尝试 `REFLECTION`，反射不可用时自动降级 `FALLBACK`。
- **Fallback 地表方块逻辑**: 按 `TerrainType` 决定表面/次表层方块（高山雪/岩分层、沙漠/冰雪/水下类型、石头变体矿脉分组等）。

### 6.4 兼容性矩阵

- **不兼容（INCOMPATIBLE）**: 其他自定义 `ChunkGenerator` 模组（Big Globe、TerraForged、Amplified Nether 等）。
- **冲突（CONFLICT）**: C2ME、ModernFix、Distant Horizons、Biomes O' Plenty、Regions Unexplored、TerraBlender、YUNG's Better Caves 等。
- **已验证兼容（VERIFIED_COMPATIBLE）**: FerriteCore、Starlight、JourneyMap、Create、Waystones、KubeJS 等。
- **C2ME 兼容层**: [`C2MECompatibility.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/compat/c2me/C2MECompatibility.java) 检测到 C2ME 时将 `RegionController` 缓存上限从 1024 提升到 4096。
- **TerraBlender 兼容层**: [`TerraBlenderCompat.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/compat/TerraBlenderCompat.java) 已实现区域生物群系收集与地形映射，但当前无外部调用方。

---

## 7. 调试、可视化与数据导出

### 7.1 Voronoi 可视化系统（客户端专用）

- **入口**: [`WorldScapeVoronoiSystem`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/voronoi/WorldScapeVoronoiSystem.java)
- **功能**: 在屏幕上绘制宏观/微观 Voronoi 单元格、边界、控制点、信息面板。
- **视图模式**: `MACRO`（2048 格单元）与 `MICRO`（512 格控制点）。
- **算法**: [`VoronoiCalculator`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/voronoi/VoronoiCalculator.java) 实现 Fortune 扫描线算法。
- **持久化**: [`VoronoiDataPersistence`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/voronoi/VoronoiDataPersistence.java) 将控制点保存为 JSON。
- **已知问题**: `IncrementalVoronoiUpdater` 命名暗示增量更新，实际为全量重算。

### 7.2 调试工具

- [`TerrainDebugSystem`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainDebugSystem.java): 调试总开关与采样率。
- [`TerrainDebugTools`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainDebugTools.java): 双语地形查询、统计、PNG 导出（高度图、Voronoi 图、增强地形图、等高线图、统计图）、单/多线程一致性校验。
- [`DebugPillarManager`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/debug/DebugPillarManager.java): 在世界中放置玻璃/混凝土柱标记控制点。
- [`TerrainFrameLogger`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainFrameLogger.java): 按区块采样日志。

### 7.3 命令集成

所有调试命令注册在 [`CommandManager`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/command/CommandManager.java)：

- `/worldscape debug ...`: 导出、统计、状态、缓存清理、调试开关等。
- `/worldscape voronoi ...`: 开关、导入、保存、加载、清空 Voronoi 覆盖层。
- `/locate landscape <terrain>`: 搜索指定地形类型。

### 7.4 欢迎界面与世界存档导出

- **WelcomeScreen**: 游戏标题界面首次打开时显示，允许设置地形预设、河流强度、山脉高度、岛屿模式、Debug Mode。配置写入 `config/worldscape/settings.txt`。
- **WorldSaveDataExporter**: 在 `LevelEvent.Save` 时同步收集玩家周围区块数据，再异步写入 `worldscape_exports/<world>/<timestamp>/`，包含 `manifest.json` 与 `chunk_<cx>_<cz>.jsonl`。

---

## 8. 资源、Mixin 与数据包

### 8.1 Mixin

- **配置**: [`worldscape.mixins.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/worldscape.mixins.json)
- **唯一 Mixin**: [`ServerChunkCacheMixin`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/mixin/ServerChunkCacheMixin.java)
- **行为**: 在 `ServerChunkCache` 构造完成后，仅对主世界输出当前 `ChunkGenerator` 类名的 INFO 日志。纯诊断型，不修改原版逻辑。

### 8.2 数据包

- **默认地形类型**: [`data/worldscape/worldscape/terrain_type/defaults.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/data/worldscape/worldscape/terrain_type/defaults.json) — 29 种地形完整定义。
- **示例地形**: [`data/worldscape/worldscape/terrain_type/example/eroded_mesa.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/data/worldscape/worldscape/terrain_type/example/eroded_mesa.json) — 仅作文档，不会自动加载。
- **自定义维度**: [`data/worldscape/dimension/worldscape_overworld.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/data/worldscape/dimension/worldscape_overworld.json) — 使用 `worldscape:landscape` generator + 原版 `multi_noise` biome source。
- **覆盖方式**: 其他模组或数据包可在 `data/<namespace>/worldscape/terrain_type/` 下放置 JSON 文件追加或覆盖。

### 8.3 运行时配置

- `config/worldscape.toml`: `ConfigManager` 管理的 TOML 配置（`sea_level`、`enableBiomeOverride` 等）。
- `config/worldscape/settings.txt`: WelcomeScreen 配置。
- `config/worldscape/terrain_biome_rules.json`: 地形-群系规则（首次运行时生成）。
- `config/worldscape/tier_heights.json`: Tier 基准高度范围（可选覆盖）。

### 8.4 资源文件

- `assets/worldscape/lang/en_us.json` 与 `zh_cn.json`: 仅包含 Voronoi 可视化与命令相关的本地化键。
- 无纹理、模型、声音、着色器——符合“不添加新方块/实体”的项目定位。

---

## 9. 缓存与线程安全

### 9.1 已做线程安全处理的组件

| 组件 | 机制 | 说明 |
|------|------|------|
| `RegionController.terrainRegionCache` | `ConcurrentHashMap` + `computeIfAbsent` | 桶级锁，C2ME 并行生成时不同 regionKey 互不阻塞 |
| `RegionController.evictionInProgress` | `AtomicBoolean` | 保证单线程执行缓存淘汰 |
| `NoiseSet.LRU_CACHE` | `Collections.synchronizedMap` | 全局同步访问，C2ME 下可能成为热点 |
| `TerrainFieldSampler.instances` | `synchronizedMap` + `synchronized` 块 | 按 world seed 分实例 |
| `TerrainFieldSampler` 噪声缓存 | `ConcurrentHashMap` + `computeIfAbsent` | `fbmCache`/`turbulenceCache`/`domainRotatedCache` |
| `MacroVoronoiSystem` 缓存 | `Collections.synchronizedMap` (LRU) | `controlPointCache`/`adjustedTierCache`/`cellGridCache` |
| `LandscapeChunkGenerator` 懒加载引用 | `AtomicReference` + `compareAndSet` | `regionControllerRef`/`noiseSetRef`/`fieldSamplerRef`/`surfaceAdapterRef` |
| `riverCache` | `ThreadLocal<Map<Long, RiverCacheData>>` | 每线程独立 |
| 诊断计数器 | `AtomicInteger` | `warningCount`/`voidWarningCount`/`extremeSlopeCount` |

### 9.2 潜在 C2ME 风险

- `NoiseSet.LRU_CACHE` 的 `synchronizedMap` 可能成为全局锁竞争点。
- `MacroVoronoiSystem` 多个 `synchronizedMap` 缓存在高并发下可能串行化。
- `riverCache` 是 `ThreadLocal`，但 `fillFromNoise` 与 `buildSurface` 在并行场景下的阶段一致性未经验证。
- 整体并发路径尚未经过压力测试。

---

## 10. 测试体系

- **框架**: JUnit 5（Jupiter）。
- **测试文件**:
  - [`TerrainCalculatorTest.java`](file:///d:/.trae-project/Worl%20Scape/src/test/java/com/worldscape/terrain/TerrainCalculatorTest.java)
  - [`TerrainFunctionInterpreterTest.java`](file:///d:/.trae-project/Worl%20Scape/src/test/java/com/worldscape/terrain/TerrainFunctionInterpreterTest.java)
  - [`TerrainFieldSamplerTest.java`](file:///d:/.trae-project/Worl%20Scape/src/test/java/com/worldscape/terrain/TerrainFieldSamplerTest.java)
  - [`TestUtils.java`](file:///d:/.trae-project/Worl%20Scape/src/test/java/com/worldscape/terrain/TestUtils.java)
- **覆盖重点**: 种子确定性、跨种子隔离、`null` blend 回退、terrain type 回退映射、JSON 函数求值、自定义原语注册、缓存等价性。
- **未覆盖领域**: 完整 chunk 生成集成测试、生物群系覆盖、`SurfaceAdapter`、Voronoi 可视化、C2ME 并发压力测试、27/29 种地形的 JSON 函数直接测试。
- **最新运行结果**: `gradlew.bat test` → `BUILD SUCCESSFUL in 23s`。

---

## 11. 技术债务与风险

### 🔴 P0 级

1. **Voronoi 边界悬崖残留**: 已多轮修复，但可能是多子系统交互的残留问题。
2. **Tier 5 覆盖率极低**: 原始概率仅 ~5%，叠加邻居修正后高山地貌在多数种子中缺失。
3. **回退路径不一致**: `defaults.json` 解析失败、`ReflectionSurfaceAdapter` 降级、`TerrainBiomeRules` 白名单为空、`functionDef == null` 等回退触发后下游可能不知情。
4. **`ReflectionSurfaceAdapter` 反射脆弱**: 依赖 6+ 个 Minecraft 内部 API 反射，Java 17+ 模块系统或版本升级可能使其失效。
5. **C2ME 线程安全未经验证**: 设计已优化，但完整并行路径未实测。
6. **`enableBiomeOverride` 反射风险**: 反射修改 `LevelChunkSection.biomes`，某些 JVM 配置下可能失效。
7. **种子分析器/查找器永久禁用**: 验证地形只能依赖游戏内加载、诊断日志或单元测试。

### 🟡 P1 级

8. **Distant Horizons 兼容性**: 与自定义 `ChunkGenerator` 存在已知不兼容风险。
9. **`TerraBlenderCompat` 未接入主流程**: 代码存在但无外部调用方。
10. **`IncrementalVoronoiUpdater` 命名与实现不符**: 实际为全量重算。
11. **调试工具中仍有硬编码数值**: 未完全迁移到 `WorldScapeConstants`。
12. **`applyCarvers` 与 `spawnOriginalMobs` 为 No-op**: 洞穴雕刻与原版生物生成缺失。
13. **`settings == null` 降级保护不足**: 降级后世界光秃秃且无明显报错。
14. **`RegionController` 缓存非真正 LRU**: 淘汰不保证最久未访问。
15. **`WorldSaveDataExporter` 使用已弃用 API**: 编译警告。

### 🟢 P2 级

16. **测试覆盖不足**。
17. **`NoiseSet.LRU_CACHE` 可考虑 `ConcurrentHashMap`**。
18. **`TerrainDebugSystem` 中存在未使用字段**。

---

## 12. 关键决策历史

- **2026-06-21**: 全项目审计修复（P0/P1 共 12 项）：`Biome` key 反射改为 `Holder.unwrapKey()`、`HashMap` 改 `ConcurrentHashMap`、HeightCalculator 与 RegionController 边界混合公式同步、`EROSION_NOISE_RANGE` 常量引用等。
- **2026-06-20**: 海岸与气候敏感地形审计：减法地形 `typeModifier` 统一为负、`FJORD`/`ICE_SHEET` 检查 `GLACIAL` 气候、`CANYON` level 调整、`determineTerrainType` tier 2 回退改为 `FLOODPLAIN`。
- **2026-06-19**: `BEACH`/`DELTA` 海洋邻近性验证、控制点专属地形（`SEA_CLIFF`/`FJORD`/`DOME`）显式放置、海岸变体使用独立 salt。
- **2026-06-15**: 控制点使用 `macroElevationTier` 作为主等级，能量噪声仅作 ±1 修正；修复悬崖、表面方块、冰层、石头变体问题。
- **2026-05-30**: C2ME 兼容性优化：`RegionController` 改用 `ConcurrentHashMap` + `AtomicBoolean`、`TerrainFieldSampler` 按 seed 分实例缓存。
- **2026-05-23**: v4.0 “地形即函数” 重构：29 种地形类型各自拥有基于地质成因的 JSON 数学函数签名。
- **2026-05-20**: 发现 `calculateFinalHeight` 中 `blendFactor` 公式反转是真正悬崖根因，修复为单调递增公式。

---

## 13. 关键文件索引

### 核心生成

- [`LandscapeChunkGenerator.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/LandscapeChunkGenerator.java)
- [`RegionController.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/RegionController.java)
- [`HeightCalculator.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/HeightCalculator.java)
- [`TerrainCalculator.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainCalculator.java)
- [`TerrainFieldSampler.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFieldSampler.java)
- [`ControlPointRegion.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/ControlPointRegion.java)
- [`MacroVoronoiSystem.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/MacroVoronoiSystem.java)
- [`NoiseSet.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/NoiseSet.java)

### 地形类型与函数

- [`TerrainType.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainType.java)
- [`TerrainFunctionInterpreter.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFunctionInterpreter.java)
- [`TerrainFunctionSchema.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFunctionSchema.java)
- [`defaults.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/data/worldscape/worldscape/terrain_type/defaults.json)

### 生物群系、地表与兼容性

- [`TerrainBiomeRules.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/biome/TerrainBiomeRules.java)
- [`BiomeMapper.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/biome/BiomeMapper.java)
- [`ReflectionSurfaceAdapter.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/ReflectionSurfaceAdapter.java)
- [`FallbackSurfaceAdapter.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/FallbackSurfaceAdapter.java)
- [`SurfaceAdapterFactory.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/generator/SurfaceAdapterFactory.java)
- [`ModCompatibilityChecker.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/compat/ModCompatibilityChecker.java)
- [`C2MECompatibility.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/compat/c2me/C2MECompatibility.java)
- [`TerraBlenderCompat.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/compat/TerraBlenderCompat.java)

### 入口、配置与命令

- [`WorldScape.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/WorldScape.java)
- [`ConfigManager.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/config/ConfigManager.java)
- [`CommandManager.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/command/CommandManager.java)
- [`WelcomeScreen.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/config/WelcomeScreen.java)

### 调试、可视化与导出

- [`WorldScapeVoronoiSystem.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/voronoi/WorldScapeVoronoiSystem.java)
- [`VoronoiOverlayRenderer.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/voronoi/VoronoiOverlayRenderer.java)
- [`TerrainDebugTools.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainDebugTools.java)
- [`WorldSaveDataExporter.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/export/WorldSaveDataExporter.java)

### 资源与 Mixin

- [`worldscape.mixins.json`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/worldscape.mixins.json)
- [`ServerChunkCacheMixin.java`](file:///d:/.trae-project/Worl%20Scape/src/main/java/com/worldscape/mixin/ServerChunkCacheMixin.java)
- [`neoforge.mods.toml`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/META-INF/neoforge.mods.toml)
- [`accesstransformer.cfg`](file:///d:/.trae-project/Worl%20Scape/src/main/resources/META-INF/accesstransformer.cfg)

### 测试

- [`src/test/java/com/worldscape/terrain/`](file:///d:/.trae-project/Worl%20Scape/src/test/java/com/worldscape/terrain/)

### 文档与记忆

- [`CLAUDE.md`](file:///d:/.trae-project/Worl%20Scape/CLAUDE.md)
- [`.trae/rules/memory.md`](file:///d:/.trae-project/Worl%20Scape/.trae/rules/memory.md)
- [`PROJECT_REPORT.md`](file:///d:/.trae-project/Worl%20Scape/PROJECT_REPORT.md)
- [`.trae/goal/comprehensive-project-understanding/`](file:///d:/.trae-project/Worl%20Scape/.trae/goal/comprehensive-project-understanding/)

---

## 14. 总结

World Scape 是一个架构清晰、高度可配置、以兼容性为优先的 NeoForge 地形生成模组。其核心创新在于：

1. **宏观-微观两层架构**：2048 格 Voronoi 决定大陆框架，512 格控制点由连续噪声场驱动细节。
2. **地形即函数**：29 种地形类型的数学签名完全由 JSON 数据包定义，解释执行。
3. **兼容性优先**：地形与生物群系解耦、默认关闭生物群系覆盖、提供多重安全回退。

当前最需关注的风险集中在：

- **P0**: Voronoi 边界悬崖残留、Tier 5 低覆盖率、反射 SurfaceAdapter 的脆弱性、C2ME 并发路径未验证。
- **P1**: Distant Horizons 兼容、`TerraBlenderCompat` 未接入、调试工具硬编码、雕刻器缺失。

后续开发应在解决 P0 风险的同时，保持“兼容性优先”的设计哲学，并继续将魔法数字迁移到 `WorldScapeConstants`、补充集成与并发测试。
