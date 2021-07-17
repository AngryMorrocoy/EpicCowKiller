package util;

import com.epicbot.api.shared.APIContext;
import com.epicbot.api.shared.entity.details.Locatable;
import com.epicbot.api.shared.model.Tile;

public class Perimeter {

    private final Tile northernTile;
    private final Tile southernTile;

    public Perimeter(Tile tile1, Tile tile2) {
        if (tile1.getY() >= tile2.getY() && tile1.getX() <= tile2.getX()) {
            this.northernTile = tile1;
            this.southernTile = tile2;
            return;
        }
        this.northernTile = tile2;
        this.southernTile = tile1;
    }

    public Perimeter(int x1, int y1, int x2, int y2) {
        if (y1 >= y2 && x1 <= x2) {
            this.northernTile = new Tile(x1, y1);
            this.southernTile = new Tile(x2, y2);
            return;
        }
        this.northernTile = new Tile(x2, y2);
        this.southernTile = new Tile(x1, y1);
    }

    public boolean isPlayerInside(APIContext ctx) {
        return isInside(ctx.localPlayer().getLocation());
    }

    public boolean isInside(Locatable entity) {
        int entityX, entityY;
        boolean isInside = true;
        Tile localLocation = entity.getLocation();
        entityX = localLocation.getX();
        entityY = localLocation.getY();

        // If entity position is out of left/top bounds, isOutside
        if (entityY > northernTile.getY() || entityX < northernTile.getX()) isInside = false;
        // If entity position is out of right/bottom bounds, isOutside
        else if (entityY < southernTile.getY() || entityX > southernTile.getX()) isInside = false;

        return isInside;
    }

    public Tile getCenterTile() {
        int midX = northernTile.getX() +
                Math.floorDiv(
                        Math.abs(northernTile.getX() - southernTile.getX()),
                        2
                );
        int midY = southernTile.getY() +
                Math.floorDiv(
                        Math.abs(northernTile.getY() - southernTile.getY()),
                        2
                );
        return new Tile(midX, midY);
    }

}
