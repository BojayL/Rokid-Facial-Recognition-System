# 修复重复签到和占位人员问题

## 问题描述

1. **重复签到问题**: 开始课堂后,同一学生会不断被重复签到,签到人数持续累加
2. **占位人员问题**: 系统中存在"钱七"等用于测试的占位人员

## 问题原因

### 1. 重复签到的原因

**原因一**: `simulateRecognitionResults()` 方法在 `startSession()` 时被调用,会自动模拟5个学生的识别过程

**原因二**: `onRecognitionResult()` 方法缺少去重逻辑,没有检查学生是否已经签到过,导致同一学生可以被多次添加到签到列表中

**原因三**: 签到计数使用 `updatedResults.size + 1` 而不是使用去重后的学生ID集合大小

### 2. 占位人员的原因

`StudentRepository.kt` 中的 `defaultStudents` 包含了8个占位学生数据,用于开发测试,但应该在实际使用时清空

## 解决方案

### 1. 添加签到去重逻辑

在 `ClassroomViewModel.kt` 的 `onRecognitionResult()` 方法中添加去重检查:

```kotlin
fun onRecognitionResult(result: RecognitionResult) {
    val studentId = result.studentId ?: return
    
    // 去重：检查该学生是否已经签到过
    if (_uiState.value.recognizedStudentIds.contains(studentId)) {
        Log.d(TAG, "Student $studentId already recognized in this session, skipping")
        return
    }
    
    _uiState.update { state ->
        // 更新状态
        state.copy(
            recognitionResults = listOf(newResult) + updatedResults,
            recognizedStudentIds = state.recognizedStudentIds + studentId,  // 记录已识别学生
            session = state.session.copy(
                recognizedCount = (state.recognizedStudentIds.size + 1).coerceAtMost(state.session.totalStudents)
            )
        )
    }
}
```

### 2. 禁用自动模拟签到

在 `startSession()` 中移除 `simulateRecognitionResults()` 调用:

```kotlin
fun startSession() {
    val sessionId = UUID.randomUUID().toString()
    
    _uiState.update { state ->
        state.copy(
            session = state.session.copy(
                sessionId = sessionId,
                status = SessionStatus.ACTIVE,
                startTime = System.currentTimeMillis(),
                recognizedCount = 0,
                attendanceStats = AttendanceStats()
            ),
            recognitionResults = emptyList(),
            recognizedStudentIds = emptySet()  // 清空已识别学生集合
        )
    }
    
    // 清空去重集合
    recentlyRecognizedStudents.clear()
    recognitionTimestamps.clear()
    
    // 不再自动调用 simulateRecognitionResults()
}
```

将 `simulateRecognitionResults()` 标记为 `@Deprecated` 并禁用:

```kotlin
@Deprecated("Use real recognition from glasses or phone camera")
private fun simulateRecognitionResults() {
    // 此方法已禁用，避免自动模拟签到
    Log.d(TAG, "simulateRecognitionResults is deprecated and disabled")
}
```

### 3. 清空占位人员

在 `StudentRepository.kt` 中清空默认学生列表:

```kotlin
companion object {
    // 默认学生列表（移除占位人员）
    val defaultStudents = emptyList<Student>()
}
```

## 修改文件清单

1. **m-app/app/src/main/java/com/sustech/bojayL/ui/screens/classroom/ClassroomViewModel.kt**
   - ✅ `onRecognitionResult()`: 添加学生ID去重检查
   - ✅ `startSession()`: 清空 `recognizedStudentIds` 集合,移除自动模拟调用
   - ✅ `simulateRecognitionResults()`: 标记为废弃并禁用

2. **m-app/app/src/main/java/com/sustech/bojayL/data/repository/StudentRepository.kt**
   - ✅ `defaultStudents`: 改为空列表

## 测试验证

### 验证去重逻辑

1. 启动课堂会话
2. 通过眼镜或手机相机识别同一学生多次
3. 确认该学生只出现一次在签到列表中
4. 签到人数不会重复累加

### 验证占位人员移除

1. 清除应用数据: `adb shell pm clear com.sustech.bojayL`
2. 重新启动应用
3. 进入学生管理页面,确认没有默认的占位学生

### 日志验证

```bash
adb logcat | grep ClassroomViewModel
```

应该能看到类似日志:
```
ClassroomViewModel: Session started: <session-id>
ClassroomViewModel: Recognition result added: studentId=s1, total recognized=1
ClassroomViewModel: Student s1 already recognized in this session, skipping
```

## 工作流程

### 眼镜模式
1. 用户点击"开始上课"
2. 系统创建课堂会话,清空签到记录
3. 眼镜识别学生并发送结果到手机
4. 手机端 `onRecognitionResult()` 接收结果并去重
5. 每个学生只会被签到一次

### 手机相机模式
1. 用户点击"手机相机"切换模式
2. 用户点击"开始识别"
3. 相机实时识别人脸并匹配学生
4. `recognizeStudent()` 方法自带30秒冷却机制
5. `updateDetectedFaces()` 自动调用 `recognizeStudent()`
6. 双重去重保护(冷却机制 + session级别去重)

## 相关数据结构

### ClassroomUiState
```kotlin
data class ClassroomUiState(
    val recognizedStudentIds: Set<String> = emptySet()  // 本会话已识别的学生ID集合
)
```

### ClassroomViewModel
```kotlin
private val recentlyRecognizedStudents = mutableSetOf<String>()  // 冷却中的学生ID
private val recognitionTimestamps = mutableMapOf<String, Long>()  // 识别时间戳
private val recognitionCooldownMs = 30_000L  // 30秒冷却时间
```

## 注意事项

1. **Session级别去重**: `recognizedStudentIds` 存储在 `ClassroomUiState` 中,每次开始新课堂会自动清空
2. **冷却机制**: 手机相机模式有30秒冷却时间,防止短时间内重复识别同一学生
3. **双重保护**: Session级别去重 + 冷却机制,确保不会重复签到
4. **实际识别来源**: 现在只接受真实的识别结果(眼镜或手机相机),不再自动模拟

## 未来改进

- [ ] 添加手动重置某个学生签到状态的功能
- [ ] 支持迟到、早退等更细粒度的考勤状态
- [ ] 添加签到历史记录和统计报表
