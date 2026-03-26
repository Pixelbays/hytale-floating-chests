package org.pixelbays.floatingchests;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import com.hypixel.hytale.assetstore.event.LoadedAssetsEvent;
import com.hypixel.hytale.assetstore.event.RemovedAssetsEvent;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockFace;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.RequiredBlockFaceSupport;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.util.Config;

public final class ChestSupportPatchService {

    private static final String AUTO_MATCH_TOKEN = "chest";
    private static final String FURNITURE_TAG_CATEGORY = "Type";
    private static final String FURNITURE_TAG_VALUE = "Furniture";
    private static final String REQUIRED_FACE_TYPE = "Full";
    private static final Field BLOCK_SUPPORT_FIELD = getDeclaredField(BlockType.class, "support");
    private static final Field BLOCK_ROTATED_SUPPORT_FIELD = getDeclaredField(BlockType.class, "rotatedSupport");
    private static final Method BLOCK_PROCESS_CONFIG_METHOD = getDeclaredMethod(BlockType.class, "processConfig");

    private final FloatingChests plugin;
    private final Config<ChestSupportOverridesConfig> config;
    private Set<String> lastAppliedBlockIds = Collections.emptySet();
    private Set<String> lastAutomaticMatchedItemIds = Collections.emptySet();
    private Map<String, String> lastKnownItemBlockIds = Collections.emptyMap();
    private Map<String, Map<BlockFace, RequiredBlockFaceSupport[]>> originalSupportByBlockId = Collections.emptyMap();

    public ChestSupportPatchService(FloatingChests plugin, Config<ChestSupportOverridesConfig> config) {
        this.plugin = plugin;
        this.config = config;
    }

    @SuppressWarnings("unchecked")
    public void register() {
        this.plugin.getEventRegistry().registerGlobal(LoadedAssetsEvent.class, this::handleLoadedAssets);
        this.plugin.getEventRegistry().registerGlobal(RemovedAssetsEvent.class, this::handleRemovedAssets);
    }

    public synchronized int reapplyAll(String reason) {
        Set<String> desiredBlockIds = this.computeEffectiveTargetBlockIds();
        LinkedHashMap<String, Map<BlockFace, RequiredBlockFaceSupport[]>> originalSupportMapByBlockId = new LinkedHashMap<>(this.originalSupportByBlockId);
        LinkedHashSet<String> appliedBlockIds = new LinkedHashSet<>();
        List<String> missingBlocks = new ArrayList<>();

        this.restoreNoLongerTargetedBlocks(desiredBlockIds, originalSupportMapByBlockId);

        for (String blockId : desiredBlockIds) {
            BlockType source = BlockType.getAssetMap().getAsset(blockId);
            if (source == null || source == BlockType.UNKNOWN) {
                missingBlocks.add(blockId);
                originalSupportMapByBlockId.remove(blockId);
                continue;
            }

            if (!this.isCurrentlyPatched(source)) {
                originalSupportMapByBlockId.put(blockId, this.cloneSupportMap(source.getSupport(0)));
            } else if (!originalSupportMapByBlockId.containsKey(blockId)) {
                originalSupportMapByBlockId.put(blockId, this.cloneSupportMap(source.getSupport(0)));
            }

            this.applySupportPatch(source);
            appliedBlockIds.add(source.getId());
        }

        this.lastAppliedBlockIds = Collections.unmodifiableSet(appliedBlockIds);
        this.originalSupportByBlockId = Collections.unmodifiableMap(originalSupportMapByBlockId);
        this.refreshItemTracking();

        this.plugin.getLogger().atInfo().log("Applied chest support overrides to "
                + this.lastAppliedBlockIds.size() + " block types (" + reason + ")");

        if (!missingBlocks.isEmpty()) {
            this.plugin.getLogger().atWarning().log("Skipped chest support overrides for missing block types: "
                    + String.join(", ", missingBlocks));
        }

        return this.lastAppliedBlockIds.size();
    }

    public synchronized void clearRuntimeOverrides() {
        this.restoreTrackedBlocks(new LinkedHashSet<>(this.originalSupportByBlockId.keySet()), new LinkedHashMap<>(this.originalSupportByBlockId));
        this.lastAppliedBlockIds = Collections.emptySet();
        this.lastAutomaticMatchedItemIds = Collections.emptySet();
        this.lastKnownItemBlockIds = Collections.emptyMap();
        this.originalSupportByBlockId = Collections.emptyMap();
    }

