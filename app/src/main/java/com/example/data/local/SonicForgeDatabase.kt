package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
  entities = [
    GeneratedTrackEntity::class,
    PlaylistEntity::class,
    PlaylistTrackCrossRef::class,
    StudioProjectEntity::class,
    ProjectClipCrossRef::class,
    TrackHistoryEntity::class,
  ],
  version = 7,
  exportSchema = false,
)
abstract class SonicForgeDatabase : RoomDatabase() {
  abstract fun generatedTrackDao(): GeneratedTrackDao
  abstract fun playlistDao(): PlaylistDao
  abstract fun studioProjectDao(): StudioProjectDao
  abstract fun trackHistoryDao(): TrackHistoryDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE generated_tracks ADD COLUMN category TEXT NOT NULL DEFAULT 'General'"
        )
        db.execSQL(
          "ALTER TABLE generated_tracks ADD COLUMN tagsCsv TEXT NOT NULL DEFAULT ''"
        )
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE generated_tracks ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0"
        )
      }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS playlists (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            createdAtEpochMs INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS playlist_tracks (
            playlistId TEXT NOT NULL,
            trackId TEXT NOT NULL,
            addedAtEpochMs INTEGER NOT NULL,
            PRIMARY KEY(playlistId, trackId),
            FOREIGN KEY(playlistId) REFERENCES playlists(id) ON DELETE CASCADE,
            FOREIGN KEY(trackId) REFERENCES generated_tracks(id) ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_playlist_tracks_playlistId ON playlist_tracks(playlistId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_playlist_tracks_trackId ON playlist_tracks(trackId)"
        )
      }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          "ALTER TABLE generated_tracks ADD COLUMN audioEnvelopeSpec TEXT NOT NULL DEFAULT ''"
        )
      }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS studio_projects (
            id TEXT NOT NULL PRIMARY KEY,
            name TEXT NOT NULL,
            notes TEXT NOT NULL DEFAULT '',
            activeTrackId TEXT,
            activeClipTitle TEXT NOT NULL DEFAULT '',
            clipIdsCsv TEXT NOT NULL DEFAULT '',
            selectedPlaylistId TEXT,
            playbackPositionMs INTEGER NOT NULL DEFAULT 0,
            volume REAL NOT NULL DEFAULT 0.85,
            isMuted INTEGER NOT NULL DEFAULT 0,
            isLooping INTEGER NOT NULL DEFAULT 0,
            pitchSemitones REAL NOT NULL DEFAULT 0.0,
            reverbSpecJson TEXT NOT NULL DEFAULT '',
            equalizerSpecJson TEXT NOT NULL DEFAULT '',
            crossfadeSpecJson TEXT NOT NULL DEFAULT '',
            loudnessSpecJson TEXT NOT NULL DEFAULT '',
            activeEnvelopeSpec TEXT NOT NULL DEFAULT '',
            sequencerSpecJson TEXT NOT NULL DEFAULT '',
            metronomeSpecJson TEXT NOT NULL DEFAULT '',
            createdAtEpochMs INTEGER NOT NULL,
            updatedAtEpochMs INTEGER NOT NULL
          )
          """.trimIndent()
        )
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS project_clips (
            projectId TEXT NOT NULL,
            trackId TEXT NOT NULL,
            sortOrder INTEGER NOT NULL DEFAULT 0,
            PRIMARY KEY(projectId, trackId),
            FOREIGN KEY(projectId) REFERENCES studio_projects(id) ON DELETE CASCADE,
            FOREIGN KEY(trackId) REFERENCES generated_tracks(id) ON DELETE CASCADE
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_project_clips_projectId ON project_clips(projectId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_project_clips_trackId ON project_clips(trackId)"
        )
      }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
          """
          CREATE TABLE IF NOT EXISTS track_generation_history (
            id TEXT NOT NULL PRIMARY KEY,
            trackId TEXT NOT NULL,
            title TEXT NOT NULL,
            prompt TEXT NOT NULL,
            engineOrSource TEXT NOT NULL,
            instrumentType TEXT NOT NULL,
            genre TEXT NOT NULL,
            bpm INTEGER NOT NULL,
            durationMs INTEGER NOT NULL,
            filePath TEXT NOT NULL,
            createdAtEpochMs INTEGER NOT NULL,
            lastPlayedAtEpochMs INTEGER NOT NULL DEFAULT 0,
            playCount INTEGER NOT NULL DEFAULT 0
          )
          """.trimIndent()
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_track_generation_history_trackId ON track_generation_history(trackId)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_track_generation_history_createdAtEpochMs ON track_generation_history(createdAtEpochMs)"
        )
        db.execSQL(
          "CREATE INDEX IF NOT EXISTS index_track_generation_history_instrumentType ON track_generation_history(instrumentType)"
        )
      }
    }

    @Volatile
    private var INSTANCE: SonicForgeDatabase? = null

    fun getInstance(context: Context): SonicForgeDatabase {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: Room.databaseBuilder(
          context.applicationContext,
          SonicForgeDatabase::class.java,
          "sonicforge.db",
        )
          .addMigrations(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
          )
          .build()
          .also { INSTANCE = it }
      }
    }
  }
}
