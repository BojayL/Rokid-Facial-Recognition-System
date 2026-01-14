package com.sustech.bojayL

import android.os.Bundle
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
import com.sustech.bojayL.data.model.*
import com.sustech.bojayL.data.repository.StudentRepository
import com.sustech.bojayL.ui.screens.classroom.ClassroomScreen
import com.sustech.bojayL.ui.screens.classroom.PhoneCameraRecognitionScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sustech.bojayL.ui.screens.classroom.ClassroomViewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.isGranted
import com.sustech.bojayL.ui.screens.device.DeviceScreen
import com.sustech.bojayL.ui.screens.profile.ProfileScreen
import com.sustech.bojayL.ui.screens.students.EnrollmentResult
import com.sustech.bojayL.ui.screens.students.FaceEnrollmentScreen
import com.sustech.bojayL.ui.screens.students.StudentDetailScreen
import com.sustech.bojayL.ui.screens.students.StudentsScreen
import com.sustech.bojayL.ui.theme.MobileappTheme
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MobileappTheme {
                ARClassroomApp()
            }
        }
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
fun ARClassroomApp() {
    // 当前导航目的地
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.CLASSROOM) }
    
    // 设备连接状态（全局共享）
    var deviceState by remember {
        mutableStateOf(
            DeviceState(
                connectionType = ConnectionType.DISCONNECTED,
                batteryLevel = 0,
                deviceName = null
            )
        )
    }
    
    // 学生数据持久化
    val context = LocalContext.current
    val studentRepository = remember { StudentRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 从 DataStore 读取学生列表
    val studentsList by studentRepository.studentsFlow.collectAsState(initial = StudentRepository.defaultStudents)
    
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
                            deviceState = deviceState.copy(sdkParams = params)
                        },
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
