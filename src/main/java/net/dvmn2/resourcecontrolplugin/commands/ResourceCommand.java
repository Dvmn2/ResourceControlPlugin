package net.dvmn2.resourcecontrolplugin.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import net.dvmn2.resourcecontrolplugin.Lang;
import net.dvmn2.resourcecontrolplugin.PackInfo;
import net.dvmn2.resourcecontrolplugin.PackInspector;
import net.dvmn2.resourcecontrolplugin.ResourceControlPlugin;
import net.dvmn2.resourcecontrolplugin.SyncManager;
import net.dvmn2.resourcecontrolplugin.gui.PackGui;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * /resourcecontrol pack add <имя> <https-ссылка>
 * /resourcecontrol pack remove|refresh|info <пак>
 * /resourcecontrol pack list
 * /resourcecontrol pack set <пак> persistent <true|false>
 * /resourcecontrol pack set <пак> priority <число>
 * /resourcecontrol give|remove <игрок> <пак>
 * /resourcecontrol reset|get|gui|refresh <игрок>
 * /resourcecontrol preview <игрок> <пак> [секунд]
 * /resourcecontrol reload
 * <p>
 * Требует право resourcecontrol.admin.
 */
public final class ResourceCommand {

    private static final int DEFAULT_PREVIEW_SECONDS = 30;

    private final ResourceControlPlugin plugin;

    public ResourceCommand(ResourceControlPlugin plugin) {
        this.plugin = plugin;
    }

