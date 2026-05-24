package com.liftley.sync360.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): SqlDriver {
        val dbFile = java.io.File("sync360.db")
        val dbExists = dbFile.exists()
        return try {
            val driver = JdbcSqliteDriver("jdbc:sqlite:sync360.db")
            if (!dbExists) {
                SyncDatabase.Schema.create(driver)
            }
            driver
        } catch (e: Exception) {
            dbFile.delete()
            val driver = JdbcSqliteDriver("jdbc:sqlite:sync360.db")
            SyncDatabase.Schema.create(driver)
            driver
        }
    }
}

actual fun createDatabaseDriverFactory(platformContext: Any?): DatabaseDriverFactory {
    return DatabaseDriverFactory()
}
