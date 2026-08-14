package fr.craftpick.eyeray.command;

import fr.craftpick.eyeray.EyeRayPlugin;
import fr.craftpick.eyeray.compat.ServerCompat;
import fr.craftpick.eyeray.config.EyeRaySettings;
import fr.craftpick.eyeray.engine.OreObfuscationEngine;
import fr.craftpick.eyeray.stats.EyeRayStats;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class EyeRayCommand implements CommandExecutor, TabCompleter {
    private final EyeRayPlugin plugin;
    private final OreObfuscationEngine engine;

    public EyeRayCommand(EyeRayPlugin plugin, OreObfuscationEngine engine) {
        this.plugin = plugin;
        this.engine = engine;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        EyeRaySettings settings = engine.settings();
        if (!sender.hasPermission("eyeray.admin")) {
            sender.sendMessage(settings.prefix() + settings.noPermission());
            return true;
        }

        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        if ("status".equals(sub)) {
            sendStatus(sender);
        } else if ("reload".equals(sub)) {
            plugin.reloadEyeRay();
            sender.sendMessage(engine.settings().prefix() + engine.settings().reloaded());
        } else if ("toggle".equals(sub)) {
            boolean enable = !engine.isRuntimeEnabled();
            engine.setRuntimeEnabled(enable);
            sender.sendMessage(engine.settings().prefix() + (enable
                ? engine.settings().enabledMessage()
                : engine.settings().disabledMessage()));
        } else if ("rescan".equals(sub)) {
            handleRescan(sender, args);
        } else {
            sendHelp(sender, label);
        }
        return true;
    }

    private void handleRescan(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(engine.settings().prefix() + ChatColor.RED + "Joueur introuvable.");
                return;
            }
            engine.forceRescan(target);
            sender.sendMessage(engine.settings().prefix() + ChatColor.GREEN + "Rescan envoye pour " + target.getName() + ".");
            return;
        }
        engine.clearCacheAndRescan();
        sender.sendMessage(engine.settings().prefix() + ChatColor.GREEN + "Cache vide et rescan global programme.");
    }

    private void sendStatus(CommandSender sender) {
        EyeRayStats stats = engine.stats();
        EyeRaySettings settings = engine.settings();
        sender.sendMessage(ChatColor.AQUA + "EyeRAY " + ChatColor.GRAY + "v" + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Serveur: " + ChatColor.WHITE + ServerCompat.serverVersion());
        sender.sendMessage(ChatColor.GRAY + "Compatibilite: " + ChatColor.WHITE + "1.8.8 -> 26.2");
        sender.sendMessage(ChatColor.GRAY + "Etat: " + (engine.isRuntimeEnabled() ? ChatColor.GREEN + "ACTIVE" : ChatColor.RED + "DESACTIVE"));
        sender.sendMessage(ChatColor.GRAY + "Joueurs proteges: " + ChatColor.WHITE + engine.protectedPlayerCount());
        sender.sendMessage(ChatColor.GRAY + "Blocs clients falsifies: " + ChatColor.WHITE + engine.hiddenBlockCount());
        sender.sendMessage(ChatColor.GRAY + "Chunks en cache / queue: " + ChatColor.WHITE + engine.cachedChunks() + " / " + engine.queuedScans());
        sender.sendMessage(ChatColor.GRAY + "Rayon chunks / reveal: " + ChatColor.WHITE + settings.chunkRadius() + " / " + settings.revealDistance());
        sender.sendMessage(ChatColor.GRAY + "Chunks scannes: " + ChatColor.WHITE + stats.chunksScanned());
        sender.sendMessage(ChatColor.GRAY + "Minerais masques / leurres: " + ChatColor.WHITE + stats.oresHidden() + " / " + stats.decoysSent());
        sender.sendMessage(ChatColor.GRAY + "Blocs reveles: " + ChatColor.WHITE + stats.blocksRevealed());
        sender.sendMessage(ChatColor.GRAY + "BlockChanges envoyes: " + ChatColor.WHITE + stats.blockChangesSent());
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.AQUA + "EyeRAY commands:");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " status");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " reload");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " toggle");
        sender.sendMessage(ChatColor.GRAY + "/" + label + " rescan [joueur]");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("eyeray.admin")) return Collections.emptyList();
        if (args.length == 1) {
            return filter(Arrays.asList("status", "reload", "toggle", "rescan"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("rescan")) {
            List<String> players = new ArrayList<String>();
            for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName());
            return filter(players, args[1]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<String>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        }
        return result;
    }
}
