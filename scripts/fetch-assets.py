#!/usr/bin/env python3
"""下载引擎运行期需要的 JS 资源（目前只有一个：CryptoJS）。

## 为什么需要

Legado 的书源规则里常见 `crypto.*`（AES / MD5 / base64 变体）。引擎侧靠 Rhino
预加载 CryptoJS 提供这些函数：上游从 `app/src/main/assets/scripts/cryptojs.min.js` 读，
桌面端没有 AssetManager，我们这层的补全代码改成从 classpath 读，
所以这个文件必须出现在 `engine/src/main/resources/scripts/` 下。

没有它不会崩——只是凡是用了 `crypto.*` 的源都会失败，并在日志里留一行
`加载CryptoJS失败`。所以它属于「跑之前先补上」的东西。

## 为什么不直接提交进仓库

64KB 的第三方压缩脚本，提交进来会让每次 grep、每次 review 都很难受。
固定提交号取文件，比提交一份无人 review 的货更诚实。

文件从 vendor/legado 同一个 commit 取，保证引擎与资源同源。
"""
from __future__ import annotations

import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "engine" / "src" / "main" / "resources" / "scripts"

# 与 scripts/vendor-upstream.py 的 PIN_COMMIT 保持一致。
PIN_COMMIT = "a7d37f1707a70a459ad708f4fe79341d1e42d716"
BASE = f"https://raw.githubusercontent.com/LegadoTeam/legado/{PIN_COMMIT}/app/src/main/assets/scripts/"

ASSETS = {
    "cryptojs.min.js": "CryptoJS：书源规则里的 crypto.* 全靠它",
}


def download(name: str) -> bytes:
    url = BASE + name
    last: Exception | None = None
    for _ in range(3):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "legado-tidy/0.1"})
            with urllib.request.urlopen(req, timeout=120) as resp:
                body = resp.read()
            if body.lstrip()[:5].lower() == b"<!doc":
                raise RuntimeError("拿到的是 HTML 页面，可能被代理或需要认证")
            if not body:
                raise RuntimeError("内容为空")
            return body
        except Exception as e:  # noqa: BLE001 —— 网络错误种类多，统一重试
            last = e
    raise RuntimeError(f"{name} 下载失败：{last}")


def main() -> int:
    DEST.mkdir(parents=True, exist_ok=True)
    failed = 0
    for name, why in ASSETS.items():
        target = DEST / name
        if target.is_file() and target.stat().st_size > 1024:
            print(f"跳过 {name}（已存在，{target.stat().st_size} 字节）")
            continue
        try:
            body = download(name)
        except Exception as e:  # noqa: BLE001
            print(f"失败 {name}：{e}", file=sys.stderr)
            failed += 1
            continue
        target.write_bytes(body)
        print(f"下载 {name}（{len(body)} 字节）—— {why}")

    if failed:
        print(
            "\n提示：这些资源在 vendor/legado 同一 commit 的 app/src/main/assets/scripts/ 下，"
            "也可以手工拷进 engine/src/main/resources/scripts/。",
            file=sys.stderr,
        )
        return 1

    (DEST / "SOURCE.md").write_text(
        "# 运行期 JS 资源来源\n\n"
        f"取自 LegadoTeam/legado `{PIN_COMMIT[:11]}` 的 `app/src/main/assets/scripts/`（GPL-3.0）。\n\n"
        "由 `python scripts/fetch-assets.py` 拉取，不要手改。\n",
        encoding="utf-8",
    )
    print("完成。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
