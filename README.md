# HPTlib 1.20.1 Forge

这是一个面向 Minecraft 1.20.1 Forge 的通用附属库模板，使用 Forge 47.4.23、Java 17、Tinkers' Construct 3.11.2.166、Mantle 1.11.104 和 JEI 15.56.0.205。

匠魂和 Mantle 是运行时必需依赖；JEI 只作为客户端可选依赖，供共用的 JEI 代码编译和开发客户端运行使用。

## 通用结构定位强化

HPTlib 提供一个统一的 `hptlib:structure_eye` 匠魂能力强化、匠魂引导书条目和结构定位升级配方。其他附属只需要把自己的定位眼睛加入 `hptlib:structure_eyes` 物品标签，并在初始化时注册眼睛对应的结构标签和显示翻译键：

```java
// 在附属模组初始化时注册本附属的定位眼睛与结构对应关系。
StructureEyeRegistry.register(
        new ResourceLocation("example", "example_eye"),
        ExampleTags.Structures.EXAMPLE_LOCATED,
        "structure_eye.example.target.example_eye");
```

这样所有附属共用同一个词条和升级展示；每只眼睛的结构映射与翻译仍由提供该眼睛的附属负责。`hptlib:structure_eyes` 是升级配方的白名单，注册表则负责把实际放入的眼睛解析为对应结构，二者缺一不可。

```powershell
$env:JAVA_HOME = 'C:\Users\30819\.jdks\ms-17.0.18'
.\gradlew.bat build
.\gradlew.bat runClient
```

## 材料三围与属性公式（1.1.0）

材料可以填写硬度（hardness）、密度（density）、韧性（toughness）三项数值（简称"三围"）。给某个材料属性类型写好线性公式后，每次 `/reload` 时，HPTlib 会为所有填了三围的材料算出该属性类型的数值和特性，并在匠魂解析材料数据之前写进去。

算出来的就是普通的匠魂材料数据，部件提示框、手册、JEI、网络同步都照常工作，客户端不需要任何改动。整个功能只靠数据包驱动，不依赖 KubeJS。Tinkers KubeJS Arsenal 提供了对应的 KubeJS 写法。

### 材料三围

路径：`data/<材料命名空间>/tinkering/material_properties/<材料路径>.json`

```json
{
  "hardness": 4,
  "density": 5,
  "toughness": 9,
  "exclude": ["tconstruct:limb"]
}
```

- 没填的三围按 0 计算。
- 可以额外写其他数字键，作为自定义属性在公式里引用。
- `exclude` 里列出的属性类型不会按公式生成数据。

### 属性公式

路径：`data/<属性类型命名空间>/tinkering/stat_formulas/<属性类型路径>.json`，例如 `data/tconstruct/tinkering/stat_formulas/head.json`

```json
{
  "fields": {
    "melee_attack": { "hardness": 1 },
    "durability": { "base": 100, "toughness": 50, "round": "nearest", "min": 1 },
    "mining_tier": {
      "score": { "hardness": 1 },
      "levels": [[0, "minecraft:wood"], [6, "minecraft:iron"], [9, "minecraft:diamond"]]
    }
  },
  "traits": [
    { "modifier": "tconstruct:reinforced", "level": { "toughness": 0.25 }, "max": 5 }
  ]
}
```

- **数值字段**：结果 = `base` + Σ(系数 × 三围)。
  - 可以用 `min`、`max` 限制结果范围。
  - `round` 可选 `none`、`nearest`、`floor`、`ceil`；整数字段（如耐久）应写 `nearest`。
- **分段字段**：先用 `score` 按同样的规则算出分数，再在 `levels` 里取不超过该分数的最高一档。
- **特性**：`level` 算出后向下取整，小于 1 时不添加；`max` 限制最高等级。

### 部件公式

路径：`data/<部件命名空间>/tinkering/part_formulas/<部件路径>.json`，按部件物品 ID 命名，匠魂原版部件也可以写。

```json
{
  "stats": [
    { "stat": "tconstruct:attack_damage", "op": "add", "value": { "hardness": 0.5 } }
  ],
  "traits": [
    { "modifier": "tconstruct:lustrous", "level": { "hardness": 0.2 }, "max": 2 }
  ]
}
```

- **作用时机**：工具计算属性时（注入 `MaterialStatsModule.addToolStats`、`addTraits`），按每个部件所用材料的三围，在材料属性之外额外加成。
- **运算方式**：`op` 可选 `base`、`add`、`percent`、`multiply`，算出的值会乘上部件倍率。
- **生效条件**：只对按部件定义的工具生效；部件所用材料没有三围时，不加成。
- **客户端同步**：工具在客户端也会重算属性，所以服务端会在同步数据包时（`OnDatapackSyncEvent`），通过 `hptlib:material_properties` 通道把三围和部件公式发给客户端。

### 合并规则

- **生成范围**：每个填了三围的材料，会对每个写了公式的属性类型生成数据，`exclude` 排除的除外。
- **手写字段优先**：材料的 `tinkering/materials/stats` 里已经写了的字段保持不变，公式只补没写的字段。
- **特性追加**：公式算出的特性会追加到该属性类型现有的特性列表后面。现有列表依次取第一个存在的：该类型的 perStat、回退类型的 perStat、default。如果列表里已有同一个词条，取较高的等级。
- **整份覆盖**：两个文件路径相同时，高优先级数据包的文件会整份覆盖低优先级的。
- **属性类型本身不能用数据包定义**：属性类型必须在启动时注册，因为客户端要靠它解码材料同步包。

### Java API

`org.hp.hptlib.material.HptMaterialProperties` 提供以下方法，读取的是最近一次 `/reload` 的服务端快照：
- `get(MaterialId)`
- `getAll()`
- `getFormula(MaterialStatsId)`
- `getFormulas()`
- `getPartFormula(ResourceLocation)`
- `getPartFormulas()`

## 让其他项目直接依赖

开发时可以使用 Gradle Composite Build，不需要上传 CurseForge：

```groovy
// 在其他项目的 settings.gradle 中关联本地 HPTlib 工程。
includeBuild('E:/模组/发cf的模组/AAA-Tinkers/HPTlib/HPTlib-1201-forge') {
    dependencySubstitution {
        substitute module('org.hp.hptlib:hptlib') using project(':')
    }
}

// 在其他项目的 build.gradle 中声明 HPTlib 坐标。
dependencies {
    // Composite Build 提供的是已映射的工程产物，这里不要再套 fg.deobf。
    implementation 'org.hp.hptlib:hptlib:1.1.0'
}
```

也可以先执行 `publishToMavenLocal`，再让其他项目加入 `mavenLocal()`，并使用 `fg.deobf('org.hp.hptlib:hptlib:1.1.0')`。Composite Build 适合联调源码；本地 Maven 适合只依赖已构建的 JAR。两种方式都只解决 Gradle 编译依赖；正式整合包仍需要把 HPTlib JAR 放入 `mods`，因为 CurseForge/Maven 不会自动替玩家安装模组。

发布到共享本地 Maven 仓库时，可执行 `publish`，产物会写入当前项目的 `mcmodsrepo` 目录，其他工程将该目录加入 `repositories` 即可。
