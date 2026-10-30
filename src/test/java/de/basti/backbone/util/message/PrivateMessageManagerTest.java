package de.basti.backbone.util.message;

import de.basti.backbone.util.DataManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PrivateMessageManagerTest {

    private UUID playerA;
    private UUID playerB;
    private UUID playerC;

    @BeforeEach
    void setUp() {
        DataManager.init(null);
        playerA = UUID.randomUUID();
        playerB = UUID.randomUUID();
        playerC = UUID.randomUUID();
        PrivateMessageManager.clearAll();
        PrivateMessageManager.setMessagingEnabled(true);
    }

    @AfterEach
    void tearDown() {
        PrivateMessageManager.clearAll();
    }

    @Test
    void testReplyTargetTracking() {
        assertNull(PrivateMessageManager.getReplyTarget(playerA));

        PrivateMessageManager.setReplyTarget(playerA, playerB);
        assertEquals(playerB, PrivateMessageManager.getReplyTarget(playerA));

        // When B replies to A
        PrivateMessageManager.setReplyTarget(playerB, playerA);
        assertEquals(playerA, PrivateMessageManager.getReplyTarget(playerB));

        // When C messages A
        PrivateMessageManager.setReplyTarget(playerA, playerC);
        assertEquals(playerC, PrivateMessageManager.getReplyTarget(playerA));
    }

    @Test
    void testConsoleTargetTracking() {
        PrivateMessageManager.setReplyTarget(playerA, PrivateMessageManager.CONSOLE_UUID);
        assertEquals(PrivateMessageManager.CONSOLE_UUID, PrivateMessageManager.getReplyTarget(playerA));
    }

    @Test
    void testTargetCleanupOnQuit() {
        PrivateMessageManager.setReplyTarget(playerA, playerB);
        PrivateMessageManager.setReplyTarget(playerB, playerA);
        PrivateMessageManager.setReplyTarget(playerC, playerB);

        // Player B quits
        PrivateMessageManager.removeReplyTarget(playerB);

        assertNull(PrivateMessageManager.getReplyTarget(playerB));
        assertNull(PrivateMessageManager.getReplyTarget(playerA));
        assertNull(PrivateMessageManager.getReplyTarget(playerC));
    }
}
