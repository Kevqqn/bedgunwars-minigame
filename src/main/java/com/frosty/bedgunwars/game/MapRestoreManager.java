package com.frosty.bedgunwars.game;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

public class MapRestoreManager {

    // Stores the original state of blocks before they were changed
    private static class BlockSnapshot {
        final BlockState state;
        final CompoundTag blockEntityData; // null if no block entity

        BlockSnapshot(BlockState state, CompoundTag blockEntityData) {
            this.state = state;
            this.blockEntityData = blockEntityData;
        }
    }

    // Map from position to original state before player interaction
    // Only the FIRST change at each position is saved (original state)
    private final Map<BlockPos, BlockSnapshot> originalStates = new LinkedHashMap<>();

    // Positions placed by players (restore to whatever was there before, or air)
    private final Set<BlockPos> playerPlaced = new LinkedHashSet<>();

    private ServerLevel level;

    public void init(ServerLevel level) {
        this.level = level;
        originalStates.clear();
        playerPlaced.clear();
    }

    // ── Called when a player breaks a block ───────────────────────────────────

    public void onBlockBroken(BlockPos pos, BlockState state, ServerLevel level) {
        if (this.level == null || !this.level.equals(level)) return;
        // Only save the original state (first change at this position)
        if (!originalStates.containsKey(pos) && !playerPlaced.contains(pos)) {
            CompoundTag nbt = null;
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null) {
                nbt = be.saveWithFullMetadata();
            }
            originalStates.put(pos.immutable(), new BlockSnapshot(state, nbt));
        } else if (playerPlaced.contains(pos)) {
            // Player placed then broke — net effect is nothing, remove from placed
            playerPlaced.remove(pos);
        }
    }

    // ── Called when a player places a block ───────────────────────────────────

    public void onBlockPlaced(BlockPos pos, ServerLevel level) {
        if (this.level == null || !this.level.equals(level)) return;
        if (!originalStates.containsKey(pos)) {
            // No original state saved = this position had air or was untouched
            playerPlaced.add(pos.immutable());
        }
        // If original state exists, placing here is still tracked via originalStates
    }

    // ── Restore all changes after game ends ───────────────────────────────────

    public void restore(ServerLevel level) {
        if (originalStates.isEmpty() && playerPlaced.isEmpty()) return;

        int restored = 0;

        // Restore broken/modified blocks to their original state
        for (Map.Entry<BlockPos, BlockSnapshot> entry : originalStates.entrySet()) {
            BlockPos pos = entry.getKey();
            BlockSnapshot snap = entry.getValue();
            level.setBlock(pos, snap.state, 3);
            if (snap.blockEntityData != null) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null) {
                    be.load(snap.blockEntityData);
                    be.setChanged();
                }
            }
            restored++;
        }

        // Remove player-placed blocks (restore to air)
        for (BlockPos pos : playerPlaced) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            restored++;
        }

        com.frosty.bedgunwars.BedGunWars.LOGGER.info(
                "[MapRestore] Restored {} block changes.", restored);

        originalStates.clear();
        playerPlaced.clear();
    }

    public void reset() {
        originalStates.clear();
        playerPlaced.clear();
        level = null;
    }

    public int getChangeCount() {
        return originalStates.size() + playerPlaced.size();
    }
}