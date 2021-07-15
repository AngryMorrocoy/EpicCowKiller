import com.epicbot.api.shared.APIContext;
import com.epicbot.api.shared.GameType;
import com.epicbot.api.shared.entity.GroundItem;
import com.epicbot.api.shared.entity.NPC;
import com.epicbot.api.shared.entity.SceneObject;
import com.epicbot.api.shared.model.Tile;
import com.epicbot.api.shared.model.path.Path;
import com.epicbot.api.shared.query.result.LocatableEntityQueryResult;
import com.epicbot.api.shared.script.LoopScript;
import com.epicbot.api.shared.script.ScriptManifest;
import util.Sleep;

import static com.epicbot.api.os.model.game.GameState.LOGGED_IN;

@ScriptManifest(name = "EpicCowKiller", gameType = GameType.OS)
public class main extends LoopScript {

    private NPC cowNPC;
    private final Tile CastleStairs = new Tile(3206, 3228);
    private final Tile CowsEntrance = new Tile(3249, 3266);
    private final Tile[] CowsTerrain = {
            new Tile(3253, 3271), // Northern point
            new Tile(3265, 3255), // Southern point
    };

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

    private boolean isPLayerOnCowsTerrain() {
        int northernX, northernY, southernX, southernY, playerX, playerY;
        Tile localLocation = getAPIContext().localPlayer().getLocation();
        playerX = localLocation.getX();
        playerY = localLocation.getY();
        northernX = CowsTerrain[0].getX();
        northernY = CowsTerrain[0].getY();
        southernX = CowsTerrain[1].getX();
        southernY = CowsTerrain[1].getY();
        return (playerY <= northernY && playerY >= southernY)
                &&
                (playerX >= northernX && playerX <= southernX);
    }

    private void fixCamera() {
        if (getAPIContext().camera().getPitch() < 85) {
            getAPIContext().camera().setPitch(88, true);
        }
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
            Tile destination = CastleStairs;
            // Creates the path from the current location to the stairs of the castle
            Path path = apiContext.walking().findPath(CastleStairs);
            if (path == null) {
                destination = apiContext.walking().getClosestTileOnMap(CastleStairs);
                path = apiContext.walking().findPath(destination);
            }

            // If the cannot reach the destination, it means the gate is closed, so it opens it
            if (!apiContext.localPlayer().getLocation().canReach(apiContext, destination)
                    && apiContext.localPlayer().getLocation().getPlane() < 1) {
                SceneObject gate = apiContext.objects().query()
                        .nameMatches("Gate")
                        .reachable()
                        .results()
                        .nearest();  // Gets the nearest gate
                if (gate.hasAction("Open")) {
                    // Move the camera, so you can see it to interact with
                    if (!gate.isVisible()) {
                        int angleToGate = apiContext.camera()
                                .getAngleToDeg(apiContext.localPlayer().getLocation(), gate);
                        apiContext.camera().setYaw(angleToGate);
                        Sleep.sleepUntil(apiContext, gate::isVisible, 10000);
                        if (!gate.isVisible()) {
                            apiContext.walking().walkTo(gate, 2);
                            return 400;
                        }
                    }
                    gate.interact("Open");
                    return 750;
                }
            }
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

        }
//        else if (!isPLayerOnCowsTerrain()) {
//        }
        // If the player isn't interacting with anything
        else if (apiContext.localPlayer().getInteracting() == null) {
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
//    protected int loop() {
//        if (isPLayerOnCowsTerrain()) {
//            System.out.println("You're near of the cows!");
//        } else {
//            System.out.println("You're not near of the cows!>:c");
//        }
//        APIContext apiContext = getAPIContext();
//        if (apiContext.game().getGameState() != LOGGED_IN) {
//            return 500;
//        }
//        Tile destination = new Tile(3205, 3225, 2);
//        ScreenPath path = apiContext.walking().findPathOnScreen(CastleStairs);
//        if (!(apiContext.localPlayer().getLocation().distanceTo(apiContext, CastleStairs) <= 1)){
//            apiContext.walking().walkPathOnScreen(path);
//            System.out.println("Walking!");
//            return 700;
//        }
//        System.out.println("Reached destination" + destination.toString());
//        apiContext.script().stop("uwu");
//        return 100;
//    }

    @Override
    public boolean onStart(String... strings) {
        System.out.println("Starting EpicCowKiller");
        return true;
    }

}
