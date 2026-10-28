# 引擎层：怎么把上游 Legado 搬上桌面 JVM

## 为什么不用 submodule

用 submodule 也能固定版本，但**构建时还得联网**。把引擎源码直接 vendor 进仓库后，
任何人 `git clone` 完就能编，不依赖第三方站点是否可达，也不受上游删分支影响。

代价是仓库里多约 1.8MB Kotlin/Java 源码（不算 jar）。可以接受。

## 固定点

| 项 | 值 | 在哪改 |
| --- | --- | --- |
| 上游仓库 | `LegadoTeam/legado` | `scripts/vendor-upstream.py` |
| tag | `3.26101819` | 同上 |
| commit | `a7d37f1707a70a459ad708f4fe79341d1e42d716` | 同上 |
| jsoup | `1.23.2`（不能升） | `gradle/libs.versions.toml` |
| okhttp | `5.x`（不能降到 4.x） | 同上 |
| Rhino | `htmlunit-core-js:5.3.0-legado.4`（上游自己编的） | `scripts/fetch-engine-jar.py` |

### 两个“不能升级”的坑

- **jsoup 必须 1.23.2**：上游的规则解析依赖它的 `text()` 语义，新版改了空白处理，
  会导致正文提取多/少空白，进而让“相同章”判定失准（jhy/jsoup#2017）。
- **okhttp 必须 5.x**：上游代码把 `Response.body` 当非空用，4.x 里它是 nullable，
  编译会飙十几处 `Only safe (?.)` 错误。

## 取哪些目录

只取**解析引擎本身**：

```
app/src/main/java/io/legado/app/model/analyzeRule
app/src/main/java/io/legado/app/model/webBook
app/src/main/java/io/legado/app/model/jsSource
modules/rhino/src/main/java
```

上游其他包（`constant` / `help` / `utils` / `data.entities` …）**不要取**：
`vendor/glue/` 已经提供了同名类，两边都取会 `重复声明`。

## 平台补全层（vendor/glue）

引擎里到处是 `android.*`、`androidx.annotation.*`、Room、Glide。补全层把它们换成能在
桌面 JVM 上跑的桩，并补上 `BaseSource` / `AppConfig` / `appDb` / `JsExtensions` 这些 Legado 自带部分。

这一层来自参考项目 [legado-tool-kotlin](https://gitee.com/lunar-lure/legado-tool-kotlin)（同为 GPL-3.0），
不为别的：那些坑（`android.util.Base64` 失败时的行为、`SDK_INT` 阈值、AES 算法名差异）
已经被人踩过一遍了。

拉取：`python scripts/bootstrap-glue.py`（清单在 `scripts/glue-filelist.txt`，共 135 个文件）。

## 三个已知的静默坑

1. **`.kt` 与 `.java` 要同时进源集**。`modules/rhino` 里有 12 个 `.java`，只加 `kotlin.srcDir`
   会“编译通过、运行期 `NoClassDefFoundError`”。`engine/build.gradle.kts` 里两边都加。
2. **`SDK_INT` 必须是 34**。`RhinoClassShutter` 在 `SDK_INT >= 33` 时才把 `java.nio.file.*`
   关进黑名单；填低了，JS 书源能直接用文件 API，与 App 行为不一致。
3. **jar 缺失是静默降级**。`engine/build.gradle.kts` 用 `if (rhinoJar.exists())` 引 jar：
   没有它也能编译，但跑 JS 书源时会 `NoClassDefFoundError`。所以 CI 里必须先跑 `fetch-engine-jar.py`。

## 升级引擎

1. 改 `scripts/vendor-upstream.py` 里的 `PIN_TAG` / `PIN_COMMIT`。
2. `python scripts/vendor-upstream.py`（会清空重写 `vendor/legado/`）。
3. `python scripts/fetch-engine-jar.py --force` 或删掉 jar 重拉。
4. 对照上游 `app/build.gradle` 逐项检查依赖版本（jsoup/okhttp/hutool/bouncycastle…）。
5. 看 `git diff --stat vendor/legado`：**目录消失 = 源集要改**，`engine/build.gradle.kts` 里的
   `engineSourceDirs` 跟着改。
6. 跑 `gradle :core:test` 与一次真实书源校验，确认失败分类没变。

## 坑：必须用上游的 `GSON`，不能自建 `GsonBuilder`

`io.legado.app.utils.GSON` 上挂了几个适配器，其中 `StringJsonDeserializer` 会把「非原始值」的字段整体 `toString()` 成字面量。真实书源里 `ruleContent.replaceRegex` 有时是**数组**（老版本导出），自建 Gson 会抛：

```
java.lang.IllegalStateException: Expected STRING but was BEGIN_ARRAY at path $.ruleContent.replaceRegex
```

后果是**本来能用的源被整条判成解析失败**。2407 个真实源里这类占相当比例，属于「一上来全红」的典型原因。

规则：任何 `fromJson(..., BookSource::class.java)` 都必须走 `io.legado.app.utils.GSON`。

## 坑：不要因为出现 `@js:` 就静态判「跑不了」

Rhino 就是用来跑这些脚本的，真实源里九成以上带 `@js:`。静态阶段只拦「再跑也没意义」的（没有 searchUrl、`webView`/`webJs` 这类需要真浏览器内核的）；其余交给运行时暴露，由失败分类归因。
