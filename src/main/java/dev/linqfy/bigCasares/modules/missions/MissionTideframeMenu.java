package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.modules.tideframe.TideframeClick;
import dev.linqfy.bigCasares.modules.tideframe.TideframeScreenHandler;
import dev.linqfy.bigCasares.modules.tideframe.TideframeUi;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The missions as a read-only Tideframe screen for Java players: the daily or weekly list with each mission's progress,
 * status and reward. It changes nothing: claiming stays in the inventory menu, which its "Reclamar" button opens once
 * Tideframe has closed. The texts come from the same state the inventory menu reads. The X keeps Tideframe's own close.
 */
final class MissionTideframeMenu implements TideframeScreenHandler {
    static final String SCREEN = "bigc_misiones";
    static final String LIST = "bigc_misiones";
    /** Rows the screen's list shows; never more data rows than this (Tideframe's row index is then the data index). */
    static final int ROWS = 6;

    /** Runs a task on the server thread {@code ticks} later. */
    interface Later {
        void after(long ticks, Runnable task);
    }

    private final Function<Player, MissionPlayerState> states;
    private final Function<MissionPlayerState, Integer> claimables;
    private final DoubleFunction<String> money;
    private final Supplier<TideframeUi> ui;
    private final Later later;
    private final Consumer<Player> openClaims;
    private final Consumer<String> warn;
    private final Map<UUID, MissionScope> tabs = new ConcurrentHashMap<>();
    /** Players whose "Reclamar" is waiting for the screen's own close, within the click's step. */
    private final Map<UUID, Long> claiming = new ConcurrentHashMap<>();
    /** The installed screen's "Reclamar" did not close it: until BigCasares reloads, missions keep the inventory menu. */
    private final AtomicBoolean reclamarBroken = new AtomicBoolean();
    private AutoCloseable registration = () -> { };

    /**
     * {@code openClaims}: opens the inventory menu of rewards to claim, at once. {@code warn}: tells the server log that
     * the installed screen breaks the contract this menu relies on.
     */
    MissionTideframeMenu(Function<Player, MissionPlayerState> states, Function<MissionPlayerState, Integer> claimables,
                         DoubleFunction<String> money, Supplier<TideframeUi> ui, Later later, Consumer<Player> openClaims,
                         Consumer<String> warn) {
        this.states = Objects.requireNonNull(states, "states");
        this.claimables = Objects.requireNonNull(claimables, "claimables");
        this.money = Objects.requireNonNull(money, "money");
        this.ui = Objects.requireNonNull(ui, "ui");
        this.later = Objects.requireNonNull(later, "later");
        this.openClaims = Objects.requireNonNull(openClaims, "openClaims");
        this.warn = Objects.requireNonNull(warn, "warn");
    }

    /**
     * Shows {@code scope}'s missions; false when Tideframe cannot show the screen now (the caller opens the inventory
     * menu). When the opening fails later (pack refused, timeout), {@code fallback} runs instead.
     */
    boolean open(Player player, MissionScope scope, Runnable fallback) {
        TideframeUi tideframe = ui.get();
        if (reclamarBroken.get() || !tideframe.canShow(player, SCREEN)) {
            return false;
        }
        registration = tideframe.handle(SCREEN, this);
        UUID playerId = player.getUniqueId();
        tideframe.open(player, SCREEN, () -> {
            tabs.put(playerId, scope);
            render(tideframe, player, scope);
        }, opened -> {
            if (!opened) {
                tabs.remove(playerId);
                if (player.isOnline()) {
                    fallback.run();
                }
            }
        });
        return true;
    }

