package de.basti.backbone.ranks;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class PermissionResolverTest {

    private static final List<String> KNOWN_PERMS = List.of(
            "backbone.fly",
            "backbone.fly.other",
            "backbone.warp",
            "backbone.warp.set",
            "backbone.admin",
            "minecraft.command.gamemode"
    );

    @Test
    void testBasicPermissionGrant() {
        List<Collection<String>> layers = List.of(
                List.of("backbone.fly")
        );
        Map<String, Boolean> effective = PermissionResolver.resolve(layers, KNOWN_PERMS);
        assertTrue(PermissionResolver.hasPermission(effective, "backbone.fly"));
        assertFalse(PermissionResolver.hasPermission(effective, "backbone.warp"));
    }

    @Test
    void testWildcardExpansion() {
        List<Collection<String>> layers = List.of(
                List.of("backbone.fly.*")
        );
        Map<String, Boolean> effective = PermissionResolver.resolve(layers, KNOWN_PERMS);
        assertTrue(PermissionResolver.hasPermission(effective, "backbone.fly.other"));
        assertFalse(PermissionResolver.hasPermission(effective, "backbone.warp"));
    }

    @Test
    void testNegativeOverrideInSameLayer() {
        List<Collection<String>> layers = List.of(
                List.of("backbone.fly.*", "-backbone.fly.other")
        );
        Map<String, Boolean> effective = PermissionResolver.resolve(layers, KNOWN_PERMS);
        assertFalse(PermissionResolver.hasPermission(effective, "backbone.fly.other"));
    }

    @Test
    void testLayerHierarchyPrecedence() {
        // Layer 1: Rank global -> grants fly.*
        // Layer 2: Rank world -> denies fly.other
        // Layer 3: Player global -> regrants fly.other
        // Layer 4: Player world -> denies fly.other
        List<Collection<String>> layers = List.of(
                List.of("backbone.fly.*"),
                List.of("-backbone.fly.other"),
                List.of("backbone.fly.other"),
                List.of("-backbone.fly.other")
        );
        Map<String, Boolean> effective = PermissionResolver.resolve(layers, KNOWN_PERMS);
        assertFalse(PermissionResolver.hasPermission(effective, "backbone.fly.other"));

        // If Layer 4 didn't deny it, Layer 3 would override Layer 2
        List<Collection<String>> layersWithoutL4 = List.of(
                List.of("backbone.fly.*"),
                List.of("-backbone.fly.other"),
                List.of("backbone.fly.other")
        );
        Map<String, Boolean> effectiveWithoutL4 = PermissionResolver.resolve(layersWithoutL4, KNOWN_PERMS);
        assertTrue(PermissionResolver.hasPermission(effectiveWithoutL4, "backbone.fly.other"));
    }

    @Test
    void testFullWildcard() {
        List<Collection<String>> layers = List.of(
                List.of("*", "-backbone.admin")
        );
        Map<String, Boolean> effective = PermissionResolver.resolve(layers, KNOWN_PERMS);
        assertTrue(PermissionResolver.hasPermission(effective, "backbone.fly"));
        assertTrue(PermissionResolver.hasPermission(effective, "minecraft.command.gamemode"));
        assertFalse(PermissionResolver.hasPermission(effective, "backbone.admin"));
    }
}
