package dev.linqfy.bigCasares.modules.tideframe;

import java.util.ArrayList;
import java.util.List;

/**
 * What {@code /bigcasares tideframe} reports to an operator: whether the embedded engine runs, whether its own pack
 * is what decides eligibility, which BigCasares screens are loaded, and how many online players can see them.
 *
 * @param running       the engine started with this BigCasares runtime
 * @param broken        a call found the engine's API changed: every feature keeps its classic menus
 * @param ownPack       the engine sends its own pack ({@code pack.send: true})
 * @param missions      missions use Tideframe ({@code tideframe-ui.missions})
 * @param shop          the shop uses Tideframe ({@code tideframe-ui.shop})
 * @param missingScreens BigCasares screens the engine has not loaded
 * @param java          online Java players
 * @param javaWithPack  of those, the ones with the engine's pack loaded
 * @param bedrock       online Bedrock players (they keep the forms)
 */
public record TideframeStatus(boolean running, boolean broken, boolean ownPack, boolean missions, boolean shop,
                              List<String> missingScreens, int java, int javaWithPack, int bedrock) {
    public TideframeStatus {
        missingScreens = List.copyOf(missingScreens);
    }

    /** The engine is off: the module is disabled or did not start. */
    public static TideframeStatus off() {
        return new TideframeStatus(false, false, false, false, false, List.of(), 0, 0, 0);
    }

    /** Chat lines, worst news first. */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        lines.add("§6Tideframe integrado");
        if (!running) {
            lines.add("§cMotor apagado: el módulo tideframe-ui está desactivado o no arrancó (ver el log). Se usan los menús clásicos.");
            return lines;
        }
        if (broken) {
            lines.add("§cLa API interna no coincide: misiones y tienda usan los menús clásicos hasta reiniciar.");
        }
        lines.add(ownPack ? "§aPack propio: se envía (pack.send: true)."
            : "§ePack propio: no se envía (pack.send: false). Misiones y tienda usan los menús clásicos.");
        lines.add("§7Misiones: " + (missions ? "§aTideframe" : "§eclásico") + " §7· Tienda: " + (shop ? "§aTideframe" : "§eclásico"));
        lines.add(missingScreens.isEmpty() ? "§aPantallas bigc_* cargadas."
            : "§cPantallas sin cargar: " + String.join(", ", missingScreens) + " (revisar plugins/BigCasares/tideframe/engine/screens).");
        lines.add("§7Jugadores Java: §f" + java + " §7(con el pack: §f" + javaWithPack + "§7) · Bedrock: §f" + bedrock + " §7(formularios)");
        return lines;
    }
}
