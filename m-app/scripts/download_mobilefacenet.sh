#!/bin/bash

# MobileFaceNet Model Download Script
# Downloads NCNN-optimized MobileFaceNet model for face recognition

set -e  # Exit on error

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(dirname "$SCRIPT_DIR")"
ASSETS_DIR="$PROJECT_ROOT/app/src/main/assets"

echo "=================================="
echo "MobileFaceNet Model Downloader"
echo "=================================="
echo ""

# Create assets directory if not exists
mkdir -p "$ASSETS_DIR"

# Model download URLs (from InsightFace/NCNN community)
# Option 1: Try nihui's ncnn-assets repository (most reliable)
NCNN_ASSETS_BASE="https://github.com/nihui/ncnn-assets/releases/download"
MODEL_VERSION="20210525"

# Option 2: Direct links (if available)
# These URLs may need to be updated based on actual availability
PARAM_URL="$NCNN_ASSETS_BASE/$MODEL_VERSION/mobilefacenet-opt.param"
BIN_URL="$NCNN_ASSETS_BASE/$MODEL_VERSION/mobilefacenet-opt.bin"

# Alternative: Google Drive links (if GitHub fails)
# PARAM_GDRIVE_ID="YOUR_GDRIVE_ID"
# BIN_GDRIVE_ID="YOUR_GDRIVE_ID"

echo "📥 Downloading MobileFaceNet model..."
echo "Target directory: $ASSETS_DIR"
echo ""

# Function to download with curl or wget
download_file() {
    local url=$1
    local output=$2
    local filename=$(basename "$output")
    
    echo "Downloading $filename..."
    
    if command -v curl &> /dev/null; then
        curl -L -o "$output" "$url" --progress-bar
    elif command -v wget &> /dev/null; then
        wget -O "$output" "$url" --show-progress
    else
        echo "❌ Error: Neither curl nor wget is installed"
        echo "Please install curl or wget first:"
        echo "  brew install curl"
        return 1
    fi
    
    if [ -f "$output" ]; then
        local size=$(ls -lh "$output" | awk '{print $5}')
        echo "✅ Downloaded $filename (Size: $size)"
        return 0
    else
        echo "❌ Failed to download $filename"
        return 1
    fi
}

# Try downloading from GitHub releases
echo "Attempting to download from GitHub (nihui/ncnn-assets)..."
echo ""

PARAM_FILE="$ASSETS_DIR/mobilefacenet-opt.param"
BIN_FILE="$ASSETS_DIR/mobilefacenet-opt.bin"

# Download param file
if ! download_file "$PARAM_URL" "$PARAM_FILE"; then
    echo ""
    echo "⚠️  GitHub download failed. This might be because:"
    echo "  1. The release doesn't exist yet"
    echo "  2. The URL has changed"
    echo "  3. Network connectivity issues"
    echo ""
    echo "📌 Manual Download Instructions:"
    echo "=================================="
    echo ""
    echo "Option 1: From InsightFace Official"
    echo "-----------------------------------"
    echo "1. Visit: https://github.com/deepinsight/insightface"
    echo "2. Navigate to: model_zoo/recognition/mobilefacenet"
    echo "3. Download the ONNX model"
    echo "4. Convert to NCNN format using:"
    echo "   onnx2ncnn mobilefacenet.onnx mobilefacenet.param mobilefacenet.bin"
    echo ""
    echo "Option 2: Use Pre-converted Model"
    echo "-----------------------------------"
    echo "1. Search for 'mobilefacenet ncnn' on GitHub"
    echo "2. Look for repositories with pre-converted models"
    echo "3. Download .param and .bin files"
    echo ""
    echo "Option 3: Use Alternative Model"
    echo "-----------------------------------"
    echo "You can use ArcFace or other face recognition models:"
    echo "- arcface_resnet18"
    echo "- arcface_mobilefacenet"
    echo ""
    echo "📁 Place the files at:"
    echo "   $ASSETS_DIR/mobilefacenet-opt.param"
    echo "   $ASSETS_DIR/mobilefacenet-opt.bin"
    echo ""
    exit 1
fi

# Download bin file
if ! download_file "$BIN_URL" "$BIN_FILE"; then
    echo "❌ Failed to download model binary file"
    rm -f "$PARAM_FILE"  # Clean up partial download
    exit 1
fi

echo ""
echo "=================================="
echo "✅ Model Download Complete!"
echo "=================================="
echo ""
echo "Downloaded files:"
echo "  📄 $PARAM_FILE"
echo "  📦 $BIN_FILE"
echo ""

# Verify file sizes
PARAM_SIZE=$(stat -f%z "$PARAM_FILE" 2>/dev/null || stat -c%s "$PARAM_FILE" 2>/dev/null)
BIN_SIZE=$(stat -f%z "$BIN_FILE" 2>/dev/null || stat -c%s "$BIN_FILE" 2>/dev/null)

if [ "$PARAM_SIZE" -lt 1000 ] || [ "$BIN_SIZE" -lt 100000 ]; then
    echo "⚠️  Warning: Downloaded files seem too small"
    echo "   param: $PARAM_SIZE bytes"
    echo "   bin: $BIN_SIZE bytes"
    echo ""
    echo "Please verify the files are correct."
else
    echo "✅ File sizes look reasonable:"
    echo "   param: $PARAM_SIZE bytes"
    echo "   bin: $BIN_SIZE bytes"
fi

echo ""
echo "🎯 Next steps:"
echo "1. Verify the model files in Android Studio"
echo "2. Build and run the app"
echo "3. Check logcat for 'MobileFaceNet initialized successfully'"
echo ""
