package de.basti.backbone.commands;

import de.basti.backbone.util.chat.SlowchatManager;
import de.basti.backbone.util.economy.EconomyManager;
import de.basti.backbone.util.locations.DeathManager;
import de.basti.backbone.util.message.ActionbarManager;
import org.bukkit.Location;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class NewCommandsTest {

    @BeforeEach
    @AfterEach
    void cleanup() {
        SlowchatManager.disable();
        DeathManager.clear();
        ActionbarManager.shutdown();
    }

    @Test
    void testSlowchatManager() {
        assertFalse(SlowchatManager.isSlowchatEnabled());

        SlowchatManager.enable(0);
        assertTrue(SlowchatManager.isSlowchatEnabled());
        assertTrue(SlowchatManager.isPermanent());
        assertEquals(-1, SlowchatManager.getRemainingMillis());

        SlowchatManager.disable();
        assertFalse(SlowchatManager.isSlowchatEnabled());

        // Temporary slowchat (e.g. 500ms)
        SlowchatManager.enable(500);
        assertTrue(SlowchatManager.isSlowchatEnabled());
        assertFalse(SlowchatManager.isPermanent());
        assertTrue(SlowchatManager.getRemainingMillis() > 0);

        UUID testUuid = UUID.randomUUID();
        assertEquals(0, SlowchatManager.getPlayerRemainingCooldownMillis(testUuid));
        SlowchatManager.recordChat(testUuid);
        assertTrue(SlowchatManager.getPlayerRemainingCooldownMillis(testUuid) > 0);

        SlowchatManager.clear();
        assertEquals(0, SlowchatManager.getPlayerRemainingCooldownMillis(testUuid));
    }

    @Test
    void testDeathManager() {
        UUID uuid = UUID.randomUUID();
        assertNull(DeathManager.getLastDeathLocation(null));

        Location loc = new Location(null, 100, 64, -200, 90f, 0f);
        DeathManager.setLastDeathLocation(uuid, loc);

        Location retrieved = DeathManager.getLastDeathLocation(null);
        assertNull(retrieved);

        DeathManager.clear();
    }

    @Test
    void testEconomyPayBlocking() {
        UUID testUuid = UUID.randomUUID();
        // Default should be false
        assertFalse(EconomyManager.isPayBlocked(testUuid));

        EconomyManager.setPayBlocked(testUuid, true);
        assertTrue(EconomyManager.isPayBlocked(testUuid));

        EconomyManager.setPayBlocked(testUuid, false);
        assertFalse(EconomyManager.isPayBlocked(testUuid));
    }

    @Test
    void testPluginYmlCommandsAndPermissionsExistAndDefaultToOp() throws Exception {
        File file = new File("src/main/resources/plugin.yml");
        assertTrue(file.exists());

        try (FileInputStream in = new FileInputStream(file)) {
            PluginDescriptionFile pdf = new PluginDescriptionFile(in);
            Map<String, Map<String, Object>> commands = pdf.getCommands();

            String[] expectedCommands = {
                    "slowchat", "staffchat", "chatclear", "back", "warn", "warns",
                    "kick", "heal", "feed", "speed", "craft", "trash", "skull",
                    "paytoggle", "baltop", "actionbar"
            };

            for (String cmd : expectedCommands) {
                assertTrue(commands.containsKey(cmd), "Command /" + cmd + " must be defined in plugin.yml");
            }

            String[] expectedPermissions = {
                    "backbone.slowchat", "backbone.slowchat.bypass", "backbone.staffchat",
                    "backbone.chatclear", "backbone.chatclear.bypass", "backbone.back",
                    "backbone.warn", "backbone.kick", "backbone.heal", "backbone.heal.others",
                    "backbone.feed", "backbone.feed.others", "backbone.speed", "backbone.speed.others",
                    "backbone.craft", "backbone.trash", "backbone.skull", "backbone.skull.others",
                    "backbone.pay", "backbone.pay.bypass", "backbone.money.others", "backbone.actionbar"
            };

            List<Permission> perms = pdf.getPermissions();
            for (String permName : expectedPermissions) {
                Permission found = perms.stream().filter(p -> p.getName().equalsIgnoreCase(permName)).findFirst().orElse(null);
                assertNotNull(found, "Permission " + permName + " must be declared in plugin.yml");
                assertEquals(PermissionDefault.OP, found.getDefault(), "Permission " + permName + " must default to OP");
            }
        }
    }
}
