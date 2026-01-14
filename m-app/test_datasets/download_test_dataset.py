#!/usr/bin/env python3
"""
下载人脸识别测试数据集

支持多种数据集：
1. LFW (Labeled Faces in the Wild) - 通过 scikit-learn
2. 从 Kaggle 下载小型数据集
3. 创建模拟测试数据
"""

import os
import sys
import urllib.request
import tarfile
import zipfile
from pathlib import Path

def download_lfw_sklearn():
    """使用 scikit-learn 下载 LFW 数据集"""
    try:
        from sklearn.datasets import fetch_lfw_people
        print("正在使用 scikit-learn 下载 LFW 数据集...")
        print("这可能需要几分钟，数据集大小约 200MB")
        
        # 下载至少有 20 张照片的人（适合测试）
        lfw_people = fetch_lfw_people(
            min_faces_per_person=20,
            resize=1.0,  # 原始大小
            data_home='./lfw_sklearn'
        )
        
        print(f"\n✅ 下载成功!")
        print(f"总共 {len(lfw_people.images)} 张图片")
        print(f"包含 {len(lfw_people.target_names)} 个人:")
        for name in lfw_people.target_names[:10]:
            count = sum(lfw_people.target == list(lfw_people.target_names).index(name))
            print(f"  - {name}: {count} 张照片")
        if len(lfw_people.target_names) > 10:
            print(f"  ... 还有 {len(lfw_people.target_names) - 10} 个人")
        
        print(f"\n数据保存在: {os.path.abspath('./lfw_sklearn')}")
        return True
        
    except ImportError:
        print("❌ 未安装 scikit-learn")
        print("请运行: pip install scikit-learn")
        return False
    except Exception as e:
        print(f"❌ 下载失败: {e}")
        return False

def download_from_kaggle():
    """从 Kaggle 下载数据集 (需要 Kaggle API)"""
    try:
        import kaggle
        print("正在从 Kaggle 下载数据集...")
        print("确保已配置 Kaggle API: ~/.kaggle/kaggle.json")
        
        # 下载 LFW 数据集
        kaggle.api.dataset_download_files(
            'jessicali9530/lfw-dataset',
            path='./lfw_kaggle',
            unzip=True
        )
        
        print(f"\n✅ 下载成功!")
        print(f"数据保存在: {os.path.abspath('./lfw_kaggle')}")
        return True
        
    except ImportError:
        print("❌ 未安装 kaggle")
        print("请运行: pip install kaggle")
        return False
    except Exception as e:
        print(f"❌ 下载失败: {e}")
        return False

def create_mock_dataset():
    """创建一个小型模拟数据集用于测试"""
    print("正在创建模拟测试数据集...")
    
    try:
        from PIL import Image, ImageDraw, ImageFont
        import random
        
        # 创建目录
        mock_dir = Path('./mock_faces')
        mock_dir.mkdir(exist_ok=True)
        
        # 模拟学生名单（与应用中的默认学生匹配）
        students = [
            "张三", "李四", "王五", "赵六", "钱七",
            "孙八", "周九", "吴十"
        ]
        
        # 为每个学生生成 3-5 张不同的模拟照片
        for student in students:
            student_dir = mock_dir / student
            student_dir.mkdir(exist_ok=True)
            
            num_photos = random.randint(3, 5)
            for i in range(num_photos):
                # 创建一个简单的人脸图像
                img = Image.new('RGB', (250, 250), 
                               color=(random.randint(200, 255), 
                                     random.randint(200, 255), 
                                     random.randint(200, 255)))
                draw = ImageDraw.Draw(img)
                
                # 绘制简单的脸部轮廓
                # 脸型
                draw.ellipse([50, 50, 200, 200], 
                            fill=(255, 224, 189))
                # 眼睛
                draw.ellipse([80, 100, 100, 120], fill='black')
                draw.ellipse([150, 100, 170, 120], fill='black')
                # 鼻子
                draw.line([125, 130, 125, 160], fill='brown', width=2)
                # 嘴巴
                draw.arc([90, 160, 160, 180], 0, 180, fill='red', width=2)
                
                # 添加文字标签
                try:
                    draw.text((10, 10), f"{student}_{i+1}", 
                             fill='blue')
                except:
                    pass
                
                # 保存图片
                img.save(student_dir / f'{student}_{i+1}.jpg')
            
            print(f"  ✓ {student}: {num_photos} 张照片")
        
        print(f"\n✅ 模拟数据集创建成功!")
        print(f"包含 {len(students)} 个人，共 {sum(len(list((mock_dir / s).glob('*.jpg'))) for s in students)} 张照片")
        print(f"数据保存在: {os.path.abspath('./mock_faces')}")
        
        # 创建说明文件
        readme = mock_dir / 'README.txt'
        readme.write_text(f"""
模拟人脸测试数据集
==================

这是一个用于测试的模拟人脸数据集，包含 {len(students)} 个人：

""" + "\n".join([f"- {s}" for s in students]) + """

每个人有 3-5 张不同的照片。

注意：这些是简化的模拟图像，仅用于测试应用功能。
      实际使用时请使用真实的人脸数据集。
""")
        
        return True
        
    except ImportError:
        print("❌ 未安装 Pillow")
        print("请运行: pip install Pillow")
        return False
    except Exception as e:
        print(f"❌ 创建失败: {e}")
        return False

def main():
    print("=" * 60)
    print("人脸识别测试数据集下载工具")
    print("=" * 60)
    print("\n请选择下载方式:")
    print("1. 使用 scikit-learn 下载 LFW 数据集 (推荐, ~200MB)")
    print("2. 从 Kaggle 下载 LFW 数据集 (需要 Kaggle API)")
    print("3. 创建小型模拟数据集 (快速测试用)")
    print("4. 退出")
    
    choice = input("\n请输入选项 (1-4): ").strip()
    
    if choice == '1':
        success = download_lfw_sklearn()
    elif choice == '2':
        success = download_from_kaggle()
    elif choice == '3':
        success = create_mock_dataset()
    elif choice == '4':
        print("退出")
        return
    else:
        print("❌ 无效选项")
        return
    
    if success:
        print("\n" + "=" * 60)
        print("下载/创建完成!")
        print("=" * 60)
        print("\n使用说明:")
        print("1. 将数据集照片复制到应用的测试目录")
        print("2. 在应用中使用这些照片进行人脸检测测试")
        print("3. 测试人脸录入和识别功能")

if __name__ == '__main__':
    main()
