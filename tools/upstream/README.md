# 上游项目工具脚本（归档）

本目录存放自上游项目 **ZeroRecorder** 继承的辅助脚本，仅作历史追溯与参考。

| 文件 | 原用途 | 当前状态 |
|---|---|---|
| manage.ps1 / manage.sh | 上游的构建/运行封装脚本 | 已不适用（改用 gradle wrapper） |
| clean.py / clean.sh | 清理构建缓存 | 已被 gradlew clean 取代 |
| run_detekt.sh | 静态检查 | 已不适用 |
| tree.ps1 | 生成目录树 | 可选工具 |

> ⚠️ 这些脚本引用的是上游包名 com.zero.recorder，直接运行可能不生效。
> 本项目请使用 README.md 中说明的构建方式。