    public synchronized UpdateResult addIncludedBlockId(String blockId) {
        String normalized = this.normalizeBlockId(blockId);
        ChestSupportOverridesConfig currentConfig = this.config.get();
        boolean changed = currentConfig.addIncludedBlockId(normalized);
        changed |= currentConfig.removeExcludedBlockId(normalized);
        if (changed) {
            this.config.save().join();
        }
        return new UpdateResult(normalized, changed, this.reapplyAll("manual include add"));
    }

    public synchronized UpdateResult removeIncludedBlockId(String blockId) {
        String normalized = this.normalizeBlockId(blockId);
        boolean changed = this.config.get().removeIncludedBlockId(normalized);
        if (changed) {
            this.config.save().join();
        }
        return new UpdateResult(normalized, changed, this.reapplyAll("manual include remove"));
    }

    public synchronized UpdateResult addExcludedBlockId(String blockId) {
        String normalized = this.normalizeBlockId(blockId);
        ChestSupportOverridesConfig currentConfig = this.config.get();
        boolean changed = currentConfig.addExcludedBlockId(normalized);
        changed |= currentConfig.removeIncludedBlockId(normalized);
        if (changed) {
            this.config.save().join();
        }
        return new UpdateResult(normalized, changed, this.reapplyAll("manual exclude add"));
    }

    public synchronized UpdateResult removeExcludedBlockId(String blockId) {
        String normalized = this.normalizeBlockId(blockId);
        boolean changed = this.config.get().removeExcludedBlockId(normalized);
        if (changed) {
            this.config.save().join();
        }
        return new UpdateResult(normalized, changed, this.reapplyAll("manual exclude remove"));
    }

    public synchronized ChestSupportStateSnapshot snapshot() {
        Set<String> automaticBlockIds = this.computeAutomaticTargetBlockIds();
        Set<String> includedBlockIds = new LinkedHashSet<>(this.config.get().getIncludedBlockIdSet());
        Set<String> excludedBlockIds = new LinkedHashSet<>(this.config.get().getExcludedBlockIdSet());
        Set<String> effectiveBlockIds = this.computeEffectiveTargetBlockIds(automaticBlockIds, includedBlockIds, excludedBlockIds);
        return new ChestSupportStateSnapshot(
                Collections.unmodifiableSet(automaticBlockIds),
                Collections.unmodifiableSet(includedBlockIds),
                Collections.unmodifiableSet(excludedBlockIds),
                Collections.unmodifiableSet(effectiveBlockIds),
                this.lastAppliedBlockIds);
    }

    public synchronized BlockStatus status(String blockId) {
        String normalized = this.normalizeBlockId(blockId);
        ChestSupportStateSnapshot snapshot = this.snapshot();
        BlockType blockType = BlockType.getAssetMap().getAsset(normalized);
        boolean exists = blockType != null && blockType != BlockType.UNKNOWN;
        return new BlockStatus(
                normalized,
                exists,
                snapshot.automaticBlockIds().contains(normalized),
                snapshot.includedBlockIds().contains(normalized),
                snapshot.excludedBlockIds().contains(normalized),
                snapshot.effectiveBlockIds().contains(normalized),
                snapshot.appliedBlockIds().contains(normalized));
    }

    @SuppressWarnings("rawtypes")
    private void handleLoadedAssets(LoadedAssetsEvent event) {
        if (event.getAssetClass() != Item.class) {
            return;
        }

        if (event.isInitial()) {
            this.reapplyAll("initial item asset load");
            return;
        }

        @SuppressWarnings("unchecked")
        Map<String, Item> loadedAssets = (Map<String, Item>) event.getLoadedAssets();
        if (this.shouldReapplyForLoadedItems(loadedAssets)) {
            this.reapplyAll("item asset load");
        }
    }

    @SuppressWarnings("rawtypes")
    private void handleRemovedAssets(RemovedAssetsEvent event) {
        if (event.getAssetClass() != Item.class) {
            return;
        }

        @SuppressWarnings("unchecked")
        Set<String> removedAssetIds = (Set<String>) event.getRemovedAssets();
        if (this.shouldReapplyForRemovedItems(removedAssetIds)) {
            this.reapplyAll(event.isReplaced() ? "item asset replace" : "item asset removal");
        }
    }

