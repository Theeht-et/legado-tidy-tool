#!/usr/bin/env python3
"""把上游 Legado 引擎源码（固定到某个 tag）取到 vendor/legado/。

为什么是「供应商代码」而不是 submodule：
  - vendor 进仓库后，任何人 clone 完就能 build，不依赖 gitee/github 可达；
  - 上游删分支、改目录结构都不影响我们；
  - 升级是一个显式动作（改 PIN_TAG → 重跑本脚本 → 看 diff），不是默默漂移。

用法：
    python scripts/vendor-upstream.py           # 拉取/更新 vendor/legado
    python scripts/vendor-upstream.py --check   # 只校验已生成的 vendor 是否完整

CI 在 .github/workflows/vendor-upstream.yml 里跑同样的命令并把结果提交回仓库。
"""

from __future__ import annotations

import io
import os
import shutil
import sys
import tarfile
import urllib.request
from pathlib import Path

# ---------------------------------------------------------------------------
# 固定点。改这里 = 升级引擎，必须同时跑一遍 docs/ENGINE.md 里的升级检查清单。
# ---------------------------------------------------------------------------
UPSTREAM = "LegadoTeam/legado"
PIN_TAG = "3.26101819"
PIN_COMMIT = "a7d37f1707a70a459ad708f4fe79341d1e42d716"

# 只取「解析引擎」需要的那几块，其余（UI、播放器、下载…）一概不要。
SUBSET = [
    "app/src/main/java/io/legado/app/model/analyzeRule",
    "app/src/main/java/io/legado/app/model/webBook",
    "app/src/main/java/io/legado/app/model/jsSource",
    "modules/rhino/src/main/java",
    # AnalyzeUrl / AnalyzeRule 直接引用的几个包，不取会编译不过。
    "app/src/main/java/io/legado/app/data/entities",
    "app/src/main/java/io/legado/app/constant",
    "app/src/main/java/io/legado/app/help/http",
    "app/src/main/java/io/legado/app/help",
    "app/src/main/java/io/legado/app/utils",
    "app/src/main/java/io/legado/app/coroutine",
    "app/src/main/java/io/legado/app/exception",
    "app/src/main/java/io/legado/app/api",
    "app/src/main/java/io/legado/app/model/localBook",
]

ROOT = Path(__file__).resolve().parent.parent
VENDOR = ROOT / "vendor" / "legado"
TARBALL_URL = f"https://codeload.github.com/{UPSTREAM}/tar.gz/{PIN_COMMIT}"


def log(msg: str) -> None:
    print(f"[vendor] {msg}", flush=True)


def check_existing() -> int:
    marker = VENDOR / "VENDOR.md"
    if not marker.exists():
        log(f"缺少 {marker}，尚未 vendor")
        return 1
    missing = [d for d in SUBSET if not (VENDOR / d).exists()]
    if missing:
        log("vendor 不完整，缺少：" + "\n  ".join(missing))
        return 1
    text = marker.read_text(encoding="utf-8")
    if PIN_COMMIT not in text:
        log(f"VENDOR.md 里的 commit 与 PIN_COMMIT={PIN_COMMIT} 不一致")
        return 1
    log(f"OK：{PIN_TAG} / {PIN_COMMIT[:12]}")
    return 0


def download() -> bytes:
    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    if proxy:
        log(f"使用代理 {proxy}")
        opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({"http": proxy, "https": proxy})
        )
    else:
        opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "legado-tidy-tool/vendor-upstream")]
    log(f"下载 {TARBALL_URL}")
    with opener.open(TARBALL_URL, timeout=600) as resp:
        data = resp.read()
    log(f"下载完成 {len(data) / 1024 / 1024:.1f} MB")
    return data


def write_tree(data: bytes) -> int:
    prefix = f"legado-{PIN_COMMIT}/"
    written = 0
    if VENDOR.exists():
        log(f"清空旧内容 {VENDOR}")
        shutil.rmtree(VENDOR)
    with tarfile.open(fileobj=io.BytesIO(data), mode="r:gz") as tar:
        for member in tar:
            if not member.isfile():
                continue
            if not member.name.startswith(prefix):
                continue
            rel = member.name[len(prefix):]
            keep = any(rel == d or rel.startswith(d + "/") for d in SUBSET)
            # 上游 LICENSE 单独拷一份，GPL 要求随分发附带。
            if not keep and rel != "LICENSE":
                continue
            src = tar.extractfile(member)
            if src is None:
                continue
            dest = VENDOR / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(src.read())
            written += 1
    log(f"写入 {written} 个文件 -> {VENDOR}")
    return written


def write_marker() -> None:
    file_count = sum(1 for _ in VENDOR.rglob("*"))
    (VENDOR / "VENDOR.md").write_text(
        "# 供应商代码（请勿手改）\n\n"
        f"- 上游：https://github.com/{UPSTREAM}\n"
        f"- tag：`{PIN_TAG}`\n"
        f"- commit：`{PIN_COMMIT}`\n"
        "- 由 `scripts/vendor-upstream.py` 生成，手改会在下次 vendor 时丢失。\n\n"
        "## 取用的目录\n\n"
        + "\n".join(f"- `{d}`" for d in SUBSET)
        + "\n\n## 没取但需要的\n\n"
        "- `third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.4/` 的 jar："
        "Maven Central 没有这个版本，需单独 vendor（见 docs/ENGINE.md）。\n"
        "- 上游 `app/build.gradle` 里的依赖版本：升级时逐项对照。\n",
        encoding="utf-8",
    )
    log(f"VENDOR.md 已写入（共 {file_count} 个条目）")


def main() -> int:
    if "--check" in sys.argv:
        return check_existing()
    data = download()
    write_tree(data)
    write_marker()
    return check_existing()


if __name__ == "__main__":
    raise SystemExit(main())
