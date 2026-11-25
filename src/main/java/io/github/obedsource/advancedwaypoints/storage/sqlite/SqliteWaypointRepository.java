package io.github.obedsource.advancedwaypoints.storage.sqlite;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.api.WaypointType;
import io.github.obedsource.advancedwaypoints.storage.StorageException;
import io.github.obedsource.advancedwaypoints.storage.WaypointRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** SQLite waypoints. Every value goes through PreparedStatement parameters (no SQL injection). */
public final class SqliteWaypointRepository implements WaypointRepository {

    private static final String COLUMNS = "id, owner, code, name, world, x, y, z, text_color, icon, icon_color, "
            + "background, favorite, favorite_at, created_at, kind, expires_at";

    private final SqliteDatabase database;

    public SqliteWaypointRepository(SqliteDatabase database) {
        this.database = database;
    }

    private Connection connection() {
        return database.connection();
    }

    @Override
    public List<Waypoint> findByOwner(UUID owner) throws StorageException {
        String sql = "SELECT " + COLUMNS + " FROM awp_waypoints WHERE owner = ? ORDER BY created_at ASC";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, owner.toString());
            try (ResultSet rs = ps.executeQuery()) {
                List<Waypoint> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(map(rs));
                }
                return list;
            }
        } catch (SQLException e) {
            throw new StorageException("Could not load waypoints of " + owner, e);
        }
    }

    private static Waypoint map(ResultSet rs) throws SQLException {
        return new Waypoint(
                rs.getLong("id"),
                UUID.fromString(rs.getString("owner")),
                rs.getString("code"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getString("text_color"),
                rs.getString("icon"),
                rs.getString("icon_color"),
                rs.getInt("background"),
                rs.getInt("favorite") != 0,
                rs.getLong("favorite_at"),
                rs.getLong("created_at"),
                WaypointType.byId(rs.getInt("kind")),
                rs.getLong("expires_at"));
    }

    @Override
    public void insert(Waypoint waypoint) throws StorageException {
        String sql = "INSERT INTO awp_waypoints (owner, code, name, world, x, y, z, text_color, icon, icon_color, "
                + "background, favorite, favorite_at, created_at, kind, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, waypoint.getOwner().toString());
            ps.setString(2, waypoint.getCode());
            ps.setString(3, waypoint.getName());
            ps.setString(4, waypoint.getWorld());
            ps.setDouble(5, waypoint.getX());
            ps.setDouble(6, waypoint.getY());
            ps.setDouble(7, waypoint.getZ());
            ps.setString(8, waypoint.getTextColor());
            ps.setString(9, waypoint.getIcon());
            ps.setString(10, waypoint.getIconColor());
            ps.setInt(11, waypoint.getBackgroundArgb());
            ps.setInt(12, waypoint.isFavorite() ? 1 : 0);
            ps.setLong(13, waypoint.getFavoriteAt());
            ps.setLong(14, waypoint.getCreatedAt());
            ps.setInt(15, waypoint.getType().id());
            ps.setLong(16, waypoint.getExpiresAt());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    waypoint.setId(keys.getLong(1));
                }
            }
        } catch (SQLException e) {
            throw new StorageException("Could not insert " + waypoint, e);
        }
    }

    @Override
    public void update(Waypoint waypoint) throws StorageException {
        String sql = "UPDATE awp_waypoints SET name = ?, text_color = ?, icon = ?, icon_color = ?, background = ?, "
                + "favorite = ?, favorite_at = ? WHERE owner = ? AND code = ?";
        try (PreparedStatement ps = connection().prepareStatement(sql)) {
            ps.setString(1, waypoint.getName());
            ps.setString(2, waypoint.getTextColor());
            ps.setString(3, waypoint.getIcon());
            ps.setString(4, waypoint.getIconColor());
            ps.setInt(5, waypoint.getBackgroundArgb());
            ps.setInt(6, waypoint.isFavorite() ? 1 : 0);
            ps.setLong(7, waypoint.getFavoriteAt());
            ps.setString(8, waypoint.getOwner().toString());
            ps.setString(9, waypoint.getCode());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not update " + waypoint, e);
        }
    }

    @Override
    public void delete(UUID owner, String code) throws StorageException {
        try (PreparedStatement wp = connection().prepareStatement("DELETE FROM awp_waypoints WHERE owner = ? AND code = ?");
             PreparedStatement vis = connection().prepareStatement("DELETE FROM awp_visible WHERE owner = ? AND code = ?")) {
            wp.setString(1, owner.toString());
            wp.setString(2, code);
            wp.executeUpdate();
            vis.setString(1, owner.toString());
            vis.setString(2, code);
            vis.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not delete waypoint " + code + " of " + owner, e);
        }
    }

    @Override
    public void deleteAll(UUID owner) throws StorageException {
        try (PreparedStatement wp = connection().prepareStatement("DELETE FROM awp_waypoints WHERE owner = ?");
             PreparedStatement vis = connection().prepareStatement("DELETE FROM awp_visible WHERE owner = ?")) {
            wp.setString(1, owner.toString());
            wp.executeUpdate();
            vis.setString(1, owner.toString());
            vis.executeUpdate();
        } catch (SQLException e) {
            throw new StorageException("Could not delete the waypoints of " + owner, e);
        }
    }
}