    private boolean shouldReapplyForLoadedItems(Map<String, Item> loadedAssets) {
        for (Map.Entry<String, Item> entry : loadedAssets.entrySet()) {
            String itemId = this.resolveItemId(entry.getKey(), entry.getValue());
            if (itemId == null) {
                continue;
            }

            boolean wasAutomaticMatch = this.lastAutomaticMatchedItemIds.contains(itemId);
            String previousBlockId = this.lastKnownItemBlockIds.get(itemId);
            boolean touchedAppliedBlock = previousBlockId != null && containsIgnoreCase(this.lastAppliedBlockIds, previousBlockId);
            Item item = entry.getValue();

            if (item == null || item == Item.UNKNOWN) {
                if (wasAutomaticMatch || touchedAppliedBlock) {
                    return true;
                }
                continue;
            }

            boolean isAutomaticMatch = this.isFurnitureChestItem(item);
            String currentBlockId = this.resolveItemBlockId(item);
            if (isAutomaticMatch || wasAutomaticMatch) {
                return true;
            }

            if (currentBlockId != null && containsIgnoreCase(this.lastAppliedBlockIds, currentBlockId)) {
                return true;
            }

            if (touchedAppliedBlock) {
                return true;
            }
        }

        return false;
    }

    private boolean shouldReapplyForRemovedItems(Set<String> removedAssetIds) {
        for (String itemId : removedAssetIds) {
            if (this.lastAutomaticMatchedItemIds.contains(itemId)) {
                return true;
            }

            String previousBlockId = this.lastKnownItemBlockIds.get(itemId);
            if (previousBlockId != null && containsIgnoreCase(this.lastAppliedBlockIds, previousBlockId)) {
                return true;
            }
        }

        return false;
    }

    private void refreshItemTracking() {
        LinkedHashSet<String> automaticMatchedItemIds = new LinkedHashSet<>();
        LinkedHashMap<String, String> knownItemBlockIds = new LinkedHashMap<>();

        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            if (item == null || item == Item.UNKNOWN) {
                continue;
            }

            String itemId = item.getId();
            if (itemId == null || itemId.isBlank()) {
                continue;
            }

            String blockId = this.resolveItemBlockId(item);
            if (blockId != null) {
                knownItemBlockIds.put(itemId, blockId);
            }

            if (blockId != null && this.isFurnitureChestItem(item)) {
                automaticMatchedItemIds.add(itemId);
            }
        }

