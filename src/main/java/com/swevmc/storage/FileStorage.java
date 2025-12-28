package com.swevmc.storage;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FileStorage implements StorageInterface {

    private final scc plugin;
    private final File dataFile;
    private YamlConfiguration config;

    public FileStorage(scc plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "collectors.yml");
    }

    @Override
    public boolean initialize() {
        try {
            if (!dataFile.exists()) {
                dataFile.getParentFile().mkdirs();
                dataFile.createNewFile();
            }
            config = YamlConfiguration.loadConfiguration(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to initialize file storage: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public boolean saveCollectors(List<ChunkCollector> collectors) {
        try {
            config.set("collectors", null);

            for (int i = 0; i < collectors.size(); i++) {
                ChunkCollector collector = collectors.get(i);
                String path = "collectors." + i;

                config.set(path + ".uuid", collector.getUuid().toString());
                config.set(path + ".ownerUuid", collector.getOwnerUuid().toString());
                config.set(path + ".ownerName", collector.getOwnerName());
                config.set(path + ".world", collector.getLocation().getWorld().getName());
                config.set(path + ".x", collector.getLocation().getX());
                config.set(path + ".y", collector.getLocation().getY());
                config.set(path + ".z", collector.getLocation().getZ());
                config.set(path + ".yaw", collector.getLocation().getYaw());
                config.set(path + ".pitch", collector.getLocation().getPitch());
                config.set(path + ".createdAt", collector.getCreatedAt());
                config.set(path + ".timeRemaining", collector.getTimeRemaining());
                config.set(path + ".itemsCollected", collector.getItemsCollected());
                config.set(path + ".active", collector.isActive());
                config.set(path + ".maxChargeTime", collector.getMaxChargeTime());
                config.set(path + ".totalMoneyEarned", collector.getTotalMoneyEarned());
                config.set(path + ".lastAutosellTime", collector.getLastAutosellTime());
            }

            config.save(dataFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save collectors to file: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<ChunkCollector> loadCollectors() {
        try {
            List<ChunkCollector> collectors = new ArrayList<>();

            if (config.contains("collectors")) {
                int index = 0;
                while (config.contains("collectors." + index + ".uuid")) {
                    String path = "collectors." + index;

                    try {
                        UUID uuid = UUID.fromString(config.getString(path + ".uuid"));
                        UUID ownerUuid = UUID.fromString(config.getString(path + ".ownerUuid"));
                        String ownerName = config.getString(path + ".ownerName");

                        String worldName = config.getString(path + ".world");
                        double x = config.getDouble(path + ".x");
                        double y = config.getDouble(path + ".y");
                        double z = config.getDouble(path + ".z");
                        float yaw = (float) config.getDouble(path + ".yaw");
                        float pitch = (float) config.getDouble(path + ".pitch");

                        org.bukkit.World world = plugin.getServer().getWorld(worldName);
                        if (world == null) {
                            plugin.getLogger().warning("World not found: " + worldName + " for collector " + uuid);
                            index++;
                            continue;
                        }

                        org.bukkit.Location location = new org.bukkit.Location(world, x, y, z, yaw, pitch);
                        long createdAt = config.getLong(path + ".createdAt");
                        long timeRemaining = config.getLong(path + ".timeRemaining");
                        int itemsCollected = config.getInt(path + ".itemsCollected");
                        boolean active = config.getBoolean(path + ".active");
                        long maxChargeTime = config.getLong(path + ".maxChargeTime", 0);
                        double totalMoneyEarned = config.getDouble(path + ".totalMoneyEarned", 0.0);
                        long lastAutosellTime = config.getLong(path + ".lastAutosellTime", 0);

                        ChunkCollector collector = new ChunkCollector(uuid, ownerUuid, ownerName, location,
                                createdAt, timeRemaining, itemsCollected, active);
                        collector.setMaxChargeTime(maxChargeTime);
                        collector.setTotalMoneyEarned(totalMoneyEarned);
                        collector.setLastAutosellTime(lastAutosellTime);

                        collectors.add(collector);
                    } catch (Exception e) {
                        plugin.getLogger()
                                .warning("Failed to load collector at index " + index + ": " + e.getMessage());
                    }

                    index++;
                }
            }

            return collectors;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load collectors from file: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean saveCollector(ChunkCollector collector) {
        List<ChunkCollector> collectors = loadCollectors();

        collectors.removeIf(c -> c.getUuid().equals(collector.getUuid()));

        collectors.add(collector);

        return saveCollectors(collectors);
    }

    @Override
    public boolean deleteCollector(UUID collectorUuid) {
        List<ChunkCollector> collectors = loadCollectors();
        boolean removed = collectors.removeIf(c -> c.getUuid().equals(collectorUuid));

        if (removed) {
            return saveCollectors(collectors);
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        return dataFile.exists() && config != null;
    }
}
