package link.e4steam.lan;

import link.e4steam.Config;
import link.e4steam.E4steamClient;
import link.e4steam.MinecraftUiCompat;
import link.e4steam.Mirror;
import link.e4steam.internal.api.CoreAccessModeCatalog;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

/** One settings form for both vanilla LAN entry screens and an already published world. */
public final class LanSettingsScreen extends Screen {
    private final Screen parent;
    private final Screen worldOptions;
    private LanSettings.Snapshot draft;
    private CoreAccessModeCatalog.Selection access;
    private EditBox players;
    private EditBox port;
    private EditBox publicName;
    private Button onlineButton;
    private Button done;
    private boolean saving;
    private boolean published;
    private boolean playersValid = true;
    private boolean portValid = true;
    private int panelLeft, panelTop, panelWidth, panelHeight;
    private int gameTop, networkTop, sectionHeight, columnWidth;
    private int doneX, doneY, doneWidth;
    private int tabsLeft, tabWidth;

    public LanSettingsScreen(Screen parent) {
        this(parent, null);
    }

    public LanSettingsScreen(Screen parent, Screen worldOptions) {
        super(Mirror.translatable("text.e4steam_minecraft.lan.settings"));
        this.parent = parent;
        this.worldOptions = worldOptions;
        this.draft = LanSettings.current();
        this.access = CoreAccessModeCatalog.normalize(E4steamClient.selectedAccessMode,
                E4steamClient.selectedCustomAccessMode);
    }

