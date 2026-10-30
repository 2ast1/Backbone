package de.basti.backbone.util.automod.chat.filter;

import de.basti.backbone.util.Lang;
import de.basti.backbone.util.automod.core.AutoMod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Überprüft Nachrichten auf unerlaubte Werbung (Discord-Einladungen, Web-Domains, Server-IPs).
 * Reagiert dynamisch auf die Einstellungen in der automod/config.yml.
 */
public class AdvertisingFilter {

    // Erkennt Discord-Einladungen (z.B. discord.gg/xyz, discord.com/invite/xyz)
    private static final Pattern DISCORD_INVITE_PATTERN = Pattern.compile(
            "(?i)\\b(?:https?://)?(?:www\\.)?(?:discord\\.(?:gg|io|me|li)|discord(?:app)?\\.com/invite)/\\S+\\b"
    );

    // Erkennt allgemeine Links und Domains (z.B. https://..., www..., beispiel.de)
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
            "(?i)\\b((https?://|www\\.)\\S+|[a-z0-9.-]+\\.(de|com|net|org|eu|gg|me|tv|xyz|io|ch|at)(:\\d{2,5})?(/\\S*)?)\\b"
    );

    // Erkennt IPv4-Adressen (z.B. 123.45.67.89 oder 123.45.67.89:25565)
    private static final Pattern IP_PATTERN = Pattern.compile(
            "\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{2,5})?\\b"
    );

    public record AdvertisingCheckResult(boolean detected, List<String> findings, int strikes) {}

    /**
     * Prüft die Nachricht auf Fremdwerbung.
     *
     * @param message Die Originalnachricht
     * @return Ein {@link AdvertisingCheckResult} mit den Funden
     */
    public static AdvertisingCheckResult check(String message) {
        if (message == null || message.isBlank()) {
            return new AdvertisingCheckResult(false, List.of(), 0);
        }

        List<String> findings = new ArrayList<>();
        int strikes = 0;

        // 1. Discord-Einladungen prüfen (settings.chat.block-discord-invites)
        if (!AutoMod.areDiscordInvitesAllowed()) {
            if (DISCORD_INVITE_PATTERN.matcher(message).find()) {
                findings.add(Lang.get("automod.filter.ad.discord"));
                strikes = 1;
            }
        }

        // 2. Domains / Web-Links prüfen (settings.chat.block-domains)
        // Wichtig: Falls Discord-Invites erlaubt sind, entfernen wir Discord-Links vorab aus dem
        // Test-String, damit "discord.gg/..." nicht fälschlicherweise als normale ".gg"-Domain geblockt wird!
        if (!AutoMod.areDomainsAllowed()) {
            String messageForDomainCheck = DISCORD_INVITE_PATTERN.matcher(message).replaceAll("");
            if (DOMAIN_PATTERN.matcher(messageForDomainCheck).find()) {
                findings.add(Lang.get("automod.filter.ad.domain"));
                strikes = 1;
            }
        }

        // 3. Zahlen-IPs prüfen (settings.chat.block-ips)
        if (!AutoMod.areIpsAllowed()) {
            if (IP_PATTERN.matcher(message).find()) {
                findings.add(Lang.get("automod.filter.ad.ip"));
                strikes = 1;
            }
        }

        return new AdvertisingCheckResult(!findings.isEmpty(), findings, strikes);
    }
}
