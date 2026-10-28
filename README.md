# 书源整理工具（legado-tidy-tool）

对 Legado（开源阅读）书源做一条流水线：**去重 → 校验 → 整理 → 导出**。

- 平台：Windows 桌面（便携版）
- 技术栈：Kotlin / JVM 21 / Compose Desktop
- 引擎：直接 vendor [LegadoTeam/legado](https://github.com/LegadoTeam/legado) 的解析源码，不复制、不改写
- 许可证：GPL-3.0（上游引擎是 GPL-3.0，见 `docs/DECISIONS.md` D4）

## 模块

```
core/     纯 JVM，无引擎：模型、归一化、去重、整理、导出、流水线编排（可单测）
engine/   JVM，唯一带引擎的模块：vendor 源码 + 平台补全 + 一次探测入口
 desktop/  Compose Desktop 界面（后续）
 vendor/   上游引擎源码（vendored，见 docs/ENGINE.md）
```

## 四阶段

| 阶段 | 做什么 | 会不会删源 |
|---|---|---|
| S1 去重 | 精确重复（URL+名称）、行为指纹重复、相似聚类 | 不删，只分组并给「保留建议」 |
| S2 校验 | 用真实引擎跑一次搜索（默认只到搜索深度），按失败原因分类 | 不删 |
| S3 整理 | 清洗名称、按可用性与名称排序、分类命名 | 不删，只重排 |
| S4 导出 | 本地 JSON 为主；直链为可选通道 | — |

设计原则：**任何阶段都不自动删除用户的书源**，只做分组、标注、排序建议。

## 构建

需要 JDK 21。仓库暂不携带 Gradle wrapper 的二进制 jar，首次构建执行一次：

```bash
gradle wrapper --gradle-version 8.14.4   # 之后用 ./gradlew
gradle build
```

## 状态

项目正在搭建中：`core`（去重/整理/导出、纯逻辑）正在成形，`engine` 待接入，UI 未开始。
