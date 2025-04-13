package de.damcraft.serverseeker.mixin;

import de.damcraft.serverseeker.gui.GetInfoScreen;
import de.damcraft.serverseeker.gui.ServerSeekerScreen;
import de.damcraft.serverseeker.utils.ServerListUtils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    @Shadow protected MultiplayerServerListWidget serverListWidget;

    @Unique private static final Identifier SERVER_SEEKER_ICON = new Identifier("serverseeker", "textures/gui/icon.png");
    @Unique private static final Identifier PLAYER_INFO_ICON = new Identifier("serverseeker", "textures/gui/players.png");

    @Unique private ButtonWidget serverSeekerButton;
    @Unique private ButtonWidget playerInfoButton;
    @Unique private ButtonWidget quickJoinButton;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screen/multiplayer/MultiplayerScreen;updateButtonActivationStates()V"))
    private void onInit(CallbackInfo ci) {
        int buttonWidth = 20;
        int buttonX = this.width - buttonWidth - 5;
        int buttonSpacing = buttonWidth + 2;

        // ServerSeeker button
        this.serverSeekerButton = this.addDrawableChild(
            new TexturedButtonWidget(
                buttonX - buttonSpacing * 2,
                3,
                buttonWidth,
                20,
                0, 0,
                20,
                SERVER_SEEKER_ICON,
                20, 40,
                btn -> this.client.setScreen(new ServerSeekerScreen((MultiplayerScreen) (Object) this)),
                Text.literal("Open ServerSeeker")
            )
        );

        // Player Info button
        this.playerInfoButton = this.addDrawableChild(
            new TexturedButtonWidget(
                buttonX - buttonSpacing,
                3,
                buttonWidth,
                20,
                0, 0,
                20,
                PLAYER_INFO_ICON,
                20, 40,
                btn -> this.openPlayerInfoScreen(),
                Text.literal("View Player Info")
            )
        );

        // Quick Join button
        this.quickJoinButton = this.addDrawableChild(
            new ButtonWidget.Builder(Text.literal("Quick Join"), btn ->
                ServerListUtils.joinBestServer((MultiplayerScreen) (Object) this)
            )
            .position(buttonX, 3)
            .width(buttonWidth)
            .build()
        );
    }

    @Unique
    private void openPlayerInfoScreen() {
        MultiplayerServerListWidget.Entry entry = this.serverListWidget.getSelectedOrNull();
        if (entry != null && this.client != null) {
            this.client.setScreen(new GetInfoScreen((MultiplayerScreen) (Object) this, entry));
        }
    }

    @Inject(method = "updateButtonActivationStates", at = @At("TAIL"))
    private void onUpdateButtonActivationStates(CallbackInfo ci) {
        boolean hasValidSelection = this.serverListWidget.getSelectedOrNull() != null
            && !(this.serverListWidget.getSelectedOrNull() instanceof MultiplayerServerListWidget.ScanningEntry);

        this.playerInfoButton.active = hasValidSelection;
        this.quickJoinButton.active = hasValidSelection;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (this.serverSeekerButton.isHovered()) {
            context.drawTooltip(this.textRenderer, Text.literal("Open ServerSeeker"), mouseX, mouseY);
        }
        if (this.playerInfoButton.isHovered()) {
            context.drawTooltip(this.textRenderer, Text.literal("View Player Info"), mouseX, mouseY);
        }
        if (this.quickJoinButton.isHovered()) {
            context.drawTooltip(this.textRenderer, Text.literal("Quick Join Best Server"), mouseX, mouseY);
        }
    }
                    }
