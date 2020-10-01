package mindustry.client.antigreif;

import arc.scene.ui.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import mindustry.world.*;

public class TileLog {
    public Seq<TileLogItem> log = new Seq<>();
    public int x, y;

    public TileLog(Tile tile){
        x = tile.x;
        y = tile.y;
    }

    public void addItem(TileLogItem item) {
        log.add(item);
    }

    public Table toTable(){
        Table table = new Table();
        if (log.isEmpty()) {
            table.add(new Label(String.format("No logs for %d,%d", x, y)));
        } else{
            table.add(new Label(String.format("Logs for %d,%d:", x, y)));
            log.forEach(item -> {
                table.row();
                table.add(item.toElement());
            });
        }
        return table;
    }
}
