# 供应商代码（请勿手改）

- 上游：https://github.com/LegadoTeam/legado
- tag：`3.26101819`
- commit：`a7d37f1707a70a459ad708f4fe79341d1e42d716`
- 文件数：58
- 由 `scripts/vendor-upstream.py` 生成，手改会在下次 vendor 时丢失。

## 取用的目录

- `app/src/main/java/io/legado/app/model/analyzeRule`
- `app/src/main/java/io/legado/app/model/webBook`
- `app/src/main/java/io/legado/app/model/jsSource`
- `modules/rhino/src/main/java`

## 没取但需要的

- `third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.4/` 的 jar：Maven Central 没有这个版本，需单独 vendor（见 docs/ENGINE.md）。
- `constant` / `help` / `utils` / `data.entities` 等包由 `vendor/glue` 提供，不要重复取。
