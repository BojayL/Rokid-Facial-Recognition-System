package com.sustech.bojayL

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.data.repository.StudentRepository
import com.sustech.bojayL.rokid.*
import com.sustech.bojayL.ui.screens.classroom.ClassroomScreen
import com.sustech.bojayL.ui.screens.classroom.PhoneCameraRecognitionScreen
import com.sustech.bojayL.ui.screens.classroom.ClassroomViewModel
import com.sustech.bojayL.ui.screens.device.DeviceScreen
import com.sustech.bojayL.ui.screens.profile.ProfileScreen
import com.sustech.bojayL.ui.screens.students.EnrollmentResult
import com.sustech.bojayL.ui.screens.students.FaceEnrollmentScreen
import com.sustech.bojayL.ui.screens.students.StudentDetailScreen
import com.sustech.bojayL.ui.screens.students.StudentsScreen
import com.sustech.bojayL.ui.theme.MobileappTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

/**
 * AR 智慧课堂伴侣 - 主 Activity
 * 
 * 根据 PRD 信息架构，应用分为四个底部导航 Tab：
 * 1. 课堂 (Classroom)：核心主页，展示实时识别流、当前课程状态
 * 2. 学生 (Students)：班级花名册、学生档案查询、人脸库管理
 * 3. 设备 (Device)：眼镜连接状态、二维码配对、参数调节
 * 4. 我的 (Profile)：账户信息、通用设置、数据同步
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    // Rokid 服务
    private var rokidService: RokidService? = null
    
    // 识别处理器
    private var recognitionProcessor: RecognitionProcessor? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // 初始化 Rokid 服务
        initRokidService()
        
        setContent {
            MobileappTheme {
                ARClassroomApp(
                    rokidService = rokidService,
                    recognitionProcessor = recognitionProcessor
                )
            }
        }
    }
    
    /**
     * 初始化 Rokid 服务
     */
    private fun initRokidService() {
        Log.d(TAG, "Initializing Rokid service")
        
        rokidService = RokidService(this).apply {
            initialize(R.raw.d176a07fb29c4203ac13f37554bc43bc)
        }
        
        recognitionProcessor = RecognitionProcessor(this)
        
        // 设置识别请求回调
        rokidService?.setOnRecognitionRequest { request ->
            handleRecognitionRequest(request)
        }
    }
    
    /**
     * 处理识别请求
     */
    private fun handleRecognitionRequest(request: RecognitionRequest) {
        Log.d(TAG, "Handling recognition request")
        
        kotlinx.coroutines.MainScope().launch {
            val result = recognitionProcessor?.process(request.bitmap)
            
            if (result != null) {
                rokidService?.sendRecognitionResult(
                    isKnown = result.isKnown,
                    student = result.student,
                    confidence = result.confidence
                )
            }
            
            // 释放 Bitmap
            if (!request.bitmap.isRecycled) {
                request.bitmap.recycle()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        rokidService?.release()
        recognitionProcessor?.release()
        Log.d(TAG, "MainActivity destroyed")
    }
}

/**
 * 应用导航目的地
 */
enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    CLASSROOM("课堂", Icons.Default.School),
    STUDENTS("学生", Icons.Default.People),
    DEVICE("设备", Icons.Default.Devices),
    PROFILE("我的", Icons.Default.Person)
}

/**
 * AR 智慧课堂伴侣应用主界面
 */
