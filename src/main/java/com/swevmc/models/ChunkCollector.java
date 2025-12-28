package com.swevmc.models;

import com.swevmc.scc;
import com.swevmc.managers.HologramManager;
import com.swevmc.managers.ConfigManager;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ChunkCollector {

    private final UUID uuid;
    private final UUID ownerUuid;
    private final String ownerName;
    private final Location location;
    private final long createdAt;
    private long timeRemaining;
    private int itemsCollected;
    private boolean active;
    private long lastCollectionTime;

    private Map<Material, Integer> virtualItems;
    private double totalMoneyEarned;
    private long lastAutosellTime;
    private long maxChargeTime;

    public ChunkCollector(UUID ownerUuid, String ownerName, Location location) {
        this.uuid = UUID.randomUUID();
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.location = location.clone();
        this.createdAt = Instant.now().getEpochSecond();
        this.timeRemaining = 0;
        this.itemsCollected = 0;
        this.active = false;
        this.lastCollectionTime = 0;

        this.virtualItems = new HashMap<>();
        this.totalMoneyEarned = 0.0;
        this.lastAutosellTime = System.currentTimeMillis();
        this.maxChargeTime = 0;
    }

    public ChunkCollector(UUID uuid, UUID ownerUuid, String ownerName, Location location,
            long createdAt, long timeRemaining, int itemsCollected, boolean active) {
        this.uuid = uuid;
        this.ownerUuid = ownerUuid;
        this.ownerName = ownerName;
        this.location = location.clone();
        this.createdAt = createdAt;
        this.timeRemaining = timeRemaining;
        this.itemsCollected = itemsCollected;
        this.active = active;
        this.lastCollectionTime = 0;

        this.virtualItems = new HashMap<>();
        this.totalMoneyEarned = 0.0;
        this.lastAutosellTime = System.currentTimeMillis();
        this.maxChargeTime = timeRemaining;
    }

    public void createHologram() {
        HologramManager hologramManager = scc.getInstance().getHologramManager();
        hologramManager.createHologram(this);
    }

    public void updateHologram() {
        HologramManager hologramManager = scc.getInstance().getHologramManager();
        hologramManager.updateHologram(this);
    }

    public void removeHologram() {
        HologramManager hologramManager = scc.getInstance().getHologramManager();
        hologramManager.removeHologram(this);
    }

    public void collectItem(ItemStack item, Location itemLocation) {
        if (!active || timeRemaining <= 0)
            return;

        Material material = item.getType();
        int amount = item.getAmount();

        virtualItems.put(material, virtualItems.getOrDefault(material, 0) + amount);
        itemsCollected += amount;

        updateHologram();

        long currentTime = System.currentTimeMillis();
        int cooldown = scc.getInstance().getConfigManager().getCollectionEffectCooldown();
        if (currentTime - lastCollectionTime < cooldown) {
            return;
        }
        lastCollectionTime = currentTime;

        try {
            itemLocation.getWorld().playSound(itemLocation,
                    org.bukkit.Sound.valueOf(scc.getInstance().getConfigManager().getSound("item-collect")),
                    0.2f, 1.5f);
        } catch (IllegalArgumentException e) {
            itemLocation.getWorld().playSound(itemLocation, org.bukkit.Sound.ENTITY_ITEM_PICKUP, 0.2f, 1.5f);
        }

        itemLocation.getWorld().spawnParticle(
                org.bukkit.Particle.EXPLOSION,
                itemLocation.clone().add(0.0, 0.1, 0.0),
                1,
                0.05, 0.05, 0.05,
                0.0);
    }

    public void collectItem(ItemStack item) {
        collectItem(item, location);
    }

    public void tick() {
        if (!active)
            return;

        timeRemaining--;

        long currentTime = System.currentTimeMillis();
        long autosellInterval = scc.getInstance().getConfigManager().getAutosellInterval() * 1000;

        if (currentTime - lastAutosellTime >= autosellInterval) {
            performAutosell();
            lastAutosellTime = currentTime;
        }

        if (timeRemaining <= 0) {
            deactivate();
        } else {
            updateHologram();
        }
    }

    private void performAutosell() {
        if (virtualItems.isEmpty())
            return;

        scc plugin = scc.getInstance();
        ConfigManager config = plugin.getConfigManager();
        double totalEarned = 0.0;

        for (Map.Entry<Material, Integer> entry : virtualItems.entrySet()) {
            Material material = entry.getKey();
            int amount = entry.getValue();
            double pricePerItem = plugin.getEconomyPriceManager().getItemPrice(material);
            totalEarned += pricePerItem * amount;
        }

        if (totalEarned > 0) {
            Player owner = plugin.getServer().getPlayer(ownerUuid);
            if (owner != null && owner.isOnline()) {
                plugin.getEconomyManager().depositMoney(owner, totalEarned);

                String message = config.getMessage("autosell-complete")
                        .replace("${amount}", String.format("%.2f", totalEarned));
                owner.sendMessage(config.getPrefix() + message);
            } else {
                plugin.getLogger().info("Owner " + ownerName + " is offline, earned $"
                        + String.format("%.2f", totalEarned) + " from autosell");
            }

            totalMoneyEarned += totalEarned;
            virtualItems.clear();
            updateHologram();
        }
    }

    public long getMaxDuration(Player player) {
        long defaultSeconds = scc.getInstance().getConfigManager().getDefaultChargeMinutes() * 60L;

        if (player.hasPermission("chunkcollectors.recharge.unlimited")) {
            return defaultSeconds + (24 * 3600);
        }

        int extraHours = 0;
        for (int i = 100; i >= 1; i--) {
            if (player.hasPermission("chunkcollectors.recharge." + i)) {
                extraHours = i;
                break;
            }
        }

        return defaultSeconds + (extraHours * 3600L);
    }

    public boolean addCharge(Player player) {
        if (!player.getUniqueId().equals(ownerUuid) && !player.hasPermission("chunkcollector.admin")) {
            return false;
        }

        long maxDuration = getMaxDuration(player);
        if (timeRemaining >= maxDuration) {
            return false;
        }

        long defaultChargeSeconds = scc.getInstance().getConfigManager().getDefaultChargeMinutes() * 60L;
        long spaceRemaining = maxDuration - timeRemaining;
        long chargeToAdd = Math.min(defaultChargeSeconds, spaceRemaining);

        if (chargeToAdd <= 0) {
            return false;
        }

        double chargeMinutes = chargeToAdd / 60.0;
        scc plugin = scc.getInstance();
        double costPerMinute = plugin.getConfigManager().getRechargeCostPerMinute();
        double totalCost = chargeMinutes * costPerMinute;

        if (!plugin.getEconomyManager().hasEnoughMoney(player, totalCost)) {
            player.sendMessage(plugin.getConfigManager().getPrefix() +
                    plugin.getConfigManager().getMessage("insufficient-funds",
                            "${cost}", String.format("%.2f", totalCost)));
            return false;
        }

        if (!plugin.getEconomyManager().withdrawMoney(player, totalCost)) {
            return false;
        }

        timeRemaining += chargeToAdd;
        if (timeRemaining > maxChargeTime) {
            maxChargeTime = timeRemaining;
        }
        active = true;
        updateHologram();

        try {

            @SuppressWarnings("deprecation")
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(plugin.getConfigManager().getSound("collector-recharge"));
            location.getWorld().playSound(location, sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        }

        location.getWorld().spawnParticle(
                org.bukkit.Particle.valueOf(plugin.getConfigManager().getEffect("collector-recharge-particle")),
                location.clone().add(0.5, 1, 0.5), 20, 0.5, 0.5, 0.5, 0.1);

        player.sendMessage(plugin.getConfigManager().getPrefix() +
                plugin.getConfigManager().getMessage("collector-recharged"));

        return true;
    }

    public void deactivate() {
        active = false;
        timeRemaining = 0;
        updateHologram();

        try {
            location.getWorld().playSound(location,
                    org.bukkit.Sound.valueOf(scc.getInstance().getConfigManager().getSound("collector-depleted")),
                    1.0f, 0.5f);
        } catch (IllegalArgumentException e) {
            location.getWorld().playSound(location, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        }

        location.getWorld().spawnParticle(
                org.bukkit.Particle
                        .valueOf(scc.getInstance().getConfigManager().getEffect("collector-depleted-particle")),
                location.clone().add(0.5, 1, 0.5), 30, 0.5, 0.5, 0.5, 0.1);
    }

    public ItemStack getGuiItem() {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();

        scc plugin = scc.getInstance();
        ConfigManager config = plugin.getConfigManager();

        meta.setDisplayName(config.getGuiCollectorInfo());

        List<String> lore = new ArrayList<>();

        double costForDefault = config.getDefaultChargeMinutes() * config.getRechargeCostPerMinute();

        lore.add(config.getGuiRechargeCost().replace("${cost}", String.format("%.2f", costForDefault)));
        lore.add(config.getGuiTimeRemaining().replace("${time}", formatTime(timeRemaining)));
        lore.add(config.getGuiMoneyEarned().replace("${amount}", String.format("%.2f", totalMoneyEarned)));

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    public double getRechargeCost() {
        scc plugin = scc.getInstance();
        return plugin.getConfigManager().getRechargeCostPerMinute();
    }

    private String formatTime(long timeRemaining) {
        if (timeRemaining <= 0) {
            return "§cNo Charge";
        }

        long hours = timeRemaining / 3600;
        long minutes = (timeRemaining % 3600) / 60;
        long seconds = timeRemaining % 60;

        if (hours > 0) {
            return String.format("§e%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("§e%dm %ds", minutes, seconds);
        } else {
            return String.format("§e%ds", seconds);
        }
    }

    public boolean isOwner(Player player) {
        return player.getUniqueId().equals(ownerUuid);
    }

    public boolean isOwner(UUID playerUuid) {
        return playerUuid.equals(ownerUuid);
    }

    public UUID getUuid() {
        return uuid;
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public Location getLocation() {
        return location.clone();
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getTimeRemaining() {
        return timeRemaining;
    }

    public int getItemsCollected() {
        return itemsCollected;
    }

    public boolean isActive() {
        return active;
    }

    public Map<Material, Integer> getVirtualItems() {
        return new HashMap<>(virtualItems);
    }

    public double getTotalMoneyEarned() {
        return totalMoneyEarned;
    }

    public long getLastAutosellTime() {
        return lastAutosellTime;
    }

    public void setTotalMoneyEarned(double totalMoneyEarned) {
        this.totalMoneyEarned = totalMoneyEarned;
    }

    public void setLastAutosellTime(long lastAutosellTime) {
        this.lastAutosellTime = lastAutosellTime;
    }

    public long getMaxChargeTime() {
        return maxChargeTime;
    }

    public void setMaxChargeTime(long maxChargeTime) {
        this.maxChargeTime = maxChargeTime;
    }

    public String getBatteryBars() {
        if (timeRemaining <= 0) {
            return "&7||||||||||";
        }

        int totalBars = 10;
        long maxTime = Math.max(maxChargeTime, scc.getInstance().getConfigManager().getDefaultChargeMinutes() * 60);
        double percentage = (double) timeRemaining / maxTime;
        int filledBars = (int) Math.round(percentage * totalBars);

        StringBuilder battery = new StringBuilder();

        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                battery.append("&a|");
            } else {
                battery.append("&7|");
            }
        }

        return battery.toString();
    }
}
