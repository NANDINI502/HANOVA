package com.nandini.hanova.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** One recorded class session = one Lecture Diary entry. */
@Entity
data class Lecture(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val course: String,
    val startedAt: Long,          // epoch millis
    val durationMs: Long = 0,
    val audioPath: String? = null,   // full lecture WAV, for re-transcribing later
)

/** One caption line (a finished sentence) inside a lecture. */
@Entity(
    foreignKeys = [ForeignKey(
        entity = Lecture::class, parentColumns = ["id"], childColumns = ["lectureId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("lectureId")],
)
data class Line(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long,
    val tMs: Long,                // offset from lecture start
    val zh: String,
    val en: String,
    val starred: Boolean = false,
    val translationPending: Boolean = false,
)

/** One Homework Diary task — auto-detected from a lecture line, or added by hand. */
@Entity(indices = [Index("lectureId")])
data class Homework(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lectureId: Long?,
    val tMs: Long,
    val course: String,
    val en: String,
    val zh: String,
    val dueEpochDay: Long?,       // LocalDate.toEpochDay(), null = no date found
    val done: Boolean = false,
    val createdAt: Long,
)

data class LectureSummary(
    @Embedded val lecture: Lecture,
    val hwCount: Int,
    val starCount: Int,
    val firstLine: String?,
)

@Dao
interface LectureDao {
    @Insert suspend fun insert(lecture: Lecture): Long

    @Query("UPDATE Lecture SET durationMs = :durationMs WHERE id = :id")
    suspend fun setDuration(id: Long, durationMs: Long)

    @Query("UPDATE Lecture SET audioPath = :path WHERE id = :id")
    suspend fun setAudioPath(id: Long, path: String)

    @Query("SELECT * FROM Lecture WHERE id = :id")
    fun observe(id: Long): Flow<Lecture?>

    @Query(
        """
        SELECT l.*,
          (SELECT COUNT(*) FROM Homework h WHERE h.lectureId = l.id) AS hwCount,
          (SELECT COUNT(*) FROM Line x WHERE x.lectureId = l.id AND x.starred = 1) AS starCount,
          (SELECT en FROM Line x WHERE x.lectureId = l.id ORDER BY x.tMs LIMIT 1) AS firstLine
        FROM Lecture l ORDER BY l.startedAt DESC
        """
    )
    fun observeSummaries(): Flow<List<LectureSummary>>

    @Query("SELECT DISTINCT course FROM Lecture ORDER BY startedAt DESC LIMIT 6")
    suspend fun recentCourses(): List<String>
}

@Dao
interface LineDao {
    @Insert suspend fun insert(line: Line): Long

    @Query("SELECT * FROM Line WHERE lectureId = :lectureId ORDER BY tMs")
    fun observeFor(lectureId: Long): Flow<List<Line>>

    @Query("UPDATE Line SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("SELECT * FROM Line WHERE translationPending = 1")
    suspend fun pending(): List<Line>

    @Query("UPDATE Line SET en = :en, translationPending = 0 WHERE id = :id")
    suspend fun setTranslation(id: Long, en: String)
}

@Dao
interface HomeworkDao {
    @Insert suspend fun insert(hw: Homework): Long

    @Query("SELECT * FROM Homework ORDER BY done, COALESCE(dueEpochDay, 999999), createdAt DESC")
    fun observeAll(): Flow<List<Homework>>

    @Query("SELECT COUNT(*) FROM Homework WHERE done = 0")
    fun observeOpenCount(): Flow<Int>

    @Query("UPDATE Homework SET done = :done WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean)

    @Query("DELETE FROM Homework WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [Lecture::class, Line::class, Homework::class], version = 1, exportSchema = false)
abstract class HanovaDb : RoomDatabase() {
    abstract fun lectures(): LectureDao
    abstract fun lines(): LineDao
    abstract fun homework(): HomeworkDao

    companion object {
        fun create(context: Context): HanovaDb =
            Room.databaseBuilder(context, HanovaDb::class.java, "hanova.db").build()
    }
}