@OptIn(ExperimentalPermissionsApi::class)
@PreviewScreenSizes
@Composable
fun ARClassroomApp(
    rokidService: RokidService? = null,
    recognitionProcessor: RecognitionProcessor? = null
) {
    // 当前导航目的地
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CLASSROOM) }
    
    // 学生数据持久化
    val context = LocalContext.current
    val studentRepository = remember { StudentRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // Rokid 连接状态
    val rokidConnectionState by rokidService?.connectionState?.collectAsState() 
        ?: remember { mutableStateOf(RokidConnectionState()) }
    val scannedDevices by rokidService?.scannedDevices?.collectAsState()
        ?: remember { mutableStateOf(emptyList<ScannedDevice>()) }
    val isScanning by rokidService?.isScanning?.collectAsState()
        ?: remember { mutableStateOf(false) }
    val isPaired by rokidService?.isPaired?.collectAsState()
        ?: remember { mutableStateOf(false) }
    
    // 眼镜画面预览（调试用）
    val latestGlassesFrame by rokidService?.latestGlassesFrame?.collectAsState()
        ?: remember { mutableStateOf<GlassesFrameData?>(null) }
    
    // 设备连接状态（结合 Rokid 状态）
    var deviceState by remember { mutableStateOf(DeviceState()) }
    
    // 同步 Rokid 连接状态到 deviceState
    LaunchedEffect(rokidConnectionState) {
        deviceState = deviceState.copy(
            connectionType = when (rokidConnectionState.status) {
                ConnectionStatus.CONNECTED -> ConnectionType.BLUETOOTH
                else -> ConnectionType.DISCONNECTED
            },
            batteryLevel = rokidConnectionState.batteryLevel,
            deviceName = if (rokidConnectionState.status == ConnectionStatus.CONNECTED) 
                "Rokid AR" else null
        )
    }
    
    // 蓝牙权限
    val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    } else {
        listOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }
    
    val bluetoothPermissionsState = rememberMultiplePermissionsState(bluetoothPermissions)
    
    // 从 DataStore 读取学生列表
    val studentsList by studentRepository.studentsFlow.collectAsState(initial = StudentRepository.defaultStudents)
    
    // 更新识别处理器的学生列表
    LaunchedEffect(studentsList) {
        recognitionProcessor?.updateStudents(studentsList)
    }
    
    // 当前选中的学生（用于详情页）
    var selectedStudent by remember { mutableStateOf<Student?>(null) }
    
    // 是否显示人脸录入页面
    var showFaceEnrollment by remember { mutableStateOf(false) }
    
    // ClassroomViewModel
    val classroomViewModel: ClassroomViewModel = viewModel()
    val classroomUiState by classroomViewModel.uiState.collectAsState()
    
    // 相机权限
    val cameraPermissionState = rememberPermissionState(
        android.Manifest.permission.CAMERA
    )
    
    // 初始化识别处理器
    LaunchedEffect(Unit) {
        recognitionProcessor?.initialize()
    }
    
    // 监听眼镜端识别结果，并传递给 ClassroomViewModel
    LaunchedEffect(rokidService) {
        rokidService?.recognitionResults?.collect { glassResult ->
            // 将眼镜端结果转换为 RecognitionResult 并添加到 ViewModel
            val result = com.sustech.bojayL.data.model.RecognitionResult(
                id = glassResult.id,
                studentId = glassResult.student?.id,
                student = glassResult.student,
                sessionId = glassResult.sessionId.ifEmpty { classroomViewModel.uiState.value.session.sessionId },
                status = if (glassResult.isKnown) 
                    com.sustech.bojayL.data.model.RecognitionStatus.SUCCESS 
                else 
                    com.sustech.bojayL.data.model.RecognitionStatus.UNKNOWN,
                confidence = glassResult.confidence,
                timestamp = glassResult.timestamp,
                attendanceStatus = com.sustech.bojayL.data.model.AttendanceStatus.PRESENT
            )
            classroomViewModel.onRecognitionResult(result)
        }
    }
    
    // 如果显示手机相机识别页面
    if (classroomUiState.isPhoneCameraActive) {
        PhoneCameraRecognitionScreen(
            students = studentsList,
            onStudentRecognized = { student ->
                classroomViewModel.recognizeStudent(student)
            },
            onBackClick = {
                classroomViewModel.closePhoneCamera()
            },
            viewModel = classroomViewModel
        )
        return
    }
    
    // 如果显示人脸录入页面
    if (showFaceEnrollment && selectedStudent != null) {
        FaceEnrollmentScreen(
            student = selectedStudent!!,
            onBackClick = { showFaceEnrollment = false },
            onEnrollmentComplete = { uri ->
                // 兑容旧版回调
                if (uri != null && selectedStudent != null) {
                    val updated = selectedStudent!!.copy(
                        avatarUrl = uri.toString(),
                        isEnrolled = true
                    )
                    selectedStudent = updated
                    coroutineScope.launch {
                        studentRepository.updateStudent(updated, studentsList)
                    }
                }
                showFaceEnrollment = false
            },
            onEnrollmentCompleteWithFeature = { result ->
                // 新版回调：保存头像 + 特征向量
                if (selectedStudent != null) {
                    val updated = selectedStudent!!.copy(
                        avatarUrl = result.imageUri.toString(),
                        isEnrolled = true,
                        faceFeature = result.faceFeature
                    )
                    selectedStudent = updated
                    coroutineScope.launch {
                        studentRepository.updateStudent(updated, studentsList)
                    }
                }
                showFaceEnrollment = false
            }
        )
        return
    }
    
    // 如果选中了学生，显示详情页
    if (selectedStudent != null) {
        StudentDetailScreen(
            student = selectedStudent!!,
            onBackClick = { selectedStudent = null },
            onEnrollFace = { showFaceEnrollment = true }
        )
        return
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            destination.icon,
                            contentDescription = destination.label
                        )
                    },
                    label = { androidx.compose.material3.Text(destination.label) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            when (currentDestination) {
                AppDestinations.CLASSROOM -> {
                    ClassroomScreen(
                        deviceState = deviceState,
                        viewModel = classroomViewModel,
                        students = studentsList,
                        cameraPermissionState = cameraPermissionState,
                        onDeviceClick = { currentDestination = AppDestinations.DEVICE },
                        onStudentClick = { student -> selectedStudent = student },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                
                AppDestinations.STUDENTS -> {
                    StudentsScreen(
                        students = studentsList,
                        onStudentClick = { student -> selectedStudent = student },
                        onAddStudent = { student ->
                            coroutineScope.launch {
                                studentRepository.addStudent(student, studentsList)
                            }
                        },
                        onAddStudents = { newStudents ->
                            // 批量添加学生（LFW 导入）
                            coroutineScope.launch {
                                val updatedList = studentsList + newStudents
                                studentRepository.saveStudents(updatedList)
                            }
                        },
                        onDeleteStudent = { student ->
                            coroutineScope.launch {
                                studentRepository.deleteStudent(student.id, studentsList)
                            }
                        },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                
                AppDestinations.DEVICE -> {
                    DeviceScreen(
                        deviceState = deviceState,
                        onUpdateParams = { params ->
                            // 更新本地状态
                            deviceState = deviceState.copy(sdkParams = params)
                            // 发送配置到眼镜
                            rokidService?.sendConfig(
                                threshold = params.recognitionThreshold.value,
                                intervalMs = params.captureInterval.intervalMs,
                                brightness = params.displayBrightness,
                                showReticle = params.showReticle
                            )
                        },
                        rokidConnectionState = rokidConnectionState,
                        scannedDevices = scannedDevices,
                        isScanning = isScanning,
                        onStartScan = {
                            // 检查权限
                            if (bluetoothPermissionsState.allPermissionsGranted) {
                                rokidService?.startScan()
                            } else {
                                bluetoothPermissionsState.launchMultiplePermissionRequest()
                            }
                        },
                        onStopScan = { rokidService?.stopScan() },
                        onConnectDevice = { device -> rokidService?.connect(device) },
                        onDisconnect = { rokidService?.disconnect() },
                        isPaired = isPaired,
                        // 眼镜画面预览（调试用）
                        latestGlassesFrame = latestGlassesFrame?.bitmap,
                        latestFrameTimestamp = latestGlassesFrame?.timestamp ?: 0L,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
                
                AppDestinations.PROFILE -> {
                    ProfileScreen(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
fun ARClassroomAppPreview() {
    MobileappTheme {
        ARClassroomApp()
    }
}
