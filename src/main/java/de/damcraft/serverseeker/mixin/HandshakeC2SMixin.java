package de.damcraft.serverseeker.mixin;

import com.google.gson.JsonObject;
import de.damcraft.serverseeker.modules.BungeeSpoofModule;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.network.Http;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

import static de.damcraft.serverseeker.ServerSeeker.LOG;
import static de.damcraft.serverseeker.ServerSeeker.gson;
import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Mixin for modifying handshake packets to bypass BungeeCord IP forwarding.
 */
@Mixin(HandshakeC2SPacket.class)
public abstract class HandshakeC2SMixin {
    @Shadow @Final @Mutable private String address;
    @Shadow public abstract ConnectionIntent getIntent();

    @Unique private static final String MOJANG_API_URL = "https://api.mojang.com/users/profiles/minecraft/";
    @Unique private static final String FORWARDING_FORMAT = "\u0000%s\u0000%s";
    @Unique private static final int MAX_ADDRESS_LENGTH = 255;

    @Inject(
        method = "<init>(ILjava/lang/String;ILnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V",
        at = @At("RETURN")
    )
    private void onHandshakeConstructed(int protocolVersion, String address, int port,
                                        ConnectionIntent intent, CallbackInfo ci) {
        if (getIntent() != ConnectionIntent.LOGIN) return;

        BungeeSpoofModule module = Modules.get().get(BungeeSpoofModule.class);
        if (!module.isActive() || !module.shouldSpoofCurrentServer()) return;

        try {
            String spoofedAddress = module.getSpoofedAddress();
            String playerUuid = getPlayerUuid(mc.getSession().getUsername());

            String forwardedAddress = String.format(FORWARDING_FORMAT, spoofedAddress, playerUuid);
            String newAddress = address + forwardedAddress;

            if (newAddress.length() > MAX_ADDRESS_LENGTH) {
                LOG.warn("Spoofed address too long, truncating...");
                newAddress = newAddress.substring(0, MAX_ADDRESS_LENGTH);
            }

            this.address = newAddress;
            LOG.info("Spoofed BungeeCord handshake with address: {}", spoofedAddress);
        } catch (Exception e) {
            LOG.error("Failed to spoof BungeeCord handshake", e);
            if (module.enableWarning.get()) {
                module.warning("BungeeSpoof failed - connection not modified");
            }
        }
    }

    /**
     * Gets the player's UUID from Mojang API or falls back to local session UUID.
     */
    @Unique
    private String getPlayerUuid(String username) {
        UUID localUuid = mc.getSession().getUuidOrNull();
        if (localUuid != null) return localUuid.toString().replace("-", "");

        try {
            String response = Http.get(MOJANG_API_URL + username)
                .timeout(3000)
                .sendString();

            if (response != null) {
                JsonObject json = gson.fromJson(response, JsonObject.class);
                if (json != null && json.has("id")) {
                    return json.get("id").getAsString();
                }
            }
        } catch (Exception e) {
            LOG.warn("Failed to fetch UUID from Mojang API", e);
        }

        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes()).toString().replace("-", "");
    }
}
