package dev.selestor1462.cannonencoder.client.gui;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.PigCannonCalculator;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculationResult;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculator;
import dev.selestor1462.cannonencoder.calc.ftl.FtlConstants;
import dev.selestor1462.cannonencoder.calc.ftl.FtlEncodingData;
import dev.selestor1462.cannonencoder.calc.ftl.Vec3d;
import dev.selestor1462.cannonencoder.client.ClientSessionContext;
import dev.selestor1462.cannonencoder.client.HighlightManager;
import dev.selestor1462.cannonencoder.client.catcher.CatcherPlacementManager;
import dev.selestor1462.cannonencoder.client.validation.EncodingValidator;
import dev.selestor1462.cannonencoder.client.validation.ValidationReport;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.FtlPearlCannonTemplate;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import dev.selestor1462.cannonencoder.text.ResultMessageFormatter;
import dev.selestor1462.cannonencoder.client.AutoAnchorDetector;
import dev.selestor1462.cannonencoder.client.MaterialChecklist;
import dev.selestor1462.cannonencoder.client.WorldHighlightRenderer;
import dev.selestor1462.cannonencoder.state.TargetRecord;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public final class PigCannonScreen extends Screen {
    private static final ResourceLocation LAMP_OFF_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_lamp.png");
    private static final ResourceLocation LAMP_ON_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/redstone_lamp_on.png");

    private static final PigCannonCalculator PIG_CALCULATOR = new PigCannonCalculator();
    private static final FtlCalculator FTL_CALCULATOR = new FtlCalculator();

    private final ClientStateStore stateStore;
    private final HighlightManager highlightManager;
    private final dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager easyPlaceManager;

    private CannonMode currentMode = CannonMode.FTL_PEARL_CANNON;

    // Cannon size: 6688 (Full), 3344 (Half), 1672 (1/4)
    private int cannonSize = 6688;

    // Controls
    private Button modeBtn;
    private Button cannonSizeBtn;
    private EditBox destXBox;
    private EditBox destZBox;
    private EditBox maxTntBox;
    private EditBox maxTicksBox;
    private EditBox maxDistBox;

    public enum FtlSortMode {
        DESTINATION("Dest"),
        TNT("TNT"),
        TICKS("Ticks");

        private final String displayName;

        FtlSortMode(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return this.displayName;
        }
    }

    // Solution candidates list & scroll offset
    private List<FtlCalculationResult> ftlResults = new ArrayList<>();
    private int currentResultIndex = 0;
    private int tableScrollOffset = 0;

    // Sorting
    private FtlSortMode sortMode = FtlSortMode.DESTINATION;
    private boolean sortAscending = true;
    private Button sortDestBtn;
    private Button sortTntBtn;
    private Button sortTicksBtn;

    // Action buttons
    private Button calculateBtn;
    private Button useMyPosBtn;
    private Button historyBtn;
    private boolean isHistoryOpen = false;
    private int historyScrollOffset = 0;
    private int historyInspectedRow = -1;
    private int historyInspectedCol = -1;
    private EditBox historyLabelBox;
    private boolean updatingLabelBox = false;

    private Button setAnchorBtn;
    private Button tpCatcherBtn;
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
    private static List<FtlCalculationResult> cachedFtlResults = new ArrayList<>();
    private static int cachedResultIndex = -1;
    private static int cachedTableScroll = 0;
    private static String cachedDestX = "";
    private static String cachedDestZ = "";
    private static String cachedMaxTnt = "6688";
    private static String cachedMaxTicks = String.valueOf(FtlConstants.DEFAULT_MAX_TICKS);
    private static String cachedMaxDist = String.valueOf((int) FtlConstants.DEFAULT_MAX_DISTANCE);
    private static int cachedCannonSize = 6688;
    private static CannonMode cachedMode = null;
    private static int cachedSelectedLevel = -1;
    private static int cachedSelectedCol = -1;
    private static FtlSortMode cachedSortMode = FtlSortMode.DESTINATION;
    private static boolean cachedSortAscending = true;

    public PigCannonScreen(
        ClientStateStore stateStore,
        HighlightManager highlightManager
    ) {
        this(stateStore, highlightManager, null);
    }

    public PigCannonScreen(
        ClientStateStore stateStore,
        HighlightManager highlightManager,
        dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager easyPlaceManager
    ) {
        super(Component.translatable("cannoncalculator.gui.title"));
        this.stateStore = stateStore;
        this.highlightManager = highlightManager;
        this.easyPlaceManager = easyPlaceManager;
        this.sortMode = cachedSortMode;
        this.sortAscending = cachedSortAscending;

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(Minecraft.getInstance());
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
            this.currentMode = (cachedMode != null) ? cachedMode : ((state.cannonMode() != null) ? state.cannonMode() : CannonMode.FTL_PEARL_CANNON);
            if (state.cannonSize() > 0) {
                this.cannonSize = state.cannonSize();
                cachedCannonSize = this.cannonSize;
            } else {
                this.cannonSize = cachedCannonSize;
            }

            if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
                this.selectedLevel = (cachedSelectedLevel >= 0) ? cachedSelectedLevel : Math.max(0, Math.min(3, state.ftlSelectedRow()));
                this.selectedColumn = (cachedSelectedCol >= 0) ? cachedSelectedCol : Math.max(0, Math.min(8, state.ftlSelectedCol()));
                if (!cachedFtlResults.isEmpty()) {
                    this.ftlResults = new ArrayList<>(cachedFtlResults);
                    this.currentResultIndex = Math.min(cachedResultIndex, this.ftlResults.size() - 1);
                    this.tableScrollOffset = cachedTableScroll;
                } else {
                    this.ftlResults = new ArrayList<>();
                    this.currentResultIndex = -1;
                }
            } else {
                this.selectedLevel = (cachedSelectedLevel >= 0) ? cachedSelectedLevel : Math.max(0, Math.min(3, state.selectedRow()));
                this.selectedColumn = (cachedSelectedCol >= 0) ? cachedSelectedCol : Math.max(0, Math.min(8, state.selectedCol()));
            }
            if (this.easyPlaceManager != null) {
                this.easyPlaceManager.setEnabled(state.easyPlaceEnabled());
            }


        }
    }

    private int getTableY() {
        return (this.currentMode == CannonMode.FTL_PEARL_CANNON) ? 106 : 70;
    }

    private int getTableHeight() {
        return Math.max(40, (this.height - 24) - getTableY());
    }

    private int getTableWidth() {
        return 194;
    }

    private int getLeftColX() {
        int totalWidth = 404;
        if (this.width >= totalWidth + 12) {
            return (this.width - totalWidth) / 2;
        }
        return Math.max(2, (this.width - totalWidth) / 2);
    }

    private int getRightX() {
        return getLeftColX() + 204;
    }

    private int getPanelY() {
        return 28;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = this.width / 2;

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(SessionState.empty());
        if (state.cannonSize() > 0) {
            this.cannonSize = state.cannonSize();
            cachedCannonSize = this.cannonSize;
        }

        // Top bar: Mode Toggle and Cannon Size (aligned with left column)
        int leftColX = getLeftColX();
        int topY = 4;

        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            int modeBtnWidth = 104;
            int sizeBtnWidth = 86;
            this.modeBtn = Button.builder(
                Component.translatable("cannoncalculator.gui.mode", this.currentMode.displayName()),
                btn -> this.onToggleMode()
            ).bounds(leftColX, topY, modeBtnWidth, 18).build();
            this.modeBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cannoncalculator.gui.mode.tooltip")
            ));
            this.addRenderableWidget(this.modeBtn);

            this.cannonSizeBtn = Button.builder(
                Component.translatable("cannoncalculator.gui.size", getCannonSizeLabel(this.cannonSize)),
                btn -> this.onCycleCannonSize()
            ).bounds(leftColX + modeBtnWidth + 4, topY, sizeBtnWidth, 18).build();
            this.cannonSizeBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cannoncalculator.gui.size.tooltip")
            ));
            this.cannonSizeBtn.visible = true;
            this.addRenderableWidget(this.cannonSizeBtn);
        } else {
            int modeBtnWidth = 194;
            this.modeBtn = Button.builder(
                Component.translatable("cannoncalculator.gui.mode", this.currentMode.displayName()),
                btn -> this.onToggleMode()
            ).bounds(leftColX, topY, modeBtnWidth, 18).build();
            this.modeBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
                Component.translatable("cannoncalculator.gui.mode.tooltip")
            ));
            this.addRenderableWidget(this.modeBtn);

            this.cannonSizeBtn = Button.builder(
                Component.translatable("cannoncalculator.gui.size", getCannonSizeLabel(this.cannonSize)),
                btn -> this.onCycleCannonSize()
            ).bounds(leftColX, topY, 0, 0).build();
            this.cannonSizeBtn.visible = false;
            this.addRenderableWidget(this.cannonSizeBtn);
        }

        int rightX = getRightX();
        this.historyBtn = Button.builder(Component.translatable("cannoncalculator.gui.history"), btn -> this.onToggleHistory())
            .bounds(rightX + 104, 6, 88, 18)
            .build();
        this.historyBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cannoncalculator.gui.history.tooltip")
        ));
        this.addRenderableWidget(this.historyBtn);

        this.historyLabelBox = new EditBox(this.font, 0, 0, 100, 14, Component.translatable("cannoncalculator.gui.target_label"));
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
        this.addRenderableWidget(this.historyLabelBox);

        // Positioning for left input column
        int inputY = getPanelY() + 2;

        // Destination Inputs
        int destBoxWidth = 60;
        this.destXBox = new EditBox(this.font, leftColX + 13, inputY, destBoxWidth, 16, Component.translatable("cannoncalculator.gui.dest_x"));
        this.destXBox.setValue(cachedDestX);
        this.addRenderableWidget(this.destXBox);

        this.destZBox = new EditBox(this.font, leftColX + 89, inputY, destBoxWidth, 16, Component.translatable("cannoncalculator.gui.dest_z"));
        this.destZBox.setValue(cachedDestZ);
        this.addRenderableWidget(this.destZBox);

        this.useMyPosBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.pos"), btn -> this.onUseMyPos())
            .bounds(leftColX + 153, inputY, 41, 16)
            .build();
        this.useMyPosBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cannoncalculator.gui.button.pos.tooltip")
        ));
        this.addRenderableWidget(this.useMyPosBtn);


        // FTL Restrictions / Filters (Max TNT, Max Ticks, Max Dist)
        int filterY = inputY + 19;
        this.maxTntBox = new EditBox(this.font, leftColX + 26, filterY, 44, 16, Component.translatable("cannoncalculator.gui.max_tnt"));
        this.maxTntBox.setValue(String.valueOf(this.cannonSize));
        this.maxTntBox.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.maxTntBox);

        this.maxTicksBox = new EditBox(this.font, leftColX + 85, filterY, 36, 16, Component.translatable("cannoncalculator.gui.max_ticks"));
        this.maxTicksBox.setValue(cachedMaxTicks);
        this.maxTicksBox.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.maxTicksBox);

        this.maxDistBox = new EditBox(this.font, leftColX + 137, filterY, 36, 16, Component.translatable("cannoncalculator.gui.max_dist"));
        this.maxDistBox.setValue(cachedMaxDist);
        this.maxDistBox.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.maxDistBox);

        // Action buttons row: Set Anchor & Calculate
        int actionY = (this.currentMode == CannonMode.FTL_PEARL_CANNON) ? (filterY + 19) : (inputY + 19);
        this.setAnchorBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.auto_anchor"), btn -> this.onSetAnchorHere())
            .bounds(leftColX, actionY, 94, 18)
            .build();
        AnchorState initialAnchor = state.effectiveAnchor(this.currentMode);
        updateAnchorTooltip(initialAnchor);
        this.addRenderableWidget(this.setAnchorBtn);

        this.calculateBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.calculate"), btn -> this.onCalculate())
            .bounds(leftColX + 98, actionY, 96, 18)
            .build();
        this.addRenderableWidget(this.calculateBtn);

        // Sort buttons row (Dest, TNT, Ticks) above FTL candidates table
        int sortY = actionY + 20;
        int sortBtnW = 64;
        this.sortDestBtn = Button.builder(Component.literal("Dest"), btn -> this.setSortMode(FtlSortMode.DESTINATION))
            .bounds(leftColX, sortY, sortBtnW, 16)
            .build();
        this.sortDestBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cannoncalculator.gui.sort.dest.tooltip")
        ));
        this.sortDestBtn.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.sortDestBtn);

        this.sortTntBtn = Button.builder(Component.literal("TNT"), btn -> this.setSortMode(FtlSortMode.TNT))
            .bounds(leftColX + sortBtnW + 1, sortY, sortBtnW, 16)
            .build();
        this.sortTntBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cannoncalculator.gui.sort.tnt.tooltip")
        ));
        this.sortTntBtn.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.sortTntBtn);

        this.sortTicksBtn = Button.builder(Component.literal("Ticks"), btn -> this.setSortMode(FtlSortMode.TICKS))
            .bounds(leftColX + (sortBtnW * 2) + 2, sortY, sortBtnW, 16)
            .build();
        this.sortTicksBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cannoncalculator.gui.sort.ticks.tooltip")
        ));
        this.sortTicksBtn.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.sortTicksBtn);

        this.updateSortButtonLabels();

        // Bottom action buttons: Place Catcher, TP Catcher, Check Encoding, EasyPlace, Rails Toggle, Glow Toggle, Clear, Done
        int bottomY = this.height - 19;

        this.tpCatcherBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.tp"), btn -> this.onTpCatcher())
            .bounds(0, 0, 36, 16)
            .build();
        this.tpCatcherBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable("cannoncalculator.gui.button.tp.tooltip")
        ));
        this.tpCatcherBtn.visible = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        this.addRenderableWidget(this.tpCatcherBtn);

        this.checkEncodingBtn = Button.builder(Component.translatable("cannoncalculator.gui.button.check"), btn -> this.onCheckEncoding())
            .bounds(0, 0, 42, 16)
            .build();
        this.addRenderableWidget(this.checkEncodingBtn);

        boolean easyEnabled = (this.easyPlaceManager != null && this.easyPlaceManager.isEnabled());
        this.easyPlaceBtn = Button.builder(
            Component.translatable(easyEnabled ? "cannoncalculator.gui.easyplace_btn_on" : "cannoncalculator.gui.easyplace_btn_off"),
            btn -> this.onToggleEasyPlace()
        ).bounds(0, 0, 58, 16).build();
        this.easyPlaceBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable(easyEnabled ? "cannoncalculator.gui.easyplace_active" : "cannoncalculator.gui.easyplace_inactive")
        ));
        this.addRenderableWidget(this.easyPlaceBtn);

        boolean seqRails = (this.highlightManager != null && this.highlightManager.isSequentialRails());
        this.railsToggleBtn = Button.builder(
            Component.translatable(seqRails ? "cannoncalculator.gui.rails_step" : "cannoncalculator.gui.rails_all"),
            btn -> this.onToggleSequentialRails()
        ).bounds(0, 0, 42, 16).build();
        this.railsToggleBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
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
        this.glowToggleBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.translatable("cannoncalculator.gui.glow_tooltip")));
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
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            int totalWidth = 36 + 42 + easyBtnWidth + 42 + 38 + 36 + 38 + (6 * btnGap);
            int curX = centerX - totalWidth / 2;
            this.tpCatcherBtn.setPosition(curX, bottomY); curX += 36 + btnGap;
            this.checkEncodingBtn.setPosition(curX, bottomY); curX += 42 + btnGap;
            this.easyPlaceBtn.setPosition(curX, bottomY); curX += easyBtnWidth + btnGap;
            this.railsToggleBtn.setPosition(curX, bottomY); curX += 42 + btnGap;
            this.glowToggleBtn.setPosition(curX, bottomY); curX += 38 + btnGap;
            this.clearHighlightBtn.setPosition(curX, bottomY); curX += 36 + btnGap;
            this.doneBtn.setPosition(curX, bottomY);
        } else {
            int totalWidth = 42 + easyBtnWidth + 42 + 38 + 36 + 38 + (5 * btnGap);
            int curX = centerX - totalWidth / 2;
            this.checkEncodingBtn.setPosition(curX, bottomY); curX += 42 + btnGap;
            this.easyPlaceBtn.setPosition(curX, bottomY); curX += easyBtnWidth + btnGap;
            this.railsToggleBtn.setPosition(curX, bottomY); curX += 42 + btnGap;
            this.glowToggleBtn.setPosition(curX, bottomY); curX += 38 + btnGap;
            this.clearHighlightBtn.setPosition(curX, bottomY); curX += 36 + btnGap;
            this.doneBtn.setPosition(curX, bottomY);
        }

        if (this.currentMode == CannonMode.FTL_PEARL_CANNON && !this.ftlResults.isEmpty() && this.currentResultIndex >= 0 && this.currentResultIndex < this.ftlResults.size()) {
            FtlCalculationResult res = this.ftlResults.get(this.currentResultIndex);
            this.statusMessage = String.format("§aSelected #%d/%d: Dist %.1fm | %d Ticks | TNT: %d | Catcher: %s",
                this.currentResultIndex + 1, this.ftlResults.size(), res.distance(), res.ticks(), res.totalTnt(), res.catcherPos().asInt());
        }

        updateWidgetActiveStates();
    }

    private Component getCannonSizeLabel(int size) {
        return switch (size) {
            case 3344 -> Component.translatable("cannoncalculator.gui.size.half");
            case 1672 -> Component.translatable("cannoncalculator.gui.size.quarter");
            default -> Component.translatable("cannoncalculator.gui.size.full");
        };
    }

    private void onCycleCannonSize() {
        if (this.cannonSize == 6688) {
            this.cannonSize = 3344;
        } else if (this.cannonSize == 3344) {
            this.cannonSize = 1672;
        } else {
            this.cannonSize = 6688;
        }
        this.cannonSizeBtn.setMessage(Component.translatable("cannoncalculator.gui.size", getCannonSizeLabel(this.cannonSize)));
        cachedCannonSize = this.cannonSize;
        if (this.maxTntBox != null) {
            this.maxTntBox.setValue(String.valueOf(this.cannonSize));
            cachedMaxTnt = String.valueOf(this.cannonSize);
        }
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withCannonSize(this.cannonSize);
            this.stateStore.set(sessionContext.get().environmentKey(), state);
        }
        if (!this.ftlResults.isEmpty() && this.destXBox != null && !this.destXBox.getValue().isBlank() && this.destZBox != null && !this.destZBox.getValue().isBlank()) {
            this.onCalculate();
        } else {
            this.onApplyHighlight();
        }
    }

    private void onToggleMode() {
        switchMode((this.currentMode == CannonMode.PIG_CANNON) ? CannonMode.FTL_PEARL_CANNON : CannonMode.PIG_CANNON);
    }

    public void switchMode(CannonMode newMode) {
        if (newMode == null) return;
        this.currentMode = newMode;
        cachedMode = this.currentMode;
        if (this.destXBox != null) cachedDestX = this.destXBox.getValue();
        if (this.destZBox != null) cachedDestZ = this.destZBox.getValue();

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withMode(this.currentMode);
            this.stateStore.set(sessionContext.get().environmentKey(), state);

            boolean isFtl = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
            if (isFtl) {
                this.selectedLevel = Math.max(0, Math.min(3, state.ftlSelectedRow()));
                this.selectedColumn = Math.max(0, Math.min(8, state.ftlSelectedCol()));
                if (!cachedFtlResults.isEmpty()) {
                    this.ftlResults = new ArrayList<>(cachedFtlResults);
                    this.currentResultIndex = Math.min(cachedResultIndex, this.ftlResults.size() - 1);
                    this.tableScrollOffset = cachedTableScroll;
                } else {
                    this.ftlResults = new ArrayList<>();
                    this.currentResultIndex = -1;
                }
            } else {
                this.selectedLevel = Math.max(0, Math.min(3, state.selectedRow()));
                this.selectedColumn = Math.max(0, Math.min(8, state.selectedCol()));
            }
        }
        this.clearWidgets();
        this.highlightManager.clear();
        this.init();
        this.onApplyHighlight();
    }

    private void onUseMyPos() {
        if (this.minecraft.player == null) return;
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            double centerX = Math.floor(this.minecraft.player.getX()) + 0.5;
            double centerZ = Math.floor(this.minecraft.player.getZ()) + 0.5;
            this.destXBox.setValue(String.format(java.util.Locale.ROOT, "%.1f", centerX));
            this.destZBox.setValue(String.format(java.util.Locale.ROOT, "%.1f", centerZ));
            this.statusMessage = "§aTarget set to block center (X.5, Z.5).";
        } else {
            this.destXBox.setValue(String.valueOf((int) Math.floor(this.minecraft.player.getX())));
            this.destZBox.setValue(String.valueOf((int) Math.floor(this.minecraft.player.getZ())));
            this.statusMessage = "§aTarget set to your current coordinates.";
        }
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
        this.statusMessage = "§aShowing past encoded targets for this cannon.";
    }

    private void closeHistoryModal() {
        this.isHistoryOpen = false;
        if (this.historyLabelBox != null) {
            this.historyLabelBox.visible = false;
            this.historyLabelBox.setFocused(false);
        }
        this.setFocused(null);
        updateWidgetActiveStates();
        this.statusMessage = "";
    }

    private void updateWidgetActiveStates() {
        boolean active = !this.isHistoryOpen;
        if (this.modeBtn != null) { this.modeBtn.active = active; this.modeBtn.visible = active; }
        if (this.cannonSizeBtn != null) { this.cannonSizeBtn.active = active && (this.currentMode == CannonMode.FTL_PEARL_CANNON); this.cannonSizeBtn.visible = active && (this.currentMode == CannonMode.FTL_PEARL_CANNON); }
        if (this.historyBtn != null) { this.historyBtn.active = active; this.historyBtn.visible = active; }
        if (this.destXBox != null) {
            this.destXBox.setEditable(active);
            this.destXBox.active = active;
            this.destXBox.visible = active;
        }
        if (this.destZBox != null) {
            this.destZBox.setEditable(active);
            this.destZBox.active = active;
            this.destZBox.visible = active;
        }
        if (this.useMyPosBtn != null) { this.useMyPosBtn.active = active; this.useMyPosBtn.visible = active; }
        if (this.maxTntBox != null) {
            this.maxTntBox.setEditable(active);
            this.maxTntBox.active = active;
            this.maxTntBox.visible = active && (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        }
        if (this.maxTicksBox != null) {
            this.maxTicksBox.setEditable(active);
            this.maxTicksBox.active = active;
            this.maxTicksBox.visible = active && (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        }
        if (this.maxDistBox != null) {
            this.maxDistBox.setEditable(active);
            this.maxDistBox.active = active;
            this.maxDistBox.visible = active && (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        }
        if (this.setAnchorBtn != null) { this.setAnchorBtn.active = active; this.setAnchorBtn.visible = active; }
        if (this.calculateBtn != null) { this.calculateBtn.active = active; this.calculateBtn.visible = active; }
        boolean isFtl = (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        if (this.sortDestBtn != null) { this.sortDestBtn.active = active; this.sortDestBtn.visible = active && isFtl; }
        if (this.sortTntBtn != null) { this.sortTntBtn.active = active; this.sortTntBtn.visible = active && isFtl; }
        if (this.sortTicksBtn != null) { this.sortTicksBtn.active = active; this.sortTicksBtn.visible = active && isFtl; }
        if (this.tpCatcherBtn != null) {
            this.tpCatcherBtn.active = active;
            this.tpCatcherBtn.visible = active && (this.currentMode == CannonMode.FTL_PEARL_CANNON);
        }
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
        List<TargetRecord> history = (state != null) ? state.targetHistory() : null;
        TargetRecord assigned = findTargetForSlot(history, this.historyInspectedRow, this.historyInspectedCol, this.currentMode);
        if (assigned != null) {
            this.updatingLabelBox = true;
            this.historyLabelBox.setValue(assigned.label() != null ? assigned.label() : "");
            this.updatingLabelBox = false;
            this.historyLabelBox.visible = true;
            this.historyLabelBox.active = true;
            this.historyLabelBox.setEditable(true);
        } else {
            this.historyLabelBox.visible = false;
        }
    }

    private void updateAnchorTooltip(AnchorState anchor) {
        if (this.setAnchorBtn == null) return;
        String modeName = (this.currentMode == CannonMode.FTL_PEARL_CANNON) ? "firing lever" : "4x9 selector panel";
        String customTip = (this.currentMode == CannonMode.FTL_PEARL_CANNON)
            ? "(Custom cannons without a lever can be set via /cannon anchor set <x> <z>)"
            : "(Custom cannons can be set via /cannon anchor set <x> <z>)";
        if (anchor != null && anchor.pos() != null && anchor.pos().getY() > 0) {
            String originLabel = (this.currentMode == CannonMode.FTL_PEARL_CANNON) ? "Pearl Origin" : "Launch Origin";
            String tip = "⚓ Auto Anchor (" + originLabel + ": X=" + anchor.pos().getX() + ", Z=" + anchor.pos().getZ() + ")\n"
                + "§7Click to auto-detect nearby " + modeName + ".\n"
                + "§8" + customTip;
            this.setAnchorBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tip)));
        } else {
            String tip = "⚓ Auto Anchor (No anchor set)\n"
                + "§7Click to auto-detect nearby " + modeName + ".\n"
                + "§8" + customTip;
            this.setAnchorBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(tip)));
        }
    }

    private void onSetAnchorHere() {
        if (this.minecraft.player == null || this.minecraft.level == null) return;
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty()) return;

        // Strictly detect anchor for the currently selected mode
        AutoAnchorDetector.DetectionResult autoDet = (this.currentMode == CannonMode.FTL_PEARL_CANNON)
            ? AutoAnchorDetector.detectFtlAnchor(this.minecraft.level, this.minecraft.player.blockPosition())
            : AutoAnchorDetector.detectPigCannonAnchor(this.minecraft.level, this.minecraft.player.blockPosition());

        if (autoDet == null || autoDet.pos() == null || autoDet.pos().getY() <= 0) {
            String structureTip = (this.currentMode == CannonMode.FTL_PEARL_CANNON)
                ? "firing lever (with sign)"
                : "snow layers";
            this.statusMessage = "§c✖ Failed to find " + this.currentMode.displayName() + " anchor nearby! Stand near " + structureTip + " (or set via /cannon anchor set <x> <z>).";
            if (this.minecraft.player != null) {
                this.minecraft.player.displayClientMessage(Component.translatable("cannoncalculator.gui.auto_anchor_failed", this.currentMode.displayName()), true);
            }
            return;
        }

        BlockPos targetPos = autoDet.pos();
        String desc = autoDet.description();

        String tmplId = (this.currentMode == CannonMode.FTL_PEARL_CANNON) ? TemplateRegistry.FTL_836_DEFAULT : TemplateRegistry.CANNON_V1_DEFAULT;
        AnchorState anchor = new AnchorState(targetPos, sessionContext.get().dimensionId(), tmplId, autoDet.panelPos());

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        SessionState updated = (this.currentMode == CannonMode.FTL_PEARL_CANNON)
            ? state.withFtlAnchor(anchor)
            : state.withAnchor(anchor);

        this.stateStore.set(sessionContext.get().environmentKey(), updated);
        this.statusMessage = "§a✔ Auto-detected " + this.currentMode.displayName() + " Anchor at X=" + targetPos.getX() + ", Y=" + targetPos.getY() + ", Z=" + targetPos.getZ() + " (" + desc + ")!";
        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.translatable("cannoncalculator.gui.auto_anchor_success", this.currentMode.displayName()), true);
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
            double destX = Double.parseDouble(this.destXBox.getValue().trim());
            double destZ = Double.parseDouble(this.destZBox.getValue().trim());
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());

            if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
                if (this.cannonSize != 6688 && this.cannonSize != 3344 && this.cannonSize != 1672) {
                    this.cannonSize = 6688;
                }
                int maxTnt = this.cannonSize;
                int maxTicks = FtlConstants.DEFAULT_MAX_TICKS;
                double maxDist = FtlConstants.DEFAULT_MAX_DISTANCE;
                try {
                    if (this.maxTntBox != null && !this.maxTntBox.getValue().trim().isEmpty()) {
                        maxTnt = Integer.parseInt(this.maxTntBox.getValue().trim());
                    }
                    if (this.maxTicksBox != null && !this.maxTicksBox.getValue().trim().isEmpty()) {
                        maxTicks = Integer.parseInt(this.maxTicksBox.getValue().trim());
                    }
                    if (this.maxDistBox != null && !this.maxDistBox.getValue().trim().isEmpty()) {
                        maxDist = Double.parseDouble(this.maxDistBox.getValue().trim());
                    }
                } catch (Exception ignored) {}

                if (maxTnt > this.cannonSize) {
                    maxTnt = this.cannonSize;
                    if (this.maxTntBox != null) this.maxTntBox.setValue(String.valueOf(maxTnt));
                }

                AnchorState anchor = state.effectiveAnchor(CannonMode.FTL_PEARL_CANNON);
                if ((anchor == null || anchor.pos().getY() <= 0) && this.minecraft.player != null && this.minecraft.level != null) {
                    AutoAnchorDetector.DetectionResult autoDet = AutoAnchorDetector.detectFtlAnchor(
                        this.minecraft.level, this.minecraft.player.blockPosition()
                    );
                    if (autoDet != null && autoDet.pos().getY() > 0) {
                        anchor = new AnchorState(autoDet.pos(), sessionContext.get().dimensionId(), TemplateRegistry.FTL_836_DEFAULT, autoDet.panelPos());
                        state = state.withFtlAnchor(anchor);
                        this.stateStore.set(sessionContext.get().environmentKey(), state);
                    }
                }
                if (anchor == null || anchor.pos().getY() <= 0) {
                    this.statusMessage = "§cFailed to find FTL Anchor! Stand near lever & click '⚓ Anchor'.";
                    return;
                }
                double pearlX = anchor.pos().getX() + 0.51;
                double pearlZ = anchor.pos().getZ() + 0.51;

                Vec3d pearlPos = new Vec3d(pearlX, FtlConstants.PEARL_Y, pearlZ);
                Vec3d destPos = new Vec3d(destX, 256.0, destZ);

                this.ftlResults = FTL_CALCULATOR.calculate(
                    pearlPos, destPos, maxTnt, maxTicks, maxDist, this.cannonSize
                );

                if (this.ftlResults.isEmpty()) {
                    this.statusMessage = "§cNo FTL trajectory found within limits.";
                    return;
                }

                this.sortFtlResults();

                this.statusMessage = String.format(java.util.Locale.ROOT,
                    "§aCalculated from Pearl [%d, %d]! Found %d options.",
                    (int) Math.floor(pearlX), (int) Math.floor(pearlZ), this.ftlResults.size());

                cachedDestX = this.destXBox.getValue();
                cachedDestZ = this.destZBox.getValue();
                if (this.maxTntBox != null) cachedMaxTnt = this.maxTntBox.getValue();
                if (this.maxTicksBox != null) cachedMaxTicks = this.maxTicksBox.getValue();
                if (this.maxDistBox != null) cachedMaxDist = this.maxDistBox.getValue();
                cachedCannonSize = this.cannonSize;
                cachedFtlResults = new ArrayList<>(this.ftlResults);
                cachedResultIndex = 0;
                cachedTableScroll = 0;

                this.tableScrollOffset = 0;
                this.currentResultIndex = 0;
                selectResult(0);

                // Add to history
                FtlCalculationResult topRes = this.ftlResults.get(0);
                SessionState latestState = this.stateStore.get(sessionContext.get().environmentKey());
                TargetRecord existingFtl = findTargetForSlot(latestState.targetHistory(), this.selectedLevel, this.selectedColumn, CannonMode.FTL_PEARL_CANNON);
                String ftlLabel = (existingFtl != null && existingFtl.label() != null) ? existingFtl.label() : "";
                TargetRecord rec = new TargetRecord(
                    (int) Math.round(destX), (int) Math.round(destZ), CannonMode.FTL_PEARL_CANNON,
                    this.selectedLevel, this.selectedColumn,
                    topRes.distance(), System.currentTimeMillis(), ftlLabel
                );
                this.stateStore.set(sessionContext.get().environmentKey(), latestState.addTarget(rec));
            } else {
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

                int pDestX = (int) Math.round(destX);
                int pDestZ = (int) Math.round(destZ);

                CalculationResult result = PIG_CALCULATOR.calculate(initX, initZ, pDestX, pDestZ);
                double dist = Math.hypot(pDestX - initX, pDestZ - initZ);
                TargetRecord existingPig = findTargetForSlot(state.targetHistory(), this.selectedLevel, this.selectedColumn, CannonMode.PIG_CANNON);
                String pigLabel = (existingPig != null && existingPig.label() != null) ? existingPig.label() : "";
                TargetRecord rec = new TargetRecord(
                    pDestX, pDestZ, CannonMode.PIG_CANNON,
                    this.selectedLevel, this.selectedColumn,
                    dist, System.currentTimeMillis(), pigLabel
                );
                SessionState updatedState = state.withMode(CannonMode.PIG_CANNON)
                    .withLastCalculation(result)
                    .withSelectedSlot(this.selectedLevel, this.selectedColumn)
                    .addTarget(rec);
                this.stateStore.set(sessionContext.get().environmentKey(), updatedState);

                this.highlightManager.apply(sessionContext.get(), updatedState, result, this.selectedLevel, this.selectedColumn);
                int totalTicks = result.straightAccelFirst() + result.straightAccelSecond() + result.diagonalAccelThird() + result.diagonalAccelFourth() + result.directionCode();
                double minutes = totalTicks / 1200.0;
                this.statusMessage = String.format(java.util.Locale.ROOT,
                    "§aCalculated! Origin: [%d, %d] | Land: [%d, %d] | Time: %dt (~%.1fm) | Dir: %d",
                    result.initX(), result.initZ(), result.landingX(), result.landingZ(), totalTicks, minutes, result.directionCode());
            }
        } catch (NumberFormatException e) {
            this.statusMessage = "§cInvalid numbers in coordinate or filter fields.";
        }
    }

    private void selectResult(int idx) {
        if (this.ftlResults.isEmpty() || idx < 0 || idx >= this.ftlResults.size()) return;
        this.currentResultIndex = idx;
        cachedResultIndex = idx;
        cachedTableScroll = this.tableScrollOffset;
        FtlCalculationResult res = this.ftlResults.get(idx);

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
            SessionState updatedState = state.withMode(CannonMode.FTL_PEARL_CANNON)
                .withFtlCalculation(res)
                .withFtlSelectedSlot(this.selectedLevel, this.selectedColumn);
            this.stateStore.set(sessionContext.get().environmentKey(), updatedState);
            this.highlightManager.applyFtl(sessionContext.get(), updatedState, res, this.selectedLevel, this.selectedColumn);
        }
        this.statusMessage = String.format("§aSelected #%d/%d: Dist %.1fm | %d Ticks | TNT: %d | Catcher: %s",
            idx + 1, this.ftlResults.size(), res.distance(), res.ticks(), res.totalTnt(), res.catcherPos().asInt());
    }

    private void setSortMode(FtlSortMode mode) {
        if (this.sortMode == mode) {
            this.sortAscending = !this.sortAscending;
        } else {
            this.sortMode = mode;
            this.sortAscending = true;
        }
        cachedSortMode = this.sortMode;
        cachedSortAscending = this.sortAscending;
        this.sortFtlResults();
        this.updateSortButtonLabels();
        if (!this.ftlResults.isEmpty() && this.currentResultIndex >= 0 && this.currentResultIndex < this.ftlResults.size()) {
            selectResult(this.currentResultIndex);
        }
    }

    private void updateSortButtonLabels() {
        if (this.sortDestBtn != null) {
            String name = Component.translatable("cannoncalculator.gui.sort.dest").getString();
            String label = (this.sortMode == FtlSortMode.DESTINATION)
                ? (this.sortAscending ? "§e" + name + " ▲" : "§e" + name + " ▼")
                : name;
            this.sortDestBtn.setMessage(Component.literal(label));
        }
        if (this.sortTntBtn != null) {
            String name = Component.translatable("cannoncalculator.gui.sort.tnt").getString();
            String label = (this.sortMode == FtlSortMode.TNT)
                ? (this.sortAscending ? "§e" + name + " ▲" : "§e" + name + " ▼")
                : name;
            this.sortTntBtn.setMessage(Component.literal(label));
        }
        if (this.sortTicksBtn != null) {
            String name = Component.translatable("cannoncalculator.gui.sort.ticks").getString();
            String label = (this.sortMode == FtlSortMode.TICKS)
                ? (this.sortAscending ? "§e" + name + " ▲" : "§e" + name + " ▼")
                : name;
            this.sortTicksBtn.setMessage(Component.literal(label));
        }
    }

    private void sortFtlResults() {
        if (this.ftlResults.isEmpty()) return;

        FtlCalculationResult selected = (this.currentResultIndex >= 0 && this.currentResultIndex < this.ftlResults.size())
            ? this.ftlResults.get(this.currentResultIndex) : null;

        Comparator<FtlCalculationResult> comp = switch (this.sortMode) {
            case TNT -> {
                Comparator<FtlCalculationResult> c = Comparator.comparingInt(FtlCalculationResult::totalTnt);
                if (!this.sortAscending) c = c.reversed();
                yield c.thenComparingDouble(FtlCalculationResult::distance)
                       .thenComparingInt(FtlCalculationResult::ticks);
            }
            case TICKS -> {
                Comparator<FtlCalculationResult> c = Comparator.comparingInt(FtlCalculationResult::ticks);
                if (!this.sortAscending) c = c.reversed();
                yield c.thenComparingDouble(FtlCalculationResult::distance)
                       .thenComparingInt(FtlCalculationResult::totalTnt);
            }
            case DESTINATION -> {
                Comparator<FtlCalculationResult> c = Comparator.comparingDouble(FtlCalculationResult::distance);
                if (!this.sortAscending) c = c.reversed();
                yield c.thenComparingInt(FtlCalculationResult::totalTnt)
                       .thenComparingInt(FtlCalculationResult::ticks);
            }
        };

        this.ftlResults.sort(comp);
        cachedFtlResults = new ArrayList<>(this.ftlResults);

        if (selected != null) {
            int newIdx = this.ftlResults.indexOf(selected);
            if (newIdx >= 0) {
                this.currentResultIndex = newIdx;
                cachedResultIndex = newIdx;
            }
        }

        int totalRows = this.ftlResults.size();
        int visibleRows = Math.max(1, getTableHeight() / 22);
        int maxScroll = Math.max(0, totalRows - visibleRows);
        this.tableScrollOffset = Math.max(0, Math.min(this.tableScrollOffset, maxScroll));
        cachedTableScroll = this.tableScrollOffset;
    }

    private void onApplyHighlight() {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty()) return;

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            SessionState updatedState = state.withFtlSelectedSlot(this.selectedLevel, this.selectedColumn);
            this.stateStore.set(sessionContext.get().environmentKey(), updatedState);
            this.highlightManager.applyFtl(sessionContext.get(), updatedState, state.ftlCalculation(), this.selectedLevel, this.selectedColumn);
            if (state.ftlCalculation() != null) {
                this.statusMessage = "§aFTL Highlight active: Level " + this.selectedLevel + ", Col " + (this.selectedColumn + 1);
            } else {
                this.statusMessage = "§aSelected Lamp: Level " + this.selectedLevel + ", Col " + (this.selectedColumn + 1);
            }
        } else {
            SessionState updatedState = state.withSelectedSlot(this.selectedLevel, this.selectedColumn);
            this.stateStore.set(sessionContext.get().environmentKey(), updatedState);
            this.highlightManager.apply(sessionContext.get(), updatedState, state.lastCalculation(), this.selectedLevel, this.selectedColumn);
            if (state.lastCalculation() != null) {
                this.statusMessage = "§aPig Cannon Highlight active: Level " + this.selectedLevel + ", Col " + (this.selectedColumn + 1);
            } else {
                this.statusMessage = "§aSelected Lamp: Level " + this.selectedLevel + ", Col " + (this.selectedColumn + 1);
            }
        }
    }

    private void onPlaceCatcher() {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty() || this.minecraft.player == null) return;

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        BlockPos catcherPos = state.activeCatcherPos();
        if (catcherPos == null) {
            this.statusMessage = "§eNo active catcher block calculated yet.";
            return;
        }

        boolean ok = CatcherPlacementManager.placeNow(this.minecraft, this.minecraft.player, catcherPos);
        if (ok) {
            this.statusMessage = "§aCatcher placed at " + catcherPos.toShortString() + "!";
        }
    }

    private void onTpCatcher() {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isEmpty() || this.minecraft.player == null) return;

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        BlockPos catcherPos = state.activeCatcherPos();
        if (catcherPos == null && !this.ftlResults.isEmpty() && this.currentResultIndex >= 0 && this.currentResultIndex < this.ftlResults.size()) {
            Vec3d c = this.ftlResults.get(this.currentResultIndex).catcherPos();
            if (c != null) {
                catcherPos = new BlockPos((int) Math.floor(c.x()), (int) Math.floor(c.y()), (int) Math.floor(c.z()));
            }
        }
        if (catcherPos == null) {
            this.statusMessage = "§eNo active catcher block calculated yet.";
            return;
        }

        if (this.minecraft.player != null && this.minecraft.player.connection != null) {
            this.minecraft.player.connection.sendCommand(
                String.format(java.util.Locale.ROOT, "tp %d %d %d", catcherPos.getX(), catcherPos.getY(), catcherPos.getZ())
            );
            this.statusMessage = "§aTeleporting to catcher at " + catcherPos.toShortString() + "...";
        }
    }

    private void onClearHighlight() {
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
            SessionState updated = (this.currentMode == CannonMode.FTL_PEARL_CANNON)
                ? state.withFtlCalculation(null)
                : state.withLastCalculation(null);
            this.stateStore.set(sessionContext.get().environmentKey(), updated);
        }
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            this.ftlResults.clear();
            cachedFtlResults.clear();
            this.currentResultIndex = -1;
        }
        this.highlightManager.clear();
        this.statusMessage = "§eEncoding calculation cleared.";
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
        this.easyPlaceBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable(enabled ? "cannoncalculator.gui.easyplace_active" : "cannoncalculator.gui.easyplace_inactive")
        ));
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withEasyPlace(enabled);
            this.stateStore.set(sessionContext.get().environmentKey(), state);
        }
        this.statusMessage = enabled ? "§a✔ EasyPlace enabled." : "§cEasyPlace disabled.";
    }

    private void onToggleSequentialRails() {
        if (this.highlightManager == null) return;
        boolean enabled = this.highlightManager.toggleSequentialRails();
        this.railsToggleBtn.setMessage(Component.translatable(enabled ? "cannoncalculator.gui.rails_step" : "cannoncalculator.gui.rails_all"));
        this.railsToggleBtn.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.translatable(enabled ? "cannoncalculator.gui.rails_step_tooltip" : "cannoncalculator.gui.rails_all_tooltip")
        ));
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        if (sessionContext.isPresent()) {
            SessionState state = this.stateStore.get(sessionContext.get().environmentKey()).withSequentialRails(enabled);
            this.stateStore.set(sessionContext.get().environmentKey(), state);
        }
        this.statusMessage = enabled
            ? "§a✔ Rails: Step (only render after placing supporting block)."
            : "§eRails: All (all ghost rails render immediately).";
    }

    private void onCheckEncoding() {
        Minecraft client = Minecraft.getInstance();
        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(client);
        if (sessionContext.isEmpty() || client.level == null) return;

        SessionState state = this.stateStore.get(sessionContext.get().environmentKey());
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            SessionState ftlState = state.withFtlSelectedSlot(this.selectedLevel, this.selectedColumn);
            this.lastReport = EncodingValidator.validateFtl(client.level, this.highlightManager.templateRegistry(), ftlState);
        } else {
            SessionState pigState = state.withSelectedSlot(this.selectedLevel, this.selectedColumn);
            this.lastReport = EncodingValidator.validate(client.level, this.highlightManager.templateRegistry(), pigState);
        }

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

    private boolean isMouseOverResultsTable(double mouseX, double mouseY) {
        int leftColX = getLeftColX();
        int tableY = getTableY();
        int tableWidth = getTableWidth();
        int tableHeight = getTableHeight();
        return mouseX >= leftColX && mouseX < leftColX + tableWidth && mouseY >= tableY && mouseY < tableY + tableHeight;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isHistoryOpen) {
            int panelW = 410;
            int panelH = Math.min(240, this.height - 24);
            int panelX = (this.width - panelW) / 2;
            int panelY = (this.height - panelH) / 2;
            int listY = panelY + 118;
            int listH = panelH - 124;
            if (mouseX >= panelX + 8 && mouseX <= panelX + panelW - 8 && mouseY >= listY && mouseY <= listY + listH) {
                Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
                SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(SessionState.empty());
                List<TargetRecord> history = state.targetHistory();
                int totalRows = (history != null) ? history.size() : 0;
                int rowH = 16;
                int maxRows = Math.max(1, (listH - 20) / rowH);
                int maxScroll = Math.max(0, totalRows - maxRows);
                if (scrollY > 0) {
                    this.historyScrollOffset = Math.max(0, this.historyScrollOffset - 1);
                } else if (scrollY < 0) {
                    this.historyScrollOffset = Math.min(maxScroll, this.historyScrollOffset + 1);
                }
                return true;
            }
            return true;
        }

        if (this.currentMode == CannonMode.FTL_PEARL_CANNON && !this.ftlResults.isEmpty()) {
            if (isMouseOverResultsTable(mouseX, mouseY)) {
                int tableHeight = getTableHeight();
                int rowHeight = 22;
                int visibleRows = Math.max(1, tableHeight / rowHeight);
                int maxScroll = Math.max(0, this.ftlResults.size() - visibleRows);

                if (scrollY > 0) {
                    this.tableScrollOffset = Math.max(0, this.tableScrollOffset - 1);
                } else if (scrollY < 0) {
                    this.tableScrollOffset = Math.min(maxScroll, this.tableScrollOffset + 1);
                }
                cachedTableScroll = this.tableScrollOffset;
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();

        // Check clicking on history list
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
                if (this.historyLabelBox.mouseClicked(event, doubleClick)) {
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
                    TargetRecord assigned = findTargetForSlot(history, this.historyInspectedRow, this.historyInspectedCol, this.currentMode);
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

                // 3. Check clicking on history list rows or delete buttons
                int listY = panelY + 118;
                int listH = panelH - 124;
                if (history != null && !history.isEmpty() && mouseX >= panelX + 10 && mouseX < panelX + panelW - 10 && mouseY >= listY + 16 && mouseY < listY + listH) {
                    int rowH = 16;
                    int clickedRow = (int) ((mouseY - (listY + 16)) / rowH);
                    int idx = this.historyScrollOffset + clickedRow;
                    if (idx >= 0 && idx < history.size()) {
                        TargetRecord chosen = history.get(idx);
                        int curY = (listY + 16) + clickedRow * rowH;
                        int delX = panelX + panelW - 24;
                        int delY = curY + 2;
                        int delW = 12;
                        int delH = 12;

                        if (mouseX >= delX && mouseX < delX + delW && mouseY >= delY && mouseY < delY + delH) {
                            // Clicked delete button [✕]
                            SessionState updated = state.removeTarget(chosen);
                            this.stateStore.set(sessionContext.get().environmentKey(), updated);
                            this.statusMessage = "§cDeleted target from history.";
                            updateHistoryLabelBox();
                            return true;
                        } else {
                            // Clicked row body
                            if (doubleClick) {
                                loadTargetRecord(chosen);
                                closeHistoryModal();
                                return true;
                            } else {
                                this.historyInspectedRow = chosen.row();
                                this.historyInspectedCol = chosen.col();
                                updateHistoryLabelBox();
                                return true;
                            }
                        }
                    }
                }
            }

            // Click outside closes modal
            if (mouseX < panelX || mouseX > panelX + panelW || mouseY < panelY || mouseY > panelY + panelH) {
                closeHistoryModal();
                return true;
            }

            return true;
        }

        // Check clicking on results table
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON && !this.ftlResults.isEmpty()) {
            if (isMouseOverResultsTable(mouseX, mouseY)) {
                int tableY = getTableY();
                int rowHeight = 22;
                int clickedIndex = this.tableScrollOffset + (int) ((mouseY - tableY) / rowHeight);
                if (clickedIndex >= 0 && clickedIndex < this.ftlResults.size()) {
                    selectResult(clickedIndex);
                    return true;
                }
            }

            // Check clicking on FTL Encoding Card to copy to clipboard
            int rightX = getRightX();
            int panelY = getPanelY();
            int cellSize = 18;
            int gap = 2;
            int cardY = panelY + 22 + 4 * (cellSize + gap) + 15;
            int cardWidth = 192;
            int cardHeight = Math.max(50, (this.height - 24) - cardY);
            if (mouseX >= rightX && mouseX < rightX + cardWidth && mouseY >= cardY && mouseY < cardY + cardHeight) {
                if (this.currentResultIndex >= 0 && this.currentResultIndex < this.ftlResults.size()) {
                    FtlCalculationResult res = this.ftlResults.get(this.currentResultIndex);
                    if (res.encodingData() != null && this.minecraft != null) {
                        this.minecraft.keyboardHandler.setClipboard(res.encodingData().formatEncodingOutput());
                        this.statusMessage = "§a✔ Copied FTL encoding to clipboard!";
                        return true;
                    }
                }
            }
        }

        // Check clicking on Redstone Lamp Panel (4 Rows × 9 Columns)
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

        return super.mouseClicked(event, doubleClick);
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

        // Input Labels
        int inputY = getPanelY() + 2;
        graphics.drawString(this.font, Component.literal("§7X:"), leftColX + 1, inputY + 4, 0xFFDDDDDD);
        graphics.drawString(this.font, Component.literal("§7Z:"), leftColX + 77, inputY + 4, 0xFFDDDDDD);

        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            int filterY = inputY + 19;
            graphics.drawString(this.font, Component.literal("§7TNT:"), leftColX + 1, filterY + 4, 0xFFAAAAAA);
            graphics.drawString(this.font, Component.literal("§7T:"), leftColX + 73, filterY + 4, 0xFFAAAAAA);
            graphics.drawString(this.font, Component.literal("§7±:"), leftColX + 125, filterY + 4, 0xFFAAAAAA);
        }

        // Render Destinations Table or Pig Cannon Summary or Targets History
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            renderFtlDestinationsTable(graphics, leftColX, getTableY(), getTableWidth(), getTableHeight(), mouseX, mouseY);
        } else {
            renderPigCannonSummary(graphics, leftColX, getTableY(), getTableWidth());
        }

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
                boolean isSelected = (this.selectedLevel == row && this.selectedColumn == col);
                boolean isHovered = (mouseX >= curX && mouseX < curX + cellSize && mouseY >= curY && mouseY < curY + cellSize);
                TargetRecord assigned = findTargetForSlot(history, row, col, this.currentMode);
                boolean isProgrammed = (assigned != null);

                // Draw realistic in-game Redstone Lamp (only active/selected slot is lit, history does not light main menu lamps)
                drawRedstoneLamp(graphics, curX, curY, cellSize, isSelected, isSelected, isHovered);

                // Hover tooltip
                if (isHovered) {
                    graphics.setTooltipForNextFrame(
                        Component.literal("§6Level " + row + ", Column " + (col + 1) + " §7(Click to select slot)"),
                        mouseX,
                        mouseY
                    );
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

        // Right side info card below active slot & materials (Bits returned in place of old material list)
        int cardY = matY + matH + 3;
        int cardH = Math.max(50, (this.height - 24) - cardY);
        if (this.currentMode == CannonMode.FTL_PEARL_CANNON) {
            renderFtlEncodingCard(graphics, rightX, cardY, 192, cardH, mouseX, mouseY);
        }

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

    private void renderFtlEncodingCard(
        GuiGraphics graphics,
        int x, int y, int width, int height,
        int mouseX, int mouseY
    ) {
        graphics.fill(x, y, x + width, y + height, 0xCC111111);
        boolean isHovered = (mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height);
        drawOutline(graphics, x, y, width, height, isHovered ? 0xFFFFA000 : 0xFF333333);

        if (this.currentResultIndex < 0 || this.currentResultIndex >= this.ftlResults.size()) {
            graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.ftl_encoding"), x + 4, y + 4, 0xFFFFA000);
            graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.none"), x + width / 2, y + height / 2 - 4, 0xFF888888);
            return;
        }

        FtlCalculationResult res = this.ftlResults.get(this.currentResultIndex);
        FtlEncodingData enc = res.encodingData();
        if (enc == null) {
            graphics.drawString(this.font, Component.translatable("cannoncalculator.gui.ftl_encoding"), x + 4, y + 4, 0xFFFFA000);
            graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.none"), x + width / 2, y + height / 2 - 4, 0xFF888888);
            return;
        }

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            // Card title + quick info
            graphics.drawString(this.font, Component.literal("§6FTL Solution #" + (this.currentResultIndex + 1) + " §8(Click to copy)"), x + 4, y + 4, 0xFFFFA000);

            // Line 2: TNT & Ticks & Distance
            String tntInfo = String.format(java.util.Locale.ROOT, "§e%d TNT §7(%dE/%dL) §a%dt §7| §f%.1fm",
                res.totalTnt(), res.earlyTnt(), res.lateTnt(), res.ticks(), res.distance());
            graphics.drawString(this.font, Component.literal(tntInfo), x + 4, y + 14, 0xFFDDDDDD);

            // Line 3: Land Position
            String landInfo = String.format(java.util.Locale.ROOT, "§aLand: §e[%.1f, %.1f, %.1f]",
                res.endPos().x(), res.endPos().y(), res.endPos().z());
            graphics.drawString(this.font, Component.literal(landInfo), x + 4, y + 24, 0xFF55FF55);

            // Line 4: Catcher Position
            String catchInfo = String.format(java.util.Locale.ROOT, "§6Catch: §b[%d, %d, %d]",
                (int) Math.floor(res.catcherPos().x()), (int) Math.floor(res.catcherPos().y()), (int) Math.floor(res.catcherPos().z()));
            graphics.drawString(this.font, Component.literal(catchInfo), x + 4, y + 34, 0xFFFFB74D);

            if (enc != null) {
                graphics.fill(x + 4, y + 44, x + width - 4, y + 45, 0xFF444444);

                boolean twoCols = (height < 155);
                int textX = x + 4;
                int bitY = y + 47;
                int lineSpacing = 9;

                String purStr = "§5pur: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.purple(), new int[]{4, 2, 1}));
                String bluStr = "§9blu: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.blue(), new int[]{2, 1}));
                String cyaStr = "§bcya: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.cyan(), new int[]{4, 4, 2, 1}));
                String ltbStr = "§3ltb: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.lightBlue(), new int[]{4, 2, 1}));
                String limStr = "§alim: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.lime(), new int[]{11, 11, 8, 4, 2, 1}));
                String yelStr = "§eyel: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.yellow(), new int[]{8, 4, 2, 1}));
                String oraStr = "§6ora: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.orange(), new int[]{11, 11, 8, 4, 2, 1}));
                String redStr = "§cred: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.red(), new int[]{4, 2, 1}));
                String pnkStr = "§dpnk: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.pink(), new int[]{4, 4, 2, 1}));
                String magStr = "§5mag: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.magenta(), new int[]{2, 1}));
                String pu2Str = "§5pu2: §f" + FtlEncodingData.rev(FtlEncodingData.binary(enc.purple2(), new int[]{2, 1}));
                String blockStr = (enc.blocker() != null) ? String.format("[%d %d %d]", (int) Math.floor(enc.blocker().x()), (int) Math.floor(enc.blocker().y()), (int) Math.floor(enc.blocker().z())) : "none";
                String blkStr = "§7blk: §f" + blockStr;

                if (!twoCols) {
                    graphics.drawString(this.font, Component.literal(purStr), textX, bitY + 0 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(bluStr), textX, bitY + 1 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(cyaStr), textX, bitY + 2 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(ltbStr), textX, bitY + 3 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(limStr), textX, bitY + 4 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(yelStr), textX, bitY + 5 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(oraStr), textX, bitY + 6 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(redStr), textX, bitY + 7 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(pnkStr), textX, bitY + 8 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(magStr), textX, bitY + 9 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(pu2Str), textX, bitY + 10 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(blkStr), textX, bitY + 11 * lineSpacing, 0xFFFFFFFF);
                } else {
                    int col2X = x + 96;
                    graphics.drawString(this.font, Component.literal(purStr), textX, bitY + 0 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(bluStr), textX, bitY + 1 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(cyaStr), textX, bitY + 2 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(ltbStr), textX, bitY + 3 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(limStr), textX, bitY + 4 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(yelStr), textX, bitY + 5 * lineSpacing, 0xFFFFFFFF);

                    graphics.drawString(this.font, Component.literal(oraStr), col2X, bitY + 0 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(redStr), col2X, bitY + 1 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(pnkStr), col2X, bitY + 2 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(magStr), col2X, bitY + 3 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(pu2Str), col2X, bitY + 4 * lineSpacing, 0xFFFFFFFF);
                    graphics.drawString(this.font, Component.literal(blkStr), col2X, bitY + 5 * lineSpacing, 0xFFFFFFFF);
                }
            }
        } finally {
            graphics.disableScissor();
        }

        if (isHovered) {
            graphics.setTooltipForNextFrame(
                Component.translatable("cannoncalculator.gui.copy_bits_tooltip"),
                mouseX, mouseY
            );
        }
    }

    private void renderFtlDestinationsTable(
        GuiGraphics graphics,
        int x, int y, int width, int height,
        int mouseX, int mouseY
    ) {
        if (this.ftlResults.isEmpty()) {
            graphics.fill(x, y, x + width, y + height, 0x99111111);
            drawOutline(graphics, x, y, width, height, 0xFF333333);
            graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.no_trajectories"), x + width / 2, y + height / 2 - 4, 0xFF888888);
            graphics.drawCenteredString(this.font, Component.translatable("cannoncalculator.gui.set_target_calc"), x + width / 2, y + height / 2 + 6, 0xFF666666);
            return;
        }

        int rowHeight = 22;
        int visibleRows = Math.max(1, height / rowHeight);
        int totalRows = this.ftlResults.size();
        boolean needsScroll = totalRows > visibleRows;
        int contentWidth = needsScroll ? (width - 6) : width;

        // Container background
        graphics.fill(x, y, x + width, y + height, 0xCC0D0D0D);
        drawOutline(graphics, x, y, width, height, 0xFF333333);

        int startIdx = Math.max(0, Math.min(this.tableScrollOffset, totalRows - visibleRows));
        int endIdx = Math.min(totalRows, startIdx + visibleRows);

        graphics.enableScissor(x, y, x + width, y + height);
        try {
            for (int i = startIdx; i < endIdx; ++i) {
                int rowY = y + (i - startIdx) * rowHeight;
                FtlCalculationResult res = this.ftlResults.get(i);
                boolean isSelected = (i == this.currentResultIndex);
                boolean isHovered = (mouseX >= x && mouseX < x + contentWidth && mouseY >= rowY && mouseY < rowY + rowHeight);

                // Row background
                if (isSelected) {
                    graphics.fill(x + 1, rowY + 1, x + contentWidth - 1, rowY + rowHeight - 1, 0x44FFA000);
                    drawOutline(graphics, x + 1, rowY + 1, contentWidth - 2, rowHeight - 2, 0xFFFFD54F);
                } else if (isHovered) {
                    graphics.fill(x + 1, rowY + 1, x + contentWidth - 1, rowY + rowHeight - 1, 0x22FFFFFF);
                    drawOutline(graphics, x + 1, rowY + 1, contentWidth - 2, rowHeight - 2, 0xFF80D8FF);
                } else if (i % 2 == 1) {
                    graphics.fill(x + 1, rowY + 1, x + contentWidth - 1, rowY + rowHeight - 1, 0x33222222);
                }

                // Line 1: #i  Distance  |  Total TNT (Early/Late)
                String line1 = String.format(java.util.Locale.ROOT, "§6#%d §f%.1fm §7| §e%d TNT §7(%dE/%dL)",
                    i + 1, res.distance(), res.totalTnt(), res.earlyTnt(), res.lateTnt());
                graphics.drawString(this.font, Component.literal(line1), x + 3, rowY + 2, 0xFFFFFFFF);

                // Line 2: Ticks | Land Pos
                String line2 = String.format(java.util.Locale.ROOT, "§a%dt §7| Land: §e[%.0f, %.0f]",
                    res.ticks(), res.endPos().x(), res.endPos().z());
                graphics.drawString(this.font, Component.literal(line2), x + 3, rowY + 11, 0xFFCCCCCC);
            }
        } finally {
            graphics.disableScissor();
        }

        // Row hover tooltip outside scissor
        for (int i = startIdx; i < endIdx; ++i) {
            int rowY = y + (i - startIdx) * rowHeight;
            FtlCalculationResult res = this.ftlResults.get(i);
            boolean isHovered = (mouseX >= x && mouseX < x + contentWidth && mouseY >= rowY && mouseY < rowY + rowHeight);
            if (isHovered) {
                graphics.setTooltipForNextFrame(
                    Component.literal(String.format(java.util.Locale.ROOT,
                        "§6#%d §e%d TNT §7(%dE/%dL) §a%dt §7| Dist: §f%.2fm\n§aLand: §e[%.1f, %.1f, %.1f]\n§6Catch: §b[%d, %d, %d]",
                        i + 1, res.totalTnt(), res.earlyTnt(), res.lateTnt(), res.ticks(), res.distance(),
                        res.endPos().x(), res.endPos().y(), res.endPos().z(),
                        (int) Math.floor(res.catcherPos().x()), (int) Math.floor(res.catcherPos().y()), (int) Math.floor(res.catcherPos().z()))),
                    mouseX, mouseY
                );
                break;
            }
        }

        // Scrollbar
        if (needsScroll) {
            int scrollbarX = x + width - 5;
            graphics.fill(scrollbarX, y + 1, scrollbarX + 4, y + height - 1, 0xFF1A1A1A);
            int maxScroll = totalRows - visibleRows;
            int thumbHeight = Math.max(10, (visibleRows * (height - 2)) / totalRows);
            int thumbY = y + 1 + (this.tableScrollOffset * ((height - 2) - thumbHeight)) / maxScroll;
            graphics.fill(scrollbarX, thumbY, scrollbarX + 4, thumbY + thumbHeight, 0xFFAAAAAA);
        }
    }

    private void renderPigCannonSummary(GuiGraphics graphics, int x, int y, int width) {
        int maxAllowedH = Math.max(40, (this.height - 24) - y);
        int h = Math.min(76, maxAllowedH);
        graphics.fill(x, y, x + width, y + h, 0xCC111111);
        drawOutline(graphics, x, y, width, h, 0xFF333333);

        Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
        SessionState state = sessionContext.map(c -> this.stateStore.get(c.environmentKey())).orElse(SessionState.empty());
        if (state.cannonSize() > 0) {
            this.cannonSize = state.cannonSize();
            cachedCannonSize = this.cannonSize;
        }

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

    private void loadTargetRecord(TargetRecord rec) {
        if (rec == null) return;
        boolean modeChanged = (this.currentMode != rec.mode());
        this.currentMode = rec.mode();
        cachedMode = this.currentMode;
        if (this.destXBox != null) this.destXBox.setValue(String.valueOf(rec.destX()));
        if (this.destZBox != null) this.destZBox.setValue(String.valueOf(rec.destZ()));
        cachedDestX = String.valueOf(rec.destX());
        cachedDestZ = String.valueOf(rec.destZ());
        this.selectedLevel = Math.max(0, Math.min(3, rec.row()));
        this.selectedColumn = Math.max(0, Math.min(8, rec.col()));
        cachedSelectedLevel = this.selectedLevel;
        cachedSelectedCol = this.selectedColumn;

        if (modeChanged) {
            this.clearWidgets();
            this.init();
        }

        this.onCalculate();

        if (rec.label() != null && !rec.label().isBlank()) {
            Optional<ClientSessionContext> sessionContext = ClientSessionContext.resolve(this.minecraft);
            if (sessionContext.isPresent()) {
                SessionState s = this.stateStore.get(sessionContext.get().environmentKey());
                SessionState updated = s.updateTargetLabel(this.selectedLevel, this.selectedColumn, null, rec.label());
                this.stateStore.set(sessionContext.get().environmentKey(), updated);
            }
        }

        String modeTag = (rec.mode() == CannonMode.FTL_PEARL_CANNON) ? "FTL" : "Pig";
        String loadedMsg = String.format(java.util.Locale.ROOT,
            "§a✔ Loaded [%s] Lamp L%d C%d -> Dest [%d, %d] (%.0fm) - Encoding Active!",
            modeTag, this.selectedLevel, this.selectedColumn + 1, rec.destX(), rec.destZ(), rec.distance());
        this.statusMessage = loadedMsg;

        if (this.minecraft.player != null) {
            this.minecraft.player.displayClientMessage(Component.literal(loadedMsg), true);
        }
    }

    private static TargetRecord findTargetForSlot(List<TargetRecord> history, int row, int col) {
        return findTargetForSlot(history, row, col, null);
    }

    private static TargetRecord findTargetForSlot(List<TargetRecord> history, int row, int col, CannonMode preferredMode) {
        if (history == null) return null;
        for (TargetRecord tr : history) {
            if (tr.row() == row && tr.col() == col && (preferredMode == null || tr.mode() == preferredMode)) {
                return tr;
            }
        }
        for (TargetRecord tr : history) {
            if (tr.row() == row && tr.col() == col) {
                return tr;
            }
        }
        return null;
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

        for (int row = 3; row >= 0; --row) {
            int displayRowIdx = 3 - row;
            int cy = gridStartY + displayRowIdx * (cellSize + gap);

            // Row header
            graphics.drawString(this.font, Component.literal("L" + row), gridStartX - 16, cy + 5, 0xFFCCCCCC);

            for (int col = 0; col < 9; col++) {
                int cx = gridStartX + col * (cellSize + gap);
                TargetRecord assigned = findTargetForSlot(history, row, col, this.currentMode);
                boolean isProgrammed = (assigned != null);
                boolean isCurrentSlot = (row == this.selectedLevel && col == this.selectedColumn);
                boolean isHovered = (mouseX >= cx && mouseX < cx + cellSize && mouseY >= cy && mouseY < cy + cellSize);
                boolean isInspected = (row == this.historyInspectedRow && col == this.historyInspectedCol);
                drawRedstoneLamp(graphics, cx, cy, cellSize, isProgrammed, isCurrentSlot, isHovered);
                if (isInspected) {
                    drawOutline(graphics, cx, cy, cellSize, cellSize, 0xFFFFD54F);
                }
                if (isHovered) {
                    String hoverTip = (assigned != null)
                        ? String.format(java.util.Locale.ROOT, "§6L%d C%d: §eX=%d, Z=%d §7(Click to inspect)", row, col + 1, assigned.destX(), assigned.destZ())
                        : String.format(java.util.Locale.ROOT, "§6L%d C%d: §7Empty (Click to inspect)", row, col + 1);
                    graphics.setTooltipForNextFrame(Component.literal(hoverTip), mouseX, mouseY);
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
        TargetRecord inspectedTarget = findTargetForSlot(history, insRow, insCol, this.currentMode);
        boolean isCurrentInspected = (insRow == this.selectedLevel && insCol == this.selectedColumn);

        String activeBadge = isCurrentInspected ? " §b★ Active" : "";
        graphics.drawString(this.font, Component.literal("§6Slot: §fL" + insRow + " C" + (insCol + 1) + activeBadge), infoX + 6, infoY + 6, 0xFFFFA000);

        if (inspectedTarget != null) {
            String tag = (inspectedTarget.mode() == CannonMode.FTL_PEARL_CANNON) ? "§b[FTL Pearl]" : "§d[Pig Cannon]";
            graphics.drawString(this.font, Component.literal("§7Mode: " + tag), infoX + 6, infoY + 17, 0xFFDDDDDD);
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
                this.historyLabelBox.render(graphics, mouseX, mouseY, 0.0f);
            }

            int btnX = infoX + 6;
            int btnY = infoY + 58;
            int btnW = infoW - 12;
            int btnH = 22;
            boolean btnHover = (mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH);
            graphics.fill(btnX, btnY, btnX + btnW, btnY + btnH, btnHover ? 0xFF2E7D32 : 0xFF1B5E20);
            drawOutline(graphics, btnX, btnY, btnW, btnH, btnHover ? 0xFF81C784 : 0xFF4CAF50);
            Component btnComp = isCurrentInspected
                ? Component.translatable("cannoncalculator.gui.slot_active_close")
                : Component.translatable("cannoncalculator.gui.slot_apply");
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

        for (int i = startIdx; i < endIdx; i++) {
            TargetRecord rec = history.get(i);
            int curY = rowY + (i - startIdx) * rowH;
            int delX = panelX + panelW - 24;
            int delY = curY + 2;
            int delW = 12;
            int delH = 12;
            boolean isDeleteHover = (mouseX >= delX && mouseX < delX + delW && mouseY >= delY && mouseY < delY + delH);
            boolean isRowHover = (mouseX >= panelX + 10 && mouseX < delX - 2 && mouseY >= curY && mouseY < curY + rowH);
            boolean isRowInspected = (rec.row() == this.historyInspectedRow && rec.col() == this.historyInspectedCol && rec.mode() == this.currentMode);

            if (isRowHover || isRowInspected) {
                graphics.fill(panelX + 10, curY, panelX + panelW - 14, curY + rowH, isRowInspected ? 0x4400E5FF : 0x44FFA000);
                drawOutline(graphics, panelX + 10, curY, panelW - 24, rowH, isRowInspected ? 0xFF00E5FF : 0xFFFFD54F);
            } else if (i % 2 == 1) {
                graphics.fill(panelX + 10, curY, panelX + panelW - 14, curY + rowH, 0x22222222);
            }

            String tag = (rec.mode() == CannonMode.FTL_PEARL_CANNON) ? "§b[FTL]" : "§d[Pig]";
            String line;
            if (rec.label() != null && !rec.label().isBlank()) {
                line = String.format(java.util.Locale.ROOT, "%s §e\"%s\" §7(L%d C%d -> X=%d, Z=%d)", tag, rec.label(), rec.row(), rec.col() + 1, rec.destX(), rec.destZ());
            } else {
                line = String.format(java.util.Locale.ROOT, "%s §eL%d C%d §7-> §fX=%d, Z=%d §7(%.0fm)", tag, rec.row(), rec.col() + 1, rec.destX(), rec.destZ(), rec.distance());
            }
            graphics.drawString(this.font, Component.literal(line), panelX + 14, curY + 4, 0xFFFFFFFF);

            // Delete button [✕]
            graphics.fill(delX, delY, delX + delW, delY + delH, isDeleteHover ? 0xFFD32F2F : 0xFF552222);
            drawOutline(graphics, delX, delY, delW, delH, isDeleteHover ? 0xFFFF5252 : 0xFF883333);
            graphics.drawCenteredString(this.font, Component.literal("✕"), delX + delW / 2, delY + 2, isDeleteHover ? 0xFFFFFFFF : 0xFFFFAAAA);
            if (isDeleteHover) {
                graphics.setTooltipForNextFrame(Component.translatable("cannoncalculator.gui.delete_target_tooltip"), mouseX, mouseY);
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
                graphics.setTooltipForNextFrame(Component.literal("§e" + entry.name() + "\n" + status), mouseX, mouseY);
            }

            itemX += spacing;
        }
    }

    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        if (event.key() == 257 || event.key() == 335) { // Enter or Numpad Enter
            if ((this.destXBox != null && this.destXBox.isFocused())
                || (this.destZBox != null && this.destZBox.isFocused())
                || (this.maxTntBox != null && this.maxTntBox.isFocused())
                || (this.maxTicksBox != null && this.maxTicksBox.isFocused())
                || (this.maxDistBox != null && this.maxDistBox.isFocused())) {
                this.onCalculate();
                return true;
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        if (this.isHistoryOpen) {
            closeHistoryModal();
            return;
        }
        if (this.destXBox != null) cachedDestX = this.destXBox.getValue();
        if (this.destZBox != null) cachedDestZ = this.destZBox.getValue();
        if (this.maxTntBox != null) cachedMaxTnt = this.maxTntBox.getValue();
        if (this.maxTicksBox != null) cachedMaxTicks = this.maxTicksBox.getValue();
        if (this.maxDistBox != null) cachedMaxDist = this.maxDistBox.getValue();
        cachedCannonSize = this.cannonSize;
        cachedMode = this.currentMode;
        cachedSelectedLevel = this.selectedLevel;
        cachedSelectedCol = this.selectedColumn;
        cachedResultIndex = this.currentResultIndex;
        cachedTableScroll = this.tableScrollOffset;
        if (!this.ftlResults.isEmpty()) {
            cachedFtlResults = new ArrayList<>(this.ftlResults);
        }
        super.onClose();
    }

    /**
     * Draws an authentic in-game Redstone Lamp using the actual Minecraft textures
     * (minecraft:textures/block/redstone_lamp.png and redstone_lamp_on.png), with a glowing
     * radiant border when lit, distinct cyan active slot indicator, and hover frame.
     */
    private void drawRedstoneLamp(
        GuiGraphics graphics,
        int x, int y, int size,
        boolean lit, boolean isCurrentSlot, boolean hovered
    ) {
        ResourceLocation texture = lit ? LAMP_ON_TEXTURE : LAMP_OFF_TEXTURE;
        // Blit texture properly fitted inside the cell (16x16 inside 18x18 cell)
        graphics.blit(texture, x + 1, y + 1, x + size - 1, y + size - 1, 0.0f, 1.0f, 0.0f, 1.0f);

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
            drawOutline(graphics, x, y, size, size, 0xFFFFFFFF);
        }
    }

    private static void drawOutline(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
