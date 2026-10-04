package net.dvmn2.resourcecontrolplugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRegisterChannelEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Считает эффективный набор паков каждого игрока (индивидуальные + групповые + превью),
 * отправляет его клиентскому моду, принимает от мода статус применения и кикает игроков без мода.
 */
public final class SyncManager implements Listener, PluginMessageListener {

    public static final String SYNC_CHANNEL = "dvmn2:rc_sync";
    public static final String STATUS_CHANNEL = "dvmn2:rc_status";
    public static final int PROTOCOL = 1;

    /** Лимит, чтобы сообщение уложилось в 32 КБ plugin-message (клиент допускает до 64). */
    private static final int MAX_PACKS_PER_PLAYER = 32;

    public static final String GROUP_PERMISSION_PREFIX = "resourcecontrol.group.";

    public static final int STATE_DOWNLOADING = 0;
    public static final int STATE_READY = 1;
    public static final int STATE_FAILED = 2;

    public record ClientStatus(int state, String message) {
    }

    private final ResourceControlPlugin plugin;
    private final Map<UUID, String> lastSent = new HashMap<>();
    private final Map<UUID, ClientStatus> statuses = new HashMap<>();

    public SyncManager(ResourceControlPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // Расчёт набора
    // ------------------------------------------------------------------

    public boolean allowShaders() {
        return plugin.getConfig().getBoolean("settings.allow-shaders", true);
    }

    public static boolean hasMod(Player player) {
        return player.getListeningPluginChannels().contains(SYNC_CHANNEL);
    }

    public Set<String> groupPackNames(Player player) {
        Set<String> names = new LinkedHashSet<>();
        ConfigurationSection groups = plugin.getConfig().getConfigurationSection("groups");
        if (groups == null) {
            return names;
        }
        for (String group : groups.getKeys(false)) {
            if (player.hasPermission(GROUP_PERMISSION_PREFIX + group)) {
                names.addAll(groups.getStringList(group));
            }
        }
        return names;
    }

    /** Эффективный набор: от нижнего слоя к верхнему. Неизвестные имена и запрещённые шейдеры отсеиваются. */
    public List<PackInfo> effectivePacks(Player player) {
        UUID uuid = player.getUniqueId();
        Set<String> names = new LinkedHashSet<>(plugin.getAssignments().get(uuid));
        names.addAll(plugin.getAssignments().previews(uuid));
        names.addAll(groupPackNames(player));

        boolean allowShaders = allowShaders();
        List<PackInfo> result = new ArrayList<>();
        for (String name : names) {
            PackInfo info = plugin.getLibrary().get(name);
            if (info == null || (info.shaders() && !allowShaders)) {
                continue;
            }
            result.add(info);
        }
        result.sort(Comparator.comparingInt(PackInfo::priority).thenComparing(PackInfo::name));
        if (result.size() > MAX_PACKS_PER_PLAYER) {
            plugin.getLogger().warning("У игрока " + player.getName() + " больше " + MAX_PACKS_PER_PLAYER
                    + " паков — лишние отброшены.");
            // отбрасываем самые нижние по приоритету
            result = new ArrayList<>(result.subList(result.size() - MAX_PACKS_PER_PLAYER, result.size()));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Отправка
    // ------------------------------------------------------------------

    /** Шлёт игроку набор, только если он изменился с прошлой отправки (или force). */
    public void sync(Player player, boolean force) {
        if (!hasMod(player)) {
            return;
        }
        List<PackInfo> packs = effectivePacks(player);
        boolean allowShaders = allowShaders();
        String signature = allowShaders + "#" + packs.stream().map(PackInfo::signature).collect(Collectors.joining(";"));
        if (!force && signature.equals(lastSent.get(player.getUniqueId()))) {
            return;
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        try {
            writeVarInt(out, PROTOCOL);
            out.writeBoolean(allowShaders);
            writeVarInt(out, packs.size());
            for (PackInfo p : packs) {
                writeString(out, p.name());
                writeString(out, p.url());
                writeString(out, p.sha1());
                writeVarLong(out, p.size());
                writeVarInt(out, p.priority());
                out.writeBoolean(p.persistent());
            }
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сформировать пакет rc_sync: " + ex.getMessage());
            return;
        }

        try {
            player.sendPluginMessage(plugin, SYNC_CHANNEL, bytes.toByteArray());
            lastSent.put(player.getUniqueId(), signature);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Не удалось отправить rc_sync игроку " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void syncAll(boolean force) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sync(player, force);
        }
    }

    public ClientStatus status(UUID uuid) {
        return statuses.get(uuid);
    }

    // ------------------------------------------------------------------
    // Кодирование (формат Minecraft: VarInt/VarLong, строка = VarInt длина + UTF-8)
    // ------------------------------------------------------------------

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static void writeVarLong(DataOutputStream out, long value) throws IOException {
        while ((value & ~0x7FL) != 0) {
            out.writeByte((int) ((value & 0x7F) | 0x80));
            value >>>= 7;
        }
        out.writeByte((int) value);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, data.length);
        out.write(data);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int shift = 0;
        while (true) {
            int b = in.readUnsignedByte();
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IOException("VarInt слишком длинный");
            }
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = readVarInt(in);
        if (length < 0 || length > 2048) {
            throw new IOException("строка слишком длинная");
        }
        byte[] data = new byte[length];
        in.readFully(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Приём статуса от мода
    // ------------------------------------------------------------------

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!STATUS_CHANNEL.equals(channel)) {
            return;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            int protocol = readVarInt(in);
            int state = readVarInt(in);
            String text = readString(in);
            if (protocol != PROTOCOL) {
                return;
            }
            statuses.put(player.getUniqueId(), new ClientStatus(state, text));
            if (state == STATE_FAILED) {
                plugin.getLogger().warning("Клиент " + player.getName() + " не смог применить паки: " + text);
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("resourcecontrol.admin")) {
                        admin.sendMessage(Component.text(
                                Lang.get(Lang.Key.ADMIN_CLIENT_FAILED, admin, player.getName(), text),
                                NamedTextColor.RED));
                    }
                }
            }
        } catch (IOException ex) {
            plugin.getLogger().fine("Некорректный rc_status от " + player.getName() + ": " + ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // События
    // ------------------------------------------------------------------

    /** Клиент с модом регистрирует канал уже после входа — в этот момент шлём ему набор. */
    @EventHandler
    public void onRegisterChannel(PlayerRegisterChannelEvent event) {
        if (!SYNC_CHANNEL.equals(event.getChannel())) {
            return;
        }
        Player player = event.getPlayer();
        lastSent.remove(player.getUniqueId());
        sync(player, true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        scheduleModCheck(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastSent.remove(uuid);
        statuses.remove(uuid);
        plugin.getAssignments().forget(uuid);
    }

    private void scheduleModCheck(Player player) {
        if (!plugin.getConfig().getBoolean("settings.require-client-mod", true)) {
            return;
        }
        long seconds = Math.max(1L, plugin.getConfig().getLong("settings.mod-check-timeout-seconds", 10L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()
                    || player.hasPermission(ResourceControlPlugin.BYPASS_PERMISSION)
                    || hasMod(player)) {
                return;
            }
            player.kick(Component.text(Lang.get(Lang.Key.KICK_NO_MOD, player), NamedTextColor.RED));
        }, seconds * 20L);
    }
}
