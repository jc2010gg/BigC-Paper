package dev.linqfy.bigCasares.modules.tideframe;

import dev.linqfy.bigCasares.modules.missions.MissionHudController;
import dev.linqfy.bigCasares.modules.missions.MissionHudView;
import dev.linqfy.bigCasares.modules.shop.ShopCategory;
import dev.linqfy.bigCasares.modules.shop.ShopGuiController;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuInventoryProtectionTest {
    @Test
    void shopRejectsDragsIntoItsEmptySlots() throws ReflectiveOperationException {
        var holder = Class.forName(ShopGuiController.class.getName() + "$CategoryMenuHolder").getDeclaredConstructor();
        holder.setAccessible(true);
        assertDragBoundary(new ShopGuiController(null, null, null), (InventoryHolder) holder.newInstance(), 27);
    }

    @Test
    void missionsRejectsDragsIntoItsEmptySlots() throws ReflectiveOperationException {
        var holder = Class.forName(MissionHudController.class.getName() + "$MissionMenuHolder")
            .getDeclaredConstructor(MissionHudView.class);
        holder.setAccessible(true);
        assertDragBoundary(new MissionHudController(null, null),
            (InventoryHolder) holder.newInstance(MissionHudView.MAIN_MENU), 54);
    }

    @Test
    void shopClosesOpenProductMenusDuringTeardown() throws ReflectiveOperationException {
        var holder = Class.forName(ShopGuiController.class.getName() + "$ItemMenuHolder")
            .getDeclaredConstructor(ShopCategory.class);
        holder.setAccessible(true);
        AtomicInteger closedShop = new AtomicInteger();
        AtomicInteger closedChest = new AtomicInteger();
        Player customer = viewing(menuView((InventoryHolder) holder.newInstance((Object) null), 54), closedShop);
        Player otherPlayer = viewing(menuView(null, 27), closedChest);
        Server server = proxy(Server.class, (proxy, method, args) -> {
            if (method.getName().equals("getOnlinePlayers")) {
                return List.of(customer, otherPlayer);
            }
            throw new UnsupportedOperationException(method.getName());
        });
        var bukkitServer = Bukkit.class.getDeclaredField("server");
        bukkitServer.setAccessible(true);
        Object previous = bukkitServer.get(null);
        bukkitServer.set(null, server);
        try {
            new ShopGuiController(null, null, null).closeAll();
        } finally {
            bukkitServer.set(null, previous);
        }
        assertEquals(1, closedShop.get());
        assertEquals(0, closedChest.get());
    }

    private static void assertDragBoundary(Listener controller, InventoryHolder holder, int topSize)
        throws ReflectiveOperationException {
        InventoryView view = menuView(holder, topSize);
        ItemStack stone = new ItemStack(Material.STONE);
        InventoryDragEvent bothInventories = new InventoryDragEvent(view, null, stone, false,
            Map.of(topSize - 1, stone, topSize, stone));
        InventoryDragEvent playerInventory = new InventoryDragEvent(view, null, stone, false, Map.of(topSize, stone));

        for (var method : controller.getClass().getMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)
                && Arrays.equals(method.getParameterTypes(), new Class<?>[] {InventoryDragEvent.class})) {
                method.invoke(controller, bothInventories);
                method.invoke(controller, playerInventory);
            }
        }
        assertTrue(bothInventories.isCancelled(), "the menu must not accept player items");
        assertFalse(playerInventory.isCancelled(), "dragging only in the player inventory is safe");
    }

    private static InventoryView menuView(InventoryHolder holder, int topSize) {
        Inventory top = proxy(Inventory.class, (proxy, method, args) -> switch (method.getName()) {
            case "getHolder" -> holder;
            case "getSize" -> topSize;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        return proxy(InventoryView.class, (proxy, method, args) -> switch (method.getName()) {
            case "getTopInventory" -> top;
            case "convertSlot" -> (int) args[0] % topSize;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Player viewing(InventoryView view, AtomicInteger closeCount) {
        return proxy(Player.class, (proxy, method, args) -> switch (method.getName()) {
            case "getOpenInventory" -> view;
            case "closeInventory" -> {
                closeCount.incrementAndGet();
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
