#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import shutil

def clean_backups(root_dir):
    count = 0
    for dirpath, _, filenames in os.walk(root_dir):
        for file in filenames:
            if file.endswith('.bak'):
                file_path = os.path.join(dirpath, file)
                try:
                    os.remove(file_path)
                    count += 1
                except Exception as e:
                    print(f"[-] 删除失败 {file_path}: {e}")
    return count

def clean_directories(dirs_to_remove):
    count = 0
    for d in dirs_to_remove:
        if os.path.exists(d) and os.path.isdir(d):
            try:
                shutil.rmtree(d)
                count += 1
            except Exception as e:
                print(f"[-] 清理目录失败 {d}: {e}")
    return count

if __name__ == "__main__":
    print("="*45)
    print("🧹 开始执行 ZeroRecorder 深度清理")
    print("="*45)

    bak_count = clean_backups('.')
    
    # 增加了 .gradle 缓存清理，解决本地编译环境导致的幽灵报错
    cache_dirs = ['build', 'app/build', '.gradle-tmp', 'logs', 'captures', '.gradle']
    dir_count = clean_directories(cache_dirs)

    print("="*45)
    print(f"✨ 清理完成! 共移除 {bak_count} 个备份文件, {dir_count} 个缓存目录。")
    print("="*45)
