package com.example.data

import android.content.Context
import com.example.data.local.ProjectClipCrossRef
import com.example.data.local.SonicForgeDatabase
import com.example.data.local.StudioProjectDao
import com.example.data.local.toDomain
import com.example.data.local.toEntity
import com.example.model.StudioProject
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class StudioProjectRepository(
  private val db: SonicForgeDatabase,
) {
  constructor(context: Context) : this(SonicForgeDatabase.getInstance(context))

  private val projectDao: StudioProjectDao = db.studioProjectDao()
  private val trackDao = db.generatedTrackDao()

  val projectsFlow: Flow<List<StudioProject>> =
    projectDao.observeProjectsWithClips().map { list ->
      list.map { it.toDomain(requireExistingFiles = true) }
    }

  suspend fun saveProject(project: StudioProject): StudioProject = withContext(Dispatchers.IO) {
    val cleanName = project.name.trim().ifBlank { "Untitled Studio Session" }
    val cleanId = project.id.ifBlank { UUID.randomUUID().toString() }
    val now = System.currentTimeMillis()
    val existing = projectDao.getProjectWithClipsOnce(cleanId)
    val createdAt = existing?.project?.createdAtEpochMs ?: project.createdAtEpochMs.takeIf { it > 0L } ?: now

    val validTrackIds = trackDao.getAllTracksOnce().map { it.id }.toSet()
    val requestedClipIds = (project.clipIds + listOfNotNull(project.activeTrackId))
      .distinct()
      .filter { it in validTrackIds }

    val normalizedProject = project.copy(
      id = cleanId,
      name = cleanName,
      notes = project.notes.trim(),
      clipIds = requestedClipIds,
      createdAtEpochMs = createdAt,
      updatedAtEpochMs = now,
    )
    val refs = requestedClipIds.mapIndexed { idx, trackId ->
      ProjectClipCrossRef(
        projectId = cleanId,
        trackId = trackId,
        sortOrder = idx,
      )
    }
    projectDao.saveProjectWithClips(normalizedProject.toEntity(), refs)
    projectDao.getProjectWithClipsOnce(cleanId)?.toDomain(requireExistingFiles = true)
      ?: normalizedProject
  }

  suspend fun getProjectById(
    projectId: String,
    requireExistingFiles: Boolean = true,
  ): StudioProject? = withContext(Dispatchers.IO) {
    projectDao.getProjectWithClipsOnce(projectId)?.toDomain(requireExistingFiles = requireExistingFiles)
  }

  suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
    projectDao.deleteProjectById(projectId)
  }
}
