package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.PyFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PyFileDao {
    @Query("SELECT * FROM py_files ORDER BY lastModified DESC")
    fun getAllFiles(): Flow<List<PyFileEntity>>

    @Query("SELECT * FROM py_files WHERE id = :id")
    suspend fun getFileById(id: Long): PyFileEntity?

    @Query("SELECT * FROM py_files WHERE fileName = :fileName LIMIT 1")
    suspend fun getFileByName(fileName: String): PyFileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFile(file: PyFileEntity): Long

    @Update
    suspend fun updateFile(file: PyFileEntity)

    @Delete
    suspend fun deleteFile(file: PyFileEntity)

    @Query("DELETE FROM py_files WHERE id IN (:ids)")
    suspend fun deleteFilesByIds(ids: List<Long>)

    @Query("UPDATE py_files SET isLastHosted = (id = :id)")
    suspend fun markLastHosted(id: Long)
}