    public LiteralArgumentBuilder<CommandSourceStack> build() {
        return Commands.literal("resourcecontrol")
                .requires(src -> src.getSender().hasPermission("resourcecontrol.admin"))
                .then(Commands.literal("pack")
                        .then(Commands.literal("add")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .then(Commands.argument("url", StringArgumentType.greedyString())
                                                .executes(this::packAdd))))
                        .then(Commands.literal("remove").then(packArg().executes(this::packRemove)))
                        .then(Commands.literal("refresh").then(packArg().executes(this::packRefresh)))
                        .then(Commands.literal("info").then(packArg().executes(this::packInfo)))
                        .then(Commands.literal("list").executes(this::packList))
                        .then(Commands.literal("set")
                                .then(packArg()
                                        .then(Commands.literal("persistent")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                        .executes(this::packSetPersistent)))
                                        .then(Commands.literal("priority")
                                                .then(Commands.argument("value", IntegerArgumentType.integer(-1000, 1000))
                                                        .executes(this::packSetPriority))))))
                .then(Commands.literal("give")
                        .then(playerArg().then(packArg().executes(ctx -> give(ctx, true)))))
                .then(Commands.literal("remove")
                        .then(playerArg().then(packArg().executes(ctx -> give(ctx, false)))))
                .then(Commands.literal("reset").then(playerArg().executes(this::reset)))
                .then(Commands.literal("get").then(playerArg().executes(this::get)))
                .then(Commands.literal("gui").then(playerArg().executes(this::gui)))
                .then(Commands.literal("refresh").then(playerArg().executes(this::refresh)))
                .then(Commands.literal("preview")
                        .then(playerArg().then(packArg()
                                .executes(ctx -> preview(ctx, DEFAULT_PREVIEW_SECONDS))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
                                        .executes(ctx -> preview(ctx, IntegerArgumentType.getInteger(ctx, "seconds")))))))
                .then(Commands.literal("reload").executes(this::reload));
    }

    // ------------------------------------------------------------------
    // Аргументы и утилиты
    // ------------------------------------------------------------------

    private RequiredArgumentBuilder<CommandSourceStack, ?> playerArg() {
        return Commands.argument("player", ArgumentTypes.player());
    }

    private RequiredArgumentBuilder<CommandSourceStack, String> packArg() {
        return Commands.argument("pack", StringArgumentType.word()).suggests(this::suggestPacks);
    }

    private CompletableFuture<Suggestions> suggestPacks(CommandContext<CommandSourceStack> ctx,
                                                        SuggestionsBuilder builder) {
        String typed = builder.getRemainingLowerCase();
        for (String name : plugin.getLibrary().names()) {
            if (name.startsWith(typed)) {
                builder.suggest(name);
            }
        }
        return builder.buildFuture();
    }

    private List<Player> players(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        List<Player> list = ctx.getArgument("player", PlayerSelectorArgumentResolver.class).resolve(ctx.getSource());
        if (list.isEmpty()) {
            error(ctx.getSource().getSender(), Lang.Key.PLAYER_NOT_FOUND);
        }
        return list;
    }

    private static void ok(CommandSender sender, Lang.Key key, Object... args) {
        sender.sendMessage(Component.text(Lang.get(key, sender, args), NamedTextColor.GREEN));
    }

    private static void warn(CommandSender sender, Lang.Key key, Object... args) {
        sender.sendMessage(Component.text(Lang.get(key, sender, args), NamedTextColor.YELLOW));
    }

    private static void error(CommandSender sender, Lang.Key key, Object... args) {
        sender.sendMessage(Component.text(Lang.get(key, sender, args), NamedTextColor.RED));
    }

    private static void info(CommandSender sender, String text) {
        sender.sendMessage(Component.text(text, NamedTextColor.GRAY));
    }

    /** Возвращает пак либо сообщает об ошибке и возвращает null. */
    private PackInfo requirePack(CommandSender sender, String name) {
        PackInfo pack = plugin.getLibrary().get(name);
        if (pack == null) {
            error(sender, Lang.Key.PACK_NOT_FOUND, name);
        }
        return pack;
    }

    private static boolean validUrl(String url) {
        if (url.length() > PackInfo.MAX_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /** Скачивает и проверяет пак асинхронно; колбэки вызываются в основном потоке. */
    private void inspectAsync(String url, Consumer<PackInspector.Result> onSuccess, Consumer<String> onFailure) {
        long maxBytes = Math.max(1L, plugin.getConfig().getLong("settings.max-pack-size-mb", 256L)) * 1048576L;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            PackInspector.Result result = null;
            String failure = null;
            try {
                result = PackInspector.inspect(url, maxBytes);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                failure = "interrupted";
            } catch (Exception ex) {
                failure = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            }
            PackInspector.Result finalResult = result;
            String finalFailure = failure;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (finalResult != null) {
                    onSuccess.accept(finalResult);
                } else {
                    onFailure.accept(finalFailure);
                }
            });
        });
    }

    // ------------------------------------------------------------------
    // pack ...
    // ------------------------------------------------------------------

    private int packAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "name");
        String url = StringArgumentType.getString(ctx, "url").trim();

        if (!PackInfo.isValidName(name)) {
            error(sender, Lang.Key.PACK_NAME_INVALID);
            return 0;
        }
        if (plugin.getLibrary().get(name) != null) {
            error(sender, Lang.Key.PACK_EXISTS, name, name);
            return 0;
        }
        if (!validUrl(url)) {
            error(sender, Lang.Key.URL_INVALID, PackInfo.MAX_URL_LENGTH);
            return 0;
        }

        info(sender, Lang.get(Lang.Key.PACK_CHECKING, sender));
        inspectAsync(url, result -> {
            if (plugin.getLibrary().get(name) != null) {
                error(sender, Lang.Key.PACK_EXISTS, name, name);
                return;
            }
            plugin.getLibrary().put(new PackInfo(name, url, result.sha1(), result.size(), 0, false, result.hasShaders()));
            ok(sender, Lang.Key.PACK_ADDED, name, result.sha1(), result.size() / 1048576.0,
                    result.packFormat(), Lang.yesNo(result.hasShaders(), sender));
            if (result.hasShaders() && !plugin.getSyncManager().allowShaders()) {
                warn(sender, Lang.Key.PACK_SHADERS_DISABLED_WARN);
            }
        }, failure -> error(sender, Lang.Key.PACK_CHECK_FAILED, failure));
        return Command.SINGLE_SUCCESS;
    }

    private int packRefresh(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PackInfo pack = requirePack(sender, StringArgumentType.getString(ctx, "pack"));
        if (pack == null) {
            return 0;
        }
        info(sender, Lang.get(Lang.Key.PACK_CHECKING, sender));
        inspectAsync(pack.url(), result -> {
            PackInfo current = plugin.getLibrary().get(pack.name());
            if (current == null) {
                return;
            }
            plugin.getLibrary().put(current.withContent(result.sha1(), result.size(), result.hasShaders()));
            plugin.getSyncManager().syncAll(false);
            ok(sender, Lang.Key.PACK_REFRESHED, pack.name(), result.sha1(), result.size() / 1048576.0);
            if (result.hasShaders() && !plugin.getSyncManager().allowShaders()) {
                warn(sender, Lang.Key.PACK_SHADERS_DISABLED_WARN);
            }
        }, failure -> error(sender, Lang.Key.PACK_CHECK_FAILED, failure));
        return Command.SINGLE_SUCCESS;
    }

    private int packRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        String name = StringArgumentType.getString(ctx, "pack");
        if (requirePack(sender, name) == null) {
            return 0;
        }
        plugin.getLibrary().remove(name);
        plugin.getAssignments().removePackEverywhere(name);
        plugin.getSyncManager().syncAll(false);
        ok(sender, Lang.Key.PACK_REMOVED, name);
        return Command.SINGLE_SUCCESS;
    }

    private int packInfo(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PackInfo p = requirePack(sender, StringArgumentType.getString(ctx, "pack"));
        if (p == null) {
            return 0;
        }
        info(sender, Lang.get(Lang.Key.PACK_INFO, sender, p.name(), p.url(), p.sha1(), p.sizeMegabytes(),
                p.priority(), Lang.yesNo(p.persistent(), sender), Lang.yesNo(p.shaders(), sender)));
        return Command.SINGLE_SUCCESS;
    }

    private int packList(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        var all = plugin.getLibrary().all();
        if (all.isEmpty()) {
            warn(sender, Lang.Key.PACK_LIST_EMPTY);
            return Command.SINGLE_SUCCESS;
        }
        ok(sender, Lang.Key.PACK_LIST_HEADER, all.size());
        for (PackInfo p : all) {
            info(sender, Lang.get(Lang.Key.PACK_LIST_ENTRY, sender, p.name(), p.priority(),
                    Lang.yesNo(p.persistent(), sender), p.sizeMegabytes(), Lang.yesNo(p.shaders(), sender)));
        }
        return Command.SINGLE_SUCCESS;
    }

    private int packSetPersistent(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PackInfo p = requirePack(sender, StringArgumentType.getString(ctx, "pack"));
        if (p == null) {
            return 0;
        }
        boolean value = BoolArgumentType.getBool(ctx, "value");
        plugin.getLibrary().put(p.withPersistent(value));
        plugin.getSyncManager().syncAll(false);
        ok(sender, Lang.Key.PACK_SET_PERSISTENT, p.name(), Lang.yesNo(value, sender));
        return Command.SINGLE_SUCCESS;
    }

    private int packSetPriority(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        PackInfo p = requirePack(sender, StringArgumentType.getString(ctx, "pack"));
        if (p == null) {
            return 0;
        }
        int value = IntegerArgumentType.getInteger(ctx, "value");
        plugin.getLibrary().put(p.withPriority(value));
        plugin.getSyncManager().syncAll(false);
        ok(sender, Lang.Key.PACK_SET_PRIORITY, p.name(), value);
        return Command.SINGLE_SUCCESS;
    }

    // ------------------------------------------------------------------
    // Игроки
    // ------------------------------------------------------------------

    private int give(CommandContext<CommandSourceStack> ctx, boolean give) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String packName = StringArgumentType.getString(ctx, "pack");
        if (requirePack(sender, packName) == null) {
            return 0;
        }
        List<Player> targets = players(ctx);
        for (Player target : targets) {
            if (give) {
                if (plugin.getAssignments().add(target.getUniqueId(), packName)) {
                    ok(sender, Lang.Key.GIVE_OK, target.getName(), packName);
                } else {
                    warn(sender, Lang.Key.GIVE_ALREADY, target.getName(), packName);
                }
                if (!SyncManager.hasMod(target)) {
                    warn(sender, Lang.Key.GIVE_NO_MOD, target.getName());
                }
            } else {
                if (plugin.getAssignments().remove(target.getUniqueId(), packName)) {
                    ok(sender, Lang.Key.REMOVE_OK, target.getName(), packName);
                } else {
                    warn(sender, Lang.Key.REMOVE_NOT_ASSIGNED, target.getName(), packName);
                }
            }
            plugin.getSyncManager().sync(target, false);
        }
        return targets.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
    }

    private int reset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = players(ctx);
        for (Player target : targets) {
            plugin.getAssignments().clear(target.getUniqueId());
            plugin.getSyncManager().sync(target, false);
            ok(sender, Lang.Key.RESET_OK, target.getName());
        }
        return targets.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
    }

    private int refresh(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = players(ctx);
        for (Player target : targets) {
            plugin.getSyncManager().sync(target, true);
            ok(sender, Lang.Key.REFRESH_OK, target.getName());
        }
        return targets.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
    }

    private int get(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        List<Player> targets = players(ctx);
        if (targets.isEmpty()) {
            return 0;
        }
        Player target = targets.get(0);
        ok(sender, Lang.Key.GET_HEADER, target.getName());

        info(sender, Lang.get(Lang.Key.GET_INDIVIDUAL, sender, join(plugin.getAssignments().get(target.getUniqueId()), sender)));
        info(sender, Lang.get(Lang.Key.GET_GROUPS, sender, join(plugin.getSyncManager().groupPackNames(target), sender)));
        Set<String> previews = plugin.getAssignments().previews(target.getUniqueId());
        if (!previews.isEmpty()) {
            info(sender, Lang.get(Lang.Key.GET_PREVIEW, sender, join(previews, sender)));
        }

        if (!SyncManager.hasMod(target)) {
            warn(sender, Lang.Key.GET_NO_MOD);
        } else {
            SyncManager.ClientStatus st = plugin.getSyncManager().status(target.getUniqueId());
            String text;
            if (st == null) {
                text = Lang.get(Lang.Key.STATUS_UNKNOWN, sender);
            } else if (st.state() == SyncManager.STATE_READY) {
                text = Lang.get(Lang.Key.STATUS_READY, sender);
            } else if (st.state() == SyncManager.STATE_DOWNLOADING) {
                text = Lang.get(Lang.Key.STATUS_DOWNLOADING, sender, st.message());
            } else {
                text = Lang.get(Lang.Key.STATUS_FAILED, sender, st.message());
            }
            info(sender, Lang.get(Lang.Key.GET_STATUS, sender, text));
        }
        return Command.SINGLE_SUCCESS;
    }

    private String join(Set<String> names, CommandSender sender) {
        if (names.isEmpty()) {
            return Lang.get(Lang.Key.GET_NONE, sender).trim();
        }
        return String.join(", ", new ArrayList<>(names));
    }

    private int gui(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        if (!(sender instanceof Player admin)) {
            error(sender, Lang.Key.GUI_ONLY_PLAYER);
            return 0;
        }
        List<Player> targets = players(ctx);
        if (targets.isEmpty()) {
            return 0;
        }
        PackGui.open(plugin, admin, targets.get(0));
        return Command.SINGLE_SUCCESS;
    }

    private int preview(CommandContext<CommandSourceStack> ctx, int seconds) throws CommandSyntaxException {
        CommandSender sender = ctx.getSource().getSender();
        String packName = StringArgumentType.getString(ctx, "pack");
        if (requirePack(sender, packName) == null) {
            return 0;
        }
        List<Player> targets = players(ctx);
        for (Player target : targets) {
            plugin.getAssignments().addPreview(target.getUniqueId(), packName);
            plugin.getSyncManager().sync(target, false);
            ok(sender, Lang.Key.PREVIEW_OK, target.getName(), packName, seconds);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                plugin.getAssignments().removePreview(target.getUniqueId(), packName);
                if (target.isOnline()) {
                    plugin.getSyncManager().sync(target, false);
                }
            }, seconds * 20L);
        }
        return targets.isEmpty() ? 0 : Command.SINGLE_SUCCESS;
    }

    private int reload(CommandContext<CommandSourceStack> ctx) {
        plugin.reloadAll();
        ok(ctx.getSource().getSender(), Lang.Key.RELOAD_OK);
        return Command.SINGLE_SUCCESS;
    }
}
