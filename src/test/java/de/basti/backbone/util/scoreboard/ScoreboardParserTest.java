package de.basti.backbone.util.scoreboard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ScoreboardParserTest {

    @Test
    public void testBasicPlaceholdersWithoutPlayer() {
        String input = "{day}.{month}.{year} {hh}:{mm}:{ss}";
        String output = ScoreboardParser.parse(input, null);
        assertNotNull(output);
        assertFalse(output.contains("{day}"));
        assertFalse(output.contains("{year}"));
    }

    @Test
    public void testIfComparisonNumeric() {
        String inputTrue = "<if 5000 >= 1000>&aReich</if = false>&cArm</if>";
        assertEquals("§aReich", ScoreboardParser.parse(inputTrue, null));

        String inputFalse = "<if 500 >= 1000>&aReich</if = false>&cArm</if>";
        assertEquals("§cArm", ScoreboardParser.parse(inputFalse, null));
    }

    @Test
    public void testIfGermanNumberFormatting() {
        String input = "<if 1.500,50 >= 1000>&aMehr</if = false>&cWeniger</if>";
        assertEquals("§aMehr", ScoreboardParser.parse(input, null));

        String input2 = "<if 10.000,00 < 5000>&aJa</if = false>&cNein</if>";
        assertEquals("§cNein", ScoreboardParser.parse(input2, null));
    }

    @Test
    public void testIfBooleanComparison() {
        String input = "<if true = true>&aOnline</if = false>&cOffline</if>";
        assertEquals("§aOnline", ScoreboardParser.parse(input, null));

        String input2 = "<if false = true>&aMuted</if = false>&aUnmuted</if>";
        assertEquals("§aUnmuted", ScoreboardParser.parse(input2, null));
    }

    @Test
    public void testIfStringComparison() {
        String input = "<if \"world\" = \"world\">&aIn Welt</if = false>&cAndere Welt</if>";
        assertEquals("§aIn Welt", ScoreboardParser.parse(input, null));

        String input2 = "<if world = \"lobby\">&aLobby</if = false>&cKeine Lobby</if>";
        assertEquals("§cKeine Lobby", ScoreboardParser.parse(input2, null));
    }

    @Test
    public void testSwitchParsing() {
        String input = "<switch sek=10>Text1</s>Text2</s>";
        String output = ScoreboardParser.parse(input, null);
        assertTrue(output.equals("Text1") || output.equals("Text2"));
    }

    @Test
    public void testSwitchWithTrailingText() {
        String input = "<switch sek=10>A</s>B</s> &7Ende";
        String output = ScoreboardParser.parse(input, null);
        assertTrue(output.equals("A §7Ende") || output.equals("B §7Ende"));
    }

    @Test
    public void testNestedIfInSwitch() {
        String input = "<switch sek=10><if 100 > 50>&aGross</if = false>&cKlein</if></s>&eZweiter</s>";
        String output = ScoreboardParser.parse(input, null);
        assertTrue(output.equals("§aGross") || output.equals("§eZweiter"));
    }

    @Test
    public void testCentering() {
        String input = "<center>&9&lTEST</c>";
        String output = ScoreboardParser.parse(input, null);
        assertNotNull(output);
        assertFalse(output.contains("<center>"));
        assertFalse(output.contains("</c>"));
        assertTrue(output.contains("§9§lTEST"));
    }

    @Test
    public void testIfWithoutFalseBranch() {
        String inputTrue = "<if 10 > 5>&aWahr</if>";
        assertEquals("§aWahr", ScoreboardParser.parse(inputTrue, null));

        String inputFalse = "<if 5 > 10>&aWahr</if>";
        assertEquals("", ScoreboardParser.parse(inputFalse, null));
    }

    @Test
    public void testIfWithNotEqualOperator() {
        String input = "<if \"world\" != \"lobby\">&aNicht In Lobby</if = false>&cLobby</if>";
        assertEquals("§aNicht In Lobby", ScoreboardParser.parse(input, null));

        String inputNum = "<if 100 != 50>&aUngleich</if = false>&cGleich</if>";
        assertEquals("§aUngleich", ScoreboardParser.parse(inputNum, null));
    }

    @Test
    public void testIfWithElseTag() {
        String input = "<if 10 > 5>&aJa<else>&cNein</if>";
        assertEquals("§aJa", ScoreboardParser.parse(input, null));

        String inputFalse = "<if 5 > 10>&aJa<else>&cNein</if>";
        assertEquals("§cNein", ScoreboardParser.parse(inputFalse, null));

        String inputWithSlash = "<if 10 > 5>&aJa</else>&cNein</if>";
        assertEquals("§aJa", ScoreboardParser.parse(inputWithSlash, null));

        String inputWithClosingElse = "<if 5 > 10>&aJa<else>&cNein</else></if>";
        assertEquals("§cNein", ScoreboardParser.parse(inputWithClosingElse, null));
    }

    @Test
    public void testIfWorldNameElseScenario() {
        // Fall 1: Weltname stimmt nicht überein und es gibt ein <else>
        String inputWithElse = "<if \"XY\" = \"TTt\">&aWelt ist TTt<else>&cWelt ist anders: XY</if>";
        assertEquals("§cWelt ist anders: XY", ScoreboardParser.parse(inputWithElse, null));

        // Fall 2: Weltname stimmt nicht überein und es gibt KEIN <else> -> leerer String ("")
        String inputWithoutElse = "<if \"XY\" = \"TTt\">&aWelt ist TTt</if>";
        assertEquals("", ScoreboardParser.parse(inputWithoutElse, null));

        // Fall 3: Verschachteltes Else-If
        String nested = "<if \"XY\" = \"TTt\">&aTTt<else><if \"XY\" = \"lobby\">&bLobby<else>&7Andere Welt</if></if>";
        assertEquals("§7Andere Welt", ScoreboardParser.parse(nested, null));
    }

    @Test
    public void testSwitchInsideIf() {
        String input = "<if 10 > 5><switch sek=10>&aA</s>&bB</s></if = false>&cNein</if>";
        String output = ScoreboardParser.parse(input, null);
        assertTrue(output.equals("§aA") || output.equals("§bB"));
    }

    @Test
    public void testCpuAndSystemPlaceholders() {
        String input = "CPU: {cpu}% System: {cpu_system}% Uptime: {uptime}";
        String output = ScoreboardParser.parse(input, null);
        assertNotNull(output);
        assertFalse(output.contains("{cpu}"));
        assertFalse(output.contains("{cpu_system}"));
        assertFalse(output.contains("{uptime}"));

        // Test, dass {cpu} auch im <if>-Vergleich sauber numerisch ausgewertet wird
        String ifCpu = "<if {cpu} >= 0>&aOK<else>&cFehler</if>";
        assertEquals("§aOK", ScoreboardParser.parse(ifCpu, null));
    }

    @Test
    public void testAdditionalPlaceholdersWithoutPlayer() {
        String input = "Ping: {ping} Health: {health}/{maxhealth} Food: {food} Level: {level} Online: {online}/{maxplayers}";
        String output = ScoreboardParser.parse(input, null);
        assertNotNull(output);
        assertFalse(output.contains("{ping}"));
        assertFalse(output.contains("{health}"));
        assertFalse(output.contains("{food}"));
        assertFalse(output.contains("{level}"));
        assertFalse(output.contains("{online}"));
        assertFalse(output.contains("{maxplayers}"));
    }
}
