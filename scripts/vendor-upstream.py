#!/usr/bin/env python3
"""把上游 Legado 引擎源码（固定到某个 tag）取到 vendor/legado/。

为什么是「供应商代码」而不是 submodule：
  - 进仓库后任何人 clone 完就能 build，不依赖第三方站点可达；
  - 上游删分支、改目录结构都不影响我们；
  - 升级是显式动作（改 PIN_TAG → 重跑 → 看 diff），不是默默漂移。

用法：
    python scripts/vendor-upstream.py           # 拉取/更新 vendor/legado
    python scripts/vendor-upstream.py --check   # 只校验已生成的是否完整
"""
from __future__ import annotations

import io
import os
import shutil
import sys
import tarfile
import traceback
import urllib.request
from pathlib import Path

UPSTREAM = "LegadoTeam/legado"
PIN_TAG = "3.26101819"
PIN_COMMIT = "a7d37f1707a70a459ad708f4fe79341d1e42d716"

# 只取「解析引擎」本身。另外几个包（constant / help / utils / data.entities…）
# 不要取：平台补全层（vendor/glue）已经提供了同名类，两处都取会重复声明。
SUBSET = [
    "app/src/main/java/io/legado/app/model/analyzeRule",
    "app/src/main/java/io/legado/app/model/webBook",
    "app/src/main/java/io/legado/app/model/jsSource",
    "modules/rhino/src/main/java",
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
        log("vendor 不完整，缺少：\n  " + "\n  ".join(missing))
        return 1
    if PIN_COMMIT not in marker.read_text(encoding="utf-8"):
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
            if not member.isfile() or not member.name.startswith(prefix):
                continue
            rel = member.name[len(prefix):]
            keep = any(rel == d or rel.startswith(d + "/") for d in SUBSET)
            if not keep and rel != "LICENSE":  # GPL 要求随分发附带
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


def write_marker(files: int) -> None:
    (VENDOR / "VENDOR.md").write_text(
        "# 供应商代码（请勿手改）\n\n"
        f"- 上游：https://github.com/{UPSTREAM}\n"
        f"- tag：`{PIN_TAG}`\n"
        f"- commit：`{PIN_COMMIT}`\n"
        f"- 文件数：{files}\n"
        "- 由 `scripts/vendor-upstream.py` 生成，手改会在下次 vendor 时丢失。\n\n"
        "## 取用的目录\n\n"
        + "\n".join(f"- `{d}`" for d in SUBSET)
        + "\n\n## 没取但需要的\n\n"
        "- `third_party/maven/org/htmlunit/htmlunit-core-js/5.3.0-legado.4/` 的 jar："
        "Maven Central 没有这个版本，需单独 vendor（见 docs/ENGINE.md）。\n"
        "- `constant` / `help` / `utils` / `data.entities` 等包由 `vendor/glue` 提供，不要重复取。\n",
        encoding="utf-8",
    )


def main() -> int:
    if "--check" in sys.argv:
        return check_existing()
    data = download()
    written = write_tree(data)
    if written == 0:
        log("一个文件都没写出来 —— 压缩包顶层目录名与 PIN_COMMIT 对不上？")
        return 1
    write_marker(written)
    return check_existing()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SystemExit:
        raise
    except BaseException:  # CI 里看不到交互式报错，完整打印一层
        traceback.print_exc()
        raise SystemExit(1)
