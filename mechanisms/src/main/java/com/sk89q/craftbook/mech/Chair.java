package com.sk89q.craftbook.mech;

import java.util.ArrayList;

import net.minecraft.server.v1_4_5.DataWatcher;
import net.minecraft.server.v1_4_5.Packet40EntityMetadata;
import net.minecraft.server.v1_4_5.WatchableObject;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_4_5.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import com.sk89q.craftbook.bukkit.BukkitPlayer;
import com.sk89q.craftbook.bukkit.MechanismsPlugin;


/**
 * @author Me4502
 */
public class Chair implements Listener {

    public Chair(MechanismsPlugin plugin) {

        this.plugin = plugin;
        Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new ChairChecker(), 40L, 40L);
    }

    public void addChair(Player player, Block block) {
        Packet40EntityMetadata packet = new Packet40EntityMetadata(player.getPlayer().getEntityId(), new ChairWatcher((byte) 4), false);
        for (Player play : plugin.getServer().getOnlinePlayers()) {
            if(play.getWorld().equals(player.getPlayer().getWorld()))
                ((CraftPlayer) play).getHandle().netServerHandler.sendPacket(packet);
        }
        if(plugin.getLocalConfiguration().chairSettings.chairs.containsKey(player.getName()))
            return;
        player.sendMessage(ChatColor.YELLOW + "You are now sitting.");
        plugin.getLocalConfiguration().chairSettings.chairs.put(player.getName(), block);
    }

    public void removeChair(Player player) {
        Packet40EntityMetadata packet = new Packet40EntityMetadata(player.getEntityId(), new ChairWatcher((byte) 0), false);
        for (Player play : plugin.getServer().getOnlinePlayers()) {
            if(play.getWorld().equals(player.getPlayer().getWorld()))
                ((CraftPlayer) play).getHandle().netServerHandler.sendPacket(packet);
        }
        player.sendMessage(ChatColor.YELLOW + "You are no longer sitting.");
        plugin.getLocalConfiguration().chairSettings.chairs.remove(player.getName());
    }

    public Block getChair(Player player) {
        return plugin.getLocalConfiguration().chairSettings.chairs.get(player.getName());
    }

    public Player getChair(Block player) {
        return Bukkit.getPlayer(plugin.getLocalConfiguration().chairSettings.chairs.inverse().get(player));
    }

    public boolean hasChair(Player player) {
        return plugin.getLocalConfiguration().chairSettings.chairs.containsKey(player.getName());
    }

    public boolean hasChair(Block player) {
        return plugin.getLocalConfiguration().chairSettings.chairs.containsValue(player);
    }

    private MechanismsPlugin plugin;

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {

        if (!plugin.getLocalConfiguration().chairSettings.enable) return;
        if (hasChair(event.getBlock())) {
            removeChair(getChair(event.getBlock()));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractEvent event) {

        if (!plugin.getLocalConfiguration().chairSettings.enable) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || !plugin.getLocalConfiguration().chairSettings.canUseBlock(event.getClickedBlock().getTypeId())) return;

        BukkitPlayer player = new BukkitPlayer(plugin, event.getPlayer());

        //Now everything looks good, continue;
        if (player.getPlayer().getItemInHand() == null || !player.getPlayer().getItemInHand().getType().isBlock() || player.getPlayer().getItemInHand().getTypeId() == 0) {
            if (plugin.getLocalConfiguration().chairSettings.requireSneak && !player.getPlayer().isSneaking())
                return;
            if (!player.hasPermission("craftbook.mech.chair.use")) {
                player.printError("mech.use-permission");
                return;
            }
            if (hasChair(player.getPlayer())) { //Stand
                removeChair(player.getPlayer());
            } else { //Sit
                if (hasChair(event.getClickedBlock())) {
                    Player p = getChair(event.getClickedBlock());
                    if(!p.isOnline() || !p.getWorld().equals(event.getClickedBlock().getWorld()) || p.getLocation().distanceSquared(event.getClickedBlock().getLocation()) > 1) {
                        removeChair(p);
                    }
                    else {
                        player.print("This seat is already occupied.");
                        return;
                    }
                }
                player.getPlayer().teleport(event.getClickedBlock().getLocation().add(0.5, 0, 0.5)); //Teleport to the seat
                addChair(player.getPlayer(), event.getClickedBlock());
                player.print("You are now sitting.");
            }
        }
    }

    public class ChairChecker implements Runnable {

        @Override
        public void run () {
            for(String pl : plugin.getLocalConfiguration().chairSettings.chairs.keySet()) {
                Player p = Bukkit.getPlayer(pl);
                if (p == null) continue;
                if(!plugin.getLocalConfiguration().chairSettings.canUseBlock(getChair(p).getTypeId()) || !p.getWorld().equals(getChair(p).getWorld()) || p.getLocation().distanceSquared(getChair(p).getLocation()) > 1)
                    removeChair(p); //Remove it. It's unused.
                else
                    addChair(p, getChair(p)); //For any new players.
            }
        }
    }

    public static class ChairWatcher extends DataWatcher {

        private byte metadata;

        public ChairWatcher(byte metadata) {

            this.metadata = metadata;
        }

        @Override
        public ArrayList<WatchableObject> b() {

            ArrayList<WatchableObject> list = new ArrayList<WatchableObject>();
            WatchableObject wo = new WatchableObject(0, 0, metadata);
            list.add(wo);
            return list;
        }
    }
}