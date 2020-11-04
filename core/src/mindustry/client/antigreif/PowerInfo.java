package mindustry.client.antigreif;

import arc.*;
import arc.math.*;
import arc.scene.*;
import arc.scene.event.Touchable;
import arc.scene.ui.Button;
import arc.struct.*;
import arc.util.Strings;
import mindustry.*;
import mindustry.client.ui.*;
import mindustry.core.*;
import mindustry.graphics.*;
import mindustry.ui.*;
import mindustry.world.*;
import mindustry.world.blocks.power.*;
import java.util.*;
import mindustry.gen.*;

public class PowerInfo {

    public static ObjectSet<PowerGraph> graphs = new ObjectSet<>();
    private static PowerGraph found = null;
    private static int framesWithoutUpdate = 0;

    public static void initialize() {}

    public static void update() {
        graphs = graphs.select(Objects::nonNull);
        PowerGraph graph = graphs.asArray().max(g -> g.all.size);
        if (graph != null) {
            found = graph;
            framesWithoutUpdate = 0;
            if (Core.graphics.getFrameId() % 120 == 0) {
                // Every 2 seconds or so rescan
                scan();
            }
        } else {
            found = null;
            framesWithoutUpdate += 1;
            if (framesWithoutUpdate > 30) {
                // Scan twice a second
                framesWithoutUpdate = 2;
                scan();
            }
            if (framesWithoutUpdate == 1) {
                // Scan once immediately
                scan();
            }
        }
    }

    /** Scans the entire world for power nodes and gets the power graphs */
    private static void scan() {
        graphs.clear();
        if (Vars.world == null) {
            return;
        } else if (Vars.world.tiles == null) {
            return;
        }
        for (Tile tile : Vars.world.tiles) {
            if (tile != null) {
                if (tile.block() instanceof PowerNode) {
                    if (tile.build != null){
                        if (tile.build.power != null) {
                            if (tile.build.power.graph != null) {
                                if (tile.getTeamID() == Vars.player.team().id){
                                    graphs.add(tile.build.power.graph);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static Element getBars() {
        Button power = new Button(Styles.waveb);
        Bar powerBar = new MonospacedBar(() -> Strings.fixed(found != null ? found.displayPowerBalance.getAverage() * 60f : 0f, 1), () -> Pal.powerBar, () -> found != null? found.getSatisfaction() : 0f);

        Bar batteryBar = new Bar(() -> (found != null? UI.formatAmount((int)found.getLastPowerStored()) : 0) + " / " + (found != null? UI.formatAmount((int)found.getLastCapacity()) : 0), () -> Pal.powerBar, () -> found != null? Mathf.clamp(found.getLastPowerStored() / Math.max(found.getLastCapacity(), 0.0001f)) : 0f);

        power.touchable = Touchable.disabled; // Don't touch my button :)
        power.table(Tex.windowEmpty, t -> t.add(batteryBar).margin(0).grow()).grow();
        power.row();
        power.table(Tex.windowEmpty, t -> t.add(powerBar).margin(0).grow()).grow();

        return power;
    }
}
