package de.basti.backbone.ranks;

import java.util.*;

/**
 * Pure POJO resolver for calculating effective permissions across hierarchical layers.
 * Layer priority (later layers override earlier ones):
 * 1. Rank global permissions
 * 2. Rank world-specific permissions
 * 3. Player global individual permissions
 * 4. Player world-specific individual permissions
 *
 * Within each layer:
 * - Pass 1: Positive permissions & positive wildcards (*, prefix.*)
 * - Pass 2: Negative permissions & negative wildcards (-*, -prefix.*)
 */
public final class PermissionResolver {

    private PermissionResolver() {}

    /**
     * Resolves the effective permission map (permission -> boolean) by applying each layer in order.
     *
     * @param layers Ordered list of permission collections (layer 1 to layer N).
     * @param knownPermissions Known server/plugin permissions used to expand wildcards.
     * @return Map of effective permissions and their boolean values (true = granted, false = denied).
     */
    public static Map<String, Boolean> resolve(List<Collection<String>> layers, Collection<String> knownPermissions) {
        Map<String, Boolean> effective = new LinkedHashMap<>();
        if (layers == null || layers.isEmpty()) {
            return effective;
        }

        Set<String> knownLower = new HashSet<>();
        if (knownPermissions != null) {
            for (String kp : knownPermissions) {
                if (kp != null && !kp.isBlank()) {
                    knownLower.add(kp.trim().toLowerCase(Locale.ROOT));
                }
            }
        }

        for (Collection<String> layer : layers) {
            if (layer == null || layer.isEmpty()) continue;

            // Pass 1: Positive permissions & positive wildcards
            for (String raw : layer) {
                if (raw == null || raw.isBlank()) continue;
                String perm = raw.trim();
                if (perm.startsWith("-")) continue;

                String lower = perm.toLowerCase(Locale.ROOT);
                if (lower.equals("*")) {
                    effective.put("*", true);
                    effective.put("backbone.*", true);
                    for (String kp : knownLower) {
                        effective.put(kp, true);
                    }
                } else if (lower.endsWith(".*")) {
                    String base = lower.substring(0, lower.length() - 2);
                    effective.put(lower, true);
                    for (String kp : knownLower) {
                        if (kp.startsWith(base + ".")) {
                            effective.put(kp, true);
                        }
                    }
                } else {
                    effective.put(lower, true);
                }
            }

            // Pass 2: Negative permissions & negative wildcards (strictly override positive in same or earlier layers)
            for (String raw : layer) {
                if (raw == null || raw.isBlank()) continue;
                String perm = raw.trim();
                if (!perm.startsWith("-")) continue;

                String actual = perm.substring(1).trim().toLowerCase(Locale.ROOT);
                if (actual.isEmpty()) continue;

                if (actual.equals("*")) {
                    effective.put("*", false);
                    effective.put("backbone.*", false);
                    for (String kp : knownLower) {
                        effective.put(kp, false);
                    }
                } else if (actual.endsWith(".*")) {
                    String base = actual.substring(0, actual.length() - 2);
                    effective.put(actual, false);
                    for (String kp : knownLower) {
                        if (kp.startsWith(base + ".")) {
                            effective.put(kp, false);
                        }
                    }
                } else {
                    effective.put(actual, false);
                }
            }
        }

        return effective;
    }

    /**
     * Checks if a target permission is granted according to the resolved effective map.
     */
    public static boolean hasPermission(Map<String, Boolean> effective, String targetPermission) {
        if (effective == null || targetPermission == null || targetPermission.isBlank()) {
            return false;
        }

        String target = targetPermission.trim().toLowerCase(Locale.ROOT);

        // 1. Direct match has highest precedence
        Boolean direct = effective.get(target);
        if (direct != null) {
            return direct;
        }

        // 2. Check wildcard prefixes (e.g. "backbone.rank.*" for "backbone.rank.set")
        String current = target;
        while (current.contains(".")) {
            int lastDot = current.lastIndexOf('.');
            current = current.substring(0, lastDot);
            String wildcard = current + ".*";
            Boolean wildcardVal = effective.get(wildcard);
            if (wildcardVal != null) {
                return wildcardVal;
            }
        }

        // 3. Full wildcard "*"
        Boolean fullWildcard = effective.get("*");
        if (fullWildcard != null) {
            return fullWildcard;
        }

        return false;
    }
}
