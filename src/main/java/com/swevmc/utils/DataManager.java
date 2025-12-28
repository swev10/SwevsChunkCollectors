package com.swevmc.utils;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import com.swevmc.storage.StorageInterface;
import com.swevmc.storage.FileStorage;
import com.swevmc.storage.MySQLStorage;
import com.swevmc.storage.RedisStorage;

import java.util.List;

public class DataManager {
    
    private final scc plugin;
    private StorageInterface storage;
    
    public DataManager(scc plugin) {
        this.plugin = plugin;
        initializeStorage();
    }
    
    private void initializeStorage() {
        String storageType = plugin.getConfigManager().getStorageType();
        
        switch (storageType) {
            case "MYSQL":
                String mysqlHost = plugin.getConfigManager().getMySQLHost();
                int mysqlPort = plugin.getConfigManager().getMySQLPort();
                String mysqlDatabase = plugin.getConfigManager().getMySQLDatabase();
                String mysqlUsername = plugin.getConfigManager().getMySQLUsername();
                String mysqlPassword = plugin.getConfigManager().getMySQLPassword();
                boolean mysqlUseSSL = plugin.getConfigManager().getMySQLUseSSL();
                
                storage = new MySQLStorage(plugin, mysqlHost, mysqlPort, mysqlDatabase, mysqlUsername, mysqlPassword, mysqlUseSSL);
                plugin.getLogger().info("Initializing MySQL storage...");
                break;
                
            case "REDIS":
                String redisHost = plugin.getConfigManager().getRedisHost();
                int redisPort = plugin.getConfigManager().getRedisPort();
                String redisPassword = plugin.getConfigManager().getRedisPassword();
                int redisDatabase = plugin.getConfigManager().getRedisDatabase();
                boolean redisUseSSL = plugin.getConfigManager().getRedisUseSSL();
                
                storage = new RedisStorage(plugin, redisHost, redisPort, redisPassword, redisDatabase, redisUseSSL);
                plugin.getLogger().info("Initializing Redis storage...");
                break;
                
            case "FILE":
            default:
                storage = new FileStorage(plugin);
                plugin.getLogger().info("Initializing file storage...");
                break;
        }
        
        if (!storage.initialize()) {
            plugin.getLogger().severe("Failed to initialize " + storageType + " storage! Falling back to file storage.");
            storage = new FileStorage(plugin);
            if (!storage.initialize()) {
                plugin.getLogger().severe("Failed to initialize file storage as fallback!");
            }
        } else {
            plugin.getLogger().info("Successfully initialized " + storageType + " storage!");
        }
    }
    
    public void saveCollectors(List<ChunkCollector> collectors) {
        if (storage != null) {
            storage.saveCollectors(collectors);
        }
    }
    
    public List<ChunkCollector> loadCollectors() {
        if (storage != null) {
            return storage.loadCollectors();
        }
        return null;
    }
    
    public void saveCollector(ChunkCollector collector) {
        if (storage != null) {
            storage.saveCollector(collector);
        }
    }
    
    public void deleteCollector(java.util.UUID collectorUuid) {
        if (storage != null) {
            storage.deleteCollector(collectorUuid);
        }
    }
    
    public boolean isStorageConnected() {
        return storage != null && storage.isConnected();
    }
    
    public void shutdown() {
        if (storage != null) {
            storage.shutdown();
        }
    }
}