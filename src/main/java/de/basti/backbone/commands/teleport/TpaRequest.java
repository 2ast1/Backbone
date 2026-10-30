package de.basti.backbone.commands.teleport;

import java.util.UUID;

/**
 * Repräsentiert eine ausstehende Teleportations-Anfrage (/tpa).
 */
public class TpaRequest {

    private final UUID requesterId;
    private final String requesterName;
    private final UUID targetId;
    private final String targetName;
    private final long createdAt;
    private static final long TIMEOUT_MILLIS = 60_000L; // 60 Sekunden

    public TpaRequest(UUID requesterId, String requesterName, UUID targetId, String targetName) {
        this.requesterId = requesterId;
        this.requesterName = requesterName;
        this.targetId = targetId;
        this.targetName = targetName;
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getRequesterId() {
        return requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetName() {
        return targetName;
    }

    public boolean isExpired() {
        return (System.currentTimeMillis() - createdAt) > TIMEOUT_MILLIS;
    }
}
