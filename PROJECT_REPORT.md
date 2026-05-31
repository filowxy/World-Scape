# World Scape - 项目报告 / Project Report

> **版本**: Beta 1.3.1  
> **作者**: Filowxy  
> **协议**: Apache License 2.0  
> **类型**: Minecraft NeoForge 1.21.1 地形生成模组  
> **核心定位**: 完全替换原版地形生成，基于 Voronoi 控制点 + 连续噪声场的"地形即函数"框架  
> **报告生成日期**: 2026-05-30

---

## 目录 / Table of Contents

1. [项目概述 / Project Overview](#1-项目概述--project-overview)
2. [构建系统与依赖 / Build System & Dependencies](#2-构建系统与依赖--build-system--dependencies)
3. [项目结构 / Project Structure](#3-项目结构--project-structure)
4. [核心架构 / Core Architecture](#4-核心架构--core-architecture)
5. [模块详解 / Module Details](#5-模块详解--module-details)
6. [地形系统 / Terrain System](#6-地形系统--terrain-system)
7. [Voronoi 可视化系统 / Voronoi Visualization System](#7-voronoi-可视化系统--voronoi-visualization-system)
8. [生物群落系统 / Biome System](#8-生物群落系统--biome-system)
9. [表面构建系统 / Surface Building System](#9-表面构建系统--surface-building-system)
10. [调试系统 / Debug System](#10-调试系统--debug-system)
11. [兼容性系统 / Compatibility System](#11-兼容性系统--compatibility-system)
12. [配置与欢迎界面 / Configuration & Welcome Screen](#12-配置与欢迎界面--configuration--welcome-screen)
13. [命令行系统 / Command System](#13-命令行系统--command-system)
14. [种子分析器 / Seed Analyzer](#14-种子分析器--seed-analyzer)
15. [MCP 服务器集成 / MCP Server Integration](#15-mcp-服务器集成--mcp-server-integration)
16. [资源与数据文件 / Resources & Data Files](#16-资源与数据文件--resources--data-files)
17. [已知问题 / Known Issues](#17-已知问题--known-issues)
18. [开发环境 / Development Environment](#18-开发环境--development-environment)

---

## 1. 项目概述 / Project Overview

### 1.1 项目目标

World Scape 是一个 Minecraft NeoForge 1.21.1 模组，**完全替换原版地形生成系统**。其核心理念是：

> **"地形即函数"** — 每种地形类型根据真实地质成因，分配专属数学函数组合，通过 Voronoi 控制点 + 连续噪声场实现无限变化的自然地形。

### 1.2 核心特性

- **29 种地形类型**：从深海沟到高耸山峰，涵盖构造力、风力、水力、冰力等地质成因
- **两级 Voronoi 系统**：宏观（2048 格单元）控制海拔等级，微观（512 格区域）控制局部地形细节
- **连续噪声场驱动**：所有控制点类型和偏移量由全局噪声场决定，确保边界自然过渡
- **自适应表面构建**：反射式 + 回退式双适配器模式，确保跨版本兼容性
- **生物群落映射**：29 种地形类型映射到 Minecraft 生物群落，地形决定生态
- **内置调试系统**：高度图导出、Voronoi 可视化、地形查询等完整调试工具
- **种子分析器**：离线分析世界种子，预测地形分布、悬崖位置、海洋质量等

### 1.3 技术栈

| 技术 | 用途 |
|------|------|
| NeoForge 21.1.219 | 模组加载框架 |
| Gradle 8.10 | 构建系统 |
| Java 21 | 开发语言 |
| Mixin 0.8.7 | 字节码注入 |
| CFR 0.152 | 反编译器（灾难恢复用） |
| Minecraft 1.21.1 | 目标游戏版本 |

---

## 2. 构建系统与依赖 / Build System & Dependencies

### 2.1 构建配置

使用 NeoForge ModDev 插件，配置文件位于 [build.gradle](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/build.gradle)：

```groovy
plugins {
    id 'net.neoforged.moddev' version '2.0.125'
}

neoForge {
    version = "21.1.219"
    mods {
        worldscape {
            sourceSet sourceSets.main
        }
    }
    runs {
        client { client() }
        server { server() }
    }
    accessTransformers = files('src/main/resources/META-INF/accesstransformer.cfg')
}
```

### 2.2 依赖管理

| 依赖 | 版本 | 用途 |
|------|------|------|
| NeoForge | 21.1.219+ | 模加载框架 |
| Minecraft | 1.21.1+ | 游戏本体 |
| Mixin (SpongePowered) | 0.8.7 | 编译期 Mixin 注解处理器 |

### 2.3 特殊任务

- `runSeedAnalyzer`：使用 `com.worldscape.analyzer.SeedAnalyzer` 作为主类运行种子分析器

### 2.4 Access Transformer

配置文件：[accesstransformer.cfg](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/META-INF/accesstransformer.cfg)

暴露了 NeoForge 中的两个包内私有字段：
- `net.minecraft.server.level.ChunkMap.worldGenContext`
- `net.minecraft.world.level.chunk.status.WorldGenContext.generator`

### 2.5 Gradle 配置

- **Gradle 版本**: 8.10
- **分发 URL**: `https://services.gradle.org/distributions/gradle-8.10-bin.zip`

---

## 3. 项目结构 / Project Structure

```
Worl Scape/
├── build.gradle                         # Gradle 构建配置
├── gradle/wrapper/
│   └── gradle-wrapper.properties        # Gradle 版本配置
├── src/
│   ├── main/
│   │   ├── java/com/worldscape/
│   │   │   ├── WorldScape.java          # 模组主入口
│   │   │   ├── WorldScapeClient.java    # 客户端代理
│   │   │   ├── mixin/
│   │   │   │   └── ServerChunkCacheMixin.java  # 区块缓存 Mixin
│   │   │   ├── analyzer/
│   │   │   │   ├── SeedAnalyzer.java          # 种子分析器
│   │   │   │   ├── SeedAnalyzerVerification.java # 验证器
│   │   │   │   └── ClimateInterpolatorTest.java  # 气候插值测试
│   │   │   ├── biome/
│   │   │   │   ├── BiomeMapper.java           # 生物群落映射
│   │   │   │   └── TerrainBiomeRules.java     # 地形-群系规则
│   │   │   ├── command/
│   │   │   │   └── CommandManager.java        # 命令管理
│   │   │   ├── compat/
│   │   │   │   ├── ModCompatibilityChecker.java     # 兼容性检查
│   │   │   │   ├── IncompatibleModWarningScreen.java # 不兼容警告
│   │   │   │   ├── TerraBlenderCompat.java          # TerraBlender 兼容
│   │   │   │   └── c2me/
│   │   │   │       └── C2MECompatibility.java       # C2ME 兼容
│   │   │   ├── config/
│   │   │   │   ├── ConfigManager.java        # 配置管理
│   │   │   │   ├── WelcomeScreen.java        # 欢迎界面
│   │   │   │   ├── WelcomeScreenConfig.java  # 欢迎界面配置
│   │   │   │   └── WelcomeScreenAssets.java  # 欢迎界面资源
│   │   │   ├── debug/
│   │   │   │   ├── TerrainDebugSystem.java   # 调试系统核心
│   │   │   │   ├── TerrainDebugTools.java    # 调试工具集
│   │   │   │   ├── TerrainFrameLogger.java   # 帧日志
│   │   │   │   └── DebugPillarManager.java   # 调试柱子
│   │   │   ├── generator/
│   │   │   │   ├── LandscapeChunkGenerator.java  # 核心区块生成器
│   │   │   │   ├── SurfaceAdapter.java          # 表面适配器接口
│   │   │   │   ├── SurfaceAdapterFactory.java   # 适配器工厂
│   │   │   │   ├── ReflectionSurfaceAdapter.java # 反射式适配器
│   │   │   │   └── FallbackSurfaceAdapter.java  # 回退式适配器
│   │   │   ├── terrain/
│   │   │   │   ├── WorldScapeConstants.java     # 全局常量
│   │   │   │   ├── TerrainCalculator.java       # 地形计算核心
│   │   │   │   ├── TerrainType.java             # 地形类型枚举
│   │   │   │   ├── RegionController.java        # 区域控制器
│   │   │   │   ├── HeightCalculator.java        # 高度计算
│   │   │   │   ├── ControlPointManager.java     # 控制点管理
│   │   │   │   ├── ControlPointRegion.java      # 控制点区域
│   │   │   │   ├── TerrainControlPoint.java     # 地形控制点
│   │   │   │   ├── MacroVoronoiSystem.java      # 宏观 Voronoi
│   │   │   │   ├── MacroRegionInfo.java         # 宏观区域信息
│   │   │   │   ├── NoiseSet.java                # 噪声集
│   │   │   │   ├── TerrainFieldSampler.java     # 地形场采样器
│   │   │   │   ├── TerrainVoronoiCache.java     # Voronoi 缓存
│   │   │   │   ├── TerrainContext.java          # 地形上下文
│   │   │   │   ├── TerrainType.java             # 地形类型枚举
│   │   │   │   ├── RiverNoiseSampler.java       # 河流噪声采样
│   │   │   │   └── RiverInfo.java               # 河流信息
│   │   │   ├── util/
│   │   │   │   ├── ClimateUtils.java            # 气候工具
│   │   │   │   ├── NoiseUtils.java              # 噪声工具
│   │   │   │   ├── WorldScapeUtils.java         # 通用工具
│   │   │   │   └── SeedDeriver.java             # 种子派生
│   │   │   └── voronoi/
│   │   │       ├── WorldScapeVoronoiSystem.java  # Voronoi 可视化系统
│   │   │       ├── VoronoiCalculator.java        # Voronoi 计算
│   │   │       ├── VoronoiDiagram.java           # Voronoi 图
│   │   │       ├── VoronoiCell.java              # Voronoi 单元
│   │   │       ├── VoronoiEdge.java              # Voronoi 边
│   │   │       ├── VoronoiVertex.java            # Voronoi 顶点
│   │   │       ├── VoronoiControlPoint.java      # Voronoi 控制点
│   │   │       ├── VoronoiControlPointManager.java # 控制点管理器
│   │   │       ├── VoronoiSpatialIndex.java      # 空间索引
│   │   │       ├── VoronoiDataPersistence.java   # 数据持久化
│   │   │       ├── VoronoiCamera.java            # 相机
│   │   │       ├── VoronoiOverlayRenderer.java   # 覆盖渲染器
│   │   │       ├── VoronoiInputHandler.java      # 输入处理器
│   │   │       ├── VoronoiInputEvents.java       # 输入事件
│   │   │       ├── VoronoiViewMode.java          # 视图模式
│   │   │       ├── IncrementalVoronoiUpdater.java # 增量更新器
│   │   │       ├── TerrainFeatureCalculator.java # 地形特征计算
│   │   │       └── TerrainFeatures.java          # 地形特征
│   │   └── resources/
│   │       ├── META-INF/
│   │       │   ├── neoforge.mods.toml            # 模组元数据
│   │       │   └── accesstransformer.cfg         # 访问转换器
│   │       ├── worldscape.mixins.json            # Mixin 配置
│   │       ├── assets/worldscape/lang/
│   │       │   ├── en_us.json                    # 英文语言文件
│   │       │   └── zh_cn.json                    # 中文语言文件
│   │       └── data/
│   │           ├── worldscape/dimension/
│   │           │   └── worldscape_overworld.json # 自定义维度
│   │           ├── worldscape/dimension_type/
│   │           │   └── worldscape_overworld_type.json # 维度类型
│   │           └── minecraft/
│   │               ├── dimension/overworld.json  # 覆写原版主世界
│   │               └── dimension_type/
│   │                   ├── overworld.json         # 覆写主世界类型
│   │                   ├── overworld_caves.json   # 覆写洞穴类型
│   │                   ├── the_nether.json        # 覆写下界
│   │                   └── the_end.json           # 覆写末地
├── .trae/
│   ├── mcp.json                                  # MCP 服务器配置
│   ├── rules/
│   │   ├── CLAUDE.md                             # 工作规则
│   │   ├── memory.md                             # 长期记忆文件
│   │   └── 所有代码必须确保兼容性.md              # 兼容性规则
│   ├── commands/                                 # 自定义命令
│   └── .ignore                                   # 忽略规则
├── mcp-servers/
│   ├── minecraft-server-mcp/                     # Minecraft 服务器 MCP
│   └── modrinth-mcp/                             # Modrinth API MCP
└── .github/ISSUE_TEMPLATE/config.yml             # Issue 模板
```

---

## 4. 核心架构 / Core Architecture

### 4.1 三层地形生成架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       宏观层 (MacroVoronoiSystem)                     │
│  ─ 单元大小: 2048 格                                                   │
│  ─ 过渡带宽: 400-800 格                                                │
│  ─ 提供: 海拔等级 (Tier 0-5)、基准高度、混合权重                       │
│  ─ 驱动: 低频 Perlin 噪声                                             │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                      微观层 (RegionController)                         │
│  ─ 控制点区域: 512×512 格                                              │
│  ─ 控制点间距: ~256 格（±128 格随机偏移）                              │
│  ─ 影响半径: 600-1000 格（按地形类型变化）                             │
│  ─ 提供: 局部地形类型、高度偏移                                         │
│  ─ 驱动: TerrainFieldSampler（连续噪声场）                              │
└─────────────────────────────────────────────────────────────────────────┘
                                    ↓
┌─────────────────────────────────────────────────────────────────────────┐
│                     地形特征层 (TerrainCalculator)                     │
│  ─ 29 种地形类型的数学函数                                              │
│  ─ 河流侵蚀 + 冲积扇沉积                                                │
│  ─ 宏观-微观混合权重计算                                                │
│  ─ 提供: 最终高度、河流/冲积因子                                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 4.2 模组初始化流程

```
Mod 构造 (WorldScape)
├── 注册自定义 ChunkGenerator (worldscape:landscape)
├── 注册 CommandManager
├── CommonSetup: 日志记录
└── ClientSetup:
    ├── 初始化 Voronoi 可视化系统
    └── 初始化地形调试系统
```

### 4.3 区块生成流程

```
fillFromNoise (NOISE 阶段)
├── 获取 RegionController (延迟初始化)
├── 获取 NoiseSet (延迟初始化)
├── buildChunkBlendCache (预收集 3×3 区域控制点)
├── 遍历 16×16=256 列:
│   ├── getTerrainBlend (使用 BlendCache 快路径)
│   ├── determineTerrainType
│   ├── calculateFinalHeight
│   ├── getRiverErosionIntensity
│   ├── getAlluvialFactor
│   ├── isRiverAt / getRiverDepthAt
│   └── calculateErodedHeight
├── 异常检测 (Debug 模式)
├── overrideTerrainBiomesInChunk (反射覆盖生物群系)
├── world_scape_fillColumn (填充基岩/深板岩/石头)
└── 缓存河流数据到 RiverCache

buildSurface (SURFACE 阶段)
├── 获取 SurfaceAdapter (延迟初始化)
├── 从 RiverCache 获取河流数据
└── 调用适配器构建表面方块
```

### 4.4 关键设计模式

| 模式 | 使用位置 | 说明 |
|------|----------|------|
| **适配器模式** | SurfaceAdapter + 实现类 | 反射式 + 回退式双策略适应不同 Minecraft 版本 |
| **工厂模式** | SurfaceAdapterFactory | 根据可用性自动选择适配器 |
| **策略模式** | TerrainCalculator | 每种地形类型独立计算策略 |
| **单例 + 缓存** | NoiseSet/TerrainFieldSampler | 按世界种子缓存噪声实例 |
| **延迟初始化** | 所有核心组件 | 构造函数不执行耗时操作，按需创建 |
| **双重检查锁定** | 所有单例 | volatile + synchronized 保证线程安全 |
| **BlendCache** | 区块级预缓存 | 避免每格重复搜索控制点，性能提升 100-200x |

---

## 5. 模块详解 / Module Details

### 5.1 模组主入口 — [WorldScape.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/WorldScape.java)

```java
@Mod(value="worldscape")
public class WorldScape {
    public static final String MOD_ID = "worldscape";
    public static final Logger LOGGER = LoggerFactory.getLogger(WorldScape.class);
}
```

**职责**：
- 注册自定义区块生成器 `worldscape:landscape`
- 初始化命令系统
- 初始化 Voronoi 可视化系统（客户端）
- 初始化地形调试系统（客户端）

### 5.2 区块生成器 — [LandscapeChunkGenerator.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/generator/LandscapeChunkGenerator.java)

这是整个模组的**绝对核心**，继承 `net.minecraft.world.level.chunk.ChunkGenerator`。

**关键字段**：
- `worldSeed`：世界种子，所有噪声的源头
- `biomeSource`：生物群系源
- `settings`：噪声生成设置 (Holder)
- `seaLevel`：海平面高度（默认 63）
- `minY` / `height`：世界高度范围

**延迟初始化的缓存**：
- `regionControllerRef`：区域控制器
- `noiseSetRef`：噪声集
- `heightCalculatorRef`：高度计算器
- `fieldSamplerRef`：地形场采样器
- `surfaceAdapterRef`：表面适配器

**关键方法**：
- `fillFromNoise()`：NOISE 阶段核心，生成地形方块
- `buildSurface()`：SURFACE 阶段，构建表面方块
- `overrideTerrainBiomesInChunk()`：基于地形类型覆盖生物群系
- `getBaseColumn()` / `getBaseHeight()`：基础列/高度查询
- `codec()`：序列化/反序列化

### 5.3 Mixin — [ServerChunkCacheMixin.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/mixin/ServerChunkCacheMixin.java)

在 `ServerChunkCache` 构造完成后注入，仅记录主世界使用的区块生成器信息。

### 5.4 命令系统 — [CommandManager.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/command/CommandManager.java)

注册 `/worldscape` 命令，包含以下子命令：
- `debug export_heightmap` / `export_voronoi` / `export_enhanced` / `export_contour`：调试导出
- `debug status` / `query` / `summary`：调试查询
- `debug c2me_report`：C2ME 兼容性报告
- `debug clear_cache`：清除缓存
- `debug pillars`：调试柱子可视化
- `debug clear_fluids`：清除流体
- `voronoi toggle/populate/save/load/clear/center/status`：Voronoi 可视化控制

### 5.5 工具类模块

#### [SeedDeriver.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/util/SeedDeriver.java)

负责从世界种子派生各种噪声实例的种子，确保确定性——相同世界种子总是产生相同地形。

关键盐值包括：`SALT_FBM_OCTAVE_0~5`、`SALT_DOMAIN_ANGLE/OFFSET_X/OFFSET_Z`、`SALT_RIVER_PATH/WIDTH/DRAINAGE` 等。

#### [NoiseUtils.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/util/NoiseUtils.java)

提供噪声组合、duneNoise、bowlNoise 等数学工具函数。

#### [ClimateUtils.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/util/ClimateUtils.java)

气候参数（温度、湿度、季节性、大陆度）的计算与混合，以及海拔校正。

#### [WorldScapeUtils.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/util/WorldScapeUtils.java)

提供 clamp、smoothstep、lerp 等通用数学函数。

---

## 6. 地形系统 / Terrain System

### 6.1 地形类型枚举 — [TerrainType.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainType.java)

定义 29 种地形类型，按地质成因分为五大类：

#### 构造力地形 (7 种)
| 类型 | 高度范围 | 数学签名 |
|------|----------|----------|
| HIGH_MOUNTAINS | 260-512 | fBm(6,0.5)×200 + 域旋转×15 + 湍流×20 |
| RIDGE | 140-275 | 正弦波骨架(梯度约束) + fBm×150 + 湍流×15 |
| PEAK | 165-330 | fBm(6,0.4)×120 + 湍流×80 + 域旋转×12 |
| HORN | 165-330 | fBm(6,0.3)×100 + 湍流(0.8)×70 |
| CLIFF | 55-220 | fBm(6,0.5)×80 + tanh×40 |
| PLATEAU | 165-275 | fBm(3,0.3)×100 |
| DOME | 83-193 | gaussian(σ=200)×150 |

#### 风力地形 (4 种)
| 类型 | 高度范围 | 数学签名 |
|------|----------|----------|
| DUNE | 28-55 | |sin(windDir)|×25 + sin(perpDir)×8 + fBm×5 |
| YARDANG | 44-99 | `sin(freqMod×方向性)×30 + 湍流锐化×10 + 域旋转×15` |
| GOBI | 33-66 | `fBm(4,0.7)×15 + 湍流碎石×3` |
| SALT_FLAT | 22-33 | `fBm(2,0.1)×3 + 湍流裂纹×1.5` |

#### 水力地形 (6 种)
| 类型 | 高度范围 | 数学签名 |
|------|----------|----------|
| CANYON | 11-83 | |fBm|×60 + fBm×10 |
| VALLEY | 28-83 | sigmoid(gradient)×40 + fBm×10 |
| FLOODPLAIN | 28-44 | fBm(3,0.15)×5 + riverStripe×2 |
| DELTA | 22-39 | gradient×10 + 域旋转×8 |
| ALLUVIAL_FAN | 44-110 | erf(控制点中心dist%200)×25 + fBm×5 |
| BASIN | 22-66 | gaussian(σ=300)×30 |

#### 冰力地形 (6 种)
| 类型 | 高度范围 | 数学签名 |
|------|----------|----------|
| FJORD | 17-110 | 湍流(0.7)×55 + tanh(cliff)×80 |
| GLACIAL_VALLEY | 28-110 | sigmoid(gradient)×60 + fBm×8 |
| CIRQUE | 83-193 | gaussian(σ=150)×70 + 湍流×40 |
| ICE_SHEET | 55-165 | `fBm(3,0.2)×8 + 方向性脊线×3 + 湍流裂隙×5` |
| SEA_CLIFF | 44-110 | fBm(4,0.4)×tanh(3)×100 |
| BEACH | 28-39 | fBm(2,0.2)×sigmoid×5 |

#### 特殊地形 (6 种)
| 类型 | 高度范围 | 说明 |
|------|----------|------|
| SINKHOLE | 11-55 | |gaussian(σ=80)|×40 陡峭坑壁 |
| PEAK_FOREST | 83-193 | 湍流(0.7)×80 + fBm×40 |
| TRENCH | -55-0 | sigmoid×30 + 基础偏移-20 |
| SEA_PLATEAU | -28-17 | fBm(3,0.15)×15 + 纹理×2 |
| HILLS | 55-110 | fBm(6,0.65)×40 + 湍流起伏×30 |
| PLAINS | 33-55 | fBm(4,0.2)×15 + 长波×3 + 湍流沟纹×1.5 |

### 6.2 6 级海拔体系

| 等级 | 基准高度 | 包含地形类型 | 分布 |
|------|----------|-------------|------|
| 0: 深海 | -80 | TRENCH, SEA_PLATEAU | ~9% |
| 1: 浅海 | -20 | SEA_PLATEAU, DELTA | ~15% |
| 2: 沿海 | 10 | BEACH, DELTA, FLOODPLAIN, DUNE, SALT_FLAT | ~27% |
| 3: 低地 | 60 | PLAINS, HILLS, FLOODPLAIN, DUNE, GOBI, YARDANG, BASIN, SINKHOLE, PEAK_FOREST | ~32% |
| 4: 高地 | 160 | HILLS, CLIFF, PLATEAU, VALLEY, CANYON, ALLUVIAL_FAN, GOBI, CIRQUE, GLACIAL_VALLEY | ~12% |
| 5: 山脉 | 300 | HIGH_MOUNTAINS, CLIFF, PLATEAU, RIDGE, PEAK, CIRQUE, HORN, ICE_SHEET, GLACIAL_VALLEY | ~5% |

**Tier 上限概率**：6 级初始均匀分布后施加 cap，分布为 10%/25%/35%/30%（分别 cap 到 T2/T3/T4/T5），非 spawn 区域 T4+T5≈17%。Spawn 中心单元（2048×2048）受海洋约束降级，T4+T5≈10%。

**宏微观整合**：宏观系统提供海拔等级和基准高度，微观系统在等级白名单中选择具体地形类型。`ELEVATION_BASE_HEIGHTS = [-80, -20, 10, 60, 160, 300]`。

### 6.3 全局常量 — [WorldScapeConstants.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/WorldScapeConstants.java)

包含约 250+ 个命名常量，涵盖：
- 地形高度范围（MIN_TERRAIN_HEIGHT=-64, MAX_TERRAIN_HEIGHT=512）
- 噪声参数（FBM_OCTAVES=6, FBM_LACUNARITY=2.0, FBM_GAIN=0.5）
- 各地形类型数学函数的频率/振幅参数
- 河流侵蚀参数
- 宏观-微观混合权重阈值
- Voronoi 可视化参数
- 生物群落气候参数

### 6.4 核心地形计算 — [TerrainCalculator.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainCalculator.java)

纯静态工具类，提供：

```java
calculateFinalHeight(x, z, blend, type, noiseSet, fs) → double
calcHeightForType(worldX, worldZ, baseHeight, type, fs, blend) → double
determineTerrainType(blend) → TerrainType
getRiverErosionIntensity(worldX, worldZ, noiseSet, baseHeight, seaLevel, blend) → double
getAlluvialFactor(worldX, worldZ, noiseSet, baseHeight, seaLevel) → double
calculateErodedHeight(continuousHeight, isRiver, riverDepth, seaLevel, erosion, alluvial, erosionMultiplier) → int
isRiverAt(worldX, worldZ, noiseSet) → boolean
getRiverDepthAt(worldX, worldZ, noiseSet, surfaceHeight, seaLevel, isRiver, depthMultiplier) → double
getErosionMultiplierForTier(elevationTier) → double  // Tier≥5: 1.5, Tier≤2: 0.5
getRiverDepthMultiplierForTier(elevationTier) → double  // Tier≥4: 1.5, Tier≤2: 0.8
```

**地形混合算法**：
- `dominantWeight ≥ 0.4`：使用 dominantType 的纯高度
- `dominantWeight < 0.4`：dominantType 和当前类型按权重线性混合
- `blendFactor = dominantWeight / 0.4` 确保阈值处 C0 连续

### 6.5 连续噪声场 — [TerrainFieldSampler.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainFieldSampler.java)

**设计目标**：替代旧版独立随机生成，使用连续噪声场驱动控制点类型和偏移量。

**按种子缓存**：使用 `ConcurrentHashMap<Long, TerrainFieldSampler>` 按世界种子缓存实例。

**核心字段**：
- `energyNoise` / `detailNoise`：地形能量场（主频 1/4096，细节 1/1024）
- `moistureNoise`：湿润度场（主频 1/2048）

**关键方法**：
- `sampleFbm(x, z, octaves, gain)`：分形布朗运动采样
- `sampleTurbulence(x, z, strength)`：湍流噪声 (|noise×2-1|)
- `sampleDomainRotated(x, z, warpStrength)`：域旋转扭曲
- `calculateGradient(x, z)`：Sobel 梯度计算
- `sigmoid(t)` / `tanhScaled(t, steepness)` / `gaussian(x, z, sigma)`：数学函数

### 6.6 宏观 Voronoi — [MacroVoronoiSystem.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/MacroVoronoiSystem.java)

**职责**：生成 2048 格单元的宏观 Voronoi 图，决定海拔等级分布。

**关键特性**：
- 17×17 控制点网格（跨越 ~34000 格范围）
- 单元间过渡带宽 400-1000 格
- 6 级海拔体系（Tier 0-5），`getRawElevationTier()` 使用 10%/25%/35%/30% cap 概率
- Spawn 中心单元（`SPAWN_OCEAN_RADIUS_CELLS=1`）受海洋约束：`random.nextInt(2)` 强制 T0/T1
- LRU 缓存区域信息（最多 10000 条）

### 6.7 区域控制器 — [RegionController.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/RegionController.java)

**职责**：整合宏观和微观地形系统，提供最终的 `TerrainBlendResult`。

**关键特性**：
- 512×512 格的控制点区域缓存（ConcurrentHashMap）
- 支持 BlendCache 区块级预缓存（性能关键优化）
- C2ME 兼容的无锁缓存淘汰（AtomicBoolean CAS）
- 气候混合计算（按权重混合相邻控制点的气候参数）

**混合算法**：
```java
区域内 (blendWeight > 0.8): finalHeight = microHeight + tierAdjustment
过渡带 (blendWeight ≤ 0.8): finalHeight = lerp(microHeight + tierAdjustment, macroBaseHeight, macroInfluence)
其中 macroInfluence = (1-smoothT) × 0.5, smoothT = 3t²-2t³
```

### 6.8 控制点系统

#### [ControlPointRegion.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/ControlPointRegion.java)

512×512 格区域，包含多个控制点。生成时基于宏观海拔等级白名单，从 TerrainFieldSampler 获取类型和偏移量。

#### [TerrainControlPoint.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainControlPoint.java)

每个控制点包含：
- 位置 (x, z)
- 地形类型 (TerrainType)
- 海拔偏移量 (elevationOffset)
- 影响半径 (influenceRadius)
- 影响力计算：`weight = max(0, (influenceRadius - distance) / influenceRadius)^power`

#### [ControlPointManager.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/ControlPointManager.java)

管理控制点的创建和缓存，使用网格索引 + 单元格加速空间查询。

#### [TerrainVoronoiCache.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainVoronoiCache.java)

纯静态工具类，缓存 Voronoi 计算结果。

### 6.9 河流系统

#### [RiverNoiseSampler.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/RiverNoiseSampler.java)

生成河流路径噪声，提供梯度驱动宽度（山区 10 格，平原 20 格）。

#### [RiverInfo.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/RiverInfo.java)

河流信息数据类，存储河流位置、宽度、深度。

#### [NoiseSet.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/NoiseSet.java)

管理多个噪声采样器的集合，提供统一采样接口。

**噪声配置文件**：`RIVER_PATH`、`RIVER_WIDTH`、`DRAINAGE`、`SEABED` 等。

### 6.10 高度计算 — [HeightCalculator.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/HeightCalculator.java)

与 RegionController 共享 MacroVoronoiSystem 实例，提供生物群落高度计算。

### 6.11 地形上下文 — [TerrainContext.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/terrain/TerrainContext.java)

封装了地形计算所需的多层噪声值（n1, n2, n3, distance），用于 TerrainType 的高度函数计算。

---

## 7. Voronoi 可视化系统 / Voronoi Visualization System

### 7.1 系统架构

位于 `com.worldscape.voronoi` 包，包含 14 个文件，是一个完整的游戏内 Voronoi 图可视化工具。

| 组件 | 职责 |
|------|------|
| WorldScapeVoronoiSystem | 系统核心，管理生命周期 |
| VoronoiDiagram | Voronoi 图数据结构 |
| VoronoiCalculator | 图计算（Fortune 算法） |
| VoronoiCell/Edge/Vertex | 图元素 |
| VoronoiControlPoint | 可视化控制点 |
| VoronoiControlPointManager | 控制点管理 |
| VoronoiSpatialIndex | 空间索引加速 |
| VoronoiCamera | 相机控制 |
| VoronoiOverlayRenderer | 覆盖渲染 |
| VoronoiInputHandler/VoronoiInputEvents | 输入处理 |
| VoronoiViewMode | 视图模式（宏观/微观） |
| VoronoiDataPersistence | 数据持久化 |
| IncrementalVoronoiUpdater | 增量更新 |

### 7.2 功能特性

- **两种视图模式**：宏观（0.25x 缩放）和微观（2x 缩放）
- **控制点操作**：创建、删除、选择、移动
- **数据持久化**：保存/加载控制点到磁盘
- **键盘快捷键**：WASD 移动、滚轮缩放、Tab 切换模式
- **信息面板**：显示缩放、点数、LOD、渲染时间等

---

## 8. 生物群落系统 / Biome System

### 8.1 生物群落映射 — [BiomeMapper.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/biome/BiomeMapper.java)

将 29 种地形类型映射到 Minecraft 生物群落。

### 8.2 地形-群系规则 — [TerrainBiomeRules.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/biome/TerrainBiomeRules.java)

**核心职责**：
- 为每种地形类型定义允许/排除的生物群落列表
- 启动时预计算标签展开（如 `#minecraft:is_ocean` → 具体群系列表）
- 提供种子确定性群系选择

**关键设计**：
- 使用 `synchronized` + 双重检查锁定保证线程安全初始化
- 预计算缓存使用 `Collections.unmodifiableList` 不可变包装
- 群系选择按 `seed ^ cellX*31 + cellZ*17` 确定性选择

**覆盖机制**：
在 `fillFromNoise` 阶段，通过反射修改 `LevelChunkSection.biomes` 字段，将 4×4×4 生物群系细胞替换为地形匹配的群系。若反射失败（Java 模块系统阻止），静默降级为原版群系。

---

## 9. 表面构建系统 / Surface Building System

### 9.1 适配器接口 — [SurfaceAdapter.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/generator/SurfaceAdapter.java)

```java
public interface SurfaceAdapter {
    boolean buildSurface(SurfaceBuildContext context);
    boolean isAvailable();
    String getName();
}
```

### 9.2 反射式适配器 — [ReflectionSurfaceAdapter.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/generator/ReflectionSurfaceAdapter.java)

**设计目标**：通过反射调用 Minecraft 内部 `SurfaceSystem.buildSurface()`，保持与原版表面规则兼容。

**工作原理**：
1. 预缓存反射对象（`ReflectionCache`）：包括 `NoiseChunk.forChunk()`、`SurfaceSystem.buildSurface()` 等 6 个关键反射点
2. 构建 `SurfaceBuildContext`：包含高度图、河流图、河流深度图
3. 创建 `NoiseChunk` 实例并注入初步表面高度
4. 创建 `SurfaceSystem` 实例并调用 `buildSurface()`
5. 回退机制：若反射失败，自动降级到 `FallbackSurfaceAdapter`

### 9.3 回退式适配器 — [FallbackSurfaceAdapter.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/generator/FallbackSurfaceAdapter.java)

**设计目标**：不依赖 Minecraft 内部 API 的完整表面构建方案。

**工作原理**：
1. 遍历 16×16 列
2. 每列查询生物群系获取 ID
3. 根据生物群系 ID 和高度决定表面方块（草、沙、雪、砂砾等）
4. 地下 0-4 格使用对应表面下方的填充方块
5. 4-16 格随机石材变体（花岗岩/闪长岩/安山岩）
6. 16 格以下全部石材变体

### 9.4 适配器工厂 — [SurfaceAdapterFactory.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/generator/SurfaceAdapterFactory.java)

三种适配器类型：
- `REFLECTION`：强制使用反射适配器
- `FALLBACK`：强制使用回退适配器
- `AUTO`：自动检测（反射可用时用反射，否则回退）

---

## 10. 调试系统 / Debug System

### 10.1 调试系统核心 — [TerrainDebugSystem.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainDebugSystem.java)

**职责**：管理调试模式开关、日志采样率、可视化开关。

**关键配置**：
- `chunkSampleRate`：日志采样率（默认每 50 块记录一次）
- `debugLoggingEnabled`：调试日志开关
- `debugPillarsEnabled`：方块列可视化开关
- `enhancedHeightmapEnabled`：增强高度图导出开关

### 10.2 调试工具集 — [TerrainDebugTools.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainDebugTools.java)

**导出功能**：
- `exportHeightMapImage()`：灰度高度图 PNG
- `exportMacroVoronoiImage()`：宏观 Voronoi 可视化
- `exportEnhancedTerrainMap()`：地形类型着色 + 海拔梯度 + Voronoi 边界
- `exportContourTerrainMap()`：等高线地形图
- `exportTerrainStatsChart()`：地形统计图表

**查询功能**：
- `queryTerrainAt()`：指定坐标地形信息（宏观/微观/描述）
- `generateTerrainSummary()`：区域地形摘要统计
- `verifyHeightConsistency()`：单/多线程 MD5 校验

### 10.3 调试柱子 — [DebugPillarManager.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/debug/DebugPillarManager.java)

在游戏中生成彩色方块列，可视化地形类型分布：
- 高山=红色玻璃、山地=橙色、丘陵=黄色、高原=紫色
- 平原=绿色、峡谷=蓝色、海岸=青色、沙丘=白色、冰川=浅蓝

### 10.4 帧日志 — [TerrainFrameLogger.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/debug/TerrainFrameLogger.java)

记录和分析地形生成性能数据。

---

## 11. 兼容性系统 / Compatibility System

### 11.1 模组兼容性检查 — [ModCompatibilityChecker.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/compat/ModCompatibilityChecker.java)

在模组加载时扫描已安装的模组，按严重程度分类：

| 级别 | 说明 | 示例模组 |
|------|------|---------|
| INCOMPATIBLE | 不兼容，显示警告 | terraincontrol, worldpainter, amplify |
| CONFLICT | 部分冲突，需配置 | Biomes O' Plenty, Regions Unexplored |
| COMPATIBLE | 兼容 | JEI, JourneyMap, Create |

### 11.2 不兼容警告界面 — [IncompatibleModWarningScreen.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/compat/IncompatibleModWarningScreen.java)

检测到不兼容模组时显示的红色警告界面。

### 11.3 C2ME 兼容 — [C2MECompatibility.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/compat/c2me/C2MECompatibility.java)

**设计目标**：适配 C2ME 并行化区块生成场景。

**关键策略**：
- 将 `RegionController` 缓存上限从 1024 提升到 4096
- 使用 `ConcurrentHashMap.computeIfAbsent`（桶级锁）替代全局 `synchronized`
- 缓存淘汰使用 `AtomicBoolean` CAS 实现非阻塞单线程淘汰
- 淘汰逻辑使用弱一致性迭代器，不阻塞读操作

### 11.4 TerraBlender 兼容 — [TerraBlenderCompat.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/compat/TerraBlenderCompat.java)

处理 TerraBlender 的生物群系注入兼容，确保 TerraBlender 添加的群系能够被地形覆盖系统正确处理。

---

## 12. 配置与欢迎界面 / Configuration & Welcome Screen

### 12.1 配置管理 — [ConfigManager.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/config/ConfigManager.java)

管理用户配置，包括生成器设置等。

### 12.2 欢迎界面 — [WelcomeScreen.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/config/WelcomeScreen.java)

版本变化时自动显示的欢迎界面。

**可配置选项**：
- 地形预设：Vanilla-like / Large Scale / Dramatic
- 河流强度：Calm / Standard / Deep
- 山脉高度：Low / Standard / Extreme
- 岛屿模式：ON / OFF
- 调试模式：ON / OFF

### 12.3 欢迎界面配置 — [WelcomeScreenConfig.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/config/WelcomeScreenConfig.java)

欢迎界面配置数据类，使用双重检查锁定保证线程安全的延迟初始化。

### 12.4 欢迎界面资源 — [WelcomeScreenAssets.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/config/WelcomeScreenAssets.java)

管理欢迎界面使用的纹理和样式资源。

---

## 13. 命令行系统 / Command System

详见 [5.4 命令系统](#54-命令系统--commandmanagerjava)。命令系统通过 NeoForge 的 `RegisterCommandEvent` 注册所有 `/worldscape` 子命令。

---

## 14. 种子分析器 / Seed Analyzer

### 14.1 分析器 — [SeedAnalyzer.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/analyzer/SeedAnalyzer.java)

**职责**：离线分析世界种子，输出详细的种子报告（HTML）。

**分析内容**：
- 地形类型分布
- 海拔高度统计（含"透水"连续高度）
- 海洋质量评估
- 悬崖/梯度分析
- 生物群落分布
- 地形多样性指标

**分析结果验证**：已验证与游戏内生成完全一致（初始化、计算链、常数值均等价）。

**注意事项**：
- `collectTerrainTypes()` 使用 `blend.dominantType` 而非 `determineTerrainType(blend)`，导致 dominantWeight < 0.4 时类型统计有约 5-10% 偏差

### 14.2 验证器 — [SeedAnalyzerVerification.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/analyzer/SeedAnalyzerVerification.java)

验证分析器与游戏内生成的一致性。

### 14.3 测试 — [ClimateInterpolatorTest.java](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/java/com/worldscape/analyzer/ClimateInterpolatorTest.java)

气候插值算法的独立测试。

---

## 15. MCP 服务器集成 / MCP Server Integration

### 15.1 配置文件 — [.trae/mcp.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/.trae/mcp.json)

配置了两个 MCP 服务器：

#### Minecraft 服务器 MCP
- **来源**: GitHub tamo2918/Minecraft-Server-MCP
- **功能**: 40 个 Minecraft 服务器管理工具
- **连接**: RCON (localhost:25575, 密码: ws_rcon_dev_2026)
- **用途**: 远程操控测试服务器

#### Modrinth API MCP
- **功能**: 搜索、查询 Modrinth 模组
- **用途**: 模组版本检查、兼容性查询

### 15.2 工作流程

```
gradle server → NeoForge 开发服务器启动
    → MCP 通过 RCON 连接 localhost:25575
    → 使用 execute_command 等工具控制服务器
    → 进行地形生成测试
```

---

## 16. 资源与数据文件 / Resources & Data Files

### 16.1 语言文件

#### [en_us.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/assets/worldscape/lang/en_us.json)
英文语言文件，包含 30 个键值对，覆盖：
- 键盘快捷键说明（13 个）
- Voronoi 工具提示（10 个）
- 命令反馈信息（7 个）

#### [zh_cn.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/assets/worldscape/lang/zh_cn.json)
中文语言文件，内容与英文版对应。

### 16.2 维度数据

#### 主世界维度文件
- [worldscape_overworld.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/worldscape/dimension/worldscape_overworld.json)：自定义世界类型，使用 `worldscape:landscape` 生成器
- [worldscape_overworld_type.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/worldscape/dimension_type/worldscape_overworld_type.json)：自定义维度类型（高度 928 格，海平面 63）

#### 原版维度覆写
- [overworld.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/minecraft/dimension/overworld.json)：将原版主世界生成器替换为 `worldscape:landscape`
- [overworld.json (dimension_type)](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/minecraft/dimension_type/overworld.json)：扩展主世界高度到 928 格
- [overworld_caves.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/minecraft/dimension_type/overworld_caves.json)：覆写洞穴维度类型
- [the_nether.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/minecraft/dimension_type/the_nether.json)：下界维度类型
- [the_end.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/data/minecraft/dimension_type/the_end.json)：末地维度类型

### 16.3 Mixin 配置 — [worldscape.mixins.json](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/worldscape.mixins.json)

```json
{
  "required": false,
  "minVersion": "0.8",
  "package": "com.worldscape.mixin",
  "compatibilityLevel": "JAVA_21",
  "mixins": ["ServerChunkCacheMixin"]
}
```

### 16.4 模组元数据 — [neoforge.mods.toml](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/src/main/resources/META-INF/neoforge.mods.toml)

```toml
modLoader="javafml"
loaderVersion="[1,)"
license="MIT"
authors="Filowxy"

[[mods]]
modId="worldscape"
version="1.3.1-beta"
displayName="World Scape"
description="Completely overhauls Minecraft's terrain generation with realistic landforms."
```

---

## 17. 已知问题 / Known Issues

### 17.1 待排查问题

| 问题 | 优先级 | 状态 | 说明 |
|------|--------|------|------|
| Voronoi 边界过渡 | P1 | ✅ 已修复 | 增强宏观影响权重和模糊范围 |
| 极端地形相邻 | P1 | ✅ 已修复 | 添加地形类型邻接约束 |
| 高海拔无生物群系 | P1 | ✅ 已修复 | 添加 HIGH_MOUNTAINS 群系映射 |
| 硬编码高度变化限制 | P1 | ✅ 已修复 | 移除 25 格硬编码限制 |
| 控制点密度不均 | P2 | ⚠️ 待验证 | 需检查控制点分布均匀性 |
| 噪声层与地形不协调 | P2 | ⚠️ 待验证 | 噪声可能破坏宏观地形结构 |
| 缓存竞态条件 | P2 | ⚠️ 待验证 | 多线程访问可能产生不一致 |
| 岛屿模式生成质量 | P2 | ⚠️ 待验证 | 需测试岛屿边缘过渡 |
| 河流侵蚀效果 | P3 | ⚠️ 待验证 | 河流深度与周围地形协调性 |

### 17.2 已知 Bug

| Bug | 严重度 | 状态 |
|-----|--------|------|
| `collectTerrainTypes()` 类型统计偏差（~5-10%） | P2 | 📝 已知 |
| Codec settings=null 降级保护不足 | P2 | 📝 已知 |

### 17.3 兼容性风险

| 模组 | 风险等级 | 说明 |
|------|---------|------|
| C2ME | 🔴 高 | 线程安全设计未充分验证 |
| Distant Horizons | 🔴 高 | 自定义 ChunkGenerator 已知不兼容 |
| Biomes O' Plenty | 🟡 中 | 群系覆盖冲突，可配置 |
| TerraBlender | 🟡 中 | Mixin 影响 surfaceRule 反射调用 |
| YUNG's Better Caves | 🟡 中 | AquiferMixin 可能干扰 |

---

## 18. 开发环境 / Development Environment

### 18.1 环境配置

| 项目 | 值 |
|------|-----|
| 操作系统 | Windows |
| Java 版本 | 21 |
| Gradle 版本 | 8.10 |
| NeoForge 版本 | 21.1.219 |
| Minecraft 版本 | 1.21.1 |
| 开发 IDE | Trae (AI-powered IDE) |

### 18.2 常用命令

| 命令 | 说明 |
|------|------|
| `gradlew client` | 启动 Minecraft 客户端 |
| `gradlew server` | 启动 Minecraft 服务器 |
| `gradlew runSeedAnalyzer` | 运行种子分析器 |
| `gradlew build` | 构建模组 JAR |

### 18.3 项目规则文件

| 文件 | 说明 |
|------|------|
| [CLAUDE.md](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/.trae/rules/CLAUDE.md) | 核心工作规则（版本控制、记忆管理） |
| [memory.md](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/.trae/rules/memory.md) | AI 长期记忆（P0/P1/P2 分级） |
| [所有代码必须确保兼容性.md](file:///c:/Users/ASUS/Documents/trae_projects/Worl%20Scape/.trae/rules/所有代码必须确保兼容性.md) | 兼容性规范 |

### 19.4 灾难恢复

项目经历过两次文件丢失灾难（`git checkout -f` 和 PowerShell 脚本错误），通过反编译 `build/classes/` 下的 class 文件恢复。因此项目规则中强调了：

1. **绝对禁止** `git checkout -f` / `git reset --hard` / `git clean -fd`
2. **每次逻辑变更后立即提交**
3. **无 commit 的分支切换前必须手动备份**
4. **破坏性操作前先 `git status` 确认状态**

---

## 附录 / Appendix

### A. 文件统计

| 类别 | 数量 |
|------|------|
| Java 源文件 | ~65 个 |
| JSON 数据文件 | 10 个 |
| 配置文件 | 7 个 |
| MCP 服务器 | 2 个 |

### B. 模块间依赖关系

```
WorldScape (入口)
├── LandscapeChunkGenerator (核心)
│   ├── RegionController
│   │   ├── MacroVoronoiSystem
│   │   ├── ControlPointRegion / TerrainControlPoint
│   │   ├── NoiseSet
│   │   └── ClimateUtils
│   ├── HeightCalculator
│   │   └── MacroVoronoiSystem (共享实例)
│   ├── TerrainCalculator (静态)
│   │   ├── TerrainFieldSampler
│   │   └── WorldScapeConstants
│   ├── SurfaceAdapterFactory
│   │   ├── ReflectionSurfaceAdapter
│   │   └── FallbackSurfaceAdapter
│   ├── NoiseSet
│   └── TerrainBiomeRules
│       └── BiomeMapper
├── CommandManager
├── WorldScapeVoronoiSystem (客户端)
│   └── Voronoi* (14个文件)
└── ModCompatibilityChecker
    ├── C2MECompatibility
    └── TerraBlenderCompat
```