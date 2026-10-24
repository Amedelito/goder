package com.ytprops;

import org.bukkit.plugin.java.JavaPlugin;

public final class YTPropsPlugin extends JavaPlugin {

    private PropManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.manager = new PropManager(this);
        getServer().getPluginManager().registerEvents(new PropListener(this, manager), this);

        YTPropsCommand cmd = new YTPropsCommand(this, manager);
        var pc = getCommand("ytprops");
        if (pc != null) {
            pc.setExecutor(cmd);
            pc.setTabCompleter(cmd);
        }
        manager.registerLoadedProps();
        manager.startTask();
        getLogger().info("YTProps activado: " + PropType.values().length + " props disponibles.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.stopTask();
    }

    public PropManager manager() {
        return manager;
    }
}
