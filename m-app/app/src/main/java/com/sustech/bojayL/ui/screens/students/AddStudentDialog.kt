package com.sustech.bojayL.ui.screens.students

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.sustech.bojayL.data.model.Student
import com.sustech.bojayL.ui.theme.*
import java.util.UUID

/**
 * 添加学生对话框
 *
 * 支持输入学号、姓名、班级、年级等基础信息
 */
@Composable
fun AddStudentDialog(
    onDismiss: () -> Unit,
    onConfirm: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    var studentId by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }
    
    // 验证表单
    val isFormValid = studentId.isNotBlank() && name.isNotBlank() && 
                      className.isNotBlank() && grade.isNotBlank()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        containerColor = DarkSurface,
        title = {
            Text(
                text = "添加学生",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = studentId,
                    onValueChange = { studentId = it },
                    label = { Text("学号", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanPrimary
                    )
                )
                
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("姓名", color = TextSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanPrimary
                    )
                )
                
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it },
                    label = { Text("班级", color = TextSecondary) },
                    placeholder = { Text("如：高三(2)班", color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanPrimary
                    )
                )
                
                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("年级", color = TextSecondary) },
                    placeholder = { Text("如：高三", color = TextTertiary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPrimary,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = CyanPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val student = Student(
                        id = UUID.randomUUID().toString(),
                        studentId = studentId,
                        name = name,
                        className = className,
                        grade = grade,
                        isEnrolled = false
                    )
                    onConfirm(student)
                },
                enabled = isFormValid,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyanPrimary,
                    contentColor = DarkBackground,
                    disabledContainerColor = CyanPrimary.copy(alpha = 0.3f),
                    disabledContentColor = DarkBackground.copy(alpha = 0.5f)
                )
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = TextSecondary
                )
            ) {
                Text("取消")
            }
        }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun AddStudentDialogPreview() {
    MobileappTheme {
        AddStudentDialog(
            onDismiss = {},
            onConfirm = {}
        )
    }
}
