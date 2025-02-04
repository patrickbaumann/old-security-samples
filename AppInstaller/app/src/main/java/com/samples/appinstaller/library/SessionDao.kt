/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.samples.appinstaller.library

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.samples.appinstaller.apps.InstallSession

const val DATABASE_NAME = "app-database"

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: InstallSession)

    @Delete
    suspend fun delete(session: InstallSession)

    @Query("SELECT * FROM install_sessions")
    suspend fun getAll(): List<InstallSession>

    @Query("SELECT * FROM install_sessions WHERE package_name = :packageName LIMIT 1")
    suspend fun findByPackageName(packageName: String): InstallSession?

    @Query("SELECT * FROM install_sessions WHERE session_id = :sessionId LIMIT 1")
    suspend fun findBySessionId(sessionId: Int): InstallSession?

    @Query("DELETE FROM install_sessions WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("DELETE FROM install_sessions WHERE session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: Int)

    @Query("DELETE FROM install_sessions")
    suspend fun deleteAllSessions()
}
