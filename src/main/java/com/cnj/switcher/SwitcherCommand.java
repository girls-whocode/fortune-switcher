package com.cnj.switcher;

import net.kyori.adventure.text.Component;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class SwitcherCommand implements TabExecutor {

    private final FortuneSwitcherPlugin plugin;

    public SwitcherCommand(FortuneSwitcherPlugin plugin) {
        this.plugin = plugin;
    }

    private String romanNumeral(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            case 11 -> "XI";
            case 12 -> "XII";
            case 13 -> "XIII";
            case 14 -> "XIV";
            case 15 -> "XV";
            case 16 -> "XVI";
            case 17 -> "XVII";
            case 18 -> "XVIII";
            case 19 -> "XIX";
            case 20 -> "XX";
            case 21 -> "XXI";
            case 22 -> "XXII";
            case 23 -> "XXIII";
            case 24 -> "XXIV";
            case 25 -> "XXV";
            default -> String.valueOf(number);
        };
    }

    private String formatMaterialName(Material material) {
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();

        for (String part : parts) {
            if (part.isEmpty()) continue;
            builder.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1))
                    .append(" ");
        }

        return builder.toString().trim();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        FileConfiguration config = plugin.getConfig();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(color(config.getString("messages.players-only", "&cOnly players can use this command.")));
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);

            switch (sub) {
                case "tag":
                    return handleTag(player, config);
                case "untag":
                    return handleUntag(player, config);
                case "reload":
                    return handleReload(player, config);
                case "give":
                    return handleGive(player, args, config);
                default:
                    player.sendMessage(color("&cUnknown subcommand. Try: /switcher tag, /switcher untag, /switcher reload, /switcher give"));
                    return true;
            }
        }

        return handleSwitch(player, config);
    }

    private boolean handleSwitch(Player player, FileConfiguration config) {
        String usePermission = config.getString("permissions.use", "cnj.switcher.use");
        if (!player.hasPermission(usePermission)) {
            player.sendMessage(color(config.getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isValidTool(item, config)) {
            player.sendMessage(color(config.getString("messages.hold-valid-tool", "&cYou must hold a valid switcher tool in your main hand.")));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(color(config.getString("messages.invalid-tool", "&cThat item is not an allowed switcher material.")));
            return true;
        }

        boolean requireTag = config.getBoolean("settings.require-switcher-tag", true);
        String bypassPermission = config.getString("permissions.bypass-tag", "cnj.switcher.bypass.tag");

        if (requireTag && !player.hasPermission(bypassPermission) && !isTagged(meta)) {
            player.sendMessage(color(config.getString("messages.not-tagged", "&cThis tool is not marked as switchable.")));
            return true;
        }

        Enchantment fortune = Enchantment.FORTUNE;
        Enchantment silkTouch = Enchantment.SILK_TOUCH;

        boolean hasFortune = item.containsEnchantment(fortune);
        boolean hasSilkTouch = item.containsEnchantment(silkTouch);

        if (!hasFortune && !hasSilkTouch) {
            player.sendMessage(color(config.getString("messages.no-enchant", "&cThat tool has neither Fortune nor Silk Touch.")));
            return true;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        NamespacedKey storedFortuneKey = plugin.getStoredFortuneKey();

        if (hasFortune) {
            int fortuneLevel = item.getEnchantmentLevel(fortune);

            pdc.set(storedFortuneKey, PersistentDataType.INTEGER, fortuneLevel);
            item.setItemMeta(meta);

            item.removeEnchantment(fortune);
            item.addUnsafeEnchantment(silkTouch, 1);

            updateModeLore(item, config, true, fortuneLevel);

            player.sendMessage(color(
                    config.getString("messages.switched-to-silk", "&aSwitched &6Fortune %level% &ato &bSilk Touch I")
                            .replace("%level%", String.valueOf(fortuneLevel))
            ));

            sendActionBar(player, config.getString("actionbar.silk-message", "&bSwitcher Mode: &fSilk Touch"), config);
            playSuccessSound(player, config);
            return true;
        }

        if (hasSilkTouch) {
            int defaultFortuneLevel = config.getInt("settings.default-fortune-level", 3);
            int storedFortuneLevel = defaultFortuneLevel;

            if (pdc.has(storedFortuneKey, PersistentDataType.INTEGER)) {
                Integer saved = pdc.get(storedFortuneKey, PersistentDataType.INTEGER);
                if (saved != null && saved > 0) {
                    storedFortuneLevel = saved;
                }
            }

            item.removeEnchantment(silkTouch);
            item.addUnsafeEnchantment(fortune, storedFortuneLevel);

            ItemMeta updatedMeta = item.getItemMeta();
            if (updatedMeta != null) {
                updatedMeta.getPersistentDataContainer().remove(storedFortuneKey);
                item.setItemMeta(updatedMeta);
            }

            updateModeLore(item, config, false, storedFortuneLevel);

            player.sendMessage(color(
                    config.getString("messages.switched-to-fortune", "&aSwitched &bSilk Touch I &ato &6Fortune %level%")
                            .replace("%level%", String.valueOf(storedFortuneLevel))
            ));

            sendActionBar(
                    player,
                    config.getString("actionbar.fortune-message", "&6Switcher Mode: &fFortune %level%")
                            .replace("%level%", String.valueOf(storedFortuneLevel)),
                    config
            );
            playSuccessSound(player, config);
            return true;
        }

        return true;
    }

    private boolean handleGive(Player sender, String[] args, FileConfiguration config) {
        String permission = config.getString("permissions.give", "cnj.switcher.admin.give");
        if (!sender.hasPermission(permission)) {
            sender.sendMessage(color(config.getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        if (args.length < 4) {
            sender.sendMessage(color("&cUsage: /switcher give <player> <item> <fortune|silk> [level] [unbreakable]"));
            return true;
        }

        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(color(config.getString("messages.player-not-found", "&cThat player is not online.")));
            return true;
        }

        Material material;
        try {
            material = Material.valueOf(args[2].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            sender.sendMessage(color(config.getString("messages.invalid-tool", "&cThat item is not an allowed switcher material.")));
            return true;
        }

        if (!getAllowedMaterials(config).contains(material)) {
            sender.sendMessage(color(config.getString("messages.invalid-tool", "&cThat item is not an allowed switcher material.")));
            return true;
        }

        String mode = args[3].toLowerCase(Locale.ROOT);
        if (!mode.equals("fortune") && !mode.equals("silk")) {
            sender.sendMessage(color(config.getString("messages.invalid-mode", "&cMode must be fortune or silk.")));
            return true;
        }

        int fortuneLevel = config.getInt("settings.default-fortune-level", 3);
        if (mode.equals("fortune") && args.length >= 5) {
            try {
                fortuneLevel = Integer.parseInt(args[4]);
                if (fortuneLevel < 1) {
                    sender.sendMessage(color(config.getString("messages.invalid-level", "&cFortune level must be a positive number.")));
                    return true;
                }
            } catch (NumberFormatException e) {
                sender.sendMessage(color(config.getString("messages.invalid-level", "&cFortune level must be a positive number.")));
                return true;
            }
        }

        boolean unbreakable = config.getBoolean("give.default-unbreakable", true);
        if (args.length >= 6) {
            if (args[5].equalsIgnoreCase("unbreakable")) {
                unbreakable = true;
            }
        } else if (args.length == 5 && mode.equals("silk") && args[4].equalsIgnoreCase("unbreakable")) {
            unbreakable = true;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            sender.sendMessage(color("&cUnable to create item meta for that tool."));
            return true;
        }

        meta.getPersistentDataContainer().set(plugin.getSwitchableToolKey(), PersistentDataType.BYTE, (byte) 1);
        meta.setUnbreakable(unbreakable);

        for (String flagName : config.getStringList("give.add-item-flags")) {
            try {
                meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Invalid ItemFlag in config.yml: " + flagName);
            }
        }

        item.setItemMeta(meta);

        if (mode.equals("fortune")) {
            item.addUnsafeEnchantment(Enchantment.FORTUNE, fortuneLevel);
            updateModeLore(item, config, false, fortuneLevel);
        } else {
            item.addUnsafeEnchantment(Enchantment.SILK_TOUCH, 1);

            ItemMeta silkMeta = item.getItemMeta();
            if (silkMeta != null) {
                silkMeta.getPersistentDataContainer().set(plugin.getStoredFortuneKey(), PersistentDataType.INTEGER, fortuneLevel);
                item.setItemMeta(silkMeta);
            }

            updateModeLore(item, config, true, fortuneLevel);
        }

        target.getInventory().addItem(item);

        String prettyItem = formatMaterialName(material);
        String prettyMode = mode.equals("fortune") ? "Fortune " + romanNumeral(fortuneLevel) : "Silk Touch";

        sender.sendMessage(color(
                config.getString("messages.gave-tool-sender", "&aGave &f%player% &aa switcher tool: &e%item% &7(%mode%)")
                        .replace("%player%", target.getName())
                        .replace("%item%", prettyItem)
                        .replace("%mode%", prettyMode)
        ));

        target.sendMessage(color(
                config.getString("messages.gave-tool-target", "&aYou received a switcher tool: &e%item% &7(%mode%)")
                        .replace("%item%", prettyItem)
                        .replace("%mode%", prettyMode)
        ));

        return true;
    }

    private boolean handleTag(Player player, FileConfiguration config) {
        String permission = config.getString("permissions.tag", "cnj.switcher.admin.tag");
        if (!player.hasPermission(permission)) {
            player.sendMessage(color(config.getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isValidTool(item, config)) {
            player.sendMessage(color(config.getString("messages.hold-valid-tool", "&cYou must hold a valid switcher tool in your main hand.")));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(color(config.getString("messages.invalid-tool", "&cThat item is not an allowed switcher material.")));
            return true;
        }

        if (isTagged(meta)) {
            player.sendMessage(color(config.getString("messages.already-tagged", "&eThis tool is already tagged as switchable.")));
            return true;
        }

        meta.getPersistentDataContainer().set(plugin.getSwitchableToolKey(), PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);

        syncModeLoreFromCurrentState(item, config);

        player.sendMessage(color(config.getString("messages.tagged", "&aSwitcher enabled on this tool.")));
        return true;
    }

    private boolean handleUntag(Player player, FileConfiguration config) {
        String permission = config.getString("permissions.untag", "cnj.switcher.admin.untag");
        if (!player.hasPermission(permission)) {
            player.sendMessage(color(config.getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isValidTool(item, config)) {
            player.sendMessage(color(config.getString("messages.hold-valid-tool", "&cYou must hold a valid switcher tool in your main hand.")));
            return true;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(color(config.getString("messages.invalid-tool", "&cThat item is not an allowed switcher material.")));
            return true;
        }

        if (!isTagged(meta)) {
            player.sendMessage(color(config.getString("messages.already-untagged", "&eThis tool is not tagged as switchable.")));
            return true;
        }

        meta.getPersistentDataContainer().remove(plugin.getSwitchableToolKey());
        meta.getPersistentDataContainer().remove(plugin.getStoredFortuneKey());
        item.setItemMeta(meta);

        removeSwitcherLoreBlock(item, config);

        player.sendMessage(color(config.getString("messages.untagged", "&eSwitcher removed from this tool.")));
        return true;
    }

    private boolean handleReload(Player player, FileConfiguration config) {
        String permission = config.getString("permissions.reload", "cnj.switcher.admin.reload");
        if (!player.hasPermission(permission)) {
            player.sendMessage(color(config.getString("messages.no-permission", "&cYou do not have permission.")));
            return true;
        }

        plugin.reloadConfig();
        player.sendMessage(color(plugin.getConfig().getString("messages.reloaded", "&aFortuneSwitcher config reloaded.")));
        return true;
    }

    private boolean isValidTool(ItemStack item, FileConfiguration config) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Set<Material> allowed = getAllowedMaterials(config);
        return allowed.contains(item.getType());
    }

    private Set<Material> getAllowedMaterials(FileConfiguration config) {
        return config.getStringList("settings.allowed-materials").stream()
                .map(name -> {
                    try {
                        return Material.valueOf(name.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material in config.yml: " + name);
                        return null;
                    }
                })
                .filter(material -> material != null)
                .collect(Collectors.toSet());
    }

    private boolean isTagged(ItemMeta meta) {
        return meta.getPersistentDataContainer().has(plugin.getSwitchableToolKey(), PersistentDataType.BYTE);
    }

    private void syncModeLoreFromCurrentState(ItemStack item, FileConfiguration config) {
        if (!config.getBoolean("mode-lore.enabled", true)) {
            return;
        }

        if (item.containsEnchantment(Enchantment.SILK_TOUCH)) {
            int rememberedFortune = config.getInt("settings.default-fortune-level", 3);

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                Integer saved = meta.getPersistentDataContainer().get(plugin.getStoredFortuneKey(), PersistentDataType.INTEGER);
                if (saved != null && saved > 0) {
                    rememberedFortune = saved;
                }
            }

            updateModeLore(item, config, true, rememberedFortune);
        } else if (item.containsEnchantment(Enchantment.FORTUNE)) {
            int level = item.getEnchantmentLevel(Enchantment.FORTUNE);
            updateModeLore(item, config, false, level);
        }
    }

    private void updateModeLore(ItemStack item, FileConfiguration config, boolean silkMode, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = stripSwitcherLoreBlock(lore, config);

        List<String> switcherBlock = buildSwitcherLoreBlock(config, silkMode, level);

        if (!lore.isEmpty() && !isBlank(lore.get(lore.size() - 1))) {
            lore.add("");
        }

        lore.addAll(switcherBlock);

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    private void removeSwitcherLoreBlock(ItemStack item, FileConfiguration config) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
        lore = stripSwitcherLoreBlock(lore, config);

        trimTrailingBlankLines(lore);
        meta.setLore(lore.isEmpty() ? null : lore);
        item.setItemMeta(meta);
    }

    private List<String> stripSwitcherLoreBlock(List<String> lore, FileConfiguration config) {
        String titleLine = color(config.getString("tagging.lore-line", "&e&l✦ Switcher Infused ✦"));
        String instructionLine = color(config.getString("instruction-lore.line", "&7Use &f/switcher &7to toggle"));

        List<String> cleaned = new ArrayList<>();
        for (String line : lore) {
            if (isSwitcherTitle(line, titleLine)) continue;
            if (isSwitcherModeLine(line)) continue;
            if (isSwitcherInstruction(line, instructionLine)) continue;
            cleaned.add(line);
        }

        trimTrailingBlankLines(cleaned);

        return collapseExcessBlankLines(cleaned);
    }

    private List<String> buildSwitcherLoreBlock(FileConfiguration config, boolean silkMode, int level) {
        List<String> block = new ArrayList<>();

        String titleLine = color(config.getString("tagging.lore-line", "&e&l✦ Switcher Infused ✦"));
        String instructionLine = color(config.getString("instruction-lore.line", "&7Use &f/switcher &7to toggle"));

        block.add(titleLine);

        if (config.getBoolean("lore-format.add-blank-line-after-title", true)) {
            block.add("");
        }

        block.add(getStyledModeLine(config, silkMode, level));

        if (config.getBoolean("lore-format.add-blank-line-before-instruction", true)) {
            block.add("");
        }

        if (config.getBoolean("instruction-lore.enabled", true)) {
            block.add(instructionLine);
        }

        return block;
    }

    private String getStyledModeLine(FileConfiguration config, boolean silkMode, int level) {
        if (silkMode) {
            return color(config.getString("mode-lore.silk-line", "&b&lMode: &fSilk Touch"));
        }

        String numeral = romanNumeral(level);
        String template;

        if (level >= 10) {
            template = config.getString("mode-lore.fortune-line-legendary", "&e&lMode: &6&lFortune %level%");
        } else if (level >= 5) {
            template = config.getString("mode-lore.fortune-line-epic", "&6&lMode: &eFortune %level%");
        } else {
            template = config.getString("mode-lore.fortune-line", "&6Mode: Fortune %level%");
        }

        return color(template.replace("%level%", numeral));
    }

    private boolean isSwitcherTitle(String line, String titleLine) {
        return line != null && line.equals(titleLine);
    }

    private boolean isSwitcherModeLine(String line) {
        String stripped = stripColors(line);
        return stripped.startsWith("Mode: Silk Touch") || stripped.startsWith("Mode: Fortune ");
    }

    private boolean isSwitcherInstruction(String line, String instructionLine) {
        return line != null && line.equals(instructionLine);
    }

    private boolean isBlank(String line) {
        return line == null || line.trim().isEmpty();
    }

    private void trimTrailingBlankLines(List<String> lore) {
        while (!lore.isEmpty() && isBlank(lore.get(lore.size() - 1))) {
            lore.remove(lore.size() - 1);
        }
    }

    private List<String> collapseExcessBlankLines(List<String> lore) {
        List<String> result = new ArrayList<>();
        boolean lastWasBlank = false;

        for (String line : lore) {
            boolean blank = isBlank(line);
            if (blank && lastWasBlank) {
                continue;
            }
            result.add(line);
            lastWasBlank = blank;
        }

        return result;
    }

    private void playSuccessSound(Player player, FileConfiguration config) {
        if (!config.getBoolean("sound.enabled", true)) {
            return;
        }

        String soundName = config.getString("sound.success", "BLOCK_ENCHANTMENT_TABLE_USE");
        float volume = (float) config.getDouble("sound.volume", 1.0);
        float pitch = (float) config.getDouble("sound.pitch", 1.2);

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid sound in config.yml: " + soundName);
        }
    }

    private void sendActionBar(Player player, String message, FileConfiguration config) {
        if (!config.getBoolean("actionbar.enabled", true)) {
            return;
        }

        player.sendActionBar(Component.text(color(message)));
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    private String stripColors(String text) {
        return ChatColor.stripColor(color(text));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        FileConfiguration config = plugin.getConfig();

        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (sender.hasPermission(config.getString("permissions.tag", "cnj.switcher.admin.tag"))) subs.add("tag");
            if (sender.hasPermission(config.getString("permissions.untag", "cnj.switcher.admin.untag"))) subs.add("untag");
            if (sender.hasPermission(config.getString("permissions.reload", "cnj.switcher.admin.reload"))) subs.add("reload");
            if (sender.hasPermission(config.getString("permissions.give", "cnj.switcher.admin.give"))) subs.add("give");

            String current = args[0].toLowerCase(Locale.ROOT);
            return subs.stream().filter(s -> s.startsWith(current)).toList();
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            String current = args[1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(current))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            String current = args[2].toLowerCase(Locale.ROOT);
            return getAllowedMaterials(config).stream()
                    .map(Material::name)
                    .map(String::toLowerCase)
                    .filter(name -> name.startsWith(current))
                    .sorted()
                    .toList();
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            String current = args[3].toLowerCase(Locale.ROOT);
            return List.of("fortune", "silk").stream()
                    .filter(mode -> mode.startsWith(current))
                    .toList();
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            if (args[3].equalsIgnoreCase("fortune")) {
                return List.of("1", "2", "3", "4", "5", "10", "15", "20");
            }
            if (args[3].equalsIgnoreCase("silk")) {
                return List.of("unbreakable");
            }
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("give") && args[3].equalsIgnoreCase("fortune")) {
            return List.of("unbreakable");
        }

        return List.of();
    }
}