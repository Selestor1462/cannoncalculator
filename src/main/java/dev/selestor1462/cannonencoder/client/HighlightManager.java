package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.state.AnchorState;
import dev.selestor1462.cannonencoder.state.CannonMode;
import dev.selestor1462.cannonencoder.state.SessionState;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import dev.selestor1462.cannonencoder.template.TemplateRegistry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class HighlightManager {
    public enum ApplyResult {
        ACTIVE,
        NO_ANCHOR,
        DIMENSION_MISMATCH,
        NO_TEMPLATE_DATA,
        EMPTY_PLAN
    }

    private final TemplateRegistry templateRegistry;
    private ActiveHighlight activeHighlight;
    private SessionState currentState;
    private boolean visible = true;
    private boolean sequentialRails = false;

    public SessionState currentState() {
        return this.currentState;
    }

    public boolean isSequentialRails() {
        return this.sequentialRails;
    }

    public void setSequentialRails(boolean sequentialRails) {
        this.sequentialRails = sequentialRails;
    }

    public boolean toggleSequentialRails() {
        this.sequentialRails = !this.sequentialRails;
        return this.sequentialRails;
    }

    public HighlightManager(TemplateRegistry templateRegistry) {
        this.templateRegistry = Objects.requireNonNull(templateRegistry, "templateRegistry");
    }

    public ApplyResult apply(ClientSessionContext context, SessionState state, CalculationResult result) {
        return this.apply(context, state, result, 0, 0);
    }

    public ApplyResult apply(ClientSessionContext context, SessionState state, CalculationResult result, int level, int column) {
        AnchorState anchor = state.anchor();
        if (anchor == null) {
            this.clear();
            return ApplyResult.NO_ANCHOR;
        }
        if (!anchor.dimensionId().equals(context.dimensionId())) {
            this.clear();
            return ApplyResult.DIMENSION_MISMATCH;
        }

        Optional<HighlightPlan> plan = this.templateRegistry.resolve(anchor, result, level, column);
        if (plan.isEmpty()) {
            this.clear();
            return ApplyResult.NO_TEMPLATE_DATA;
        }
        if (plan.get().blocks().isEmpty()) {
            this.clear();
            return ApplyResult.EMPTY_PLAN;
        }

        this.activeHighlight = new ActiveHighlight(context.environmentKey(), context.dimensionId(), plan.get());
        this.currentState = state;
        return ApplyResult.ACTIVE;
    }

    public void restore(ClientSessionContext context, SessionState state) {
        this.visible = state.highlightsVisible();
        this.sequentialRails = state.sequentialRails();
        this.currentState = state;
        if (state.anchor() == null) {
            this.clear();
            return;
        }
        this.apply(context, state, state.lastCalculation(), state.selectedRow(), state.selectedCol());
    }

    public List<HighlightedBlock> visibleHighlights(ClientSessionContext context) {
        if (!this.visible || this.activeHighlight == null) {
            return List.of();
        }
        if (!this.activeHighlight.environmentKey.equalsIgnoreCase(context.environmentKey())) {
            return List.of();
        }
        if (!this.activeHighlight.dimensionId.equals(context.dimensionId())) {
            return List.of();
        }
        return this.activeHighlight.plan.blocks();
    }

    public void clear() {
        this.activeHighlight = null;
        this.currentState = null;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public ActiveHighlight activeHighlight() {
        return this.activeHighlight;
    }

    public HighlightPlan activePlan() {
        return this.activeHighlight != null ? this.activeHighlight.plan() : null;
    }

    public TemplateRegistry templateRegistry() {
        return this.templateRegistry;
    }

    public static final class ActiveHighlight {
        private final String environmentKey;
        private final String dimensionId;
        private final HighlightPlan plan;

        public ActiveHighlight(String environmentKey, String dimensionId, HighlightPlan plan) {
            this.environmentKey = environmentKey;
            this.dimensionId = dimensionId;
            this.plan = plan;
        }

        public String environmentKey() {
            return this.environmentKey;
        }

        public String dimensionId() {
            return this.dimensionId;
        }

        public HighlightPlan plan() {
            return this.plan;
        }
    }
}
