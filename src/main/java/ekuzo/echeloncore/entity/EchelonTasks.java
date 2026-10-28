package ekuzo.echeloncore.entity;

import ekuzo.echeloncore.EchelonCore;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Small server-tick scheduler for multi-tick ability effects. */
public final class EchelonTasks {
    private static final List<BooleanSupplier> TASKS = new ArrayList<>();

    private EchelonTasks() { }

    static void initialize() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (TASKS.isEmpty()) return;
            List<BooleanSupplier> current = new ArrayList<>(TASKS);
            TASKS.clear();
            for (BooleanSupplier task : current) {
                boolean done;
                try { done = task.getAsBoolean(); }
                catch (RuntimeException exception) {
                    EchelonCore.LOGGER.warn("Ability task failed", exception);
                    done = true;
                }
                if (!done) TASKS.add(task);
            }
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
    }

    /** Runs {@code task} every server tick until it returns true. */
    public static void everyTick(BooleanSupplier task) { TASKS.add(task); }

    /** Runs {@code action} once, {@code ticks} server ticks from now. */
    public static void later(int ticks, Runnable action) {
        int[] left = {ticks};
        TASKS.add(() -> {
            if (--left[0] > 0) return false;
            action.run();
            return true;
        });
    }
}
