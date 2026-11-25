package io.github.obedsource.advancedwaypoints.storage.sqlite;

import io.github.obedsource.advancedwaypoints.storage.StorageException;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Owns the single SQLite connection and the single thread that uses it.
 * Every query runs on that thread, so the server thread never waits on disk I/O.
 */
public final class SqliteDatabase {

    private static final int SCHEMA_VERSION = 1;

    private final File file;
    private final Logger logger;
    private final ExecutorService executor;
    private Connection connection;

    public SqliteDatabase(File file, Logger logger) {
        this.file = file;
        this.logger = logger;
        this.executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "AdvancedWaypoints-Storage");
            thread.setDaemon(true);
            return thread;
        });
    }

    public ExecutorService executor() {
        return executor;
    }

    /** Opens the connection and creates or migrates the schema. Blocking, called once on enable. */
    public void open() throws StorageException {
        try {
            executor.submit(() -> {
                openNow();
                return null;
            }).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new StorageException("Could not open the SQLite database " + file, cause);
        }
    }

    private void openNow() throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new SQLException("Could not create folder " + parent);
        }
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + file.getAbsolutePath());
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA foreign_keys=ON");
            statement.execute("PRAGMA busy_timeout=5000");
        }
        migrate();
    }

    private void migrate() throws SQLException {
        int version;
        try (Statement statement = connection.createStatement();
             java.sql.ResultSet rs = statement.executeQuery("PRAGMA user_version")) {
            version = rs.next() ? rs.getInt(1) : 0;
        }
        if (version >= SCHEMA_VERSION) {
            return;
        }
        boolean autoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try (Statement statement = connection.createStatement()) {
            if (version < 1) {
                statement.execute("CREATE TABLE IF NOT EXISTS awp_waypoints ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "owner TEXT NOT NULL,"
                        + "code TEXT NOT NULL,"
                        + "name TEXT NOT NULL,"
                        + "world TEXT NOT NULL,"
                        + "x REAL NOT NULL,"
                        + "y REAL NOT NULL,"
                        + "z REAL NOT NULL,"
                        + "text_color TEXT NOT NULL,"
                        + "icon TEXT NOT NULL,"
                        + "icon_color TEXT NOT NULL,"
                        + "background INTEGER NOT NULL,"
                        + "favorite INTEGER NOT NULL DEFAULT 0,"
                        + "favorite_at INTEGER NOT NULL DEFAULT 0,"
                        + "created_at INTEGER NOT NULL,"
                        + "UNIQUE(owner, code))");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_awp_waypoints_owner ON awp_waypoints(owner)");
                statement.execute("CREATE TABLE IF NOT EXISTS awp_players ("
                        + "uuid TEXT PRIMARY KEY,"
                        + "name TEXT,"
                        + "name_lower TEXT,"
                        + "accumulate INTEGER NOT NULL DEFAULT 0)");
                statement.execute("CREATE INDEX IF NOT EXISTS idx_awp_players_name ON awp_players(name_lower)");
                statement.execute("CREATE TABLE IF NOT EXISTS awp_visible ("
                        + "owner TEXT NOT NULL,"
                        + "code TEXT NOT NULL,"
                        + "position INTEGER NOT NULL,"
                        + "PRIMARY KEY(owner, code))");
            }
            statement.execute("PRAGMA user_version = " + SCHEMA_VERSION);
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    /** Only valid on the storage thread. */
    Connection connection() {
        return connection;
    }

    /** Finishes queued writes, then closes the connection. */
    public void close() {
        executor.submit(() -> {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.close();
                }
            } catch (SQLException e) {
                logger.log(Level.WARNING, "Error closing the database", e);
            }
        });
        executor.shutdown();
        try {
            if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                logger.warning("Timed out waiting for database writes to finish");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
