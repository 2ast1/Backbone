package de.basti.backbone.util.inventory;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Sicherer, leichtgewichtiger NBT-Parser ohne NMS-Abhängigkeiten.
 * Ermöglicht das direkte Einlesen von &lt;world&gt;/playerdata/&lt;uuid&gt;.dat Dateien,
 * falls der Spieler noch nie während der aktuellen Backbone-Laufzeit online war.
 */
public class NbtReader {

    private static final byte TAG_END = 0;
    private static final byte TAG_BYTE = 1;
    private static final byte TAG_SHORT = 2;
    private static final byte TAG_INT = 3;
    private static final byte TAG_LONG = 4;
    private static final byte TAG_FLOAT = 5;
    private static final byte TAG_DOUBLE = 6;
    private static final byte TAG_BYTE_ARRAY = 7;
    private static final byte TAG_STRING = 8;
    private static final byte TAG_LIST = 9;
    private static final byte TAG_COMPOUND = 10;
    private static final byte TAG_INT_ARRAY = 11;
    private static final byte TAG_LONG_ARRAY = 12;

    /**
     * Liest die OfflinePlayerData aus einer vanilla playerdata .dat Datei.
     */
    public static OfflinePlayerData readFromFile(File datFile) {
        if (datFile == null || !datFile.exists() || !datFile.isFile()) {
            return null;
        }

        try (InputStream fileIn = new FileInputStream(datFile);
             InputStream gzipIn = new GZIPInputStream(fileIn);
             DataInputStream in = new DataInputStream(gzipIn)) {

            byte rootType = in.readByte();
            if (rootType != TAG_COMPOUND) {
                return null;
            }
            readString(in); // Name des Root-Tags (meist leer "")

            Map<String, Object> root = readCompoundPayload(in);
            return parsePlayerData(root);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static OfflinePlayerData parsePlayerData(Map<String, Object> root) {
        ItemStack[] storage = new ItemStack[36];
        ItemStack[] armor = new ItemStack[4]; // 0=boots, 1=leggings, 2=chestplate, 3=helmet
        ItemStack offhand = null;
        ItemStack[] enderChest = new ItemStack[27];

        // 1. Inventar parsen
        Object invObj = root.get("Inventory");
        if (invObj instanceof List<?> list) {
            for (Object elem : list) {
                if (!(elem instanceof Map<?, ?> map)) continue;
                Map<String, Object> itemMap = (Map<String, Object>) map;

                int slot = getInt(itemMap.get("Slot"), -999);
                ItemStack stack = parseItemStack(itemMap);
                if (stack == null) continue;

                if (slot >= 0 && slot < 36) {
                    storage[slot] = stack;
                } else if (slot == 100) {
                    armor[0] = stack; // Boots
                } else if (slot == 101) {
                    armor[1] = stack; // Leggings
                } else if (slot == 102) {
                    armor[2] = stack; // Chestplate
                } else if (slot == 103) {
                    armor[3] = stack; // Helmet
                } else if (slot == -106) {
                    offhand = stack; // Offhand / Schildhand
                }
            }
        }

        // 1b. Modernes Equipment-Compound parsen (Minecraft 1.20.5+ / 26.x)
        Object equipObj = root.get("equipment");
        if (equipObj instanceof Map<?, ?> equipMap) {
            if (equipMap.get("feet") instanceof Map<?, ?> feetMap) {
                ItemStack feet = parseItemStack((Map<String, Object>) feetMap);
                if (feet != null) armor[0] = feet;
            }
            if (equipMap.get("legs") instanceof Map<?, ?> legsMap) {
                ItemStack legs = parseItemStack((Map<String, Object>) legsMap);
                if (legs != null) armor[1] = legs;
            }
            if (equipMap.get("chest") instanceof Map<?, ?> chestMap) {
                ItemStack chest = parseItemStack((Map<String, Object>) chestMap);
                if (chest != null) armor[2] = chest;
            }
            if (equipMap.get("head") instanceof Map<?, ?> headMap) {
                ItemStack head = parseItemStack((Map<String, Object>) headMap);
                if (head != null) armor[3] = head;
            }
            if (equipMap.get("offhand") instanceof Map<?, ?> offhandMap) {
                ItemStack off = parseItemStack((Map<String, Object>) offhandMap);
                if (off != null) offhand = off;
            }
        }

        // 2. Enderchest parsen
        Object enderObj = root.get("EnderItems");
        if (enderObj instanceof List<?> list) {
            for (Object elem : list) {
                if (!(elem instanceof Map<?, ?> map)) continue;
                Map<String, Object> itemMap = (Map<String, Object>) map;

                int slot = getInt(itemMap.get("Slot"), -1);
                ItemStack stack = parseItemStack(itemMap);
                if (stack != null && slot >= 0 && slot < 27) {
                    enderChest[slot] = stack;
                }
            }
        }

        return new OfflinePlayerData(storage, armor, offhand, enderChest);
    }

    private static ItemStack parseItemStack(Map<String, Object> itemMap) {
        String id = (String) itemMap.get("id");
        if (id == null || id.isBlank()) {
            return null;
        }

        int count = 1;
        if (itemMap.containsKey("count")) {
            count = getInt(itemMap.get("count"), 1);
        } else if (itemMap.containsKey("Count")) {
            count = getInt(itemMap.get("Count"), 1);
        }

        if (count <= 0) count = 1;

        String cleanMat = id.replace("minecraft:", "").toUpperCase(Locale.ROOT);
        Material mat = Material.matchMaterial(cleanMat);
        if (mat == null) {
            mat = Material.matchMaterial(id);
        }
        if (mat == null || mat.isAir()) {
            return null;
        }

        ItemStack stack = new ItemStack(mat, count);
        applyBasicMeta(stack, itemMap);
        return stack;
    }

    @SuppressWarnings("unchecked")
    private static void applyBasicMeta(ItemStack stack, Map<String, Object> itemMap) {
        if (stack == null || itemMap == null) return;
        try {
            ItemMeta meta = stack.getItemMeta();
            if (meta == null) return;

            // 1. Legacy tag-Compound (< 1.20.5)
            Object tagObj = itemMap.get("tag");
            if (tagObj instanceof Map<?, ?> tagMap) {
                // Display (Name & Lore)
                Object displayObj = tagMap.get("display");
                if (displayObj instanceof Map<?, ?> displayMap) {
                    Object nameObj = displayMap.get("Name");
                    if (nameObj instanceof String nameStr && !nameStr.isBlank()) {
                        meta.setDisplayName(cleanJsonText(nameStr));
                    }
                    Object loreObj = displayMap.get("Lore");
                    if (loreObj instanceof List<?> loreList) {
                        List<String> lore = new ArrayList<>();
                        for (Object l : loreList) {
                            if (l instanceof String s) {
                                lore.add(cleanJsonText(s));
                            }
                        }
                        meta.setLore(lore);
                    }
                }

                // Enchantments
                applyEnchantmentsFromList(meta, tagMap.get("Enchantments"));
                applyEnchantmentsFromList(meta, tagMap.get("StoredEnchantments"));
            }

            // 2. Modern components-Compound (1.20.5+ / 1.21+)
            Object compObj = itemMap.get("components");
            if (compObj instanceof Map<?, ?> compMap) {
                Object customName = compMap.get("minecraft:custom_name");
                if (customName instanceof String nameStr && !nameStr.isBlank()) {
                    meta.setDisplayName(cleanJsonText(nameStr));
                }

                Object loreObj = compMap.get("minecraft:lore");
                if (loreObj instanceof List<?> loreList) {
                    List<String> lore = new ArrayList<>();
                    for (Object l : loreList) {
                        if (l instanceof String s) {
                            lore.add(cleanJsonText(s));
                        }
                    }
                    meta.setLore(lore);
                }

                Object enchantsObj = compMap.get("minecraft:enchantments");
                if (enchantsObj instanceof Map<?, ?> enchantsMap) {
                    Object levelsObj = enchantsMap.get("levels");
                    Map<?, ?> levels = (levelsObj instanceof Map<?, ?> lvlMap) ? lvlMap : enchantsMap;
                    for (Map.Entry<?, ?> entry : levels.entrySet()) {
                        if (entry.getKey() instanceof String enchKey && entry.getValue() instanceof Number lvlNum) {
                            applyEnchantmentByKey(meta, enchKey, lvlNum.intValue());
                        }
                    }
                }
            }

            stack.setItemMeta(meta);
        } catch (Throwable ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void applyEnchantmentsFromList(ItemMeta meta, Object enchantsObj) {
        if (!(enchantsObj instanceof List<?> list)) return;
        for (Object elem : list) {
            if (elem instanceof Map<?, ?> enchMap) {
                Object idObj = enchMap.get("id");
                Object lvlObj = enchMap.get("lvl");
                if (idObj instanceof String idStr && lvlObj instanceof Number lvlNum) {
                    applyEnchantmentByKey(meta, idStr, lvlNum.intValue());
                }
            }
        }
    }

    private static void applyEnchantmentByKey(ItemMeta meta, String key, int level) {
        try {
            String cleanKey = key.replace("minecraft:", "").toLowerCase(Locale.ROOT);
            NamespacedKey namespacedKey = NamespacedKey.minecraft(cleanKey);
            Enchantment enchantment = Enchantment.getByKey(namespacedKey);
            if (enchantment != null) {
                meta.addEnchant(enchantment, level, true);
            }
        } catch (Throwable ignored) {}
    }

    private static String cleanJsonText(String raw) {
        if (raw == null) return "";
        String trimmed = raw.trim();
        // Falls JSON format wie {"text":"Mein Schwert"} vorliegt, einfachen Text extrahieren
        if (trimmed.startsWith("{") && trimmed.contains("\"text\":")) {
            int start = trimmed.indexOf("\"text\":");
            int quoteStart = trimmed.indexOf("\"", start + 7);
            if (quoteStart != -1) {
                int quoteEnd = trimmed.indexOf("\"", quoteStart + 1);
                if (quoteEnd != -1) {
                    return trimmed.substring(quoteStart + 1, quoteEnd);
                }
            }
        }
        return trimmed.replace("\"", "");
    }

    private static int getInt(Object obj, int def) {
        if (obj instanceof Number num) {
            return num.intValue();
        }
        return def;
    }

    private static Map<String, Object> readCompoundPayload(DataInputStream in) throws IOException {
        Map<String, Object> compound = new LinkedHashMap<>();
        while (true) {
            byte tagId = in.readByte();
            if (tagId == TAG_END) {
                break;
            }
            String name = readString(in);
            Object payload = readPayload(in, tagId);
            compound.put(name, payload);
        }
        return compound;
    }

    private static Object readPayload(DataInputStream in, byte tagId) throws IOException {
        return switch (tagId) {
            case TAG_BYTE -> in.readByte();
            case TAG_SHORT -> in.readShort();
            case TAG_INT -> in.readInt();
            case TAG_LONG -> in.readLong();
            case TAG_FLOAT -> in.readFloat();
            case TAG_DOUBLE -> in.readDouble();
            case TAG_BYTE_ARRAY -> {
                int len = in.readInt();
                byte[] arr = new byte[len];
                in.readFully(arr);
                yield arr;
            }
            case TAG_STRING -> readString(in);
            case TAG_LIST -> {
                byte elemType = in.readByte();
                int len = in.readInt();
                List<Object> list = new ArrayList<>(Math.max(0, len));
                for (int i = 0; i < len; i++) {
                    list.add(readPayload(in, elemType));
                }
                yield list;
            }
            case TAG_COMPOUND -> readCompoundPayload(in);
            case TAG_INT_ARRAY -> {
                int len = in.readInt();
                int[] arr = new int[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = in.readInt();
                }
                yield arr;
            }
            case TAG_LONG_ARRAY -> {
                int len = in.readInt();
                long[] arr = new long[len];
                for (int i = 0; i < len; i++) {
                    arr[i] = in.readLong();
                }
                yield arr;
            }
            default -> null;
        };
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