    @Override
    public boolean click(Player player, TideframeClick click) {
        MissionScope current = tabs.get(player.getUniqueId());
        if (current == null) {
            return false;
        }
        TideframeUi tideframe = ui.get();
        switch (click.elementId()) {
            case "diarias" -> show(tideframe, player, MissionScope.DAILY);
            case "semanales" -> show(tideframe, player, MissionScope.WEEKLY);
            case "reclamar" -> {
                if (click.rightClick()) {
                    return true;
                }
                // Not cancelled: the button's own "close" shuts this very screen in Tideframe, in this click's step, and
                // the inventory menu opens from that close (closed()). BigCasares never closes it here: Tideframe would do
                // that a tick later, possibly to a newer screen, with the inventory already open.
                UUID playerId = player.getUniqueId();
                long navigation = tideframe.navigationVersion(player);
                claiming.put(playerId, navigation);
                later.after(1, () -> {
                    if (claiming.remove(playerId, navigation)) {
                        reclamarNeverClosed(player);
                    }
                });
                return false;
            }
            // The X ("cerrar") keeps Tideframe's own close action; closed() hears it.
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public void closed(Player player) {
        UUID playerId = player.getUniqueId();
        tabs.remove(playerId);
        Long navigation = claiming.remove(playerId);
        if (navigation != null) {
            later.after(2, () -> {
                TideframeUi tideframe = ui.get();
                // A newer request supersedes this claim even while queued, or if its screen already closed again.
                if (player.isOnline() && navigation == tideframe.navigationVersion(player) && !tideframe.menuOpen(player)) {
                    openClaims.accept(player);
                }
            });
        }
    }

    /**
     * The installed screen's "Reclamar" did not close it (edited without its "close"): nothing opens under the menu, the
     * log says why, and missions use the inventory menu, where rewards are claimed, until BigCasares reloads.
     */
    private void reclamarNeverClosed(Player player) {
        if (reclamarBroken.compareAndSet(false, true)) {
            warn.accept("La pantalla " + SCREEN + " no se cerro con Reclamar: ese boton tiene que tener la accion close y la pantalla"
                + " no debe volver a otra al cerrar (ver docs/tideframe.md). Misiones usa su menu de siempre hasta recargar BigCasares.");
        }
        if (player.isOnline()) {
            player.sendMessage(ChatColor.YELLOW + "Cerrá este menú con la X y abrí de nuevo las misiones para reclamar.");
        }
    }

    /** BigCasares reloads: forget the open screens (the Tideframe module closes them) and stop receiving clicks. */
    void close() {
        tabs.clear();
        claiming.clear();
        try {
            registration.close();
        } catch (Exception ignored) {
            // The registration only removes a map entry.
        }
    }

    private void show(TideframeUi tideframe, Player player, MissionScope scope) {
        tabs.put(player.getUniqueId(), scope);
        render(tideframe, player, scope);
    }

    private void render(TideframeUi tideframe, Player player, MissionScope scope) {
        MissionPlayerState state = states.apply(player);
        boolean daily = scope == MissionScope.DAILY;
        Collection<MissionAssignment> assignments = (daily ? state.dailyAssignments() : state.weeklyAssignments()).values();
        Instant resetsAt = daily ? state.dailyResetsAt() : state.weeklyResetsAt();
        List<Map<String, String>> rows = rows(assignments, money);
        tideframe.setList(player, LIST, rows);
        tideframe.setVariable(player, "bigc_titulo", daily ? "Diarias" : "Semanales");
        int hidden = assignments.size() - rows.size();
        tideframe.setVariable(player, "bigc_reinicio", "se renuevan en " + MissionTexts.formatTimeRemaining(Duration.between(Instant.now(), resetsAt))
            + (hidden > 0 ? " · +" + hidden + " en el menú clásico" : ""));
        int pending = claimables.apply(state);
        tideframe.setVariable(player, "bigc_pendientes", pending == 0 ? "Nada para cobrar" : pending + " para cobrar");
        tideframe.setVariable(player, "bigc_cobrables", String.valueOf(pending));
        tideframe.setVariable(player, "bigc_vacio", rows.isEmpty() ? "No hay misiones activas" : "");
    }

    /** The list rows: at most {@link #ROWS}, in the order the inventory menu shows them. */
    static List<Map<String, String>> rows(Collection<MissionAssignment> assignments, DoubleFunction<String> money) {
        List<Map<String, String>> rows = new ArrayList<>();
        for (MissionAssignment assignment : assignments) {
            if (rows.size() == ROWS) {
                break;
            }
            MissionDefinition definition = assignment.definition();
            MissionProgressSnapshot snapshot = assignment.snapshot();
            Map<String, String> row = new LinkedHashMap<>();
            row.put("id", definition.id());
            row.put("titulo", plain(definition.title()));
            String count = Math.min(snapshot.progress(), definition.goal()) + "/" + definition.goal();
            row.put("estado", snapshot.claimed() ? "Cobrada" : snapshot.completed() ? "Para cobrar" : "En curso");
            // One column: the count while it runs, then whether it is ready to claim or already claimed.
            row.put("progreso", snapshot.claimed() ? "Cobrada" : snapshot.completed() ? "¡Lista!" : count);
            row.put("premio", plain(money.apply(definition.reward())));
            rows.add(row);
        }
        return rows;
    }

    /** Text without Minecraft colour codes (Tideframe draws its own colours). */
    static String plain(String text) {
        return text == null ? "" : text.replaceAll("(?i)[§&][0-9a-fk-orx]", "").strip();
    }
}
