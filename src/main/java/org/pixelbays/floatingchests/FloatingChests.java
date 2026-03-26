package org.pixelbays.floatingchests;

import javax.annotation.Nonnull;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.util.Config;

/**
 * This class serves as the entrypoint for your plugin. Use the setup method to register into game registries or add
 * event listeners.
 */
public class FloatingChests extends JavaPlugin {

    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    @SuppressWarnings("null")
    private final Config<ChestSupportOverridesConfig> overridesConfig = this.withConfig("ChestSupportOverrides", ChestSupportOverridesConfig.CODEC);
    private final ChestSupportPatchService chestSupportPatchService;

    public FloatingChests(@Nonnull JavaPluginInit init) {
        super(init);
        this.chestSupportPatchService = new ChestSupportPatchService(this, this.overridesConfig);
        LOGGER.atInfo().log("Hello from " + this.getName() + " version " + this.getManifest().getVersion().toString());
    }

    @Override
    protected void setup() {
        LOGGER.atInfo().log("Setting up plugin " + this.getName());
        this.overridesConfig.save().join();
        this.chestSupportPatchService.register();
        this.getCommandRegistry().registerCommand(new ChestSupportCommand(this.chestSupportPatchService));
    }

    @Override
    protected void start() {
        this.chestSupportPatchService.reapplyAll("plugin start");
    }

    @Override
    protected void shutdown() {
        this.chestSupportPatchService.clearRuntimeOverrides();
    }
}