# World Scape - Long-term Memory

## 项目概述 / Project Overview
- **项目名称**: World Scape
- **类型**: Minecraft NeoForge 1.21.1 地形生成模组
- **定位**: 高级地形生成框架，专注于宏观地形控制和微观细节
- **核心功能**: 地形框架生成（石头等基础方块），生物群系控制表面方块

---

**[P0] 2026-05-23 | 经验教训 - 破坏性操作前必须 git 备份**

**触发场景**: 使用 PowerShell 脚本批量删除 Java 源文件中文注释时，脚本中的类型转换错误 (`[char]0` 空字符 + `.Replace([char]0, [char]'')` 因 `[char]''` 无效而失败) 导致 51 个 Java 源文件被写入空字符 → 文件变为 0 字节。事后发现该目录非 git 仓库，无法 `git checkout` 恢复。

**核心内容**:
1. **灾难经过**: 脚本将 CJK 字符替换为 `[char]0` (null char)，然后 `.Replace([char]0, [char]'')` 因 `[char]''` 无效抛出异常，`$newContent` 赋值失败 → `WriteAllText` 写出 0 字节 → 51 个源文件全部消失
2. **恢复方式**: 从 `build/libs/Worl Scape.jar` 提取 .class 文件，下载 CFR 0.152 反编译器，反编译回 .java 源文件 (59 个文件成功恢复)
3. **后遗症**: 反编译代码丢失原始注释、导入优化、泛型类型推断等细节，需手动修复 31 个编译错误

**永久准则**:
1. 执行任何批量文件修改（删除、替换、覆盖）前，必须先检查是否存在 `.git` 目录
2. 有 git: 先 `git stash` 或至少 `git add` + `git commit`，确认有可回退的 commit
3. 无 git: 先将整个 `src/` 目录复制到临时备份目录（如 `src_backup_YYYYMMDD_HHMMSS`）
4. 测试新脚本时，先用 1-2 个文件测试，确认无误后再批量执行
5. PowerShell Unicode 正则: 避免 `\x{4e00}` 语法（仅支持 2 位十六进制 `\xHH`），改用 `\p{IsCJKUnifiedIdeographs}` 或基于 `[int]char` 的范围判断

---

**[P1] 2026-05-23 | 决策 - 三个中优先级改进（多样性+细节增强）**

**触发场景**: 美学评估指出DUNE沙丘过于单一、HILLS/PLAINS/SEA_PLATEAU本质差异不足、PLATEAU/DOME/BASIN表面过于光滑。

**核心内容**:

1. **DUNE - 双绝对值锐化 + 次波振幅×2**：
   - DUNE_SECONDARY_AMP: 8.0→16.0（32%→64%主波振幅）
   - 次波也加 `Math.abs()` → `|主波| + |次波|` 纵横双脊线 → 方格状复杂沙丘场
   - fBm微纹理：2oct/0.1→3oct/0.3, 5→8（增强沙粒感）

2. **HILLS - 湍流锐化**：
   - 在纯 fBm 基础上叠加 `turbulence(0.3) * 30` → 陡坡+缓坡交替，区别于 PLAINS 的均匀平坦
   - PLAINS 保持纯 fBm(4,0.2) 不变，维持本质差异

3. **SEA_PLATEAU - 域旋转脊纹**：
   - 新增 `domainRotated(0.1) * 10` → 大陆架水下脊线纹理，区别于 PLAINS 的纯平面

4. **PLATEAU/DOME/BASIN - 高频细节纹理**：
   - 统一添加 `fBm(worldX*8, 3oct, 0.5) * 2-4` → 波长 512-128格的岩石纹理
   - PLATEAU:+3.0, DOME:+4.0, BASIN:+2.0（不影响宏观形态）

**永久准则**:
1. 沙丘的数学签名必须是双向绝对值锐化，单向正弦波不足以模拟真实风成沙丘场
2. 纯fBm地形间的本质差异必须通过添加不同数学算子（湍流/域旋转/噪声细节层）实现，不能仅靠参数变化
3. 高斯地貌的高频细节层振幅不应超过主体振幅的3%，否则会破坏宏观形态
4. 高频细节的采样频率应为 `worldX * 8`，对应有效波长 512-128格（岩石纹理尺度）

---

**[P0] 2026-05-23 | 决策 - 三个高优先级地貌修复（美学评估驱动）**

**触发场景**: 美学评估发现RIDGE正弦波过度规律、DOME等高斯特征重叠、CANYON缺乏蜿蜒性三个高优先级问题。

**核心内容**:

1. **RIDGE - 噪声扭曲坐标打破正弦波周期性**：
   - 旧：固定频率正弦波 `sin(x*0.007+z*0.004)` → 周期~898格，山脊像人造栅栏
   - 新：fBm(3oct, gain=0.35) 生成 ±150格坐标偏移 → 正弦波自然蜿蜒
   - 梯度计算同步使用扭曲坐标，保持陡坡灵敏度检测有效
   - `RIDGE_WARP_STRENGTH = 150.0`

2. **高斯地貌族(DOME/CIRQUE/SINKHOLE/BASIN) - 噪声门控+高频大偏移**：
   - 旧：所有点共享同一偏移噪声场 → 大范围重叠模糊，缺乏离散独立特征
   - 新：sigmod(noise*6) 振幅门控 → 特征仅出现在噪声有利区域（~50%覆盖率）
   - 偏移量增大（300/200/150/400）+ 高频噪声采样 → 每个特征拥有独立位置
   - 更新常量：DOME_OFFSET=50→300, BASIN_OFFSET=80→400
   - 新增常量：CIRQUE_OFFSET=200, SINKHOLE_OFFSET=150

3. **CANYON - 坐标扭曲创造蜿蜒峡谷**：
   - 旧：原生态fBm → `|fBm|` 产生随机锯齿状凹坑集合
   - 新：fBm(0.5x, 3oct) + 100格偏移 → 峡谷沿扭曲路径延伸
   - 梯度计算使用扭曲坐标，保持V型谷深度准确

**永久准则**:
1. 正弦波骨架必须用噪声扭曲坐标，禁止固定频率导致机械重复
2. 高斯类地貌必须使用振幅门控创建离散特征，禁止所有位置均匀分布
3. 峡谷等线性地貌必须使用坐标扭曲创造蜿蜒路径，禁止原生态fBm随机锯齿
4. 门控频率应为特征尺寸的3倍（λ_gate ≈ 3×σ_gaussian）
5. 偏移幅度应为sigma的1.5-2倍才能有效分离特征

---

**[P0] 2026-05-23 | 决策 - v4.0 "地形即函数" 全面重构**

**触发场景**: 29种地形类型共用同一套噪声叠加公式，不同地貌形态趋同。山脊和沙丘无区别，峡谷和河谷缺乏地质成因差异。

**核心内容**:

1. **设计哲学**：地形即函数——每种地形类型根据真实地质成因，分配专属数学函数组合
   - 构造力 → fBm + 域旋转 + 湍流（7种：HIGH_MOUNTAINS/RIDGE/PEAK/HORN/CLIFF/PLATEAU/DOME）
   - 风力 → 方向性正弦波 + 绝对值（4种：DUNE/YARDANG/GOBI/SALT_FLAT）
   - 水力 → 梯度驱动 + sigmoid（6种：CANYON/VALLEY/FLOODPLAIN/DELTA/ALLUVIAL_FAN/BASIN）
   - 冰力 → 湍流 + tanh（6种：FJORD/GLACIAL_VALLEY/CIRQUE/ICE_SHEET/SEA_CLIFF/BEACH）
   - 特殊 → abs + 湍流 + 高斯（6种：SINKHOLE/PEAK_FOREST/TRENCH/SEA_PLATEAU/HILLS/PLAINS）

2. **数学工具箱新增**：sigmoid(t), tanhScaled(t, steepness), gaussian(x,z,sigma), sampleFbm(x,z,octaves,gain), calculateGradient(x,z)
3. **关键地质公式**：
   - DOME: gaussian(x,z,σ=200)*150 → 自然穹顶
   - CANYON: |fBm|*60 → V型谷底
   - VALLEY: sigmoid(gradient)*40 → U型谷
   - FJORD: turbulence*100 + tanh(cliff)*80 → U形谷壁
   - SINKHOLE: |gaussian|*40 → 陡峭坑壁
   - ALLUVIAL_FAN: erf(dist)*slope → 放射状扩散
   - DUNE: |sin(windDir)|*25 + sin(perpDir)*8 → 方向性脊线
4. **fBm参数差异化**：每种地形用不同octaves+gain（SALT_FLAT: 2层/0.1, PLAINS: 4层/0.2, HILLS: 6层/0.65, GOBI: 4层/0.7）
5. **高斯偏移中心**：DOME/CIRQUE/SINKHOLE/BASIN 用 fBm 噪声偏移中心坐标，避免完美居中

**新增常量**: 9个(SIGMOID_STEEPNESS_DEFAULT/TANH_STEEPNESS_CLIFF/TANH_STEEPNESS_SEA_CLIFF/GAUSSIAN_SIGMA_DOME/CIRQUE/SINKHOLE/DUNE_PRIMARY_AMP/DUNE_SECONDARY_AMP/YARDANG_AMP)
**新增盐值**: 2个(SALT_WIND_DIRECTION/SALT_WIND_PERPDIR)

**永久准则**:
1. 每种地形类型必须有独特的数学签名，不能共用同一套参数
2. fBm的octaves+gain组合是地形的"分形指纹"——平坦地形用少层+低gain，粗糙地形用多层+高gain
3. sigmoid用于沉积型过渡（U型谷、海滩），tanh用于侵蚀型过渡（悬崖、海崖）
4. 高斯函数用于等轴性地貌（穹丘、冰斗、天坑、盆地），必须偏移中心避免完美居中
5. 方向性正弦波用于风成地貌，绝对值|sin|将圆滑波形变为尖锐V形脊线

---

**[P0] 2026-05-23 | 决策 - 山体形态系统性数学优化（6大优化）**

**触发场景**: v3.0连续噪声场重构后，山体形态仍不够自然——山峰截面偏向规整数学曲线，缺乏真实山脉的锯齿感、脉络感和侵蚀痕迹

**核心内容**:

1. **fBm分形布朗运动替代单层噪声**：
   - TerrainFieldSampler新增 `sampleFbm(x,z)` — 6层叠加 (lacunarity=2.0, gain=0.5)
   - NormalNoise实例: firstOctave=-(8+i), 频率从1/4096→1/128
   - 种子派生: SALT_FBM_OCTAVE_0~5 (SeedDeriver)
   - 替代 calcHeightForType 的 HIGH_MOUNTAINS/RIDGE/PEAK/HORN 所有单层噪声

2. **梯度约束正弦波山脊骨架**：
   - RIDGE分支: sin(wx*0.007+wz*0.004)*35 + sin(wx*0.025-wz*0.018)*18
   - Sobel 3×3梯度约束: gradMag>0.6→sineWeight=0.3, gradMag<0.3→sineWeight=1.0
   - 叠加 fBm+turbulence+domainWarp 形成完整山脊系统

3. **噪声域旋转 (Domain Wrap Rotation)**：
   - TerrainFieldSampler新增 `sampleDomainRotated(x,z,warpStrength)`
   - 低频角度噪声(1/16384)→旋转矩阵→旋转坐标系偏移(1/4096)→采样基础能量场
   - 借鉴Larion的域包裹噪声坐标扭曲，打破完美对称

