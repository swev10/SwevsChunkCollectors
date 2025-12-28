package com.swevmc.blocks;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import com.swevmc.scc;

public class ChunkCollectorBlock {
    
    private static final NamespacedKey STATE_KEY = new NamespacedKey(scc.getInstance(), "collector_state");
    private static final String ACTIVE_STATE = "active";
    private static final String INACTIVE_STATE = "inactive";
    
    public static void setChunkCollectorBlock(Block block, boolean active) {
        block.setType(Material.BEACON);
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            
            String stateValue = active ? ACTIVE_STATE : INACTIVE_STATE;
            container.set(STATE_KEY, PersistentDataType.STRING, stateValue);
            
            tileState.update(true, false);
        }
    }
    
    public static boolean isChunkCollectorBlock(Block block) {
        if (block.getType() != Material.BEACON) {
            return false;
        }
        
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            return container.has(STATE_KEY, PersistentDataType.STRING);
        }
        return false;
    }
    
    public static boolean isActive(Block block) {
        if (!isChunkCollectorBlock(block)) {
            return false;
        }
        
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            String stateValue = container.get(STATE_KEY, PersistentDataType.STRING);
            return ACTIVE_STATE.equals(stateValue);
        }
        return false;
    }
    
    public static void updateState(Block block, boolean active) {
        if (!isChunkCollectorBlock(block)) {
            return;
        }
        
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            
            String stateValue = active ? ACTIVE_STATE : INACTIVE_STATE;
            container.set(STATE_KEY, PersistentDataType.STRING, stateValue);
            
            tileState.update(true, false);
        }
    }
    
    public static void removeChunkCollectorBlock(Block block) {
        if (!isChunkCollectorBlock(block)) {
            return;
        }
        
        BlockState state = block.getState();
        if (state instanceof TileState tileState) {
            PersistentDataContainer container = tileState.getPersistentDataContainer();
            
            container.remove(STATE_KEY);
            tileState.update(true, false);
        }
    }
}
