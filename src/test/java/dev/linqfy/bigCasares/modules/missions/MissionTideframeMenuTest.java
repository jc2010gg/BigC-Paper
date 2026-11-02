package dev.linqfy.bigCasares.modules.missions;

import dev.linqfy.bigCasares.testsupport.FakePlayer;
import dev.linqfy.bigCasares.testsupport.FakeTideframeUi;
import dev.linqfy.bigCasares.modules.tideframe.TideframeTestConnection;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MissionTideframeMenuTest {
    private static final String SCREEN = MissionTideframeMenu.SCREEN;

    private static MissionAssignment assignment(String id, MissionScope scope, String title, int goal, double reward,
                                                int progress, boolean completed, boolean claimed) {
        var definition = new MissionDefinition(id, scope, title, "", MissionType.values()[0], goal, reward, 1, true, Map.of());
        return new MissionAssignment(definition, new MissionProgressSnapshot(progress, completed, claimed, Set.of()));
    }

    private static Map<String, MissionAssignment> byId(List<MissionAssignment> assignments) {
        Map<String, MissionAssignment> out = new LinkedHashMap<>();
        for (MissionAssignment assignment : assignments) out.put(assignment.definition().id(), assignment);
        return out;
    }

    private final FakeTideframeUi ui = new FakeTideframeUi();
    private final FakePlayer juan = new FakePlayer("juan");
    private final List<String> claimsOpened = new ArrayList<>();
    private MissionPlayerState state = new MissionPlayerState(juan.id, Instant.now(), Instant.now().plus(Duration.ofHours(5)),
        Instant.now().plus(Duration.ofDays(3)),
        byId(List.of(
            assignment("piedra", MissionScope.DAILY, "§fPicar 64 de piedra", 64, 250, 23, false, false),
            assignment("zombis", MissionScope.DAILY, "Matar 10 zombis", 10, 400, 10, true, false),
            assignment("peces", MissionScope.DAILY, "Pescar 5 peces", 5, 150, 5, true, true))),
        byId(List.of(assignment("diamantes", MissionScope.WEEKLY, "Juntar 20 diamantes", 20, 2000, 3, false, false))),
        null);
    private final List<Runnable> later = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final MissionTideframeMenu menu = new MissionTideframeMenu(player -> state, ignored -> 1, amount -> "$" + (int) amount,
        () -> ui, (ticks, task) -> later.add(task), player -> claimsOpened.add(player.getName()), warnings::add);

    private List<String> column(Player player, String field) {
        return ui.list(player, MissionTideframeMenu.LIST).stream().map(row -> row.get(field)).toList();
    }

    /** The server's next ticks: runs what was scheduled. */
    private void tick() {
        List<Runnable> due = new ArrayList<>(later);
        later.clear();
        due.forEach(Runnable::run);
    }

    @Test
    void showsEachMissionsProgressStatusAndRewardFromThePlayersState() {
        assertTrue(menu.open(juan.player, MissionScope.DAILY, () -> { }));
        assertEquals(List.of("Picar 64 de piedra", "Matar 10 zombis", "Pescar 5 peces"), column(juan.player, "titulo"), "no colour codes");
        assertEquals(List.of("23/64", "¡Lista!", "Cobrada"), column(juan.player, "progreso"));
        assertEquals(List.of("$250", "$400", "$150"), column(juan.player, "premio"));
        assertEquals("Diarias", ui.variable(juan.player, "bigc_titulo"));
        assertTrue(ui.variable(juan.player, "bigc_reinicio").startsWith("se renuevan en 4h") || ui.variable(juan.player, "bigc_reinicio").startsWith("se renuevan en 5h"));
        assertEquals("1 para cobrar", ui.variable(juan.player, "bigc_pendientes"));
        assertEquals("1", ui.variable(juan.player, "bigc_cobrables"), "the screen shows Reclamar only then");
        assertEquals("", ui.variable(juan.player, "bigc_vacio"));
    }

    @Test
    void theTabsSwitchTheListAndNothingElseChanges() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertTrue(ui.click(juan.player, SCREEN, "semanales", -1, false));
        assertEquals(List.of("Juntar 20 diamantes"), column(juan.player, "titulo"));
        assertEquals("Semanales", ui.variable(juan.player, "bigc_titulo"));
        assertTrue(ui.click(juan.player, SCREEN, "diarias", -1, false));
        assertEquals(3, column(juan.player, "titulo").size());
        assertEquals(List.of(), claimsOpened, "read only: nothing was claimed");
    }

    @Test
    void reclamarOpensTheInventoryMenuOnlyAfterTideframeClosedTheScreen() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertTrue(ui.click(juan.player, SCREEN, "reclamar", -1, true), "a right click is swallowed: its close runs on left clicks");
        assertEquals(List.of(), later);
        assertFalse(ui.click(juan.player, SCREEN, "reclamar", -1, false), "not cancelled: the button's own close shuts this very screen");
        ui.closedByPlayer(juan.player, SCREEN); // Tideframe runs that close in the click's step
        assertEquals(List.of(), claimsOpened, "not in Tideframe's close itself");
        tick();
        assertEquals(List.of("juan"), claimsOpened, "once, by the inventory menu's own claim");
        assertEquals(List.of(), ui.closed, "BigCasares asked Tideframe for no close of its own");
        assertEquals(List.of(), warnings);
        assertFalse(ui.click(juan.player, SCREEN, "diarias", -1, false), "the closed screen keeps no view");
    }

    @Test
    void aTideframeMenuOpenedMeanwhileIsNeverCoveredByTheInventoryMenu() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        ui.click(juan.player, SCREEN, "reclamar", -1, false);
        ui.closedByPlayer(juan.player, SCREEN);
        ui.showOther(juan.player, "visual_menu09");
        tick();
        assertEquals(List.of(), claimsOpened);
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        ui.click(juan.player, SCREEN, "reclamar", -1, false);
        juan.online = false;
        ui.closedByPlayer(juan.player, SCREEN);
        tick();
        assertEquals(List.of(), claimsOpened, "nor for a player who left");
    }

    @Test
    void aScreenWhoseReclamarDoesNotCloseItHandsMissionsBackToTheInventoryMenu() {
        FakePlayer ana = new FakePlayer("ana");
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        menu.open(ana.player, MissionScope.DAILY, () -> { });
        // No close follows the click: the screen was edited without Reclamar's "close" (or to go back to another one).
        assertFalse(ui.click(juan.player, SCREEN, "reclamar", -1, false));
        tick();
        assertEquals(List.of(), claimsOpened, "nothing opens under the menu still up");
        assertEquals(List.of(), ui.closed, "and BigCasares does not close it a tick late");
        assertEquals(1, warnings.size(), "the log says what is wrong with the screen");
        assertEquals(1, juan.messages.size(), "the player is told how to claim");
        ui.closedByPlayer(juan.player, SCREEN); // the player closes it with the X later
        tick();
        assertEquals(List.of(), claimsOpened, "that later close is not taken for Reclamar's");
        assertFalse(menu.open(juan.player, MissionScope.DAILY, () -> { }), "missions use the inventory menu now");
        ui.click(ana.player, SCREEN, "reclamar", -1, false);
        tick();
        assertEquals(1, warnings.size(), "said once");
        assertEquals(1, ana.messages.size(), "each player still told");
    }

    @Test
    void neverMoreRowsThanTheScreenShowsAndTheRestIsNamed() {
        List<MissionAssignment> many = new ArrayList<>();
        for (int i = 0; i < 8; i++) many.add(assignment("m" + i, MissionScope.DAILY, "Mision " + i, 70, 10, 90, false, false));
        state = new MissionPlayerState(juan.id, Instant.now(), Instant.now().plus(Duration.ofHours(1)), Instant.now(), byId(many), Map.of(), null);
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertEquals(MissionTideframeMenu.ROWS, column(juan.player, "titulo").size());
        assertEquals("70/70", column(juan.player, "progreso").getFirst(), "progress past the goal shows the goal");
        assertTrue(ui.variable(juan.player, "bigc_reinicio").endsWith("+2 en el menú clásico"));
    }

    @Test
    void whenTideframeCannotShowItTheInventoryMenuOpens() {
        ui.showable = false;
        List<String> fallbacks = new ArrayList<>();
        assertFalse(menu.open(juan.player, MissionScope.DAILY, () -> fallbacks.add("inventory")));
        ui.showable = true;
        ui.opens = null;
        assertTrue(menu.open(juan.player, MissionScope.WEEKLY, () -> fallbacks.add("inventory")));
        ui.finishOpen(false);
        assertEquals(List.of("inventory"), fallbacks, "a refused pack or a timeout falls back to the inventory menu");
        assertFalse(ui.click(juan.player, SCREEN, "diarias", -1, false));
        juan.online = false;
        ui.opens = null;
        menu.open(juan.player, MissionScope.WEEKLY, () -> fallbacks.add("inventory"));
        ui.finishOpen(false);
        assertEquals(1, fallbacks.size(), "nothing opens for a player who left");
    }

    @Test
    void theBundledScreenMatchesWhatThisMenuSendsAndHandles() throws Exception {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        var contract = dev.linqfy.bigCasares.testsupport.TideframeScreenContract.load(SCREEN);
        contract.buttons(Set.of("diarias", "semanales", "reclamar"), Set.of("reclamar"));
        contract.lists(MissionTideframeMenu.LIST, MissionTideframeMenu.ROWS, Set.of("mision", "progreso", "premio"));
        contract.texts(ui.variables.get(juan.id).keySet());
    }

    @Test
    void theXIsLeftToTideframeAndClosingForgetsTheView() {
        menu.open(juan.player, MissionScope.DAILY, () -> { });
        assertFalse(ui.click(juan.player, SCREEN, "cerrar", -1, false), "the X keeps Tideframe's own close action");
        ui.closedByPlayer(juan.player, SCREEN);
        assertFalse(ui.click(juan.player, SCREEN, "semanales", -1, false));
        menu.close();
        assertFalse(ui.handlers.containsKey(SCREEN));
    }

    @Test
    void aNewerPendingOpenSupersedesTheDeferredClaimInventory() {
        TideframeTestConnection connection = new TideframeTestConnection();
        MissionTideframeMenu connected = new MissionTideframeMenu(player -> state, ignored -> 1, amount -> "$" + (int) amount,
            connection::ui, connection::after, player -> claimsOpened.add(player.getName()), warnings::add);
        connected.open(juan.player, MissionScope.DAILY, () -> { });
        connection.tick();
        connection.tick();
        assertFalse(connection.click(juan.player, SCREEN, "reclamar", -1));
        connection.closeWithCommand(juan.player, SCREEN, () -> { });
        connection.tick();
        connection.ui().open(juan.player, "bigc_tienda", ignored -> { });
        connection.tick();
        assertEquals(List.of(), claimsOpened, "a queued shop opening supersedes the old request before it is visible");
    }

    @Test
    void aNewerScreenClosingAgainDoesNotResurrectAnOldClaimRequest() {
        TideframeTestConnection connection = new TideframeTestConnection();
        MissionTideframeMenu connected = new MissionTideframeMenu(player -> state, ignored -> 1, amount -> "$" + (int) amount,
            connection::ui, connection::after, player -> claimsOpened.add(player.getName()), warnings::add);
        connected.open(juan.player, MissionScope.DAILY, () -> { });
        connection.tick();
        connection.tick();
        connection.click(juan.player, SCREEN, "reclamar", -1);
        connection.closeWithCommand(juan.player, SCREEN,
            () -> connection.ui().open(juan.player, "bigc_tienda", ignored -> { }));
        connection.tick();
        connection.closeWithCommand(juan.player, "bigc_tienda", () -> { });
        connection.tick();
        assertEquals(List.of(), claimsOpened, "the newer request wins even after its menu has closed");
    }
}
