package ai.bfsx.heroes.grave;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/** Marks an open inventory as belonging to a grave. */
public class GraveHolder implements InventoryHolder {

    private final UUID graveId;
    private Inventory inventory;

    public GraveHolder(UUID graveId) {
        this.graveId = graveId;
    }

    public UUID graveId() { return graveId; }

    void setInventory(Inventory inventory) { this.inventory = inventory; }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
