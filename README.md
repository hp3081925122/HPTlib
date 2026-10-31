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

## 让其他项目直接依赖

开发时可以使用 Gradle Composite Build，不需要上传 CurseForge：

```groovy
// 在其他项目的 settings.gradle 中关联本地 HPTlib 工程。
includeBuild('E:/模组/发cf的模组/Tinkers/HPTlib/HPTlib-1201-forge') {
    dependencySubstitution {
        substitute module('org.hp.hptlib:hptlib') using project(':')
    }
}

// 在其他项目的 build.gradle 中声明 HPTlib 坐标。
dependencies {
    // Composite Build 提供的是已映射的工程产物，这里不要再套 fg.deobf。
    implementation 'org.hp.hptlib:hptlib:1.0.0'
}
```

也可以先执行 `publishToMavenLocal`，再让其他项目加入 `mavenLocal()`，并使用 `fg.deobf('org.hp.hptlib:hptlib:1.0.0')`。Composite Build 适合联调源码；本地 Maven 适合只依赖已构建的 JAR。两种方式都只解决 Gradle 编译依赖；正式整合包仍需要把 HPTlib JAR 放入 `mods`，因为 CurseForge/Maven 不会自动替玩家安装模组。

发布到共享本地 Maven 仓库时，可执行 `publish`，产物会写入当前项目的 `mcmodsrepo` 目录，其他工程将该目录加入 `repositories` 即可。
