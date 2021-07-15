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
    private final Tile CowsTerrain = new Tile(3260, 3266);

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
        return getAPIContext().localPlayer().getLocation().distanceTo(getAPIContext(), CowsTerrain) <= 7;
    }

    @Override
    protected int loop() {
        APIContext apiContext = getAPIContext();
        if (apiContext.game().getGameState() != LOGGED_IN) {
            return 500;
        }

        if (isPLayerOnCowsTerrain()) {
            System.out.println("You're near of the cows!");
        } else {
            System.out.println("You're not near of the cows!>:c");
        }

        // If inventory is full
        if (apiContext.inventory().isFull()) {
            Path path = apiContext.walking().findPath(CastleStairs);

            if (apiContext.localPlayer().getLocation().distanceTo(apiContext, CastleStairs) <= 13 && !apiContext.walking().isRunEnabled()) {
                apiContext.walking().setRun(true);
            }
            if (!(apiContext.localPlayer().getLocation().distanceTo(apiContext, CastleStairs) <= 1) && apiContext.localPlayer().getLocation().getPlane() < 1) {
                apiContext.walking().walkPath(path.getTiles());
            } else {
                System.out.println("Getting plane");
                int oldPlane = apiContext.localPlayer().getLocation().getPlane();
                if (oldPlane == 2) {
                    if (apiContext.bank().open()) {
                        Sleep.sleepUntil(apiContext, () -> apiContext.bank().isOpen());
                        apiContext.bank().depositAll("Cowhide");
                        Sleep.sleepUntil(apiContext, () -> getCowhideCount() == 0);
                    }
                } else {
                    getStairCase().interact("Climb-up");
                    Sleep.sleepUntil(apiContext, () -> oldPlane < apiContext.localPlayer().getLocation().getPlane());
                }
            }

        }
        // If the player isn't interacting with anything
        else if (apiContext.localPlayer().getInteracting() == null) {
            // Get the nearest cow
            cowNPC = getCowNPC();
            if (cowNPC != null && !cowNPC.isDead()) {
                // Attack the cow, and move mouse off screen
                atackCow();
                // If interacting
                if (apiContext.localPlayer().getInteracting() != null) {
//                    if (!apiContext.localPlayer().getInteracting().equals(cowNPC)) {
//                        cowNPC = (NPC) apiContext.localPlayer().getInteracting();
//                    }
                    Sleep.sleepUntil(apiContext, () -> cowNPC.isDead() || apiContext.localPlayer().getInteracting() == null);  // Wait until cow dies
//                    getNearestCowhides().interact("Take");  // Takes the nearest cowhide on floor
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
