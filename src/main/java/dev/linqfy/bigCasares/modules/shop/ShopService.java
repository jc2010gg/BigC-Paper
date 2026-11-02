package dev.linqfy.bigCasares.modules.shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.ToIntFunction;

public final class ShopService {

    private final ShopItemResolver resolver;
    private final ShopEconomyGateway economy;
    private final ToIntFunction<Player> postGrantEnforcer;

    public ShopService(ShopItemResolver resolver, ShopEconomyGateway economy) {
        this(resolver, economy, player -> 0);
    }

    public ShopService(ShopItemResolver resolver, ShopEconomyGateway economy, ToIntFunction<Player> postGrantEnforcer) {
        this.resolver = resolver;
        this.economy = economy;
        this.postGrantEnforcer = postGrantEnforcer;
    }

    public ShopTransactionResult buy(ShopEntry entry) {
        if (!economy.has(entry.buyPrice())) {
            return ShopTransactionResult.failure("Saldo insuficiente.");
        }
        if (!economy.withdraw(entry.buyPrice())) {
            return ShopTransactionResult.failure("No se pudo cobrar la compra.");
        }
        return ShopTransactionResult.success("Compra realizada.", resolver.createStack(entry));
    }

    public ShopTransactionResult sell(List<ItemStack> contents, ShopEntry entry) {
        int total = contents.stream()
            .filter(stack -> resolver.matches(stack, entry))
            .mapToInt(ItemStack::getAmount)
            .sum();

        if (total < entry.amount()) {
            return ShopTransactionResult.failure("No tenes suficientes items para vender.");
        }
        if (!economy.deposit(entry.sellPrice())) {
            return ShopTransactionResult.failure("No se pudo pagar la venta.");
        }
        return ShopTransactionResult.success("Venta realizada.", null);
    }

    public ShopTransactionResult buy(Player player, ShopEntry entry) {
        return buy(player, entry, 1);
    }

    /**
     * {@code lots} times the entry in one operation: one check and one charge for the whole price, then each lot made
     * by the canonical factory (what does not fit drops at the player's feet, as for one lot).
     */
    public ShopTransactionResult buy(Player player, ShopEntry entry, int lots) {
        requireLots(lots);
        if (player == null) {
            return ShopTransactionResult.success("Compra realizada.", resolver.createStack(entry), postGrantEnforcer.applyAsInt(null));
        }
        double price = entry.buyPrice() * lots;
        if (!economy.has(player, price)) {
            return ShopTransactionResult.failure("Saldo insuficiente.");
        }
        if (!economy.withdraw(player, price)) {
            return ShopTransactionResult.failure("No se pudo cobrar la compra.");
        }

        ItemStack purchased = null;
        for (int lot = 0; lot < lots; lot++) {
            ItemStack stack = resolver.createStack(entry);
            if (purchased == null) {
                purchased = stack;
            }
            var leftovers = player.getInventory().addItem(stack);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        int overflowRemoved = postGrantEnforcer.applyAsInt(player);
        return ShopTransactionResult.success("Compraste " + entry.id() + times(lots) + " por " + economy.format(price) + ".", purchased, overflowRemoved);
    }

    public ShopTransactionResult sell(Player player, ShopEntry entry) {
        return sell(player, entry, 1);
    }

    /** {@code lots} times the entry in one operation: all of them or none, paid once. */
    public ShopTransactionResult sell(Player player, ShopEntry entry, int lots) {
        requireLots(lots);
        ItemStack[] contents = player.getInventory().getStorageContents();
        int total = 0;
        for (ItemStack stack : contents) {
            if (resolver.matches(stack, entry)) {
                total += stack.getAmount();
            }
        }

        int needed = entry.amount() * lots;
        if (total < needed) {
            return ShopTransactionResult.failure("No tenes suficientes items para vender.");
        }
        double price = entry.sellPrice() * lots;
        if (!economy.deposit(player, price)) {
            return ShopTransactionResult.failure("No se pudo pagar la venta.");
        }

        removeMatching(contents, entry, needed);
        player.getInventory().setStorageContents(contents);
        return ShopTransactionResult.success("Vendiste " + entry.id() + times(lots) + " por " + economy.format(price) + ".", null);
    }

    private static void requireLots(int lots) {
        if (lots < 1 || lots > 64) {
            throw new IllegalArgumentException("Cantidad de lotes invalida: " + lots);
        }
    }

    private static String times(int lots) {
        return lots == 1 ? "" : " x" + lots;
    }

    private void removeMatching(ItemStack[] contents, ShopEntry entry, int count) {
        int remaining = count;
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!resolver.matches(stack, entry)) {
                continue;
            }

            int taken = Math.min(stack.getAmount(), remaining);
            int nextAmount = stack.getAmount() - taken;
            remaining -= taken;

            if (nextAmount <= 0) {
                contents[i] = null;
            } else {
                stack.setAmount(nextAmount);
                contents[i] = stack;
            }
        }
    }
}
