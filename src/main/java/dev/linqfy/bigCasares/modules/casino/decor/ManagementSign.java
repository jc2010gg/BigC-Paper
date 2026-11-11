package dev.linqfy.bigCasares.modules.casino.decor;

import dev.linqfy.bigCasares.modules.casino.Attraction;
import dev.linqfy.bigCasares.modules.casino.CasinoContext;
import dev.linqfy.bigCasares.modules.casino.Installation;
import dev.linqfy.bigCasares.modules.casino.world.CasinoText;
import dev.linqfy.bigCasares.modules.casino.world.Frame;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.TextDisplay;

import java.util.List;

/** A flickering neon sign hung on a wall. The installation's variant picks the text. */
public final class ManagementSign extends Attraction {

    public static final List<String> TEXTS = List.of(
        "§c§lLA GERENCIA NO SE RESPONSABILIZA\n§fPOR PÉRDIDA DE ÓRGANOS",
        "§e§lPROHIBIDO CONTAR CARTAS\n§7(y los dedos también)",
        "§d§lSE ACEPTAN RIÑONES\n§fCOMO GARANTÍA",
        "§b§lEL QUE PIERDE, PAGA.\n§fEL QUE GANA, TAMBIÉN.",
        "§a§lGRAN CASARES\n§7Desde que la AFIP no mira",
        "§6§lNO SE FÍA\n§7Para eso está Don Casares");

    private TextDisplay text;
    private int flicker;

    public ManagementSign(CasinoContext context, Installation installation, Frame frame) {
        super(context, installation, frame);
    }

    @Override
    public void spawn() {
        context.kit().block(props, frame.at(0, -0.1, -0.05), Material.BLACK_CONCRETE.createBlockData(), 2.9f, 0.85f, 0.04f);
        text = context.kit().text(props, frame.at(0, 0, 0), CasinoText.legacy(textFor(installation.variant())), 0.7f,
            false, 0x00000000);
        text.setGlowing(true);
        text.setGlowColorOverride(Color.fromRGB(0xFF3399));
        text.setSeeThrough(false);
    }

    @Override
    protected void onTick(long tick) {
        if (text == null || !text.isValid()) {
            return;
        }
        if (flicker > 0) {
            flicker--;
            text.setTextOpacity((byte) (flicker % 2 == 0 ? -1 : 60));
        } else if (context.random().nextInt(400) == 0) {
            flicker = 6;
        }
    }

    public static String textFor(String variant) {
        try {
            int index = Integer.parseInt(variant);
            return TEXTS.get(Math.floorMod(index, TEXTS.size()));
        } catch (NumberFormatException custom) {
            return variant == null || variant.isBlank() ? TEXTS.getFirst() : variant.replace("\\n", "\n").replace('&', '§');
        }
    }
}
