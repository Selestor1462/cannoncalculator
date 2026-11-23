package dev.selestor1462.cannonencoder.client.gui;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.PigCannonCalculator;
import dev.selestor1462.cannonencoder.client.ClientSessionContext;
import dev.selestor1462.cannonencoder.client.HighlightManager;
import dev.selestor1462.cannonencoder.client.validation.EncodingValidator;
import dev.selestor1462.cannonencoder.client.validation.ValidationReport;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import dev.selestor1462.cannonencoder.client.AutoAnchorDetector;
import dev.selestor1462.cannonencoder.client.MaterialChecklist;
import dev.selestor1462.cannonencoder.client.WorldHighlightRenderer;
import dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager;
import dev.selestor1462.cannonencoder.state.TargetRecord;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PigCannonScreen extends Screen {
    private static final ResourceLocation LAMP_OFF_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_lamp.png");
    private static final ResourceLocation LAMP_ON_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_lamp_on.png");

    private static final PigCannonCalculator PIG_CALCULATOR = new PigCannonCalculator();

    private final ClientStateStore stateStore;
    private final HighlightManager highlightManager;
    private final EasyPlaceManager easyPlaceManager;

    // Controls
    private EditBox destXBox;
    private EditBox destZBox;
    private Button useMyPosBtn;
    private Button historyBtn;
    private boolean isHistoryOpen = false;
    private int historyScrollOffset = 0;
    private int historyInspectedRow = -1;
    private int historyInspectedCol = -1;
    private EditBox historyLabelBox;
    private boolean updatingLabelBox = false;

    private Button setAnchorBtn;
    private Button calculateBtn;
    private Button checkEncodingBtn;
    private Button easyPlaceBtn;
    private Button railsToggleBtn;
    private Button glowToggleBtn;
    private Button clearHighlightBtn;
    private Button doneBtn;

    // Lamp selector
    private int selectedLevel = 0; // 0 to 3
    private int selectedColumn = 0; // 0 to 8
    private ValidationReport lastReport = null;
    private String statusMessage = "";

    // Static session cache preserved across GUI opens/closes
    private static String cachedDestX = "";
    private static String cachedDestZ = "";
    private static int cachedSelectedLevel = -1;
    private static int cachedSelectedCol = -1;

    public PigCannonScreen(
        ClientStateStore stateStore,
        HighlightManager highlightManager
    ) {
        this(stateStore, highlightManager, null);
    }

    public PigCannonScreen(
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        EasyPlaceManager easyPlaceManager
    ) {
        super(Component.translatable("cannoncalculator.gui.title"));
        this.stateStore = stateStore;
        this.highlightManager = highlightManager;
        this.easyPlaceManager = easyPlaceManager;

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(Minecraft.getInstance());
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
            this.selectedLevel = (cachedSelectedLevel >= 0) ? cachedSelectedLevel : Math.max(0, Math.min(3, state.selectedRow()));
            this.selectedColumn = (cachedSelectedCol >= 0) ? cachedSelectedCol : Math.max(0, Math.min(8, state.selectedCol()));

            if (this.easyPlaceManager != null) {
                this.easyPlaceManager.setEnabled(state.easyPlaceEnabled());
            }


        }
    }

    private int getTableY() {
        return 74;
    }

    private int getTableWidth() {
        return 194;
    }

    private int getLeftColX() {
        int centerX = this.width / 2;
        int gap = 12;
        int totalW = 194 + gap + 194;
        return centerX - totalW / 2;
    }

    private int getRightX() {
        return getLeftColX() + 194 + 12;
    }

    private int getPanelY() {
        return 26;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;
        int leftColX = getLeftColX();
        int topY = getPanelY();

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(Minecraft.getInstance());
        SessionState state = sessionContext.isPresent()
            ? this.stateStore.get(sessionContext.get().environmentKey())
            : SessionState.empty(this.stateStore.globalEasyPlace(), this.stateStore.globalSequentialRails(), this.stateStore.globalHighlightsVisible());

        // Target Destination X / Z boxes
        int inputY = topY + 2;
        int destBoxWidth = 60;

        this.destXBox = new EditBox(this.font, leftColX + 13, inputY, destBoxWidth, 16, Component.translatable("cannoncalculator.gui.dest_x"));
        this.destXBox.setValue(cachedDestX);
        this.destXBox.setTooltip(Tooltip.create(Component.literal("Target X destination coordinate")));
        this.addRenderableWidget(this.destXBox);

        this.destZBox = new EditBox(this.font, leftColX + 89, inputY, destBoxWidth, 16, Component.translatable("cannoncalculator.gui.dest_z"));
        this.destZBox.setValue(cachedDestZ);
        this.destZBox.setTooltip(Tooltip.create(Component.literal("Target Z destination coordinate")));
        this.addRenderableWidget(this.destZBox);

        this.useMyPosBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.pos"), btn -> this.onUseMyPos())
            .bounds(leftColX + 153, inputY, 41, 16)
            .build();
        this.useMyPosBtn.setTooltip(Tooltip.create(Component.translatable("cannoncalculator.gui.button.pos.tooltip")));
        this.addRenderableWidget(this.useMyPosBtn);

        int rightX = getRightX();
        this.historyBtn = Button.builder(Component.translatable("cannoncalculator.gui.history"), btn -> this.onToggleHistory())
            .bounds(rightX + 104, 6, 88, 18)
            .build();
        this.historyBtn.setTooltip(Tooltip.create(Component.translatable("cannoncalculator.gui.history.tooltip")));
        this.addRenderableWidget(this.historyBtn);

        // History modal label edit box
        this.historyLabelBox = new EditBox(this.font, 0, 0, 100, 16, Component.translatable("cannoncalculator.gui.target_label"));
        this.historyLabelBox.setMaxLength(32);
        this.historyLabelBox.visible = false;
        this.historyLabelBox.setResponder(newLabel -> {
            if (this.updatingLabelBox || !this.isHistoryOpen) return;
            if (this.historyInspectedRow < 0 || this.historyInspectedCol < 0) return;
            Optional<ClientSessionContext> sc = ClientSessionContext.resolve(this.minecraft);
            if (sc.isPresent()) {
                SessionState s = this.stateStore.get(sc.get().environmentKey());
                SessionState updated = s.updateTargetLabel(this.historyInspectedRow, this.historyInspectedCol, null, newLabel);
                this.stateStore.set(sc.get().environmentKey(), updated);
            }
        });
        this.addWidget(this.historyLabelBox);

        // Action buttons row: Set Anchor & Calculate
        int actY = topY + 24;

        this.setAnchorBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.auto_anchor"), btn -> this.onSetAnchorHere())
            .bounds(leftColX, actY, 94, 18)
            .build();
        updateAnchorTooltip(state.anchor());
        this.addRenderableWidget(this.setAnchorBtn);

        this.calculateBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.calculate"), btn -> this.onCalculate())
            .bounds(leftColX + 98, actY, 96, 18)
            .build();
        this.calculateBtn.setTooltip(Tooltip.create(Component.literal("Calculate 4-stage acceleration timings for coordinates")));
        this.addRenderableWidget(this.calculateBtn);

        // Bottom action buttons: Check Encoding, EasyPlace, Rails Toggle, Glow Toggle, Clear, Done
        int bottomY = this.height - 19;

        this.checkEncodingBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.check"), btn -> this.onCheckEncoding())
            .bounds(0, 0, 42, 16)
            .build();
        this.addRenderableWidget(this.checkEncodingBtn);

        boolean easyEnabled = (this.easyPlaceManager != null && this.easyPlaceManager.isEnabled());
        this.easyPlaceBtn = Button.builder(
            Component.translatable(easyEnabled ? "cannoncalculator.gui.easyplace_btn_on" : "cannoncalculator.gui.easyplace_btn_off"),
            btn -> this.onToggleEasyPlace()
        ).bounds(0, 0, 58, 16).build();
        this.easyPlaceBtn.setTooltip(Tooltip.create(
            Component.translatable(easyEnabled ? "cannoncalculator.gui.easyplace_active" : "cannoncalculator.gui.easyplace_inactive")
        ));
        this.addRenderableWidget(this.easyPlaceBtn);

        boolean seqRails = (this.highlightManager != null && this.highlightManager.isSequentialRails());
        this.railsToggleBtn = Button.builder(
            Component.translatable(seqRails ? "cannoncalculator.gui.rails_step" : "cannoncalculator.gui.rails_all"),
            btn -> this.onToggleSequentialRails()
        ).bounds(0, 0, 42, 16).build();
        this.railsToggleBtn.setTooltip(Tooltip.create(
            Component.translatable(seqRails ? "cannoncalculator.gui.rails_step_tooltip" : "cannoncalculator.gui.rails_all_tooltip")
        ));
        this.addRenderableWidget(this.railsToggleBtn);

        boolean glowOn = state.highlightsVisible();
        if (this.highlightManager != null) {
            this.highlightManager.setVisible(glowOn);
        }
        this.glowToggleBtn = Button.builder(
            Component.translatable(glowOn ? "cannoncalculator.gui.glow_on" : "cannoncalculator.gui.glow_off"),
            btn -> this.onToggleGlow()
        ).bounds(0, 0, 38, 16).build();
        this.glowToggleBtn.setTooltip(Tooltip.create(Component.translatable("cannoncalculator.gui.glow_tooltip")));
        this.addRenderableWidget(this.glowToggleBtn);

        this.clearHighlightBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.clear"), btn -> this.onClearHighlight())
            .bounds(0, 0, 36, 16)
            .build();
        this.addRenderableWidget(this.clearHighlightBtn);

        this.doneBtn = Button.builder(Component.translatable("gui.done"), btn -> this.onClose())
            .bounds(0, 0, 38, 16)
            .build();
        this.addRenderableWidget(this.doneBtn);

        // Position bottom buttons sequentially
        int btnGap = 2;
        int easyBtnWidth = 58;
        int totalWidth = 42 + easyBtnWidth + 42 + 38 + 36 + 38 + (5 * btnGap);
        int curX = centerX - totalWidth / 2;
        this.checkEncodingBtn.setPosition(curX, bottomY); curX += 42 + btnGap;
        this.easyPlaceBtn.setPosition(curX, bottomY); curX += easyBtnWidth + btnGap;
        this.railsToggleBtn.setPosition(curX, bottomY); curX += 42 + btnGap;
        this.glowToggleBtn.setPosition(curX, bottomY); curX += 38 + btnGap;
        this.clearHighlightBtn.setPosition(curX, bottomY); curX += 36 + btnGap;
        this.doneBtn.setPosition(curX, bottomY);
        this.onApplyHighlight();
    }

    private void onUseMyPos() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        this.destXBox.setValue(String.valueOf((int) Math.floor(this.minecraft.player.getX())));
        this.destZBox.setValue(String.valueOf((int) Math.floor(this.minecraft.player.getZ())));
        this.statusMessage = "§aTarget set to your current coordinates.";
    }

    private void onToggleHistory() {
        if (this.isHistoryOpen) {
            closeHistoryModal();
        } else {
            openHistoryModal();
        }
    }

    private void openHistoryModal() {
        this.isHistoryOpen = true;
        this.historyScrollOffset = 0;
        this.historyInspectedRow = this.selectedLevel;
        this.historyInspectedCol = this.selectedColumn;
        updateWidgetActiveStates();
        updateHistoryLabelBox();
    }

    private void closeHistoryModal() {
        this.isHistoryOpen = false;
        if (this.historyLabelBox != null) {
            this.historyLabelBox.visible = false;
            this.historyLabelBox.setFocused(false);
        }
        this.setFocused(null);
        updateWidgetActiveStates();
    }

    private void updateWidgetActiveStates() {
        boolean active = !this.isHistoryOpen;
        if (this.historyBtn != null) { this.historyBtn.active = active; this.historyBtn.visible = active; }
        if (this.destXBox != null) {
            this.destXBox.setEditable(active);
            this.destXBox.visible = active;
            if (!active) this.destXBox.setFocused(false);
        }
        if (this.destZBox != null) {
            this.destZBox.setEditable(active);
            this.destZBox.visible = active;
            if (!active) this.destZBox.setFocused(false);
        }
        if (this.calculateBtn != null) { this.calculateBtn.active = active; this.calculateBtn.visible = active; }
        if (this.useMyPosBtn != null) { this.useMyPosBtn.active = active; this.useMyPosBtn.visible = active; }
        if (this.setAnchorBtn != null) { this.setAnchorBtn.active = active; this.setAnchorBtn.visible = active; }
        if (this.checkEncodingBtn != null) { this.checkEncodingBtn.active = active; this.checkEncodingBtn.visible = active; }
        if (this.easyPlaceBtn != null) { this.easyPlaceBtn.active = active; this.easyPlaceBtn.visible = active; }
        if (this.railsToggleBtn != null) { this.railsToggleBtn.active = active; this.railsToggleBtn.visible = active; }
        if (this.glowToggleBtn != null) { this.glowToggleBtn.active = active; this.glowToggleBtn.visible = active; }
        if (this.clearHighlightBtn != null) { this.clearHighlightBtn.active = active; this.clearHighlightBtn.visible = active; }
        if (this.doneBtn != null) { this.doneBtn.active = active; this.doneBtn.visible = active; }
    }

    private void updateHistoryLabelBox() {
        if (this.historyLabelBox == null) return;
        if (!this.isHistoryOpen || this.historyInspectedRow < 0 || this.historyInspectedCol < 0) {
            this.historyLabelBox.visible = false;
            return;
        }
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(null);
        TargetRecord tr = (state != null) ? findTargetForSlot(state.targetHistory(), this.historyInspectedRow, this.historyInspectedCol) : null;
        if (tr != null) {
            this.updatingLabelBox = true;
            this.historyLabelBox.setValue(tr.label() != null ? tr.label() : "");
            this.updatingLabelBox = false;
            this.historyLabelBox.visible = true;
        } else {
            this.historyLabelBox.visible = false;
        }
    }

    private void updateAnchorTooltip(AnchorState anchor) {
        if (this.setAnchorBtn == null) return;
        if (anchor != null && anchor.pos() != null && anchor.pos().getY() > 0) {
            String tip = "⚓ Auto Anchor (Launch Origin: X=" + anchor.pos().getX() + ", Z=" + anchor.pos().getZ() + ")\n"
                + "§7Click to auto-detect nearby 4x9 selector panel.\n"
                + "§8(Custom cannons can be set via /cannon anchor set <x> <z>)";
            this.setAnchorBtn.setTooltip(Tooltip.create(Component.literal(tip)));
        } else {
            String tip = "⚓ Auto Anchor (No anchor set)\n"
                + "§7Click to auto-detect nearby 4x9 selector panel.\n"
                + "§8(Custom cannons can be set via /cannon anchor set <x> <z>)";
            this.setAnchorBtn.setTooltip(Tooltip.create(Component.literal(tip)));
        }
    }

    private void onSetAnchorHere() {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) return;
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty()) return;

        AutoAnchorDetector.DetectionResult autoDet = AutoAnchorDetector.detectPigCannonAnchor(
            this.minecraft.level, this.minecraft.player.blockPosition()
        );
        if (autoDet == null || autoDet.pos() == null || autoDet.pos().getY() <= 0) {
            this.statusMessage = "§c✖ Failed to find Pig Cannon anchor nearby! Stand near snow layers (or set via /cannon anchor set <x> <z>).";
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("cannoncalculator.gui.auto_anchor_failed", "Pig Cannon"), true);
            }
            return;
        }

        BlockPos targetPos = autoDet.pos();
        String desc = autoDet.description();
        AnchorState anchor = new AnchorState(targetPos, sessionContext.get().dimensionId(), TemplateRegistry.CANNON_V1_DEFAULT, autoDet.panelPos());
        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        SessionState updated = state.withAnchor(anchor);

        this.stateStore.set(sessionContext.get().environmentKey(), updated);
        this.statusMessage = "§a✔ Auto-detected Pig Cannon Anchor at X=" + targetPos.getX() + ", Y=" + targetPos.getY() + ", Z=" + targetPos.getZ() + " (" + desc + ")!";
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("cannoncalculator.gui.auto_anchor_success", "Pig Cannon"), true);
        }
        updateAnchorTooltip(anchor);
        this.onApplyHighlight();
    }

    private void onCalculate() {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty()) {
            this.statusMessage = "§cWorld context not available.";
            return;
        }

        try {
            int destX = Integer.parseInt(this.destXBox.getValue().trim());
            int destZ = Integer.parseInt(this.destZBox.getValue().trim());
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());

            cachedDestX = this.destXBox.getValue();
            cachedDestZ = this.destZBox.getValue();

            AnchorState anchor = state.effectiveAnchor(CannonMode.PIG_CANNON);
            if ((anchor == null || anchor.pos().getY() <= 0) && this.minecraft.player != null && this.minecraft.level != null) {
                AutoAnchorDetector.DetectionResult autoDet = AutoAnchorDetector.detectPigCannonAnchor(
                    this.minecraft.level, this.minecraft.player.blockPosition()
                );
                if (autoDet != null && autoDet.pos().getY() > 0) {
                    anchor = new AnchorState(autoDet.pos(), sessionContext.get().dimensionId(), TemplateRegistry.CANNON_V1_DEFAULT, autoDet.panelPos());
                    state = state.withAnchor(anchor);
                    this.stateStore.set(sessionContext.get().environmentKey(), state);
                }
            }
            if (anchor == null || anchor.pos().getY() <= 0) {
                this.statusMessage = "§cFailed to find Pig Cannon Anchor! Stand near selector panel & click '⚓ Anchor'.";
                return;
            }

            int initX = anchor.pos().getX();
            int initZ = anchor.pos().getZ();

            CalculationResult result = PIG_CALCULATOR.calculate(initX, initZ, destX, destZ);
            double dist = Math.hypot(destX - initX, destZ - initZ);
            TargetRecord existing = findTargetForSlot(state.targetHistory(), this.selectedLevel, this.selectedColumn);
            String label = (existing != null && existing.label() != null) ? existing.label() : "";
            TargetRecord rec = new TargetRecord(destX, destZ, CannonMode.PIG_CANNON, this.selectedLevel, this.selectedColumn, dist, System.currentTimeMillis(), label);
            SessionState updatedState = state.withMode(CannonMode.PIG_CANNON)
                .withLastCalculation(result)
                .withSelectedSlot(this.selectedLevel, this.selectedColumn)
                .addTarget(rec);
            this.stateStore.set(sessionContext.get().environmentKey(), updatedState);

            this.highlightManager.apply(sessionContext.get(), updatedState, result, this.selectedLevel, this.selectedColumn);
            int totalTicks = result.straightAccelFirst() + result.straightAccelSecond() + result.diagonalAccelThird() + result.diagonalAccelFourth() + result.directionCode();
            double minutes = totalTicks / 1200.0;
            this.statusMessage = String.format(java.util.Locale.ROOT,
                "§aCalculated! Land: [%d, %d] | Time: %dt (~%.1fm) | Dir: %d",
                result.landingX(), result.landingZ(), totalTicks, minutes, result.directionCode());
        } catch (NumberFormatException e) {
            this.statusMessage = "§cInvalid numbers in coordinate fields.";
        }
    }

    private void onApplyHighlight() {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty()) return;

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        SessionState updatedState = state.withSelectedSlot(this.selectedLevel, this.selectedColumn);
        this.stateStore.set(sessionContext.get().environmentKey(), updatedState);
        this.highlightManager.apply(sessionContext.get(), updatedState, state.lastCalculation(), this.selectedLevel, this.selectedColumn);
        if (state.lastCalculation() != null) {
            this.statusMessage = "§aPig Cannon Highlight active: Level " + this.selectedLevel + ", Col " + (this.selectedColumn + 1);
        } else {
            this.statusMessage = "§aSelected Lamp: Level " + this.selectedLevel + ", Col " + (this.selectedColumn + 1);
        }
    }

    private void onClearHighlight() {
        this.highlightManager.clear();
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
            SessionState updatedState = state.withLastCalculation(null);
            this.stateStore.set(sessionContext.get().environmentKey(), updatedState);
        }
        this.statusMessage = "§eHighlight cleared from world.";
    }

    private void onToggleSequentialRails() {
        if (this.highlightManager == null) return;
        boolean enabled = this.highlightManager.toggleSequentialRails();
        this.railsToggleBtn.setMessage(Component.translatable(enabled ? "cannoncalculator.gui.rails_step" : "cannoncalculator.gui.rails_all"));
        this.railsToggleBtn.setTooltip(Tooltip.create(
            Component.translatable(enabled ? "cannoncalculator.gui.rails_step_tooltip" : "cannoncalculator.gui.rails_all_tooltip")
        ));
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withSequentialRails(enabled);
            this.stateStore.set(sessionContext.get().environmentKey(), state);
        }
        this.statusMessage = enabled
            ? "§aRails: Step mode (only shows rails after support is placed)."
            : "§eRails: All mode (shows all rails immediately).";
    }

    private void onToggleGlow() {
        if (this.highlightManager == null) return;
        boolean newVis = !this.highlightManager.isVisible();
        this.highlightManager.setVisible(newVis);
        if (this.glowToggleBtn != null) {
            this.glowToggleBtn.setMessage(Component.translatable(newVis ? "cannoncalculator.gui.glow_on" : "cannoncalculator.gui.glow_off"));
        }

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withHighlightsVisible(newVis);
            this.stateStore.set(sessionContext.get().environmentKey(), state);
        }
        this.statusMessage = newVis ? "§aWorld hologram glowing enabled." : "§eWorld hologram glowing hidden.";
    }

    private void onToggleEasyPlace() {
        if (this.easyPlaceManager == null) return;
        boolean enabled = this.easyPlaceManager.toggle();
        this.easyPlaceBtn.setMessage(Component.translatable(enabled ? "cannoncalculator.gui.easyplace_btn_on" : "cannoncalculator.gui.easyplace_btn_off"));
        this.easyPlaceBtn.setTooltip(Tooltip.create(
            Component.translatable(enabled ? "cannoncalculator.gui.easyplace_active" : "cannoncalculator.gui.easyplace_inactive")
        ));
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withEasyPlace(enabled);
            this.stateStore.set(sessionContext.get().environmentKey(), state);
        }
        this.statusMessage = enabled ? "§a✔ EasyPlace enabled." : "§cEasyPlace disabled.";
    }

    private void onCheckEncoding() {
        Minecraft client = Minecraft.getInstance();
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty() || client.level == null) return;

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        this.lastReport = EncodingValidator.validate(client.level, this.highlightManager.templateRegistry(), state);

        if (this.lastReport.isValid()) {
            this.statusMessage = "§aEncoding Check: ALL " + this.lastReport.totalChecked() + " BLOCKS VERIFIED!";
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable("cannoncalculator.gui.encoding_ready"), true);
            }
        } else {
            this.statusMessage = "§cEncoding Check: Found " + this.lastReport.errors().size() + " error(s)! Check chat.";
        }
        for (Component msg : this.lastReport.formatMessages()) {
            if (client.player != null) {
                client.player.displayClientMessage(msg, false);
            }
        }
    }

    private void loadTargetRecord(TargetRecord rec) {
        if (rec == null) return;
        if (this.destXBox != null) this.destXBox.setValue(String.valueOf(rec.destX()));
        if (this.destZBox != null) this.destZBox.setValue(String.valueOf(rec.destZ()));
        cachedDestX = String.valueOf(rec.destX());
        cachedDestZ = String.valueOf(rec.destZ());
        this.selectedLevel = Math.max(0, Math.min(3, rec.row()));
        this.selectedColumn = Math.max(0, Math.min(8, rec.col()));
        cachedSelectedLevel = this.selectedLevel;
        cachedSelectedCol = this.selectedColumn;

        this.onCalculate();

        if (rec.label() != null && !rec.label().isBlank()) {
            Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
            if (sessionContext.isPresent()) {
                SessionState s = this.stateStore.get(sessionContext.get().environmentKey());
                SessionState updated = s.updateTargetLabel(this.selectedLevel, this.selectedColumn, null, rec.label());
                this.stateStore.set(sessionContext.get().environmentKey(), updated);
            }
        }

        String loadedMsg = String.format(java.util.Locale.ROOT,
            "§a✔ Loaded Pig Cannon Lamp L%d C%d -> Dest [%d, %d] (%.0fm) - Encoding Active!",
            this.selectedLevel, this.selectedColumn + 1, rec.destX(), rec.destZ(), rec.distance());
        this.statusMessage = loadedMsg;

        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal(loadedMsg), true);
        }
    }

    private static TargetRecord findTargetForSlot(List<TargetRecord> history, int row, int col) {
        if (history == null) return null;
        for (TargetRecord tr : history) {
            if (tr.row() == row && tr.col() == col) {
                return tr;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.isHistoryOpen) {
            int panelW = 410;
            int panelH = Math.min(240, this.height - 24);
            int panelX = (this.width - panelW) / 2;
            int panelY = (this.height - panelH) / 2;
            int listY = panelY + 118;
            int listH = panelH - 124;

            if (mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8 && mouseY >= listY && mouseY <= listY + listH) {
                Optional<ClientSessionContext> sc = ClientSessionContext.resolve(this.minecraft);
                int totalRows = sc.map(c -> this.stateStore.get(c.environmentKey()).targetHistory().size()).orElse(0);
                int maxRows = Math.max(1, (listH - 20) / 16);
                int maxScroll = Math.max(0, totalRows - maxRows);

                if (verticalAmount > 0) {
                    this.historyScrollOffset = Math.max(0, this.historyScrollOffset - 1);
                } else if (verticalAmount < 0) {
                    this.historyScrollOffset = Math.min(maxScroll, this.historyScrollOffset + 1);
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isHistoryOpen) {
            int panelW = 410;
            int panelH = Math.min(240, this.height - 24);
            int panelX = (this.width - panelW) / 2;
            int panelY = (this.height - panelH) / 2;

            // Check Close button
            if (mouseX >= panelX + panelW - 55 && mouseX < panelX + panelW - 5 && mouseY >= panelY + 4 && mouseY < panelY + 18) {
                closeHistoryModal();
                return true;
            }

            // Check historyLabelBox click
            if (this.historyLabelBox != null && this.historyLabelBox.visible) {
                if (this.historyLabelBox.mouseClicked(mouseX, mouseY, button)) {
                    this.setFocused(this.historyLabelBox);
                    return true;
                }
            }

            Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
            if (sessionContext.isPresent()) {
                SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
                List<TargetRecord> history = state.targetHistory();

                int cellSize = 18;
                int gap = 2;
                int gridStartX = panelX + 22;
                int gridStartY = panelY + 40;

                // 1. Check clicking on 4x9 lamp grid in history modal -> ONLY inspects, NEVER immediately loads/syncs!
                for (int row = 3; row >= 0; --row) {
                    int displayRowIdx = 3 - row;
                    int cy = gridStartY + displayRowIdx * (cellSize + gap);

                    for (int col = 0; col < 9; col++) {
                        int cx = gridStartX + col * (cellSize + gap);
                        if (mouseX >= cx && mouseX < cx + cellSize && mouseY >= cy && mouseY < cy + cellSize) {
                            this.historyInspectedRow = row;
                            this.historyInspectedCol = col;
                            updateHistoryLabelBox();
                            return true;
                        }
                    }
                }

                // 2. Check clicking the Right Inspection Card button
                int infoX = panelX + 206;
                int infoY = panelY + 26;
                int infoW = panelW - 214;
                int btnX = infoX + 6;
                int btnY = infoY + 58;
                int btnW = infoW - 12;
                int btnH = 22;
                if (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH && this.historyInspectedRow >= 0) {
                    TargetRecord assigned = findTargetForSlot(history, this.historyInspectedRow, this.historyInspectedCol);
                    if (assigned != null) {
                        loadTargetRecord(assigned);
                        closeHistoryModal();
                        return true;
                    } else {
                        this.selectedLevel = this.historyInspectedRow;
                        this.selectedColumn = this.historyInspectedCol;
                        cachedSelectedLevel = this.historyInspectedRow;
                        cachedSelectedCol = this.historyInspectedCol;
                        closeHistoryModal();
                        this.onApplyHighlight();
                        String msg = String.format(java.util.Locale.ROOT, "§eSelected empty Lamp Slot: Level %d, Column %d", this.historyInspectedRow, this.historyInspectedCol + 1);
                        this.statusMessage = msg;
                        if (this.minecraft.player != null) {
                            this.minecraft.player.displayClientMessage(Component.literal(msg), true);
                        }
                        return true;
                    }
                }

                // 3. Check clicking rows in the History List below
                int listY = panelY + 118;
                int listH = panelH - 124;
                int rowY = listY + 16;
                int rowH = 16;
                int maxRows = Math.max(1, (listH - 20) / rowH);

                if (history != null && !history.isEmpty()) {
                    int totalRows = history.size();
                    int startIdx = this.historyScrollOffset;
                    int endIdx = Math.min(totalRows, startIdx + maxRows);

                    for (int i = startIdx; i < endIdx; i++) {
                        int curY = rowY + (i - startIdx) * rowH;
                        int delX = panelX + panelW - 24;
                        int delY = curY + 2;
                        int delW = 12;
                        int delH = 12;

                        // Delete button click [✕]
                        if (mouseX >= delX && mouseX < delX + delW && mouseY >= delY && mouseY < delY + delH) {
                            SessionState updated = state.removeTargetAt(i);
                            this.stateStore.set(sessionContext.get().environmentKey(), updated);
                            updateHistoryLabelBox();
                            return true;
                        }

                        // Row click -> inspects this target
                        if (mouseX >= panelX + 10 && mouseX < delX - 2 && mouseY >= curY && mouseY < curY + rowH) {
                            TargetRecord tr = history.get(i);
                            this.historyInspectedRow = tr.row();
                            this.historyInspectedCol = tr.col();
                            updateHistoryLabelBox();
                            return true;
                        }
                    }
                }
            }

            // Click outside the modal panel closes it safely
            if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
                closeHistoryModal();
                return true;
            }

            return true; // Consume event completely while modal is open
        }

        if (button == 0) {
            int rightX = getRightX();
            int panelY = getPanelY();

            int cellSize = 18;
            int gap = 2;
            int startX = rightX + 14;
            int matrixStartY = panelY + 22;

            for (int row = 3; row >= 0; --row) {
                int displayRowIdx = 3 - row;
                int curY = matrixStartY + displayRowIdx * (cellSize + gap);

                for (int col = 0; col < 9; ++col) {
                    int curX = startX + col * (cellSize + gap);
                    if (mouseX >= curX && mouseX < curX + cellSize && mouseY >= curY && mouseY < curY + cellSize) {
                        this.selectedLevel = row;
                        this.selectedColumn = col;
                        cachedSelectedLevel = row;
                        cachedSelectedCol = col;
                        this.onApplyHighlight();
                        return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (this.isHistoryOpen) {
            renderHistoryModal(graphics, mouseX, mouseY);
            return;
        }

        super.render(graphics, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int leftColX = getLeftColX();

        // Title at top
        graphics.drawString(this.font, Component.literal("§6§lPig Cannon Encoder"), leftColX, 8, 0xFFFFA000);

        // Input Labels
        int inputY = getPanelY() + 2;
        graphics.drawString(this.font, Component.literal("§7X:"), leftColX + 1, inputY + 4, 0xFFDDDDDD);
        graphics.drawString(this.font, Component.literal("§7Z:"), leftColX + 77, inputY + 4, 0xFFDDDDDD);

        // Pig Cannon Summary
        renderPigCannonSummary(graphics, leftColX, getTableY(), getTableWidth());

        // Right column: Redstone Lamps matrix (4 Rows × 9 Columns)
        int rightX = getRightX();
        int panelY = getPanelY();

        graphics.drawString(
            this.font,
            Component.translatable("cannoncalculator.gui.lamp_selector"),
            rightX + 14,
            panelY,
            0xFFFFA000
        );

        int cellSize = 18;
        int gap = 2;
        int startX = rightX + 14;
        int matrixStartY = panelY + 22;

        // Column headers (1 to 9)
        for (int col = 0; col < 9; ++col) {
            int curX = startX + col * (cellSize + gap);
            graphics.drawCenteredString(this.font, Component.literal(String.valueOf(col + 1)), curX + cellSize / 2, panelY + 11, 0xFFAAAAAA);
        }

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(SessionState.empty());
        List<TargetRecord> history = state.targetHistory();

        // Render rows: Level 3 at top down to Level 0 at bottom
        for (int row = 3; row >= 0; --row) {
            int displayRowIdx = 3 - row;
            int curY = matrixStartY + displayRowIdx * (cellSize + gap);

            // Row label (Level)
            graphics.drawString(this.font, Component.literal("L" + row), startX - 14, curY + 5, 0xFFCCCCCC);

            for (int col = 0; col < 9; ++col) {
                int curX = startX + col * (cellSize + gap);
                boolean isCurrentSlot = (this.selectedLevel == row && this.selectedColumn == col);
                boolean isHovered = (mouseX >= curX && mouseX < curX + cellSize && mouseY >= curY && mouseY < curY + cellSize);
                TargetRecord assigned = findTargetForSlot(history, row, col);
                boolean isProgrammed = (assigned != null);

                // Draw realistic in-game Redstone Lamp (only active/selected slot is lit, history does not light main menu lamps)
                drawRedstoneLamp(graphics, curX, curY, cellSize, isCurrentSlot, isCurrentSlot, isHovered);

                if (isHovered) {
                    String hoverTip = (assigned != null)
                        ? String.format(java.util.Locale.ROOT, "§6L%d C%d: §eX=%d, Z=%d §7(Click to switch slot)", row, col + 1, assigned.destX(), assigned.destZ())
                        : String.format(java.util.Locale.ROOT, "§6L%d C%d §7(Click to switch slot)", row, col + 1);
                    graphics.renderTooltip(this.font, Component.literal(hoverTip), mouseX, mouseY);
                }
            }
        }

        // Selected slot label below matrix
        int bottomInfoY = matrixStartY + 4 * (cellSize + gap) + 3;
        graphics.drawCenteredString(
            this.font,
            Component.literal("§bActive Slot: §fLevel " + this.selectedLevel + " (Row " + (this.selectedLevel + 1) + "), Column " + (this.selectedColumn + 1)),
            rightX + 14 + (9 * (cellSize + gap)) / 2,
            bottomInfoY,
            0xFF4DD0E1
        );

        // Compact material checklist with item icons
        int matY = bottomInfoY + 11;
        int matH = 20;
        renderMaterialIcons(graphics, rightX, matY, 192, matH, mouseX, mouseY);

        // Status message banner
        if (!this.statusMessage.isEmpty()) {
            int msgW = this.font.width(this.statusMessage);
            int msgY = this.height - 31;
            boolean isErr = this.statusMessage.contains("§c") || this.statusMessage.contains("✖") || this.statusMessage.contains("Failed");
            graphics.fill(centerX - msgW / 2 - 6, msgY - 2, centerX + msgW / 2 + 6, msgY + 11, 0xEE111111);
            drawOutline(graphics, centerX - msgW / 2 - 6, msgY - 2, msgW + 12, 13, isErr ? 0xFFFF5555 : 0xFF55FF55);
            graphics.drawCenteredString(this.font, Component.literal(this.statusMessage), centerX, msgY, 0xFFFFFFFF);
        }
    }

    private void renderHistoryModal(
        GuiGraphics graphics,
        int mouseX, int mouseY
    ) {
        int panelW = 410;
        int panelH = Math.min(240, this.height - 24);
        int panelX = (this.width - panelW) / 2;
        int panelY = (this.height - panelH) / 2;

        // Dark modal backdrop dimming
        graphics.fill(0, 0, this.width, this.height, 0x99000000);

        // Panel background and border
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xFA141414);
        drawOutline(graphics, panelX, panelY, panelW, panelH, 0xFFFFA000);

        // Title and Close button
        graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.history_title_full"), panelX + 8, panelY + 6, 0xFFFFA000);
        boolean closeHover = (mouseX >= panelX + panelW - 55 && mouseX < panelX + panelW - 5 && mouseY >= panelY + 4 && mouseY < panelY + 18);
        graphics.fill(panelX + panelW - 55, panelY + 4, panelX + panelW - 5, panelY + 18, closeHover ? 0xFFCC3333 : 0xFF442222);
        drawOutline(graphics, panelX + panelW - 55, panelY + 4, 50, 14, 0xFFDD5555);
        graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.button.close"), panelX + panelW - 30, panelY + 7, 0xFFFFFFFF);

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(SessionState.empty());
        List<TargetRecord> history = state.targetHistory();

        // Left Section: Interactive 4x9 Lamp Grid to check individual lamps
        graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.presets_title"), panelX + 8, panelY + 18, 0xFFFFD54F);

        int cellSize = 18;
        int gap = 2;
        int gridStartX = panelX + 22;
        int gridStartY = panelY + 44;

        // Column headers 1-9
        for (int col = 0; col < 9; col++) {
            int cx = gridStartX + col * (cellSize + gap);
            graphics.drawCenteredString(this.font, Component.literal(String.valueOf(col + 1)), cx + cellSize / 2, gridStartY - 11, 0xFFAAAAAA);
        }

        if (this.historyInspectedRow < 0) {
            this.historyInspectedRow = this.selectedLevel;
            this.historyInspectedCol = this.selectedColumn;
        }

        String hoverTooltip = null;
        for (int row = 3; row >= 0; --row) {
            int displayRowIdx = 3 - row;
            int cy = gridStartY + displayRowIdx * (cellSize + gap);

            // Row header
            graphics.drawString(this.font, Component.literal("L" + row), gridStartX - 16, cy + 5, 0xFFCCCCCC);

            for (int col = 0; col < 9; col++) {
                int cx = gridStartX + col * (cellSize + gap);
                TargetRecord assigned = findTargetForSlot(history, row, col);
                boolean isProgrammed = (assigned != null);
                boolean isCurrentSlot = (row == this.selectedLevel && col == this.selectedColumn);
                boolean isHovered = (mouseX >= cx && mouseX < cx + cellSize && mouseY >= cy && mouseY < cy + cellSize);
                boolean isInspected = (row == this.historyInspectedRow && col == this.historyInspectedCol);
                drawRedstoneLamp(graphics, cx, cy, cellSize, isProgrammed, isCurrentSlot, isHovered);
                if (isInspected) {
                    drawOutline(graphics, cx, cy, cellSize, cellSize, 0xFFFFD54F);
                }
                if (isHovered) {
                    hoverTooltip = (assigned != null)
                        ? String.format(java.util.Locale.ROOT, "§6L%d C%d: §eX=%d, Z=%d §7(Click to inspect)", row, col + 1, assigned.destX(), assigned.destZ())
                        : String.format(java.util.Locale.ROOT, "§6L%d C%d: §7Empty (Click to inspect)", row, col + 1);
                }
            }
        }

        // Right Section: Inspection Card for the hovered / selected lamp
        int infoX = panelX + 206;
        int infoY = panelY + 26;
        int infoW = panelW - 214;
        int infoH = 92;
        graphics.fill(infoX, infoY, infoX + infoW, infoY + infoH, 0xCC1A1A1A);
        drawOutline(graphics, infoX, infoY, infoW, infoH, 0xFF444444);

        int insRow = Math.max(0, Math.min(3, this.historyInspectedRow));
        int insCol = Math.max(0, Math.min(8, this.historyInspectedCol));
        TargetRecord inspectedTarget = findTargetForSlot(history, insRow, insCol);
        boolean isCurrentInspected = (insRow == this.selectedLevel && insCol == this.selectedColumn);

        String activeBadge = isCurrentInspected ? " §b★ Active" : "";
        graphics.drawString(this.font, Component.literal("§6Slot: §fL" + insRow + " C" + (insCol + 1) + activeBadge), infoX + 6, infoY + 6, 0xFFFFA000);

        if (inspectedTarget != null) {
            graphics.drawString(this.font, Component.literal("§7Mode: §d[Pig Cannon]"), infoX + 6, infoY + 17, 0xFFDDDDDD);
            graphics.drawString(this.font, Component.literal(String.format(java.util.Locale.ROOT, "§aDest: §eX=%d, Z=%d §7(%.0fm)", inspectedTarget.destX(), inspectedTarget.destZ(), inspectedTarget.distance())), infoX + 6, infoY + 28, 0xFF55FF55);

            // Editable custom label
            graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.slot_name"), infoX + 6, infoY + 42, 0xFFFFA000);
            if (this.historyLabelBox != null) {
                int labelX = infoX + 40;
                int labelY = infoY + 39;
                int labelW = infoW - 46;
                this.historyLabelBox.setPosition(labelX, labelY);
                this.historyLabelBox.setWidth(labelW);
                this.historyLabelBox.visible = true;
                this.historyLabelBox.render(graphics, mouseX, mouseY, 0);
            }

            int btnX = infoX + 6;
            int btnY = infoY + 58;
            int btnW = infoW - 12;
            int btnH = 22;
            boolean btnHover = (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF2E7D32 : 0xFF1B5E20);
            drawOutline(graphics, btnX, btnY, btnW, btnH, btnHover ? 0xFF81C784 : 0xFF4CAF50);
            Component btnComp = isCurrentInspected ? Component.translatable("cannoncalculator.gui.slot_active_close") : Component.translatable("cannoncalculator.gui.slot_apply");
            graphics.drawCenteredString(this.font, btnComp, btnX + btnW / 2, btnY + 7, 0xFFFFFFFF);
        } else {
            if (this.historyLabelBox != null) {
                this.historyLabelBox.visible = false;
            }
            graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.status_empty"), infoX + 6, infoY + 20, 0xFF888888);
            graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.no_past_target"), infoX + 6, infoY + 32, 0xFF888888);
            graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.this_lamp_world"), infoX + 6, infoY + 44, 0xFF888888);

            int btnX = infoX + 6;
            int btnY = infoY + 60;
            int btnW = infoW - 12;
            int btnH = 20;
            boolean btnHover = (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF0277BD : 0xFF01579B);
            drawOutline(graphics, btnX, btnY, btnW, btnH, btnHover ? 0xFF4FC3F7 : 0xFF29B6F6);
            Component btnEmptyComp = isCurrentInspected ? Component.translatable("cannoncalculator.gui.slot_selected_close") : Component.translatable("cannoncalculator.gui.slot_select_close");
            graphics.drawCenteredString(this.font, btnEmptyComp, btnX + btnW / 2, btnY + 6, 0xFFFFFFFF);
        }

        // Bottom Section: History List of all past targets
        int listY = panelY + 122;
        int listH = panelH - 128;
        graphics.fill(panelX + 8, listY, panelX + panelW - 8, listY + listH, 0xCC111111);
        drawOutline(graphics, panelX + 8, listY, panelW - 16, listH, 0xFF333333);

        graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.history_title"), panelX + 12, listY + 4, 0xFFFFA000);

        if (history == null || history.isEmpty()) {
            graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.history_empty"), panelX + panelW / 2, listY + listH / 2 - 4, 0xFF888888);
            if (hoverTooltip != null) {
                graphics.renderTooltip(this.font, Component.literal(hoverTooltip), mouseX, mouseY);
            }
            return;
        }

        int rowY = listY + 16;
        int rowH = 16;
        int maxRows = Math.max(1, (listH - 20) / rowH);
        int totalRows = history.size();
        int maxScroll = Math.max(0, totalRows - maxRows);
        if (this.historyScrollOffset > maxScroll) this.historyScrollOffset = maxScroll;
        if (this.historyScrollOffset < 0) this.historyScrollOffset = 0;

        int startIdx = this.historyScrollOffset;
        int endIdx = Math.min(totalRows, startIdx + maxRows);
        String delTooltip = null;

        for (int i = startIdx; i < endIdx; i++) {
            TargetRecord rec = history.get(i);
            int curY = rowY + (i - startIdx) * rowH;
            int delX = panelX + panelW - 24;
            int delY = curY + 2;
            int delW = 12;
            int delH = 12;
            boolean isDeleteHover = (mouseX >= delX && mouseX < delX + delW && mouseY >= delY && mouseY < delY + delH);
            boolean isRowHover = (mouseX >= panelX + 10 && mouseX < delX - 2 && mouseY >= curY && mouseY < curY + rowH);
            boolean isRowInspected = (rec.row() == this.historyInspectedRow && rec.col() == this.historyInspectedCol);

            if (isRowHover || isRowInspected) {
                graphics.fill(panelX + 10, curY, panelX + panelW - 14, curY + rowH, isRowInspected ? 0x4400E5FF : 0x44FFA000);
                drawOutline(graphics, panelX + 10, curY, panelW - 24, rowH, isRowInspected ? 0xFF00E5FF : 0xFFFFD54F);
            } else if (i % 2 == 1) {
                graphics.fill(panelX + 10, curY, panelX + panelW - 14, curY + rowH, 0x22222222);
            }

            String line;
            if (rec.label() != null && !rec.label().isBlank()) {
                line = String.format(java.util.Locale.ROOT, "§d[Pig] §e\"%s\" §7(L%d C%d -> X=%d, Z=%d)", rec.label(), rec.row(), rec.col() + 1, rec.destX(), rec.destZ());
            } else {
                line = String.format(java.util.Locale.ROOT, "§d[Pig] §eL%d C%d §7-> §fX=%d, Z=%d §7(%.0fm)", rec.row(), rec.col() + 1, rec.destX(), rec.destZ(), rec.distance());
            }
            graphics.drawString(this.font, Component.literal(line), panelX + 14, curY + 4, 0xFFFFFFFF);

            // Delete button [✕]
            graphics.fill(delX, delY, delX + delW, delY + delH, isDeleteHover ? 0xFFD32F2F : 0xFF552222);
            drawOutline(graphics, delX, delY, delW, delH, isDeleteHover ? 0xFFFF5252 : 0xFF883333);
            graphics.drawCenteredString(this.font, Component.literal("✕"), delX + delW / 2, delY + 2, isDeleteHover ? 0xFFFFFFFF : 0xFFFFAAAA);
            if (isDeleteHover) {
                delTooltip = "§cDelete target from history";
            }
        }

        // Scrollbar if history is larger than visible space
        if (totalRows > maxRows) {
            int scrollbarX = panelX + panelW - 14;
            int scrollbarY = listY + 16;
            int scrollbarH = listH - 20;
            graphics.fill(scrollbarX, scrollbarY, scrollbarX + 4, scrollbarY + scrollbarH, 0xFF1A1A1A);
            int thumbH = Math.max(8, (maxRows * scrollbarH) / totalRows);
            int thumbY = scrollbarY + (this.historyScrollOffset * (scrollbarH - thumbH)) / maxScroll;
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbH, 0xFFAAAAAA);
        }

        if (delTooltip != null) {
            graphics.renderTooltip(this.font, Component.literal(delTooltip), mouseX, mouseY);
        } else if (hoverTooltip != null) {
            graphics.renderTooltip(this.font, Component.literal(hoverTooltip), mouseX, mouseY);
        }
    }

    private void renderPigCannonSummary(GuiGraphics graphics, int x, int y, int width) {
        int maxAllowedH = Math.max(40, (this.height - 24) - y);
        int h = Math.min(76, maxAllowedH);
        graphics.fill(x, y, x + width, y + h, 0xCC111111);
        drawOutline(graphics, x, y, width, h, 0xFF333333);

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(SessionState.empty());

        graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.pig_encoding"), x + 4, y + 4, 0xFFFFA000);
        if (state.lastCalculation() != null && h >= 68) {
            CalculationResult calc = state.lastCalculation();
            int totalTicks = calc.straightAccelFirst() + calc.straightAccelSecond() + calc.diagonalAccelThird() + calc.diagonalAccelFourth() + calc.directionCode();
            double minutes = totalTicks / 1200.0;

            graphics.drawString(this.font, Component.literal("§7Dir: §e" + calc.directionCode() + "   §6Time: §e" + totalTicks + "t §7(~§f" + String.format(java.util.Locale.ROOT, "%.1fm", minutes) + "§7)"), x + 4, y + 16, 0xFFDDDDDD);
            graphics.drawString(this.font, Component.literal("§71st: §b" + calc.straightAccelFirst() + "     §72nd: §a" + calc.straightAccelSecond()), x + 4, y + 27, 0xFFDDDDDD);
            graphics.drawString(this.font, Component.literal("§73rd: §e" + calc.diagonalAccelThird() + " §74th: §6" + calc.diagonalAccelFourth()), x + 4, y + 38, 0xFFDDDDDD);
            graphics.drawString(this.font, Component.literal("§7Anchor: §f" + calc.initX() + ", " + calc.initZ() + "  §7Dest: §f" + calc.destX() + ", " + calc.destZ()), x + 4, y + 49, 0xFFAAAAAA);
            graphics.drawString(this.font, Component.literal("§aLanding: §e[" + calc.landingX() + ", " + calc.landingZ() + "]"), x + 4, y + 60, 0xFF55FF55);
        } else if (state.lastCalculation() != null) {
            CalculationResult calc = state.lastCalculation();
            int totalTicks = calc.straightAccelFirst() + calc.straightAccelSecond() + calc.diagonalAccelThird() + calc.diagonalAccelFourth() + calc.directionCode();
            double minutes = totalTicks / 1200.0;
            graphics.drawString(this.font, Component.literal("§7Dir: §e" + calc.directionCode() + " §7| Land: §a[" + calc.landingX() + ", " + calc.landingZ() + "]"), x + 4, y + 16, 0xFFDDDDDD);
            graphics.drawString(this.font, Component.literal("§6Time: §e" + totalTicks + "t §7(~" + String.format(java.util.Locale.ROOT, "%.1fm", minutes) + ")"), x + 4, y + 27, 0xFFDDDDDD);
        } else {
            graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.none"), x + width / 2, y + h / 2 - 4, 0xFF888888);
        }
    }

    private void renderMaterialIcons(
        GuiGraphics graphics,
        int x, int y, int width, int height,
        int mouseX, int mouseY
    ) {
        graphics.fill(x, y, x + width, y + height, 0xCC111111);
        boolean isHoveredBox = (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height);
        drawOutline(graphics, x, y, width, height, isHoveredBox ? 0xFFFFA000 : 0xFF333333);

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        List<HighlightedBlock> blocks = (sessionContext.isPresent() && this.highlightManager != null)
            ? this.highlightManager.visibleHighlights(sessionContext.get())
            : List.of();

        List<MaterialChecklist.Entry> entries = MaterialChecklist.compute(this.minecraft.level, blocks, this.minecraft.player);

        graphics.drawString(this.font, Component.literal("§6📦"), x + 4, y + 6, 0xFFFFA000);

        boolean hasPlaceable = blocks.stream().anyMatch(b -> dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager.isPlaceableHighlight(b.type()));
        if (entries.isEmpty()) {
            String msg = !hasPlaceable ? "§7None" : "§a✔ All slice blocks placed!";
            graphics.drawString(this.font, Component.literal(msg), x + 18, y + 6, !hasPlaceable ? 0xFF888888 : 0xFF55FF55);
            return;
        }

        int itemX = x + 18;
        int itemY = y + 2;
        int spacing = 26;

        for (int i = 0; i < entries.size(); i++) {
            if (itemX + 20 > x + width) break;
            MaterialChecklist.Entry entry = entries.get(i);
            ItemStack stack = new ItemStack(entry.item());

            // Draw real item icon
            graphics.renderItem(stack, itemX, itemY);

            // Draw required count decoration in bottom right corner
            String countText = String.valueOf(entry.required());
            graphics.renderItemDecorations(this.font, stack, itemX, itemY, countText);

            // Red border if player doesn't have enough in inventory
            if (!entry.hasEnough()) {
                drawOutline(graphics, itemX - 1, itemY - 1, 18, 18, 0xFFFF4444);
            }

            // Interactive tooltip on hover
            if (mouseX >= itemX && mouseX < itemX + 22 && mouseY >= itemY && mouseY < itemY + 18) {
                String status = entry.hasEnough()
                    ? "§aRequired: " + entry.required() + " | In Bag: " + entry.available() + " ✔"
                    : "§cRequired: " + entry.required() + " | In Bag: " + entry.available() + " (Need " + (entry.required() - entry.available()) + " more) ✘";
                graphics.renderTooltip(this.font, Component.literal("§e" + entry.name() + "\n" + status), mouseX, mouseY);
            }

            itemX += spacing;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // Enter or Numpad Enter
            if ((this.destXBox != null && this.destXBox.isFocused())
                || (this.destZBox != null && this.destZBox.isFocused())) {
                this.onCalculate();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        if (this.isHistoryOpen) {
            closeHistoryModal();
            return;
        }
        if (this.destXBox != null) cachedDestX = this.destXBox.getValue();
        if (this.destZBox != null) cachedDestZ = this.destZBox.getValue();
        cachedSelectedLevel = this.selectedLevel;
        cachedSelectedCol = this.selectedColumn;
        super.onClose();
    }

    private void drawRedstoneLamp(
        GuiGraphics graphics,
        int x, int y, int size,
        boolean lit, boolean isCurrentSlot, boolean hovered
    ) {
        ResourceLocation texture = lit ? LAMP_ON_TEXTURE : LAMP_OFF_TEXTURE;
        // Draw 16x16 texture centered inside the 18x18 cell (offset by 1px from the border)
        graphics.blit(texture, x + 1, y + 1, 0, 0, 16, 16, 16, 16);

        if (lit) {
            // Warm golden glow fill and border for programmed / lit lamp
            graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0x22FFA000);
            drawOutline(graphics, x, y, size, size, 0xFFFFB300);
        } else {
            // Dark border around unlit lamp
            drawOutline(graphics, x, y, size, size, 0xFF2A1A10);
        }

        if (isCurrentSlot) {
            // Distinct cyan indicator marking the currently active cannon slot!
            drawOutline(graphics, x, y, size, size, 0xFF00E5FF);
            graphics.fill(x + 2, y + 2, x + 5, y + 5, 0xFF00E5FF);
        }

        if (hovered) {
            // Highlight frame when hovered
            drawOutline(graphics, x - 1, y - 1, size + 2, size + 2, 0xFFFFFFFF);
        }
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
