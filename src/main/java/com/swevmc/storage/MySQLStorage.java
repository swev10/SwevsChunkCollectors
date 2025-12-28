package com.swevmc.storage;

import com.swevmc.scc;
import com.swevmc.models.ChunkCollector;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Location;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MySQLStorage implements StorageInterface {

    private final scc plugin;
    private final String host;
    private final int port;
    private final String database;
    private final String username;
    private final String password;
    private final boolean useSSL;

    private HikariDataSource dataSource;

    public MySQLStorage(scc plugin, String host, int port, String database, String username, String password,
            boolean useSSL) {
        this.plugin = plugin;
        this.host = host;
        this.port = port;
        this.database = database;
        this.username = username;
        this.password = password;
        this.useSSL = useSSL;
    }

    @Override
    public boolean initialize() {
        try {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(
                    "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + useSSL + "&serverTimezone=UTC");
            config.setUsername(username);
            config.setPassword(password);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");

            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setConnectionTimeout(30000);
            config.setIdleTimeout(600000);
            config.setMaxLifetime(1800000);

            dataSource = new HikariDataSource(config);

            try (Connection connection = dataSource.getConnection()) {
                createTables(connection);
                plugin.getLogger().info("Successfully connected to MySQL database!");
                return true;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to MySQL: " + e.getMessage());
            return false;
        }
    }

    private void createTables(Connection connection) throws SQLException {
        String createTableSQL = """
                CREATE TABLE IF NOT EXISTS chunk_collectors (
                    uuid VARCHAR(36) PRIMARY KEY,
                    owner_uuid VARCHAR(36) NOT NULL,
                    owner_name VARCHAR(16) NOT NULL,
                    world VARCHAR(64) NOT NULL,
                    x DOUBLE NOT NULL,
                    y DOUBLE NOT NULL,
                    z DOUBLE NOT NULL,
                    yaw FLOAT NOT NULL,
                    pitch FLOAT NOT NULL,
                    created_at BIGINT NOT NULL,
                    time_remaining BIGINT NOT NULL,
                    items_collected INT NOT NULL,
                    active BOOLEAN NOT NULL,
                    max_charge_time BIGINT NOT NULL DEFAULT 0,
                    total_money_earned DOUBLE NOT NULL DEFAULT 0.0,
                    last_autosell_time BIGINT NOT NULL DEFAULT 0,
                    virtual_items TEXT,
                    INDEX idx_owner_uuid (owner_uuid),
                    INDEX idx_location (world, x, y, z)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """;

        try (PreparedStatement statement = connection.prepareStatement(createTableSQL)) {
            statement.executeUpdate();
        }
    }

    @Override
    public void shutdown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Override
    public boolean saveCollectors(List<ChunkCollector> collectors) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement clearStatement = connection.prepareStatement("DELETE FROM chunk_collectors")) {
                clearStatement.executeUpdate();
            }

            String insertSQL = """
                    INSERT INTO chunk_collectors (uuid, owner_uuid, owner_name, world, x, y, z, yaw, pitch,
                        created_at, time_remaining, items_collected, active, max_charge_time,
                        total_money_earned, last_autosell_time, virtual_items)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;

            try (PreparedStatement statement = connection.prepareStatement(insertSQL)) {
                for (ChunkCollector collector : collectors) {
                    setCollectorParameters(statement, collector);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save collectors to MySQL: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<ChunkCollector> loadCollectors() {
        try (Connection connection = dataSource.getConnection()) {
            String selectSQL = "SELECT * FROM chunk_collectors";
            List<ChunkCollector> collectors = new ArrayList<>();

            try (PreparedStatement statement = connection.prepareStatement(selectSQL);
                    ResultSet resultSet = statement.executeQuery()) {

                while (resultSet.next()) {
                    ChunkCollector collector = resultSetToCollector(resultSet);
                    if (collector != null) {
                        collectors.add(collector);
                    }
                }
            }

            return collectors;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load collectors from MySQL: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @Override
    public boolean saveCollector(ChunkCollector collector) {
        try (Connection connection = dataSource.getConnection()) {
            String upsertSQL = """
                    INSERT INTO chunk_collectors (uuid, owner_uuid, owner_name, world, x, y, z, yaw, pitch,
                        created_at, time_remaining, items_collected, active, max_charge_time,
                        total_money_earned, last_autosell_time, virtual_items)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        owner_uuid = VALUES(owner_uuid),
                        owner_name = VALUES(owner_name),
                        world = VALUES(world),
                        x = VALUES(x),
                        y = VALUES(y),
                        z = VALUES(z),
                        yaw = VALUES(yaw),
                        pitch = VALUES(pitch),
                        created_at = VALUES(created_at),
                        time_remaining = VALUES(time_remaining),
                        items_collected = VALUES(items_collected),
                        active = VALUES(active),
                        max_charge_time = VALUES(max_charge_time),
                        total_money_earned = VALUES(total_money_earned),
                        last_autosell_time = VALUES(last_autosell_time),
                        virtual_items = VALUES(virtual_items)
                    """;

            try (PreparedStatement statement = connection.prepareStatement(upsertSQL)) {
                setCollectorParameters(statement, collector);
                statement.executeUpdate();
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to save collector to MySQL: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteCollector(UUID collectorUuid) {
        try (Connection connection = dataSource.getConnection()) {
            String deleteSQL = "DELETE FROM chunk_collectors WHERE uuid = ?";

            try (PreparedStatement statement = connection.prepareStatement(deleteSQL)) {
                statement.setString(1, collectorUuid.toString());
                int rowsAffected = statement.executeUpdate();
                return rowsAffected > 0;
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to delete collector from MySQL: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean isConnected() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(5);
        } catch (Exception e) {
            return false;
        }
    }

    private void setCollectorParameters(PreparedStatement statement, ChunkCollector collector) throws SQLException {
        Location loc = collector.getLocation();

        statement.setString(1, collector.getUuid().toString());
        statement.setString(2, collector.getOwnerUuid().toString());
        statement.setString(3, collector.getOwnerName());
        statement.setString(4, loc.getWorld().getName());
        statement.setDouble(5, loc.getX());
        statement.setDouble(6, loc.getY());
        statement.setDouble(7, loc.getZ());
        statement.setFloat(8, loc.getYaw());
        statement.setFloat(9, loc.getPitch());
        statement.setLong(10, collector.getCreatedAt());
        statement.setLong(11, collector.getTimeRemaining());
        statement.setInt(12, collector.getItemsCollected());
        statement.setBoolean(13, collector.isActive());
        statement.setLong(14, collector.getMaxChargeTime());
        statement.setDouble(15, collector.getTotalMoneyEarned());
        statement.setLong(16, collector.getLastAutosellTime());
        statement.setString(17, serializeVirtualItems(collector.getVirtualItems()));
    }

    private ChunkCollector resultSetToCollector(ResultSet resultSet) throws SQLException {
        try {
            UUID uuid = UUID.fromString(resultSet.getString("uuid"));
            UUID ownerUuid = UUID.fromString(resultSet.getString("owner_uuid"));
            String ownerName = resultSet.getString("owner_name");

            String worldName = resultSet.getString("world");
            double x = resultSet.getDouble("x");
            double y = resultSet.getDouble("y");
            double z = resultSet.getDouble("z");
            float yaw = resultSet.getFloat("yaw");
            float pitch = resultSet.getFloat("pitch");

            org.bukkit.World world = plugin.getServer().getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("World not found: " + worldName + " for collector " + uuid);
                return null;
            }

            Location location = new Location(world, x, y, z, yaw, pitch);
            long createdAt = resultSet.getLong("created_at");
            long timeRemaining = resultSet.getLong("time_remaining");
            int itemsCollected = resultSet.getInt("items_collected");
            boolean active = resultSet.getBoolean("active");
            long maxChargeTime = resultSet.getLong("max_charge_time");
            double totalMoneyEarned = resultSet.getDouble("total_money_earned");
            long lastAutosellTime = resultSet.getLong("last_autosell_time");

            ChunkCollector collector = new ChunkCollector(uuid, ownerUuid, ownerName, location,
                    createdAt, timeRemaining, itemsCollected, active);
            collector.setMaxChargeTime(maxChargeTime);
            collector.setTotalMoneyEarned(totalMoneyEarned);
            collector.setLastAutosellTime(lastAutosellTime);

            String virtualItemsStr = resultSet.getString("virtual_items");
            if (virtualItemsStr != null && !virtualItemsStr.isEmpty()) {
            }

            return collector;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to convert result set to collector: " + e.getMessage());
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
