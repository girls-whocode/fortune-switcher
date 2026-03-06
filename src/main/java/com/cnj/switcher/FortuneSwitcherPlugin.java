package com.cnj.switcher;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public class FortuneSwitcherPlugin extends JavaPlugin {

    private NamespacedKey storedFortuneKey;
    private NamespacedKey switchableToolKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        storedFortuneKey = new NamespacedKey(this, "stored_fortune_level");
        switchableToolKey = new NamespacedKey(this, "switchable_tool");

        SwitcherCommand switcherCommand = new SwitcherCommand(this);

        if (getCommand("switcher") != null) {
            getCommand("switcher").setExecutor(switcherCommand);
            getCommand("switcher").setTabCompleter(switcherCommand);
        } else {
            getLogger().severe("Command 'switcher' not found in plugin.yml");
        }

        getLogger().info("FortuneSwitcher enabled.");
    }

    public NamespacedKey getStoredFortuneKey() {
        return storedFortuneKey;
    }

    public NamespacedKey getSwitchableToolKey() {
        return switchableToolKey;
    }

    @Override
    public void onDisable() {
        getLogger().info("FortuneSwitcher disabled.");
    }
}