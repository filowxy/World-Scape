# World Scape Agent 规范

本文件为项目 AI 协作的精确操作手册。所有规则必须严格遵守，不得自行解释或自由发挥。

---

## 1. 指令优先级（MUST / SHOULD / MAY）

- **MUST（必须）**：硬性规定，不执行就是 Bug。例如：“所有魔法数字 MUST 提取到 WorldScapeConstants”。
- **SHOULD（应当）**：最佳实践，除非有充分理由，否则遵守。例如：“应当优先使用 ConcurrentHashMap 而非 synchronized 块”。
- **MAY（可以）**：纯粹锦上添花，做不做都行。例如：“可以在此处添加注释说明设计意图”。

AI 不得自行判断“建议”的优先级。如遇未被标注优先级的指令，MUST 主动询问用户澄清。

---

## 2. 工具调用规格

### 2.1 文件修改

- **修改 Java 文件 MUST** 使用 `edit_file_search_replace` 进行精确替换。
- **禁止使用 PowerShell 或命令行** 直接写入 Java 文件内容（`Write`、`Set-Content` 等），以防止编码或管道中断导致文件损坏。
- **创建新文件 MAY** 使用 `Write` 工具。
- **删除文件 MUST** 在用户明确确认后进行，且 `git rm -f`、`git reset --hard`、`git clean -fd` 属于禁用指令。

### 2.2 构建与测试

- **每次代码修改后 MUST** 运行 `gradlew build` 验证编译通过。
- 如果编译失败，MUST 立即停止后续任务，分析并修复错误。
- **测试命令**：`gradlew build`（完整构建）、`gradlew compileJava`（仅编译）、`gradlew test`（运行测试）。

### 2.3 搜索与浏览

- **搜索文件 MUST** 使用 `file_search` 或 `search_by_regex`，不得盲目猜测文件路径。
- **浏览项目结构 MAY** 使用 `view_folder` 或 `view_files`。

---

## 3. 编码约定

### 3.1 常量与魔法数字

- **所有魔法数字 MUST** 提取到 `WorldScapeConstants.java` 中并添加中英双语注释。
- **数值常量 MUST** 使用 `public static final` 声明，并添加清晰的中英双语注释说明其含义和单位。
- **已有常量 MUST** 被引用，不得在代码中重复硬编码。

### 3.2 注释规范

- **所有新增或修改的代码 MUST** 包含中英双语注释。
- **类、方法、复杂逻辑 MUST** 有 JavaDoc 或行内注释。
- **修复 Bug 时 MUST** 在注释中说明修复原因和逻辑。

### 3.3 日志规范

- **所有日志输出 MUST** 使用 `WorldScape.LOGGER`（或模组内统一的 Logger 实例）。
- **禁止使用** `System.out`、`System.err`、`printStackTrace()`。
- **日志级别**：ERROR（严重错误）、WARN（警告）、INFO（重要信息）、DEBUG（调试详情）。

### 3.4 异常处理

- **禁止空 catch 块**。所有异常 MUST 至少记录到日志。
- **捕获具体异常类型**，不得使用 `catch (Exception e)` 宽泛捕获。
- **回退逻辑 MUST** 在触发时输出 WARN 级别日志，说明回退原因和影响范围。

---

## 4. 协作约定

### 4.1 主动提问机制

- **遇到以下情况时 MUST** 立即停止执行并询问用户：
  - 存在多个技术方案可选，且用户未明确指定。
  - 指令中存在矛盾或无法理解的要求。
  - 需要修改核心算法或大规模重构。
  - 发现可能影响兼容性的潜在风险。
- **提问时 MUST** 清晰列出所有不确定的点，等待用户澄清，不得自行猜测。

### 4.2 方案执行

- **禁止**向用户提供“方案A/方案B”的菜单式选项。
- **用户 MUST** 被提供唯一的、明确的执行方案。
- **如果确实存在多个可行方案**，AI MUST 先与用户讨论，由用户做出决策后，再生成精确的执行指令。

### 4.3 任务执行

- **每个任务 MUST** 只包含一个明确的目标、清晰的边界和验证方法。
- **禁止**在一条指令中包含多个互不相关的任务。
- **任务完成后 MUST** 输出简要的完成报告，列出修改的文件和关键变更点。

---

## 5. 危险区（禁止操作清单）

以下指令在任何情况下都**不得执行**，除非用户以书面形式明确授权：

| 指令模式 | 处置方式 |
|---|---|
| `git rm -f` | 立即阻断，警告用户 |
| `git reset --hard` | 立即阻断，警告用户 |
| `git clean -fd` | 立即阻断，警告用户 |
| `git checkout -f` | 立即阻断，警告用户 |
| `rm -rf`（或 PowerShell 等效指令） | 立即阻断，警告用户 |
| 任何可能修改 `.git` 目录的命令 | 立即阻断，警告用户 |

---

## 6. 术语统合

| 术语 | 含义 |
|------|------|
| LCG | LandscapeChunkGenerator |
| RCon | RegionController |
| HC | HeightCalculator |
| TFS | TerrainFieldSampler |
| MVS | MacroVoronoiSystem |
| CP | ControlPointRegion |
| SA | SurfaceAdapter |
| TBR | TerrainBiomeRules |
| WSC | WorldScapeConstants |
| calcH | calcHeightForType |
| calcFH | calculateFinalHeight |
| blend | getTerrainBlend 或 TerrainBlendResult |
| dominant | 主导地形类型及权重 |
| tier | 海拔等级 (0-5) |

### 问题严重度标签

在代码审查报告中使用以下标签：

| 标签 | 含义 |
|------|------|
| 🔴 P0 | 致命：影响功能正确性，必须立即修复 |
| 🟡 P1 | 重要：影响设计合理性，应尽快修复 |
| 🟢 P2 | 建议：代码质量优化，可延后处理 |

---

## 7. 文档版本

- **当前版本**：v1.0
- **生效日期**：2026-06-19