package app.geeflow.app.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.Test
import kotlin.test.assertEquals

class BrewProfileMigrationTest {

    @Test
    fun `when version four database migrates then pending profile deletions are retained`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use { driver ->
            driver.execute(null, "CREATE TABLE users (id INTEGER PRIMARY KEY)", 0)

            AppDatabase.Schema.migrate(driver, oldVersion = 4, newVersion = 5)
            driver.execute(null, "INSERT INTO users (id) VALUES (7)", 0)
            driver.execute(
                null,
                "INSERT INTO pending_brew_profile_deletions (userId, profileId) VALUES (7, 42)",
                0,
            )
            val profileId = driver.executeQuery(
                null,
                "SELECT profileId FROM pending_brew_profile_deletions WHERE userId = 7",
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0))
                },
                0,
            ).value

            assertEquals(42L, profileId)
        }
    }

    @Test
    fun `when version one profiles migrate then Single Dose settings receive defaults`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use { driver ->
            driver.execute(null, "CREATE TABLE brew_profiles (id INTEGER PRIMARY KEY, name TEXT NOT NULL)", 0)
            driver.execute(null, "CREATE TABLE devices (id INTEGER PRIMARY KEY, manufacturer TEXT NOT NULL)", 0)
            driver.execute(null, "INSERT INTO brew_profiles (id, name) VALUES (1, 'Espresso')", 0)

            AppDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)
            val settings = driver.executeQuery(
                null,
                "SELECT singleDoseEnabled, singleDoseGrindingSize, singleDoseGrindingSpeed FROM brew_profiles WHERE id = 1",
                { cursor ->
                    cursor.next()
                    QueryResult.Value(
                        Triple(cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)),
                    )
                },
                0,
            ).value

            assertEquals(Triple(0L, 300L, 500L), settings)
        }
    }

    @Test
    fun `when existing devices migrate then assigns Data S only to Wendougee and preserves names`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use { driver ->
            driver.execute(null, "CREATE TABLE brew_profiles (id INTEGER PRIMARY KEY)", 0)
            driver.execute(
                null,
                "CREATE TABLE devices (id INTEGER PRIMARY KEY, manufacturer TEXT NOT NULL, name TEXT NOT NULL)",
                0,
            )
            driver.execute(
                null,
                "INSERT INTO devices VALUES (1, 'Wendougee', 'My coffee'), (2, 'GeeFlow', 'Demo'), (3, 'Other', 'Other')",
                0,
            )

            AppDatabase.Schema.migrate(driver, oldVersion = 1, newVersion = 2)
            val devices = driver.executeQuery(
                null,
                "SELECT name, modelId, modelSource FROM devices ORDER BY id",
                { cursor ->
                    QueryResult.Value(
                        buildList {
                            while (cursor.next().value) {
                                add(Triple(cursor.getString(0), cursor.getString(1), cursor.getString(2)))
                            }
                        }
                    )
                },
                0,
            ).value

            assertEquals(
                listOf(
                    Triple("My coffee", "wendougee.data-s", "migration"),
                    Triple("Demo", null, null),
                    Triple("Other", null, null),
                ),
                devices,
            )
        }
    }

    @Test
    fun `when version three users migrate then the profile seed ledger is created`() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.use { driver ->
            driver.execute(null, "CREATE TABLE users (id INTEGER PRIMARY KEY)", 0)
            driver.execute(null, "INSERT INTO users (id) VALUES (1)", 0)

            AppDatabase.Schema.migrate(driver, oldVersion = 3, newVersion = 4)

            val tableExists = driver.executeQuery(
                null,
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'default_profile_seeds'",
                { cursor ->
                    cursor.next()
                    QueryResult.Value(cursor.getLong(0))
                },
                0,
            ).value
            assertEquals(1L, tableExists)
        }
    }
}
