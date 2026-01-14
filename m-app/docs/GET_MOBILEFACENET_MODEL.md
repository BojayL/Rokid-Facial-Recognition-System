# MobileFaceNet 模型获取指南（实用版）

根据搜索结果，这里提供几个**实际可行**的获取方案：

## 🎯 推荐方案 1: 从现有 NCNN 项目获取

### Option A: GRAYKEY/mobilefacenet_ncnn
这个仓库包含现成的 NCNN 模型文件。

```bash
cd /Users/bojay.l/Developer/Rokid/FReg/m-app

# 克隆仓库
git clone https://github.com/GRAYKEY/mobilefacenet_ncnn.git temp_model

# 复制模型文件
cp temp_model/models/mobilefacenet.param app/src/main/assets/mobilefacenet-opt.param
cp temp_model/models/mobilefacenet.bin app/src/main/assets/mobilefacenet-opt.bin

# 清理临时文件
rm -rf temp_model

echo "✅ 模型文件已复制到 assets 目录"
```

### Option B: MirrorYuChen/ncnn_example
这个仓库也包含多个 NCNN 模型。

从以下链接下载模型：
- 百度云: https://pan.baidu.com/s/xxxxx (code: w48b)
- Google Drive: (查看仓库 README)

然后手动复制到 `app/src/main/assets/`

## 🎯 推荐方案 2: 从 PyTorch 模型转换

### 步骤 1: 获取 PyTorch 模型

```bash
# 克隆 foamliu 的 MobileFaceNet 实现（包含预训练模型）
git clone https://github.com/foamliu/MobileFaceNet.git
cd MobileFaceNet

# 下载预训练权重
mkdir -p weights
cd weights
wget https://github.com/foamliu/MobileFaceNet/releases/download/v1.0/mobilefacenet.pt
cd ..
```

### 步骤 2: 转换为 ONNX

创建 `convert_to_onnx.py`:

```python
from mobilefacenet import MobileFaceNet
import torch

# 加载模型
model = MobileFaceNet()
model.load_state_dict(torch.load('weights/mobilefacenet.pt', map_location='cpu'))
model.eval()

# 导出为 ONNX
dummy_input = torch.randn(1, 3, 112, 112)
torch.onnx.export(
    model,
    dummy_input,
    'weights/mobilefacenet.onnx',
    input_names=['data'],
    output_names=['fc1'],
    opset_version=11
)
print("✅ ONNX 模型已生成")
```

运行：
```bash
python convert_to_onnx.py
```

### 步骤 3: 转换为 NCNN

需要 NCNN 的 onnx2ncnn 工具：

```bash
# 下载 NCNN 工具
# macOS
brew install ncnn

# 或从源码编译
git clone https://github.com/Tencent/ncnn.git
cd ncnn
mkdir build && cd build
cmake -DCMAKE_BUILD_TYPE=Release ..
make -j4
# onnx2ncnn 工具在 build/tools/onnx/onnx2ncnn

# 转换
/path/to/onnx2ncnn weights/mobilefacenet.onnx mobilefacenet.param mobilefacenet.bin

# 优化模型
/path/to/ncnnoptimize mobilefacenet.param mobilefacenet.bin mobilefacenet-opt.param mobilefacenet-opt.bin 65536
```

### 步骤 4: 复制到项目

```bash
cp mobilefacenet-opt.param /Users/bojay.l/Developer/Rokid/FReg/m-app/app/src/main/assets/
cp mobilefacenet-opt.bin /Users/bojay.l/Developer/Rokid/FReg/m-app/app/src/main/assets/
```

## 🎯 推荐方案 3: 使用 InsightFace 官方模型

### 从 InsightFace Model Zoo 获取

```bash
# 克隆 InsightFace
git clone https://github.com/deepinsight/insightface.git
cd insightface/recognition/arcface_torch

# 查看可用模型
# 访问: https://github.com/deepinsight/insightface/tree/master/model_zoo

# 下载 MobileFaceNet 权重（需要根据实际 release 调整）
# 然后按照方案 2 的步骤转换为 NCNN
```

## ⚡ 快速方案: 直接从已验证仓库下载

我为你创建了一个快速下载脚本：

```bash
#!/bin/bash

cd /Users/bojay.l/Developer/Rokid/FReg/m-app

# 创建临时目录
TEMP_DIR=$(mktemp -d)
cd "$TEMP_DIR"

# 尝试从 GRAYKEY 仓库获取
echo "📥 从 GRAYKEY/mobilefacenet_ncnn 获取模型..."
git clone --depth 1 https://github.com/GRAYKEY/mobilefacenet_ncnn.git

if [ -f "mobilefacenet_ncnn/models/mobilefacenet.param" ]; then
    ASSETS_DIR="/Users/bojay.l/Developer/Rokid/FReg/m-app/app/src/main/assets"
    mkdir -p "$ASSETS_DIR"
    
    cp mobilefacenet_ncnn/models/mobilefacenet.param "$ASSETS_DIR/mobilefacenet-opt.param"
    cp mobilefacenet_ncnn/models/mobilefacenet.bin "$ASSETS_DIR/mobilefacenet-opt.bin"
    
    echo "✅ 模型文件已复制！"
    ls -lh "$ASSETS_DIR"/mobilefacenet-opt.*
else
    echo "❌ 未找到模型文件，请尝试其他方案"
fi

# 清理
cd /Users/bojay.l/Developer/Rokid/FReg/m-app
rm -rf "$TEMP_DIR"
```

保存为 `scripts/quick_get_model.sh` 并运行：

```bash
chmod +x scripts/quick_get_model.sh
./scripts/quick_get_model.sh
```

## 🔍 验证模型

模型下载后，验证文件：

```bash
cd /Users/bojay.l/Developer/Rokid/FReg/m-app/app/src/main/assets

# 检查文件大小
ls -lh mobilefacenet-opt.*

# 期望结果:
# mobilefacenet-opt.param: ~10-50 KB
# mobilefacenet-opt.bin: ~3-5 MB
```

## 📝 注意事项

1. **模型命名**: 确保文件名为 `mobilefacenet-opt.param` 和 `mobilefacenet-opt.bin`

2. **输入/输出层名称**: 不同来源的模型可能使用不同的层名称
   - 输入层: 可能是 `data`, `input`, `input.1`
   - 输出层: 可能是 `fc1`, `embedding`, `output`
   
   如果遇到问题，检查 `.param` 文件的第一行和最后一行

3. **模型验证**: 在 Android 应用中测试：
   ```kotlin
   val success = FaceRecognizer.init(context)
   Log.d("TEST", "Init: $success")
   ```

## 🆘 如果以上方法都失败

作为最后手段，可以使用：

1. **简化的面部识别**: 使用 MLKit Face Detection + 手动匹配
2. **在线 API**: 使用云端人脸识别服务
3. **替代模型**: 使用其他轻量级模型如 FaceNet-Mobile

## 📚 相关资源

- [MobileFaceNet 论文](https://arxiv.org/abs/1804.07573)
- [NCNN 官方文档](https://github.com/Tencent/ncnn)
- [InsightFace Model Zoo](https://github.com/deepinsight/insightface/tree/master/model_zoo)
- [ONNX 转换指南](https://github.com/Tencent/ncnn/wiki/how-to-use-and-FAQ#onnx)
