package de.basti.backbone.ranks;

import de.basti.backbone.util.DataManager;
import de.basti.backbone.util.DurationUtil;
import de.basti.backbone.util.Lang;
import de.basti.backbone.util.economy.EconomyManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TemporaryRankTest {

    private UUID testUuid;

    @BeforeEach
    void setUp() {
        DataManager.init(null);
        RankManager.init(null);
        RankShop.setShopEnabled(true);
        testUuid = UUID.randomUUID();
    }

    @AfterEach
    void tearDown() {
        DataManager.set("", "playerdata.yml", testUuid.toString(), null);
        RankManager.deleteRank("member");
        RankManager.deleteRank("vip");
        RankManager.deleteRank("admin");
    }

    @Test
    void testRankDefaultDuration() {
        long thirtyDays = DurationUtil.parseDuration("30d");
        Rank vip = RankManager.createRank("vip", "&6", thirtyDays, "&6[VIP] ");
        assertEquals(thirtyDays, vip.getDefaultDuration());
        assertFalse(vip.isDefaultPermanent());

        RankManager.setPlayerRank(testUuid, "vip");
        assertFalse(RankManager.isPlayerRankPermanent(testUuid));
        long rem = RankManager.getPlayerRemainingMillis(testUuid);
        assertTrue(rem > 0 && rem <= thirtyDays);
    }

    @Test
    void testDurationOverride() {
        Rank vip = RankManager.createRank("vip", "&6", DurationUtil.parseDuration("30d"), "&6[VIP] ");

        // Override mit 7 Tagen
        long sevenDays = DurationUtil.parseDuration("7d");
        RankManager.setPlayerRank(testUuid, "vip", sevenDays);
        long rem = RankManager.getPlayerRemainingMillis(testUuid);
        assertTrue(rem > 0 && rem <= sevenDays);

        // Override mit Permanent (-1)
        RankManager.setPlayerRank(testUuid, "vip", -1L);
        assertTrue(RankManager.isPlayerRankPermanent(testUuid));
        assertEquals(-1L, RankManager.getPlayerRemainingMillis(testUuid));
    }

    @Test
    void testExpirationAndPermanentFallback() {
        Rank member = RankManager.createRank("member", "&7", "");
        RankManager.setDefaultRank("member");

        Rank vip = RankManager.createRank("vip", "&6", "&6[VIP] ");
        Rank admin = RankManager.createRank("admin", "&c", "&c[Admin] ");

        // 1. Spieler hat permanenten VIP-Rang
        RankManager.setPlayerRank(testUuid, "vip", -1L);
        assertEquals("vip", RankManager.getRankOfPlayer(testUuid).getName());
        assertTrue(RankManager.isPlayerRankPermanent(testUuid));

        // 2. Spieler erhält temporären Admin-Rang für 10 Sekunden
        RankManager.setPlayerRank(testUuid, "admin", 10_000L);
        assertEquals("admin", RankManager.getRankOfPlayer(testUuid).getName());
        assertEquals("vip", RankManager.getPlayerFallbackRankName(testUuid));

        // 3. Ablaufzeit künstlich in die Vergangenheit setzen
        DataManager.set("", "playerdata.yml", testUuid + ".rank_expires", System.currentTimeMillis() - 1000L);

        // 4. Abruf prüft und wickelt Expiration ab -> Fallback auf permanenten VIP-Rang!
        Rank fallbackResult = RankManager.getRankOfPlayer(testUuid);
        assertEquals("vip", fallbackResult.getName());
        assertTrue(RankManager.isPlayerRankPermanent(testUuid));
        assertNull(RankManager.getPlayerFallbackRankName(testUuid));
    }

    @Test
    void testDurationExtensionOnReassignment() {
        Rank vip = RankManager.createRank("vip", "&6", 10_000L, "&6[VIP] ");

        RankManager.setPlayerRank(testUuid, "vip", 10_000L);
        long firstExpiry = RankManager.getPlayerExpiresAt(testUuid);

        // Nochmaliger Aufruf verlängert die Dauer!
        RankManager.setPlayerRank(testUuid, "vip", 15_000L);
        long secondExpiry = RankManager.getPlayerExpiresAt(testUuid);

        assertEquals(firstExpiry + 15_000L, secondExpiry);
    }

    @Test
    void testRankChainAndPromoteDemote() {
        Rank member = RankManager.createRank("member", "&7", "");
        Rank vip = RankManager.createRank("vip", "&6", "&6[VIP] ");
        Rank admin = RankManager.createRank("admin", "&c", "&c[Admin] ");

        // Chain aufbauen: Member -> VIP -> Admin
        RankManager.setRankNext("member", "vip");
        RankManager.setRankPrevious("vip", "member");
        RankManager.setRankNext("vip", "admin");
        RankManager.setRankPrevious("admin", "vip");

        RankManager.setDefaultRank("member");
        assertEquals("member", RankManager.getRankOfPlayer(testUuid).getName());

        // Promote: Member -> VIP
        assertTrue(RankManager.promotePlayer(testUuid, null));
        assertEquals("vip", RankManager.getRankOfPlayer(testUuid).getName());

        // Promote: VIP -> Admin
        assertTrue(RankManager.promotePlayer(testUuid, null));
        assertEquals("admin", RankManager.getRankOfPlayer(testUuid).getName());

        // Promote am Ende der Kette schlägt fehl
        assertFalse(RankManager.promotePlayer(testUuid, null));

        // Demote: Admin -> VIP
        assertTrue(RankManager.demotePlayer(testUuid, null));
        assertEquals("vip", RankManager.getRankOfPlayer(testUuid).getName());

        // Demote: VIP -> Member
        assertTrue(RankManager.demotePlayer(testUuid, null));
        assertEquals("member", RankManager.getRankOfPlayer(testUuid).getName());

        // Demote am Anfang der Kette schlägt fehl
        assertFalse(RankManager.demotePlayer(testUuid, null));
    }

    @Test
    void testRankShopMultiOffersAndChainProtection() {
        Rank member = RankManager.createRank("member", "&7", "");
        RankManager.setDefaultRank("member");

        Rank vip = RankManager.createRank("vip", "&6", "&6[VIP] ");
        Rank admin = RankManager.createRank("admin", "&c", "&c[Admin] ");

        RankManager.setRankNext("vip", "admin");
        RankManager.setRankPrevious("admin", "vip");

        // VIP Angebote: 30d für 500, 3mo für 1000, perm für 2000
        RankManager.addOffer("vip", new RankOffer(500.0, DurationUtil.parseDuration("30d")));
        RankManager.addOffer("vip", new RankOffer(1000.0, DurationUtil.parseDuration("3mo")));
        RankManager.addOffer("vip", new RankOffer(2000.0, -1L));

        EconomyManager.setBalance(testUuid, 10_000.0);

        // Angebot #1 kaufen (30 Tage)
        PurchaseResult res1 = RankShop.purchase(testUuid, "vip", 0);
        assertTrue(res1.isSuccess());
        assertEquals("vip", RankManager.getRankOfPlayer(testUuid).getName());
        assertFalse(RankManager.isPlayerRankPermanent(testUuid));

        // Gleiches Angebot nochmal kaufen -> verlängert die Laufzeit!
        long expBefore = RankManager.getPlayerExpiresAt(testUuid);
        PurchaseResult resExtend = RankShop.purchase(testUuid, "vip", 0);
        assertTrue(resExtend.isSuccess());
        assertTrue(resExtend.isExtendedDuration());
        assertEquals(expBefore + DurationUtil.parseDuration("30d"), RankManager.getPlayerExpiresAt(testUuid));

        // Spieler auf Admin setzen
        RankManager.setPlayerRank(testUuid, "admin", -1L);
        assertEquals("admin", RankManager.getRankOfPlayer(testUuid).getName());

        // Versuch, als Admin den niedrigeren VIP-Rang zu kaufen -> Kettenschutz blockiert!
        PurchaseResult failDowngrade = RankShop.purchase(testUuid, "vip", 0);
        assertFalse(failDowngrade.isSuccess());
        assertEquals(PurchaseResult.Status.CANNOT_DOWNGRADE, failDowngrade.getStatus());
    }
}
