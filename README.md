# legado-tidy-tool

面向 [Legado（开源阅读 App）](https://github.com/LegadoTeam/legado) 的书源**去重、校验、整理**工具。

拿一堆书源进来，处理完给你一份**能用的**书源：重复的归到一起，搜索搜不到正文的直接标出来，
名字归一、分组排序，导出一个 JSON 拖回 App 就能用。

## 它做什么

一条流水线四个阶段，顺序固定：

1. **去重**——完全一样、行为规则指纹一样、规则高度相似（默认 Jaccard ≥ 0.92）三层。只**合并展示**，不替你删源。
2. **校验**——用上游 Legado 的解析引擎真去搜一次，把结果分成可用 / 无结果 / 超时 / 网络错误 / 需人处理。
3. **整理**——名称去噪（emoji、营销后缀、括号注释），组内按可用性 → 名称 → URL 排序。
4. **导出**——写成 Legado 书源 JSON。不认识的字段原样保留。

## 为什么用真的引擎

校验一个书源能不能用，唯一可信的办法就是用**和 App 一模一样的解析逻辑**去跑。
所以本项目把上游引擎源码（固定到某个 tag）作为 vendored 代码编进来，不做重写。

代价是上游那套 Android 依赖得在桌面 JVM 上补齐（`vendor/glue/`，与上游 GPL-3.0 兼容）。
收益是：规则解析的每个边界情况都和 App 一致，而不是“在我这里能跑”。

## 快速开始（开发者）

需要 **JDK 21** 与 **Gradle 8.14.4**（仓库未附带 wrapper）。

```bash
python3 scripts/bootstrap-glue.py   # 补齐平台补全层（vendor/glue，135 个文件）
python3 scripts/fetch-engine-jar.py # 拉 htmlunit-core-js jar（唯一的二进制物，不进仓库）

./gradlew :desktop:run             # 或：gradle :desktop:run
```

打出免安装便携目录（自带精简 JRE，双击即用，无黑窗）：

```bash
gradle :desktop:portable
# 产物：desktop/build/portable/legado-tidy/
```

命令行跑一次（不进界面）：

```bash
gradle :desktop:cli -Pargs="--in sources.json --limit 20"
```

## 三种使用方式

界面里处理完的书源，可以：

- **存成文件**（默认）——存到本地，自己拖回 App。最稳。
- **直接订阅链接**（可选）——你提供自己的发布方式（网盘直链、自己站点、Gist raw），
  工具把链接生成二维码，手机扫一下就能在 Legado 里添加。
  注意：二维码容量上限约 2.9KB，装不下一份完整书源；它存的是**链接**，不是你选的本地文件。
  纯本地导出时不会出现二维码。

## 仓库结构

```
core/     纯 JVM 逻辑：书源模型、去重、归一化、整理、导出、流水线
engine/   唯一一个带解析引擎的模块：vendored 上游源码 + 平台补全层
  vendor/legado/  上游 Legado 引擎源码（pin 死，勿手改）
  vendor/glue/    平台补全层（Android/Room/Glide 桩），勿手改
desktop/  Compose Desktop 界面 + CLI + 便携打包
scripts/  vendor 拉取、补全层拉取、jar 拉取
docs/     决策记录与引擎说明
```

## 许可证

**GPL-3.0**。本工具把上游 Legado 的引擎源码编进二进制，属于衍生作品：
分发编译产物时，整个程序均以 GPL-3.0 发布，对应源码即本仓库。

自行使用、不分发则不受约束。

详见 [LICENSE](LICENSE)。
