package com.cavetale.redis;

import lombok.Getter;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;

public final class RedisPlugin extends JavaPlugin {
    @Getter private static RedisPlugin instance;
    // --- Members
    private Jedis jedis;

    // --- JavaPlugin

    @Override
    public void onEnable() {
        instance = this;
        try {
            this.jedis = new Jedis("localhost");
            this.jedis.ping();
        } catch (Exception e) {
            getLogger().warning("Connection to Jedis failed. Disabling plugin.");
            setEnabled(false);
            return;
        }
    }

    @Override
    public void onDisable() {
        this.jedis = null;
        instance = null;
    }

    // --- Public API

    public static Jedis getJedis() {
        return instance.jedis;
    }

    // --- Command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 0) return false;
        switch (args[0]) {
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
}
