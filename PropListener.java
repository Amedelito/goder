package com.ytprops;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import com.destroystokyo.paper.event.entity.EntityRemoveFromWorldEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class PropListener implements Listener {

    private final YTPropsPlugin plugin;
    private final PropManager manager;

    public PropListener(YTPropsPlugin plugin, PropManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void msg(Player p, String text) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[Props] &7");
        p.sendMessage(PropManager.prefix(prefix + text));
    }

    /** Clic derecho con el item de un prop -> colocarlo. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = e.getItem();
        Optional<PropType> opt = manager.typeOfItem(item);
        if (opt.isEmpty()) return;

        // Que nunca se coloque una fogata vanilla con este item.
        e.setUseItemInHand(Event.Result.DENY);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (!p.hasPermission("ytprops.place")) {
            msg(p, "No tienes permiso para colocar props.");
            return;
        }
        Block clicked = e.getClickedBlock();
        if (clicked == null) return;
        Block base = clicked.isReplaceable() ? clicked : clicked.getRelative(e.getBlockFace());

        int quarter = PropManager.quarterFromYaw(p.getLocation().getYaw());
        String error = manager.place(opt.get(), base, quarter, p.getLocation().getX(), p.getLocation().getZ());
        if (error != null) {
            msg(p, "&cNo se puede colocar: " + error);
            return;
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            item.setAmount(item.getAmount() - 1);
        }
    }

    /** Agachado + clic izquierdo sobre el prop -> recogerlo. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onAttack(PrePlayerAttackEntityEvent e) {
        Entity target = e.getAttacked();
        if (!manager.isPropEntity(target)) return;
        e.setCancelled(true);

        Player p = e.getPlayer();
        if (!p.hasPermission("ytprops.break")) return;
        boolean needSneak = plugin.getConfig().getBoolean("break.require-sneak", true);
        if (needSneak && !p.isSneaking()) {
            p.sendActionBar(Component.text("Agáchate y golpea para recoger este prop"));
            return;
        }
        Optional<PropManager.Instance> inst = manager.instanceOf(target);
        if (inst.isEmpty()) return;
        boolean drop = plugin.getConfig().getBoolean("break.give-item-back", true)
                && p.getGameMode() != GameMode.CREATIVE;
        manager.remove(inst.get(), drop);
    }

    @EventHandler
    public void onAdd(EntityAddToWorldEvent e) {
        manager.register(e.getEntity());
    }

    @EventHandler
    public void onRemove(EntityRemoveFromWorldEvent e) {
        manager.unregister(e.getEntity());
    }
}
