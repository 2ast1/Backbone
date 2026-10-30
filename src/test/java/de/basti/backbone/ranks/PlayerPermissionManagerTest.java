package de.basti.backbone.ranks;

import de.basti.backbone.util.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerPermissionManagerTest {

    private UUID testUuid;

    @BeforeEach
    void setUp() {
        DataManager.init(null);
        testUuid = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        DataManager.set("", "playerdata.yml", testUuid.toString(), null);
    }

    @Test
    void testGlobalPermissionCrud() {
        assertTrue(PlayerPermissionManager.getGlobalPermissions(testUuid).isEmpty());

        assertTrue(PlayerPermissionManager.addPermission(testUuid, "backbone.fly", null));
        // Duplicate should return false
        assertFalse(PlayerPermissionManager.addPermission(testUuid, "backbone.fly", null));

        List<String> perms = PlayerPermissionManager.getGlobalPermissions(testUuid);
        assertEquals(1, perms.size());
        assertTrue(perms.contains("backbone.fly"));

        assertTrue(PlayerPermissionManager.removePermission(testUuid, "backbone.fly", null));
        assertFalse(PlayerPermissionManager.removePermission(testUuid, "backbone.fly", null));
        assertTrue(PlayerPermissionManager.getGlobalPermissions(testUuid).isEmpty());
    }

    @Test
    void testWorldPermissionCrud() {
        assertTrue(PlayerPermissionManager.addPermission(testUuid, "backbone.warp", List.of("nether", "end")));

        List<String> netherPerms = PlayerPermissionManager.getPermissionsForWorld(testUuid, "nether");
        assertEquals(1, netherPerms.size());
        assertTrue(netherPerms.contains("backbone.warp"));

        List<String> worldPerms = PlayerPermissionManager.getPermissionsForWorld(testUuid, "world");
        assertTrue(worldPerms.isEmpty());

        Map<String, List<String>> allWorlds = PlayerPermissionManager.getWorldPermissions(testUuid);
        assertEquals(2, allWorlds.size());
        assertTrue(allWorlds.containsKey("nether"));
        assertTrue(allWorlds.containsKey("end"));

        assertTrue(PlayerPermissionManager.removePermission(testUuid, "backbone.warp", List.of("nether")));
        assertTrue(PlayerPermissionManager.getPermissionsForWorld(testUuid, "nether").isEmpty());
        assertEquals(1, PlayerPermissionManager.getPermissionsForWorld(testUuid, "end").size());
    }

    @Test
    void testResetPermissions() {
        PlayerPermissionManager.addPermission(testUuid, "backbone.fly", null);
        PlayerPermissionManager.addPermission(testUuid, "backbone.warp", List.of("nether"));

        PlayerPermissionManager.resetPermissions(testUuid);
        assertTrue(PlayerPermissionManager.getGlobalPermissions(testUuid).isEmpty());
        assertTrue(PlayerPermissionManager.getWorldPermissions(testUuid).isEmpty());
    }

    @Test
    void testResetAll() {
        PlayerPermissionManager.addPermission(testUuid, "backbone.fly", null);
        DataManager.set("", "playerdata.yml", testUuid + ".rank", "vip");

        PlayerPermissionManager.resetAll(testUuid);
        assertTrue(PlayerPermissionManager.getGlobalPermissions(testUuid).isEmpty());
        assertNull(DataManager.getString("", "playerdata.yml", testUuid + ".rank"));
    }
}
