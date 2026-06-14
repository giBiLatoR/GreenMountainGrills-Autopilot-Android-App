package com.gibilator.gmg.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.gibilator.gmg.cook.CookSession
import com.gibilator.gmg.cook.CookStore
import com.gibilator.gmg.protocol.GmgSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One persisted grill (multi-grill list). */
data class StoredGrill(
    val serial: String,
    val host: String,
    val modelId: Int,
    val model: String,
    val firmware: String,
    val label: String,
)

/**
 * Raw-SQLite persistence — a direct analog of `cook_manager._init_db_sync`
 * (the HA integration uses raw sqlite3, so this is the faithful port). Also
 * holds the known-grills list for multi-grill support.
 */
class GmgStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION),
    CookStore {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cook_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                serial TEXT NOT NULL,
                meat_key TEXT NOT NULL,
                weight_kg REAL NOT NULL,
                probe_index INTEGER NOT NULL,
                mode TEXT NOT NULL,
                pit_target_f INTEGER NOT NULL,
                projection_json TEXT NOT NULL,
                state TEXT NOT NULL,
                created_at REAL NOT NULL,
                cook_started_at REAL,
                pull_reached_at REAL,
                completed_at REAL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cook_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                ts REAL NOT NULL,
                pit_f INTEGER,
                pit_set_f INTEGER,
                probe_f INTEGER,
                state TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS grills (
                serial TEXT PRIMARY KEY,
                host TEXT NOT NULL,
                model_id INTEGER NOT NULL,
                model TEXT NOT NULL,
                firmware TEXT NOT NULL,
                label TEXT NOT NULL,
                last_seen REAL NOT NULL
            )
            """.trimIndent(),
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 only; nothing to migrate yet.
    }

    // --- CookStore ---------------------------------------------------------

    override suspend fun insertSession(session: CookSession, serial: String): Long =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put("serial", serial)
                put("meat_key", session.meatKey)
                put("weight_kg", session.weightKg)
                put("probe_index", session.probeIndex)
                put("mode", session.mode.value)
                put("pit_target_f", session.pitTargetF)
                put("projection_json", projectionJson(session))
                put("state", session.state.value)
                put("created_at", session.createdAt)
            }
            writableDatabase.insert("cook_sessions", null, values)
        }

    override suspend fun completeSession(session: CookSession, finalState: String, serial: String) {
        withContext(Dispatchers.IO) {
            writableDatabase.execSQL(
                "UPDATE cook_sessions SET state=?, completed_at=?, pull_reached_at=? " +
                    "WHERE id=(SELECT MAX(id) FROM cook_sessions WHERE serial=?)",
                arrayOf(finalState, System.currentTimeMillis() / 1000.0, session.pullReachedAt, serial),
            )
        }
    }

    override suspend fun logSample(session: CookSession, snapshot: GmgSnapshot, probeF: Double?, serial: String) {
        withContext(Dispatchers.IO) {
            val db = writableDatabase
            val cursor = db.rawQuery("SELECT MAX(id) FROM cook_sessions WHERE serial=?", arrayOf(serial))
            val sessionId = cursor.use { if (it.moveToFirst()) it.getLong(0) else 0L }
            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("ts", System.currentTimeMillis() / 1000.0)
                put("pit_f", snapshot.grillTemp)
                put("pit_set_f", snapshot.grillSetTemp)
                if (probeF != null) put("probe_f", probeF.toInt()) else putNull("probe_f")
                put("state", session.state.value)
            }
            db.insert("cook_log", null, values)
        }
    }

    private fun projectionJson(session: CookSession): String {
        val phases = JSONArray()
        for (p in session.projection.phases) {
            phases.put(
                JSONObject()
                    .put("name", p.name)
                    .put("start_f", p.startInternalF)
                    .put("end_f", p.endInternalF)
                    .put("hours", p.hours),
            )
        }
        return JSONObject()
            .put("total_hours", session.projection.totalHours)
            .put("phases", phases)
            .toString()
    }

    // --- grill list --------------------------------------------------------

    suspend fun upsertGrill(grill: StoredGrill) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("serial", grill.serial)
            put("host", grill.host)
            put("model_id", grill.modelId)
            put("model", grill.model)
            put("firmware", grill.firmware)
            put("label", grill.label)
            put("last_seen", System.currentTimeMillis() / 1000.0)
        }
        writableDatabase.insertWithOnConflict("grills", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        Unit
    }

    suspend fun listGrills(): List<StoredGrill> = withContext(Dispatchers.IO) {
        val out = mutableListOf<StoredGrill>()
        readableDatabase.rawQuery(
            "SELECT serial, host, model_id, model, firmware, label FROM grills ORDER BY label",
            null,
        ).use { c ->
            while (c.moveToNext()) {
                out.add(
                    StoredGrill(
                        serial = c.getString(0),
                        host = c.getString(1),
                        modelId = c.getInt(2),
                        model = c.getString(3),
                        firmware = c.getString(4),
                        label = c.getString(5),
                    ),
                )
            }
        }
        out
    }

    suspend fun deleteGrill(serial: String) = withContext(Dispatchers.IO) {
        writableDatabase.delete("grills", "serial=?", arrayOf(serial))
        Unit
    }

    companion object {
        const val DB_NAME = "gmg_cooks.db"
        const val DB_VERSION = 1
    }
}
