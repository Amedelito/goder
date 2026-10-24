package com.ytprops;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Light;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PropManager {

    /** Instancia viva de un prop (se rellena al cargar sus entidades). */
    public static final class Instance {
        public final UUID group;
        public final PropType type;
        public final World world;
        public final double cx, cy, cz;
        int members;

        Instance(UUID group, PropType type, World world, double cx, double cy, double cz) {
            this.group = group;
            this.type = type;
            this.world = world;
            this.cx = cx;
            this.cy = cy;
            this.cz = cz;
        }

        public Location center() {
            return new Location(world, cx, cy, cz);
        }
    }

    private static final int[] FWD_X = {0, -1, 0, 1};
    private static final int[] FWD_Z = {1, 0, -1, 0};

    private final YTPropsPlugin plugin;
    private final NamespacedKey keyProp;
    private final NamespacedKey keyGroup;
    private final NamespacedKey keyCenter;
    private final Map<UUID, Instance> instances = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private BukkitTask task;
    private long tick;

    public PropManager(YTPropsPlugin plugin) {
        this.plugin = plugin;
        this.keyProp = new NamespacedKey(plugin, "prop");
        this.keyGroup = new NamespacedKey(plugin, "group");
        this.keyCenter = new NamespacedKey(plugin, "center");
    }

    // ------------------------------------------------------------------
    // Items
    // ------------------------------------------------------------------
    public ItemStack createItem(PropType type, int amount) {
        ItemStack it = new ItemStack(Material.CAMPFIRE, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(type.cmd());
        NamedTextColor color = type.soul() ? NamedTextColor.AQUA : NamedTextColor.GOLD;
        meta.displayName(Component.text(type.displayName(), color).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Prop decorativo 3D", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Clic derecho en el suelo para colocar", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Agachado + clic izquierdo para recoger", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(keyProp, PersistentDataType.STRING, type.id());
        it.setItemMeta(meta);
        return it;
    }

    public Optional<PropType> typeOfItem(ItemStack item) {
        if (item == null || item.getType() != Material.CAMPFIRE || !item.hasItemMeta()) return Optional.empty();
        String id = item.getItemMeta().getPersistentDataContainer().get(keyProp, PersistentDataType.STRING);
        return PropType.byId(id);
    }

    // ------------------------------------------------------------------
    // Colocación
    // ------------------------------------------------------------------
    public static int quarterFromYaw(float yaw) {
        return Math.floorMod(Math.round(yaw / 90f), 4);
    }

    private static int minCoord(int base, int size, int forward, double playerCoord) {
        if (size % 2 == 1) return base - (size - 1) / 2;
        if (forward > 0) return base - (size / 2 - 1);
        if (forward < 0) return base - size / 2;
        return playerCoord < base + 0.5 ? base - (size / 2 - 1) : base - size / 2;
    }

    /**
     * Intenta colocar un prop. 'base' es el bloque de suelo libre donde va el centro de la huella.
     * @return null si se colocó, o un mensaje de error.
     */
    public String place(PropType type, Block base, int quarter, double playerX, double playerZ) {
        World world = base.getWorld();
        boolean swap = quarter % 2 != 0;
        int effW = swap ? type.depth() : type.width();
        int effD = swap ? type.width() : type.depth();
        int minX = minCoord(base.getX(), effW, FWD_X[quarter], playerX);
        int minZ = minCoord(base.getZ(), effD, FWD_Z[quarter], playerZ);
        int y = base.getY();

        if (y + type.height() >= world.getMaxHeight() || y <= world.getMinHeight()) {
            return "Fuera del límite de altura del mundo.";
        }
        boolean needSupport = plugin.getConfig().getBoolean("placement.require-support", true);
        for (int x = minX; x < minX + effW; x++) {
            for (int z = minZ; z < minZ + effD; z++) {
                for (int dy = 0; dy < type.height(); dy++) {
                    Block b = world.getBlockAt(x, y + dy, z);
                    if (!(b.isEmpty() || b.isReplaceable() || b.isPassable())) {
                        return "No hay espacio libre (" + effW + "x" + effD + ", " + type.height() + " de alto).";
                    }
                }
                if (needSupport && !world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                    return "El suelo bajo el prop debe ser sólido.";
                }
            }
        }
        BoundingBox box = new BoundingBox(minX + 0.05, y + 0.05, minZ + 0.05,
                minX + effW - 0.05, y + type.height() - 0.05, minZ + effD - 0.05);
        boolean overlap = !world.getNearbyEntities(box,
                e -> e instanceof Interaction && e.getPersistentDataContainer().has(keyGroup, PersistentDataType.STRING)).isEmpty();
        if (overlap) return "Ya hay otro prop en ese lugar.";

        double cx = minX + effW / 2.0;
        double cz = minZ + effD / 2.0;
        spawn(type, world, cx, y, cz, minX, minZ, effW, effD, quarter);
        return null;
    }

    private void spawn(PropType type, World world, double cx, int y, double cz,
                       int minX, int minZ, int effW, int effD, int quarter) {
        UUID group = UUID.randomUUID();
        String center = cx + ";" + y + ";" + cz;
        float yaw = (quarter * 90f + 180f) % 360f;

        // Visual: item_display. El centro del modelo (8,8,8) cae en la posición de la entidad;
        // el modelo se exporta con el suelo en y=-16 => entidad a 1.5 bloques sobre el suelo.
        world.spawn(new Location(world, cx, y + 1.5, cz, yaw, 0f), ItemDisplay.class, d -> {
            d.setItemStack(createItem(type, 1));
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            d.setViewRange(2.5f);
            d.setDisplayWidth(Math.max(effW, effD) + 1.5f);
            d.setDisplayHeight(type.height() + 0.5f);
            d.setPersistent(true);
            if (type.burns() || type.light() > 0) {
                d.setBrightness(new Display.Brightness(type.burns() ? 15 : 12, 15));
            }
            tag(d.getPersistentDataContainer(), type, group, center);
        });

        // Hitbox: una Interaction por celda de la huella.
        for (int x = minX; x < minX + effW; x++) {
            for (int z = minZ; z < minZ + effD; z++) {
                world.spawn(new Location(world, x + 0.5, y, z + 0.5), Interaction.class, i -> {
                    i.setInteractionWidth(1.0f);
                    i.setInteractionHeight((float) type.height());
                    i.setResponsive(true);
                    i.setPersistent(true);
                    tag(i.getPersistentDataContainer(), type, group, center);
                });
            }
        }

        // Luz
        if (type.light() > 0) {
            Block lb = world.getBlockAt((int) Math.floor(cx), y + type.lightYOffset(), (int) Math.floor(cz));
            if (lb.isEmpty() || lb.getType() == Material.LIGHT) {
                Light data = (Light) Material.LIGHT.createBlockData();
                data.setLevel(Math.min(15, type.light()));
                lb.setBlockData(data, false);
            }
        }
    }

    private void tag(PersistentDataContainer pdc, PropType type, UUID group, String center) {
        pdc.set(keyProp, PersistentDataType.STRING, type.id());
        pdc.set(keyGroup, PersistentDataType.STRING, group.toString());
        pdc.set(keyCenter, PersistentDataType.STRING, center);
    }

    // ------------------------------------------------------------------
    // Registro de instancias (para efectos)
    // ------------------------------------------------------------------
    public void registerLoadedProps() {
        for (World w : plugin.getServer().getWorlds()) {
            for (Interaction i : w.getEntitiesByClass(Interaction.class)) register(i);
        }
    }

    public void register(Entity e) {
        if (!(e instanceof Interaction)) return;
        PersistentDataContainer pdc = e.getPersistentDataContainer();
        String g = pdc.get(keyGroup, PersistentDataType.STRING);
        String p = pdc.get(keyProp, PersistentDataType.STRING);
        String c = pdc.get(keyCenter, PersistentDataType.STRING);
        if (g == null || p == null || c == null) return;
        Optional<PropType> type = PropType.byId(p);
        if (type.isEmpty()) return;
        try {
            UUID group = UUID.fromString(g);
            String[] s = c.split(";");
            double cx = Double.parseDouble(s[0]);
            double cy = Double.parseDouble(s[1]);
            double cz = Double.parseDouble(s[2]);
            Instance inst = instances.computeIfAbsent(group,
                    k -> new Instance(group, type.get(), e.getWorld(), cx, cy, cz));
            inst.members++;
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Prop con datos corruptos: " + ex.getMessage());
        }
    }

    public void unregister(Entity e) {
        if (!(e instanceof Interaction)) return;
        String g = e.getPersistentDataContainer().get(keyGroup, PersistentDataType.STRING);
        if (g == null) return;
        try {
            UUID group = UUID.fromString(g);
            Instance inst = instances.get(group);
            if (inst != null && --inst.members <= 0) instances.remove(group);
        } catch (IllegalArgumentException ignored) {
        }
    }

    public Optional<Instance> instanceOf(Entity e) {
        String g = e.getPersistentDataContainer().get(keyGroup, PersistentDataType.STRING);
        if (g == null) return Optional.empty();
        try {
            return Optional.ofNullable(instances.get(UUID.fromString(g)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    public boolean isPropEntity(Entity e) {
        return e.getPersistentDataContainer().has(keyGroup, PersistentDataType.STRING);
    }

    public Optional<Instance> nearest(Location from, double maxDist) {
        Instance best = null;
        double bd = maxDist * maxDist;
        for (Instance i : instances.values()) {
            if (i.world != from.getWorld()) continue;
            double d = i.center().distanceSquared(from);
            if (d <= bd) {
                bd = d;
                best = i;
            }
        }
        return Optional.ofNullable(best);
    }

    public List<Instance> within(Location from, double radius) {
        List<Instance> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Instance i : instances.values()) {
            if (i.world == from.getWorld() && i.center().distanceSquared(from) <= r2) out.add(i);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Eliminación
    // ------------------------------------------------------------------
    /** Elimina todas las entidades del prop y su bloque de luz. */
    public void remove(Instance inst, boolean dropItem) {
        World w = inst.world;
        String gs = inst.group.toString();
        BoundingBox box = new BoundingBox(inst.cx - 3.2, inst.cy - 1, inst.cz - 3.2,
                inst.cx + 3.2, inst.cy + 5, inst.cz + 3.2);
        for (Entity e : w.getNearbyEntities(box, en -> gs.equals(
                en.getPersistentDataContainer().get(keyGroup, PersistentDataType.STRING)))) {
            e.remove();
        }
        if (inst.type.light() > 0) {
            Block lb = w.getBlockAt((int) Math.floor(inst.cx), (int) inst.cy + inst.type.lightYOffset(),
                    (int) Math.floor(inst.cz));
            if (lb.getType() == Material.LIGHT) lb.setType(Material.AIR, false);
        }
        instances.remove(inst.group);
        if (dropItem) {
            w.dropItemNaturally(inst.center().add(0, 0.6, 0), createItem(inst.type, 1));
        }
    }

    // ------------------------------------------------------------------
    // Efectos ambientales
    // ------------------------------------------------------------------
    public void startTask() {
        stopTask();
        this.task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tickEffects, 20L, 10L);
    }

    public void stopTask() {
        if (task != null) task.cancel();
        task = null;
    }

    private void tickEffects() {
        tick++;
        var cfg = plugin.getConfig();
        boolean particles = cfg.getBoolean("effects.particles", true);
        boolean sounds = cfg.getBoolean("effects.sounds", true);
        double radius = cfg.getDouble("effects.active-radius", 40.0);
        boolean dmgOn = cfg.getBoolean("damage.enabled", true);

        for (Instance inst : instances.values()) {
            if (!inst.type.burns()) continue;
            World w = inst.world;
            Location c = inst.center();
            if (!w.isChunkLoaded(c.getBlockX() >> 4, c.getBlockZ() >> 4)) continue;
            if (w.getNearbyPlayers(c, radius).isEmpty()) continue;

            boolean soul = inst.type.fire() == PropType.Fire.SOUL;
            double r = inst.type.coreRadius();
            int size = Math.max(inst.type.width(), inst.type.depth());

            if (particles) {
                w.spawnParticle(soul ? Particle.SOUL_FIRE_FLAME : Particle.FLAME,
                        c.getX(), c.getY() + 0.35, c.getZ(), 3 + size * 2, r * 0.7, 0.12, r * 0.7, 0.012);
                if (tick % 2 == 0) {
                    w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE,
                            c.getX() + (random.nextDouble() - 0.5) * r,
                            c.getY() + inst.type.height() * 0.55,
                            c.getZ() + (random.nextDouble() - 0.5) * r,
                            0, 0.0, 0.07, 0.0, 1.0);
                }
                if (random.nextInt(6) == 0) {
                    w.spawnParticle(Particle.LAVA, c.getX(), c.getY() + 0.6, c.getZ(), 1, r * 0.5, 0.1, r * 0.5, 0);
                }
            }
            if (sounds && random.nextInt(8) == 0) {
                w.playSound(c, Sound.BLOCK_CAMPFIRE_CRACKLE, SoundCategory.BLOCKS,
                        0.5f + random.nextFloat() * 0.4f, 0.8f + random.nextFloat() * 0.4f);
            }
            if (dmgOn && tick % 2 == 0) {
                double dmg = cfg.getDouble(soul ? "damage.soul" : "damage.fire", soul ? 2.0 : 1.0);
                boolean ignite = cfg.getBoolean("damage.set-on-fire", true) && !soul;
                for (LivingEntity le : w.getNearbyLivingEntities(c.clone().add(0, 0.5, 0), r, 0.75, r)) {
                    if (le instanceof Player p && (p.getGameMode() == GameMode.CREATIVE
                            || p.getGameMode() == GameMode.SPECTATOR)) continue;
                    le.damage(dmg);
                    if (ignite) le.setFireTicks(Math.max(le.getFireTicks(), 60));
                }
            }
        }
    }

    public static Component prefix(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy);
    }

    public static BlockFace[] horizontal() {
        return new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST};
    }
}