        this.lastAutomaticMatchedItemIds = Collections.unmodifiableSet(automaticMatchedItemIds);
        this.lastKnownItemBlockIds = Collections.unmodifiableMap(knownItemBlockIds);
    }

    private Set<String> computeEffectiveTargetBlockIds() {
        Set<String> automaticBlockIds = this.computeAutomaticTargetBlockIds();
        return this.computeEffectiveTargetBlockIds(
                automaticBlockIds,
                this.config.get().getIncludedBlockIdSet(),
                this.config.get().getExcludedBlockIdSet());
    }

    private Set<String> computeEffectiveTargetBlockIds(Set<String> automaticBlockIds, Set<String> includedBlockIds, Set<String> excludedBlockIds) {
        LinkedHashSet<String> effectiveBlockIds = new LinkedHashSet<>(automaticBlockIds);
        for (String blockId : includedBlockIds) {
            effectiveBlockIds.add(this.normalizeBlockId(blockId));
        }
        effectiveBlockIds.removeIf(blockId -> containsIgnoreCase(excludedBlockIds, blockId));
        return effectiveBlockIds;
    }

    private Set<String> computeAutomaticTargetBlockIds() {
        LinkedHashSet<String> automaticBlockIds = new LinkedHashSet<>();
        for (Item item : Item.getAssetMap().getAssetMap().values()) {
            if (!this.isFurnitureChestItem(item)) {
                continue;
            }

            String blockId = this.resolveItemBlockId(item);
            if (blockId == null) {
                continue;
            }

            automaticBlockIds.add(blockId);
        }
        return automaticBlockIds;
    }

    private String resolveItemId(String fallbackItemId, Item item) {
        if (item != null && item != Item.UNKNOWN && item.getId() != null && !item.getId().isBlank()) {
            return item.getId();
        }

        if (fallbackItemId == null || fallbackItemId.isBlank()) {
            return null;
        }

        return fallbackItemId;
    }

    private String resolveItemBlockId(Item item) {
        if (item == null) {
            return null;
        }

        String blockId = item.getBlockId();
        if (blockId == null || blockId.isBlank()) {
            return null;
        }

        return this.normalizeBlockId(blockId);
    }

    private boolean isFurnitureChestItem(Item item) {
        if (item == null || item == Item.UNKNOWN || !item.hasBlockType()) {
            return false;
        }

        String itemId = item.getId();
        if (itemId == null || !itemId.toLowerCase(Locale.ROOT).contains(AUTO_MATCH_TOKEN)) {
            return false;
        }

        for (Map.Entry<String, String[]> entry : item.getData().getRawTags().entrySet()) {
            if (!FURNITURE_TAG_CATEGORY.equalsIgnoreCase(entry.getKey()) || entry.getValue() == null) {
                continue;
            }

            for (String tagValue : entry.getValue()) {
                if (FURNITURE_TAG_VALUE.equalsIgnoreCase(tagValue)) {
                    return true;
                }
            }
        }

        return false;
    }

    private void applySupportPatch(@Nonnull BlockType source) {
        Map<BlockFace, RequiredBlockFaceSupport[]> supportMap = new EnumMap<>(BlockFace.class);
        Map<BlockFace, RequiredBlockFaceSupport[]> existingSupport = source.getSupport(0);

        if (existingSupport != null) {
            for (Map.Entry<BlockFace, RequiredBlockFaceSupport[]> entry : existingSupport.entrySet()) {
                RequiredBlockFaceSupport[] requirements = entry.getValue();
                supportMap.put(entry.getKey(), requirements == null ? null : requirements.clone());
            }
        }

        supportMap.put(BlockFace.DOWN, this.patchDownSupport(supportMap.get(BlockFace.DOWN)));
        setField(BLOCK_SUPPORT_FIELD, source, supportMap);
        setField(BLOCK_ROTATED_SUPPORT_FIELD, source, null);
        invokeMethod(BLOCK_PROCESS_CONFIG_METHOD, source);
    }

    private void restoreNoLongerTargetedBlocks(Set<String> desiredBlockIds,
                                               Map<String, Map<BlockFace, RequiredBlockFaceSupport[]>> originalSupportByBlockId) {
        LinkedHashSet<String> blockIdsToRestore = this.lastAppliedBlockIds.stream()
                .filter(blockId -> !containsIgnoreCase(desiredBlockIds, blockId))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        this.restoreTrackedBlocks(blockIdsToRestore, originalSupportByBlockId);
    }

    private void restoreTrackedBlocks(Set<String> blockIdsToRestore,
                                      Map<String, Map<BlockFace, RequiredBlockFaceSupport[]>> originalSupportByBlockId) {
        for (String blockId : blockIdsToRestore) {
            Map<BlockFace, RequiredBlockFaceSupport[]> originalSupport = originalSupportByBlockId.remove(blockId);
            if (originalSupport == null) {
                continue;
            }

            BlockType blockType = BlockType.getAssetMap().getAsset(blockId);
            if (blockType == null || blockType == BlockType.UNKNOWN) {
                continue;
            }

            setField(BLOCK_SUPPORT_FIELD, blockType, this.cloneSupportMap(originalSupport));
            setField(BLOCK_ROTATED_SUPPORT_FIELD, blockType, null);
            invokeMethod(BLOCK_PROCESS_CONFIG_METHOD, blockType);
        }
    }

    private Map<BlockFace, RequiredBlockFaceSupport[]> cloneSupportMap(Map<BlockFace, RequiredBlockFaceSupport[]> supportMap) {
        Map<BlockFace, RequiredBlockFaceSupport[]> clonedSupportMap = new EnumMap<>(BlockFace.class);
        if (supportMap == null) {
            return clonedSupportMap;
        }

        for (Map.Entry<BlockFace, RequiredBlockFaceSupport[]> entry : supportMap.entrySet()) {
            RequiredBlockFaceSupport[] requirements = entry.getValue();
            clonedSupportMap.put(entry.getKey(), requirements == null ? null : requirements.clone());
        }

        return clonedSupportMap;
    }

    private boolean isCurrentlyPatched(BlockType blockType) {
        Map<BlockFace, RequiredBlockFaceSupport[]> supportMap = blockType.getSupport(0);
        if (supportMap == null) {
            return false;
        }

        RequiredBlockFaceSupport[] downRequirements = supportMap.get(BlockFace.DOWN);
        if (downRequirements == null || downRequirements.length == 0) {
            return false;
        }

        for (RequiredBlockFaceSupport requirement : downRequirements) {
            if (requirement == null || requirement.getSupport() != RequiredBlockFaceSupport.Match.IGNORED) {
                return false;
            }
        }

        return true;
    }

    private RequiredBlockFaceSupport[] patchDownSupport(RequiredBlockFaceSupport[] currentRequirements) {
        if (currentRequirements == null || currentRequirements.length == 0) {
            return new RequiredBlockFaceSupport[]{this.createIgnoredRequirement(REQUIRED_FACE_TYPE)};
        }

        RequiredBlockFaceSupport[] patchedRequirements = new RequiredBlockFaceSupport[currentRequirements.length];
        for (int index = 0; index < currentRequirements.length; index++) {
            RequiredBlockFaceSupport requirement = currentRequirements[index];
            patchedRequirements[index] = requirement == null
                    ? this.createIgnoredRequirement(REQUIRED_FACE_TYPE)
                    : this.cloneRequirementWithIgnoredSupport(requirement);
        }
        return patchedRequirements;
    }

    private RequiredBlockFaceSupport cloneRequirementWithIgnoredSupport(RequiredBlockFaceSupport requirement) {
        return new RequiredBlockFaceSupport(
                requirement.getFaceType(),
                requirement.getSelfFaceType(),
                requirement.getBlockSetId(),
                requirement.getBlockTypeId(),
                requirement.getFluidId(),
                requirement.getMatchSelf(),
                RequiredBlockFaceSupport.Match.IGNORED,
                requirement.allowsSupportPropagation(),
                requirement.isRotated(),
                requirement.getFiller() == null ? null : requirement.getFiller().clone(),
                requirement.getTagId(),
                requirement.getTagIndex());
    }

    private RequiredBlockFaceSupport createIgnoredRequirement(String faceType) {
        return new RequiredBlockFaceSupport(
                faceType,
                null,
                null,
                null,
                null,
                null,
                RequiredBlockFaceSupport.Match.IGNORED,
                false,
                false,
                null,
                null,
                Integer.MIN_VALUE);
    }

    private String normalizeBlockId(String blockId) {
        String normalized = blockId == null ? "" : blockId.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Block id cannot be blank");
        }

        BlockType blockType = BlockType.getAssetMap().getAsset(normalized);
        if (blockType != null && blockType != BlockType.UNKNOWN) {
            return blockType.getId();
        }

        return normalized;
    }

    private static boolean containsIgnoreCase(Set<String> values, String expectedValue) {
        for (String value : values) {
            if (value.equalsIgnoreCase(expectedValue)) {
                return true;
            }
        }
        return false;
    }

    private static Field getDeclaredField(Class<?> owner, String fieldName) {
        try {
            Field field = owner.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access field " + owner.getName() + "." + fieldName, exception);
        }
    }

    private static Method getDeclaredMethod(Class<?> owner, String methodName) {
        try {
            Method method = owner.getDeclaredMethod(methodName);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to access method " + owner.getName() + "." + methodName, exception);
        }
    }

    private static void setField(Field field, Object instance, Object value) {
        try {
            field.set(instance, value);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Failed to set field " + field.getName(), exception);
        }
    }

    private static void invokeMethod(Method method, Object instance) {
        try {
            method.invoke(instance);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Failed to invoke method " + method.getName(), exception);
        }
    }

    public record UpdateResult(String blockId, boolean changed, int patchedCount) {
    }

    public record BlockStatus(String blockId, boolean blockExists, boolean automatic, boolean included, boolean excluded,
                              boolean effective, boolean applied) {
    }

    public static final class ChestSupportStateSnapshot {

        private final Set<String> automaticBlockIds;
        private final Set<String> includedBlockIds;
        private final Set<String> excludedBlockIds;
        private final Set<String> effectiveBlockIds;
        private final Set<String> appliedBlockIds;

        public ChestSupportStateSnapshot(Set<String> automaticBlockIds, Set<String> includedBlockIds,
                                         Set<String> excludedBlockIds, Set<String> effectiveBlockIds,
                                         Set<String> appliedBlockIds) {
            this.automaticBlockIds = automaticBlockIds;
            this.includedBlockIds = includedBlockIds;
            this.excludedBlockIds = excludedBlockIds;
            this.effectiveBlockIds = effectiveBlockIds;
            this.appliedBlockIds = appliedBlockIds;
        }

        public Set<String> automaticBlockIds() {
            return this.automaticBlockIds;
        }

        public Set<String> includedBlockIds() {
            return this.includedBlockIds;
        }

        public Set<String> excludedBlockIds() {
            return this.excludedBlockIds;
        }

        public Set<String> effectiveBlockIds() {
            return this.effectiveBlockIds;
        }

        public Set<String> appliedBlockIds() {
            return this.appliedBlockIds;
        }

        public int automaticCount() {
            return this.automaticBlockIds.size();
        }

        public int includedCount() {
            return this.includedBlockIds.size();
        }

        public int excludedCount() {
            return this.excludedBlockIds.size();
        }

        public int effectiveCount() {
            return this.effectiveBlockIds.size();
        }

        public int appliedCount() {
            return this.appliedBlockIds.size();
        }
    }
}