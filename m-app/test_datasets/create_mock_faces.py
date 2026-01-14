#!/usr/bin/env python3
"""快速创建模拟人脸测试数据集"""

from PIL import Image, ImageDraw
import random
from pathlib import Path

print("正在创建模拟测试数据集...")

# 创建目录
mock_dir = Path('./mock_faces')
mock_dir.mkdir(exist_ok=True)

# 模拟学生名单（与应用中的默认学生匹配）
students = [
    "张三", "李四", "王五", "赵六", "钱七",
    "孙八", "周九", "吴十"
]

total_photos = 0

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
        draw.ellipse([50, 50, 200, 200], fill=(255, 224, 189))
        # 眼睛
        draw.ellipse([80, 100, 100, 120], fill='black')
        draw.ellipse([150, 100, 170, 120], fill='black')
        # 鼻子
        draw.line([125, 130, 125, 160], fill='brown', width=2)
        # 嘴巴
        draw.arc([90, 160, 160, 180], 0, 180, fill='red', width=2)
        
        # 添加文字标签
        try:
            draw.text((10, 10), f"{student}_{i+1}", fill='blue')
        except:
            pass
        
        # 保存图片
        img.save(student_dir / f'{student}_{i+1}.jpg')
        total_photos += 1
    
    print(f"  ✓ {student}: {num_photos} 张照片")

print(f"\n✅ 模拟数据集创建成功!")
print(f"包含 {len(students)} 个人，共 {total_photos} 张照片")
print(f"数据保存在: {mock_dir.absolute()}")

# 创建说明文件
readme = mock_dir / 'README.txt'
readme.write_text(f"""
模拟人脸测试数据集
==================

这是一个用于测试的模拟人脸数据集，包含 {len(students)} 个人：

""" + "\n".join([f"- {s}" for s in students]) + f"""

总共 {total_photos} 张照片。

注意：这些是简化的模拟图像，仅用于测试应用功能。
      实际使用时请使用真实的人脸数据集。

使用方法:
1. 将照片导入手机或在另一设备上显示
2. 在应用中测试人脸检测功能
3. 测试手机相机识别模式

创建时间: {Path(__file__).stat().st_mtime}
""")

print("\n📱 下一步:")
print("1. 将 mock_faces 目录中的照片传输到手机")
print("2. 在应用中测试人脸录入和识别功能")
print("3. 或者打印照片，用手机相机模式测试")
