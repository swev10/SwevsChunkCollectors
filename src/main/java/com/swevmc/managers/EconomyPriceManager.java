package com.swevmc.managers;

import com.swevmc.scc;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.logging.Level;

public class EconomyPriceManager {

    private final scc plugin;
    private final ConfigManager configManager;
    private Plugin economyPlugin;
    private String economyPluginName;

    public EconomyPriceManager(scc plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        initializeEconomyPlugin();
    }

    private void initializeEconomyPlugin() {
        economyPluginName = configManager.getEconomyPlugin();

        switch (economyPluginName.toUpperCase()) {
            case "SHOPGUIPLUS":
                economyPlugin = plugin.getServer().getPluginManager().getPlugin("ShopGUIPlus");
                if (economyPlugin != null && economyPlugin.isEnabled()) {
                    plugin.getLogger().info("Successfully connected to ShopGUIPlus for item pricing");
                } else {
                    plugin.getLogger().warning("ShopGUIPlus not found or not enabled!");
                    economyPlugin = null;
                }
                break;

            case "VAULT":
                economyPlugin = plugin.getServer().getPluginManager().getPlugin("Vault");
                if (economyPlugin != null && economyPlugin.isEnabled()) {
                    plugin.getLogger().info("Successfully connected to Vault for item pricing");
                } else {
                    plugin.getLogger().warning("Vault not found or not enabled!");
                    economyPlugin = null;
                }
                break;

            case "ECONOMYSHOPGUI":
                economyPlugin = plugin.getServer().getPluginManager().getPlugin("EconomyShopGUI");
                if (economyPlugin != null && economyPlugin.isEnabled()) {
                    plugin.getLogger().info("Successfully connected to EconomyShopGUI for item pricing");
                } else {
                    plugin.getLogger().warning("EconomyShopGUI not found or not enabled!");
                    economyPlugin = null;
                }
                break;

            case "ECONOMYSHOPGUIPREMIUM":
                economyPlugin = plugin.getServer().getPluginManager().getPlugin("EconomyShopGUIPremium");
                if (economyPlugin != null && economyPlugin.isEnabled()) {
                    plugin.getLogger().info("Successfully connected to EconomyShopGUIPremium for item pricing");
                } else {
                    plugin.getLogger().warning("EconomyShopGUIPremium not found or not enabled!");
                    economyPlugin = null;
                }
                break;

            default:
                plugin.getLogger()
                        .warning("Unknown economy plugin: " + economyPluginName + ". Using fallback pricing.");
                economyPlugin = null;
                break;
        }
    }

    public double getItemPrice(Material material) {
        if (economyPlugin == null) {
            return configManager.getFallbackPrice();
        }

        double price = 0.0;

        try {
            switch (economyPluginName.toUpperCase()) {
                case "SHOPGUIPLUS":
                    price = getShopGUIPlusPrice(material);
                    break;
                case "VAULT":
                    price = getVaultPrice(material);
                    break;
                case "ECONOMYSHOPGUI":
                    price = getEconomyShopGUIPrice(material);
                    break;
                case "ECONOMYSHOPGUIPREMIUM":
                    price = getEconomyShopGUIPremiumPrice(material);
                    break;
                default:
                    price = configManager.getFallbackPrice();
                    break;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING,
                    "Error getting price for " + material.name() + " from " + economyPluginName, e);
            price = configManager.getFallbackPrice();
        }

        price *= configManager.getPriceMultiplier();

        if (economyPlugin != null && economyPlugin.isEnabled()) {
            return getChestShopPrice(material);
        }

        return configManager.getFallbackPrice();
    }

    private double getEconomyShopGUIPrice(Material material) {
        try {
            Class<?> economyShopGUIClass = Class.forName("com.github.sanctum.economyshopgui.api.EconomyShopGUI");
            Object api = economyShopGUIClass.getMethod("getInstance").invoke(null);

            Object priceInfo = economyShopGUIClass.getMethod("getSellPrice", String.class)
                    .invoke(api, material.name().toLowerCase());

            if (priceInfo != null) {
                return (Double) priceInfo;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get EconomyShopGUI price for " + material.name(), e);
        }

        return configManager.getFallbackPrice();
    }

    private double getEconomyShopGUIPremiumPrice(Material material) {
        try {

            Class<?> economyShopGUIClass = Class.forName("com.github.sanctum.economyshopgui.api.EconomyShopGUI");
            Object api = economyShopGUIClass.getMethod("getInstance").invoke(null);

            Object priceInfo = economyShopGUIClass.getMethod("getSellPrice", String.class)
                    .invoke(api, material.name().toLowerCase());

            if (priceInfo != null) {
                return (Double) priceInfo;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get EconomyShopGUIPremium price for " + material.name(),
                    e);
        }

        return configManager.getFallbackPrice();
    }

    private double getChestShopPrice(Material material) {
        try {
            return configManager.getFallbackPrice();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get ChestShop price for " + material.name(), e);
        }

        return configManager.getFallbackPrice();
    }

    public boolean isEconomyPluginAvailable() {
        return economyPlugin != null && economyPlugin.isEnabled();
    }

    public String getEconomyPluginName() {
        return economyPluginName;
    }

    public void reload() {
        initializeEconomyPlugin();
    }

    public void reloadPrices() {
    }
}
