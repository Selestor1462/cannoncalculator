package dev.selestor1462.cannonencoder.state;

import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculationResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

public record SessionState(
    CannonMode cannonMode,
    AnchorState anchor,
    CalculationResult lastCalculation,
    int selectedRow,
    int selectedCol,
    boolean easyPlaceEnabled,
    AnchorState ftlAnchor,
    FtlCalculationResult ftlCalculation,
    int ftlSelectedRow,
    int ftlSelectedCol,
    BlockPos activeCatcherPos,
    boolean highlightsVisible,
    int cannonSize,
    List<TargetRecord> targetHistory,
    boolean sequentialRails
) {
    public SessionState(
        AnchorState anchor,
        CalculationResult lastCalculation,
        int selectedRow,
        int selectedCol
    ) {
        this(CannonMode.FTL_PEARL_CANNON, anchor, lastCalculation, selectedRow, selectedCol, false, null, null, 0, 0, null, true, 6688, List.of(), false);
    }

    public SessionState(AnchorState anchor, CalculationResult lastCalculation) {
        this(CannonMode.FTL_PEARL_CANNON, anchor, lastCalculation, 0, 0, false, null, null, 0, 0, null, true, 6688, List.of(), false);
    }

    public SessionState(
        CannonMode cannonMode,
        AnchorState anchor,
        CalculationResult lastCalculation,
        int selectedRow,
        int selectedCol,
        boolean easyPlaceEnabled,
        AnchorState ftlAnchor,
        FtlCalculationResult ftlCalculation,
        int ftlSelectedRow,
        int ftlSelectedCol,
        BlockPos activeCatcherPos,
        boolean highlightsVisible,
        int cannonSize
    ) {
        this(cannonMode, anchor, lastCalculation, selectedRow, selectedCol, easyPlaceEnabled, ftlAnchor, ftlCalculation, ftlSelectedRow, ftlSelectedCol, activeCatcherPos, highlightsVisible, cannonSize, List.of(), false);
    }

    public static SessionState empty() {
        return empty(false, false, true);
    }

    public static SessionState empty(boolean defaultEasyPlace, boolean defaultSequentialRails) {
        return empty(defaultEasyPlace, defaultSequentialRails, true);
    }

    public static SessionState empty(boolean defaultEasyPlace, boolean defaultSequentialRails, boolean defaultHighlightsVisible) {
        return new SessionState(CannonMode.FTL_PEARL_CANNON, null, null, 0, 0, defaultEasyPlace, null, null, 0, 0, null, defaultHighlightsVisible, 6688, List.of(), defaultSequentialRails);
    }

    public boolean isEmpty() {
        return this.anchor == null && this.lastCalculation == null
            && this.ftlAnchor == null && this.ftlCalculation == null
            && this.activeCatcherPos == null
            && !this.easyPlaceEnabled
            && this.highlightsVisible
            && !this.sequentialRails
            && this.cannonMode == CannonMode.FTL_PEARL_CANNON
            && this.cannonSize == 6688
            && (this.targetHistory == null || this.targetHistory.isEmpty());
    }

    public AnchorState effectiveAnchor(CannonMode mode) {
        if (mode == CannonMode.FTL_PEARL_CANNON) {
            return this.ftlAnchor;
        }
        return this.anchor;
    }

    public SessionState withMode(CannonMode mode) {
        return new SessionState(
            mode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withAnchor(AnchorState updatedAnchor) {
        return new SessionState(
            this.cannonMode, updatedAnchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withLastCalculation(CalculationResult updatedCalculation) {
        return new SessionState(
            this.cannonMode, this.anchor, updatedCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withSelectedSlot(int row, int col) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, row, col,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withEasyPlace(boolean enabled) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            enabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withFtlAnchor(AnchorState updatedAnchor) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, updatedAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withFtlCalculation(FtlCalculationResult updatedCalculation) {
        BlockPos catcher = null;
        if (updatedCalculation != null && updatedCalculation.catcherPos() != null) {
            catcher = new BlockPos(
                (int) Math.floor(updatedCalculation.catcherPos().x()),
                (int) Math.floor(updatedCalculation.catcherPos().y()),
                (int) Math.floor(updatedCalculation.catcherPos().z())
            );
        }
        int effectiveSize = (updatedCalculation != null && updatedCalculation.encodingData() != null && updatedCalculation.encodingData().cannonMaxTnt() > 0)
            ? updatedCalculation.encodingData().cannonMaxTnt() : this.cannonSize;
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, updatedCalculation, this.ftlSelectedRow, this.ftlSelectedCol, catcher, this.highlightsVisible, effectiveSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withFtlSelectedSlot(int row, int col) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, row, col, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withActiveCatcherPos(BlockPos catcherPos) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, catcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withHighlightsVisible(boolean highlightsVisible) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, highlightsVisible, this.cannonSize, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withCannonSize(int size) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, size, this.targetHistory, this.sequentialRails
        );
    }

    public SessionState withTargetHistory(List<TargetRecord> history) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, history != null ? List.copyOf(history) : List.of(), this.sequentialRails
        );
    }

    public SessionState withSequentialRails(boolean sequentialRails) {
        return new SessionState(
            this.cannonMode, this.anchor, this.lastCalculation, this.selectedRow, this.selectedCol,
            this.easyPlaceEnabled, this.ftlAnchor, this.ftlCalculation, this.ftlSelectedRow, this.ftlSelectedCol, this.activeCatcherPos, this.highlightsVisible, this.cannonSize, this.targetHistory, sequentialRails
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
