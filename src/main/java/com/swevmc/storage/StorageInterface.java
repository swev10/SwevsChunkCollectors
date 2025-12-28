package com.swevmc.storage;

import com.swevmc.models.ChunkCollector;
import java.util.List;
import java.util.UUID;

public interface StorageInterface {
    
    boolean initialize();
    
    void shutdown();
    
    boolean saveCollectors(List<ChunkCollector> collectors);
    
    List<ChunkCollector> loadCollectors();
    
    boolean saveCollector(ChunkCollector collector);
    
    boolean deleteCollector(UUID collectorUuid);
    
    boolean isConnected();
}
