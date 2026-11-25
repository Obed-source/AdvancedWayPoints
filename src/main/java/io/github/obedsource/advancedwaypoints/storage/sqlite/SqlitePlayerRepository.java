package io.github.obedsource.advancedwaypoints.storage.sqlite;

import io.github.obedsource.advancedwaypoints.storage.PlayerRepository;
import io.github.obedsource.advancedwaypoints.storage.StorageException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** SQLite player settings. Every value goes through PreparedStatement parameters. */
public final class SqlitePlayerRepository implements PlayerRepository {

    private final SqliteDatabase database;

    public SqlitePlayerRepository(SqliteDatabase database) {
        this.database = database;
    }

    private Connection connection() {
        return database.connection();
    }

    @Override
    public Optional<Boolean> findAccumulate(UUID uuid) throws StorageException {
        try (PreparedStatement ps = connection().prepareStatement("SELECT accumulate FROM awp_players WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1) != 0) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load settings of " + uuid, e);
        }
    }

    @Override
    public void save(UUID uuid, String name, boolean accumulate) throws StorageException {
        String sql = "INSERT INTO awp_players (uuid, name, name_lower, accumulate) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET name = COALESCE(excluded.name, awp_players.name), "
                + "name_lower = COALESCE(excluded.name_lower, awp_players.name_lower), accumulate = excluded.accumulate";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name);
            ps.setString(3, name == null ? null : name.toLowerCase(Locale.ROOT));
            ps.setInt(4, accumulate ? 1 : 0);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not save settings of " + uuid, e);
        }
    }

    @Override
    public Optional<UUID> findByName(String name) throws StorageException {
        try (PreparedStatement ps = connection().prepareStatement("SELECT uuid FROM awp_players WHERE name_lower = ? LIMIT 1")) {
            ps.setString(1, name.toLowerCase(Locale.ROOT));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(UUID.fromString(rs.getString(1))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new StorageException("Could not find player " + name, e);
        }
    }

    @Override
    public List<String> findVisible(UUID uuid) throws StorageException {
        try (PreparedStatement ps = connection().prepareStatement("SELECT code FROM awp_visible WHERE owner = ? ORDER BY position ASC")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<String> codes = new ArrayList<>();
                while (rs.next()) {
                    codes.add(rs.getString(1));
                }
                return codes;
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load visible waypoints of " + uuid, e);
        }
    }

    @Override
    public void replaceVisible(UUID uuid, List<String> codes) throws StorageException {
        Connection connection = connection();
        try {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM awp_visible WHERE owner = ?");
                 PreparedStatement insert = connection.prepareStatement("INSERT INTO awp_visible (owner, code, position) VALUES (?, ?, ?)")) {
                delete.setString(1, uuid.toString());
                delete.executeUpdate();
                int position = 0;
                for (String code : codes) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, code);
                    insert.setInt(3, position++);
                    insert.addBatch();
                }
                insert.executeBatch();
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException e) {
            throw new StorageException("Could not save visible waypoints of " + uuid, e);
        }
    }
}