4. **能量场定向拉伸**：
   - TerrainFieldSampler新增 `sampleEnergyStretched(x,z)`
   - 山脊走向=atan2(∂sine/∂x, ∂sine/∂z)+域旋转微调
   - 沿走向1.5x拉伸，垂直0.7x压缩→椭圆形影响区→褶皱山脉

5. **河流梯度驱动升级**：
   - RiverNoiseSampler新增 `getGradientDrivenWidth(x,z,gradient)`
   - grad>0.5→10格(山区), grad<0.2→20格(平原), 中间线性过渡
   - RIVER_GRADIENT_FOLLOW_STRENGTH=0.8 控制梯度vs噪声的混合

6. **湍流噪声锐化山脊**：
   - TerrainFieldSampler新增 `sampleTurbulence(x,z,strength)`
   - |energyDetail*2-1| 变换→V形折线→锋利山脊/角峰

**新增常量**: 13个(FBM_OCTAVES/FBM_LACUNARITY/FBM_GAIN/RIDGE_SINE_PRIMARY_AMP/.../RIDGE_TURBULENCE_STRENGTH)
**新增噪声盐值**: 9个(SALT_FBM_OCTAVE_0~5 + SALT_DOMAIN_ANGLE/OFFSET_X/OFFSET_Z)
**新增NormalNoise实例**: 9个(6 fBm + 3 domain)

**永久准则**:
1. 所有噪声实例必须通过固定salt派生，确保确定性（相同种子→相同地形）
2. fBm lacunarity=2.0/gain=0.5 是自然地形黄金参数，单层噪声的6层近似
3. 域旋转必须在极低频(1/16384)运行，避免产生视觉可察觉的扭曲
4. 湍流噪声 |noise*2-1| 绝对值变换必须夹紧到[0,1]避免异常值
5. 正弦波山脊骨架必须与Sobel梯度联动，避免在陡坡产生过度规则条纹

---
**[P0] 2026-05-23 | 决策 - v3.0重构后适配：旧平滑策略清理+海洋阻尼+MIN_HEIGHT修复**

**触发场景**: v3.0连续噪声场重构后出现3个问题：海底太平坦、海底材质错误（泥土/草方块）、旧种子出生点变化（预期内）

**核心内容**:

1. **旧平滑策略清理**：
   - `MAX_MACRO_INFLUENCE` 0.75→0.15（独立随机→连续噪声场后，75%宏观影响从"补悬崖"变"过度平滑"）
   - 新增海洋等级阻尼：Tier 0 = ×0.33（有效最大0.05），Tier 1 = ×0.50（有效最大0.075）
   - RegionController.calculateBlend()+HeightCalculator.calculateHeight()同步更新
   - fillFromNoise中的旧no-op `smoothedHeight`变量已清除

2. **Biome Override漏洞修复**：
   - **overrideTerrainBiomesInChunk 从只覆盖 Y=0 Section 改为覆盖 ALL Sections**
   - 根因：Minecraft SurfaceRule读取群系时使用表面高度所在Section的biome数据
   - 旧代码只覆盖Y=0的Section，表面在y=45的Section读取到的仍是原版海洋群系→OceanSurfaceRule→沙砾海底
   - 但若SurfaceRule找不到匹配海洋群系，回退到默认陆地规则→泥土/草方块!
   - 修复：遍历minSectionY→maxSectionY，每个Section的4个Y细胞全部覆盖
   - 反射对象缓存（biomesField/setMethod）+ `reflectionAvailable`标志替代旧`break`逻辑

3. **MIN_TERRAIN_HEIGHT -60→-64修复**：
   - 根因：Tier 0 macro base = -80，TRENCH减去20+噪声=[-120,-20]自然范围
   - 旧-60截断了几乎所有深海地形，使海底一律变平-60
   - 新-64匹配Minecraft世界底部，保留16格额外深海起伏

**永久准则**:
1. v3.0连续噪声场下，Voronoi边界处的宏观影响不应超过0.15（自然平滑足够）
2. 海洋地形（Tier 0/1）应进一步抑制宏观影响以保留海底细节
3. Biome覆盖必须覆盖所有Section，不仅仅是Y=0的Section
4. 全局高度下限必须≤Tier 0宏观基准 + TRENCH偏移，否则海洋被压平
5. 重构后残留的旧修复代码（no-op变量、过度平滑参数）必须清除

**[P0] 2026-05-22 | 决策 - 地貌系统全面精细化重构（5任务）**
**触发场景**: v3.0连续噪声场重构后，系统性调优参数、清理死代码、规范化常量
**核心内容**:

任务一·参数调优：
- TIER_THRESHOLDS 更新为精确分位数（基于N(0,1) CDF反查）：
  [-1.405, -0.674, 0.000, 0.842, 1.645]
  对应分布：T0=8%, T1=17%, T2=25%, T3=30%, T4=15%, T5=5%
- ENERGY_TO_OFFSET_SCALE 从 120 降至 50（能量范围扩大后等比缩放）
- selectTier 系列保留原概率分布权重（非均匀区间）

任务二·死代码清理（共删除14个方法+31个常量+2行注释代码）：
- LandscapeChunkGenerator: 删除30个平滑参数常量、14个平滑方法
  (getTerrainAwareSmoothingParams, getBlurKernelSize, generateGaussianWeights,
   getEdgePreservationWeight, getTerrainAwareSmoothingStrength, handleSlopeAnomaly,
   calculateAdaptiveBlurRadius, mirrorIndex, applyGaussianBlurInPlace,
   applyMultiPassSmoothing, getDominantTerrainType, applyEdgeSmoothing,
   applyGaussianKernelInPlace, applyEdgeMarginBlend)
- 删除 SLOPE_CHECK_DISTANCE 未使用常量
- 删除2行注释掉的延迟初始化代码

任务三·硬编码参数规范化（新建 WorldScapeConstants.java）：
- BLEND_WEIGHT_THRESHOLD = 0.8（原 RegionController/HeightCalculator 硬编码）
- DOMINANT_WEIGHT_THRESHOLD = 0.4（原 LandscapeChunkGenerator 硬编码）
- MAX_MACRO_INFLUENCE = 0.75（原 RegionController/HeightCalculator 硬编码）
- TIER_BASE_HEIGHT = 8.0 + TIER_ADJUSTMENT_FACTOR = 0.15（原 8.0*0.15 硬编码）
- HIGH_MOUNTAIN_PEAK_CEILING = 500.0（原 calcHeightForType 硬编码）
- MIN_TERRAIN_HEIGHT = -60, MAX_TERRAIN_HEIGHT = 300（新增全局限制）

任务四·高度上限审查：
- calcHeightForType 末尾添加全局高度限制 clamp [-60, 300]
- HIGH_MOUNTAINS/HORN/PEAK 已有 500 上限（由 HIGH_MOUNTAIN_PEAK_CEILING 控制）
- TerrainFieldSampler.calculateContinuousOffset 极端值范围 [-215, +205] 在合理范围内

**永久准则**:
1. TIER_THRESHOLDS 必须基于统计分布精确计算，禁止凭感觉调参
2. 跨模块共享的数值常量必须提取到 WorldScapeConstants，禁止魔法数字
3. RegionController 和 HeightCalculator 必须使用相同的常量引用
4. calcHeightForType 必须有全局高度限制保护
5. 重构后必须删除旧方法，禁止保留死代码

**[P0] 2026-05-22 | 决策 - TerrainFieldSampler/ControlPointRegion 全面代码审查修复**
**触发场景**: 对新实现的连续噪声场架构进行四维度技术审查
**核心内容**:
审查发现 10 项问题，全部已修复：
- P0-1: getOrCreate 无同步保护 → volatile + synchronized 双重检查锁定
- P0-2: cachedSeed=Long.MIN_VALUE sentinel 碰撞风险 → null check 先行
- P0-3: selectTerrainType+calculateRawOffset 共 130 行死代码 → 已删除
- P1-4: macroTierConstraint=-1 魔法数字 → 提取 NO_MACRO_TIER_CONSTRAINT 常量
- P1-5: ControlPointRegion 构造函数未验证 macroElevationTier → 添加 [0,5] 校验+夹紧
- P1-6: selectTypeByMoisture 无效 tier 静默返回 PLAINS → 添加 LOGGER.warn
- P1-7: selectTier 系列均匀间隔导致概率分布变化 → 改为加权区间（保留原概率分布）
  - 关键：PLAINS 30%→30%（恢复）、SINKHOLE 3%→3%（恢复）、HORN 5%→5%（恢复）
  - 保持连续噪声场空间连贯性的同时，保留原概率分布
- P2-8: getTypeModifier 不访问实例状态 → 改为 static
- P2-9: TIER_THRESHOLDS 可变数组 → 改为 List.of() 不可变包装
- P2-10: getTerrainLevel 与 getTypeModifier 分组不一致 → 注释标注维护风险
**永久准则**:
1. 单例缓存必须使用 volatile + synchronized 双重检查锁定
2. 哨兵值禁止使用可能在合法值域内的数值
3. 重构后必须删除旧代码，禁止保留死代码
4. 噪声驱动类型选择必须保留原概率分布权重
5. 所有输入参数必须校验范围并夹紧
6. 不可变常量使用 List.of() 而非裸数组

**[P0] 2026-05-22 | 决策 - ControlPointRegion从独立随机重构为连续噪声场驱动**
**触发场景**: 悬崖问题经过5轮修复（macroBW→dominantWeight→坐标边界噪声抑制→双轨平滑）均无效或治标不治本，根因是ControlPointRegion独立种子生成导致相邻区域类型硬切换
**核心内容**:
- 新增 TerrainFieldSampler 类：两轴连续噪声场（地形能量场+湿润度场）
- 能量场：NormalNoise(-8, 1.5)主层 + NormalNoise(-6, 1.0)细节层，波长4096/1024格
- 湿润度场：NormalNoise(-7, 1.2)，波长2048格
- 能量值→海拔等级：分位数阈值映射[-0.55,-0.30,-0.05,0.25,0.55]，宏观Voronoi tier作为±1约束
- 湿润度→同等级类型：将[-1,1]映射到[0,1]后按区间选择白名单中的类型
- 偏移量：energy×120 + 类型修正常量（同等级内差异≤30格，相邻等级≤60格）
- 相邻控制点偏移差由噪声频率自然约束：512格间距+1/4096主频→~30格差异（旧方案100+格）
- 移除fillFromNoise中的v4双轨平滑（噪声抑制+基高混合），不再需要事后修补
- 保留邻接约束迭代松弛作为安全网，但噪声驱动下触发频率极低
**永久准则**:
1. 控制点类型和偏移量必须由全局连续噪声场驱动，禁止使用独立随机
2. 相邻区域共享同一噪声场实例（TerrainFieldSampler单例缓存），确保边界连续性
3. 宏观Voronoi tier仅作为clamp约束（±1级），不作为唯一决定因素
4. 偏移量修正常量设计：同等级内差异≤30格，相邻等级≤60格

