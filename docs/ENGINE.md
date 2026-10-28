# 引擎 vendor 策略

## 基线

| 项 | 值 |
|---|---|
| 上游仓库 | <https://github.com/LegadoTeam/legado> |
| 基线 tag | `3.26101819` |
| 基线 commit | `a7d37f1707a70a459ad708f4fe79341d1e42d716` |
| vendor 目录 | `vendor/legado/` |
| 许可证 | GPL-3.0（`vendor/legado/LICENSE`） |

> 上游是 **LegadoTeam/legado**，不是 gedoor/legado。

## 只 vendor 引擎需要的子集

```
app/src/main/java/io/legado/app/model/analyzeRule/   # 地址/规则解析
app/src/main/java/io/legado/app/model/webBook/      # 书源→书的流程
app/src/main/java/io/legado/app/model/jsSource/     # 书源里 JS 的作用域
modules/rhino/src/main/java/                       # Legado 的 Rhino fork（.java）
third_party/maven/                                 # vendored htmlunit-core-js
LICENSE                                            # GPL-3.0 原文
```

其余（`io/legado/app/data/**`、`help/**`、Android 平台类）**不 vendor**，由本项目在 `engine/src/main/kotlin` 下按需补全。补全的每个类都必须在 `docs/ENGINE.md` 的「补全清单」里登记，注明它替身的是上游哪个类、做了哪些简化。

## 生成

```bash
python scripts/vendor-upstream.py            # 用 VENDOR_PIN 里的 tag/commit
python scripts/vendor-upstream.py --check    # 只校验，不下载
```

或在 GitHub Actions 里手动触发 `Vendor upstream` 工作流。

## 升级流程（显式动作，不要顺手做）

1. 改 `scripts/vendor-upstream.py` 顶部的 `PIN_TAG` / `PIN_COMMIT`。
2. 重新生成 `vendor/legado/`，提交（commit message 写清旧→新 SHA）。
3. 编译 `:engine`，按报错补齐/删掉补全层里已失效的替身。
4. 跑 `:core` 与 `:engine` 测试。
5. 更新本文件的基线表与「补全清单」。

## 已知坑

- 上游这三个目录里 **既有 .kt 也有 .java**，Gradle 必须同时把目录加进 `kotlin.srcDir` 与 `java.srcDir`，只加一边会「编译通过、运行 NoClassDefFoundError」。
- `third_party/maven/` 里是 vendored 的 `org.htmlunit:htmlunit-core-js`，Maven Central 上没有可替代版本。
- jsoup 必须与上游一致（当前 **1.23.2**），升级会静默改变 `text()` 语义。
- 上游 `app/build.gradle` 的 `versionCode` 用 `git rev-list --count` 计算，浅克隆会退化；本项目不引 app 模块，不受影响，但不要照抄它的构建逻辑。
