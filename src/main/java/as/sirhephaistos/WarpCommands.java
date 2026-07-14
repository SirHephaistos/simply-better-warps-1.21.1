package as.sirhephaistos;

import as.sirhephaistos.simplybetter.library.WarpDTO;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;



import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class WarpCommands {
    private static final Logger LOGGER = LoggerFactory.getLogger("simplybetter-warps");
    // --- Suggestion provider: list of warps the user can see ---
    private static final SuggestionProvider<CommandSourceStack> WARP_NAME_SUGGESTER = (ctx, builder) -> {
        var wManager = WarpManager.get();
        var src = ctx.getSource();
        var warps = wManager.listWarps().keySet().stream()
                .filter(name -> canSeeWarp(src, name))
                .sorted()
                .toArray(String[]::new);
        return SharedSuggestionProvider.suggest(warps, builder);
    };

    private WarpCommands() {
    }
    private static boolean checkWildcardOrPerm(CommandSourceStack src, String node, String argument, int opLevelDefault,boolean shouldNegativePermOverride) {
        final String permission = node.toLowerCase() + "." + argument.toLowerCase();
        if (shouldNegativePermOverride &&  Permissions.getPermissionValue(src, permission) == TriState.FALSE ) return false;
        return Permissions.check(src, node.toLowerCase() + ".*") || Permissions.check(src, permission, opLevelDefault);
    }


    /**
     * Check if the source has permission to see the given warp in the warp list.
     *
     * @param src      Command source
     * @param warpName Name of the warp
     * @return true if the source has permission to see the warp
     */
    private static boolean canSeeWarp(CommandSourceStack src, String warpName) {
        /*return Permissions.check(src, "simplybetter.warps.see." + warpName.toLowerCase(), 1) || Permissions.check(src, "simplybetter.warps.see.*");*/
        return checkWildcardOrPerm(src, "simplybetter.warps.see", warpName,1,true);
    }

    /**
     * Check if the source has permission to teleport to the given warp.
     *
     * @param src      Command source
     * @param warpName Name of the warp
     * @return true if the source has permission to teleport to the warp
     */
    private static boolean canTpToWarp(CommandSourceStack src, String warpName) {
        /*return Permissions.check(src, "simplybetter.warps.tpto." + warpName.toLowerCase(), 1) || Permissions.check(src, "simplybetter.warps.tpto.*");*/
        return checkWildcardOrPerm(src, "simplybetter.warps.tpto", warpName,1,true);
    }

    /**
     * Register warp commands to the dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // ----- Reusable executors -----
        Command<CommandSourceStack> HELP_EXECUTOR = ctx -> {
            ctx.getSource().sendSuccess(() -> Component.literal("""
                                    [Simply Better Warps] Commands:
                    /warp <name>      - teleport to a warp
                    /setwarp <name>   - create a warp at your position
                    /delwarp <name>   - delete a warp
                    /warps            - list warps
                    /warp help        - show this help
                    """), false);
            return 1;
        };

        Command<CommandSourceStack> WARPTP_EXECUTOR = ctx -> {
            var source = ctx.getSource();
            var player = source.getPlayer();
            if (player == null) {
                source.sendFailure(Component.literal("Only players can use warps."));
                return 0;
            }
            String warpName = StringArgumentType.getString(ctx, "name");

            if (!canTpToWarp(source, warpName)) {
                source.sendFailure(Component.literal("[Simply Better Warps] You don't have permission to teleport to '" + warpName + "'."));
                return 0;
            }

            var wManager = WarpManager.get();
            try {
                Optional<WarpDTO> owp = wManager.getWarp(warpName);
                if (owp.isEmpty()) {
                    source.sendFailure(Component.literal("Warp not found: " + warpName));
                    return 0;
                }
                WarpDTO wp = owp.get();
                ResourceLocation dimId = ResourceLocation.tryParse(wp.position().dimensionId());
                if (dimId == null) {
                    source.sendFailure(Component.literal("Invalid dimension id on warp: " + wp.position().dimensionId()));
                    return 0;
                }
                ResourceKey<Level> targetKey = ResourceKey.create(Registries.DIMENSION, dimId);
                ServerLevel targetWorld = Objects.requireNonNull(player.getServer()).getLevel(targetKey);
                if (targetWorld == null) {
                    source.sendFailure(Component.literal("Target dimension not found on server: " + wp.position().dimensionId()));
                    return 0;
                }
                BlockPos targetPos = BlockPos.containing(wp.position().x(), wp.position().y(), wp.position().z());
                ChunkPos chunkPos = new ChunkPos(targetPos);
                targetWorld.getChunkSource().addRegionTicket(TicketType.POST_TELEPORT, chunkPos, 1, player.getId());
                targetWorld.getChunk(chunkPos.x, chunkPos.z);
                player.teleportTo(targetWorld, wp.position().x(), wp.position().y(), wp.position().z(),
                        wp.position().yRot(), wp.position().xRot());
                source.sendSuccess(() -> Component.literal("Teleported to '" + warpName + "' in " + wp.position().dimensionId() + "."), false);
                return 1;
            } catch (Exception e) {
                source.sendFailure(Component.literal(e.getMessage()));
                LOGGER.error("[Simply Better Warps] Error during warp teleport:{}", e.getMessage(), e);
                return 0;
            }
        };

        Command<CommandSourceStack> SETWARP_EXECUTOR = ctx -> {
            ServerPlayer p = ctx.getSource().getPlayer();
            String warpName = StringArgumentType.getString(ctx, "name");
            if (p == null) {
                ctx.getSource().sendFailure(Component.literal("[Simply Better Warps] Only players can set warps."));
                return 0;
            }
            var wManager = WarpManager.get();
            var createdWarp = wManager.setWarp(warpName, null,
                    p.level().dimension().location().toString(),
                    p.getX(), p.getY(), p.getZ(),
                    p.getYRot(), p.getXRot());
            ctx.getSource().sendSuccess(() -> Component.literal("[Simply Better Warps] Warp '%s' saved. at %s%s".formatted(
                    warpName,
                    createdWarp.position().dimensionId(),
                    String.format(" (%.1f, %.1f, %.1f)", createdWarp.position().x(), createdWarp.position().y(), createdWarp.position().z())
            )), false);
            return 1;
        };

        Command<CommandSourceStack> DELWARP_EXECUTOR = ctx -> {
            String warpName = StringArgumentType.getString(ctx, "name");
            WarpManager.get().delWarp(warpName);
            ctx.getSource().sendSuccess(() -> Component.literal("[Simply Better Warps] Warp deleted: " + warpName), false);
            return 1;
        };

        Command<CommandSourceStack> LIST_EXECUTOR = ctx -> {
            var src = ctx.getSource();
            var names = WarpManager.get().listWarps().keySet().stream()
                    .filter(name -> canSeeWarp(src, name))
                    .sorted()
                    .toList();

            if (names.isEmpty()) {
                src.sendSuccess(() -> Component.literal("[Simply Better Warps] Aucun warp visible."), false);
                return 1;
            }

            List<Component> clickable = new ArrayList<>();
            for (String name : names) {
                boolean canTp = canTpToWarp(src, name);

                MutableComponent item = Component.literal(name);

                if (canTp) {
                    item = item.withStyle(s -> s
                            .withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/warp " + name))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Clique pour /warp " + name))));
                } else {
                    item = item.withStyle(s -> s
                            .withItalic(true)
                            .withColor(0x7f7f7f)
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                    Component.literal("Tu n'as pas la permission de /warp " + name))));
                }

                clickable.add(item);
            }

            MutableComponent listText = Component.empty();
            for (int i = 0; i < clickable.size(); i++) {
                if (i > 0) listText.append(Component.literal(", "));
                listText.append(clickable.get(i));
            }

            Component msg = Component.literal("[Simply Better Warps] Warps (" + names.size() + "): ").append(listText);
            src.sendSuccess(() -> msg, false);
            return 1;
        };

        Command<CommandSourceStack> INFO_EXECUTOR = ctx -> {
            var source = ctx.getSource();
            String warpName = StringArgumentType.getString(ctx, "name");

            if (!canSeeWarp(source, warpName)) {
                source.sendFailure(Component.literal("[Simply Better Warps] You don't have permission to teleport to '" + warpName + "'."));
                return 0;
            }
            var oWarp = WarpManager.get().getWarp(warpName);
            var warp = oWarp.orElseThrow(() -> new IllegalStateException("Warp not found: " + warpName));
            MutableComponent msg = Component.literal("[Simply Better Warps] Warp '").append(Component.literal(warpName).withStyle(s -> s.withColor(0x00ff00)))
                    .append(Component.literal("': "))
                    .append(Component.literal(String.format("Dimension: %s, Position: (%.1f, %.1f, %.1f), Yaw: %.1f, Pitch: %.1f",
                            warp.position().dimensionId(), warp.position().x(), warp.position().y(), warp.position().z(), warp.position().yRot(), warp.position().xRot())));
            source.sendSuccess(() -> msg, false);
            return 1;
        };

        Command<CommandSourceStack> RENAMEWARP_EXECUTOR = ctx -> {
            String oldName = StringArgumentType.getString(ctx, "oldname");
            String newName = StringArgumentType.getString(ctx, "newname");
            var wManager = WarpManager.get();
            try {
                wManager.renameWarp(oldName, newName);
                ctx.getSource().sendSuccess(() -> Component.literal("[Simply Better Warps] Warp renamed from '%s' to '%s'.".formatted(oldName, newName)), false);
                ctx.getSource().sendSuccess(() -> Component.literal("[Simply Better Warps] Note: Permissions are not automatically updated. Change them manually from '%s' to '%s'.".formatted(oldName,newName)), false);
                return 1;
            } catch (Exception e) {
                ctx.getSource().sendFailure(Component.literal("[Simply Better Warps] " + e.getMessage()));
                return 0;
            }
        };

        // ----- Command registrations -----

        // /simplybetterwarps -> usage hint
        dispatcher.register(
                literal("simplybetterwarps")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.basic", 1))
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(() -> Component.literal("[Simply Better Warps] Usage: /warp help"), false);
                            return 1;
                        })
        );

        // /warp, /warp help, /warp <name>
        dispatcher.register(
                literal("warp")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.basic", 1))
                        .executes(HELP_EXECUTOR)
                        .then(literal("help")
                                .requires(src -> Permissions.check(src, "simplybetter.warps.basic", 1))
                                .executes(HELP_EXECUTOR)
                        )
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .requires(src -> Permissions.check(src, "simplybetter.warps.warpto", 1))
                                .executes(WARPTP_EXECUTOR)
                        )
        );

        // /warpinfo <name>
        dispatcher.register(
                literal("warpinfo")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.warpinfo",1))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(INFO_EXECUTOR)
                        )
        );

        // /renamewarp <oldname> <newname>
        dispatcher.register(
                literal("renamewarp")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.renamewarp", 1))
                        .then(argument("oldname", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .then(argument("newname", StringArgumentType.word())
                                        .executes(RENAMEWARP_EXECUTOR)
                                )
                        )
        );


        // /setwarp <name>
        dispatcher.register(
                literal("setwarp")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.setwarp", 1))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(SETWARP_EXECUTOR)
                        )
        );

        // /delwarp <name>
        dispatcher.register(
                literal("delwarp")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.delwarp", 1))
                        .then(argument("name", StringArgumentType.word())
                                .suggests(WARP_NAME_SUGGESTER)
                                .executes(DELWARP_EXECUTOR)
                        )
        );

        // /warps
        dispatcher.register(
                literal("warps")
                        .requires(src -> Permissions.check(src, "simplybetter.warps.basic", 1))
                        .executes(LIST_EXECUTOR)
        );
    }
}
