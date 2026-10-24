package com.ytprops;

import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class YTPropsCommand implements CommandExecutor, TabCompleter {

    private final YTPropsPlugin plugin;
    private final PropManager manager;

    public YTPropsCommand(YTPropsPlugin plugin, PropManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private void msg(CommandSender s, String text) {
        String prefix = plugin.getConfig().getString("messages.prefix", "&6[Props] &7");
        s.sendMessage(PropManager.prefix(prefix + text));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("ytprops.admin")) {
            msg(sender, "&cNo tienes permiso.");
            return true;
        }
        if (args.length == 0) {
            msg(sender, "Uso: /" + label + " <give|place|list|remove|clear|reload>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                msg(sender, "Props disponibles:");
                for (PropType t : PropType.values()) {
                    sender.sendMessage(PropManager.prefix("&8 - &e" + t.id() + " &7(" + t.width() + "x" + t.depth()
                            + ", " + t.height() + " alto)"));
                }
            }
            case "give" -> give(sender, args);
            case "place" -> place(sender, args);
            case "remove" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Solo jugadores.");
                    return true;
                }
                Optional<PropManager.Instance> inst = manager.nearest(p.getLocation(), 6.0);
                if (inst.isEmpty()) {
                    msg(sender, "&cNo hay ningún prop a menos de 6 bloques.");
                } else {
                    manager.remove(inst.get(), false);
                    msg(sender, "Prop eliminado: &e" + inst.get().type.id());
                }
            }
            case "clear" -> {
                if (!(sender instanceof Player p)) {
                    msg(sender, "Solo jugadores.");
                    return true;
                }
                double radius = 10;
                if (args.length > 1) {
                    try {
                        radius = Math.min(100, Math.max(1, Double.parseDouble(args[1])));
                    } catch (NumberFormatException ex) {
                        msg(sender, "&cRadio inválido.");
                        return true;
                    }
                }
                List<PropManager.Instance> list = manager.within(p.getLocation(), radius);
                list.forEach(i -> manager.remove(i, false));
                msg(sender, "Props eliminados: &e" + list.size());
            }
            case "reload" -> {
                plugin.reloadConfig();
                msg(sender, "Configuración recargada.");
            }
            default -> msg(sender, "&cSubcomando desconocido.");
        }
        return true;
    }

    private void give(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "Uso: /ytprops give <id> [jugador] [cantidad]");
            return;
        }
        Optional<PropType> type = PropType.byId(args[1]);
        if (type.isEmpty()) {
            msg(sender, "&cProp desconocido. Usa /ytprops list");
            return;
        }
        Player target = null;
        if (args.length > 2) target = Bukkit.getPlayerExact(args[2]);
        else if (sender instanceof Player p) target = p;
        if (target == null) {
            msg(sender, "&cJugador no encontrado.");
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException ex) {
                msg(sender, "&cCantidad inválida.");
                return;
            }
        }
        ItemStack item = manager.createItem(type.get(), amount);
        final Player receiver = target;
        var left = receiver.getInventory().addItem(item);
        left.values().forEach(i -> receiver.getWorld().dropItemNaturally(receiver.getLocation(), i));
        msg(sender, "Entregado &e" + amount + "x " + type.get().displayName() + " &7a " + receiver.getName());
    }

    private void place(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            msg(sender, "Solo jugadores.");
            return;
        }
        if (args.length < 2) {
            msg(sender, "Uso: /ytprops place <id>  (mira un bloque del suelo)");
            return;
        }
        Optional<PropType> type = PropType.byId(args[1]);
        if (type.isEmpty()) {
            msg(sender, "&cProp desconocido. Usa /ytprops list");
            return;
        }
        Block target = p.getTargetBlockExact(8);
        if (target == null) {
            msg(sender, "&cMira un bloque a menos de 8 bloques.");
            return;
        }
        Block base = target.getRelative(BlockFace.UP);
        int quarter = PropManager.quarterFromYaw(p.getLocation().getYaw());
        String err = manager.place(type.get(), base, quarter, p.getLocation().getX(), p.getLocation().getZ());
        msg(sender, err == null ? "Colocado: &e" + type.get().displayName() : "&c" + err);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("ytprops.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "place", "list", "remove", "clear", "reload"));
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("place"))) {
            Arrays.stream(PropType.values()).map(PropType::id).forEach(out::add);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(pl -> out.add(pl.getName()));
        }
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
