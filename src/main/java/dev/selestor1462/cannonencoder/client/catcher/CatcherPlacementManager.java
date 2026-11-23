package dev.selestor1462.cannonencoder.client.catcher;

import dev.selestor1462.cannonencoder.client.ClientSessionContext;
import dev.selestor1462.cannonencoder.state.ClientStateStore;
import dev.selestor1462.cannonencoder.state.SessionState;
import java.util.Optional;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class CatcherPlacementManager {
    private final ClientStateStore stateStore;

    public CatcherPlacementManager(ClientStateStore stateStore) {
        this.stateStore = stateStore;
    }

    public void register() {
        UseItemCallback.EVENT.register(this::handleUseItem);
    }

    private InteractionResult handleUseItem(Player player, Level world, InteractionHand hand) {
        if (!world.isClientSide()) {
            return InteractionResult.PASS;
        }

        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem)) {
            return InteractionResult.PASS;
        }

        Minecraft client = Minecraft.getInstance();
        Optional<ClientSessionContext> session = ClientSessionContext.resolve(client);
        if (session.isEmpty()) {
            return InteractionResult.PASS;
        }

        SessionState state = this.stateStore.get(session.get().environmentKey());
        if (!state.easyPlaceEnabled()) {
            return InteractionResult.PASS;
        }

        BlockPos catcherPos = state.activeCatcherPos();
        if (catcherPos == null) {
            return InteractionResult.PASS;
        }

        // Raycast check: ONLY when the player is actually aiming directly at the catcher block ghost!
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 look = player.getViewVector(1.0f);
        Vec3 reach = eye.add(look.scale(6.5));
        AABB box = new AABB(catcherPos).inflate(0.1);

        Optional<Vec3> hitOpt = box.clip(eye, reach);
        if (!hitOpt.isPresent()) {
            return InteractionResult.PASS;
        }

        // Auto-select or pick Red Stained Glass (or honey/slime)
        net.minecraft.world.item.Item redGlass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();
        held = player.getItemInHand(hand);
        if (!held.is(redGlass)
            && !held.is(net.minecraft.world.item.Items.HONEY_BLOCK)
            && !held.is(net.minecraft.world.item.Items.SLIME_BLOCK)) {
            boolean picked = dev.selestor1462.cannonencoder.client.picker.BlockPickerManager.selectOrPickCatcherItem(client, true);
            if (!picked) {
                player.sendOverlayMessage(Component.literal("§e[FTL] Missing §fRed Stained Glass§e in inventory to place catcher!"));
                return InteractionResult.FAIL;
            }
            held = player.getItemInHand(hand);
        }

        Vec3 hitVec = hitOpt.get();
        placeAt(client, player, hand, catcherPos, hitVec);
        return InteractionResult.SUCCESS;
    }

    public static boolean placeNow(Minecraft client, Player player, BlockPos catcherPos) {
        if (client == null || player == null || catcherPos == null || client.gameMode == null) {
            return false;
        }

        InteractionHand hand = InteractionHand.MAIN_HAND;
        ItemStack held = player.getItemInHand(hand);
        if (!(held.getItem() instanceof BlockItem)) {
            hand = InteractionHand.OFF_HAND;
            held = player.getItemInHand(hand);
        }

        if (!(held.getItem() instanceof BlockItem)) {
            dev.selestor1462.cannonencoder.client.picker.BlockPickerManager.selectOrPickCatcherItem(client, false);
            hand = InteractionHand.MAIN_HAND;
            held = player.getItemInHand(hand);
        }

        if (!(held.getItem() instanceof BlockItem)) {
            player.sendOverlayMessage(Component.literal("§c[FTL] Missing Red Stained Glass in inventory to place the catcher!"));
            return false;
        }

        placeAt(client, player, hand, catcherPos, Vec3.atCenterOf(catcherPos));
        return true;
    }

    private static void placeAt(Minecraft client, Player player, InteractionHand hand, BlockPos pos, Vec3 hitVec) {
        if (client.gameMode == null || client.level == null) return;
        if (player instanceof net.minecraft.client.player.LocalPlayer lp) {
            Level world = client.level;
            BlockState targetState = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().defaultBlockState();
            net.minecraft.world.item.Item redGlass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();

            // Check if there is an adjacent existing solid block to place against
            Direction supportDir = null;
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = pos.relative(dir);
                BlockState nState = world.getBlockState(neighbor);
                if (!nState.isAir() && !nState.canBeReplaced()) {
                    supportDir = dir.getOpposite(); // The face of neighbor pointing towards pos
                    break;
                }
            }

            BlockPos clickPos = (supportDir != null) ? pos.relative(supportDir.getOpposite()) : pos;
            Direction clickFace = (supportDir != null) ? supportDir : Direction.UP;
            Vec3 clickLocation = (supportDir != null) ? Vec3.atCenterOf(clickPos) : hitVec;

            // Apply Servux / Carpet Accurate Block Placement protocol
            Vec3 finalHitVec = dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager.applyServuxProtocol(pos, targetState, clickLocation);
            BlockHitResult hit = new BlockHitResult(finalHitVec, clickFace, clickPos, false);
            client.gameMode.useItemOn(lp, hand, hit);
            player.swing(hand);
            player.sendOverlayMessage(Component.literal("§a[FTL] Placed catcher block at " + pos.toShortString() + "!"));
        }
    }
}
