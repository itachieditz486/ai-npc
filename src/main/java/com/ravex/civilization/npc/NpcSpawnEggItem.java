package com.ravex.civilization.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Custom "spawn egg": the Yassine egg always makes the one permanent Yassine; the random egg makes a new identity. */
public class NpcSpawnEggItem extends Item {
    private final boolean yassine;

    public NpcSpawnEggItem(boolean yassine, Properties props) {
        super(props);
        this.yassine = yassine;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        if (!(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        Player player = ctx.getPlayer();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());

        boolean ok;
        if (yassine) {
            NpcSpawner.Result r = NpcSpawner.yassine(sl, pos, ctx.getRotation());
            ok = r.npc() != null;
            if (!ok && player != null) player.displayClientMessage(Component.literal(r.error()), true);
        } else {
            ok = NpcSpawner.random(sl, pos, ctx.getRotation()) != null;
        }
        if (!ok) return InteractionResult.FAIL;
        if (player != null && !player.getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
