# YTProps – props 3D decorativos para Paper 1.21.1

Fogatas de **3x3** y **2x2**, fogata pequeña, brasero, asador con caldero, pila de leña, tocón,
pila de huesos, farol de poste y banco. Hay variantes de **fuego de almas** (azul) de varias.
Cada prop es un modelo JSON (resource pack) mostrado con un `item_display`, con hitbox
(`interaction`), luz (`light` block), partículas, sonido y daño por fuego.

> Código **no compilado ni probado en un servidor** (en el entorno donde se generó no hay `javac` ni
> acceso a Internet). Los modelos sí se generaron y se validaron (rango -16..32). Compila con Gradle 8+/Java 21.

## Props (`/ytprops list`)
| id | huella | notas |
|---|---|---|
| fogata_grande / _alma | 3x3 | anillo de piedras, troncos apilados y llamas altas |
| fogata_mediana / _alma | 2x2 | |
| fogata_pequena / _alma | 1x1 | |
| brasero / _alma | 1x1 | |
| asador / _alma | 3x1 | caldero colgando sobre fuego |
| pila_lena | 2x1 | |
| tocon | 1x1 | |
| pila_huesos | 2x2 | calavera y huesos, estética oscura |
| farol_poste / _alma | 1x1 | 3 bloques de alto |
| banco | 2x1 | |

## Instalación
1. `gradle build` → `build/libs/YTProps-1.0.0.jar` a `plugins/`.
2. Sube `YTProps-resourcepack.zip` a un hosting con URL directa y pide el pack a los jugadores
   (en `server.properties`: `resource-pack=` y `resource-pack-sha1=`; o por tu sistema de YTCustom).
3. `/ytprops give fogata_grande` → clic derecho en el suelo. Agachado + clic izquierdo para recogerla.

## Comandos / permisos
- `/ytprops give <id> [jugador] [cantidad]`, `/ytprops place <id>`, `/ytprops list`,
  `/ytprops remove` (el más cercano, 6 bloques), `/ytprops clear [radio]`, `/ytprops reload`
- `ytprops.admin` (op), `ytprops.place` (todos), `ytprops.break` (todos)

## Cómo funciona
- Item base: `minecraft:campfire` con `custom_model_data` 91001–91010 (normales) y 91101–91106 (almas),
  definidos en `assets/minecraft/models/item/campfire.json`.
- El modelo se exporta con el suelo en y=-16 y el centro de la huella en el centro del modelo, así que el
  `item_display` se coloca a +1.5 bloques sobre el suelo en el centro de la huella. Máximo 3x3x3 bloques.
- Texturas: todas son **vanilla** (cobblestone, campfire_fire animada, dark_oak_log, magma, bone_block…); no hay PNG propios.

## Integrarlo en YTCustom
- Copia `com.ytprops` a tu proyecto (cambia el `package`), registra el listener/comando en tu `onEnable`
  y añade `config.yml` / permisos. El `NamespacedKey` usa el plugin que le pases.
- En tu resource pack: copia `assets/ytcustom/models/prop/` y **fusiona** los `overrides` de
  `assets/minecraft/models/item/campfire.json` con los que ya tengas (si usas ese item para otra cosa).

## Probar sin plugin (solo el pack, modo creativo)
```
/summon item_display ~ ~1.5 ~ {item:{id:"minecraft:campfire",count:1,components:{"minecraft:custom_model_data":91001}},brightness:{block:15,sky:15},view_range:2.5f}
```
Cambia `91001` por otro id (91002 = 2x2, 91101 = fogata de almas 3x3…).

## Regenerar / editar modelos
`python3 tools/generate_models.py` regenera los JSON (carpeta `pack/…/prop`); `tools/preview.py` hace una vista previa
sin texturas. Para más props, añade una función en el generador, una entrada en `PropType.java` y un override en `campfire.json`.

## Limitaciones conocidas
- Los props no bloquean el paso (solo tienen hitbox para golpearlos); no respetan plugins de protección (WorldGuard…).
- Las llamas son planos con la textura animada de la fogata vanilla; con shaders pueden verse distintas.
- La orientación del modelo se ajusta a 90° según hacia dónde mire el jugador.
