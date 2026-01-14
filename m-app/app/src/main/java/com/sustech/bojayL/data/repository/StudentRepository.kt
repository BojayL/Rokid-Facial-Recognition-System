package com.sustech.bojayL.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sustech.bojayL.data.model.Student
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 学生数据仓库
 * 
 * 使用 DataStore + JSON 序列化实现持久化存储
 */
class StudentRepository(private val context: Context) {
    
    companion object {
        private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "students")
        private val STUDENTS_KEY = stringPreferencesKey("students_list")
        
        // 默认学生列表
        val defaultStudents = listOf(
            Student(id = "s1", studentId = "2021001", name = "张三", className = "高三(2)班", grade = "高三", isEnrolled = true),
            Student(id = "s2", studentId = "2021002", name = "李四", className = "高三(2)班", grade = "高三", isEnrolled = true),
            Student(id = "s3", studentId = "2021003", name = "王五", className = "高三(2)班", grade = "高三", isEnrolled = false),
            Student(id = "s4", studentId = "2021004", name = "赵六", className = "高三(2)班", grade = "高三", isEnrolled = true),
            Student(id = "s5", studentId = "2021005", name = "钱七", className = "高三(2)班", grade = "高三", isEnrolled = false),
            Student(id = "s6", studentId = "2021006", name = "孙八", className = "高三(2)班", grade = "高三", isEnrolled = true),
            Student(id = "s7", studentId = "2021007", name = "周九", className = "高三(2)班", grade = "高三", isEnrolled = true),
            Student(id = "s8", studentId = "2021008", name = "吴十", className = "高三(2)班", grade = "高三", isEnrolled = false)
        )
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    /**
     * 学生列表 Flow
     */
    val studentsFlow: Flow<List<Student>> = context.dataStore.data.map { preferences ->
        val studentsJson = preferences[STUDENTS_KEY]
        if (studentsJson != null) {
            try {
                json.decodeFromString<List<Student>>(studentsJson)
            } catch (e: Exception) {
                defaultStudents
            }
        } else {
            defaultStudents
        }
    }
    
    /**
     * 保存学生列表
     */
    suspend fun saveStudents(students: List<Student>) {
        context.dataStore.edit { preferences ->
            preferences[STUDENTS_KEY] = json.encodeToString(students)
        }
    }
    
    /**
     * 添加学生
     */
    suspend fun addStudent(student: Student, currentList: List<Student>) {
        saveStudents(currentList + student)
    }
    
    /**
     * 删除学生
     */
    suspend fun deleteStudent(studentId: String, currentList: List<Student>) {
        saveStudents(currentList.filter { it.id != studentId })
    }
    
    /**
     * 更新学生信息
     */
    suspend fun updateStudent(student: Student, currentList: List<Student>) {
        saveStudents(currentList.map { if (it.id == student.id) student else it })
    }
}
