package org.pixelbays.floatingchests;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

public class ChestSupportOverridesConfig {

    public static final BuilderCodec<ChestSupportOverridesConfig> CODEC = BuilderCodec.builder(ChestSupportOverridesConfig.class, ChestSupportOverridesConfig::new)
            .append(new KeyedCodec<>("IncludedBlockIds", Codec.STRING_ARRAY),
                    (config, value) -> config.includedBlockIds = sanitizeArray(value),
                    ChestSupportOverridesConfig::getIncludedBlockIds)
            .add()
            .append(new KeyedCodec<>("ExcludedBlockIds", Codec.STRING_ARRAY),
                    (config, value) -> config.excludedBlockIds = sanitizeArray(value),
                    ChestSupportOverridesConfig::getExcludedBlockIds)
            .add()
            .build();

    private String[] includedBlockIds = new String[0];
    private String[] excludedBlockIds = new String[0];

    public ChestSupportOverridesConfig() {
    }

    public String[] getIncludedBlockIds() {
        return this.includedBlockIds;
    }

    public String[] getExcludedBlockIds() {
        return this.excludedBlockIds;
    }

    public Set<String> getIncludedBlockIdSet() {
        return new LinkedHashSet<>(Arrays.asList(this.includedBlockIds));
    }

    public Set<String> getExcludedBlockIdSet() {
        return new LinkedHashSet<>(Arrays.asList(this.excludedBlockIds));
    }

    public boolean addIncludedBlockId(String blockId) {
        Set<String> values = this.getIncludedBlockIdSet();
        if (!values.add(normalizeBlockId(blockId))) {
            return false;
        }
        this.includedBlockIds = sanitizeCollection(values);
        return true;
    }

    public boolean removeIncludedBlockId(String blockId) {
        Set<String> values = this.getIncludedBlockIdSet();
        if (!removeIgnoreCase(values, blockId)) {
            return false;
        }
        this.includedBlockIds = sanitizeCollection(values);
        return true;
    }

    public boolean addExcludedBlockId(String blockId) {
        Set<String> values = this.getExcludedBlockIdSet();
        if (!values.add(normalizeBlockId(blockId))) {
            return false;
        }
        this.excludedBlockIds = sanitizeCollection(values);
        return true;
    }

    public boolean removeExcludedBlockId(String blockId) {
        Set<String> values = this.getExcludedBlockIdSet();
        if (!removeIgnoreCase(values, blockId)) {
            return false;
        }
        this.excludedBlockIds = sanitizeCollection(values);
        return true;
    }

    private static boolean removeIgnoreCase(Set<String> values, String blockId) {
        String normalized = normalizeBlockId(blockId);
        return values.removeIf(existing -> existing.equalsIgnoreCase(normalized));
    }

    private static String[] sanitizeCollection(Collection<String> values) {
        return sanitizeArray(values.toArray(String[]::new));
    }

    private static String[] sanitizeArray(String[] values) {
        LinkedHashMap<String, String> normalizedValues = new LinkedHashMap<>();
        if (values != null) {
            for (String value : values) {
                String normalized = normalizeBlockId(value);
                normalizedValues.putIfAbsent(normalized.toLowerCase(Locale.ROOT), normalized);
            }
        }
        return normalizedValues.values().toArray(String[]::new);
    }

    private static String normalizeBlockId(String blockId) {
        if (blockId == null) {
            throw new IllegalArgumentException("Block id cannot be null");
        }
        String normalized = blockId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Block id cannot be blank");
        }
        return normalized;
    }
}