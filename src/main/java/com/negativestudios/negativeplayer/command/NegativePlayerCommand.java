package com.negativestudios.negativeplayer.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.negativestudios.negativeplayer.NegativePlayer;
import com.negativestudios.negativeplayer.network.NegativePlayerNetwork;
import com.negativestudios.negativeplayer.network.PlaybackRequestPacket;
import com.negativestudios.negativeplayer.util.VideoUrlValidator;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.Collection;

public final class NegativePlayerCommand {
    private static final int MAX_FADE_TICKS = 1_200;
    private static final int START_DELAY_TICKS = 60;

    private NegativePlayerCommand() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("negativeplayer")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("url", StringArgumentType.string())
                                .then(Commands.argument("fadein", IntegerArgumentType.integer(0, MAX_FADE_TICKS))
                                        .then(Commands.argument("fadeout", IntegerArgumentType.integer(0, MAX_FADE_TICKS))
                                                .then(Commands.argument("targets", EntityArgument.players())
                                                        .then(Commands.argument("volumen", IntegerArgumentType.integer(1, 100))
                                                                .executes(context -> execute(
                                                                        context.getSource(),
                                                                        StringArgumentType.getString(context, "url"),
                                                                        IntegerArgumentType.getInteger(context, "fadein"),
                                                                        IntegerArgumentType.getInteger(context, "fadeout"),
                                                                        EntityArgument.getPlayers(context, "targets"),
                                                                        IntegerArgumentType.getInteger(context, "volumen")
                                                                ))
                                                        )
                                                )
                                        )
                                )
                        )
        );
    }

    private static int execute(
            net.minecraft.commands.CommandSourceStack source,
            String url,
            int fadeInTicks,
            int fadeOutTicks,
            Collection<ServerPlayer> targets,
            int volume
    ) throws CommandSyntaxException {
        if (VideoUrlValidator.detectProvider(url) == VideoUrlValidator.Provider.UNSUPPORTED) {
            throw new SimpleCommandExceptionType(Component.literal(
                    "NegativePlayer solo acepta enlaces HTTPS compatibles de Google Drive o YouTube."
            )).create();
        }

        long startAt = source.getLevel().getGameTime() + START_DELAY_TICKS;
        PlaybackRequestPacket packet = new PlaybackRequestPacket(
                url,
                fadeInTicks,
                fadeOutTicks,
                volume,
                startAt
        );

        for (ServerPlayer player : targets) {
            NegativePlayerNetwork.sendToPlayer(player, packet);
        }

        NegativePlayer.LOGGER.info(
                "Comando /negativeplayer ejecutado: solicitud enviada a {} jugador(es). Proveedor={}, fadeIn={} ticks, fadeOut={} ticks, volumen={}.",
                targets.size(),
                VideoUrlValidator.detectProvider(url),
                fadeInTicks,
                fadeOutTicks,
                volume
        );

        // Deliberadamente no se envía ningún mensaje de éxito al chat.
        return targets.size();
    }
}
