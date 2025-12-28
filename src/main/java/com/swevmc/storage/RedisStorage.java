package com.swevmc.storage;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import org.bukkit.Location;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RedisStorage implements StorageInterface {

    private final scc plugin;
    private final String host;
    private final int port;
    private final String password;
    private final int database;
    private final boolean useSSL;

    private JedisPool jedisPool;

    public RedisStorage(scc plugin, String host, int port, String password, int database, boolean useSSL) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.password = password;
        this.database = database;
        this.useSSL = useSSL;
    }

    @Override
    public boolean initialize() {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(20);
            poolConfig.setMaxIdle(10);
            poolConfig.setMinIdle(5);
            poolConfig.setTestOnBorrow(true);
            poolConfig.setTestOnReturn(true);
            poolConfig.setTestWhileIdle(true);
            poolConfig.setTimeBetweenEvictionRuns(Duration.ofSeconds(30));
            poolConfig.setNumTestsPerEvictionRun(3);
            poolConfig.setBlockWhenExhausted(true);

            jedisPool = new JedisPool(poolConfig, host, port, 2000, password, database, useSSL);

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                plugin.getLogger().info("Successfully connected to Redis!");
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Redis: " + e.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }

    @Override
    public boolean saveCollectors(List<ChunkCollector> collectors) {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> existingKeys = jedis.keys("chunk_collector:*");
            if (!existingKeys.isEmpty()) {
                jedis.del(existingKeys.toArray(new String[0]));
            }

            for (ChunkCollector collector : collectors) {
                saveCollectorToRedis(jedis, collector);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save collectors to Redis: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<ChunkCollector> loadCollectors() {
        try (Jedis jedis = jedisPool.getResource()) {
            Set<String> keys = jedis.keys("chunk_collector:*");
            List<ChunkCollector> collectors = new ArrayList<>();

            for (String key : keys) {
                ChunkCollector collector = loadCollectorFromRedis(jedis, key);
                if (collector != null) {
                    collectors.add(collector);
                }
            }

            return collectors;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load collectors from Redis: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean saveCollector(ChunkCollector collector) {
        try (Jedis jedis = jedisPool.getResource()) {
            return saveCollectorToRedis(jedis, collector);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save collector to Redis: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteCollector(UUID collectorUuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            String key = "chunk_collector:" + collectorUuid.toString();
            Long deleted = jedis.del(key);
            return deleted > 0;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete collector from Redis: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        try (Jedis jedis = jedisPool.getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean saveCollectorToRedis(Jedis jedis, ChunkCollector collector) {
        try {
            String key = "chunk_collector:" + collector.getUuid().toString();
            Location loc = collector.getLocation();

            jedis.hset(key, "uuid", collector.getUuid().toString());
            jedis.hset(key, "owner_uuid", collector.getOwnerUuid().toString());
            jedis.hset(key, "owner_name", collector.getOwnerName());
            jedis.hset(key, "world", loc.getWorld().getName());
            jedis.hset(key, "x", String.valueOf(loc.getX()));
            jedis.hset(key, "y", String.valueOf(loc.getY()));
            jedis.hset(key, "z", String.valueOf(loc.getZ()));
            jedis.hset(key, "yaw", String.valueOf(loc.getYaw()));
            jedis.hset(key, "pitch", String.valueOf(loc.getPitch()));
            jedis.hset(key, "created_at", String.valueOf(collector.getCreatedAt()));
            jedis.hset(key, "time_remaining", String.valueOf(collector.getTimeRemaining()));
            jedis.hset(key, "items_collected", String.valueOf(collector.getItemsCollected()));
            jedis.hset(key, "active", String.valueOf(collector.isActive()));
            jedis.hset(key, "max_charge_time", String.valueOf(collector.getMaxChargeTime()));
            jedis.hset(key, "total_money_earned", String.valueOf(collector.getTotalMoneyEarned()));
            jedis.hset(key, "last_autosell_time", String.valueOf(collector.getLastAutosellTime()));
            jedis.hset(key, "virtual_items", serializeVirtualItems(collector.getVirtualItems()));

            jedis.expire(key, 30 * 24 * 60 * 60);

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save collector to Redis: " + e.getMessage());
            return false;
        }
    }

    private ChunkCollector loadCollectorFromRedis(Jedis jedis, String key) {
        try {
            String uuidStr = jedis.hget(key, "uuid");
            if (uuidStr == null)
                return null;

            UUID uuid = UUID.fromString(uuidStr);
            UUID ownerUuid = UUID.fromString(jedis.hget(key, "owner_uuid"));
            String ownerName = jedis.hget(key, "owner_name");

            String worldName = jedis.hget(key, "world");
            double x = Double.parseDouble(jedis.hget(key, "x"));
            double y = Double.parseDouble(jedis.hget(key, "y"));
            double z = Double.parseDouble(jedis.hget(key, "z"));
            float yaw = Float.parseFloat(jedis.hget(key, "yaw"));
            float pitch = Float.parseFloat(jedis.hget(key, "pitch"));

            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName + " for collector " + uuid);
                return null;
            }

            Location location = new Location(world, x, y, z, yaw, pitch);
            long createdAt = Long.parseLong(jedis.hget(key, "created_at"));
            long timeRemaining = Long.parseLong(jedis.hget(key, "time_remaining"));
            int itemsCollected = Integer.parseInt(jedis.hget(key, "items_collected"));
            boolean active = Boolean.parseBoolean(jedis.hget(key, "active"));
            long maxChargeTime = Long.parseLong(jedis.hget(key, "max_charge_time"));
            double totalMoneyEarned = Double.parseDouble(jedis.hget(key, "total_money_earned"));
            long lastAutosellTime = Long.parseLong(jedis.hget(key, "last_autosell_time"));

            ChunkCollector collector = new ChunkCollector(uuid, ownerUuid, ownerName, location,
                    createdAt, timeRemaining, itemsCollected, active);
            collector.setMaxChargeTime(maxChargeTime);
            collector.setTotalMoneyEarned(totalMoneyEarned);
            collector.setLastAutosellTime(lastAutosellTime);

            String virtualItemsStr = jedis.hget(key, "virtual_items");
            if (virtualItemsStr != null && !virtualItemsStr.isEmpty()) {
            }

            return collector;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load collector from Redis: " + e.getMessage());
            return null;
        }
    }

    private String serializeVirtualItems(java.util.Map<org.bukkit.Material, Integer> virtualItems) {
        if (virtualItems == null || virtualItems.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<org.bukkit.Material, Integer> entry : virtualItems.entrySet()) {
            if (sb.length() > 0) {
                sb.append(",");
            }
            sb.append(entry.getKey().name()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }
}
