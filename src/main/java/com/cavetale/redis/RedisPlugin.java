package com.cavetale.redis;

import java.util.Collection;
import java.util.HashMap;
import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

@Getter
public final class RedisPlugin extends JavaPlugin implements Listener {
    public static final String PLAYERLIST_KEY = "cavetale.playerlist";
    private static final int ALIVE = 5;
    private String serverName;
    private Jedis jedis;

    // --- JavaPlugin

    @Override
    public void onEnable() {
        try {
            this.jedis = new Jedis("localhost");
            this.jedis.ping();
        } catch (Exception e) {
            getLogger().warning("Connection to Jedis failed. Disabling plugin.");
            setEnabled(false);
            return;
        }
        getServer().getPluginManager().registerEvents(this, this);
        importConfig();
        getServer().getScheduler().runTaskTimer(this, this::keepPlayerListAlive, 20L, 20L);
    }

    @Override
    public void onDisable() {
        removePlayerList();
        this.jedis = null;
    }

    void importConfig() {
        this.serverName = getConfig().getString("ServerName");
    }

    // --- Command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
        case "reload": {
            if (args.length != 1) return false;
            removePlayerList();
            importConfig();
            updatePlayerList();
            if (sender instanceof Player) sender.sendMessage("Config reloaded");
            getLogger().info("Config reloaded");
            return true;
        }
        case "get": {
            if (args.length != 2) return false;
            String key = args[1];
            sender.sendMessage("redis get `" + key + "` = \"" + this.jedis.get(key) + "\"");
            return true;
        }
        case "set": {
            if (args.length < 3) return false;
            String key = args[1];
            String value;
            {
                StringBuilder sb = new StringBuilder(args[2]);
                for (int i = 3; i < args.length; i += 1) sb.append(" ").append(args[i]);
                value = sb.toString();
            }
            this.jedis.set(key, value);
            sender.sendMessage("redis set `" + key + "` => \"" + value + "\"");
            return true;
        }
        default:
            return false;
        }
    }

    // --- EventHandlers

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        getServer().getScheduler().runTask(this, this::updatePlayerList);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        getServer().getScheduler().runTask(this, this::updatePlayerList);
    }

    // --- Player list

    void updatePlayerList() {
        Collection<? extends Player> players = getServer().getOnlinePlayers();
        if (players.isEmpty()) {
            getServer().getScheduler().runTaskAsynchronously(this, this::removePlayerList);
            return;
        }
        HashMap<String, String> map = new HashMap<>();
        for (Player player: players) {
            map.put(player.getUniqueId().toString(), player.getName());
        }
        final String key = PLAYERLIST_KEY + "." + this.serverName;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
                Transaction t = this.jedis.multi();
                t.del(key);
                t.hset(key, map);
                t.expire(key, ALIVE);
                t.exec();
            });
    }

    void keepPlayerListAlive() {
        final String key = PLAYERLIST_KEY + "." + this.serverName;
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
                this.jedis.expire(key, ALIVE);
            });
    }

    void removePlayerList() {
        final String key = PLAYERLIST_KEY + "." + this.serverName;
        this.jedis.del(key);
    }
}
