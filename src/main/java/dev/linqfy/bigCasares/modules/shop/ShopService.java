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
        if (player == null) {
            return ShopTransactionResult.success("Compra realizada.", resolver.createStack(entry), postGrantEnforcer.applyAsInt(null));
        }
        if (!economy.has(player, entry.buyPrice())) {
            return ShopTransactionResult.failure("Saldo insuficiente.");
        }
        if (!economy.withdraw(player, entry.buyPrice())) {
            return ShopTransactionResult.failure("No se pudo cobrar la compra.");
        }

        ItemStack purchased = resolver.createStack(entry);
        var leftovers = player.getInventory().addItem(purchased);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        int overflowRemoved = postGrantEnforcer.applyAsInt(player);
        return ShopTransactionResult.success("Compraste " + entry.id() + " por " + economy.format(entry.buyPrice()) + ".", purchased, overflowRemoved);
    }

    public ShopTransactionResult sell(Player player, ShopEntry entry) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        int total = 0;
        for (ItemStack stack : contents) {
            if (resolver.matches(stack, entry)) {
                total += stack.getAmount();
            }
        }

        if (total < entry.amount()) {
            return ShopTransactionResult.failure("No tenes suficientes items para vender.");
        }
        if (!economy.deposit(player, entry.sellPrice())) {
            return ShopTransactionResult.failure("No se pudo pagar la venta.");
        }

        removeMatching(contents, entry);
        player.getInventory().setStorageContents(contents);
        return ShopTransactionResult.success("Vendiste " + entry.id() + " por " + economy.format(entry.sellPrice()) + ".", null);
    }

    private void removeMatching(ItemStack[] contents, ShopEntry entry) {
        int remaining = entry.amount();
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
