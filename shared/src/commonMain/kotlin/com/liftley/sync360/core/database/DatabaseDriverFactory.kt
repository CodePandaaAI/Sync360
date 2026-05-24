package com.liftley.sync360.core.database

import app.cash.sqldelight.db.SqlDriver

expect class DatabaseDriverFactory {
    fun createDriver(): SqlDriver
}

expect fun createDatabaseDriverFactory(platformContext: Any?): DatabaseDriverFactory

fun createDatabase(driverFactory: DatabaseDriverFactory): SyncDatabase {
    return SyncDatabase(driverFactory.createDriver())
}
