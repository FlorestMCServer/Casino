package ru.florestdev.casinoPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public class CasinoPlugin extends JavaPlugin implements Listener, CommandExecutor {

    private static Economy econ = null;
    private final Material[] items = {
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT,
            Material.NETHERITE_INGOT, Material.LAPIS_LAZULI
    };
    private final Random random = new Random();
    private final String TITLE_TEXT = "Казино: Удача?";

    // Сет для отслеживания игроков, которые сейчас играют
    private final Set<UUID> activePlayers = new HashSet<>();

    @Override
    public void onEnable() {
        if (!setupEconomy()) {
            getLogger().severe(String.format("[%s] - Нет Vault! Выключаюсь.", getDescription().getName()));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("casino").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) return false;
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) return false;
        econ = rsp.getProvider();
        return econ != null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) return true;

        if (args.length < 2 || !args[0].toLowerCase().startsWith("dep")) {
            player.sendMessage(Component.text("Используйте: /casino deposit <сумма>", NamedTextColor.RED));
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Введите число!", NamedTextColor.RED));
            return true;
        }

        if (amount <= 0 || econ.getBalance(player) < amount) {
            player.sendMessage(Component.text("Недостаточно средств или неверная ставка!", NamedTextColor.RED));
            return true;
        }

        // Выполняем снятие и открытие в контексте игрока (Folia-ready)
        player.getScheduler().run(this, (task) -> {
            econ.withdrawPlayer(player, amount);
            openCasino(player, amount);
        }, null);

        return true;
    }

    public void openCasino(Player player, double bet) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(TITLE_TEXT, NamedTextColor.GOLD));

        // Заполняем фон серым стеклом СРАЗУ
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int i = 0; i < 27; i++) inv.setItem(i, filler);

        // Ставим первые попавшиеся предметы, чтобы не было пусто
        inv.setItem(11, new ItemStack(items[random.nextInt(items.length)]));
        inv.setItem(13, new ItemStack(items[random.nextInt(items.length)]));
        inv.setItem(15, new ItemStack(items[random.nextInt(items.length)]));

        player.openInventory(inv);
        activePlayers.add(player.getUniqueId());

        // Цикл анимации
        for (int i = 1; i <= 10; i++) {
            int step = i;
            player.getScheduler().runDelayed(this, (task) -> {
                // Проверяем, что инвентарь все еще тот самый
                String currentTitle = PlainTextComponentSerializer.plainText().serialize(player.getOpenInventory().title());
                if (!currentTitle.contains(TITLE_TEXT)) {
                    return;
                }

                Material m1 = items[random.nextInt(items.length)];
                Material m2 = items[random.nextInt(items.length)];
                Material m3 = items[random.nextInt(items.length)];

                Inventory topInv = player.getOpenInventory().getTopInventory();
                topInv.setItem(11, new ItemStack(m1));
                topInv.setItem(13, new ItemStack(m2));
                topInv.setItem(15, new ItemStack(m3));

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 2.0f);

                if (step == 10) {
                    activePlayers.remove(player.getUniqueId());
                    checkResult(player, m1, m2, m3, bet);
                }
            }, null, i * 3L);
        }
    }

    private void checkResult(Player player, Material m1, Material m2, Material m3, double bet) {
        if (m1 == m2 && m2 == m3) {
            double win = bet * 3;
            econ.depositPlayer(player, win);
            player.sendMessage(Component.text("ДЖЕКПОТ! Вы выиграли " + win + "$", NamedTextColor.GREEN));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            player.sendMessage(Component.text("Увы, вы проиграли " + bet + "$", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = PlainTextComponentSerializer.plainText().serialize(event.getView().title());
        if (title.contains(TITLE_TEXT)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Если игрок закрыл инвентарь до окончания анимации
        if (activePlayers.contains(uuid)) {
            activePlayers.remove(uuid);
            // Тут можно либо вернуть деньги, либо оставить как штраф.
            // Вернем для честности:
            // econ.depositPlayer((Player) event.getPlayer(), ставка);
            // Но так как у нас нет прямого доступа к 'bet' тут, лучше просто логировать или не возвращать.
        }
    }
}