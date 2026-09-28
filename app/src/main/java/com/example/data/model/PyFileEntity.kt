package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "py_files")
data class PyFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val filePath: String,
    val content: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val detectedPackages: String = "", // Comma-separated package names
    val isLastHosted: Boolean = false
) {
    fun getPackageList(): List<String> {
        if (detectedPackages.isBlank()) return emptyList()
        return detectedPackages.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
