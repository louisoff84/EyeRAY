package fr.craftpick.eyeray;

import fr.craftpick.eyeray.command.EyeRayCommand;
import fr.craftpick.eyeray.config.EyeRaySettings;
import fr.craftpick.eyeray.engine.OreObfuscationEngine;
import fr.craftpick.eyeray.listener.PlayerListener;
import fr.craftpick.eyeray.listener.WorldListener;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class EyeRayPlugin extends JavaPlugin {
    private OreObfuscationEngine engine;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        EyeRaySettings settings = EyeRaySettings.load(getConfig());
        engine = new OreObfuscationEngine(this, settings);

        getServer().getPluginManager().registerEvents(new PlayerListener(engine), this);
        getServer().getPluginManager().registerEvents(new WorldListener(engine), this);

        PluginCommand command = getCommand("eyeray");
        if (command == null) {
            throw new IllegalStateException("Command 'eyeray' missing from plugin.yml");
        }
        EyeRayCommand executor = new EyeRayCommand(this, engine);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        engine.start();
        getServer().getOnlinePlayers().forEach(engine::onPlayerJoin);

        getLogger().info("EyeRAY " + getPluginMeta().getVersion() + " enabled. Client-side anti-XRay is "
            + (engine.isRuntimeEnabled() ? "ACTIVE" : "DISABLED") + ".");
    }

    @Override
    public void onDisable() {
        if (engine != null) engine.shutdown();
    }

    public void reloadEyeRay() {
        reloadConfig();
        engine.reload(EyeRaySettings.load(getConfig()));
    }
}
