#!/usr/bin/env python3
"""拉取「平台补全层」（vendor/glue）。

## 为什么要单独一个脚本

`vendor/legado` 是上游 Legado 的引擎源码。但引擎要跑在桌面 JVM 上还需要一层**平台补全**：
把 `android.*`、`androidx.annotation.*`、`com.bumptech.glide.*`、Room/SharedPreferences
之类换成能在 JVM 上跑的桩，再补上 Legado 自己那些 `BaseSource` / `AppConfig` /
`appDb` / `JsExtensions`。

这一层在 [legado-tool-kotlin](https://gitee.com/lunar-lure/legado-tool-kotlin) 里已经做过一遍
（作者把 2407 个真实源的失败原因逐条打进去了）。我们**直接取用**，不自研——
重写一遍就是重新踩一遍那些坑，而且会引入与上游不一致的偏差。

## 当前状态：文件已在仓库里

`vendor/glue/` 已经提交进仓库，正常情况下**不需要跑这个脚本**。
它保留两个用途：

1. `--check`：CI 里校验补全层完整（不联网，秒级）。
2. 重建：把 vendor/glue 误删了、或者要跟参考项目重新对齐时用。

## 为什么不 clone

gitee 的 `archive` 端点要登录（匿名访问返回登录页 HTML），但 **raw 端点匿名可用**，
所以按文件清单逐个取。清单在 `scripts/glue-filelist.txt`，共 135 个文件。

## 许可证

上游参考项目与本项目同为 GPL-3.0，取用其补全层代码需在 `NOTICE` / README 里注明来源。
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

HTTP_HELPER_REL = "io/legado/app/help/http/HttpHelper.kt"
PROBE_PROXY_MARKER = "ProbeProxyContext.currentProxy()"


def load_manifest() -> list[str]:
    if not MANIFEST.is_file():
        print(f"找不到清单：{MANIFEST}", file=sys.stderr)
        raise SystemExit(2)
    rels = [line.strip() for line in MANIFEST.read_text("utf-8").splitlines() if line.strip()]
    if not rels:
        print("清单是空的", file=sys.stderr)
        raise SystemExit(2)
    return rels


def check(rels: list[str]) -> int:
    """只查本地，不联网。CI 用这个。"""
    missing = [rel for rel in rels if not (DEST / rel).is_file()]
    present = len(rels) - len(missing)
    if missing:
        print(f"补全层缺文件：{len(missing)}/{len(rels)}", file=sys.stderr)
        for rel in missing[:20]:
            print(f"  缺：{rel}", file=sys.stderr)
        if len(missing) > 20:
            print(f"  ……还有 {len(missing) - 20} 个", file=sys.stderr)
        return 1
    helper = DEST / HTTP_HELPER_REL
    if not helper.is_file() or PROBE_PROXY_MARKER not in helper.read_text("utf-8"):
        print(
            "[glue] 缺少校验级代理上下文补丁；请重新运行 bootstrap-glue.py。",
            file=sys.stderr,
        )
        return 1
    upstream_smoke = (
        "fun getProxyClient(proxy: String? = null): OkHttpClient {\\n"
        "    if (proxy.isNullOrBlank()) {\\n"
        "        return okHttpClient\\n"
        "    }\\n"
        "    val proxyConfig = parseProxyConfig(proxy)"
    ).encode("utf-8")
    try:
        patched_smoke = apply_project_overlays(HTTP_HELPER_REL, upstream_smoke).decode("utf-8")
    except Exception as e:  # noqa: BLE001 — CI also verifies the rebuild patch itself
        print(f"[glue] 代理补丁自检失败：{e}", file=sys.stderr)
        return 1
    if PROBE_PROXY_MARKER not in patched_smoke:
        print("[glue] 代理补丁自检未生成预期代码。", file=sys.stderr)
        return 1
    print(f"[glue] OK：{present}/{len(rels)} 个文件；校验级代理补丁及重建补丁均通过")
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
            if head.startswith(b"<!doctype") or head.startswith(b"<html"):
                last = "返回的是 HTML 页面（文件不存在或需要登录）"
                continue
            if not body:
                last = "内容为空"
                continue
            return rel, body, ""
        except Exception as e:  # noqa: BLE001 —— 网络错误种类多，一律重试
            last = f"{type(e).__name__}: {e}"
    return rel, None, last


def apply_project_overlays(rel: str, body: bytes) -> bytes:
    """Apply small headless-only adaptations after fetching the reference glue source."""
    if rel != HTTP_HELPER_REL:
        return body

    text = body.decode("utf-8")
    if PROBE_PROXY_MARKER in text:
        return body

    upstream = (
        "fun getProxyClient(proxy: String? = null): OkHttpClient {\n"
        "    if (proxy.isNullOrBlank()) {\n"
        "        return okHttpClient\n"
        "    }\n"
        "    val proxyConfig = parseProxyConfig(proxy)"
    )
    replacement = (
        "fun getProxyClient(proxy: String? = null): OkHttpClient {\n"
        "    // A source-level header.proxy wins; otherwise use the current validation's context.\n"
        "    val effectiveProxy = proxy?.takeIf { it.isNotBlank() }\n"
        "        ?: ProbeProxyContext.currentProxy()\n"
        "    if (effectiveProxy.isNullOrBlank()) {\n"
        "        return okHttpClient\n"
        "    }\n"
        "    val proxyConfig = parseProxyConfig(effectiveProxy)"
    )
    if text.count(upstream) != 1:
        raise ValueError(
            "无法安全应用 HttpHelper 代理补丁：上游 getProxyClient 代码与预期不一致。"
        )
    return text.replace(upstream, replacement, 1).encode("utf-8")


def rebuild(rels: list[str]) -> int:
    print(f"补全层：{len(rels)} 个文件 → {DEST}")
    failed: list[tuple[str, str]] = []
    written = 0

    with concurrent.futures.ThreadPoolExecutor(max_workers=WORKERS) as pool:
        for rel, body, err in pool.map(fetch, rels):
            if body is None:
                failed.append((rel, err))
                continue
            try:
                body = apply_project_overlays(rel, body)
            except Exception as e:  # noqa: BLE001 — fail closed rather than losing a required patch
                failed.append((rel, f"项目补丁失败：{e}"))
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
            "\n提示：把 gitee 上的同一份文件手动放进 vendor/glue 对应路径也能补齐。"
            "清单见 scripts/glue-filelist.txt。",
            file=sys.stderr,
        )
        return 1

    (DEST / "SOURCE.md").write_text(
        "# 平台补全层来源\n\n"
        "本目录下的文件取自 [legado-tool-kotlin](https://gitee.com/lunar-lure/legado-tool-kotlin)\n"
        f"（GPL-3.0），共 {written} 个文件，清单见 `scripts/glue-filelist.txt`。\n\n"
        "它们把 Android / Room / Glide 等平台依赖替换成可在桌面 JVM 上运行的桩，\n"
        "并补上 Legado 引擎自身依赖的 `BaseSource`、`AppConfig`、`appDb`、`JsExtensions`。\n\n"
        "**不要手改**：`python scripts/bootstrap-glue.py` 会整体覆盖；校验级代理上下文补丁由脚本自动应用。\n"
        "其他行为改动请放在 `engine/src/main/kotlin/` 下我们自己的代码。\n",
        encoding="utf-8",
    )
    print("完成。")
    return 0


def main(argv: list[str]) -> int:
    rels = load_manifest()
    if "--check" in argv:
        return check(rels)
    return rebuild(rels)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
