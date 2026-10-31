package com.negativestudios.negativeplayer;

import com.negativestudios.negativeplayer.network.NegativePlayerNetwork;
import com.negativestudios.negativeplayer.command.NegativePlayerCommand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(NegativePlayer.MOD_ID)
public final class NegativePlayer {
    public static final String MOD_ID = "negativeplayer";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public NegativePlayer() {
        NegativePlayerNetwork.register();
        MinecraftForge.EVENT_BUS.addListener(NegativePlayerCommand::register);
    }
}
