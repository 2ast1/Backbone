package de.basti.backbone.util.vault;

import de.basti.backbone.ranks.RankManager;
import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.PlayerResolver;
import de.basti.backbone.util.PrefixManager;
import de.basti.backbone.util.economy.EconomyManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VaultIntegrationTest {

    private UUID testUuid;
    private String testName;

    @BeforeEach
    void setUp() {
        DataManager.init(null);
        RankManager.init(null);
        testUuid = UUID.randomUUID();
        testName = "VaultTester";
        PlayerResolver.recordPlayer(testUuid, testName);
    }

    @AfterEach
    void tearDown() {
        DataManager.set("", "playerdata.yml", testUuid.toString(), null);
        RankManager.deleteRank("vip");
    }

    @Test
    void testVaultEconomyProvider() {
        BackboneVaultEconomy eco = new BackboneVaultEconomy();
        assertEquals("Backbone", eco.getName());
        assertFalse(eco.hasBankSupport());
        assertEquals(2, eco.fractionalDigits());

        EconomyManager.setBalance(testUuid, 500.0);
        assertTrue(eco.hasAccount(testName));
        assertEquals(500.0, eco.getBalance(testName), 0.001);
        assertTrue(eco.has(testName, 250.0));
        assertFalse(eco.has(testName, 750.0));

        EconomyResponse depositRes = eco.depositPlayer(testName, 150.5);
        assertTrue(depositRes.transactionSuccess());
        assertEquals(650.5, eco.getBalance(testName), 0.001);

        EconomyResponse withdrawRes = eco.withdrawPlayer(testName, 200.5);
        assertTrue(withdrawRes.transactionSuccess());
        assertEquals(450.0, eco.getBalance(testName), 0.001);

        EconomyResponse failWithdraw = eco.withdrawPlayer(testName, 1000.0);
        assertFalse(failWithdraw.transactionSuccess());
        assertEquals(450.0, eco.getBalance(testName), 0.001);
    }

    @Test
    void testVaultPermissionAndChatProviders() {
        BackboneVaultPermission perm = new BackboneVaultPermission(null);
        BackboneVaultChat chat = new BackboneVaultChat(perm);

        RankManager.createRank("vip", "VIP", "&6", "&6[VIP] ");
        RankManager.addPermission("vip", "backbone.fly");

        assertTrue(perm.groupHas((String) null, "vip", "backbone.fly"));
        assertTrue(perm.playerAddGroup((String) null, testName, "vip"));
        assertEquals("vip", perm.getPrimaryGroup((String) null, testName));
        assertTrue(perm.playerInGroup((String) null, testName, "vip"));
        assertTrue(perm.playerHas((String) null, testName, "backbone.fly"));

        assertEquals("§6[VIP] ", chat.getGroupPrefix((String) null, "vip"));

        chat.setPlayerPrefix((String) null, testName, "&c[Hero]");
        assertEquals("§c[Hero]", chat.getPlayerPrefix((String) null, testName));

        chat.setPlayerPrefix((String) null, testName, "");
        assertFalse(PrefixManager.hasPrefix(testUuid) && DataManager.contains("", "playerdata.yml", testUuid + ".prefix"));
    }
}
