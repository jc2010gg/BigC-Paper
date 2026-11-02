package dev.linqfy.bigCasares.modules.shop;

import dev.linqfy.bigCasares.items.CustomItemRegistry;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Buying or selling several lots with one click is one operation: one charge or payment, never part of it. */
class ShopServiceLotsTest {
    private static final ShopEntry STONE = new ShopEntry("stone", 10, Material.STONE, null, 32, 64.0, 4.0, null, List.of());

    private final List<ItemStack> given = new ArrayList<>();
    private ItemStack[] storage = new ItemStack[36];
    private final Economy economy = new Economy(1000.0);
    private final ShopService service = new ShopService(new ShopItemResolver(new CustomItemRegistry()), economy);
    private final Player player = player();

    @Test
    void buyingFourLotsChargesOnceAndGivesFourCanonicalStacks() {
        ShopTransactionResult result = service.buy(player, STONE, 4);

        assertTrue(result.success());
        assertEquals(1, economy.charges, "one charge for the whole price");
        assertEquals(1000.0 - 256.0, economy.balance);
        assertEquals(4, given.size());
        given.forEach(stack -> {
            assertEquals(Material.STONE, stack.getType());
            assertEquals(32, stack.getAmount());
        });
        assertEquals("Compraste stone x4 por $256.", result.message());
    }

    @Test
    void notEnoughForAllTheLotsBuysNoneOfThem() {
        economy.balance = 200.0;
        ShopTransactionResult result = service.buy(player, STONE, 4);

        assertFalse(result.success());
        assertEquals(0, economy.charges);
        assertEquals(200.0, economy.balance);
        assertEquals(List.of(), given);
    }

    @Test
    void sellingLotsNeedsThemAllAndPaysOnce() {
        storage[0] = new ItemStack(Material.STONE, 64);
        storage[5] = new ItemStack(Material.STONE, 40);
        ShopTransactionResult tooMany = service.sell(player, STONE, 4);
        assertFalse(tooMany.success(), "104 stones are not four lots of 32");
        assertEquals(1000.0, economy.balance);

        ShopTransactionResult sold = service.sell(player, STONE, 3);
        assertTrue(sold.success());
        assertEquals(1000.0 + 12.0, economy.balance);
        int left = 0;
        for (ItemStack stack : storage) left += stack == null ? 0 : stack.getAmount();
        assertEquals(104 - 96, left);
        assertEquals("Vendiste stone x3 por $12.", sold.message());
    }

    @Test
    void oneLotKeepsTheOldMessagesAndNoLotIsRefused() {
        assertEquals("Compraste stone por $64.", service.buy(player, STONE).message());
        assertThrows(IllegalArgumentException.class, () -> service.buy(player, STONE, 0));
        assertThrows(IllegalArgumentException.class, () -> service.sell(player, STONE, 65));
    }

    private Player player() {
        PlayerInventory inventory = (PlayerInventory) Proxy.newProxyInstance(PlayerInventory.class.getClassLoader(),
            new Class<?>[]{PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                case "addItem" -> {
                    for (ItemStack stack : (ItemStack[]) args[0]) given.add(stack.clone());
                    yield new HashMap<Integer, ItemStack>();
                }
                case "getStorageContents" -> storage.clone();
                case "setStorageContents" -> {
                    storage = ((ItemStack[]) args[0]).clone();
                    yield null;
                }
                default -> throw new AssertionError("Unexpected inventory call: " + method.getName());
            });
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getInventory" -> inventory;
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                default -> throw new AssertionError("Unexpected player call: " + method.getName());
            });
    }

    private static final class Economy extends ShopEconomyGateway {
        double balance;
        int charges;

        Economy(double balance) {
            super(null, null);
            this.balance = balance;
        }

        @Override
        public boolean has(Player player, double amount) {
            return balance >= amount;
        }

        @Override
        public boolean withdraw(Player player, double amount) {
            if (balance < amount) return false;
            balance -= amount;
            charges++;
            return true;
        }

        @Override
        public boolean deposit(Player player, double amount) {
            balance += amount;
            return true;
        }

        @Override
        public String format(double amount) {
            return "$" + (int) amount;
        }
    }
}
