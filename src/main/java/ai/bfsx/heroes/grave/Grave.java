package ai.bfsx.heroes.grave;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** One grave: who died, where the marker stands, and what is still inside. */
public class Grave {

    public static final int SLOTS = 45;

    private final UUID id;
    private final UUID owner;
    private final String ownerName;
    private final Location location;
    private final long createdAt;
    private ItemStack[] contents;
    private UUID markerId;
    private UUID openedBy;

    public Grave(UUID id, UUID owner, String ownerName, Location location, long createdAt, ItemStack[] contents) {
        this.id = id;
        this.owner = owner;
        this.ownerName = ownerName;
        this.location = location;
        this.createdAt = createdAt;
        this.contents = contents;
    }

    public UUID id() { return id; }
    public UUID owner() { return owner; }
    public String ownerName() { return ownerName; }
    public Location location() { return location.clone(); }
    public long createdAt() { return createdAt; }
    public ItemStack[] contents() { return contents; }
    public void setContents(ItemStack[] contents) { this.contents = contents; }
    public UUID markerId() { return markerId; }
    public void setMarkerId(UUID markerId) { this.markerId = markerId; }
    public UUID openedBy() { return openedBy; }
    public void setOpenedBy(UUID openedBy) { this.openedBy = openedBy; }

    public boolean isEmpty() {
        if (contents == null) return true;
        for (ItemStack it : contents) {
            if (it != null && !it.getType().isAir()) return false;
        }
        return true;
    }

    public List<ItemStack> nonEmptyItems() {
        List<ItemStack> out = new ArrayList<>();
        if (contents == null) return out;
        for (ItemStack it : contents) {
            if (it != null && !it.getType().isAir()) out.add(it);
        }
        return out;
    }

    public String shortId() {
        return id.toString().substring(0, 8);
    }
}
