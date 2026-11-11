package dev.linqfy.bigCasares.modules.schizophrenia;

import java.util.List;
import java.util.random.RandomGenerator;

/** Things the voices say. Plain strings so they stay easy to tweak and test. */
final class SchizophreniaLines {

    static final List<String> WHISPERS = List.of(
        "te estoy viendo",
        "date vuelta",
        "detrás tuyo",
        "no estás solo",
        "¿lo escuchaste?",
        "ya casi llego",
        "no confíes en ellos",
        "ellos hablan de vos",
        "abrí los ojos",
        "no mires",
        "sé dónde vivís",
        "quedate quieto",
        "¿por qué corrés?",
        "estuve acá todo el tiempo",
        "nadie más me ve",
        "contá los bloques",
        "escuchá las paredes",
        "no es real. ¿o sí?",
        "te están siguiendo",
        "mirá arriba",
        "nadie va a venir",
        "ya es tarde",
        "te dejaron solo",
        "no pertenecés acá",
        "¿escuchás cómo respiro?",
        "abrí la puerta",
        "estoy adentro",
        "no despiertes",
        "sigo acá. sigo acá. sigo acá.",
        "cuántos somos ahora",
        "no es quien dice ser",
        "no le abras la puerta",
        "no mires a la luna",
        "él te conoce",
        "ya no sos vos"
    );

    /** What an alternate says while it walks up wearing a friend's face. */
    static final List<String> ALTERNATE_LINES = List.of(
        "ey, soy yo",
        "abrime, soy yo",
        "¿por qué me mirás así?",
        "soy yo. soy yo. soy yo.",
        "no le hagas caso al otro",
        "dejame entrar"
    );

    /** An emergency broadcast that should not exist. */
    static final List<String> BROADCAST = List.of(
        "Se reportó un INTRUSO en su zona.",
        "Si alguien que conoce le pide entrar, NO lo deje pasar.",
        "No responda si escucha su nombre.",
        "No mire hacia afuera. No mire a la luna.",
        "Recuerde: no es quien dice ser.",
        "Este mensaje se repetirá."
    );

    static final List<String> VOID_LINES = List.of(
        "acá no hay nadie más",
        "te estuve esperando",
        "mirame",
        "no es quien dice ser",
        "ahora sos mío"
    );

    /** The last stage, while nothing living is in sight. */
    static final List<String> FRENZY_SEARCHING = List.of(
        "¿dónde se escondieron?",
        "todavía quedan",
        "los escucho respirar",
        "no pares",
        "buscalos"
    );

    /** The last stage, every few swings. */
    static final List<String> FRENZY_KILLING = List.of(
        "otra vez",
        "más",
        "no era real. ninguno era real.",
        "así se callan",
        "seguí"
    );

    /** The face that flashes in front of your eyes. */
    static final String FACE = String.join("\n",
        "▓▓▓▓          ▓▓▓▓",
        "▓██▓          ▓██▓",
        "▓▓▓▓          ▓▓▓▓",
        "",
        "",
        "▀▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▀",
        " ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀ ");

    static final List<String> TITLES = List.of(
        "NO ESTÁS SOLO",
        "DATE VUELTA",
        "TE VEO",
        "¿QUIÉN ESTÁ AHÍ?",
        "NO ES REAL",
        "DESPERTÁ",
        "CORRÉ",
        "ELLOS SABEN"
    );

    static final List<String> SUBTITLES = List.of(
        "las paredes respiran",
        "alguien te sigue",
        "no confíes en nadie",
        "¿escuchaste eso?",
        "tu cabeza miente",
        "no mires atrás"
    );

    /** Up to four lines each, written in red on a sign only the victim sees. */
    static final List<List<String>> WALL_MESSAGES = List.of(
        List.of("", "DATE", "VUELTA", ""),
        List.of("", "TE VEO", "", ""),
        List.of("NO", "ESTÁS", "SOLO", ""),
        List.of("", "ÉL ESTÁ", "ACÁ", ""),
        List.of("", "CORRÉ", "", ""),
        List.of("¿POR QUÉ", "NO ME", "MIRÁS?", ""),
        List.of("", "AYUDA", "", ""),
        List.of("NO", "DUERMAS", "", "")
    );

    static final List<String> PHANTOM_NAMES = List.of(
        "Herobrine", "nadie", "???", "Él", "tu_sombra", "observador", "null"
    );

    private SchizophreniaLines() {
    }

    static <T> T pick(List<T> lines, RandomGenerator random) {
        return lines.get(random.nextInt(lines.size()));
    }

    /** Replaces a share of the letters with §k-style noise markers handled by the caller. */
    static boolean[] glitchMask(String text, double share, RandomGenerator random) {
        boolean[] mask = new boolean[text.length()];
        for (int i = 0; i < text.length(); i++) {
            mask[i] = !Character.isWhitespace(text.charAt(i)) && random.nextDouble() < share;
        }
        return mask;
    }
}
