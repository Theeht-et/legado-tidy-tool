#!/usr/bin/env python3
"""拉取「平台补全层」（vendor/glue）。

## 为什么要单独一个脚本

`vendor/legado` 是上游 Legado 的引擎源码，可以直接从 GitHub 拿（见 `vendor-upstream.py`）。
但引擎跑在桌面 JVM 上还需要一层**平台补全**：把 `android.*`、`androidx.annotation.*`、
`com.bumptech.glide.*`、Room/SharedPreferences 之类替换成能在 JVM 上跑的桩，
再补上 Legado 自己那些 `BaseSource` / `AppConfig` / `appDb` / `JsExtensions`。

这一层在 [legado-tool-kotlin](https://gitee.com/lunar-lure/legado-tool-kotlin)
里已经做过一遍（作者把 2407 个真实书的失败原因逐条打进去了）。我们**直接取用**，
不自研——重写一遍就是重新踩一遍那些坑，而且会引入与上游不一致的偏差。

## 它在什么时候跑

**正常情况不跑**：135 个文件已经提交在 `vendor/glue/` 里，构建只需要它们在场。
本脚本只在两种情况用：

- `--check`（CI 每月跑）：只校验文件齐不齐，不联网。
- 新拉一份仓库但 `vendor/glue/` 是空的（比如 clone 时用了稀疏检出），需要重新填。

## 为什么不 clone

gitee 的 `archive` 端点要登录（匿名访问返回的是登录页 HTML），
但 **raw 端点匿名可用**，所以按文件清单逐个取。清单在 `scripts/glue-filelist.txt`，
共 135 个文件——这也让我们能一眼看出到底取了哪些东西。

注意：从 GitHub Actions 的海外 runner 访问 gitee 很慢甚至超时，
所以 **CI 不依赖本脚本联网**，只跑 `--check`。

## 许可证

上游参考项目与本项目同为 GPL-3.0，取用其补全层代码已在 `vendor/glue/SOURCE.md` 注明来源。
"""
from __future__ import annotations

import concurrent.futures
import sys
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "vendor" / "glue"
MANIFEST = ROOT / "scripts" / "glue-filelist.txt"

RAW_BASE = "https://gitee.com/lunar-lure/legado-tool-kotlin/raw/master/headless/src/main/kotlin/"

RETRIES = 3
TIMEOUT = 60
WORKERS = 8


def read_manifest() -> list[str]:
    if not MANIFEST.is_file():
        raise SystemExit(f"找不到清单：{MANIFEST}")
    rels = [line.strip() for line in MANIFEST.read_text("utf-8").splitlines() if line.strip()]
    if not rels:
        raise SystemExit("清单是空的")
    return rels


def check(rels: list[str]) -> int:
    missing = [rel for rel in rels if not (DEST / rel).is_file()]
    if missing:
        print(f"补全层缺 {len(missing)} 个文件（应在 {DEST}）：", file=sys.stderr)
        for rel in missing[:20]:
            print(f"  {rel}", file=sys.stderr)
        if len(missing) > 20:
            print(f"  ……还有 {len(missing) - 20} 个", file=sys.stderr)
        print("\n修复：python3 scripts/bootstrap-glue.py（需要能访问 gitee）", file=sys.stderr)
        return 1
    print(f"补全层完整：{len(rels)} 个文件")
    return 0


def fetch(rel: str) -> tuple[str, bytes | None, str]:
    url = RAW_BASE + urllib.parse.quote(rel)
    last = ""
    for _ in range(RETRIES):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "legado-tidy/0.1"})
            with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
                body = resp.read()
            # gitee 在文件不存在时也可能 200 返回 HTML，所以要按内容判。
            head = body.lstrip()[:15].lower()
            if head.startswith(b"<!doctype") or body.lstrip()[:5].lower() == b"<html":
                last = "返回的是 HTML 页面（文件不存在或需要登录）"
                continue
            if not body:
                last = "内容为空"
                continue
            return rel, body, ""
        except Exception as e:  # noqa: BLE001 —— 网络错误种类多，一律重试
            last = f"{type(e).__name__}: {e}"
    return rel, None, last


def download(rels: list[str]) -> int:
    print(f"补全层：{len(rels)} 个文件 → {DEST}")
    failed: list[tuple[str, str]] = []
    written = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for rel, body, err in pool.map(fetch, rels):
            if body is None:
                failed.append((rel, err))
                continue
            target = DEST / rel
            target.parent.mkdir(parents=True, exist_ok=True)
            # 逐字节写入：这些是源码，任何转码都会破坏中文字符串。
            target.write_bytes(body)
            written += 1

    print(f"写入 {written} 个文件")

    if failed:
        print(f"\n失败 {len(failed)} 个：", file=sys.stderr)
        for rel, err in failed:
            print(f"  {rel}  ← {err}", file=sys.stderr)
        print(
            "\n提示：gitee raw 在海外可能很慢。在国内网络下重跑本脚本即可；"
            "或者把同一份文件手动放进 vendor/glue 对应路径（清单见 scripts/glue-filelist.txt）。",
            file=sys.stderr,
        )
        return 1

    (DEST / "SOURCE.md").write_text(
        "# 平台补全层来源\n\n"
        "本目录下的文件取自 [legado-tool-kotlin](https://gitee.com/lunar-lure/legado-tool-kotlin)\n"
        f"（GPL-3.0），共 {written} 个文件，清单见 `scripts/glue-filelist.txt`。\n\n"
        "它们把 Android / Room / Glide 等平台依赖替换成可在桌面 JVM 上运行的桩，\n"
        "并补上 Legado 引擎自身依赖的 `BaseSource`、`AppConfig`、`appDb`、`JsExtensions`。\n\n"
        "**不要手改**：`python scripts/bootstrap-glue.py` 会整体覆盖。\n"
        "需要改行为请改 `engine/src/main/kotlin/io/github/theeht/legadotidy/engine/` 下的代码。\n",
        encoding="utf-8",
    )
    print("完成。")
    return 0


def main() -> int:
    rels = read_manifest()
    if "--check" in sys.argv[1:]:
        return check(rels)
    return download(rels)


if __name__ == "__main__":
    sys.exit(main())
