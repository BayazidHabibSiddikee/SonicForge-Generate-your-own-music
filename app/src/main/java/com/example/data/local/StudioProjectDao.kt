package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StudioProjectDao {

  @Transaction
  @Query("SELECT * FROM studio_projects ORDER BY updatedAtEpochMs DESC")
  fun observeProjectsWithClips(): Flow<List<StudioProjectWithClips>>

  @Query("SELECT * FROM studio_projects ORDER BY updatedAtEpochMs DESC")
  suspend fun getAllProjectsOnce(): List<StudioProjectEntity>

  @Transaction
  @Query("SELECT * FROM studio_projects WHERE id = :projectId LIMIT 1")
  suspend fun getProjectWithClipsOnce(projectId: String): StudioProjectWithClips?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProject(project: StudioProjectEntity)

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertProjectClips(refs: List<ProjectClipCrossRef>)

  @Query("DELETE FROM project_clips WHERE projectId = :projectId")
  suspend fun clearProjectClips(projectId: String)

  @Query("UPDATE studio_projects SET name = :name, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :projectId")
  suspend fun renameProject(projectId: String, name: String, updatedAtEpochMs: Long)

  @Query("DELETE FROM studio_projects WHERE id = :projectId")
  suspend fun deleteProjectById(projectId: String)

  @Transaction
  suspend fun saveProjectWithClips(
    project: StudioProjectEntity,
    refs: List<ProjectClipCrossRef>,
  ) {
    insertProject(project)
    clearProjectClips(project.id)
    if (refs.isNotEmpty()) {
      insertProjectClips(refs)
    }
  }
}
