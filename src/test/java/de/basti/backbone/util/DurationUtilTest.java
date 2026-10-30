package de.basti.backbone.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DurationUtilTest {

    @BeforeAll
    static void init() {
        DataManager.init(null);
    }

    @Test
    void testIsPermanent() {
        assertTrue(DurationUtil.isPermanent("perm"));
        assertTrue(DurationUtil.isPermanent("PERM"));
        assertTrue(DurationUtil.isPermanent("permanent"));
        assertTrue(DurationUtil.isPermanent("-1"));
        assertFalse(DurationUtil.isPermanent("30d"));
        assertFalse(DurationUtil.isPermanent(null));
        assertFalse(DurationUtil.isPermanent(""));
    }

    @Test
    void testIsDurationSyntax() {
        assertTrue(DurationUtil.isDurationSyntax("30s"));
        assertTrue(DurationUtil.isDurationSyntax("10m"));
        assertTrue(DurationUtil.isDurationSyntax("2h"));
        assertTrue(DurationUtil.isDurationSyntax("1d"));
        assertTrue(DurationUtil.isDurationSyntax("2w"));
        assertTrue(DurationUtil.isDurationSyntax("3mo"));
        assertTrue(DurationUtil.isDurationSyntax("1y"));
        assertTrue(DurationUtil.isDurationSyntax("1y6mo3d12h30m10s"));

        assertFalse(DurationUtil.isDurationSyntax("perm"));
        assertFalse(DurationUtil.isDurationSyntax("3d-Model"));
        assertFalse(DurationUtil.isDurationSyntax("abc"));
        assertFalse(DurationUtil.isDurationSyntax(""));
        assertFalse(DurationUtil.isDurationSyntax(null));
    }

    @Test
    void testParseDuration() {
        assertEquals(30 * 1000L, DurationUtil.parseDuration("30s"));
        assertEquals(15 * 60 * 1000L, DurationUtil.parseDuration("15m"));
        assertEquals(2 * 3600 * 1000L, DurationUtil.parseDuration("2h"));
        assertEquals(24 * 3600 * 1000L, DurationUtil.parseDuration("1d"));
        assertEquals(7 * 24 * 3600 * 1000L, DurationUtil.parseDuration("1w"));
        assertEquals(30L * 24 * 3600 * 1000L, DurationUtil.parseDuration("1mo"));
        assertEquals(365L * 24 * 3600 * 1000L, DurationUtil.parseDuration("1y"));

        // Combined duration
        long expected = (365L * 24 * 3600 * 1000L) + (30L * 24 * 3600 * 1000L) + (24 * 3600 * 1000L);
        assertEquals(expected, DurationUtil.parseDuration("1y1mo1d"));

        // Invalid
        assertEquals(0L, DurationUtil.parseDuration("invalid"));
        assertEquals(0L, DurationUtil.parseDuration(""));
        assertEquals(0L, DurationUtil.parseDuration(null));
    }

    @Test
    void testFormatDuration() {
        String sec = DurationUtil.formatDuration(30 * 1000L);
        assertTrue(sec.startsWith("30 "));
        String day = DurationUtil.formatDuration(24 * 3600 * 1000L);
        assertTrue(day.startsWith("1 "));
        String month = DurationUtil.formatDuration(30L * 24 * 3600 * 1000L);
        assertTrue(month.startsWith("1 "));
        String year = DurationUtil.formatDuration(365L * 24 * 3600 * 1000L);
        assertTrue(year.startsWith("1 "));
        assertNotNull(DurationUtil.formatDuration(0));
    }
}
