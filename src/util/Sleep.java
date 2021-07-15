package util;

import com.epicbot.api.shared.APIContext;
import com.epicbot.api.shared.util.time.Time;

import java.util.function.BooleanSupplier;

public class Sleep {

    private static final int DEFAULT_SLEEP_TIMEOUT = 1000 * 60 * 10; // Default tiemout to 10 minutes

    public static void sleepUntil(APIContext ctx, BooleanSupplier breakCondition) {
        long startTime = System.currentTimeMillis();
        while (ctx.script().isRunning() && !ctx.script().isPaused()) {
            if (breakCondition.getAsBoolean() || System.currentTimeMillis() - startTime == DEFAULT_SLEEP_TIMEOUT) {
                break;
            }
            Time.sleep(100);
        }
    }

    public static void sleepUntil(APIContext ctx, BooleanSupplier breakCondition, int timeOut) {
        long startTime = System.currentTimeMillis();
        while (ctx.script().isRunning() && !ctx.script().isPaused()) {
            if (breakCondition.getAsBoolean() || System.currentTimeMillis() - startTime >= timeOut) break;
            Time.sleep(100);
        }
    }

    public static void sleepUntilDelay(APIContext ctx, BooleanSupplier breakCondition, int itDelay) {
        long startTime = System.currentTimeMillis();
        while (ctx.script().isRunning() && !ctx.script().isPaused()) {
            if (breakCondition.getAsBoolean() || System.currentTimeMillis() - startTime >= DEFAULT_SLEEP_TIMEOUT) break;
            Time.sleep(itDelay);
        }
    }

    public static void sleepUntilDelay(APIContext ctx, BooleanSupplier breakCondition, int itDelay, int timeOut) {
        long startTime = System.currentTimeMillis();
        while (ctx.script().isRunning() && !ctx.script().isPaused()) {
            if (breakCondition.getAsBoolean() || System.currentTimeMillis() - startTime >= timeOut) break;
            Time.sleep(itDelay);
        }
    }


}
