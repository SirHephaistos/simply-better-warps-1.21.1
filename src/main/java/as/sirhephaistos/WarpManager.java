package as.sirhephaistos;

import as.sirhephaistos.simplybetter.core.db.PositionsCrudManager;
import as.sirhephaistos.simplybetter.core.db.WarpsCrudManager;
import as.sirhephaistos.simplybetter.library.PositionDTO;
import as.sirhephaistos.simplybetter.library.WarpDTO;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Central registry for warps, grouped per dimension.
 * Persists to and loads from <server>/config/betterwarps.json.
 */
public final class WarpManager {
    private static final WarpManager INSTANCE = new WarpManager();
    private static WarpsCrudManager warpsCrudManager = null;
    private static PositionsCrudManager positionsCrudManager = null;
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-warps");

    private WarpManager() {
    }

    public static boolean init() {
        try {
            warpsCrudManager = SimplyBetterCoreServer.getWarpsCrudManager();
            positionsCrudManager = SimplyBetterCoreServer.getPositionsCrudManager();
            if (warpsCrudManager == null) {
                throw new IllegalStateException("WarpsCrudManager is not initialized");
            }
            if (positionsCrudManager == null) {
                throw new IllegalStateException("PositionsCrudManager is not initialized");
            }
            return true;
        } catch (Exception e) {
            LOGGER.error("Failed to initialize WarpManager", e);
            throw e;
        }
    }

    public static WarpManager get() {
        return INSTANCE;
    }

    /**
     * Sets or updates a warp point.
     *
     * @return
     */
    public WarpDTO setWarp(String name, @Nullable Long id, @NotNull String dimensionId,
                           double x, double y, double z , float xRot, float yRot){
        try {
            var createdWarp = warpsCrudManager.createWarp(name,new PositionDTO(null,dimensionId,x,y,z,xRot,yRot));
            LOGGER.info("Set warp {} at position {} in dimension {}",name,createdWarp.position().id(),createdWarp.position().dimensionId());
            return createdWarp;
        } catch (Exception e) {
            LOGGER.error("Failed to set warp {}", name, e);
            throw e;
        }
    }

    /**
     * Deletes a warp point. Returns true if deleted, false if not found.
     */
    public void delWarp(String name) {
        try {
            warpsCrudManager.deleteWarpByName(name);
        } catch (Exception e) {
            LOGGER.error("Failed to delete warp {}", name, e);
            throw e;
        }
    }

    /**
     * Lists all warps.
     */
    @Contract(pure = true)
    public @NotNull @Unmodifiable Map<String,Long> listWarps() {
        try {
            return warpsCrudManager.getWarpsMap();
        } catch (Exception e) {
            LOGGER.error("Failed to list warps", e);
            throw e;
        }
    }

    /**
     * Gets a warp point by name. Returns null if not found.
     *
     * @param warpName Name of the warp to retrieve.
     * @return WarpPoint or null if not found.
     */
    public Optional<WarpDTO> getWarp(String warpName) {
        try {
            return warpsCrudManager.getWarpByName(warpName);
        } catch (Exception e) {
            LOGGER.error("Failed to get warp {}", warpName, e);
            throw e;
        }
    }

    public void renameWarp(String oldName, String newName) {
        try {
            var warpOpt = warpsCrudManager.getWarpByName(oldName);
            if (warpOpt.isPresent()) warpsCrudManager.renameWarp(warpOpt.get().id(), newName);
            else LOGGER.warn("Warp {} not found for renaming", oldName);
        } catch (Exception e) {
            LOGGER.error("Failed to rename warp from {} to {}", oldName, newName, e);
            throw e;
        }
    }
}