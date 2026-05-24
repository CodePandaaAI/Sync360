package com.liftley.sync360.core.database

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

actual class DatabaseDriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return try {
            AndroidSqliteDriver(SyncDatabase.Schema, context, "sync360.db")
        } catch (e: Exception) {
            context.deleteDatabase("sync360.db")
            AndroidSqliteDriver(SyncDatabase.Schema, context, "sync360.db")
        }
    }
}

actual fun createDatabaseDriverFactory(platformContext: Any?): DatabaseDriverFactory {
    return DatabaseDriverFactory(platformContext as Context)
}
