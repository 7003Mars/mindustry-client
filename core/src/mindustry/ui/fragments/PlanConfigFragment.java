package mindustry.ui.fragments;

import arc.*;
import arc.math.*;
import arc.math.geom.*;
import arc.scene.*;
import arc.scene.actions.*;
import arc.scene.ui.layout.*;
import arc.struct.*;
import arc.util.*;
import mindustry.ctype.*;
import mindustry.entities.units.*;
import mindustry.game.*;
import mindustry.type.*;
import mindustry.world.*;
import mindustry.world.blocks.*;

import static mindustry.Vars.*;

public class PlanConfigFragment {
	Table table = new Table();
	BuildPlan selected;

	public void build(Group parent){
		table.visible = false;
		parent.addChild(table);

		Events.on(EventType.ResetEvent.class, e -> forceHide());
	}

	public void showConfig(BuildPlan plan) {
		if (this.selected == plan) {
			hide();
			return;
		}
		Block block = plan.block;
		if (!block.configurable) return;
		selected = plan;
		table.clear();
		Seq<UnlockableContent> items = new Seq<>();
		if (block.configurations.containsKey(Item.class)) {
			items.add(content.items());
		} else if (block.configurations.containsKey(Liquid.class)) {
			items.add(content.liquids());
		}
		if (items.isEmpty()) return;
		ItemSelection.buildTable(table, items, () -> selected != null ? (selected.config instanceof UnlockableContent c ? c : null) : null,
				content -> {
			selected.config = content;
			hide();
				}, block.selectionRows, block.selectionColumns);
		table.pack();
		table.setTransform(true);
		table.visible = true;
		table.actions(Actions.scaleTo(0f, 1f), Actions.visible(true),
				Actions.scaleTo(1f, 1f, 0.07f, Interp.pow3Out));
		table.update(() -> {
			table.setOrigin(Align.center);
			if (plan.isDone() || !(control.input.selectPlans.contains(plan) || player.unit().plans.contains(plan))) {
				Log.info("hiding");
				this.hide();
				return;
			}
			Vec2 pos = Core.input.mouseScreen(plan.drawx(), plan.drawy() - block.size * tilesize / 2.0F - 1);
			table.setPosition(pos.x, pos.y, Align.top);
		});
	}

	public void forceHide() {
		table.visible = false;
		selected = null;
	}

	public void hide() {
		selected = null;
		table.actions(Actions.scaleTo(0f, 1f, 0.06f, Interp.pow3Out), Actions.visible(false));
	}
}
