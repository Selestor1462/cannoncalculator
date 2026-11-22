package dev.selestor1462.cannonencoder.client.validation;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

public record ValidationReport(
    int level,
    int column,
    int totalChecked,
    List<EncodingError> errors
) {
    public boolean isValid() {
        return this.errors.stream().noneMatch(EncodingError::isError);
    }

    public int errorCount() {
        return (int) this.errors.stream().filter(EncodingError::isError).count();
    }

    public int warningCount() {
        return (int) this.errors.stream().filter(e -> !e.isError()).count();
    }

    public List<Component> formatMessages() {
        List<Component> messages = new ArrayList<>();
        if (this.isValid()) {
            messages.add(Component.literal("§a[Cannon] Encoding for Slot (Level " + this.level + ", Col " + (this.column + 1) + ") is 100% CORRECT! (Checked " + this.totalChecked + " blocks)"));
        } else {
            messages.add(Component.literal("§c[Cannon] Encoding for Slot (Level " + this.level + ", Col " + (this.column + 1) + ") has " + this.errorCount() + " error(s):"));
            for (EncodingError err : this.errors) {
                String prefix = err.isError() ? "§c✖ " : "§e⚠ ";
                messages.add(Component.literal(prefix + err.message() + " at " + err.pos().getX() + " " + err.pos().getY() + " " + err.pos().getZ()));
            }
        }
        return messages;
    }
}
