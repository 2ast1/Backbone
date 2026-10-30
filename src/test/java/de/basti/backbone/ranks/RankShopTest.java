package de.basti.backbone.ranks;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.economy.EconomyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RankShopTest {

    private UUID testUuid;

    @BeforeEach
    void setUp() {
        DataManager.init(null);
        RankManager.init(null);
        testUuid = UUID.randomUUID();
        RankShop.setShopEnabled(true);
        RankManager.createRank("member", "Member", "&7", "");
    }

    @AfterEach
    void tearDown() {
        DataManager.set("", "playerdata.yml", testUuid.toString(), null);
        RankManager.deleteRank("member");
        RankManager.deleteRank("vip");
        RankManager.deleteRank("premium");
        RankManager.deleteRank("unbuyable");
    }

    @Test
    void testRankShopPurchaseFlow() {
        RankManager.createRank("vip", "VIP", "&6", "&6[VIP] ");
        RankManager.setRankPrice("vip", 100.0);

        EconomyManager.setBalance(testUuid, 50.0);

        PurchaseResult failResult = RankShop.purchase(testUuid, "vip");
        assertFalse(failResult.isSuccess());
        assertEquals(PurchaseResult.Status.INSUFFICIENT_FUNDS, failResult.getStatus());

        EconomyManager.setBalance(testUuid, 150.0);
        PurchaseResult okResult = RankShop.purchase(testUuid, "vip");
        assertTrue(okResult.isSuccess());
        assertEquals(50.0, EconomyManager.getBalance(testUuid), 0.001);
        assertEquals("vip", RankManager.getPlayerAssignedRankId(testUuid));

        // Already owned check
        PurchaseResult alreadyOwned = RankShop.purchase(testUuid, "vip");
        assertFalse(alreadyOwned.isSuccess());
        assertEquals(PurchaseResult.Status.ALREADY_OWNS_RANK, alreadyOwned.getStatus());
    }

    @Test
    void testUpgradeDifferenceMode() {
        RankManager.createRank("vip", "VIP", "&6", "&6[VIP] ");
        RankManager.setRankPrice("vip", 100.0);

        RankManager.createRank("premium", "Premium", "&b", "&b[Premium] ");
        RankManager.setRankPrice("premium", 250.0);

        DataManager.setConfig("ranks.buy.upgrade-mode", "DIFFERENCE");

        RankManager.setPlayerRank(testUuid, "vip");
        assertEquals(150.0, RankShop.getPriceForPlayer(testUuid, RankManager.getRank("premium")), 0.001);

        DataManager.setConfig("ranks.buy.upgrade-mode", "FULL");
        assertEquals(250.0, RankShop.getPriceForPlayer(testUuid, RankManager.getRank("premium")), 0.001);
    }
}
