package dev.selestor1462.cannonencoder.state;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import java.util.ArrayList;
import java.util.List;

public record SessionState(
    CannonMode cannonMode,
    AnchorState anchor,
    CalculationResult lastCalculation,
    int selectedRow,
    int selectedCol,
    boolean easyPlaceEnabled,
    boolean highlightsVisible,
    List<TargetRecord> targetHistory,
    boolean sequentialRails
) {
    public SessionState(
        AnchorState anchor,
        CalculationResult lastCalculation,
        int selectedRow,
        int selectedCol,
        boolean easyPlaceEnabled
    ) {
        this(CannonMode.PIG_CANNON, anchor, lastCalculation, selectedRow, selectedCol, easyPlaceEnabled, true, List.of(), false);
    }

    public SessionState(
        AnchorState anchor,
        CalculationResult lastCalculation,
        int selectedRow,
        int selectedCol
    ) {
        this(CannonMode.PIG_CANNON, anchor, lastCalculation, selectedRow, selectedCol, false, true, List.of(), false);
    }

    public SessionState(AnchorState anchor, CalculationResult lastCalculation) {
        this(CannonMode.PIG_CANNON, anchor, lastCalculation, 0, 0, false, true, List.of(), false);
    }

    public SessionState(
        CannonMode cannonMode,
        AnchorState anchor,
        CalculationResult lastCalculation,
        int selectedRow,
        int selectedCol,
        boolean easyPlaceEnabled,
        boolean highlightsVisible
    ) {
        this(cannonMode, anchor, lastCalculation, selectedRow, selectedCol, easyPlaceEnabled, highlightsVisible, List.of(), false);
    }

    public static SessionState empty() {
        return empty(false, false, true);
    }

    public static SessionState empty(boolean defaultEasyPlace, boolean defaultSequentialRails) {
        return empty(defaultEasyPlace, defaultSequentialRails, true);
    }

    public static SessionState empty(boolean defaultEasyPlace, boolean defaultSequentialRails, boolean defaultHighlightsVisible) {
        return new SessionState(CannonMode.PIG_CANNON, null, null, 0, 0, defaultEasyPlace, defaultHighlightsVisible, List.of(), defaultSequentialRails);
    }

    public boolean isEmpty() {
        return this.anchor == null && this.lastCalculation == null
            && !this.easyPlaceEnabled && this.highlightsVisible
            && (this.targetHistory == null || this.targetHistory.isEmpty())
            && !this.sequentialRails;
    }

    public AnchorState effectiveAnchor(CannonMode mode) {
        return this.anchor;
    }

    public SessionState withMode(CannonMode mode) {
        return this;
    }

    public SessionState withAnchor(AnchorState updatedAnchor) {
        return new SessionState(
            this.cannonMode, updatedAnchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.highlightsVisible, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withLastCalculation(CalculationResult result) {
        return new SessionState(
            this.cannonMode, this.anchor, result, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.highlightsVisible, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withSelectedSlot(int row, int col) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, row, col,
            this.easyPlaceEnabled, this.highlightsVisible, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withEasyPlace(boolean enabled) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            enabled, this.highlightsVisible, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withSequentialRails(boolean sequentialRails) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.highlightsVisible, this.targetHistory, sequentialRails
        );
    }

    public SessionState withHighlightsVisible(boolean visible) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, visible, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withTargetHistory(List<TargetRecord> history) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.highlightsVisible,
            history != null ? List.copyOf(history) : List.of(), this.sequentialRails
        );
    }

    public SessionState addTarget(TargetRecord record) {
        if (record == null) return this;
        List<TargetRecord> list = new ArrayList<>(this.targetHistory != null ? this.targetHistory : List.of());
        list.removeIf(r -> r.destX() == record.destX() && r.destZ() == record.destZ() && r.mode() == record.mode());
        list.add(0, record);
        if (list.size() > 20) {
            list = list.subList(0, 20);
        }
        return withTargetHistory(list);
    }

    public SessionState removeTarget(TargetRecord record) {
        if (record == null || this.targetHistory == null) return this;
        List<TargetRecord> list = new ArrayList<>(this.targetHistory);
        list.remove(record);
        return withTargetHistory(list);
    }

    public SessionState removeTargetAt(int index) {
        if (this.targetHistory == null || index < 0 || index >= this.targetHistory.size()) return this;
        List<TargetRecord> list = new ArrayList<>(this.targetHistory);
        list.remove(index);
        return withTargetHistory(list);
    }

    public SessionState updateTargetLabel(int row, int col, CannonMode mode, String label) {
        if (this.targetHistory == null) return this;
        List<TargetRecord> list = new ArrayList<>();
        for (TargetRecord tr : this.targetHistory) {
            if (tr.row() == row && tr.col() == col && (mode == null || tr.mode() == mode)) {
                list.add(tr.withLabel(label));
            } else {
                list.add(tr);
            }
        }
        return withTargetHistory(list);
    }
}
