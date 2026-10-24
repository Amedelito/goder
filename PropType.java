package com.ytprops;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Catálogo de props. El id debe coincidir con el nombre del modelo JSON
 * (assets/ytcustom/models/prop/<id>.json) y el cmd con el override de item/campfire.json.
 */
public enum PropType {
    FOGATA_GRANDE("fogata_grande", "Fogata Grande (3x3)", 91001, 3, 3, 3, 15, Fire.FIRE, 1.3, 1),
    FOGATA_MEDIANA("fogata_mediana", "Fogata Mediana (2x2)", 91002, 2, 2, 3, 14, Fire.FIRE, 0.9, 1),
    FOGATA_PEQUENA("fogata_pequena", "Fogata Pequeña (1x1)", 91003, 1, 1, 2, 12, Fire.FIRE, 0.45, 1),
    BRASERO("brasero", "Brasero de Hierro", 91004, 1, 1, 3, 14, Fire.FIRE, 0.4, 1),
    ASADOR("asador", "Asador con Caldero (3x1)", 91005, 3, 1, 3, 12, Fire.FIRE, 0.5, 0),
    PILA_LENA("pila_lena", "Pila de Leña (2x1)", 91006, 2, 1, 2, 0, Fire.NONE, 0, 0),
    TOCON("tocon", "Tocón", 91007, 1, 1, 1, 0, Fire.NONE, 0, 0),
    PILA_HUESOS("pila_huesos", "Pila de Huesos (2x2)", 91008, 2, 2, 1, 0, Fire.NONE, 0, 0),
    FAROL_POSTE("farol_poste", "Farol de Poste", 91009, 1, 1, 3, 15, Fire.NONE, 0, 1),
    BANCO("banco", "Banco de Tronco (2x1)", 91010, 2, 1, 1, 0, Fire.NONE, 0, 0),

    FOGATA_GRANDE_ALMA("fogata_grande_alma", "Fogata de Almas Grande (3x3)", 91101, 3, 3, 3, 10, Fire.SOUL, 1.3, 1),
    FOGATA_MEDIANA_ALMA("fogata_mediana_alma", "Fogata de Almas Mediana (2x2)", 91102, 2, 2, 3, 10, Fire.SOUL, 0.9, 1),
    FOGATA_PEQUENA_ALMA("fogata_pequena_alma", "Fogata de Almas Pequeña (1x1)", 91103, 1, 1, 2, 9, Fire.SOUL, 0.45, 1),
    BRASERO_ALMA("brasero_alma", "Brasero de Almas", 91104, 1, 1, 3, 10, Fire.SOUL, 0.4, 1),
    ASADOR_ALMA("asador_alma", "Asador de Almas (3x1)", 91105, 3, 1, 3, 9, Fire.SOUL, 0.5, 0),
    FAROL_POSTE_ALMA("farol_poste_alma", "Farol de Poste de Almas", 91106, 1, 1, 3, 12, Fire.NONE, 0, 1);

    public enum Fire { NONE, FIRE, SOUL }

    private final String id;
    private final String displayName;
    private final int cmd;
    private final int width;
    private final int depth;
    private final int height;
    private final int light;
    private final Fire fire;
    private final double coreRadius;
    private final int lightYOffset;

    PropType(String id, String displayName, int cmd, int width, int depth, int height,
             int light, Fire fire, double coreRadius, int lightYOffset) {
        this.id = id;
        this.displayName = displayName;
        this.cmd = cmd;
        this.width = width;
        this.depth = depth;
        this.height = height;
        this.light = light;
        this.fire = fire;
        this.coreRadius = coreRadius;
        this.lightYOffset = lightYOffset;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public int cmd() { return cmd; }
    public int width() { return width; }
    public int depth() { return depth; }
    public int height() { return height; }
    public int light() { return light; }
    public Fire fire() { return fire; }
    public double coreRadius() { return coreRadius; }
    public int lightYOffset() { return lightYOffset; }
    public boolean burns() { return fire != Fire.NONE; }
    public boolean soul() { return fire == Fire.SOUL || id.endsWith("_alma"); }

    public static Optional<PropType> byId(String id) {
        if (id == null) return Optional.empty();
        String k = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(p -> p.id.equals(k)).findFirst();
    }
}
