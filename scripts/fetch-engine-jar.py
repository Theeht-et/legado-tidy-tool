#!/usr/bin/env python3
"""取上游 vendored 的 htmlunit-core-js jar（Rhino 分支）。

## 为什么不进仓库

`org.htmlunit:htmlunit-core-js:5.3.0-legado.4` 是 Legado 自己编译的版本，Maven Central
没有，上游把它直接放在仓库的 `third_party/maven/` 里。这个 jar 约 1.9MB，是**唯一的二进制物**
—— 把它也提交进仓库会让 `git clone` 变慢，而且升级时 diff 没法看。

所以策略是：**源码进仓库，二进制用脚本拉**。CI 和本地都跑同一个脚本。

## 没有它会怎样

`engine/build.gradle.kts` 里对它的引用是 `if (rhinoJar.exists())` —— 没有 jar 也能编译过
（走 JS 的书源在**运行期**才会 `NoClassDefFoundError`）。这是故意的：宁可让纯规则书源先跑起来，
也不要把「忘了拉 jar」变成一个看起来像代码 bug 的东西。
"""
from __future__ import annotations

import os
import sys
import urllib.request
from pathlib import Path

UPSTREAM = "LegadoTeam/legado"
PIN_COMMIT = "a7d37f1707a70a459ad708f4fe79341d1e42d716"
REL = "third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.4/htmlunit-core-js-5.3.0-legado.4.jar"

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "vendor" / "legado" / REL
URL = f"https://raw.githubusercontent.com/{UPSTREAM}/{PIN_COMMIT}/{REL}"

MIN_SIZE = 1_500_000  # 小于这个数说明拿到的是错误页或 LFS 指针


def main() -> int:
    if DEST.is_file() and DEST.stat().st_size >= MIN_SIZE:
        print(f"[jar] 已存在 {DEST.stat().st_size / 1024 / 1024:.1f} MB，跳过")
        return 0

    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    handlers = []
    if proxy:
        print(f"[jar] 使用代理 {proxy}")
        handlers.append(urllib.request.ProxyHandler({"http": proxy, "https": proxy}))
    opener = urllib.request.build_opener(*handlers)
    opener.addheaders = [("User-Agent", "legado-tidy-tool/fetch-engine-jar")]

    print(f"[jar] 下载 {URL}")
    with opener.open(URL, timeout=300) as resp:
        data = resp.read()

    # 不是 zip 就不是我们要的东西（GitHub 对不存在的文件会返 404 页）
    if len(data) < MIN_SIZE or not data.startswith(b"PK"):
        print(f"[jar] 内容不像 jar（{len(data)} 字节，头 {data[:4]!r}）", file=sys.stderr)
        return 1

    DEST.parent.mkdir(parents=True, exist_ok=True)
    DEST.write_bytes(data)
    print(f"[jar] 写入 {DEST}（{len(data) / 1024 / 1024:.1f} MB）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
