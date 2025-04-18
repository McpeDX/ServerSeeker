package de.damcraft.serverseeker.gui;

import com.google.common.net.HostAndPort;
import de.damcraft.serverseeker.ServerSeeker;
import de.damcraft.serverseeker.ssapi.requests.ServerInfoRequest;
import de.damcraft.serverseeker.ssapi.responses.ServerInfoResponse;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.utils.network.Http;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;

import static de.damcraft.serverseeker.ServerSeeker.LOG;

public class ServerInfoScreen extends WindowScreen {
    private final String serverIp;

    public ServerInfoScreen(String serverIp) {
        super(GuiThemes.get(), "Server Info: " + serverIp);
        this.serverIp = serverIp;
    }

    @Override
    public void initWidgets() {
        add(theme.label("Fetching server info..."));

        HostAndPort hap = HostAndPort.fromString(serverIp);
        ServerInfoRequest request = new ServerInfoRequest(ServerSeeker.API_KEY, hap.getHost(), hap.getPort());

        MeteorExecutor.execute(() -> {
            ServerInfoResponse response = Http.post("https://api.serverseeker.net/server_info")
                .exceptionHandler(e -> LOG.error("Could not post to 'server_info': ", e))
                .bodyJson(request)
                .sendJson(ServerInfoResponse.class);

            this.client.execute(() -> {
                clear();

                if (response == null) {
                    add(theme.label("Network error")).expandX();
                    return;
                }

                if (response.isError()) {
                    add(theme.label(response.error())).expandX();
                    return;
                }

                load(response, hap);
            });
        });
    }

    private void load(ServerInfoResponse response, HostAndPort hap) {
        Boolean cracked = response.cracked();
        String description = response.description();
        int onlinePlayers = response.onlinePlayers();
        int maxPlayers = response.maxPlayers();
        int protocol = response.protocol();
        int lastSeen = response.lastSeen();
        String version = response.version();
        String software = response.serverSoftware();  // NEW
        List<ServerInfoResponse.Player> players = response.players();

        WTable dataTable = add(theme.table()).widget();

        dataTable.add(theme.label("Cracked: "));
        dataTable.add(theme.label(cracked == null ? "Unknown" : cracked.toString()));
        dataTable.row();

        dataTable.add(theme.label("Description: "));
        if (description.length() > 100) description = description.substring(0, 100) + "...";
        description = description.replace("\n", "\\n").replace("§r", "");
        dataTable.add(theme.label(description));
        dataTable.row();

        dataTable.add(theme.label("Online Players (last scan): "));
        dataTable.add(theme.label(String.valueOf(onlinePlayers)));
        dataTable.row();

        dataTable.add(theme.label("Max Players: "));
        dataTable.add(theme.label(String.valueOf(maxPlayers)));
        dataTable.row();

        dataTable.add(theme.label("Last Seen: "));
        String lastSeenDate = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
            .format(Instant.ofEpochSecond(lastSeen).atZone(ZoneId.systemDefault()).toLocalDateTime());
        dataTable.add(theme.label(lastSeenDate));
        dataTable.row();

        dataTable.add(theme.label("Version: "));
        dataTable.add(theme.label(version + " (" + protocol + ")"));
        dataTable.row();

        // Server Software
        String guessedSoftware = guessSoftwareFromDescription(description);
        String finalSoftware = software != null && !software.isEmpty() ? software : guessedSoftware;
        dataTable.add(theme.label("Server Software: "));
        dataTable.add(theme.label(finalSoftware != null && !finalSoftware.isEmpty() ? finalSoftware : "Unknown"));
        dataTable.row();

        if (!players.isEmpty()) {
            WTable playersTable = add(theme.table()).expandX().widget();

            playersTable.add(theme.label(""));
            playersTable.row();
            playersTable.add(theme.label("Players:"));
            playersTable.row();

            playersTable.add(theme.label("Name ")).expandX();
            playersTable.add(theme.label("Last seen ")).expandX();
            playersTable.row();

            playersTable.add(theme.horizontalSeparator()).expandX();
            playersTable.row();

            for (ServerInfoResponse.Player player : players) {
                String name = player.name();
                long playerLastSeen = player.lastSeen();
                String lastSeenFormatted = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .format(Instant.ofEpochSecond(playerLastSeen).atZone(ZoneId.systemDefault()).toLocalDateTime());

                playersTable.add(theme.label(name + " ")).expandX();
                playersTable.add(theme.label(lastSeenFormatted + " ")).expandX();
                playersTable.row();
            }
        }

        WButton joinServerButton = add(theme.button("Join this Server")).expandX().widget();
        joinServerButton.action = () ->
            ConnectScreen.connect(new TitleScreen(), MinecraftClient.getInstance(),
                new ServerAddress(hap.getHost(), hap.getPort()),
                new ServerInfo("a", hap.toString(), ServerInfo.ServerType.OTHER), false, null);
    }

    private String guessSoftwareFromDescription(String description) {
        String descLower = description.toLowerCase();
        if (descLower.contains("paper")) return "Paper";
        if (descLower.contains("purpur")) return "Purpur";
        if (descLower.contains("spigot")) return "Spigot";
        if (descLower.contains("bukkit")) return "Bukkit";
        if (descLower.contains("fabric")) return "Fabric";
        if (descLower.contains("forge")) return "Forge";
        if (descLower.contains("vanilla")) return "Vanilla";
        if (descLower.contains("snapshot")) return "Snapshot";
        if (descLower.contains("velocity")) return "Velocity";
        if (descLower.contains("bungeecord")) return "BungeeCord";
        return "Unknown";
    }
}
