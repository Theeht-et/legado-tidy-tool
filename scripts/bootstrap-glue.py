#!/usr/bin/env python3
"""一次性引导脚本：把参考项目的「平台补全层」拉进 vendor/glue/。

背景：上游 Legado 的 `analyzeRule` / `webBook` 不直接依赖 Android SDK，而是依赖一整套
`io.legado.app.*` 类（BookSource、BaseSource、CacheManager、HttpHelper……）。这些类在
上游仓库的 app 模块里，而 app 模块本身是 Android 应用，拿不进桌面 JVM。

参考项目 [legado-tool-kotlin] 已经把这块补全层写出来并跑通了 2400+ 个真实书源。
它们和我们一样是 GPL-3.0，所以这里把这块作为**起点**取下来，之后由本项目维护、
按需重写或裁剪（脱钩后可删掉本脚本与对应 workflow）。

用法：
    python scripts/bootstrap-glue.py           # 拉取并写入 vendor/glue
    python scripts/bootstrap-glue.py --check   # 只校验
"""

from __future__ import annotations

import io
import os
import shutil
import sys
import time
import urllib.request
import zipfile
from pathlib import Path

SOURCE_REPO = "lunar-lure/legado-tool-kotlin"
SOURCE_URL = f"https://gitee.com/{SOURCE_REPO}/repository/archive/master.zip"
SOURCE_LICENSE = "GPL-3.0"

# 这些目录原样拷过来（包名不变：io.legado.app.* / android.* / androidx.* …）
SUBSET = [
    "io/legado/app/",
    "android/",
    "androidx/",
    "com/",
    "splitties/",
]

MARKER = "/headless/src/main/kotlin/"
ROOT = Path(__file__).resolve().parent.parent
GLUE = ROOT / "vendor" / "glue"


def log(msg: str) -> None:
    print(f"[glue] {msg}", flush=True)


def open_url() -> urllib.request.OpenerDirector:
    proxy = os.environ.get("HTTPS_PROXY") or os.environ.get("https_proxy")
    if proxy:
        log(f"使用代理 {proxy}")
        opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({"http": proxy, "https": proxy})
        )
    else:
        opener = urllib.request.build_opener()
    opener.addheaders = [("User-Agent", "legado-tidy-tool/bootstrap-glue")]
    return opener


def fetch(attempts: int = 5) -> bytes:
    opener = open_url()
    for i in range(1, attempts + 1):
        try:
            log(f"下载 {SOURCE_URL}（第 {i} 次）")
            with opener.open(SOURCE_URL, timeout=600) as resp:
                data = resp.read()
            log(f"下载完成 {len(data) / 1024 / 1024:.2f} MB")
            return data
        except Exception as e:  # noqa: BLE001
            log(f"失败：{str(e)[:200]}")
            time.sleep(3)
    raise SystemExit("下载失败")


def write_tree(data: bytes) -> int:
    written = 0
    if GLUE.exists():
        log(f"清空旧内容 {GLUE}")
        shutil.rmtree(GLUE)
    with zipfile.ZipFile(io.BytesIO(data)) as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            idx = info.filename.find(MARKER)
            if idx < 0:
                continue
            rel = info.filename[idx + len(MARKER):]
            if not any(rel.startswith(s) for s in SUBSET):
                continue
            dest = GLUE / rel
            dest.parent.mkdir(parents=True, exist_ok=True)
            dest.write_bytes(zf.read(info))
            written += 1
    log(f"写入 {written} 个文件 -> {GLUE}")
    if written == 0:
        raise SystemExit("未匹配到任何文件，上游归档结构可能变了")
    return written


def write_notice() -> None:
    (GLUE / "NOTICE.md").write_text(
        "# 平台补全层（vendored，请勿手改）\n\n"
        f"来源：https://gitee.com/{SOURCE_REPO}（{SOURCE_LICENSE}）\n"
        "生成脚本：`scripts/bootstrap-glue.py`\n\n"
        "## 为什么有这一层\n\n"
        "上游 `analyzeRule` / `webBook` 依赖 `io.legado.app.*` 的一整套类，"
        "而它们写在上游的 app（Android）模块里。这里把这些类按「桌面 JVM 能跑」的方式重写了一遍，\n"
        "包名保持一致，所以引擎源码零修改就能编译。\n\n"
        "## 与本项目的关系\n\n"
        "这是**起点代码**，不是我们的设计。后续会按需裁剪（例如去掉阅读器、播放器相关部分）、\n"
        "修掉已知缺陷，并与上游解耦。脱钩后删除本文件与 `scripts/bootstrap-glue.py`。\n\n"
        "## 已知需要关注的点\n\n"
        "- `app/help/CacheManager.kt` 的 `flush()` 从来没被调用→磁盘缓存索引不持久。\n"
        "- `app/data/dao/BookChapterDao.kt` 写了一个恒空的章节表，`put()` 无调用者。\n"
        "- `app/utils/ContextExtensions.kt` 等桩类只是为了编译通过，不保证行为等价。\n",
        encoding="utf-8",
    )


def check() -> int:
    marker = GLUE / "NOTICE.md"
    if not marker.exists():
        log(f"缺少 {marker}")
        return 1
    for name in ("io/legado/app/model/SharedJsScope.kt", "android/content/Context.kt"):
        if not (GLUE / name).exists():
            log(f"缺少 {name}")
            return 1
    log("OK")
    return 0


def main() -> int:
    if "--check" in sys.argv:
        return check()
    write_tree(fetch())
    write_notice()
    return check()


if __name__ == "__main__":
    raise SystemExit(main())
