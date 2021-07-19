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
import com.epicbot.api.shared.util.paint.frame.PaintFrame;
import com.epicbot.api.shared.util.time.Time;
import util.Perimeter;
import util.Sleep;

import java.awt.*;

import static com.epicbot.api.os.model.game.GameState.LOGGED_IN;

@ScriptManifest(name = "EpicCowKiller", gameType = GameType.OS)
public class main extends LoopScript {

    private NPC cowNPC;
    private final Tile castleStairs = new Tile(3206, 3228);
    private final Tile cowsEntrance = new Tile(3249, 3266);
    private final Perimeter cowsTerrain = new Perimeter(
            new Tile(3253, 3271), // Northern point
            new Tile(3265, 3255) // Southern point
    );
    private long startTime;
    private int collectedCowHide = 0;

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
        final SceneObject[] gate = {null};
        getAPIContext().objects().query()
                .nameMatches("Gate")
                .actions("Open")
                .results()
                .forEach((SceneObject obj) -> {
                    if (cowsEntrance.distanceTo(getAPIContext(), obj) <= 10) {
                        gate[0] = obj;
                    }
                });
        return gate[0];
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
            return 800;
        }
        return 100;
    }

    private int getRandomInt(int min, int max) {
        return (int) Math.floor(Math.random() * (max - min + 1) - min);
    }

    private Path managePathTo(Locatable destination) {
        Path path = getAPIContext().walking().findPath(destination);
        if (path == null) {
            destination = getAPIContext().walking().getClosestTileOnMap(destination);
            path = getAPIContext().walking().findPath(destination);
            if (path == null) {
                destination = new Perimeter(getAPIContext().localPlayer().getLocation(),
                        destination.getLocation()).getCenterTile();
                path = getAPIContext().walking().findPath(destination);
            }
        }

        if (!path.validate(getAPIContext())) {
            return null;
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
            Path path = managePathTo(castleStairs);
            // If the player cannot reach the destination, it means the gate is closed, so it opens it
            // If near enough to the stairs, run
            if (apiContext.localPlayer().getLocation().distanceTo(apiContext, castleStairs) <= 13
                    && !apiContext.walking().isRunEnabled()) {
                apiContext.walking().setRun(true);
            }
            // If path == null means it cannot be reached so, if it happens means the gate is closed
            if (path == null && apiContext.localPlayer().getLocation().getPlane() < 1) {
                return openGate();
            }

            // Walk to the stairs if in plane 0, and obviously not there
            if (!(apiContext.localPlayer().getLocation().distanceTo(apiContext, castleStairs) <= 1)
                    && apiContext.localPlayer().getLocation().getPlane() < 1) {
                apiContext.walking().walkPath(path.getTiles());
            } else {
                // Climb up stairs, until plane == 2 which is the plane where bank is at
                int currentPlane = apiContext.localPlayer().getLocation().getPlane();
                if (currentPlane == 2) {
                    // If in bank floor, open bank
                    if (apiContext.bank().open()) {
                        // Wait until bank is open
                        Sleep.sleepUntil(apiContext, () -> apiContext.bank().isOpen(), 3500);
                        // Deposit all the cowhide
                        apiContext.bank().depositAll("Cowhide");
                        Sleep.sleepUntil(apiContext, () -> getCowhideCount() == 0, 3500);
                    }
                } else {
                    // Climb up the stairs and wait until plane gets bigger
                    getStairCase().interact("Climb-up");
                    Sleep.sleepUntil(apiContext,
                            () -> currentPlane < apiContext.localPlayer().getLocation().getPlane(),
                            4000);
                }
            }
        }
        // If player not inside cows terrain
        else if (!cowsTerrain.isPlayerInside(apiContext)) {
            // Enable run if it's not
            if (!apiContext.walking().isRunEnabled()
                    && apiContext.walking().getRunEnergy() >= getRandomInt(35, 60)) {
                apiContext.walking().setRun(true);
            }

            if (apiContext.bank().isOpen()) {
                apiContext.bank().close();
                return 300;
            }

            // Make the path to the cows terrain
            Path path = managePathTo(cowsTerrain.getCenterTile());

            // If path is null, means cannot reach destination and if in plane < 1 means the gate is closed
            if (path == null && apiContext.localPlayer().getLocation().getPlane() < 1) {
                System.out.println("Path null :c");
                if (apiContext.localPlayer().getLocation().distanceTo(apiContext, cowsEntrance) >= 6) {
                    path = managePathTo(cowsEntrance);
                } else {
                    return openGate();
                }
            }
            if (path != null && apiContext.localPlayer().getLocation().getPlane() == 0) {
                // Otherwise, just walk
                if (!apiContext.localPlayer().getLocation().canReach(apiContext, cowsTerrain.getCenterTile())
                        && getCowsTerrainGate() != null) {
                    return openGate();
                }
                apiContext.walking().walkPath(path.getTiles());
            }
            if (apiContext.localPlayer().getLocation().getPlane() > 0) {
                // If plane > 0 means you're in some places of the lumbridge castle, so go down
                SceneObject stairCase = getStairCase();
                if (stairCase == null) {
                    getAPIContext().script().stop("No staircases found, not expected situation");
                }
                if (!stairCase.isVisible()) {
                    // Get the angle where u can see the staircase
                    int angleToStairCase = apiContext.camera()
                            .getAngleToDeg(getAPIContext().localPlayer().getLocation(), stairCase);
                    apiContext.camera().setYaw(angleToStairCase);
                    Sleep.sleepUntil(apiContext, stairCase::isVisible, 5000);
                    if (!stairCase.isVisible()) {
                        apiContext.walking().walkTo(stairCase, 2);
                        return 400;
                    }
                }
                stairCase.interact("Climb-down");
                return 800;
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
                        if (!apiContext.inventory().isFull()
                                && item.distanceTo(getAPIContext(), apiContext.localPlayer().getLocation()) <= 6) {
                            int oldCowHideOnInv = getCowhideCount();
                            Sleep.sleepUntilDelay(apiContext, () -> item.interact("Take"), 750, 3000);
                            Sleep.sleepUntil(apiContext, () -> getCowhideCount() > oldCowHideOnInv, 4000);
                            collectedCowHide += Math.abs(getCowhideCount() - oldCowHideOnInv);
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
        startTime = System.currentTimeMillis();
        return true;
    }

    @Override
    protected void onPaint(Graphics2D g, APIContext ctx) {
        if (getAPIContext().client().isLoggedIn()) {
            PaintFrame frame = new PaintFrame();
            frame.setTitle("EpicCowKiller uwu");
            frame.addLine("Runtime: ", Time.getFormattedRuntime(startTime)); // we use startTime here from the very beginning
            frame.addLine("", "");
            frame.addLine("Cowhide collected: ", collectedCowHide);
            frame.draw(g, 0, 90, ctx); //drawing the actual frame.
//            g.setColor(new Color(208, 189, 155, 255));
//            g.fillRect(11, 468, 120, 15); //name covering stuff, honestly might remove it cuz kinda pointless? Dunno
        }
    }


}
