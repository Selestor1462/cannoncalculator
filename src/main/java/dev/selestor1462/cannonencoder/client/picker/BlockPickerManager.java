package dev.selestor1462.cannonencoder.client.picker;

import dev.selestor1462.cannonencoder.client.ClientSessionContext;
import dev.selestor1462.cannonencoder.client.HighlightManager;
import dev.selestor1462.cannonencoder.client.WorldHighlightRenderer;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BlockPickerManager {
    private final HighlightManager highlightManager;
    private boolean wasPickDown = false;

    public BlockPickerManager(HighlightManager highlightManager) {
        this.highlightManager = highlightManager;
    }

    public void register() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft client) {
        if (client.player == null || client.level == null || client.gameMode == null) {
            return;
        }

        boolean isDown = client.options.keyPickItem.isDown();
        if (isDown && !this.wasPickDown) {
            HighlightedBlock target = getTargetedHighlightedBlock(client, this.highlightManager, 5.5);
            if (target != null) {
                boolean handled = tryPickHighlightedBlock(client);
                if (handled) {
                    while (client.options.keyPickItem.consumeClick()) {
                        // Consumed so vanilla doesn't pick world block behind the ghost block
                    }
                }
            }
        }
        this.wasPickDown = isDown;
    }

    public boolean tryPickHighlightedBlock(Minecraft client) {
        HighlightedBlock targeted = getTargetedHighlightedBlock(client, this.highlightManager, 5.5);
        if (targeted == null) {
            return false;
        }

        Item requiredItem = resolveRequiredItem(targeted);
        if (requiredItem == null || requiredItem == Items.AIR) {
            return false;
        }

        boolean picked = selectOrPickItem(client, requiredItem, true);
        if (!picked && client.player != null) {
            client.player.sendOverlayMessage(
                Component.literal("§e[Pick Block] Missing §f" + requiredItem.getName(requiredItem.getDefaultInstance()).getString() + "§e in inventory!")
            );
        }
        return picked;
    }

    public static boolean selectOrPickItem(Minecraft client, Item requiredItem, boolean playFeedback) {
        if (client.player == null || client.gameMode == null || requiredItem == null || requiredItem == Items.AIR) {
            return false;
        }
        Inventory inv = client.player.getInventory();

        // 1. Check if already holding it in main hand
        if (client.player.getMainHandItem().is(requiredItem)) {
            return true;
        }

        // 2. Search hotbar (slots 0..8)
        for (int i = 0; i < 9; i++) {
            if (inv.getItem(i).is(requiredItem)) {
                inv.setSelectedSlot(i);
                if (playFeedback) {
                    playPickSound(client);
                    showPickFeedback(client, requiredItem);
                }
                return true;
            }
        }

        // 3. Search main inventory (slots 9..35)
        for (int i = 9; i < 36; i++) {
            if (inv.getItem(i).is(requiredItem)) {
                inv.pickSlot(i);
                if (playFeedback) {
                    playPickSound(client);
                    showPickFeedback(client, requiredItem);
                }
                return true;
            }
        }

        // 4. In Creative mode, grant the item directly
        if (client.player.isCreative()) {
            ItemStack stack = new ItemStack(requiredItem);
            if (!client.player.getMainHandItem().isEmpty() && !client.player.getMainHandItem().is(requiredItem)) {
                for (int i = 0; i < 9; i++) {
                    if (inv.getItem(i).isEmpty()) {
                        inv.setSelectedSlot(i);
                        break;
                    }
                }
            }
            inv.setItem(inv.getSelectedSlot(), stack);
            client.gameMode.handleCreativeModeItemAdd(stack, 36 + inv.getSelectedSlot());
            if (playFeedback) {
                playPickSound(client);
                showPickFeedback(client, requiredItem);
            }
            return true;
        }

        return false;
    }

    public static boolean hasItemInInventory(Minecraft client, Item item) {
        if (client.player == null) return false;
        if (client.player.isCreative()) return true;
        Inventory inv = client.player.getInventory();
        for (int i = 0; i < 36; i++) {
            if (inv.getItem(i).is(item)) return true;
        }
        return false;
    }

    public static boolean hasCatcherItem(Minecraft client) {
        if (client.player == null) return false;
        if (client.player.isCreative()) return true;
        Item redGlass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();
        Item[] catcherItems = new Item[]{redGlass, Items.HONEY_BLOCK, Items.SLIME_BLOCK};
        for (Item item : catcherItems) {
            if (hasItemInInventory(client, item)) return true;
        }
        return false;
    }

    public static boolean selectOrPickCatcherItem(Minecraft client, boolean playFeedback) {
        Item redGlass = net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();
        Item[] catcherItems = new Item[]{redGlass, Items.HONEY_BLOCK, Items.SLIME_BLOCK};
        for (Item item : catcherItems) {
            if (hasItemInInventory(client, item)) {
                return selectOrPickItem(client, item, playFeedback);
            }
        }
        return selectOrPickItem(client, redGlass, playFeedback);
    }

    public static HighlightedBlock getTargetedHighlightedBlock(Minecraft client, HighlightManager highlightManager, double maxDist) {
        if (client.player == null || client.level == null) return null;
        Optional<ClientSessionContext> session = ClientSessionContext.resolve(client);
        if (session.isEmpty()) return null;

        List<HighlightedBlock> blocks = highlightManager.visibleHighlights(session.get());
        if (blocks.isEmpty()) return null;

        Vec3 eye = client.player.getEyePosition(1.0f);
        Vec3 look = client.player.getViewVector(1.0f);
        Vec3 end = eye.add(look.scale(maxDist));

        HighlightedBlock bestBlock = null;
        double bestDistSq = Double.MAX_VALUE;

        for (HighlightedBlock hb : blocks) {
            if (hb.type() == dev.selestor1462.cannonencoder.template.HighlightType.SELECTED_LAMP
                || hb.type() == dev.selestor1462.cannonencoder.template.HighlightType.STAINED_GLASS) {
                continue;
            }
            if (WorldHighlightRenderer.isBlockSatisfied(client.level, hb)) {
                continue;
            }
            BlockPos pos = WorldHighlightRenderer.resolveEffectivePos(client.level, hb);
            AABB box = new AABB(pos).inflate(0.06);
            Optional<Vec3> hit = box.clip(eye, end);
            if (hit.isPresent()) {
                double distSq = eye.distanceToSqr(hit.get());
                if (distSq < bestDistSq) {
                    bestDistSq = distSq;
                    bestBlock = hb;
                }
            }
        }
        return bestBlock;
    }

    public static Item resolveRequiredItem(HighlightedBlock hb) {
        if (hb == null) return Items.AIR;
        if (hb.type() == dev.selestor1462.cannonencoder.template.HighlightType.CATCHER_BLOCK) {
            return net.minecraft.world.level.block.Blocks.STAINED_GLASS.red().asItem();
        }
        if (hb.type() == dev.selestor1462.cannonencoder.template.HighlightType.LANDING_BLOCK) {
            return Items.POWDER_SNOW_BUCKET;
        }
        BlockState state = WorldHighlightRenderer.resolveBlockState(hb);
        if (state == null) return Items.AIR;
        return state.getBlock().asItem();
    }

    private static void playPickSound(Minecraft client) {
        if (client.level != null && client.player != null) {
            client.level.playLocalSound(
                client.player.getX(), client.player.getY(), client.player.getZ(),
                SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.4f, false
            );
        }
    }

    private static void showPickFeedback(Minecraft client, Item item) {
        if (client.player != null) {
            String name = item.getName(item.getDefaultInstance()).getString();
            client.player.sendOverlayMessage(Component.literal("§a[Pick Block] Selected §f" + name));
        }
    }
}
