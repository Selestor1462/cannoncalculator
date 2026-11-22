package dev.selestor1462.cannonencoder.client;

import dev.selestor1462.cannonencoder.template.HighlightType;
import dev.selestor1462.cannonencoder.template.HighlightedBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

public final class MaterialChecklist {

    public record Entry(Item item, String name, int required, int available) {
        public boolean hasEnough() {
            return available >= required;
        }

        public String formatted() {
            String color = hasEnough() ? "§a" : "§c";
            String symbol = hasEnough() ? "✔" : "✘";
            return String.format(java.util.Locale.ROOT, "%s%s: %d (Have: %d %s)", color, name, required, available, symbol);
        }
    }

    public static List<Entry> compute(Level level, List<HighlightedBlock> blocks, Player player) {
        List<Entry> result = new ArrayList<>();
        if (blocks == null || blocks.isEmpty()) return result;

        Map<Item, Integer> neededCounts = new LinkedHashMap<>();

        for (HighlightedBlock hb : blocks) {
            // Only count placeable, unsatisfied blocks
            if (!dev.selestor1462.cannonencoder.client.easyplace.EasyPlaceManager.isPlaceableHighlight(hb.type())) {
                continue;
            }
            if (level != null && WorldHighlightRenderer.isBlockSatisfied(level, hb)) {
                continue;
            }

            Item item = resolveItemForHighlight(hb.type());
            if (item != null) {
                neededCounts.put(item, neededCounts.getOrDefault(item, 0) + 1);
            }
        }

        for (Map.Entry<Item, Integer> e : neededCounts.entrySet()) {
            Item item = e.getKey();
            int req = e.getValue();
            int avail = (player != null) ? countItemInInventory(player, item) : 0;
            String name = new ItemStack(item).getHoverName().getString();
            result.add(new Entry(item, name, req, avail));
        }

        return result;
    }

    private static Item resolveItemForHighlight(HighlightType type) {
        return switch (type) {
            case OBSERVER, TARGET_OBSERVER -> Items.OBSERVER;
            case RAIL -> Items.ACTIVATOR_RAIL;
            case GLASS -> Items.BLACK_STAINED_GLASS;
            case REDSTONE_DUST -> Items.REDSTONE;
            case NOTE_BLOCK -> Items.NOTE_BLOCK;
            case CATCHER_BLOCK -> Items.RED_STAINED_GLASS;
            case TWISTING_VINES -> Items.TWISTING_VINES;
            case LANDING_BLOCK -> Items.POWDER_SNOW_BUCKET;
            case GRAY_CARPET -> Items.GRAY_CARPET;
            default -> null;
        };
    }

    private static int countItemInInventory(Player player, Item item) {
        int count = 0;
        Item redGlassItem = Items.RED_STAINED_GLASS;
        Item grayCarpetItem = Items.GRAY_CARPET;
        Item lightGrayCarpetItem = Items.LIGHT_GRAY_CARPET;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;
            if (stack.is(item)) {
                count += stack.getCount();
            } else if (item == redGlassItem && (stack.is(Items.HONEY_BLOCK) || stack.is(Items.SLIME_BLOCK))) {
                // Honey or slime can also satisfy catcher block!
                count += stack.getCount();
            } else if (item == grayCarpetItem && stack.is(lightGrayCarpetItem)) {
                // Light gray carpet can also satisfy gray carpet
                count += stack.getCount();
            }
        }
        return count;
    }
}
