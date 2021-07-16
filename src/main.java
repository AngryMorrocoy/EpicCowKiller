import com.epicbot.api.shared.APIContext;
import com.epicbot.api.shared.GameType;
import com.epicbot.api.shared.entity.GroundItem;
import com.epicbot.api.shared.entity.NPC;
import com.epicbot.api.shared.entity.SceneObject;
import com.epicbot.api.shared.entity.details.Locatable;
import com.epicbot.api.shared.model.Tile;
import com.epicbot.api.shared.model.path.Path;
import com.epicbot.api.shared.query.result.LocatableEntityQueryResult;
import com.epicbot.api.shared.script.LoopScript;
import com.epicbot.api.shared.script.ScriptManifest;
import util.Perimeter;
import util.Sleep;

import static com.epicbot.api.os.model.game.GameState.LOGGED_IN;

@ScriptManifest(name = "EpicCowKiller", gameType = GameType.OS)
public class main extends LoopScript {

    private NPC cowNPC;
    private final Tile CastleStairs = new Tile(3206, 3228);
    private final Perimeter cowsTerrain = new Perimeter(
            new Tile(3253, 3271), // Northern point
            new Tile(3265, 3255) // Southern point
    );

    private NPC getCowNPC() {
        return getAPIContext().npcs().query()
                .nameMatches("Cow")
                .notInCombat()
                .animation(-1)
                .reachable()
                .results()
                .nearest();
    }

    private SceneObject getStairCase() {
        return getAPIContext().objects().query()
                .nameMatches("Staircase")
                .results()
                .nearest();
    }

    private LocatableEntityQueryResult<GroundItem> getNearestCowhides() {
        return getAPIContext().groundItems().query()
                .nameMatches("Cowhide")
                .reachable()
                .results();
    }

    private int getCowhideCount() {
        return getAPIContext().inventory()
                .getCount("Cowhide");
    }

    private void atackCow() {
        cowNPC.click();
        getAPIContext().mouse().moveOffScreen();
    }

    private void fixCamera() {
        if (getAPIContext().camera().getPitch() < 85) {
            getAPIContext().camera().setPitch(88, true);
        }
    }

    private SceneObject getCowsTerrainGate() {
        return getAPIContext().objects().query()
                .nameMatches("Gate")
                .reachable()
                .results()
                .nearest();  // Gets the nearest gate
    }

    private int openGate() {
        SceneObject gate = getCowsTerrainGate(); // Gets the nearest gate
        if (gate.hasAction("Open")) {
            // Move the camera, so you can see it to interact with
            if (!gate.isVisible()) {
                int angleToGate = getAPIContext().camera()
                        .getAngleToDeg(getAPIContext().localPlayer().getLocation(), gate);
                // Angle where the camera should be, so the gate is visible
                getAPIContext().camera().setYaw(angleToGate);
                Sleep.sleepUntil(getAPIContext(), gate::isVisible, 5000);
                // If gate isn't visible after moving camera, move to the gate xd
                if (!gate.isVisible()) {
                    getAPIContext().walking().walkTo(gate, 2);
                    return 400;
                }
            }
            gate.interact("Open");
            return 750;
        }
        return 100;
    }

    private int getRandomInt(int min, int max) {
        return (int) Math.floor(Math.random() * (max - min + 1) - min);
    }

    private Path managePathTo(Locatable destination) throws Exception {
        Path path = getAPIContext().walking().findPath(destination);
        if (path == null) {
            destination = getAPIContext().walking().getClosestTileOnMap(destination);
            path = getAPIContext().walking().findPath(destination);
        }

        if (!getAPIContext().localPlayer().getLocation().canReach(getAPIContext(), destination)) {
            throw new Exception("Cannot reach the destination");
        }
        return path;
    }

    @Override
    protected int loop() {
        APIContext apiContext = getAPIContext();
        if (apiContext.game().getGameState() != LOGGED_IN) {
            return 500;
        }
        fixCamera();
        // If inventory is full
        if (apiContext.inventory().isFull()) {
            // Creates the path from the current location to the stairs of the castle
            try {
                Path path = managePathTo(CastleStairs);
                // If the player cannot reach the destination, it means the gate is closed, so it opens it
                // If near enough to the stairs, run
                if (apiContext.localPlayer().getLocation().distanceTo(apiContext, CastleStairs) <= 13
                        && !apiContext.walking().isRunEnabled()) {
                    apiContext.walking().setRun(true);
                }
                // Walk to the stairs if in plane 0, and obviously not there
                if (!(apiContext.localPlayer().getLocation().distanceTo(apiContext, CastleStairs) <= 1)
                        && apiContext.localPlayer().getLocation().getPlane() < 1) {
                    apiContext.walking().walkPath(path.getTiles());
                } else {
                    // Climb up stairs, until plane == 2 which is the plane where bank is at
                    int oldPlane = apiContext.localPlayer().getLocation().getPlane();
                    if (oldPlane == 2) {
                        // If in bank floor, open bank
                        if (apiContext.bank().open()) {
                            // Wait until bank is open
                            Sleep.sleepUntil(apiContext, () -> apiContext.bank().isOpen());
                            // Deposit all the cowhide
                            apiContext.bank().depositAll("Cowhide");
                            Sleep.sleepUntil(apiContext, () -> getCowhideCount() == 0);
                        }
                    } else {
                        // Climb up the stairs and wait until plane gets bigger
                        getStairCase().interact("Climb-up");
                        Sleep.sleepUntil(apiContext, () -> oldPlane < apiContext.localPlayer().getLocation().getPlane());
                    }
                }
            } catch (Exception ignored) {
                if (apiContext.localPlayer().getLocation().getPlane() < 1) {
                    return openGate();
                }
            }
        } else if (!cowsTerrain.isPlayerInside(apiContext)) {
            // Enable run if it's not
            if (!apiContext.walking().isRunEnabled()
                    && apiContext.walking().getRunEnergy() >= getRandomInt(35, 60)) {
                apiContext.walking().setRun(true);
            }
            try {
                Path path = managePathTo(cowsTerrain.getCenterTile());
            } catch (Exception ignored) {
                if (apiContext.localPlayer().getLocation().getPlane() < 1) {
                    return openGate();
                }
            }
        }
        // If the player isn't interacting with anything
        else if (apiContext.localPlayer().getInteracting() == null) {
            // Disable running
            if (apiContext.walking().isRunEnabled()) {
                apiContext.walking().setRun(false);
            }
            // Get the nearest cow
            cowNPC = getCowNPC();
            if (cowNPC != null && !cowNPC.isDead()) {
                // Attack the cow, and move mouse off screen
                atackCow();
                // If interacting
                if (apiContext.localPlayer().getInteracting() != null) {
                    // Wait until cow dies
                    Sleep.sleepUntil(apiContext,
                            () -> cowNPC.isDead() || apiContext.localPlayer().getInteracting() == null);
                    // Wait till the cowhide is on the inventory
                    getNearestCowhides().forEach((GroundItem item) -> {
                        if (!apiContext.inventory().isFull()) {
                            int oldCowHideOnInv = getCowhideCount();
                            item.interact("Take");
                            Sleep.sleepUntil(apiContext, () -> getCowhideCount() > oldCowHideOnInv);
                        }
                    });
                }
            }
        }
        return 200;
    }

    @Override
    public boolean onStart(String... strings) {
        System.out.println("Starting EpicCowKiller");
        return true;
    }

}
