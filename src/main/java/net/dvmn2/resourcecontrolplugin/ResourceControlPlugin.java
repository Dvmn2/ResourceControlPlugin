package net.dvmn2.resourcecontrolplugin;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.dvmn2.resourcecontrolplugin.commands.ResourceCommand;
import net.dvmn2.resourcecontrolplugin.gui.PackGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class ResourceControlPlugin extends JavaPlugin {

    /**
     * Игроки с этим правом не кикаются за отсутствие мода.
     * По умолчанию его нет даже у операторов — выдавать явно (LuckPerms и т.п.).
     */
    public static final String BYPASS_PERMISSION = "resourcecontrol.bypass";

    private PackLibrary library;
    private AssignmentManager assignments;
    private SyncManager syncManager;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }
        saveDefaultConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));

        library = new PackLibrary(this);
        library.load();
        assignments = new AssignmentManager(this);
        assignments.load();

        syncManager = new SyncManager(this);
        getServer().getPluginManager().registerEvents(syncManager, this);
        getServer().getPluginManager().registerEvents(new PackGuiListener(this), this);

        // Исходящий канал: набор паков -> мод. Входящий: статус применения <- мод.
        getServer().getMessenger().registerOutgoingPluginChannel(this, SyncManager.SYNC_CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, SyncManager.STATUS_CHANNEL, syncManager);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(
                        new ResourceCommand(this).build().build(),
                        "Управление ресурспаками отдельных игроков (клиентский мод ResourceControl)"
                ));

        scheduleSyncTask();

        getLogger().info("ResourceControlPlugin включён. Паков в библиотеке: " + library.all().size()
                + ". Команда: /resourcecontrol pack|give|remove|reset|get|gui|preview|refresh|reload");
    }

    private int syncTaskId = -1;

    /**
     * Периодический пересчёт наборов: подхватывает смену групповых прав без перезахода.
     * sync() дешёвый — без изменений ничего не отправляет.
     */
    private void scheduleSyncTask() {
        if (syncTaskId != -1) {
            Bukkit.getScheduler().cancelTask(syncTaskId);
        }
        long seconds = Math.max(1L, getConfig().getLong("settings.group-check-interval-seconds", 5L));
        syncTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> syncManager.syncAll(false),
                seconds * 20L, seconds * 20L).getTaskId();
    }

    /** /resourcecontrol reload: конфиг, библиотека и назначения; затем принудительная пересылка. */
    public void reloadAll() {
        reloadConfig();
        Lang.setLanguage(getConfig().getString("settings.language", "auto"));
        library.load();
        assignments.load();
        scheduleSyncTask();
        syncManager.syncAll(true);
    }

    @Override
    public void onDisable() {
        if (assignments != null) {
            assignments.save();
        }
    }

    public PackLibrary getLibrary() {
        return library;
    }

    public AssignmentManager getAssignments() {
        return assignments;
    }

    public SyncManager getSyncManager() {
        return syncManager;
    }
}
