package io.github.obedsource.advancedwaypoints;

import io.github.obedsource.advancedwaypoints.api.Waypoint;
import io.github.obedsource.advancedwaypoints.session.OwnerData;
import io.github.obedsource.advancedwaypoints.storage.DataStore;
import io.github.obedsource.advancedwaypoints.storage.sqlite.SqliteDatabase;
import io.github.obedsource.advancedwaypoints.storage.sqlite.SqlitePlayerRepository;
import io.github.obedsource.advancedwaypoints.storage.sqlite.SqliteWaypointRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round trip through the real SQLite repositories. */
class StorageTest {

    @TempDir
    File folder;

    private static Waypoint waypoint(UUID owner, String code, String name, long created) {
        return new Waypoint(0L, owner, code, name, "world", 10.5, 64, -20.25, "&f", "•", "&c", 0xA0000000,
                false, 0L, created);
    }

    @Test
    void roundTrip() throws Exception {
        SqliteDatabase database = new SqliteDatabase(new File(folder, "test.db"), Logger.getLogger("test"));
        database.open();
        DataStore store = new DataStore(new SqliteWaypointRepository(database), new SqlitePlayerRepository(database),
                database.executor(), Logger.getLogger("test"));
        UUID owner = UUID.randomUUID();

        Waypoint home = waypoint(owner, "aB3x", "casa de amigo", 1000L);
        Waypoint evil = waypoint(owner, "Zz99", "x'); DROP TABLE awp_waypoints; --", 2000L);
        store.insert(home).get(5, TimeUnit.SECONDS);
        store.insert(evil).get(5, TimeUnit.SECONDS);
        assertTrue(home.getId() > 0);

        home.setFavorite(true);
        home.setName("Casa ñandú");
        store.update(home);
        store.saveVisible(owner, List.of("Zz99", "aB3x"));
        store.saveProfile(owner, "Steve", true);

        OwnerData data = store.loadOwner(owner, "Steve").get(5, TimeUnit.SECONDS);
        assertEquals(2, data.count());
        assertEquals("Casa ñandú", data.get("aB3x").orElseThrow().getName());
        assertEquals("x'); DROP TABLE awp_waypoints; --", data.get("Zz99").orElseThrow().getName());
        assertTrue(data.get("aB3x").orElseThrow().isFavorite());
        assertEquals(List.of("Zz99", "aB3x"), data.visible());
        assertTrue(data.accumulate());
        // Favorites first.
        assertEquals("aB3x", data.sorted(false).get(0).getCode());
        assertEquals("aB3x", data.lastSelected().orElseThrow().getCode());
        assertEquals(owner, store.findByName("steve").get(5, TimeUnit.SECONDS).orElseThrow());

        store.delete(owner, "Zz99");
        OwnerData after = store.loadOwner(owner, "Steve").get(5, TimeUnit.SECONDS);
        assertEquals(1, after.count());
        assertEquals(List.of("aB3x"), after.visible());

        store.deleteAll(owner).get(5, TimeUnit.SECONDS);
        assertEquals(0, store.loadOwner(owner, "Steve").get(5, TimeUnit.SECONDS).count());
        assertFalse(store.findByName("nobody").get(5, TimeUnit.SECONDS).isPresent());
        database.close();
    }

    @Test
    void codesAreCaseSensitivePerOwner() throws Exception {
        SqliteDatabase database = new SqliteDatabase(new File(folder, "case.db"), Logger.getLogger("test"));
        database.open();
        DataStore store = new DataStore(new SqliteWaypointRepository(database), new SqlitePlayerRepository(database),
                database.executor(), Logger.getLogger("test"));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.insert(waypoint(a, "abcd", "uno", 1L)).get(5, TimeUnit.SECONDS);
        store.insert(waypoint(a, "ABCD", "dos", 2L)).get(5, TimeUnit.SECONDS);
        store.insert(waypoint(b, "abcd", "tres", 3L)).get(5, TimeUnit.SECONDS);
        assertEquals(2, store.loadOwner(a, null).get(5, TimeUnit.SECONDS).count());
        assertEquals(1, store.loadOwner(b, null).get(5, TimeUnit.SECONDS).count());
        database.close();
    }
}
