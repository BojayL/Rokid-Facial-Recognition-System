package com.sustech.bojayL.ui.screens.students

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ui.components.StudentListItem
import com.sustech.bojayL.ui.theme.*
import com.sustech.bojayL.util.LfwImporter
import kotlinx.coroutines.launch

/**
 * 学生筛选状态
 */
enum class StudentFilter(val label: String) {
    ALL("全部"),
    RECOGNIZED("已识别"),
    NOT_RECOGNIZED("未识别"),
    ENROLLED("已录入人脸"),
    NOT_ENROLLED("未录入人脸")
}

/**
 * 学生列表页面
 * 
 * 根据 PRD 要求 STU-03：
 * - 支持按姓名、学号模糊搜索
 * - 支持按"已识别/未识别"筛选列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    students: List<Student> = defaultStudents,
    onStudentClick: (Student) -> Unit = {},
    onAddStudent: (Student) -> Unit = {},
    onAddStudents: (List<Student>) -> Unit = {},  // 批量添加
    onDeleteStudent: (Student) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(StudentFilter.ALL) }
    var showFilterMenu by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    
    // LFW 导入相关状态
    var showImportDialog by remember { mutableStateOf(false) }
    var importProgress by remember { mutableStateOf<LfwImporter.ImportProgress?>(null) }
    var importedStudents by remember { mutableStateOf<List<Student>>(emptyList()) }
    
    // 从默认路径导入 LFW 数据
    fun startLfwImport() {
        val lfwDir = LfwImporter.getLfwDir(context)
        val manifestFile = java.io.File(lfwDir, "manifest.json")
        
        android.util.Log.d("LfwImport", "LFW dir: ${lfwDir.absolutePath}")
        android.util.Log.d("LfwImport", "Manifest exists: ${manifestFile.exists()}")
        android.util.Log.d("LfwImport", "LFW dir exists: ${lfwDir.exists()}")
        
        // 确保目录存在
        if (!lfwDir.exists()) {
            lfwDir.mkdirs()
        }
        
        if (!manifestFile.exists()) {
            Toast.makeText(
                context, 
                "未找到 LFW 数据\n路径: ${lfwDir.absolutePath}\n请运行: adb push lfw_export/* \"${lfwDir.absolutePath}/\"", 
                Toast.LENGTH_LONG
            ).show()
            return
        }
        
        val manifestPath = manifestFile.absolutePath
        
        showImportDialog = true
        importProgress = LfwImporter.ImportProgress(0, 0, "", LfwImporter.ImportStatus.INITIALIZING, "正在初始化...")
        
        coroutineScope.launch {
            try {
                LfwImporter.importFromManifest(context, manifestPath).collect { progress ->
                    importProgress = progress
                    // 导入完成时直接获取学生数据
                    if (progress.status == LfwImporter.ImportStatus.COMPLETED && progress.students.isNotEmpty()) {
                        importedStudents = progress.students
                    }
                }
            } catch (e: Exception) {
                importProgress = LfwImporter.ImportProgress(
                    0, 0, "", 
                    LfwImporter.ImportStatus.FAILED, 
                    "导入失败: ${e.message}"
                )
            }
        }
    }
    
    // 添加学生对话框
    if (showAddDialog) {
        AddStudentDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { student ->
                onAddStudent(student)
                showAddDialog = false
            }
        )
    }
    
    // LFW 导入进度对话框
    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = {
                // 导入完成后才允许关闭
                if (importProgress?.status == LfwImporter.ImportStatus.COMPLETED ||
                    importProgress?.status == LfwImporter.ImportStatus.FAILED) {
                    showImportDialog = false
                    // 如果导入成功，添加学生
                    if (importedStudents.isNotEmpty()) {
                        onAddStudents(importedStudents)
                        importedStudents = emptyList()
                    }
                    importProgress = null
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "LFW 数据导入",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    importProgress?.let { progress ->
                        when (progress.status) {
                            LfwImporter.ImportStatus.INITIALIZING -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    color = CyanPrimary
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = progress.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
                            LfwImporter.ImportStatus.PROCESSING -> {
                                Text(
                                    text = "${progress.current} / ${progress.total}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = CyanPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { progress.current.toFloat() / progress.total.coerceAtLeast(1) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = CyanPrimary,
                                    trackColor = DarkSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = progress.currentName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextSecondary,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                            LfwImporter.ImportStatus.COMPLETED -> {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = AccentGreen
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = progress.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentGreen,
                                    textAlign = TextAlign.Center
                                )
                            }
                            LfwImporter.ImportStatus.FAILED -> {
                                Text(
                                    text = progress.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = AccentRed,
                                    textAlign = TextAlign.Center
                                )
                            }
                            else -> {}
                        }
                    }
                }
            },
            confirmButton = {
                if (importProgress?.status == LfwImporter.ImportStatus.COMPLETED ||
                    importProgress?.status == LfwImporter.ImportStatus.FAILED) {
                    Button(
                        onClick = {
                            showImportDialog = false
                            if (importedStudents.isNotEmpty()) {
                                onAddStudents(importedStudents)
                                importedStudents = emptyList()
                            }
                            importProgress = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyanPrimary,
                            contentColor = DarkBackground
                        )
                    ) {
                        Text("确定")
                    }
                }
            },
            dismissButton = {}
        )
    }
    
    // 删除确认对话框
    studentToDelete?.let { student ->
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            shape = RoundedCornerShape(16.dp),
            containerColor = DarkSurface,
            title = {
                Text(
                    text = "确认删除",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "确定要删除学生 \"${student.name}\" 吗？此操作不可撤销。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudent(student)
                        studentToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AccentRed,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { studentToDelete = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = TextSecondary
                    )
                ) {
                    Text("取消")
                }
            }
        )
    }
    
    // 使用外部传入的学生列表
    val allStudents = students
    
    // 模拟已识别学生
    val recognizedStudentIds = remember { setOf("s1", "s2", "s4", "s6") }
    
    // 筛选学生列表
    val filteredStudents = remember(searchQuery, selectedFilter, allStudents) {
        allStudents
            .filter { student ->
                // 搜索过滤
                if (searchQuery.isBlank()) true
                else student.name.contains(searchQuery) || student.studentId.contains(searchQuery)
            }
            .filter { student ->
                // 状态筛选
                when (selectedFilter) {
                    StudentFilter.ALL -> true
                    StudentFilter.RECOGNIZED -> student.id in recognizedStudentIds
                    StudentFilter.NOT_RECOGNIZED -> student.id !in recognizedStudentIds
                    StudentFilter.ENROLLED -> student.isEnrolled
                    StudentFilter.NOT_ENROLLED -> !student.isEnrolled
                }
            }
    }
    
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // LFW 导入按钮
                SmallFloatingActionButton(
                    onClick = { startLfwImport() },
                    containerColor = AccentOrange,
                    contentColor = DarkBackground
                ) {
                    Icon(
                        imageVector = Icons.Default.FileUpload,
                        contentDescription = "导入LFW数据"
                    )
                }
                // 添加学生按钮
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = CyanPrimary,
                    contentColor = DarkBackground
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "添加学生"
                    )
                }
            }
        },
        containerColor = DarkBackground
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "学生档案",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // 搜索栏
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = {
                Text(
                    text = "搜索姓名或学号",
                    color = TextTertiary
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "搜索",
                    tint = TextSecondary
                )
            },
            trailingIcon = {
                Box {
                    IconButton(onClick = { showFilterMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.FilterList,
                            contentDescription = "筛选",
                            tint = if (selectedFilter != StudentFilter.ALL) CyanPrimary else TextSecondary
                        )
                    }
                    
                    DropdownMenu(
                        expanded = showFilterMenu,
                        onDismissRequest = { showFilterMenu = false }
                    ) {
                        StudentFilter.entries.forEach { filter ->
                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = filter.label,
                                        color = if (filter == selectedFilter) CyanPrimary else TextPrimary
                                    )
                                },
                                onClick = {
                                    selectedFilter = filter
                                    showFilterMenu = false
                                }
                            )
                        }
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CyanPrimary,
                unfocusedBorderColor = CardBorder,
                focusedContainerColor = DarkSurface,
                unfocusedContainerColor = DarkSurface
            ),
            singleLine = true
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // 当前筛选状态
        if (selectedFilter != StudentFilter.ALL) {
            FilterChip(
                selected = true,
                onClick = { selectedFilter = StudentFilter.ALL },
                label = { Text(selectedFilter.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CyanPrimary.copy(alpha = 0.2f),
                    selectedLabelColor = CyanPrimary
                )
            )
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // 统计信息
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "共 ${filteredStudents.size} 人",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Text(
                text = "已识别 ${filteredStudents.count { it.id in recognizedStudentIds }} 人",
                style = MaterialTheme.typography.bodyMedium,
                color = CyanPrimary
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // 学生列表
        if (filteredStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "未找到匹配的学生" else "暂无学生数据",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextTertiary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(
                    items = filteredStudents,
                    key = { it.id }
                ) { student ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                studentToDelete = student
                                false // 不立即删除，等待确认
                            } else {
                                false
                            }
                        }
                    )
                    
                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(AccentRed)
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = TextPrimary
                                )
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true
                    ) {
                        StudentListItem(
                            student = student,
                            isRecognized = student.id in recognizedStudentIds,
                            onClick = { onStudentClick(student) }
                        )
                    }
                    
                    HorizontalDivider(
                        color = Divider,
                        thickness = 1.dp
                    )
                }
            }
        }
    }
    }
}

// 默认模拟学生数据
private val defaultStudents = listOf(
    Student(id = "s1", studentId = "2021001", name = "张三", className = "高三(2)班", grade = "高三", isEnrolled = true),
    Student(id = "s2", studentId = "2021002", name = "李四", className = "高三(2)班", grade = "高三", isEnrolled = true),
    Student(id = "s3", studentId = "2021003", name = "王五", className = "高三(2)班", grade = "高三", isEnrolled = false),
    Student(id = "s4", studentId = "2021004", name = "赵六", className = "高三(2)班", grade = "高三", isEnrolled = true),
    Student(id = "s5", studentId = "2021005", name = "钱七", className = "高三(2)班", grade = "高三", isEnrolled = false),
    Student(id = "s6", studentId = "2021006", name = "孙八", className = "高三(2)班", grade = "高三", isEnrolled = true),
    Student(id = "s7", studentId = "2021007", name = "周九", className = "高三(2)班", grade = "高三", isEnrolled = true),
    Student(id = "s8", studentId = "2021008", name = "吴十", className = "高三(2)班", grade = "高三", isEnrolled = false)
)

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun StudentsScreenPreview() {
    MobileappTheme {
        StudentsScreen()
    }
}
