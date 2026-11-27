package com.pratyush.orbitmobile.data.local.database

import com.pratyush.orbitmobile.data.local.database.dao.DeviceDao

/**
 * Architectural foundation for Room persistence.
 * Ready to be annotated with @Database when local SQLite persistence is required.
 */
interface OrbitDatabase {
    fun deviceDao(): DeviceDao
    fun aiChatDao(): com.pratyush.orbitmobile.data.local.database.dao.AiChatDao

    companion object {
        const val DATABASE_NAME = "orbit_mobile.db"
        const val DATABASE_VERSION = 1
    }
}
