package net.dvmn2.resourcecontrolplugin;

import java.util.regex.Pattern;

/**
 * Пак из библиотеки (packs.yml).
 *
 * @param name       уникальное имя [a-z0-9_-]{1,32}
 * @param url        прямая HTTPS-ссылка на zip
 * @param sha1       SHA-1 файла (40 hex, нижний регистр)
 * @param size       размер в байтах
 * @param priority   больше = выше (перекрывает остальные паки игрока)
 * @param persistent клиент сохраняет пак и после выхода с сервера (панорама главного меню и т.п.)
 * @param shaders    в паке есть core-шейдеры
 */
public record PackInfo(String name, String url, String sha1, long size, int priority,
                       boolean persistent, boolean shaders) {

    private static final Pattern NAME = Pattern.compile("[a-z0-9_-]{1,32}");
    public static final int MAX_URL_LENGTH = 512;

    public static boolean isValidName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    public PackInfo withContent(String newSha1, long newSize, boolean newShaders) {
        return new PackInfo(name, url, newSha1, newSize, priority, persistent, newShaders);
    }

    public PackInfo withPersistent(boolean value) {
        return new PackInfo(name, url, sha1, size, priority, value, shaders);
    }

    public PackInfo withPriority(int value) {
        return new PackInfo(name, url, sha1, size, value, persistent, shaders);
    }

    /** Любое изменение подписи означает, что клиенту нужно отправить набор заново. */
    public String signature() {
        return name + "|" + url + "|" + sha1 + "|" + size + "|" + priority + "|" + persistent;
    }

    public double sizeMegabytes() {
        return size / 1048576.0;
    }
}