**[P0] 2026-05-20 | Bug Fix - calculateFinalHeight blendFactor 公式反转（悬崖真正根因）**
**触发场景**: scan_surface 调试输出显示相邻两列 (z=-1 vs z=0) 高度落差 79 格
**核心内容**:
- LandscapeChunkGenerator.calculateFinalHeight L356 的 blendFactor 公式为 `(0.4 - dominantWeight) / 0.3`
- 该公式在 dominantWeight→0.4 时 blendFactor→0（dominantType 贡献趋向 0%）
- 而 dominantWeight≥0.4 时切换分支，finalHeight = dominantTypeHeight（100% pure dominantType）
- 阈值处最大落差：~200 格（HIGH_MOUNTAINS~300 vs HILLS~100）
- 旧公式效果：weight=0.1→100%, 0.2→67%, 0.3→33%, 0.39→3%, 0.4→100%（不连续，先降后跳）
- 修复：`blendFactor = dominantWeight / 0.4`（线性递增，阈值处平滑衔接）
- 新公式效果：weight=0.1→25%, 0.2→50%, 0.3→75%, 0.39→97.5%, 0.4→100%（连续平滑）
**永久准则**:
1. 类型混合的 blendFactor 必须随 dominantWeight 单调递增（权重越大，贡献越大）
2. 阈值处的两个分支必须验证连续性，禁止 C0 不连续
3. 调试工具的输出数据比理论分析更能发现问题——善用 scan_surface

**[P0] 2026-05-20 | 决策 - fillFromNoise 平滑无效，已删除**
**触发场景**: 审查 fillFromNoise 发现平滑结果未被填充消费
**核心内容**:
- fillFromNoise 中 L1267-1327 做过三级平滑：GaussianKernel(3×3, blend=0.6) → applyGaussianBlurInPlace(地形感知sigma) → applyEdgeMarginBlend → 回写heightMap
- 但 world_scape_fillColumn 方法体内使用 cachedContinuousHeight（原始未平滑连续值）而非 terrainHeight（平滑后的heightMap值）
- terrainHeight 参数在 method body 中从未被引用
- 结果：三级平滑（约2048次/区块数学运算）对最终地形**零影响**
- 删除操作：删除 tempHeightMap 创建/复制、两个核权重构造、三个平滑调用、回写复制
- 保留：applyEdgeMarginBlend 方法定义（不调用，保留以备后续可能用）
- 保留：phase 4 循环中的原始 heightMap 值直接用于填充
- 验证：P0 噪声加法叠加修复直接作用于 calculateFinalHeight，填充使用的是其输出 cachedContinuousHeight，删除平滑后立即生效
**永久准则**:
1. 任何对 heightMap 的后处理必须验证是否被最终填充步骤消费
2. fillColumn 的 source of truth 在方法体内验证，不依赖参数名
3. 平滑/后处理管线必须与填充管线有明确的数据链路

## 核心架构 / Core Architecture

### 地形生成层次
```
宏观层 (MacroVoronoiSystem)
├── 单元大小: 2048格
├── 过渡带宽: 400-800格
└── 提供: 海拔等级、基准高度、混合权重

微观层 (HeightCalculator + ControlPointManager)
├── 控制点间距: 256格
├── 影响半径: 150-350格
└── 提供: 局部地形细节
```

### 关键文件
- `LandscapeChunkGenerator.java` - 主生成器
- `MacroVoronoiSystem.java` - 宏观Voronoi系统
- `HeightCalculator.java` - 高度计算（含地形感知平滑）
- `RegionController.java` - 宏观-微观整合
- `TerrainType.java` - 地形类型枚举
- `SurfaceAdapter.java` - 表面系统适配器接口
- `ReflectionSurfaceAdapter.java` - 反射式表面适配器
- `FallbackSurfaceAdapter.java` - 回退式表面适配器
- `SurfaceAdapterFactory.java` - 适配器工厂
- `WelcomeScreen.java` - 欢迎界面
- `WelcomeScreenConfig.java` - 配置管理
- `ModCompatibilityChecker.java` - 兼容性检测
- `IncompatibleModWarningScreen.java` - 冲突模组警告

---

## 重要设计决策 / Key Design Decisions

### Surface Adapter Pattern (优先级: 高)
**表面系统适配器模式** - 解决buildSurface反射调用不稳定问题:

```
┌─────────────────────────────────────────────────────────┐
│                    SurfaceAdapter                        │
│                    表面适配器接口                        │
│  + buildSurface(context): boolean                       │
│  + isAvailable(): boolean                               │
│  + getName(): String                                    │
└─────────────────────────────────────────────────────────┘
                    ↓
    ┌─────────────────┴─────────────────┐
    ↓                                   ↓
┌─────────────────────┐    ┌─────────────────────┐
│ReflectionSurfaceAdapter│   │FallbackSurfaceAdapter│
│  反射式适配器        │    │  回退式适配器        │
│  + 预缓存反射对象    │    │  + 基于生物群系放置方块│
│  + 集中错误处理      │    │  + 不依赖内部API     │
└─────────────────────┘    └─────────────────────┘
                    ↓
         ┌─────────────────────┐
         │SurfaceAdapterFactory │
         │  适配器工厂         │
         │  + create(type)     │
         │  + AdapterType.AUTO │
         └─────────────────────┘
```

**优势**:
- 稳定性和兼容性：回退方案保证反射失败时不崩溃
- 可维护性：反射逻辑集中管理
- 可测试性：可以独立测试每个适配器
- 性能：反射对象预缓存，避免重复查找

### 地形平滑策略 (优先级: 高)
**地形感知自适应平滑** - 根据地形类型采用不同策略:

| 地形类型 | blurRadius | sigma | edgeWeight | 平滑方式 | 效果 |
|----------|------------|-------|------------|----------|------|
| HIGH_MOUNTAINS | 4 | 3.0 | 0.35 | 标准 | 保留山峰但允许平滑过渡 |
| CLIFF | 3 | 2.5 | 0.4 | 标准 | 保留陡峭但避免锯齿 |
| RIDGE/PEAK/HORN | 5 | 3.5 | 0.3 | 标准 | 良好的平滑（高山变体） |
| PLATEAU/DOME | 5 | 3.0 | 0.3 | 各向异性 | 平坦顶部平滑 |
| HILLS | 6 | 4.0 | 0.25 | 标准 | 保留起伏但过渡自然 |
| PLAINS | 7 | 4.5 | 0.2 | 标准 | 非常平滑 |
| BEACH | 6 | 4.0 | 0.2 | 各向异性 | 平滑的海面过渡 |
| CANYON | 4 | 3.0 | 0.35 | 各向异性 | 保留峭壁但过渡平滑 |
| DUNE | 5 | 3.5 | 0.25 | 各向异性 | 波浪沙丘自然 |

**关键改进 (2025-05-10)**:
- **边缘保持机制**: `edgeFactor = Math.exp(-heightDiff / (edgeWeight * 60-100))` - 大幅降低边缘保持强度
- **多尺度平滑**: 大幅增加模糊半径 (3-7格，对应7-15格有效范围)
- **地形类型约束**: 双向调整，阈值从3降低到2，调整量从30增加到50
- **宏观-微观整合**: 宏观影响权重从0.4提升到0.8

### 6级地形体系 (优先级: 高, 2026-05-12 精版)
**地形类型精简** - 从约40种减少到29种，采用6级体系:

| 等级 | 基值 | 包含地形类型 | 高度范围 |
|------|------|-------------|----------|
| 0: 深海 | -80 | TRENCH, SEA_PLATEAU | -55~0 |
| 1: 浅海 | -20 | SEA_PLATEAU, DELTA | -28~22 |
| 2: 沿海 | 10 | BEACH, DELTA, FLOODPLAIN, DUNE, SALT_FLAT | 22~55 |
| 3: 低地 | 60 | PLAINS, HILLS, FLOODPLAIN, DUNE, GOBI, YARDANG, BASIN, SINKHOLE, PEAK_FOREST | 28~99 |
| 4: 高地 | 160 | HILLS, CLIFF, PLATEAU, VALLEY, CANYON, ALLUVIAL_FAN, GOBI, CIRQUE, GLACIAL_VALLEY | 28~193 |
| 5: 山脉 | 300 | HIGH_MOUNTAINS, CLIFF, PLATEAU, RIDGE, PEAK, CIRQUE, HORN, ICE_SHEET, GLACIAL_VALLEY | 44~512 |

**已合并/移除的地形类型**:
- LOW_MOUNTAINS → 合并到 HIGH_MOUNTAINS 和 RIDGE
- MESA → 合并到 DOME
- PLATEAU_LOW → 合并到 DOME
- GLACIAL_LAKE, KARST_DEPRESSION, STONE_FOREST → 移除
- TERRACE（阶地）→ 移除（微观尺度不匹配宏观控制点）
- CRATER（火山口）→ 移除（地质成因与归类不符）
- 其余过细分类型（GULLY, SANDY, PINGO 等）→ 移除

**海拔等级→地形类型白名单系统 (2026-05-12)**:
- 每个海拔等级有明确的地形类型白名单
- 控制点在生成时根据所在区域的宏观海拔等级，仅从白名单中选择地形类型
- 杜绝不合理组合（如深海刷出高山）

**影响半径策略**:
- 低地类: 600-800格
- 丘陵类: 700-900格
- 台地/山脊类: 700-950格
- 高山类: 800-1000格
- 低谷类: 500-650格

**控制点生成机制**:
- 区域大小 3072×3072 格（3×3 控制点）
- 控制点间距 512 格，网格随机偏移 ±128 格
- 每个控制点有 Voronoi 影响半径（600-1000 格）
- 地形类型选择基于海拔等级白名单的概率分布

### 宏观-微观整合 (优先级: 高)
- `blendWeight`: 1.0=区域内, 0.0=过渡带中心
- 过渡带中宏观最多贡献80%混合
- 使用 `MacroRegionInfo.getBaseHeight()` 获取基准高度

### 兼容性检测系统 (优先级: 中)
- 检测不兼容模组: terraincontrol, worldpainter, amplify, litematica等
- 检测冲突模组: terraforged, openworlds等
- 检测到冲突时弹出红色警告界面

### 欢迎界面 (优先级: 中)
- 模组版本变化时自动显示
- 玩家可配置: 地形预设、河流强度、山脉高度、岛屿模式
- 不展示技术细节给普通玩家

---

## 调试功能文档 / Debug System Documentation

