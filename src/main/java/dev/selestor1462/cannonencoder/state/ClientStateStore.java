package dev.selestor1462.cannonencoder.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.selestor1462.cannonencoder.PigCannonEncoder;
import dev.selestor1462.cannonencoder.calc.CalculationResult;
import dev.selestor1462.cannonencoder.calc.ftl.FtlCalculationResult;
import dev.selestor1462.cannonencoder.calc.ftl.FtlEncodingData;
import dev.selestor1462.cannonencoder.calc.ftl.Vec3d;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;

public final class ClientStateStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path configPath;
    private final Map<String, SessionState> sessions;
    private boolean globalEasyPlace = false;
    private boolean globalSequentialRails = false;
    private boolean globalHighlightsVisible = true;

    public ClientStateStore(Path configPath) {
        this.configPath = Objects.requireNonNull(configPath, "configPath");
        this.sessions = this.load();
    }

    public static ClientStateStore openDefault() {
        Path newPath = FabricLoader.getInstance().getConfigDir().resolve("cannoncalculator.json");
        Path oldPath = FabricLoader.getInstance().getConfigDir().resolve("cannonencoder.json");
        Path legacyPath = FabricLoader.getInstance().getConfigDir().resolve("pigcannonencoder.json");
        if (!Files.exists(newPath)) {
            try {
                if (Files.exists(oldPath)) {
                    Files.copy(oldPath, newPath);
                } else if (Files.exists(legacyPath)) {
                    Files.copy(legacyPath, newPath);
                }
            } catch (Exception ignored) {
            }
        }
        return new ClientStateStore(newPath);
    }

    public boolean globalEasyPlace() {
        return this.globalEasyPlace;
    }

    public boolean globalSequentialRails() {
        return this.globalSequentialRails;
    }

    public boolean globalHighlightsVisible() {
        return this.globalHighlightsVisible;
    }

    public synchronized void setGlobalHighlightsVisible(boolean visible) {
        this.globalHighlightsVisible = visible;
        this.save();
    }

    public synchronized SessionState get(String environmentKey) {
        if (environmentKey == null) {
            return SessionState.empty(this.globalEasyPlace, this.globalSequentialRails, this.globalHighlightsVisible);
        }
        SessionState direct = this.sessions.get(environmentKey);
        if (direct != null) {
            return direct;
        }

        // Case-insensitive key lookup fallback
        for (Map.Entry<String, SessionState> entry : this.sessions.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(environmentKey)) {
                return entry.getValue();
            }
        }

        // Port-insensitive fallback for multiplayer (e.g. "multiplayer:example.com:25565" vs "multiplayer:example.com")
        if (environmentKey.startsWith("multiplayer:")) {
            String bareKey = environmentKey;
            int colonIdx = environmentKey.lastIndexOf(':');
            if (colonIdx > "multiplayer:".length()) {
                bareKey = environmentKey.substring(0, colonIdx);
            }
            for (Map.Entry<String, SessionState> entry : this.sessions.entrySet()) {
                String k = entry.getKey();
                if (k.startsWith("multiplayer:")) {
                    String entryBare = k;
                    int c = k.lastIndexOf(':');
                    if (c > "multiplayer:".length()) {
                        entryBare = k.substring(0, c);
                    }
                    if (bareKey.equalsIgnoreCase(entryBare)) {
                        return entry.getValue();
                    }
                }
            }
        }

        // Migration from legacy "singleplayer:." or "session:default" keys
        SessionState legacyDot = this.sessions.remove("singleplayer:.");
        if (legacyDot != null) {
            this.sessions.put(environmentKey, legacyDot);
            this.save();
            return legacyDot;
        }

        SessionState legacyDefault = this.sessions.remove("session:default");
        if (legacyDefault != null) {
            this.sessions.put(environmentKey, legacyDefault);
            this.save();
            return legacyDefault;
        }

        // If there is only one session in the entire database, adopt it for the current world/server
        if (this.sessions.size() == 1) {
            Map.Entry<String, SessionState> singleEntry = this.sessions.entrySet().iterator().next();
            if (singleEntry.getValue() != null) {
                SessionState candidate = singleEntry.getValue();
                this.sessions.put(environmentKey, candidate);
                this.save();
                return candidate;
            }
        }

        return SessionState.empty(this.globalEasyPlace, this.globalSequentialRails, this.globalHighlightsVisible);
    }

    public synchronized void set(String environmentKey, SessionState state) {
        if (state == null) {
            this.sessions.remove(environmentKey);
        } else {
            this.sessions.put(environmentKey, state);
            this.globalEasyPlace = state.easyPlaceEnabled();
            this.globalSequentialRails = state.sequentialRails();
            this.globalHighlightsVisible = state.highlightsVisible();
        }
        this.save();
    }

    public synchronized void clear(String environmentKey) {
        this.sessions.remove(environmentKey);
        this.save();
    }

    private Map<String, SessionState> load() {
        LinkedHashMap<String, SessionState> loadedSessions = new LinkedHashMap<>();
        if (!Files.exists(this.configPath)) {
            return loadedSessions;
        }
        try (BufferedReader reader = Files.newBufferedReader(this.configPath)) {
            RootJson root = GSON.fromJson(reader, RootJson.class);
            if (root == null) {
                return loadedSessions;
            }
            if (root.globalEasyPlace != null) {
                this.globalEasyPlace = root.globalEasyPlace;
            }
            if (root.globalSequentialRails != null) {
                this.globalSequentialRails = root.globalSequentialRails;
            }
            if (root.globalHighlightsVisible != null) {
                this.globalHighlightsVisible = root.globalHighlightsVisible;
            }
            if (root.sessions != null) {
                for (Map.Entry<String, SessionJson> entry : root.sessions.entrySet()) {
                    if (entry.getValue() == null) continue;
                    loadedSessions.put(entry.getKey(), fromJson(entry.getValue(), this.globalEasyPlace, this.globalSequentialRails, this.globalHighlightsVisible));
                }
            }
            return loadedSessions;
        } catch (Exception exception) {
            PigCannonEncoder.LOGGER.warn("Failed to load state from {}", this.configPath, exception);
        }
        return loadedSessions;
    }

    private void save() {
        try {
            Path parent = this.configPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            RootJson root = new RootJson();
            root.globalEasyPlace = this.globalEasyPlace;
            root.globalSequentialRails = this.globalSequentialRails;
            root.globalHighlightsVisible = this.globalHighlightsVisible;
            for (Map.Entry<String, SessionState> entry : this.sessions.entrySet()) {
                root.sessions.put(entry.getKey(), toJson(entry.getValue()));
            }
            try (BufferedWriter writer = Files.newBufferedWriter(this.configPath)) {
                GSON.toJson(root, writer);
            }
        } catch (Exception exception) {
            PigCannonEncoder.LOGGER.warn("Failed to save state to {}", this.configPath, exception);
        }
    }

    private static SessionState fromJson(SessionJson json, boolean fallbackEasyPlace, boolean fallbackSequentialRails, boolean fallbackHighlightsVisible) {
        AnchorState anchor = null;
        CalculationResult lastCalculation = null;
        if (json.anchor != null) {
            BlockPos panel = (json.anchor.panelX != null && json.anchor.panelY != null && json.anchor.panelZ != null)
                ? new BlockPos(json.anchor.panelX, json.anchor.panelY, json.anchor.panelZ)
                : null;
            anchor = new AnchorState(new BlockPos(json.anchor.x, json.anchor.y, json.anchor.z), json.anchor.dimensionId, json.anchor.templateId, panel);
        }
        if (json.lastCalculation != null) {
            try {
                lastCalculation = fromJson(json.lastCalculation);
            } catch (Exception e) {
                PigCannonEncoder.LOGGER.warn("Failed to load lastCalculation", e);
            }
        }

        boolean easyPlace = json.easyPlaceEnabled != null ? json.easyPlaceEnabled : fallbackEasyPlace;
        boolean sequentialRails = json.sequentialRails != null ? json.sequentialRails : fallbackSequentialRails;
        CannonMode mode = json.cannonMode != null ? json.cannonMode : CannonMode.PIG_CANNON;

        AnchorState ftlAnchor = null;
        if (json.ftlAnchor != null) {
            BlockPos panel = (json.ftlAnchor.panelX != null && json.ftlAnchor.panelY != null && json.ftlAnchor.panelZ != null)
                ? new BlockPos(json.ftlAnchor.panelX, json.ftlAnchor.panelY, json.ftlAnchor.panelZ)
                : null;
            ftlAnchor = new AnchorState(new BlockPos(json.ftlAnchor.x, json.ftlAnchor.y, json.ftlAnchor.z), json.ftlAnchor.dimensionId, json.ftlAnchor.templateId, panel);
        }

        FtlCalculationResult ftlCalc = null;
        if (json.ftlCalculation != null) {
            try {
                ftlCalc = fromFtlJson(json.ftlCalculation);
            } catch (Exception e) {
                PigCannonEncoder.LOGGER.warn("Failed to load ftlCalculation", e);
            }
        }
        BlockPos catcher = null;

        boolean highlights = json.highlightsVisible != null ? json.highlightsVisible : fallbackHighlightsVisible;
        int cannonSize = (json.cannonSize != null && json.cannonSize > 0) ? json.cannonSize : 6688;

        java.util.List<TargetRecord> history = new java.util.ArrayList<>();
        if (json.targetHistory != null) {
            for (TargetRecordJson tr : json.targetHistory) {
                if (tr != null) {
                    history.add(new TargetRecord(
                        (int) tr.destX, (int) tr.destZ, tr.mode != null ? tr.mode : CannonMode.PIG_CANNON,
                        tr.row, tr.col, tr.distance, tr.timestamp, tr.label != null ? tr.label : ""
                    ));
                }
            }
        }

        return new SessionState(
            mode,
            anchor,
            lastCalculation,
            json.selectedRow,
            json.selectedCol,
            easyPlace,
            ftlAnchor,
            ftlCalc,
            json.ftlSelectedRow,
            json.ftlSelectedCol,
            catcher,
            highlights,
            cannonSize,
            history,
            sequentialRails
        );
    }

    private static SessionJson toJson(SessionState state) {
        SessionJson json = new SessionJson();
        json.cannonMode = state.cannonMode();
        json.cannonSize = state.cannonSize();
        if (state.anchor() != null) {
            json.anchor = new AnchorJson();
            json.anchor.x = state.anchor().pos().getX();
            json.anchor.y = state.anchor().pos().getY();
            json.anchor.z = state.anchor().pos().getZ();
            json.anchor.dimensionId = state.anchor().dimensionId();
            json.anchor.templateId = state.anchor().templateId();
            if (state.anchor().panelPos() != null) {
                json.anchor.panelX = state.anchor().panelPos().getX();
                json.anchor.panelY = state.anchor().panelPos().getY();
                json.anchor.panelZ = state.anchor().panelPos().getZ();
            }
        }

        json.selectedRow = state.selectedRow();
        json.selectedCol = state.selectedCol();
        json.easyPlaceEnabled = state.easyPlaceEnabled();
        json.highlightsVisible = state.highlightsVisible();
        json.sequentialRails = state.sequentialRails();

        if (state.ftlAnchor() != null) {
            json.ftlAnchor = new AnchorJson();
            json.ftlAnchor.x = state.ftlAnchor().pos().getX();
            json.ftlAnchor.y = state.ftlAnchor().pos().getY();
            json.ftlAnchor.z = state.ftlAnchor().pos().getZ();
            json.ftlAnchor.dimensionId = state.ftlAnchor().dimensionId();
            json.ftlAnchor.templateId = state.ftlAnchor().templateId();
            if (state.ftlAnchor().panelPos() != null) {
                json.ftlAnchor.panelX = state.ftlAnchor().panelPos().getX();
                json.ftlAnchor.panelY = state.ftlAnchor().panelPos().getY();
                json.ftlAnchor.panelZ = state.ftlAnchor().panelPos().getZ();
            }
        }

        json.ftlSelectedRow = state.ftlSelectedRow();
        json.ftlSelectedCol = state.ftlSelectedCol();

        if (state.lastCalculation() != null) {
            json.lastCalculation = toJson(state.lastCalculation());
        }
        if (state.ftlCalculation() != null) {
            json.ftlCalculation = toFtlJson(state.ftlCalculation());
        }

        if (state.targetHistory() != null && !state.targetHistory().isEmpty()) {
            json.targetHistory = new java.util.ArrayList<>();
            for (TargetRecord tr : state.targetHistory()) {
                TargetRecordJson trJson = new TargetRecordJson();
                trJson.destX = tr.destX();
                trJson.destZ = tr.destZ();
                trJson.mode = tr.mode();
                trJson.row = tr.row();
                trJson.col = tr.col();
                trJson.distance = tr.distance();
                trJson.timestamp = tr.timestamp();
                trJson.label = tr.label();
                json.targetHistory.add(trJson);
            }
        }
        return json;
    }

    private static CalculationResult fromJson(CalculationJson json) {
        return new CalculationResult(
            json.initX, json.initZ, json.destX, json.destZ,
            json.dX, json.dZ, json.landingX, json.landingZ,
            json.xMotion, json.zMotion,
            json.straightAccelFirst, json.straightAccelSecond,
            json.diagonalAccelThird, json.diagonalAccelFourth,
            json.directionCode
        );
    }

    private static CalculationJson toJson(CalculationResult result) {
        CalculationJson json = new CalculationJson();
        json.initX = result.initX();
        json.initZ = result.initZ();
        json.destX = result.destX();
        json.destZ = result.destZ();
        json.dX = result.dX();
        json.dZ = result.dZ();
        json.landingX = result.landingX();
        json.landingZ = result.landingZ();
        json.xMotion = result.xMotion();
        json.zMotion = result.zMotion();
        json.straightAccelFirst = result.straightAccelFirst();
        json.straightAccelSecond = result.straightAccelSecond();
        json.diagonalAccelThird = result.diagonalAccelThird();
        json.diagonalAccelFourth = result.diagonalAccelFourth();
        json.directionCode = result.directionCode();
        return json;
    }

    private static FtlCalculationResult fromFtlJson(FtlCalculationJson json) {
        Vec3d end = json.endPos != null && json.endPos.length == 3 ? new Vec3d(json.endPos[0], json.endPos[1], json.endPos[2]) : Vec3d.ZERO;
        Vec3d simPos = json.simPos != null && json.simPos.length == 3 ? new Vec3d(json.simPos[0], json.simPos[1], json.simPos[2]) : Vec3d.ZERO;
        Vec3d simMot = json.simMotion != null && json.simMotion.length == 3 ? new Vec3d(json.simMotion[0], json.simMotion[1], json.simMotion[2]) : Vec3d.ZERO;
        Vec3d catcher = json.catcherPos != null && json.catcherPos.length == 3 ? new Vec3d(json.catcherPos[0], json.catcherPos[1], json.catcherPos[2]) : Vec3d.ZERO;

        int cannonSize = (json.cannonSize != null && json.cannonSize > 0) ? json.cannonSize : 6688;
        FtlEncodingData enc = FtlEncodingData.compute(
            json.earlyTnt, json.lateTnt, json.upaccelTnt, json.longRange,
            json.direction, json.directionAngle, catcher, cannonSize
        );

        return new FtlCalculationResult(
            json.earlyTnt, json.lateTnt, json.totalTnt, json.distance, json.ticks,
            end, simPos, simMot, json.upaccelTnt, json.longRange,
            json.direction, json.directionAngle, catcher, enc
        );
    }

    private static FtlCalculationJson toFtlJson(FtlCalculationResult result) {
        FtlCalculationJson json = new FtlCalculationJson();
        json.cannonSize = (result.encodingData() != null && result.encodingData().cannonMaxTnt() > 0)
            ? result.encodingData().cannonMaxTnt() : 6688;
        json.earlyTnt = result.earlyTnt();
        json.lateTnt = result.lateTnt();
        json.totalTnt = result.totalTnt();
        json.distance = result.distance();
        json.ticks = result.ticks();
        if (result.endPos() != null) json.endPos = new double[]{result.endPos().x(), result.endPos().y(), result.endPos().z()};
        if (result.simPos() != null) json.simPos = new double[]{result.simPos().x(), result.simPos().y(), result.simPos().z()};
        if (result.simMotion() != null) json.simMotion = new double[]{result.simMotion().x(), result.simMotion().y(), result.simMotion().z()};
        json.upaccelTnt = result.upaccelTnt();
        json.longRange = result.longRange();
        json.direction = result.direction();
        json.directionAngle = result.directionAngle();
        if (result.catcherPos() != null) json.catcherPos = new double[]{result.catcherPos().x(), result.catcherPos().y(), result.catcherPos().z()};
        return json;
    }

    private static final class RootJson {
        private Boolean globalProtection;
        private Boolean globalEasyPlace;
        private Boolean globalSequentialRails;
        private Boolean globalHighlightsVisible;
        private Map<String, SessionJson> sessions = new LinkedHashMap<>();
    }

    private static final class SessionJson {
        private CannonMode cannonMode;
        private AnchorJson anchor;
        private CalculationJson lastCalculation;
        private int selectedRow;
        private int selectedCol;
        private Boolean protectionEnabled;
        private Boolean easyPlaceEnabled;
        private Boolean highlightsVisible;
        private Boolean sequentialRails;
        private Integer cannonSize;

        private AnchorJson ftlAnchor;
        private FtlCalculationJson ftlCalculation;
        private int ftlSelectedRow;
        private int ftlSelectedCol;
        private Integer catcherX;
        private Integer catcherY;
        private Integer catcherZ;
        private java.util.List<TargetRecordJson> targetHistory;
    }

    private static final class TargetRecordJson {
        private int destX;
        private int destZ;
        private CannonMode mode;
        private int row;
        private int col;
        private double distance;
        private long timestamp;
        private String label;
    }

    private static final class AnchorJson {
        private int x;
        private int y;
        private int z;
        private String dimensionId;
        private String templateId;
        private Integer panelX;
        private Integer panelY;
        private Integer panelZ;
    }

    private static final class CalculationJson {
        private int initX;
        private int initZ;
        private int destX;
        private int destZ;
        private int dX;
        private int dZ;
        private int landingX;
        private int landingZ;
        private double xMotion;
        private double zMotion;
        private int straightAccelFirst;
        private int straightAccelSecond;
        private int diagonalAccelThird;
        private int diagonalAccelFourth;
        private int directionCode;
    }

    private static final class FtlCalculationJson {
        private int earlyTnt;
        private int lateTnt;
        private int totalTnt;
        private double distance;
        private int ticks;
        private double[] endPos;
        private double[] simPos;
        private double[] simMotion;
        private int upaccelTnt;
        private boolean longRange;
        private int direction;
        private int directionAngle;
        private double[] catcherPos;
        private Integer cannonSize;
    }
}
