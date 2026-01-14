#!/usr/bin/env python3
"""
LFW 数据集导入脚本

将 LFW (Labeled Faces in the Wild) 数据集转换为应用可导入的格式。
由于人脸特征提取需要在 Android 设备上执行，此脚本只准备学生基本信息和照片路径。

使用方法:
1. 运行脚本生成 students.json 和照片目录
2. 使用 adb push 将数据推送到设备
3. 在应用中执行批量导入

示例:
    python scripts/import_lfw_data.py --limit 50  # 只导入前50人
    python scripts/import_lfw_data.py --min-images 3  # 只导入有3张以上照片的人
"""

import argparse
import json
import os
import shutil
from pathlib import Path
from typing import List, Dict

# LFW 数据集路径
LFW_PATH = Path(__file__).parent.parent / "test_datasets/lfw_sklearn/lfw_home/lfw_funneled"
# 输出目录
OUTPUT_DIR = Path(__file__).parent.parent / "test_datasets/lfw_export"


def scan_lfw_dataset(lfw_path: Path, min_images: int = 1) -> List[Dict]:
    """
    扫描 LFW 数据集目录
    
    Args:
        lfw_path: LFW 数据集根目录
        min_images: 最少图片数量（用于过滤只有少量照片的人）
    
    Returns:
        人员信息列表
    """
    persons = []
    
    if not lfw_path.exists():
        print(f"错误: LFW 目录不存在: {lfw_path}")
        return persons
    
    for person_dir in sorted(lfw_path.iterdir()):
        if not person_dir.is_dir():
            continue
        
        # 获取该人的所有照片
        images = sorted([
            f for f in person_dir.iterdir()
            if f.suffix.lower() in ['.jpg', '.jpeg', '.png']
        ])
        
        if len(images) < min_images:
            continue
        
        # 人名（从目录名）
        name = person_dir.name.replace('_', ' ')
        
        persons.append({
            'name': name,
            'dir_name': person_dir.name,
            'images': [img.name for img in images],
            'image_count': len(images)
        })
    
    return persons


def generate_students_json(persons: List[Dict], output_path: Path) -> List[Dict]:
    """
    生成学生 JSON 数据
    
    Args:
        persons: 人员信息列表
        output_path: 输出目录
    
    Returns:
        学生数据列表
    """
    students = []
    
    for i, person in enumerate(persons):
        student_id = f"lfw_{i+1:05d}"
        
        # 使用第一张照片作为主照片
        primary_image = person['images'][0] if person['images'] else None
        
        student = {
            "id": student_id,
            "studentId": f"LFW{i+1:05d}",
            "name": person['name'],
            "className": "LFW测试班",
            "grade": "测试",
            "avatarUrl": None,  # 将在设备上设置
            "photoUrl": None,
            "parentContact": None,
            "faceFeatureId": None,
            "faceFeature": None,  # 将在设备上提取
            "isEnrolled": False,
            "tags": ["LFW测试数据"],
            "academicInfo": None,
            "behaviorRecord": None,
            # 额外信息用于导入
            "_lfw_dir": person['dir_name'],
            "_lfw_images": person['images'],
            "_primary_image": primary_image
        }
        
        students.append(student)
    
    return students


def copy_images(persons: List[Dict], lfw_path: Path, output_path: Path):
    """
    复制图片到输出目录
    
    Args:
        persons: 人员信息列表
        lfw_path: LFW 原始路径
        output_path: 输出目录
    """
    images_dir = output_path / "images"
    images_dir.mkdir(parents=True, exist_ok=True)
    
    for person in persons:
        person_output_dir = images_dir / person['dir_name']
        person_output_dir.mkdir(exist_ok=True)
        
        person_source_dir = lfw_path / person['dir_name']
        
        for image_name in person['images']:
            src = person_source_dir / image_name
            dst = person_output_dir / image_name
            if src.exists():
                shutil.copy2(src, dst)


def main():
    parser = argparse.ArgumentParser(description='LFW 数据集导入脚本')
    parser.add_argument('--limit', type=int, default=0,
                        help='限制导入人数（0 表示全部）')
    parser.add_argument('--min-images', type=int, default=1,
                        help='最少图片数量（过滤条件）')
    parser.add_argument('--output', type=str, default=str(OUTPUT_DIR),
                        help='输出目录')
    parser.add_argument('--no-copy', action='store_true',
                        help='不复制图片，只生成 JSON')
    parser.add_argument('--lfw-path', type=str, default=str(LFW_PATH),
                        help='LFW 数据集路径')
    
    args = parser.parse_args()
    
    lfw_path = Path(args.lfw_path)
    output_path = Path(args.output)
    
    print(f"扫描 LFW 数据集: {lfw_path}")
    persons = scan_lfw_dataset(lfw_path, args.min_images)
    
    if args.limit > 0:
        persons = persons[:args.limit]
    
    print(f"找到 {len(persons)} 人")
    
    if not persons:
        print("没有找到符合条件的数据")
        return
    
    # 创建输出目录
    output_path.mkdir(parents=True, exist_ok=True)
    
    # 生成学生 JSON
    students = generate_students_json(persons, output_path)
    
    # 保存 JSON
    json_path = output_path / "students.json"
    with open(json_path, 'w', encoding='utf-8') as f:
        json.dump(students, f, ensure_ascii=False, indent=2)
    print(f"学生数据已保存: {json_path}")
    
    # 生成导入清单
    manifest = {
        "version": 1,
        "total_persons": len(persons),
        "total_images": sum(p['image_count'] for p in persons),
        "lfw_base_path": str(lfw_path),
        "persons": [
            {
                "name": p['name'],
                "dir": p['dir_name'],
                "images": p['images']
            }
            for p in persons
        ]
    }
    
    manifest_path = output_path / "manifest.json"
    with open(manifest_path, 'w', encoding='utf-8') as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)
    print(f"导入清单已保存: {manifest_path}")
    
    # 复制图片
    if not args.no_copy:
        print("复制图片...")
        copy_images(persons, lfw_path, output_path)
        print(f"图片已复制到: {output_path / 'images'}")
    
    # 打印 adb 命令提示
    print("\n=== 导入到 Android 设备 ===")
    print("1. 连接设备并启用 USB 调试")
    print("2. 运行以下命令推送数据 (使用应用私有目录，无需权限):")
    print(f"   adb push {output_path}/* /sdcard/Android/data/com.sustech.bojayL/files/lfw_data/")
    print("")
    print("注意: 需先创建目录:")
    print("   adb shell mkdir -p /sdcard/Android/data/com.sustech.bojayL/files/lfw_data")
    print("")
    print("3. 在应用中点击右下角橙色导入按钮")


if __name__ == '__main__':
    main()
