package net.dvmn2.resourcecontrolplugin;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public final class Lang {

    public enum Key {
        PLAYER_NOT_FOUND,
        PACK_NOT_FOUND,
        PACK_NAME_INVALID,
        PACK_EXISTS,
        URL_INVALID,
        PACK_CHECKING,
        PACK_ADDED,
        PACK_CHECK_FAILED,
        PACK_SHADERS_DISABLED_WARN,
        PACK_REFRESHED,
        PACK_REMOVED,
        PACK_SET_PERSISTENT,
        PACK_SET_PRIORITY,
        PACK_LIST_HEADER,
        PACK_LIST_EMPTY,
        PACK_LIST_ENTRY,
        PACK_INFO,
        GIVE_OK,
        GIVE_ALREADY,
        GIVE_NO_MOD,
        REMOVE_OK,
        REMOVE_NOT_ASSIGNED,
        RESET_OK,
        GET_HEADER,
        GET_INDIVIDUAL,
        GET_GROUPS,
        GET_PREVIEW,
        GET_NONE,
        GET_STATUS,
        GET_NO_MOD,
        STATUS_UNKNOWN,
        STATUS_DOWNLOADING,
        STATUS_READY,
        STATUS_FAILED,
        PREVIEW_OK,
        RELOAD_OK,
        REFRESH_OK,
        KICK_NO_MOD,
        GUI_ONLY_PLAYER,
        GUI_TITLE,
        GUI_TRUNCATED,
        ADMIN_CLIENT_FAILED,
        LORE_PRIORITY,
        LORE_PERSISTENT,
        LORE_SIZE,
        LORE_SHADERS,
        LORE_STATE_INDIVIDUAL,
        LORE_STATE_GROUP,
        LORE_STATE_OFF,
        LORE_CLICK,
        YES,
        NO
    }

    private static final Map<Key, String> RU = new EnumMap<>(Key.class);
    private static final Map<Key, String> EN = new EnumMap<>(Key.class);

    private static void put(Key key, String ru, String en) {
        RU.put(key, ru);
        EN.put(key, en);
    }

    static {
        put(Key.PLAYER_NOT_FOUND, "Не удалось найти игрока.", "Player not found.");
        put(Key.PACK_NOT_FOUND, "Пак '%s' не найден в библиотеке.", "Pack '%s' was not found in the library.");
        put(Key.PACK_NAME_INVALID, "Имя пака: только a-z, 0-9, '_' и '-', до 32 символов.",
                "Pack name: only a-z, 0-9, '_' and '-', up to 32 characters.");
        put(Key.PACK_EXISTS, "Пак '%s' уже есть. Для обновления файла используйте /resourcecontrol pack refresh %s",
                "Pack '%s' already exists. To update its file use /resourcecontrol pack refresh %s");
        put(Key.URL_INVALID, "Нужна прямая https-ссылка (до %d символов).",
                "A direct https link is required (up to %d characters).");
        put(Key.PACK_CHECKING, "Скачиваю и проверяю пак по ссылке...", "Downloading and validating the pack...");
        put(Key.PACK_ADDED,
                "Пак '%s' добавлен. SHA-1: %s, размер: %.2f МБ, pack_format: %s, шейдеры: %s. Приоритет 0, persistent: нет (меняется через /resourcecontrol pack set).",
                "Pack '%s' added. SHA-1: %s, size: %.2f MB, pack_format: %s, shaders: %s. Priority 0, persistent: no (change via /resourcecontrol pack set).");
        put(Key.PACK_CHECK_FAILED, "Пак не прошёл проверку: %s", "Pack validation failed: %s");
        put(Key.PACK_SHADERS_DISABLED_WARN,
                "В паке есть шейдеры, а settings.allow-shaders = false — клиентам он отправляться не будет.",
                "The pack contains shaders but settings.allow-shaders = false — it will not be sent to clients.");
        put(Key.PACK_REFRESHED, "Пак '%s' обновлён. SHA-1: %s, размер: %.2f МБ.",
                "Pack '%s' refreshed. SHA-1: %s, size: %.2f MB.");
        put(Key.PACK_REMOVED, "Пак '%s' удалён из библиотеки и снят со всех игроков.",
                "Pack '%s' removed from the library and unassigned from all players.");
        put(Key.PACK_SET_PERSISTENT, "Пак '%s': persistent = %s.", "Pack '%s': persistent = %s.");
        put(Key.PACK_SET_PRIORITY, "Пак '%s': приоритет = %d.", "Pack '%s': priority = %d.");
        put(Key.PACK_LIST_HEADER, "Паки в библиотеке (%d):", "Packs in the library (%d):");
        put(Key.PACK_LIST_EMPTY, "Библиотека пуста. Добавьте пак: /resourcecontrol pack add <имя> <ссылка>",
                "The library is empty. Add one: /resourcecontrol pack add <name> <url>");
        put(Key.PACK_LIST_ENTRY, " - %s | приоритет %d | persistent: %s | %.2f МБ | шейдеры: %s",
                " - %s | priority %d | persistent: %s | %.2f MB | shaders: %s");
        put(Key.PACK_INFO, "Пак '%s'\nURL: %s\nSHA-1: %s\nРазмер: %.2f МБ\nПриоритет: %d\nPersistent: %s\nШейдеры: %s",
                "Pack '%s'\nURL: %s\nSHA-1: %s\nSize: %.2f MB\nPriority: %d\nPersistent: %s\nShaders: %s");
        put(Key.GIVE_OK, "Игроку %s выдан пак '%s'.", "Gave pack '%2$s' to %1$s.");
        put(Key.GIVE_ALREADY, "У игрока %s пак '%s' уже выдан.", "%s already has pack '%s'.");
        put(Key.GIVE_NO_MOD, "У игрока %s не обнаружен мод ResourceControl — пак применится после его установки.",
                "%s has no ResourceControl mod detected — the pack will apply once it is installed.");
        put(Key.REMOVE_OK, "У игрока %s снят пак '%s'.", "Removed pack '%2$s' from %1$s.");
        put(Key.REMOVE_NOT_ASSIGNED, "Пак '%2$s' не выдан игроку %1$s индивидуально.",
                "Pack '%2$s' is not individually assigned to %1$s.");
        put(Key.RESET_OK, "Игроку %s сброшены все индивидуальные паки.", "Reset all individual packs of %s.");
        put(Key.GET_HEADER, "Паки игрока %s:", "Packs of %s:");
        put(Key.GET_INDIVIDUAL, " Индивидуальные: %s", " Individual: %s");
        put(Key.GET_GROUPS, " Из групп: %s", " From groups: %s");
        put(Key.GET_PREVIEW, " Превью: %s", " Preview: %s");
        put(Key.GET_NONE, " (нет)", " (none)");
        put(Key.GET_STATUS, " Статус клиента: %s", " Client status: %s");
        put(Key.GET_NO_MOD, " Мод ResourceControl не обнаружен.", " ResourceControl mod not detected.");
        put(Key.STATUS_UNKNOWN, "нет данных", "no data");
        put(Key.STATUS_DOWNLOADING, "загрузка (%s)", "downloading (%s)");
        put(Key.STATUS_READY, "применено", "applied");
        put(Key.STATUS_FAILED, "ОШИБКА: %s", "FAILED: %s");
        put(Key.PREVIEW_OK, "Превью пака '%2$s' для %1$s на %3$d сек.", "Previewing pack '%2$s' for %1$s for %3$d s.");
        put(Key.RELOAD_OK, "Конфиг, библиотека паков и назначения перезагружены.",
                "Config, pack library and assignments reloaded.");
        put(Key.REFRESH_OK, "Набор паков игрока %s отправлен заново.", "Re-sent the pack set of %s.");
        put(Key.KICK_NO_MOD, "Для игры на этом сервере нужен клиентский мод ResourceControl.",
                "The ResourceControl client mod is required to play on this server.");
        put(Key.GUI_ONLY_PLAYER, "Эту команду может выполнить только игрок.", "This command can only be run by a player.");
        put(Key.GUI_TITLE, "Паки: %s", "Packs: %s");
        put(Key.GUI_TRUNCATED, "В GUI помещается 45 паков, остальные не показаны (используйте команды).",
                "The GUI fits 45 packs; the rest are hidden (use commands).");
        put(Key.ADMIN_CLIENT_FAILED, "[ResourceControl] У клиента %s ошибка применения паков: %s",
                "[ResourceControl] Client %s failed to apply packs: %s");
        put(Key.LORE_PRIORITY, "Приоритет: %d", "Priority: %d");
        put(Key.LORE_PERSISTENT, "Persistent: %s", "Persistent: %s");
        put(Key.LORE_SIZE, "Размер: %.2f МБ", "Size: %.2f MB");
        put(Key.LORE_SHADERS, "Шейдеры: %s", "Shaders: %s");
        put(Key.LORE_STATE_INDIVIDUAL, "Выдан игроку", "Assigned to player");
        put(Key.LORE_STATE_GROUP, "Выдан группой (клик — выдать ещё и лично)", "Granted by group (click to also assign directly)");
        put(Key.LORE_STATE_OFF, "Не выдан", "Not assigned");
        put(Key.LORE_CLICK, "Клик — выдать/снять", "Click to give/remove");
        put(Key.YES, "да", "yes");
        put(Key.NO, "нет", "no");
    }

    private static volatile String configuredLanguage = "auto";

    private Lang() {
    }

    public static void setLanguage(String language) {
        if (language == null || language.isBlank()) {
            configuredLanguage = "auto";
            return;
        }
        configuredLanguage = language.toLowerCase(Locale.ROOT);
    }

    public static String get(Key key, CommandSender sender, Object... args) {
        Map<Key, String> table = resolveTable(sender);
        String pattern = table.getOrDefault(key, RU.get(key));
        return args.length == 0 ? pattern : String.format(Locale.ROOT, pattern, args);
    }

    public static String yesNo(boolean value, CommandSender sender) {
        return get(value ? Key.YES : Key.NO, sender);
    }

    private static Map<Key, String> resolveTable(CommandSender sender) {
        return switch (configuredLanguage) {
            case "en" -> EN;
            case "ru" -> RU;
            default -> autoResolve(sender);
        };
    }

    private static Map<Key, String> autoResolve(CommandSender sender) {
        if (sender instanceof Player player) {
            String langCode = player.locale().getLanguage();
            return "ru".equalsIgnoreCase(langCode) ? RU : EN;
        }
        return EN;
    }
}
