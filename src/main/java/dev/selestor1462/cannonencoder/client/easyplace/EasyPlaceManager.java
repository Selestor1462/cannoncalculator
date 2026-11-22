package dev.selestor1462.cannonencoder.client.easyplace;

import dev.selestor1462.cannonencoder.client.ClientSessionContext;
import dev.selestor1462.cannonencoder.client.HighlightManager;
import dev.selestor1462.cannonencoder.client.WorldHighlightRenderer;
import dev.selestor1462.cannonencoder.client.picker.BlockPickerManager;
import dev.selestor1462.cannonencoder.template.HighlightPlan;
import dev.selestor1462.cannonencoder.template.HighlightType;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.ObserverBlock;
import net.minecraft.world.level.block.SupportType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EasyPlaceManager {
    private final HighlightManager highlightManager;
    private final BlockPickerManager pickerManager;
    private boolean enabled = false;
    private boolean placementRestriction = true;

    private static final class PositionCache {
        final BlockPos pos;
        final long expireNano;

        PositionCache(BlockPos pos, long expireNano) {
            this.pos = pos;
            this.expireNano = expireNano;
        }

        boolean hasExpired(long now) {
            return now >= this.expireNano;
        }
    }

    private final List<PositionCache> positionCache = new ArrayList<>();
    private long lastPlaceTimeNano = 0L;
    private long lastCancelMessageTimeNano = 0L;
    private boolean isPlacing = false;
    private static volatile boolean isPlacingActive = false;
    // 200ms per block position cooldown (matches Litematica EASY_PLACE_POSITION_CACHE)
    private static final long COOLDOWN_NANO = 200_000_000L;
    // 25ms throttle between actions for fast and responsive placement
    private static final long THROTTLE_NANO = 25_000_000L;

    public EasyPlaceManager(HighlightManager highlightManager, BlockPickerManager pickerManager) {
        this.highlightManager = highlightManager;
        this.pickerManager = pickerManager;
    }

    private void sendCancelMessage(Player player) {
        if (player == null) return;
        long now = System.nanoTime();
        if (now - this.lastCancelMessageTimeNano > 400_000_000L) {
            this.lastCancelMessageTimeNano = now;
            player.sendOverlayMessage(Component.literal("§cAction prevented by cannon encoder easy place mode"));
        }
    }

    public void register() {
        ClientTickEvents.START_CLIENT_TICK.register(this::onClientTick);
        UseBlockCallback.EVENT.register(this::onUseBlock);
    }

    public static boolean isCurrentlyPlacing() {
        return isPlacingActive;
    }

    public static boolean isPlaceableHighlight(HighlightType type) {
        return type != HighlightType.SELECTED_LAMP
            && type != HighlightType.STAINED_GLASS;
    }

    public boolean isEnabled() {
        return this.enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean toggle() {
        this.enabled = !this.enabled;
        return this.enabled;
    }

    public boolean isPlacementRestriction() {
        return this.placementRestriction;
    }

    public void setPlacementRestriction(boolean placementRestriction) {
        this.placementRestriction = placementRestriction;
    }

    private void onClientTick(Minecraft client) {
        if (!this.isEnabled() || client.player == null || client.level == null || client.gameMode == null || client.gui.screen() != null) {
            return;
        }

        // Mod EasyPlace is completely off / idle if no encoding is currently rendering
        Optional<ClientSessionContext> session = ClientSessionContext.resolve(client);
        List<HighlightedBlock> blocks = session.map(this.highlightManager::visibleHighlights)
            .filter(b -> !b.isEmpty())
            .orElseGet(() -> (this.highlightManager.activePlan() != null && this.highlightManager.isVisible()) ? this.highlightManager.activePlan().blocks() : List.of());
        if (blocks.isEmpty()) {
            return;
        }

        if (client.options.keyUse.isDown()) {
            boolean placed = tryEasyPlace(client);
            if (placed) {
                while (client.options.keyUse.consumeClick()) {
                    // Drain clicks to prevent duplicate vanilla interactions
                }
            }
        }
    }

    private InteractionResult onUseBlock(Player player, Level world, InteractionHand hand, BlockHitResult hitResult) {
        if (this.isPlacing) {
            return InteractionResult.PASS;
        }

        if (!world.isClientSide() || !this.isEnabled() || player == null || hitResult == null) {
            return InteractionResult.PASS;
        }

        // If EasyPlace recently placed a block, cancel vanilla's duplicate right-click event
        long now = System.nanoTime();
        if (now - this.lastPlaceTimeNano < 100_000_000L) {
            return InteractionResult.FAIL;
        }

        if (!this.isPlacementRestriction()) {
            return InteractionResult.PASS;
        }

        // 1. If player is not holding a block in this hand, allow normal interaction
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem) && !held.is(Items.POWDER_SNOW_BUCKET)) {
            return InteractionResult.PASS;
        }

        // 2. Allow interacting with interactive blocks (NoteBlock, Diode, Button, Lever, Chest, etc.) when not sneaking
        BlockPos clickedPos = hitResult.getBlockPos();
        BlockState clickedState = world.getBlockState(clickedPos);
        if (!player.isShiftKeyDown() && isInteractiveBlock(clickedState)) {
            return InteractionResult.PASS;
        }

        // 3. Resolve active encoding highlights
        Minecraft client = Minecraft.getInstance();
        Optional<ClientSessionContext> session = ClientSessionContext.resolve(client);
        List<HighlightedBlock> visible = session.map(this.highlightManager::visibleHighlights)
            .filter(b -> !b.isEmpty())
            .orElseGet(() -> (this.highlightManager.activePlan() != null && this.highlightManager.isVisible()) ? this.highlightManager.activePlan().blocks() : List.of());
        if (visible.isEmpty()) {
            return InteractionResult.PASS;
        }

        // 4. Calculate where the block would be placed
        BlockPos targetPlacePos = clickedState.canBeReplaced() ? clickedPos : clickedPos.relative(hitResult.getDirection());

        // 5. Check if targetPlacePos matches any planned ghost block
        HighlightedBlock matchingPlan = null;
        for (HighlightedBlock hb : visible) {
            BlockPos pos = WorldHighlightRenderer.resolveEffectivePos(world, hb);
            if (pos.equals(targetPlacePos)) {
                matchingPlan = hb;
                break;
            }
        }

        // If placing into an unreserved space (e.g. extra observer, custom wiring, support block),
        // allow vanilla placement! Never restrict or cancel blocks outside designated ghost block positions.
        if (matchingPlan == null) {
            return InteractionResult.PASS;
        }

        // If the designated ghost block is already placed and satisfied, allow interaction
        if (WorldHighlightRenderer.isBlockSatisfied(world, matchingPlan)) {
            return InteractionResult.PASS;
        }

        // The position is an unsatisfied ghost block!
        // Verify held item matches required item
        boolean itemMatches;
        if (matchingPlan.type() == HighlightType.CATCHER_BLOCK) {
            Item redGlass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();
            itemMatches = held.is(redGlass) || held.is(Items.HONEY_BLOCK) || held.is(Items.SLIME_BLOCK);
        } else {
            Item req = BlockPickerManager.resolveRequiredItem(matchingPlan);
            itemMatches = (req != null && req != Items.AIR && held.is(req));
        }

        if (!itemMatches) {
            // Placement restriction: prevent placing wrong block type into ghost outline
            sendCancelMessage(player);
            return InteractionResult.FAIL;
        }

        // Held item is correct for this ghost block. Try placing via EasyPlace to ensure accurate orientation/protocol.
        if (tryEasyPlace(client)) {
            return InteractionResult.SUCCESS;
        }

        // If EasyPlace could not place right now (e.g. cooldown or sequential rails prerequisite missing),
        // cancel vanilla placement so misaligned blocks are not placed.
        return InteractionResult.FAIL;
    }

    public boolean tryEasyPlace(Minecraft client) {
        long now = System.nanoTime();
        if (now - this.lastPlaceTimeNano < THROTTLE_NANO) {
            return false;
        }

        LocalPlayer player = client.player;
        ClientLevel world = client.level;
        MultiPlayerGameMode gameMode = client.gameMode;
        if (player == null || world == null || gameMode == null) {
            return false;
        }

        cleanExpiredPositions(now);

        Optional<ClientSessionContext> session = ClientSessionContext.resolve(client);
        List<HighlightedBlock> blocks = session.map(this.highlightManager::visibleHighlights)
            .filter(b -> !b.isEmpty())
            .orElseGet(() -> (this.highlightManager.activePlan() != null && this.highlightManager.isVisible()) ? this.highlightManager.activePlan().blocks() : List.of());
        if (blocks.isEmpty()) return false;

        // If crosshair is aiming directly at a placed Note Block and player is not sneaking, let them click it
        if (client.hitResult instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            if (world.getBlockState(bhr.getBlockPos()).is(Blocks.NOTE_BLOCK) && !player.isShiftKeyDown()) {
                return false;
            }
        }

        double maxReach = player.blockInteractionRange() + 1.0;
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(maxReach));

        EasyPlaceCandidate candidate = findBestCandidate(world, blocks, eye, end, client.hitResult, client);
        if (candidate == null) {
            return false;
        }

        HighlightedBlock target = candidate.block();
        BlockPos targetPos = candidate.pos();
        BlockHitResult clickHit = candidate.clickHit();
        Item requiredItem = candidate.requiredItem();

        // Auto-select or pick required item
        boolean hasItem;
        if (target.type() == HighlightType.CATCHER_BLOCK) {
            hasItem = BlockPickerManager.selectOrPickCatcherItem(client, false);
            requiredItem = player.getMainHandItem().getItem();
        } else {
            hasItem = BlockPickerManager.selectOrPickItem(client, requiredItem, false);
        }

        if (!hasItem) {
            player.sendOverlayMessage(Component.literal("§e[EasyPlace] Missing §f" + requiredItem.getName(requiredItem.getDefaultInstance()).getString() + "§e in inventory!"));
            return false;
        }

        // Verify carried item matches required item
        ItemStack held = player.getMainHandItem();
        if (target.type() == HighlightType.CATCHER_BLOCK) {
            Item redGlass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();
            if (!held.is(redGlass) && !held.is(Items.HONEY_BLOCK) && !held.is(Items.SLIME_BLOCK)) {
                return false;
            }
        } else if (!held.is(requiredItem)) {
            return false;
        }

        // Make sure the server knows our carried hotbar slot immediately
        if (player.connection != null) {
            player.connection.send(new ServerboundSetCarriedItemPacket(player.getInventory().getSelectedSlot()));
        }

        Minecraft mc = Minecraft.getInstance();
        boolean isSingleplayer = mc.isLocalServer() && mc.getSingleplayerServer() != null;

        BlockState targetState = WorldHighlightRenderer.resolveBlockState(target);

        // Apply Accurate Block Placement protocol (Servux V3 / Carpet V2)
        Vec3 finalHitVec = applyServuxProtocol(targetPos, targetState, clickHit.getLocation());

        BlockHitResult finalHit = new BlockHitResult(finalHitVec, clickHit.getDirection(), clickHit.getBlockPos(), false);

        // Fake sneak state if placing against interactive blocks (NoteBlock, Repeater, etc.)
        boolean wasSneaking = player.isShiftKeyDown();
        boolean needSneak = !wasSneaking && isInteractiveBlock(world.getBlockState(clickHit.getBlockPos()));
        if (needSneak) {
            player.setShiftKeyDown(true);
        }

        this.isPlacing = true;
        isPlacingActive = true;
        InteractionResult result;
        try {
            result = gameMode.useItemOn(player, InteractionHand.MAIN_HAND, finalHit);
        } finally {
            this.isPlacing = false;
            isPlacingActive = false;
            if (needSneak) {
                player.setShiftKeyDown(wasSneaking);
            }
        }

        if (result.consumesAction()) {
            cachePosition(targetPos);
            this.lastPlaceTimeNano = System.nanoTime();
            player.swing(InteractionHand.MAIN_HAND);

            // In singleplayer, force the exact target blockstate directly on the integrated server
            if (isSingleplayer) {
                net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
                if (server != null) {
                    server.execute(() -> {
                        net.minecraft.server.level.ServerLevel serverWorld = server.getLevel(world.dimension());
                        if (serverWorld != null) {
                            serverWorld.setBlock(targetPos, targetState, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
                        }
                    });
                }
                world.setBlock(targetPos, targetState, net.minecraft.world.level.block.Block.UPDATE_CLIENTS | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
            }

            return true;
        }

        return false;
    }

    private record EasyPlaceCandidate(
        HighlightedBlock block,
        BlockPos pos,
        double distSq,
        BlockHitResult clickHit,
        Item requiredItem
    ) {}

    private EasyPlaceCandidate findBestCandidate(
        ClientLevel world,
        List<HighlightedBlock> blocks,
        Vec3 eye,
        Vec3 end,
        HitResult vanillaHit,
        Minecraft client
    ) {
        double maxAllowedDistSq = (vanillaHit != null && vanillaHit.getType() == HitResult.Type.BLOCK)
            ? eye.distanceToSqr(vanillaHit.getLocation()) + 0.5
            : Double.MAX_VALUE;

        java.util.Map<BlockPos, EasyPlaceCandidate> candidateMap = new java.util.HashMap<>();

        for (HighlightedBlock hb : blocks) {
            if (!isPlaceableHighlight(hb.type()) || WorldHighlightRenderer.isBlockSatisfied(world, hb) || (this.highlightManager.isSequentialRails() && WorldHighlightRenderer.isRailSupportingBlockMissing(world, hb, blocks))) {
                continue;
            }
            BlockPos hbPos = WorldHighlightRenderer.resolveEffectivePos(world, hb);
            if (isPositionCached(hbPos)) {
                continue;
            }

            double distSq = Double.MAX_VALUE;
            boolean matched = false;

            // 1. Ray intersection with ghost block bounding box
            AABB box;
            if (hb.type() == HighlightType.RAIL || hb.type() == HighlightType.GRAY_CARPET) {
                box = new AABB(
                    hbPos.getX(), hbPos.getY(), hbPos.getZ(),
                    hbPos.getX() + 1.0, hbPos.getY() + 0.18, hbPos.getZ() + 1.0
                ).inflate(0.04);
            } else {
                box = new AABB(hbPos).inflate(0.06);
            }
            Optional<Vec3> clip = box.clip(eye, end);
            if (clip.isPresent()) {
                double d = eye.distanceToSqr(clip.get());
                if (d <= maxAllowedDistSq) {
                    distSq = d;
                    matched = true;
                }
            }

            // 2. Vanilla crosshair aiming directly at supporting surface that places into hbPos
            if (vanillaHit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
                BlockPos clicked = bhr.getBlockPos();
                BlockPos placePos = world.getBlockState(clicked).canBeReplaced() ? clicked : clicked.relative(bhr.getDirection());
                if (hbPos.equals(placePos)) {
                    double d = eye.distanceToSqr(bhr.getLocation());
                    if (d < distSq) {
                        distSq = d;
                    }
                    matched = true;
                }
            }

            if (matched) {
                BlockHitResult clickHit = resolveClickHitResult(world, hbPos, vanillaHit, hb);
                if (clickHit != null) {
                    Item req = BlockPickerManager.resolveRequiredItem(hb);
                    if (req != null && req != Items.AIR) {
                        EasyPlaceCandidate existing = candidateMap.get(hbPos);
                        if (existing == null || distSq < existing.distSq()) {
                            candidateMap.put(hbPos, new EasyPlaceCandidate(hb, hbPos, distSq, clickHit, req));
                        }
                    }
                }
            }
        }

        if (candidateMap.isEmpty()) {
            return null;
        }

        // Sort candidates strictly from CLOSEST to FURTHEST (first in view is placed first, matching Litematica default)
        List<EasyPlaceCandidate> sorted = new ArrayList<>(candidateMap.values());
        sorted.sort(java.util.Comparator.comparingDouble(EasyPlaceCandidate::distSq));

        // Prioritize the closest block that the player has items for; if none, return the closest so error message can show
        for (EasyPlaceCandidate cand : sorted) {
            boolean hasItem = (cand.block.type() == HighlightType.CATCHER_BLOCK)
                ? BlockPickerManager.hasCatcherItem(client)
                : BlockPickerManager.hasItemInInventory(client, cand.requiredItem);
            if (hasItem) {
                return cand;
            }
        }

        return sorted.get(0);
    }

    private BlockHitResult resolveClickHitResult(
        ClientLevel world,
        BlockPos targetPos,
        HitResult vanillaHit,
        HighlightedBlock target
    ) {
        BlockState targetState = world.getBlockState(targetPos);
        if (!targetState.canBeReplaced()) {
            return null;
        }

        // Rails, redstone dust, twisting vines, and carpet strictly require clicking the top face of the supporting surface below
        if (target != null && (target.type() == HighlightType.RAIL
            || target.type() == HighlightType.REDSTONE_DUST
            || target.type() == HighlightType.GRAY_CARPET
            || target.type() == HighlightType.TWISTING_VINES)) {
            BlockPos below = targetPos.below();
            BlockState belowState = world.getBlockState(below);
            if (belowState.canBeReplaced() || belowState.isAir() || !isValidPlacementSupport(world, below, Direction.UP)) {
                return null; // Not ready yet! (Wait for supporting block below to be placed)
            }
            Vec3 hitPos = new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
            return new BlockHitResult(hitPos, Direction.UP, below, false);
        }

        // Case 1: If crosshair is aiming directly at a supporting block surface that places into targetPos
        if (vanillaHit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            BlockPos clicked = bhr.getBlockPos();
            if (clicked.relative(bhr.getDirection()).equals(targetPos) && isValidPlacementSupport(world, clicked, bhr.getDirection())) {
                return bhr;
            }
        }

        // Case 2: Vertical support directly below targetPos (for observers, glass, powder snow, catcher)
        BlockPos below = targetPos.below();
        if (isValidPlacementSupport(world, below, Direction.UP)) {
            Vec3 hitPos = new Vec3(below.getX() + 0.5, below.getY() + 1.0, below.getZ() + 0.5);
            return new BlockHitResult(hitPos, Direction.UP, below, false);
        }

        // Case 3: Horizontal adjacent neighbors (for glass bridge and observers over empty space)
        Direction[] horizontal = new Direction[]{
            Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH
        };
        for (Direction dir : horizontal) {
            BlockPos neighbor = targetPos.relative(dir);
            Direction clickFace = dir.getOpposite(); // Face of neighbor pointing into targetPos
            if (isValidPlacementSupport(world, neighbor, clickFace)) {
                double hx = neighbor.getX() + 0.5 + clickFace.getStepX() * 0.5;
                double hy = neighbor.getY() + 0.5 + clickFace.getStepY() * 0.5;
                double hz = neighbor.getZ() + 0.5 + clickFace.getStepZ() * 0.5;
                return new BlockHitResult(new Vec3(hx, hy, hz), clickFace, neighbor, false);
            }
        }

        // Case 4: Block above targetPos pointing down
        BlockPos above = targetPos.above();
        if (isValidPlacementSupport(world, above, Direction.DOWN)) {
            Vec3 hitPos = new Vec3(above.getX() + 0.5, above.getY(), above.getZ() + 0.5);
            return new BlockHitResult(hitPos, Direction.DOWN, above, false);
        }

        // Case 5: Catcher block fallback (can be placed in mid-air directly at the target position if no supporting neighbors exist)
        if (target != null && target.type() == HighlightType.CATCHER_BLOCK) {
            return new BlockHitResult(Vec3.atCenterOf(targetPos), Direction.UP, targetPos, false);
        }

        return null;
    }

    private static boolean isValidPlacementSupport(ClientLevel world, BlockPos pos, Direction face) {
        BlockState state = world.getBlockState(pos);
        if (state == null || state.isAir() || state.canBeReplaced()) return false;
        if (face != Direction.UP) {
            if (state.getBlock() instanceof BaseRailBlock
                || state.is(Blocks.REDSTONE_WIRE)
                || state.getBlock() instanceof net.minecraft.world.level.block.CarpetBlock) {
                return false;
            }
        }
        return true;
    }

    private static boolean isInteractiveBlock(BlockState state) {
        if (state == null) return false;
        return state.getBlock() instanceof NoteBlock
            || state.getBlock() instanceof DiodeBlock
            || state.getBlock() instanceof ButtonBlock
            || state.getBlock() instanceof LeverBlock
            || state.getBlock() instanceof TrapDoorBlock
            || state.getBlock() instanceof ChestBlock;
    }

    private boolean isPositionCached(BlockPos pos) {
        long now = System.nanoTime();
        for (PositionCache pc : this.positionCache) {
            if (!pc.hasExpired(now) && pc.pos.equals(pos)) {
                return true;
            }
        }
        return false;
    }

    private void cachePosition(BlockPos pos) {
        this.positionCache.add(new PositionCache(pos, System.nanoTime() + COOLDOWN_NANO));
    }

    private void cleanExpiredPositions(long now) {
        this.positionCache.removeIf(pc -> pc.hasExpired(now));
    }

    private static HighlightedBlock findBlockInPlan(HighlightPlan plan, BlockPos pos) {
        for (HighlightedBlock hb : plan.blocks()) {
            if (hb.pos().equals(pos)) {
                return hb;
            }
        }
        return null;
    }

    private static boolean litematicaReflectionInitialized = false;
    private static Object litematicaEasyPlaceConfig = null;
    private static java.lang.reflect.Method litematicaGetBoolean = null;
    private static java.lang.reflect.Method litematicaSetBoolean = null;
    private static Object litematicaRestrictionConfig = null;
    private static java.lang.reflect.Method litematicaGetRestrictionBoolean = null;

    private static void initLitematica() {
        if (litematicaReflectionInitialized) return;
        litematicaReflectionInitialized = true;
        try {
            Class<?> genericClass = Class.forName("fi.dy.masa.litematica.config.Configs$Generic");
            java.lang.reflect.Field field = null;
            try {
                field = genericClass.getField("EASY_PLACE_MODE");
            } catch (NoSuchFieldException e) {
                try {
                    field = genericClass.getField("EASY_PLACE");
                } catch (NoSuchFieldException ignored) {}
            }
            if (field != null) {
                litematicaEasyPlaceConfig = field.get(null);
                if (litematicaEasyPlaceConfig != null) {
                    litematicaGetBoolean = litematicaEasyPlaceConfig.getClass().getMethod("getBooleanValue");
                    litematicaSetBoolean = litematicaEasyPlaceConfig.getClass().getMethod("setBooleanValue", boolean.class);
                }
            }

            try {
                java.lang.reflect.Field rField = genericClass.getField("PLACEMENT_RESTRICTION");
                litematicaRestrictionConfig = rField.get(null);
                if (litematicaRestrictionConfig != null) {
                    litematicaGetRestrictionBoolean = litematicaRestrictionConfig.getClass().getMethod("getBooleanValue");
                }
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {
        }
    }

    public static Boolean getLitematicaEasyPlace() {
        initLitematica();
        if (litematicaEasyPlaceConfig != null && litematicaGetBoolean != null) {
            try {
                return (Boolean) litematicaGetBoolean.invoke(litematicaEasyPlaceConfig);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    public static void setLitematicaEasyPlace(boolean val) {
        initLitematica();
        if (litematicaEasyPlaceConfig != null && litematicaSetBoolean != null) {
            try {
                litematicaSetBoolean.invoke(litematicaEasyPlaceConfig, val);
            } catch (Throwable ignored) {}
        }
    }

    public static Vec3 applyServuxProtocol(BlockPos targetPos, BlockState state, Vec3 hitVec) {
        // 1. Try Litematica's built-in V3 protocol (Servux V3)
        for (String className : new String[]{"fi.dy.masa.litematica.util.WorldUtils", "fi.dy.masa.litematica.util.EasyPlaceUtils"}) {
            try {
                Class<?> epClass = Class.forName(className);
                java.lang.reflect.Method method = epClass.getMethod("applyPlacementProtocolV3", BlockPos.class, BlockState.class, Vec3.class);
                Vec3 res = (Vec3) method.invoke(null, targetPos, state, hitVec);
                if (res != null) {
                    return res;
                }
            } catch (Throwable ignored) {}
        }

        // 2. Try Litematica's Carpet protocol (V2)
        for (String className : new String[]{"fi.dy.masa.litematica.util.WorldUtils", "fi.dy.masa.litematica.util.EasyPlaceUtils"}) {
            try {
                Class<?> epClass = Class.forName(className);
                java.lang.reflect.Method method = epClass.getMethod("applyCarpetProtocolHitVec", BlockPos.class, BlockState.class, Vec3.class);
                Vec3 res = (Vec3) method.invoke(null, targetPos, state, hitVec);
                if (res != null) {
                    return res;
                }
            } catch (Throwable ignored) {}
        }

        // 3. Fallback direct computation for Servux / Carpet accurate placement
        for (net.minecraft.world.level.block.state.properties.Property<?> prop : state.getProperties()) {
            Comparable<?> val = state.getValue(prop);
            if (val instanceof Direction facing) {
                int dataValue = facing.get3DDataValue();
                return new Vec3(hitVec.x + 2.0 + (dataValue * 2.0), hitVec.y, hitVec.z);
            }
        }
        return hitVec;
    }
}
