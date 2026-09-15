# ========================================================
# 现代化高颜值目录树生成脚本 (跨平台兼容版)
# ========================================================

param (
    [string]$Path = "."
)

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$rootPath = (Resolve-Path $Path).Path
$rootName = Split-Path $rootPath -Leaf

Write-Host "`n📁 $rootName/" -ForegroundColor Cyan

# 1. 精准排除逻辑：使用正则表达式，只匹配整个文件夹名称，杜绝“误杀”同名文件
# 注意：原本带点的文件夹（如 .git）需要加反斜杠转义，否则正则会把点当作通配符
$excludeList = @('\.gradle', 'build', '\.git', '\.cxx', '\.idea', 'captures', 'node_modules')
$excludePattern = "(^|[\\/])(" + ($excludeList -join '|') + ")([\\/]|$)"

# 2. 获取文件并过滤
$files = Get-ChildItem -Path $rootPath -Recurse -File | Where-Object {
    $relative = $_.FullName.Substring($rootPath.Length + 1)
    # 如果路径中不包含排除的文件夹，则保留
    $relative -notmatch $excludePattern
}

$printedDirs = @{}

# 3. 遍历并绘制高颜值目录树
foreach ($file in $files) {
    $relPath = $file.FullName.Substring($rootPath.Length + 1)
    
    # 使用正则分割路径，完美兼容 Windows(\) 和 Linux/Mac(/)
    $parts = $relPath -split '[\\/]'
    
    for ($i = 0; $i -lt $parts.Length - 1; $i++) {
        $dirKey = ($parts[0..$i] -join '\')
        if (-not $printedDirs.ContainsKey($dirKey)) {
            $indent = "│   " * $i
            Write-Host "$indent├── 📁 $($parts[$i])/" -ForegroundColor Blue
            $printedDirs[$dirKey] = $true
        }
    }
    
    $fileIndent = "│   " * ($parts.Length - 1)
    # 根据文件扩展名可以自己拓展颜色，这里统一用灰色
    Write-Host "$fileIndent├── 📄 $($parts[-1])" -ForegroundColor Gray
}

Write-Host ""