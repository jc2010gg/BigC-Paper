package dev.linqfy.bigCasares.modules.elytrafailure;

/** The wing that failed; the player drifts towards that side. */
enum ElytraWing {
    LEFT("izquierda"),
    RIGHT("derecha");

    private final String displayName;

    ElytraWing(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    static ElytraWing fromRoll(double roll) {
        return roll < 0.5 ? LEFT : RIGHT;
    }

    static ElytraWing parse(String value) {
        if (value == null) {
            return null;
        }
        for (ElytraWing wing : values()) {
            if (wing.name().equalsIgnoreCase(value)) {
                return wing;
            }
        }
        return null;
    }
}