    @Override protected void init() {
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) { onClose(); return; }
        if (worldOptions != null) addWorldSettingsTabs();
        published = LanServerSettingsApplier.published(server);
        LanSettings.Snapshot active = LanSettings.active(server);
        if (active != null && players == null) draft = active;
        int initialPort = published ? LanServerSettingsApplier.port(server) : draft.port();
        if (initialPort < 1 || initialPort > 65535) initialPort = LanSettings.DEFAULT_PORT;
        String playersText = numericValue(players == null
                ? Integer.toString(draft.maxPlayers()) : players.getValue(), draft.maxPlayers());
        String portText = numericValue(port == null
                ? Integer.toString(initialPort) : port.getValue(), initialPort);
        // Minecraft 26.x uses a denser, wider world-options layout. Give this
        // form enough room to breathe there while retaining the compact legacy
        // dialog on smaller screens.
        panelWidth = Math.min(420, width - 24);
        panelHeight = Math.min(288, height - 12);
        panelLeft = (width - panelWidth) / 2;
        panelTop = (height - panelHeight) / 2;
        boolean compact = panelHeight < 252;
        sectionHeight = compact ? 68 : 80;
        gameTop = panelTop + (compact ? 28 : 40);
        networkTop = gameTop + sectionHeight + 4;
        int left = panelLeft + 22;
        int contentWidth = panelWidth - 44;
        columnWidth = (contentWidth - 8) / 2;
        int right = left + columnWidth + 8;
        int gameRow = gameTop + 22;
        Button mode = addRenderableWidget(MinecraftUiCompat.button(gameModeLabel(), button -> {
            String[] modes = {"survival", "creative", "adventure", "spectator"};
            int index = java.util.Arrays.asList(modes).indexOf(draft.gameMode());
            draft = draft.withGameMode(modes[(index + 1) % modes.length]);
            button.setMessage(gameModeLabel());
            MinecraftUiCompat.tooltip(button, Mirror.translatable("text.e4steam_minecraft.lan.gameMode", gameModeLabel()));
        }, left, gameRow, columnWidth, 20));
        MinecraftUiCompat.tooltip(mode, Mirror.translatable("text.e4steam_minecraft.lan.gameMode", gameModeLabel()));
        Button cheats = addRenderableWidget(MinecraftUiCompat.button(state("cheats", draft.allowCheats()), button -> {
            draft = draft.withAllowCheats(!draft.allowCheats());
            button.setMessage(state("cheats", draft.allowCheats()));
        }, right, gameRow, columnWidth, 20));
        MinecraftUiCompat.tooltip(cheats, Mirror.translatable("text.e4steam_minecraft.lan.cheatsHelp"));
        addRenderableWidget(MinecraftUiCompat.button(state("pvp", draft.pvp()), button -> {
            draft = draft.withPvp(!draft.pvp());
            button.setMessage(state("pvp", draft.pvp()));
        }, left, gameRow + 24, columnWidth, 20));
        players = numberBox(right + columnWidth - 44, gameRow + 24, 44, draft.maxPlayers());
        players.setMaxLength(2);
        players.setValue(playersText);
        MinecraftUiCompat.tooltip(players, Mirror.translatable("text.e4steam_minecraft.lan.playersHelp"));
        int networkRow = networkTop + 22;
        Button accessButton = addRenderableWidget(MinecraftUiCompat.button(accessLabel(), button -> {
            access = CoreAccessModeCatalog.next(access.mode(), access.customModeId());
            button.setMessage(accessLabel());
            MinecraftUiCompat.tooltip(button, accessLabel());
            updatePublicNameVisibility();
        }, left, networkRow, contentWidth, 20));
        accessButton.active = Config.INSTANCE.hostEnabled.value();
        MinecraftUiCompat.tooltip(accessButton, accessLabel());
        Button online = addRenderableWidget(MinecraftUiCompat.button(state("onlineMode", draft.onlineMode()), button -> {
            draft = draft.withOnlineMode(!draft.onlineMode());
            button.setMessage(state("onlineMode", draft.onlineMode()));
        }, left, networkRow + 24, columnWidth, 20));
        onlineButton = online;
        MinecraftUiCompat.tooltip(online, Mirror.translatable("text.e4steam_minecraft.lan.onlineHelp"));
        port = numberBox(right + columnWidth - 64, networkRow + 24, 64, initialPort);
        port.setEditable(!published);
        // Set the actual numeric value after the read-only state is applied;
        // some 26.x EditBox versions otherwise keep showing the hint text.
        port.setValue(portText);
        MinecraftUiCompat.tooltip(port, Mirror.translatable(published
                ? "text.e4steam_minecraft.lan.portLocked" : "text.e4steam_minecraft.lan.portHelp"));
        String nameText = publicName == null ? Config.INSTANCE.publicServerName.value() : publicName.getValue();
        if (nameText.isBlank()) nameText = minecraft.getUser().getName() + " server";
        publicName = addRenderableWidget(new EditBox(font, left + 88, networkRow + 48,
                contentWidth - 88, 20, Mirror.literal("Имя сервера")));
        publicName.setMaxLength(48);
        publicName.setValue(nameText);
        updatePublicNameVisibility();
        int footer = networkTop + sectionHeight + 24 + (compact ? 8 : 12);
        int footerWidth = (panelWidth - 32) / 2;
        addRenderableWidget(MinecraftUiCompat.button(Mirror.translatable("gui.cancel"), button -> onClose(),
                panelLeft + 12, footer, footerWidth, 20));
        doneX = panelLeft + panelWidth - 12 - footerWidth;
        doneY = footer;
        doneWidth = footerWidth;
        done = addRenderableWidget(MinecraftUiCompat.button(Mirror.translatable(published
                ? "text.e4steam_minecraft.lan.apply" : "lanServer.start"), button -> save(),
                panelLeft + panelWidth - 12 - footerWidth, footer, footerWidth, 20));
        MinecraftUiCompat.numericResponder(players, value -> validate());
        MinecraftUiCompat.numericResponder(port, value -> validate());
        validate();
    }

    private void addWorldSettingsTabs() {
        int availableWidth = Math.max(1, width - 24);
        tabWidth = Math.min(168, Math.max(70, (availableWidth - 8) / 2));
        tabsLeft = (width - tabWidth * 2 - 8) / 2;
        Button lanTab = addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.lan.tabLan"), button -> { },
                tabsLeft, 6, tabWidth, 20));
        lanTab.active = false;
        addRenderableWidget(MinecraftUiCompat.button(
                Mirror.translatable("text.e4steam_minecraft.lan.tabWorld"),
                button -> WorldOptionsScreenSupport.openNativeWorldTab(minecraft, worldOptions, parent),
                tabsLeft + tabWidth + 8, 6, tabWidth, 20));
    }

    public void renderLanPanel(Object context) {
        LanScreenGraphics graphics = new LanScreenGraphics(context);
        graphics.fill(0, 0, width, height, 0xB80A1019);
        graphics.fill(panelLeft + 3, panelTop + 4, panelLeft + panelWidth + 3,
                panelTop + panelHeight + 4, 0x50000000);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + panelHeight, 0xF216202C);
        graphics.fill(panelLeft, panelTop, panelLeft + 1, panelTop + panelHeight, 0xFF334A5E);
        graphics.fill(panelLeft + panelWidth - 1, panelTop, panelLeft + panelWidth,
                panelTop + panelHeight, 0xFF334A5E);
        graphics.fill(panelLeft, panelTop + panelHeight - 1, panelLeft + panelWidth,
                panelTop + panelHeight, 0xFF334A5E);
        graphics.fill(panelLeft, panelTop, panelLeft + panelWidth, panelTop + 2, 0xFF54CFBC);
        section(graphics, gameTop, "rules");
        section(graphics, networkTop, "connection");
    }

    private void renderLanLabels(LanScreenGraphics graphics) {
        graphics.drawCenteredString(font, title, width / 2, panelTop + 12, 0xF1F6FB);
        if (panelHeight >= 252) graphics.drawCenteredString(font, Mirror.translatable(published
                ? "text.e4steam_minecraft.lan.live" : "text.e4steam_minecraft.lan.ready"),
                width / 2, panelTop + 27, published ? 0x7DE1C7 : 0x98AABD);
        sectionLabel(graphics, gameTop, "rules");
        sectionLabel(graphics, networkTop, "connection");
        int right = panelLeft + 22 + columnWidth + 8;
        graphics.drawString(font, Mirror.translatable("text.e4steam_minecraft.lan.players"),
                right, gameTop + 52, playersValid ? 0xB7C6D5 : 0xFF8E8E, false);
        if (isPublicServer()) graphics.drawString(font, Mirror.literal("Имя сервера"),
                panelLeft + 22, networkTop + 76, 0xB7C6D5, false);
        graphics.drawString(font, Mirror.translatable("text.e4steam_minecraft.lan.port"),
                right, networkTop + 52, !portValid ? 0xFF8E8E : published ? 0x738497 : 0xB7C6D5, false);
        if (!playersValid) outline(graphics, players, 44);
        if (!portValid) outline(graphics, port, 64);
    }

    public void renderLanIndicators(Object context) {
        LanScreenGraphics graphics = new LanScreenGraphics(context);
        renderLanLabels(graphics);
        if (worldOptions != null) graphics.fill(tabsLeft, 27, tabsLeft + tabWidth, 29, 0xFF54CFBC);
        int right = panelLeft + 22 + columnWidth + 8;
        // Retain native Minecraft button focus and hover behavior.
        indicator(graphics, right + 3, gameTop + 25, draft.allowCheats());
        indicator(graphics, panelLeft + 25, gameTop + 49, draft.pvp());
        indicator(graphics, panelLeft + 25, networkTop + 49, draft.onlineMode());
        graphics.fill(doneX, doneY + 19, doneX + doneWidth, doneY + 20,
                done.active ? 0xFF54CFBC : 0xFF43505E);
    }

    private void section(LanScreenGraphics graphics, int top, String key) {
        int left = panelLeft + 12;
        graphics.fill(left, top, panelLeft + panelWidth - 12, top + (key.equals("connection") ? sectionHeight + 24 : sectionHeight), 0xFF202D3B);
        graphics.fill(left, top, panelLeft + panelWidth - 12, top + 1, 0xFF2B3D4D);
        graphics.fill(left, top + (key.equals("connection") ? sectionHeight + 24 : sectionHeight) - 1, panelLeft + panelWidth - 12,
                top + (key.equals("connection") ? sectionHeight + 24 : sectionHeight), 0xFF2B3D4D);
        graphics.fill(left, top, left + 2, top + (key.equals("connection") ? sectionHeight + 24 : sectionHeight), 0xFF334A5E);
    }

    private void sectionLabel(LanScreenGraphics graphics, int top, String key) {
        int left = panelLeft + 12;
        graphics.drawString(font, Mirror.translatable("text.e4steam_minecraft.lan." + key),
                left + 10, top + 7, 0x92D9CE, false);
    }

    private void indicator(LanScreenGraphics graphics, int x, int y, boolean enabled) {
        graphics.fill(x, y, x + 2, y + 14, enabled ? 0xFF66D5AF : 0xFF758496);
    }

    private void outline(LanScreenGraphics graphics, EditBox input, int size) {
        int x = panelLeft + 22 + columnWidth + 8 + columnWidth - size;
        int y = (input == players ? gameTop : networkTop) + 46;
        graphics.fill(x - 2, y - 2, x + size + 2, y - 1, 0xFFFF8E8E);
        graphics.fill(x - 2, y + 21, x + size + 2, y + 22, 0xFFFF8E8E);
    }

    private EditBox numberBox(int x, int y, int size, int value) {
        // The external labels already name these fields. An empty widget
        // message avoids leaking translation-component debug text into the
        // 26.3 EditBox rendering path.
        EditBox input = new EditBox(font, x, y, size, 20, Mirror.literal(""));
        input.setMaxLength(5);
        input.setValue(Integer.toString(value));
        return addRenderableWidget(input);
    }

    private String numericValue(String value, int fallback) {
        String digits = value == null ? "" : value.replaceAll("[^0-9]", "");
        return digits.isEmpty() ? Integer.toString(fallback) : digits;
    }

    private void validate() {
        if (done == null || players == null || port == null) return;
        String playerText = normalizeDigits(players.getValue());
        if (!playerText.equals(players.getValue())) {
            if (playerText.isEmpty() && !players.getValue().isEmpty()) playerText = "8";
            players.setValue(playerText);
        }
        Integer count = number(playerText, LanSettings.MIN_PLAYERS, LanSettings.MAX_PLAYERS);

        Integer selectedPort;
        if (published) {
            selectedPort = Integer.valueOf(draft.port());
        } else {
            String rawPort = port.getValue();
            String portText = normalizeDigits(rawPort);
            if (!portText.equals(rawPort)) {
                if (portText.isEmpty() && !rawPort.isEmpty()) {
                    portText = Integer.toString(LanSettings.DEFAULT_PORT);
                }
                port.setValue(portText);
            }
            selectedPort = portText.trim().isEmpty()
                    ? Integer.valueOf(LanSettings.DEFAULT_PORT)
                    : number(portText, 1, 65535);
        }
        playersValid = count != null;
        portValid = selectedPort != null;
        if (playersValid && portValid) draft = draft.withMaxPlayers(count).withPort(selectedPort);
        done.active = playersValid && portValid && !saving;
    }

    private String normalizeDigits(String value) {
        return value == null ? "" : value.replaceAll("[^0-9]", "");
    }

    private Integer number(String text, int min, int max) {
        try {
            int value = Integer.parseInt(text);
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException invalid) { return null; }
    }

    private boolean isPublicServer() {
        return access.customModeId().equals("k2studio:public")
                || access.translationKey().equals("e4steam_public_servers.access.public");
    }

    private void updatePublicNameVisibility() {
        if (publicName == null) return;
        publicName.visible = isPublicServer();
        onlineButton.visible = true;
        port.visible = true;
    }

    private void applyPublicName() {
        if (!isPublicServer()) return;
        String name = publicName.getValue().trim();
        if (name.isBlank()) name = minecraft.getUser().getName() + " server";
        try {
            Class<?> runtime = Class.forName("dev.k2studio.e4steam.publicservers.common.PublicServersRuntime");
            runtime.getMethod("setIntegratedServerName", String.class).invoke(null, name);
            Config.INSTANCE.publicServerName.setValue(name, true);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Public Servers name bridge is unavailable", failure);
        }
    }
    private void save() {
        validate();
        if (!done.active) return;
        if (port.getValue().trim().isEmpty()) port.setValue(Integer.toString(draft.port()));
        if (access.requiresConfirmation() && !E4steamClient.customAccessConfirmed(access.customModeId())) {
            MinecraftUiCompat.setScreen(minecraft, new ConfirmScreen(confirmed -> {
                if (confirmed) E4steamClient.confirmCustomAccess(access.customModeId());
                MinecraftUiCompat.setScreen(minecraft, this);
                if (confirmed) save();
            }, Mirror.translatable(access.confirmationTitleKey()), Mirror.translatable(access.confirmationMessageKey())));
            return;
        }
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null) { onClose(); return; }
        saving = true;
        done.active = false;
        LanSettings.Snapshot selected = draft;
        try { applyPublicName(); } catch (RuntimeException failure) {
            saving = false; E4steamClient.LOGGER.warn("Cannot set public server name", failure);
            done.setMessage(Mirror.translatable("text.e4steam_minecraft.lan.failed")); validate(); return;
        }
        E4steamClient.selectedAccessMode = access.mode();
        E4steamClient.selectedCustomAccessMode = access.customModeId();
        server.execute(() -> {
            try {
                if (minecraft.getSingleplayerServer() != server)
                    throw new IllegalStateException("The integrated world changed");
                LanServerSettingsApplier.apply(server, selected);
                if (!published && !LanServerSettingsApplier.publish(server, selected))
                    throw new IllegalStateException("Minecraft could not open the selected LAN port");
                // Vanilla publication may reset game mode/commands; apply the same snapshot again.
                LanServerSettingsApplier.apply(server, selected);
                minecraft.execute(() -> {
                    if (minecraft.getSingleplayerServer() != server) { saving = false; return; }
                    ModernLanSettings.save(selected);
                    if (published) ModernLanSettings.refreshSteamSession(server);
                    MinecraftUiCompat.setScreen(minecraft, null);
                });
            } catch (RuntimeException failure) {
                E4steamClient.LOGGER.warn("Could not apply e4steam LAN settings", failure);
                minecraft.execute(() -> {
                    saving = false;
                    done.setMessage(Mirror.translatable("text.e4steam_minecraft.lan.failed"));
                    validate();
                });
            }
        });
    }

    private Component gameModeLabel() {
        return Mirror.translatable("gameMode." + draft.gameMode());
    }

    private Component accessLabel() {
        return Mirror.append(Mirror.append(Mirror.translatable("text.e4steam_minecraft.accessMode"),
                Mirror.literal(": ")), Mirror.translatable(access.translationKey()));
    }

    private Component state(String key, boolean enabled) {
        return Mirror.translatable("text.e4steam_minecraft.lan." + key,
                Mirror.translatable(enabled ? "options.on" : "options.off"));
    }

    @Override public void onClose() {
        if (!saving) MinecraftUiCompat.setScreen(minecraft, parent);
    }
}
