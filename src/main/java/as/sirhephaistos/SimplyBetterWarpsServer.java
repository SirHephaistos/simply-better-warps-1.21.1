package as.sirhephaistos;

import as.sirhephaistos.simplybetter.core.db.PositionsCrudManager;
import as.sirhephaistos.simplybetter.core.db.WarpsCrudManager;
import as.sirhephaistos.simplybetter.library.PositionDTO;
import as.sirhephaistos.simplybetter.library.WarpDTO;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimplyBetterWarpsServer implements DedicatedServerModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-warps");

    @Override
    public void onInitializeServer() {
        LOGGER.info("Initializing Simply Better Warps");
        LOGGER.info("Registering commands");
        //command registration callback
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                WarpCommands.register(dispatcher)
        );
        LOGGER.info("Commands registered");
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            LOGGER.info("Initializing WarpManager");
            if (WarpManager.init()) LOGGER.info("WarpManager initialized");
            else LOGGER.error("Failed to initialize WarpManager");
        });
    }
}
