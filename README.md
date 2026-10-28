# legado-tidy-tool

把一堆积压的 Legado 书源，跑成一份干净、能用的书源库。

四个阶段：**去重 → 校验 → 整理 → 导出**。本地运行，不联网上传任何东西（除了校验时访问书源自己的站点）。

> 状态：工程骨架 + 核心库（去重/整理/导出/流水线）+ 单元测试已就绪；界面与引擎接入进行中。

## 为什么再写一个

已有的批量校验工具（例如 `legado-tool-kotlin`）证明了「用 Legado 自己的解析引擎在桌面上跑」这条路可行，但它的价值押在「报告页逐段差分」上，交互流程反而是够用的。

本项目把它压成一条直线：**池子 → 校验 → 报告 → 导出**。校验默认只做搜索（最便宜且信号最强），不做逐段正文差分。

## 范围

**做**

- 批量导入书源（文件 / 粘贴 / 目录），支持 JSON 数组、单对象、`{sources:[...]}`
- S1 去重：精确重复 → 指纹重复 → 相似重复，三层合并，**不自动删源**，只给建议
- S2 校验：用上游 Legado 引擎真实跑一次搜索（可选加跑目录），区分 `超时` / `网络错误` / `HTTP 4xx` / `规则失效` / `引擎不支持`
- S3 整理：按源自带的 `bookSourceGroup` 归组，没有就按类型命名；组内按「可用 → 超时 → 错误」排序
- S4 导出：本地 JSON 文件为主（保留未知字段、写回 `customOrder`）；可选开启直链通道供同网络导入
- Windows 单文件便携包（无控制台窗口、可拷走运行）

**不做**

- 手机端（校验的工作量级不适合在手机上跑）
- 正文逐段差分审核
- 固定口令的网盘上传
- 局域网 HTTP 服务默认常驻

## 构建

需要 **JDK 21** 与 **Gradle 8.14.4**（暂未内置 wrapper，见 `docs/ENGINE.md`）。

```bash
# 1. 取上游引擎源码（一次性，之后提交进仓库）
python scripts/vendor-upstream.py

# 2. 编译与测试
gradle :core:test
```

网络在代理后面时：`set HTTPS_PROXY=http://127.0.0.1:7890`（`scripts/vendor-upstream.py` 会读）。

## 目录

```
core/     纯 JVM，无引擎依赖：书源模型、归一化、三层去重、整理、导出、流水线编排
engine/   唯一与 Legado 引擎打交道的地方（vendored 源码 + 平台补全层）
desktop/  Compose Desktop 界面（进行中；当前是可跑通链路的命令行入口）
vendor/   scripts/vendor-upstream.py 生成的上游源码，勿手改
docs/     决策记录与引擎说明
```

## 许可

GPL-3.0。校验能力来自 [LegadoTeam/legado](https://github.com/LegadoTeam/legado)（GPL-3.0），引擎源码以 [供应商代码](vendor/legado/VENDOR.md) 形式随本仓库分发。
