# SCRFD Face Detection JNI Module
# Based on https://github.com/nihui/ncnn-android-scrfd

cmake_minimum_required(VERSION 3.10)
project(scrfd_jni)

# OpenCV
set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.12.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

# NCNN
set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20250503-android-vulkan/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# Source files
add_library(scrfd_jni SHARED
    scrfd_jni.cpp
    scrfd.cpp
    mobilefacenet.cpp
    face_recognition_jni.cpp
)

# Include directories
target_include_directories(scrfd_jni PRIVATE
    ${CMAKE_SOURCE_DIR}
)

# Link libraries
target_link_libraries(scrfd_jni
    ncnn
    ${OpenCV_LIBS}
    android
    log
    jnigraphics
)