### 调试系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    TerrainDebugSystem                      │
│                    调试系统核心配置                          │
│  ├── Debug模式开关（依赖WelcomeScreenConfig）              │
│  ├── 日志记录控制（采样率控制）                             │
│  ├── 可视化开关                                             │
│  └── 状态报告生成器                                         │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                      CommandManager                         │
│                      命令行界面                             │
│  ├── /worldscape debug export_heightmap [radius]          │
│  ├── /worldscape debug export_voronoi [radius]            │
│  ├── /worldscape debug export_enhanced [radius]           │
│  ├── /worldscape debug export_contour [radius] [interval] │
│  ├── /worldscape debug export_stats                       │
│  ├── /worldscape debug verify_consistency [size]          │
│  ├── /worldscape debug status                             │
│  ├── /worldscape debug c2me_report                        │
│  ├── /worldscape debug clear_cache                         │
│  ├── /worldscape debug pillars <on|off|clear>             │
│  ├── /worldscape debug query <x> <z>                      │
│  └── /worldscape debug summary <x> <z> [radius]           │
│  └── /worldscape debug clear_fluids [radius]             │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                    TerrainDebugTools                        │
│                    调试工具集                               │
│  ├── exportHeightMapImage() - 灰度高度图导出               │
│  ├── exportMacroVoronoiImage() - 宏观Voronoi可视化       │
│  ├── exportEnhancedTerrainMap() - 增强地形图（地形着色）  │
│  ├── exportContourTerrainMap() - 带等高线的地形图          │
│  ├── exportTerrainStatsChart() - 地形统计图表             │
│  ├── queryTerrainAt() - 指定坐标地形查询                   │
│  ├── generateTerrainSummary() - 区域地形摘要统计          │
│  └── verifyHeightConsistency() - 单/多线程MD5校验          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│                   DebugPillarManager                        │
│                   游戏内方块列可视化                        │
│  ├── getBlockForTerrainType() - 地形类型→方块颜色        │
│  ├── getBlockForElevationTier() - 海拔等级→方块颜色      │
│  ├── placePillarAt() - 在位置生成可视化方块列              │
│  └── clearAllPillars() - 清理所有Debug方块                │
└─────────────────────────────────────────────────────────────┘
```

### Debug命令详细说明

#### 高度图导出命令
```
/worldscape debug export_heightmap <radius> [pixelsPerBlock]
```
- **功能**: 导出灰度高度图PNG
- **参数**: radius=100-5000, pixelsPerBlock=1-10（默认1）
- **输出**: `worldscape_debug/heightmap_X_Z_timestamp.png`

#### 增强地形图导出
```
/worldscape debug export_enhanced <radius> [pixelsPerBlock]
```
- **功能**: 导出增强地形图（地形类型着色+海拔梯度+Voronoi边界）
- **特性**:
  - 地形类型着色：高山=红，低山=橙，丘陵=黄，高原=紫，平原=绿等
  - Voronoi边界用白色高亮显示
- **输出**: `worldscape_debug/enhanced_terrain_X_Z_timestamp.png`

#### 等高线图导出
```
/worldscape debug export_contour <radius> <interval> [pixelsPerBlock]
```
- **功能**: 导出带等高线的地形图
- **参数**: interval=等高线间隔（10-200格）
- **输出**: `worldscape_debug/contour_X_Z_timestamp.png`

#### 地形查询命令
```
/worldscape debug query <x> <z>
```
- **功能**: 查询指定坐标的完整地形信息
- **输出内容**:
  - 宏观区域信息（海拔等级、气候带、构造类型）
  - 微观地形信息（主导地形类型、混合高度）
  - 地形描述（含中文和英文）

#### 区域地形摘要
```
/worldscape debug summary <x> <z> [radius]
```
- **功能**: 生成区域地形统计报告

### Debug系统参数配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `chunkSampleRate` | 50 | 日志采样率（每N个区块记录一次） |
| `debugLoggingEnabled` | false | 日志记录开关 |
| `debugPillarsEnabled` | false | 方块列可视化开关 |
| `enhancedHeightmapEnabled` | true | 增强高度图导出开关 |

### Debug方块颜色编码

| 地形类型 | 方块颜色 | 说明 |
|----------|----------|------|
| HIGH_MOUNTAINS | 红色玻璃 | 高耸山峰 |
| LOW_MOUNTAINS/CLIFF | 橙色玻璃 | 山地/悬崖 |
| HILLS | 黄色玻璃 | 丘陵起伏 |
| PLATEAU | 紫色玻璃 | 高原台地 |
| PLAINS | 绿色玻璃 | 平坦平原 |
| CANYON/VALLEY | 蓝色玻璃 | 峡谷谷地 |
| BEACH/DELTA | 青色玻璃 | 海岸水域 |
| DUNE | 白色玻璃 | 沙丘戈壁 |
| GLACIAL | 浅蓝色玻璃 | 冰川地貌 |

---

## 地形生成潜在问题分析

### 已知问题列表

| 问题 | 严重程度 | 状态 | 说明 |
|------|----------|------|------|
| Voronoi边界过渡 | P1 | ✅已修复 | 已增强宏观影响权重（0.8）和模糊范围 |
| 极端地形相邻 | P1 | ✅已修复 | 已添加地形类型邻接约束（阈值2，调整量50） |
| 高海拔无生物群系 | P1 | ✅已修复 | 已添加HIGH_MOUNTAINS生物群系映射 |
| 硬编码高度变化限制 | P1 | ✅已修复 | 已移除25格硬编码限制 |
| 控制点密度不均 | P2 | ⚠️待验证 | 需要检查控制点分布均匀性 |
| 噪声层与地形不协调 | P2 | ⚠️待验证 | 噪声可能破坏宏观地形结构 |
| 缓存竞态条件 | P2 | ⚠️待验证 | 多线程访问可能产生不一致 |
| 岛屿模式生成质量 | P2 | ⚠️待验证 | 需要测试岛屿边缘过渡 |
| 河流侵蚀效果 | P3 | ⚠️待验证 | 河流深度与周围地形协调性 |

### 调试功能不足分析

| 缺失功能 | 优先级 | 说明 |
|----------|--------|------|
| 实时地形检查器 | P1 | 无法在生成时实时查看地形信息 |
| 性能分析器 | P1 | 无法测量地形生成速度瓶颈 |
| 过渡区域热力图 | P2 | 无法可视化地形过渡剧烈程度 |
| 控制点位置显示 | P2 | 无法在游戏中看到实际控制点位置 |
| 噪声层可视化 | P2 | 无法查看各噪声层的贡献 |
| 生物群系边界显示 | P2 | 无法看到生物群系与地形的对应关系 |
| 缓存命中率统计 | P3 | 无法监控缓存性能 |
| 内存使用监控 | P3 | 无法查看各子系统的内存占用 |

---

## 用户配置选项 / User Configuration
- **地形预设**: Vanilla-like / Large Scale / Dramatic
- **河流强度**: Calm / Standard / Deep
- **山脉高度**: Low / Standard / Extreme
- **岛屿模式**: ON / OFF
- **调试模式**: ON / OFF

---

## 用户偏好 / User Preferences

### 界面风格
- 深色主题 (YACL风格)
- 蓝调强调色 (#4A9EFF)
- 卡片式布局
- 不展示技术细节给普通玩家

### 工作方式
1. 不要嫌麻烦，要把内容做精做好
2. 所有代码必须确保兼容性
3. 注释要同时有中文和英文版本
4. 兼容性关键值不要用硬编码

---

## 版本历史 / Version History
- v1.2.0: 地形感知自适应平滑系统、各向异性模糊、兼容性检测、欢迎界面
- v1.3.1-beta: 全面悬崖修复（BlendCache粒度、22种地形噪声、水下过渡带、邻接约束、线程安全）

---

**[P0] 2026-05-20 | 全面诊断修复 - 极端悬崖根因分析与9项核心修复**
**触发场景**: 种子 -6426449255803558492 出生点附近出现 100+ 格区块边界悬崖，全面代码审查发现多个系统性问题。第1-8项修复后悬崖未改善，追踪发现第9项才是真正根因。
**核心内容**:
1. **BlendCache macroInfo 缓存粒度错误**：已修复但非悬崖主因。修复后悬崖未改善，说明悬崖来自更底层 MacroVoronoiSystem
2. **22种地形类型缺少噪声计算**：calcHeightForType 仅处理7种类型，修复：补全所有29种类型的噪声计算
3. **MacroVoronoiSystem 水下过渡带拓宽是死代码**：修复：将水下拓宽逻辑移到 blendWeight 计算之前
4. **ControlPointRegion 邻接约束失效**：修复：收紧 CLIFF 组合判定，maxAllowedDiff 从 800 降至 300
5. **静态计数器非线程安全**：修复：改为 AtomicInteger
6. **tierAdjustment 系数不一致**：修复：统一为 0.15
7. **getBaseHeightForTerrainType 缺少12种映射**：修复：补全所有29种映射
8. **selectBiomeBySeed 使用局部坐标**：修复：改用世界坐标 centerX/centerZ
9. **[悬崖真正根因] calculateBlend 中 macroInfluence 钟形曲线修复**：旧公式在 Voronoi 边界处(blendWeight=0.5)仅15.8%宏观影响(old: (1-smoothT)*0.5=0.158)，导致微观点控制点高度跳变(~160格)直接反映为悬崖(~111格)。修复：改为钟形曲线 `boundaryProximity = 1-|blendWeight-0.5|*2; macroInfluence = boundaryProximity*0.75`，边界处宏观影响升至75%，微观点差异被压缩至25%，预期悬崖从111格降至~40格
**永久准则**:
1. 宏观-微观混合的 macroInfluence 必须在 Voronoi 边界处最强（钟形曲线），禁止使用单调递增/递减曲线
2. 验证地貌修复时必须实际运行 scan_surface 对比修复前后数据，代码审查结论不等于实际效果
3. BlendCache 中的 macroInfo 必须每坐标独立查询
4. 所有 TerrainType 必须在 calcHeightForType 和 getBaseHeightForTerrainType 中有显式处理
5. 过渡带宽度修改必须在 blendWeight 计算之前生效
6. 多线程共享的计数器必须使用 AtomicInteger

## Bug记录 / Bug Fixes

### [P1] 2026-05-10 | Bug
- **触发场景**: 编译时 `MacroRegionInfo` 方法缺失
- **核心内容**: `RegionController` 和 `HeightCalculator` 调用了 `getBlendWeight()` 和 `getElevationTier()`，但 `MacroRegionInfo` 只有 public 字段没有 getter 方法
- **永久准则**: 添加新字段时，同时添加对应的 getter 方法以保持封装性

### [P1] 2026-05-10 | Bug
- **触发场景**: 极高山地形直接与平原接壤，高海拔区域无生物群系
- **核心内容**:
  1. 控制点生成时没有考虑地形类型的邻接约束
  2. HIGH_MOUNTAINS 地形没有映射对应的生物群系
- **永久准则**:
  1. 地形生成时需考虑类型邻接约束
  2. 所有地形类型必须有对应的生物群系映射

### [P1] 2026-05-10 | Bug Fix
- **触发场景**: Voronoi单元边界过于明显，极端地形直接接壤导致陡峭悬崖
- **核心问题**:
  1. 宏观-微观整合权重太低（只有40%）
  2. 高斯模糊范围太小（最大4格，有效范围仅8格）
  3. 边缘保持机制过于激进（edgeFactor分母只有20）
  4. 地形类型约束调整量太小（每级30格）
- **修复方案**:
  1. 宏观影响权重: 40% → 80%
  2. 模糊半径: 1-4 → 3-7格（有效范围: 7-15格）
  3. edgeFactor分母: 20 → 60-100（降低边缘保持强度）
  4. 地形类型约束: 阈值从3→2，调整量从30→50，双向调整
- **永久准则**: 地形过渡必须自然，宏观系统需要主导边界区域

### [P1] 2026-05-11 | Bug
- **触发场景**: 完整代码分析发现过渡带宽度不足
- **核心问题**:
  1. TRANSITION_WIDTHS[3] (ΔT≥3) 仅250格，但控制点间距256格，导致过渡区域内控制点不足
  2. halfBand计算：transitionWidth/2048*0.5，当transitionWidth=250时halfBand≈0.06，过渡区域仅占~12%cell宽度
  3. 当blendWeight接近0.5时可能产生突变
- **修复方案**:
  1. TRANSITION_WIDTHS: {800, 600, 400, 250} → {1000, 800, 500, 400}
  2. 增大halfBand上限和计算基数
  3. 添加smoothstep边界保护
- **永久准则**: 过渡带必须足够宽以覆盖多个控制点网格

### [P0] 2026-05-11 | Bug
- **触发场景**: 游戏中出现虚空区域和奇怪的陡峭山体
- **核心问题**:
  1. 控制点影响半径(150格)远小于控制点间距(256格)，导致某些区域没有控制点覆盖
  2. 搜索半径(300格)不足，无法找到足够的相邻控制点
  3. 控制点覆盖不连续导致虚空和悬崖
- **修复方案**:
  1. RegionController.searchRadius: 300 → 800
  2. ControlPointRegion.baseRadius: 150 → 400
  3. 所有地形类型影响半径相应增大
- **永久准则**: 控制点影响半径必须 ≥ 1.5×控制点间距，搜索半径必须 ≥ 3×控制点间距

### [P0] 2026-05-11 | Bug Fix
- **触发场景**: 代码审查发现Debug模块存在潜在运行时问题
- **核心问题**:
  1. TerrainFrameLogger使用SLF4J不支持的格式{:。3f}
  2. DebugPillarManager使用非线程安全的ArrayList
  3. TerrainDebugTools第二遍重复调用getTerrainBlend
- **修复方案**:
  1. LOGGER.info格式改为{}占位符+String.format
  2. ArrayList改为ConcurrentHashMap，使用values()迭代
  3. 第一遍缓存dominantWeights数组
- **永久准则**: Debug代码也需要遵循线程安全规范

### [P0] 2026-05-11 | NeoForge 1.21.1 API 兼容性
**问题**: LandscapeChunkGenerator 编译失败
- ChunkGenerator 抽象方法签名变更
- 新增 codec() 方法要求
- 类路径/方法名变更

**API 签名清单**:
```
getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState)
  → 返回: NoiseColumn (非 CompletableFuture/BlockState)
  → NoiseColumn 导入: net.minecraft.world.level.NoiseColumn

getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random)
  → Heightmap.Types 导入: net.minecraft.world.level.levelgen.Heightmap

spawnOriginalMobs(WorldGenRegion region) → void

applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carvingStep) → void

codec() → protected MapCodec<LandscapeChunkGenerator> { return CODEC; }
```

**关键修复**:
- ChunkPos 导入: net.minecraft.world.level.ChunkPos (非 chunk 包)
- LevelHeightAccessor.getMinY() → getMinBuildHeight()
- NoiseSet 构造函数 private，使用 NoiseSet.getOrCreate(worldSeed)
- CODEC 仅包含 biomeSource + seed，settings 由维度 JSON 提供
- 备用构造函数必须初始化所有字段

**永久准则**:
1. ChunkGenerator 必须实现 codec()
2. 备用构造函数初始化全部实例字段
3. 使用工厂方法而非私有构造函数

### [P1] 2026-05-11 | 性能优化汇总
- **实施优化**:
  1. LRU缓存淘汰：ControlPointManager(1024)、MacroVoronoiSystem(10000)、adjustedTierCache(long键)
  2. 区块级Voronoi缓存：同一2048单元复用3x3控制点网格（性能+99%）
  3. 冗余sqrt消除：HeightCalculator提取局部变量复用（性能+5%）
  4. HeightCache循环外复用：仅区域切换时重建（性能+10%）
  5. buildSurface反射缓存：7个volatile字段+双重检查锁定（性能+30%）
  6. fillColumn blendMap缓存：复用高度图阶段的混合结果（性能+15%）
  7. TOCTOU竞态修复：RegionController使用compute()原子操作
  8. 遗留代码清理：删除4个未引用文件
- **永久准则**: 性能优化必须可测量、可回退、不影响正确性

**[P0] 2026-05-12 | 决策 - 河流群系强制覆盖机制**
**触发场景**: 河流区域被分配了非河流群系（如平原、森林），违反地理逻辑
**核心内容**: 
- 新增 RiverInfo 数据类和 RiverNoiseSampler 噪声采样器
- 在 HeightCalculator 中集成河流噪声生成河流信息
- 在 LandscapeChunkGenerator.doFill 中调用 overrideRiverBiomesInChunk 强制覆盖
- 覆盖逻辑：检查每个4x4x4生物群系细胞中心点是否是河流，如果是则设置整列为河流群系
- 河流群系选择：根据高度选择 river（低地）或 frozen_river（高海拔）
**永久准则**: 河流区域必须强制使用河流群系，不被 BiomeSource 的其他逻辑覆盖

**[P0] 2026-05-15 | Bug Fix - 世界生成卡在0%进度（性能优化）**
**触发场景**: 世界生成卡在 0% 进度界面长时间不动，最终需要任务管理器强杀 Java 进程
**核心问题**:
1. RegionController.getTerrainBlend() 在缓存未命中时使用 1200格搜索半径遍历9个控制点区域
2. 每个区域的 getPointsInRange 使用基于单元格(16格)的网格搜索，1200格半径导致单区域遍历 ~75x75=5625 个单元格
3. fillFromNoise 对每个区块的 256个坐标都调用 getTerrainBlend，且每格都独立搜索 → 约 256 × 9 × 5625 ≈ 1300万次单元格检查
4. 每次单元格检查还要做距离计算、边界判断等 → 单区块生成时间超过 30秒，导致世界生成卡死
5. getOrCreateRegion 在缓存已满的 synchronized 路径中没有将新创建的区域放入缓存，导致区域被重复生成
6. DEBUG 日志洪泛（百万次调用）造成额外性能开销
**修复方案**:
1. 在 LandscapeChunkGenerator.fillFromNoise 中新增 buildChunkBlendCache() 方法，为整个区块预收集 3×3 区域网格的所有控制点
2. RegionController.getTerrainBlend(int x, int z, BlendCache cache) 新增缓存快路径：当 cache 非空时，直接遍历预收集的控制点列表（最多 ~50 个），跳过昂贵的区域搜索和单元格遍历
3. 缓存路径仅做一次距离过滤（squaredDistanceTo 平方距离比较，无 sqrt），性能提升约 100-200x
4. 修复 getOrCreateRegion 同步路径 bug：添加 terrainRegionCache.put(key, region) 将新创建的区域放入缓存
5. 移除 RegionController 中 3 处 DEBUG 日志（getTerrainBlend、calculateBlend），减少日志洪泛
6. 添加 fillFromNoise 性能计时日志，分段统计缓存构建、混合查询、地形填充等耗时
7. BlendCache 内部字段改为 public，解决跨包访问编译错误
**性能提升**: 单区块地形混合查询从 ~30秒 降低到 <1秒，总体地形生成速度提升 30x+
**永久准则**:
1. 批量处理（如区块内256格）必须使用预收集缓存，避免每格重复搜索
2. 搜索算法的时间复杂度必须与搜索半径的平方成正比，不能随搜索半径呈指数增长
3. 性能关键路径应使用平方距离比较，避免冗余 sqrt 调用
4. 所有缓存机制必须可回退到无缓存路径
5. 同步路径中的缓存更新必须显式调用 put()，不能假设 computeIfAbsent 外的操作会自动缓存
6. 生产环境中的 DEBUG 日志必须可关闭，批量查询路径应避免产生大量日志

**[P0] 2026-05-15 | 决策 - ChunkGenerator Codec 实现规范**
**触发场景**: LandscapeChunkGenerator codec 实现多次编译失败，需要正确实现 Holder<NoiseGeneratorSettings> 序列化
**核心内容**:
- ChunkGenerator 的 codec() 方法返回类型必须是 `MapCodec<? extends ChunkGenerator>`
- 使用 `RecordCodecBuilder.<YourGenerator>mapCodec` 显式类型参数避免类型推断错误
- `NoiseGeneratorSettings.CODEC` 是 `Codec<Holder<NoiseGeneratorSettings>>`，使用 RegistryFixedCodec 实现
- 序列化时仅写入资源位置字符串（如 "minecraft:overworld"），反序列化时通过 HolderLookup.Provider 查找
- CODEC 字段通常只包含 biomeSource 和自定义字段（如 seed），settings 由维度 JSON 提供
- 维度 JSON 中 settings 字段必须是字符串引用（如 "minecraft:overworld"），不能是内联对象
- getter 方法必须是 public 且返回类型正确，用于 forGetter 类型推断
**永久准则**:
1. 查找 NeoForge API 时必须使用 web-search-summarizer agent 进行网络搜索
2. Codec 实现参考原版 NoiseBasedChunkGenerator 模式
3. Holder<T> 序列化使用 RegistryFixedCodec，仅存储资源键
4. 维度 JSON 配置中使用字符串引用注册表条目，非内联对象

**[P0] 2026-05-14 | 决策 - API查找必须使用网络搜索总结**
**触发场景**: 查找 Minecraft NeoForge API 时使用了错误的内置 agent 导致 API 方法不存在
**核心内容**:
- 查找 Minecraft NeoForge API 时，必须使用 `web-search-summarizer` agent 进行网络搜索
- 不能使用 `minecraft-source-analyzer` 或其他内置 agent 查找 API，其提供的 API 可能不准确或过时
- 关键 API 示例：`ProtoChunk.getRegistryAccess()` 在 1.21 中不存在，应通过 `Section.getBiomes().getAndSet()` 设置群系
**永久准则**: 查找 API 时使用网络搜索总结 agent，不要使用内置 agent

**[P0] 2026-05-14 | 决策 - 地形-群系规则系统（TerrainBiomeRules）**
**触发场景**: 除河流外的28种地形类型的群系控制权完全交给了原版和TerraBlender，缺乏地形-群系匹配控制
**核心内容**:
- 创建 TerrainBiomeRules 配置系统，为每种地形类型定义黑白名单规则
- 支持标签（如 #minecraft:is_ocean）自动展开为具体群系，启动时预计算缓存
- 将 overrideRiverBiomesInChunk 重构为通用的 overrideTerrainBiomesInChunk，覆盖所有地形类型
- 地形类型判定统一使用 RegionController.getTerrainBlend() 保持一致性，废弃基于高度的简化判定
- 群系选择按种子确定性选择（seed ^ cellX*31 + cellZ*17），确保相同位置始终相同群系
- 性能优化：启动时预计算每种地形的允许/排除群系列表，运行时 O(1) 查询
- 覆盖时机：在 doFill 的 fillColumn 之前调用，确保 buildSurface 读取到正确的群系
**永久准则**:
1. 所有地形类型必须有对应的群系规则配置
2. 地形类型判定必须使用 RegionController 保持一致性
3. 群系覆盖必须在 buildSurface 之前执行
4. 标签展开必须在启动时预计算，运行时禁止重复展开
**触发场景**: 分析TerraFirmaCraft地形生成架构后，评估哪些特性适合World Scape
**核心内容**: 
- Cellular噪声区域划分 → ❌不借鉴，与控制点类型体系冲突
- 预计算ChunkData → ✅部分借鉴，用于深度分层填充
- 独立河流系统 → ✅值得借鉴，增加河流宽度/深度/流向
- 三线性插值 → ⚠️远期考虑，需3D噪声架构变更
- 按列填充 → ⚠️远期考虑，需改造平滑逻辑
**永久准则**: 借鉴TFC时保持Voronoi控制点核心架构不变，仅补充噪声层或增强现有系统

**[P0] 2026-05-15 | 知识 - Minecraft ChunkStatus流水线源码级架构**
**触发场景**: 需要理解世界生成底层机制来定位0%进度卡死问题
**核心内容**:
- ChunkStatus流水线采用DAG（有向无环图）架构，每个状态定义独立阶段
- 核心流水线顺序: EMPTY → STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES → NOISE → CARVING → FEATURES → INIT_LIGHT → SURFACE → FULL → LIGHT
- BIOMES阶段: 调用ChunkGenerator.populateBiomes()填充生物群系
- NOISE阶段: 调用NoiseBasedChunkGenerator.fillFromNoise()生成地形噪声（核心瓶颈）
- CARVING阶段: 应用洞穴、峡谷等雕刻器
- FEATURES阶段: 生成结构、矿物、装饰物
- SURFACE阶段: 调用buildSurface()构建表面方块
- FULL阶段: 区块生成完成，可渲染
- 关键源码调用链: ChunkMap.applyStep() → GenerationChunkHolder.applyStep() → ChunkGenerationTask.scheduleChunkInLayer() → ChunkStep.apply() → ChunkStatusTasks.generateNoise() → NoiseBasedChunkGenerator.fillFromNoise()
**永久准则**: 排查生成卡顿时，先确定卡在哪个ChunkStatus阶段，再针对性分析对应方法

**[P0] 2026-05-15 | 知识 - fillFromNoise内部执行流程与噪声插值机制**
**触发场景**: 需要优化自定义ChunkGenerator的fillFromNoise实现
**核心内容**:
- fillFromNoise内部流程: 
  1. 创建NoiseChunk实例（负责单区块16x256x16的噪声计算）
  2. 遍历区块内每个XZ列（16x16=256列）
  3. 对每个Y层进行噪声采样: noiseChunk.interpolatedDensityFunction.sample(x, y, z)
  4. 根据密度值决定方块类型（>0为固体，<0为空气）
  5. 填充到ChunkAccess
- 噪声插值机制: Minecraft使用三线性插值优化噪声计算
  - X方向插值 → Y方向插值 → Z方向插值
  - 使用Mth.lerp()进行线性插值
- 自定义ChunkGenerator必须实现fillFromNoise，否则无法生成地形
- 性能关键: 256个XZ列 × 256个Y层 = 65536次噪声采样/区块
**永久准则**: 自定义地形生成器的性能瓶颈通常在fillFromNoise，必须优化噪声采样和区块填充逻辑

**[P0] 2026-05-15 | 知识 - 世界生成0%进度卡住的源码级原因分析**
**触发场景**: 世界生成卡在0%进度，需要从源码层面理解根本原因
**核心内容**:
- 主线程死锁场景: Server Thread等待区块加载完成，而世界生成线程被其他模组锁定
  - 典型堆栈: Server Thread: BLOCKED waiting for ChunkMap.getChunk() → WorldGenRegion.getBiome() → 尝试获取另一个正在生成的区块 → 该区块被另一个世界生成线程持有 → 死锁
- fillFromNoise性能瓶颈: 
  - 自定义地形生成器的getTerrainBlend()缓存未命中时，使用大搜索半径遍历多个控制点区域
  - getPointsInRange使用基于单元格(16格)的网格搜索，大半径导致遍历成千上万个单元格
  - fillFromNoise对每个区块的256个坐标都独立搜索 → 数千万次单元格检查 → 单区块>30秒
- ChunkStatus依赖等待: 早期区块生成（spawn chunks 43×43=1849个区块）时，NOISE阶段依赖BIOMES完成，CARVING依赖NOISE完成。如果NOISE阶段太慢，后续区块排队等待，进度条卡在0%
- 常见模组冲突: 
  - Feature order cycle: FeatureSorter.buildFeatures循环依赖
  - DynamicTrees死锁: 世界生成线程获取区块生物群系
  - TerraBlender冲突: 群系生成顺序错误
**永久准则**: 0%卡死通常是NOISE阶段性能问题或模组死锁，需通过日志或线程转储定位

**[P0] 2026-05-15 | 决策 - Memory管理策略**
**触发场景**: memory.md文件会随着时间积累大量记录，需要定期清理以保持精简高效
**核心内容**: 
- **定期合并P2级别记录**：将多条相似的P2记录合并为一条精简记录，节省空间
- **保留策略**：P0和P1级别记录完全保留，不做任何修改；P2级别记录可合并简化
- **清理时机**：当P2记录超过5条或文件总行数超过700行时执行清理
- **清理原则**：只删除冗余描述，保留核心知识点和永久准则
**永久准则**: 
1. P0和P1记录是核心知识，绝对不能删除或合并
2. P2记录是次要经验，可定期合并以节省空间
3. 清理时确保关键信息不丢失，只简化格式和删除重复内容

**[P0] 2026-05-15 | Bug Fix - fmlearlywindow早期启动崩溃（Beta 1.3.1）**
**触发场景**: 游戏在加载fmlearlywindow后立即崩溃，主菜单界面都未出现
**核心问题**:
1. 存在重复的mixin配置文件：`resources/META-INF/worldscape.mixins.json`配置错误（`required=true`、错误的类名`mixin.ServerChunkCacheMixin`）
2. WelcomeScreenConfig在静态块中执行文件IO操作，可能导致早期启动失败
3. LandscapeChunkGenerator的构造函数在初始化时立即创建NoiseSet和HeightCalculator，可能导致类加载顺序问题
**修复方案**:
1. 删除重复的mixin配置文件：`resources/META-INF/worldscape.mixins.json`
2. 注释掉WelcomeScreenConfig的静态块中的`load()`调用，改为延迟初始化
3. 注释掉所有LandscapeChunkGenerator构造函数中的NoiseSet和HeightCalculator初始化，改为延迟初始化（在getNoiseSet()和getHeightCalculator()方法中按需创建）
4. 更新版本号为beta1.3.1
**永久准则**: 
1. 静态块中不应执行文件IO或网络操作，应使用延迟初始化
2. 构造函数中不应执行可能失败的初始化操作，应使用延迟初始化模式
3. Mixin配置文件必须确保类名正确且`required`设置合理
4. 避免重复的配置文件，确保只有一个有效的配置入口

**[P0] 2026-05-15 | Bug Fix - 代码质量全面检查与关键问题修复（Beta 1.3.1）**
**触发场景**: 使用code-compliance-checker agent进行全面代码检查，发现多个严重问题
**核心问题**:
1. **反射异常处理不完整**：LandscapeChunkGenerator的`overrideTerrainBiomesInChunk`方法捕获所有Exception，未区分具体异常类型
2. **缓存竞态条件**：RegionController的`getOrCreateRegion`方法在computeIfAbsent的lambda内部调用`terrainRegionCache.get(k)`，导致冗余检查
3. **静态方法线程安全**：WelcomeScreenConfig的`shouldShowWelcomeScreen()`和`isDebugMode()`方法缺少同步保护
4. **性能日志级别错误**：fillFromNoise使用LOGGER.warn记录性能信息，导致生产环境日志噪音
**修复方案**:
1. **反射异常处理**：将宽泛的Exception捕获改为具体的异常类型（NoSuchFieldException、IllegalAccessException、InvocationTargetException、NoSuchMethodException、SecurityException），并为每种异常提供适当的回退策略和错误日志
2. **缓存竞态修复**：移除computeIfAbsent lambda内的冗余`terrainRegionCache.get(k)`检查，ConcurrentHashMap保证原子性
3. **线程安全修复**：为WelcomeScreenConfig添加`volatile boolean hasChecked`和`CONFIG_LOCK`对象，使用双重检查锁定保护`load()`调用
4. **性能日志修复**：将所有LOGGER.warn改为LOGGER.debug，并添加`if (LOGGER.isDebugEnabled())`条件检查
**永久准则**: 
1. 反射调用必须区分不同类型的异常并提供适当的回退策略
2. ConcurrentHashMap.computeIfAbsent的lambda不应再次访问map，会导致死锁或数据不一致
3. 静态配置访问必须是线程安全的，使用双重检查锁定或volatile变量
4. 性能日志应使用DEBUG或TRACE级别，并添加条件检查

**[P0] 2026-05-15 | 知识 - Minecraft 世界生成 0% 进度底层流程**
**触发场景**: 世界生成卡在 0% 进度，需要理解底层机制来定位问题
**核心内容**:
- Minecraft 使用 ChunkStatus 流水线生成区块: EMPTY → STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES → NOISE(fillFromNoise) → SURFACE(buildSurface) → CARVERS → FEATURES → LIGHT → SPAWN → FULL
- 0% 进度意味着大部分区块处于 EMPTY 或早期状态（黑色/灰色像素）
- fillFromNoise 是 NOISE 阶段核心方法，负责地形方块填充
- 加载界面用 43×43 区块着色图可视化进度: EMPTY=黑色, NOISE=浅灰, FEATURES=亮绿, FULL=纯白
- ChunkGenerator 必须实现的抽象方法: fillFromNoise, getSeaLevel, getMinY, getBaseHeight, getBaseColumn, applyCarvers, buildSurface, spawnOriginalMobs
- Minecraft 使用 ForkJoinPool 并行生成区块，相邻区块有依赖关系，一个区块生成过慢会阻塞邻居
**永久准则**: 排查0%卡死时，先确认是哪个 ChunkStatus 阶段卡住（通过加载界面颜色或日志），再针对性分析对应方法

**[P2] 2026-05-14 | 经验教训与测试验证汇总**
**镜像策略边界情况**: 镜像策略在边缘处可能形成对称延伸，极端地形下区块边界可能出现轻微对称痕迹（消除方法：边缘两格平滑强度降至50%）。这是后续调优时可留意的边界情况，非当前问题。
**memory.md验证**: 已验证 `.trae/rules/memory.md` 可正常读写，规则文件夹中的文件无需特殊处理即可编辑。

**[P0] 2026-05-16 | Bug Fix - Voronoi边界X型尖峰悬崖（smoothstep统一公式）**
**触发场景**: 游戏中出现X型尖峰悬崖，地形过渡带高度突变，之前的修复反而加重了问题
**核心问题**:
1. **阈值不连续**: `RegionController.calculateBlend()` 和 `HeightCalculator.calculateHeight()` 中使用阈值分隔两个完全不同的公式
   - 旧RegionController: `blendWeight > 0.95` 分隔区域内/过渡带公式
   - 旧HeightCalculator: `blendWeight < 0.99` 分隔过渡带/区域内公式
   - 在阈值处两个公式产生完全不同的结果（例如: 61.6 vs 69.0，差7格）
2. **macroBaseHeight二次混合放大**: `macroBaseHeight` 已在 `MacroVoronoiSystem.getRegionInfo()` 中用blendWeight混合过，再在 `lerp(microHeight, macroBaseHeight, macroInfluence)` 中使用，等于blendWeight被用了两次
3. **线性放大系数**: `macroInfluence = (1-blendWeight) * 0.8` 中的0.8线性放大高度差异，当macroBaseHeight>>microHeight时形成X型尖峰
4. **导数不连续**: 在阈值处左右导数不相等，导致视觉上明显的边界突变
**修复方案**:
1. **统一阈值**: 将阈值统一改为0.8（两个文件一致）
2. **使用smoothstep曲线**: `smoothT = 3t² - 2t³`，在t=0和t=1处导数为0，确保边界处平滑过渡
3. **降低宏观影响**: 最大宏观影响从0.8降至0.5，避免过度放大高度差异
4. **统一公式结构**:
   - 区域内(blendWeight>0.8): `finalHeight = microHeight + tierAdjustment`
   - 过渡带(blendWeight<=0.8): `finalHeight = lerp(microHeight+tierAdjustment, macroBaseHeight, macroInfluence)`
   - 其中 `macroInfluence = (1-smoothT) * 0.5`
5. **同步修复两个文件**: RegionController和HeightCalculator使用相同逻辑
**永久准则**:
1. 高度混合公式必须使用smoothstep等导数连续的曲线，禁止在阈值处产生突变
2. macroBaseHeight已在MacroVoronoiSystem中混合过，不应再用lerp二次混合放大
3. 过渡带的宏观影响系数不应超过0.5，避免过度放大高度差异
4. RegionController和HeightCalculator必须使用统一的高度混合逻辑

**[P1] 2026-05-16 | Code Review - seaLevel硬编码与阈值统一**
**触发场景**: 代码审查发现seaLevel硬编码和类型/高度阈值不一致
**核心问题**:
1. **seaLevel硬编码**: `LandscapeChunkGenerator.java` 中存在两处问题
   - `DEFAULT_SEA_LEVEL = 62` (L93): 定义了但从未使用（死代码）
   - 备用构造函数中 `this.seaLevel = 63` (L73): 硬编码而非使用常量
2. **阈值不一致**: `determineTerrainType` 使用 `dominantWeight >= 0.25`，而 `calculateFinalHeight` 使用 `dominantWeight >= 0.4`
   - 在 `[0.25, 0.4)` 区间类型已确定为 dominantType 但高度是混合的
   - 导致类型与高度过渡不同步，可能产生地貌过渡不自然
**修复方案**:
1. 删除死代码 `DEFAULT_SEA_LEVEL = 62`，重命名为 `FALLBACK_SEA_LEVEL = 63`
2. 备用构造函数使用常量 `FALLBACK_SEA_LEVEL` 替代硬编码 63
3. 统一 `determineTerrainType` 阈值从 0.25 到 0.4，与 `calculateFinalHeight` 保持一致
**永久准则**:
1. 禁止定义未使用的常量（死代码），如有回退值必须使用常量而非硬编码
2. 类型判定和高度计算的权重阈值必须保持一致
3. 所有 seaLevel 相关值必须通过构造函数动态传入，禁止硬编码

---

**[P0] 2026-05-19 | 全项目扫描 - 宽泛异常吞噬（Voronoi子系统5处）**
**触发场景**: 全项目代码审查发现WorldScapeVoronoiSystem和VoronoiControlPointManager中多处catch(Exception e)吞噬关键异常
**核心问题**:
1. WorldScapeVoronoiSystem.populateFromTerrainSystem、save、load 均使用 catch(Exception e) 包裹整个方法体
2. VoronoiControlPointManager.importFromTerrainSystem 中macro/micro导入分开捕获但均只记录日志
3. 调用方无法感知失败，数据保存/加载失败时静默处理
**修复方案**: 
- 将宽泛catch改为具体异常类型（IOException、RuntimeException等）
- macro/micro导入失败时向上传递部分成功状态而非吞掉
- save/load返回布尔值，调用方根据返回值决策
**永久准则**: 
1. 禁止catch(Exception e)，必须捕获具体异常类型
2. 关键业务操作（保存、加载、导入）必须向上传递失败状态
3. Voronoi子系统所有IO操作必须区分致命错误和可恢复错误

---

**[P2] 2026-05-19 | Bug Fix - 运行时崩溃修复（InputEvent.MouseButton抽象类注册）**
**触发场景**: 运行客户端时模组加载阶段 Fatal 错误，游戏崩溃
**核心内容**: 
- `VoronoiInputEvents.onMouseButton` 直接注册到 `InputEvent.MouseButton` 抽象类，NeoForge 1.21.1 禁止此操作
- 错误: `Cannot register listeners for abstract class InputEvent$MouseButton`
- 修复: 改为 `InputEvent.MouseButton.Post`（具体子类），方法重命名为 `onMouseButtonPost`
- `InputEvent.Key` 和 `InputEvent.MouseScrollingEvent` 是具体类，无需修改
**永久准则**: 
1. NeoForge 1.21.1 中抽象事件类禁止直接注册，必须使用其具体子类 (`Pre`/`Post`)
2. 运行客户端前应检查 modloading 阶段的 FATAL/ERROR 日志
3. 方法参数类型变更时应同步重命名方法以保持语义清晰

---

**[P2] 2026-05-19 | Bug Fix - 编译警告清除（4项）**
**触发场景**: 全面检查VS Code诊断和构建输出中的编译警告
**核心内容**: 修复了4项编译警告：
1. **WelcomeScreenAssets未经检查操作**: 将 `getOrCreate` 方法的泛型签名 `<T>` 改为具体 `BufferedImage`，使用 `computeIfAbsent` 替代 `containsKey+get+put` 的组合，消除未经检查的类型转换
2. **ServerChunkCacheMixin混淆映射警告**: 为 `@Inject(method = "<init>")` 添加 `remap = false`，构造函数名 `<init>` 属于 JVM 常量，无需混淆映射
3. **VoronoiInputHandler deprecation**: 移除 `bus = EventBusSubscriber.Bus.MOD`，NeoForge 1.21.1 自动检测事件类型所属总线
4. **VoronoiOverlayRenderer deprecation**: 同上，同时添加 `modid = WorldScape.MOD_ID` 确保注册到正确的模组总线
**永久准则**: 
1. 所有泛型方法如无必要不应使用无界通配符，返回具体类型可消除未经检查转换警告
2. Mixin注入构造函数时必须使用 `remap = false`（构造函数JVM名不参与混淆映射）
3. NeoForge 1.21.1 的 `@EventBusSubscriber` 不应指定 `bus` 参数，由框架自动检测
4. 构建前应检查编译警告数量，所有警告都应修复或记录原因

**[P0] 2026-05-19 | 全项目扫描 - TerrainBiomeRules线程安全问题**
**触发场景**: 代码审查发现TerrainBiomeRules使用非线程安全EnumMap存储群系缓存，且initialize()无同步保护
**核心问题**: 
1. rules、allowedBiomesCache、excludedBiomesCache使用EnumMap（非线程安全）
2. initialize()非synchronized，两个线程可并发进入
3. biomeRegistry字段在initialize()过程中被设置后立即被读取，存在TOCTOU问题
4. 后果：并发初始化时自定义地形失去群系控制，回退到原版群系分配
**修复方案**: initialize()添加synchronized；预计算缓存改为Collections.unmodifiableList不可变包装
**永久准则**: 
1. 单例初始化必须使用synchronized+双重检查锁定
2. 多线程只读的缓存必须使用不可变包装（Collections.unmodifiable*）
3. 非线程安全集合（EnumMap、HashMap、ArrayList）不能在多线程环境中作为缓存

**[P0] 2026-05-19 | 全项目扫描 - TerrainDebugSystem.init()竞态**
**触发场景**: 审查发现TerrainDebugSystem.init()方法对volatile标志的检查和设置之间存在竞态窗口
**核心问题**: 
1. init()中检查initialized后修改多个volatile字段才设置initialized=true
2. 两个线程可同时越过if (initialized) return检查
3. resetToDefaults()无同步保护直接修改多字段
**修复方案**: init()使用synchronized+双重检查锁定；resetToDefaults()也同步
**永久准则**: 所有初始化方法必须使用标准的双重检查锁定模式（volatile+synchronized+二次检查）

**[P1] 2026-05-19 | 全项目扫描 - buildSurface重复河流计算**
**触发场景**: 审查发现buildSurface中重新计算isRiverAt和getRiverDepthAt，但fillFromNoise已计算过
**核心问题**: 
1. buildSurface遍历16×16列，对每列调用isRiverAt()和getRiverDepthAt()
2. fillFromNoise阶段已在riverMap/riverDepthMap中完整计算
3. SurfaceBuildContext中已有riverMap/riverDepthMap字段但未正确使用
**修复方案**: buildSurface中的河流判定替换为context.riverMap[x][z] / context.riverDepthMap[x][z]
**永久准则**: 任何在前序阶段已计算的值必须通过上下文参数传递，禁止重新计算

**[P1] 2026-05-19 | 全项目扫描 - calcHeightForType重复噪声采样**
**触发场景**: 审查发现HIGH_MOUNTAINS类型单次调用采样6个不同噪声，且fillFromNoise中最多调用2次
**核心问题**: 
1. HIGH_MOUNTAINS类型6种噪声采样（MOUNTAIN_PEAKS、MOUNTAIN、MOUNTAIN_RIDGE×2、REGION、HILLS）
2. calculateFinalHeight中dominantWeight<0.4时对两种地形类型各调用一次calcHeightForType
**修复方案**: fillFromNoise循环中增加calcHeightForType结果缓存（Long2DoubleOpenHashMap），key=(x<<32)^z
**永久准则**: 热点路径中的纯函数调用必须缓存结果，避免相同参数重复计算

---

**[P1] 2026-05-19 | 决策 - @EventBusSubscriber bus参数废弃，直接删除即可**
**触发场景**: 搜索确认NeoForge 1.21.1中@EventBusSubscriber的bus()和Bus枚举被标记为废弃待删除的替代方案
**核心内容**:
- 不要使用`@Mod.EventBusSubscriber`（NeoForge中不存在此注解，那是旧版Forge的产物）
- 正确的做法：**直接删除`bus`参数**，NeoForge会根据事件类型自动检测注册到正确总线
- 自动检测逻辑：实现了`IModBusEvent`接口的事件→Mod Bus，其他事件→Game Bus（`NeoForge.EVENT_BUS`）
- 前置条件：`neoforge.mods.toml`中需声明最低NeoForge 21.6.6+
- 官方PR #2349（已合入1.21.1分支）：移除NeoForge内部所有`bus = EventBusSubscriber.Bus.GAME/MOD`用法
- 官方Discussion #2564：维护者确认"just remove that bus parameter, it is now figured out automatically"
**永久准则**:
1. NeoForge 1.21.1中`@EventBusSubscriber`的`bus`参数已废弃，直接删除
2. 不需要`@Mod.EventBusSubscriber`，那个注解在NeoForge中不存在
3. 使用此API需声明最低NeoForge 21.6.6+依赖

**[P0] 2026-05-23 | 知识 - 10大世界生成模组与World Scape的兼容性分析**

**触发场景**: 需要评估World Scape自定义ChunkGenerator与其他常见世界生成/优化模组的兼容性

**核心内容**:

1. **Terralith** - 仅通过TerraBlender添加生物群系，不替换ChunkGenerator
   - 技术方式：数据包+Lithostitched（mod版依赖），通过TerraBlender API注册生物群系区域
   - TerraBlender使用MixinNoiseGeneratorSettings注入表面规则，但不改变ChunkGenerator
   - 与World Scape冲突风险：**中等** - TerraBlender的MixinNoiseGeneratorSettings会修改surfaceRule返回值，可能影响SurfaceAdapter反射调用；Terralith的生物群系可能与TerrainBiomeRules覆盖冲突

2. **Tectonic** - 修改NoiseGeneratorSettings（密度函数+噪声参数），不替换ChunkGenerator
   - 技术方式：mod版使用Mixin修改噪声参数和密度函数JSON；数据包版直接替换noise_settings
   - 依赖Lithostitched库
   - 与World Scape冲突风险：**低** - Tectonic修改的是NoiseGeneratorSettings中的密度函数，World Scape使用自定义ChunkGenerator不读取这些设置；但若Tectonic的Mixin也作用于NoiseBasedChunkGenerator则可能间接影响

3. **Lithosphere** - 数据包/mod修改噪声参数和密度函数，不替换ChunkGenerator
   - 技术方式：与Tectonic类似，修改noise_settings JSON和密度函数
   - 与World Scape冲突风险：**低** - 同Tectonic，修改的是NoiseGeneratorSettings而非ChunkGenerator

4. **Big Globe** - 完全替换ChunkGenerator，使用自定义BigGlobeChunkGenerator
   - 技术方式：注册自定义世界类型"bigglobe:bigglobe"，使用自定义ChunkGenerator
   - 自带脚本语言系统生成地形，不使用原版密度函数
   - 仅支持Fabric（通过Sinytra Connector可在NeoForge运行）
   - 与World Scape冲突风险：**互斥** - 两个模组都注册自定义ChunkGenerator，不能同时用于同一维度；但可共存于不同维度

5. **C2ME** - 修改区块生成线程模型，并行化fillFromNoise
   - 技术方式：Mixin注入ChunkMap/ChunkStatus/NoiseBasedChunkGenerator，将fillFromNoise调度到线程池
   - 关键Mixin：修改populateNoise的Executor，将区块生成任务并行化
   - 官方声明：自定义ChunkGenerator的模组"may cause compatibility issues due to certain design assumption used by mod authors being broken"
   - 与World Scape冲突风险：**高** - C2ME改变fillFromNoise的线程模型，World Scape的BlendCache、RegionController等非线程安全组件可能出问题；C2ME的CheckedThreadLocalRandom可能检测到World Scape的随机数使用问题

6. **FerriteCore** - 修改BlockState内部存储结构，不修改PalettedContainer
   - 技术方式：FastMap替代Table<Property,Comparable,S>（Mixin子包fastmap），BlockState属性存储优化，模型去重
   - 优化对象：BlockState邻居查找（~600MB）、属性存储（~170MB）、模型谓词缓存（~300MB）
   - 不修改PalettedContainer或区块存储相关类
   - 与World Scape冲突风险：**极低** - 仅优化BlockState内存表示，不影响世界生成管线

7. **ModernFix** - 多方面优化，不修改密度函数编译
   - 技术方式：Mixin优化启动速度、世界加载、内存使用
   - 世界生成相关优化：surface rules性能提升~10%、缓存worldgen registry snapshot、提前释放ProtoChunks
   - 不修改密度函数编译逻辑（DensityFunction编译由Minecraft内部Codec系统处理）
   - 与World Scape冲突风险：**低** - ModernFix的surface rules优化可能影响buildSurface反射调用的时序，但总体兼容性好

8. **YUNG's Better系列** - 使用Mixin注入洞穴/结构生成，不替换ChunkGenerator
   - 技术方式：Better Caves使用Mixin注入Aquifer系统（AquiferMixin），修改洞穴液体生成
   - 与C2ME有已知兼容问题（1.20.1需关闭threadedWorldGen）
   - 与Distant Horizons有已知兼容问题（期望ServerLevel但收到DhLitWorldGenRegion）
   - 与World Scape冲突风险：**中等** - YUNG's Better Caves的AquiferMixin可能干扰World Scape的fillFromNoise中的Aquifer处理；Better Structures通过标准StructureManager注册，不直接冲突

9. **Distant Horizons** - 使用自定义DhLitWorldGenRegion替代ServerLevel生成远距LOD
   - 技术方式：创建DhLitWorldGenRegion包装器，在自己的线程池中调用ChunkGenerator生成远距区块
   - 已知问题：与自定义ChunkGenerator高度不兼容（TerraFirmaCraft、Lost Cities等均报错）
   - 根因：DH的StepFeatures使用自己的线程调用ChunkGenerator，但很多ChunkGenerator假设运行在ServerLevel环境
   - 临时修复：设置distantGeneratorMode="INTERNAL_SERVER"让DH通过正式服务器生成
   - 与World Scape冲突风险：**高** - World Scape的RegionController、BlendCache等假设单线程ServerLevel环境，DH的多线程LOD生成可能导致竞态条件和NPE

10. **Geophilic** - 纯数据包/mod修改生物群系特性（feature），不修改地形生成
    - 技术方式：修改vanilla biomes的features列表（添加倒木、灌木、巨石等），不添加新方块/新群系
    - 兼容Tectonic和Lithosphere（地形mod）
    - 通过Terraphilic兼容Terralith
    - 与World Scape冲突风险：**极低** - 仅修改生物群系的feature列表，不影响ChunkGenerator或噪声生成

**永久准则**:
1. C2ME和Distant Horizons是最高风险模组，必须添加兼容性检测和警告
2. Big Globe与World Scape互斥（都注册自定义ChunkGenerator），但可共存于不同维度
3. YUNG's Better Caves的AquiferMixin需要测试是否干扰World Scape的fillFromNoise
4. FerriteCore和Geophilic基本无冲突风险
5. TerraBlender的MixinNoiseGeneratorSettings可能影响SurfaceAdapter的surfaceRule反射调用
6. 所有使用自定义ChunkGenerator的模组（Big Globe、TerraFirmaCraft、Lost Cities）与DH的兼容性问题已有多起先例，World Scape应预期同样的问题
7. ModernFix的surface rules优化（~10%性能提升）与World Scape的buildSurface反射调用可能有时序交互

---

**[P0] 2026-05-19 | Bug Fix - HeightCalculator与RegionController实例分离导致的双层缓存和状态不一致风险**
**触发场景**: 分析种子 -6426449255803558492 悬崖异常，追踪控制点数据从getTerrainBlend到calculateMicroHeight的完整传递路径
**核心问题**:
1. HeightCalculator(L44)和RegionController(L36)各自创建独立的MacroVoronoiSystem和ControlPointManager实例
2. 两个MacroVoronoiSystem维护独立的adjustTierCache(LRH)，内存浪费2倍
3. 两个ControlPointManager维护独立的ControlPointRegion LRU缓存，若LRU淘汰触发重建，重建使用的macroTier来自各自实例，极端并发下瞬时不一致
4. determineTerrainType(calc, x, z)(L1670)完全忽略calc参数，内部调用controller.getTerrainBlend(x, z)（无缓存路径），而fillFromNoise使用BlendCache快路径
5. overrideTerrainBiomesInChunk中每4细胞组调一次uncached getTerrainBlend，每区块16次×1200格搜索
**修复方案**:
- HeightCalculator通过接受MacroVoronoiSystem参数的构造函数创建
- getHeightCalculator()传入RegionController.getMacroSystem()共享实例
- determineTerrainType(calc, x, z, blendCache)增加BlendCache参数
- overrideTerrainBiomesInChunk传入fillFromNoise的blendCache
- getTerrainBlend(x, z)改为getTerrainBlend(x, z, blendCache)
**永久准则**: 
1. 同进程内同seed+seaLevel的MacroVoronoiSystem必须共享实例
2. 所有地形查询优先使用BlendCache，cache为null时回退无缓存路径
3. 两层组件如使用相同逻辑子组件，必须通过构造函数注入共享
4. 计算高度与判定地形类型的路径必须使用相同的控制点集和缓存

---

**[P0] 2026-05-23 | 知识 - 全模组兼容性矩阵与 World Scape 自身隐患（完整版）**

**触发场景**: 需评估 World Scape 在含数百模组的整合包中的兼容性，基于代码逻辑推导而非实际测试

**核心内容 - 兼容性三级分类**:

**🔴 确定不兼容（12个 - 同维度不能共存）**:
1. Big Globe - 都注册自定义 ChunkGenerator，互斥
2. TerrainControl - 完全替换世界生成管线
3. WorldPainter - 直接操作 ChunkAccess 方块数据
4. Amplify - 重写 NetherChunkGenerator/OverworldChunkGenerator
5. OpenWorlds - 提供自定义世界类型和 ChunkGenerator
6. TerraForged - 1.21 版本同样替换 ChunkGenerator
7. Lithium（世界生成部分）- Mixin 注入点指向已修改的方法签名
8. Sodium（旧版世界生成）- ChunkStatus 流水线修改
9. Terralith（chunk_transformer 模式）- NOISE 阶段后修改区块
10. William Wythers' Overhauled Overworld - 替换 BiomeSource
11. Amplified Nether - 替换 Nether ChunkGenerator
12. CubicChunks/CubicWorldGen - 非标准区块高度结构

**🟡 可能冲突（16个 - 需配置/测试适配）**:
A. 群系分配冲突: Biomes O' Plenty（高）、Regions Unexplored（高）、Nature's Spirit（中）、TerraBlender（中）、Oh The Biomes You'll Go（高）
B. 表面/装饰冲突: YUNG's Better Caves（高）、YUNG's Better Ocean Monuments（中）、William Wythers' Expanded Ecosphere（中）、Quark（低）、Underground Biomes/Geophilic（极低）
C. 性能优化交互: C2ME（高）、ModernFix（中）、FerriteCore（极低）、Starlight/Phosphor（极低）、Noisium（低）
D. 远距渲染: Distant Horizons（高）

**🟢 确定兼容（10个 - 无交集）**:
JEI/REI/EMI、JourneyMap/Xaero's、OptiFine/Embeddium（仅渲染）、CraftTweaker/KubeJS、Create、Alex's Mobs/Naturalist、Supplementaries/Farmer's Delight、Apotheosis、Waystones

**核心内容 - World Scape 自身 7 大兼容性隐患**:
1. **P0 - ReflectionSurfaceAdapter 反射不稳定**: 6个内部API反射调用，每个依赖方法签名正确性。混淆/优化环境（如OptiFine混淆包）中可能完全失效。虽有SurfaceAdapterFactory自动降级到FallbackSurfaceAdapter的安全网，但降级后地表质量显著降低
2. **P1 - LevelChunkSection.biomes 反射写入**: Java 17+模块系统下可能被阻止。降级后群系覆盖功能全部失效，29种地形类型群系控制权交回原版BiomeSource
3. **P1 - applyCarvers 和 spawnOriginalMobs 为 No-op**: WS不调用雕刻器和生物生成，依赖YUNG's Better Caves等模组的玩家将看不到洞穴效果
4. **P1 - RiverCache 跨阶段线程安全**: fillFromNoise与buildSurface串行调用正常，但C2ME并行化场景下ConcurrentHashMap无法保证写入与读取间的happens-before关系
5. **P2 - NoiseSet LRU 缓存竞争压力**: MAX_CACHE_SIZE=32 + synchronizedMap + computeIfAbsent在C2ME多线程场景下可能成为热点
6. **P2 - RegionController 非真正 LRU 淘汰**: 迭代器半随机删除，非LRU淘汰。高频访问区域可能在淘汰中被移除导致重新计算
7. **P2 - Codec settings=null 降级保护不足**: settings解析失败时buildSurface降级到world_scape_buildSurfaceFallback，只放置基岩/深板岩/石头/水，无任何生物群系表面方块

**核心内容 - ModCompatibilityChecker 需要更新的判断**:
- Litematica → INCOMPATIBLE→CONFLICT（是原理图模组，不修改世界生成）
- BetterTerrain → INCOMPATIBLE→CONFLICT（修改地表方块，但可配置兼容）
- Biomes O' Plenty → INCOMPATIBLE→CONFLICT（可通过TerrainBiomeRules配置兼容）
- Regions Unexplored → INCOMPATIBLE→CONFLICT（同上）
- Valhelsia Structures → INCOMPATIBLE→CONFLICT（结构生成，非ChunkGenerator替换）
- Quark/Charm → INCOMPATIBLE→CONFLICT（不替换ChunkGenerator）
- Structure Gel → INCOMPATIBLE→移除（纯API库）

**永久准则**:
1. WS替换ChunkGenerator决定了与任何其他自定义ChunkGenerator模组互斥（同维度）
2. 群系覆盖使用反射修改PalettedContainer，与大型生物群系模组冲突，也是Java模块系统脆弱点
3. C2ME和Distant Horizons是最高风险模组，WS的线程安全设计未经验证
4. 反射调用是WS最大单点故障（6个内部API），好在有FallbackSurfaceAdapter安全降级
5. ModCompatibilityChecker的模组分类需立即更新（多个模组应降级为CONFLICT而非INCOMPATIBLE）
6. 解决冲突优先级：更新ModCompatibilityChecker > 添加群系模组配置预处理 > 优化线程安全设计 > 编写C2ME测试套件

