package dev.jdguo.shop.outlet;

import com.mojang.brigadier.arguments.StringArgumentType;
import dev.jdguo.shop.ui.ShopChestScreen;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.decoration.InteractionEntity;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import java.util.*;
import static net.minecraft.server.command.CommandManager.*;

/** Vanilla interaction, block and text display entities, scoped to a saved world. */
public final class ShopOutlets {
    private static final String TAG = "shopmod_outlet", KEY = "shopmod_outlet_name_", ROOT = "shopmod_counter_root", SIGN = "shopmod_counter_sign";
    private ShopOutlets() { }
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> {
            var outlet = literal("outlet").requires(source -> source.hasPermissionLevel(2));
            outlet.then(literal("set").then(argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> set(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
            outlet.then(literal("remove").then(argument("name", StringArgumentType.greedyString())
                    .executes(ctx -> remove(ctx.getSource(), StringArgumentType.getString(ctx, "name")))));
            var rotation = literal("rotate");
            for (int i = 0; i < 4; i++) {
                final int facing = i;
                rotation.then(literal(CounterRotation.name(i)).then(argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> rotate(ctx.getSource(), StringArgumentType.getString(ctx, "name"), facing))));
            }
            outlet.then(rotation);
            outlet.then(literal("list").executes(ctx -> {
                for (ShopOutlet o : ShopOutletState.get(ctx.getSource().getServer()).all()) {
                    ctx.getSource().sendFeedback(() -> Text.literal(o.name() + " · " + o.dimension() + " " + o.x() + " " + o.y() + " " + o.z()), false);
                }
                return 1;
            }));
            dispatcher.register(literal("shop").then(outlet).then(literal("chest").executes(ctx -> {
                ShopChestScreen.open(ctx.getSource().getPlayerOrThrow()); return 1;
            })).then(literal("ui").executes(ctx -> {
                dev.jdguo.shop.net.ShopUiNetwork.open(ctx.getSource().getPlayerOrThrow());
                return 1;
            })));
        });
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || hand != Hand.MAIN_HAND || player.isSpectator()) return ActionResult.PASS;
            for (ShopOutlet o : ShopOutletState.get(serverPlayer.getServer()).all()) {
                BlockPos p = hit.getBlockPos();
                if (o.at(world.getRegistryKey().getValue().toString(), p.getX(),p.getY(),p.getZ())) return open(serverPlayer,o);
            }
            return ActionResult.PASS;
        });
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!(player instanceof ServerPlayerEntity serverPlayer) || hand != Hand.MAIN_HAND || player.isSpectator() || !entity.getCommandTags().contains(TAG)) return ActionResult.PASS;
            ShopOutlet o = ShopOutletState.get(serverPlayer.getServer()).find(name(entity));
            return o == null ? ActionResult.FAIL : open(serverPlayer,o);
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> { if (server.getTicks() % 20 == 0) reconcile(server); });
    }
    private static ActionResult open(ServerPlayerEntity player, ShopOutlet o) {
        if (!player.getWorld().getRegistryKey().getValue().toString().equals(o.dimension()) || player.squaredDistanceTo(o.x()+0.5,o.y()+1,o.z()+0.5)>36) return ActionResult.FAIL;
        if (player.getWorld().getBlockState(new BlockPos(o.x(),o.y(),o.z())).isAir()) return ActionResult.FAIL;
        dev.jdguo.shop.net.ShopUiNetwork.open(player); return ActionResult.SUCCESS;
    }
    private static int set(ServerCommandSource source,String name) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayerEntity player=source.getPlayerOrThrow(); HitResult hit=player.raycast(5,0,false);
        if (!(hit instanceof BlockHitResult block) || hit.getType()!=HitResult.Type.BLOCK) { source.sendError(Text.literal("请看准五格内的实体方块作为柜台底座。")); return 0; }
        BlockPos p=block.getBlockPos();
        if (!player.getWorld().getBlockState(p).isSolidBlock(player.getWorld(),p)) { source.sendError(Text.literal("柜台需要实心底座。")); return 0; }
        if (!player.getWorld().getBlockState(p.up()).isAir() || !player.getWorld().getBlockState(p.up(2)).isAir()) { source.sendError(Text.literal("底座上方请留两格空气。")); return 0; }
        for (Entity nearby : player.getServerWorld().getOtherEntities(null, new net.minecraft.util.math.Box(p.up()).expand(1.5))) {
            Set<String> tags = nearby.getCommandTags();
            if ((tags.contains("bankmod_outlet") || tags.contains("marketmod_outlet") || tags.contains(TAG))
                    && !(tags.contains(TAG) && name(nearby).equals(name))) {
                source.sendError(Text.literal("此处靠近已有交易柜台，请至少间隔三格。")); return 0;
            }
        }
        try { ShopOutletState.get(source.getServer()).put(new ShopOutlet(name,player.getWorld().getRegistryKey().getValue().toString(),p.getX(),p.getY(),p.getZ(), ShopOutletState.get(source.getServer()).find(name)==null?0:ShopOutletState.get(source.getServer()).find(name).facing())); }
        catch (IllegalArgumentException e) { source.sendError(Text.literal(e.getMessage())); return 0; }
        source.getServer().getOverworld().getPersistentStateManager().save(); reconcile(source.getServer());
        source.sendFeedback(() -> Text.literal("已设置商店柜台 "+name+"，右键打开商店。"),true); return 1;
    }
    private static int rotate(ServerCommandSource source, String name, int facing) {
        ShopOutletState state = ShopOutletState.get(source.getServer());
        ShopOutlet outlet = state.find(name);
        if (outlet == null) { source.sendError(Text.literal("没有这个商店网点。")); return 0; }
        state.put(outlet.rotated(facing));
        source.getServer().getOverworld().getPersistentStateManager().save();
        for (ServerWorld world : source.getServer().getWorlds()) {
            List<Entity> remove = new ArrayList<>();
            world.iterateEntities().forEach(e -> { if (e.getCommandTags().contains(TAG) && name(e).equals(name)) remove.add(e); });
            remove.forEach(Entity::discard);
        }
        reconcile(source.getServer());
        source.sendFeedback(() -> Text.literal("商店柜台 " + name + " 已朝向 " + CounterRotation.name(facing)), true);
        return 1;
    }
    private static int remove(ServerCommandSource source,String name) {
        boolean removed=ShopOutletState.get(source.getServer()).remove(name);
        source.getServer().getOverworld().getPersistentStateManager().save(); reconcile(source.getServer());
        source.sendFeedback(() -> Text.literal(removed ? "已移除商店网点；不改动底座方块。" : "没有这个网点。"),true); return removed?1:0;
    }
    private static String name(Entity e) { return e.getCommandTags().stream().filter(t->t.startsWith(KEY)).map(t->t.substring(KEY.length())).findFirst().orElse(""); }
    private static void drop(Entity e) { for(Entity p:List.copyOf(e.getPassengerList())) p.discard(); e.discard(); }
    private static void tag(Entity e, ShopOutlet o) { e.addCommandTag(TAG); e.addCommandTag("shopmod_facing_"+CounterRotation.normalize(o.facing())); e.addCommandTag(KEY+o.name()); e.setInvulnerable(true); e.setSilent(true); e.setNoGravity(true); }
    private static void reconcile(MinecraftServer server) {
        ShopOutletState state=ShopOutletState.get(server);
        for(ServerWorld world:server.getWorlds()) {
            Map<String,Entity> roots=new HashMap<>(), signs=new HashMap<>(); List<Entity> all=new ArrayList<>();
            world.iterateEntities().forEach(e->{if(e.getCommandTags().contains(TAG)) all.add(e);});
            for(Entity e:all) {
                ShopOutlet o=state.find(name(e));
                if(o==null || !matchesFacing(e,o.facing()) || !world.getRegistryKey().getValue().toString().equals(o.dimension())) { drop(e); continue; }
                if(!world.isChunkLoaded(o.x()>>4,o.z()>>4)) { drop(e); continue; }
                if(world.getBlockState(new BlockPos(o.x(),o.y(),o.z())).isAir()) { drop(e); continue; }
                boolean root=e.getCommandTags().contains(ROOT), sign=e.getCommandTags().contains(SIGN);
                if(root || sign) {
                    double y=o.y()+(root?1:3.3);
                    if(e.squaredDistanceTo(o.x()+0.5,y,o.z()+0.5)>0.1 || (root && !(e instanceof InteractionEntity)) || (sign && !(e instanceof DisplayEntity.TextDisplayEntity))) { drop(e); continue; }
                    Map<String,Entity> map=root?roots:signs;
                    if(map.putIfAbsent(o.name(),e)!=null) drop(e);
                } else if(e.getVehicle()==null || !e.getVehicle().getCommandTags().contains(ROOT) || !name(e.getVehicle()).equals(o.name())) drop(e);
            }
            for(ShopOutlet o:state.all()) {
                if(!world.getRegistryKey().getValue().toString().equals(o.dimension()) || !world.isChunkLoaded(o.x()>>4,o.z()>>4) || world.getBlockState(new BlockPos(o.x(),o.y(),o.z())).isAir()) continue;
                Entity root=roots.get(o.name());
                if(root==null || root.isRemoved() || root.getPassengerList().size()!=4) { if(root!=null) drop(root); spawnCounter(world,o); }
                Entity sign=signs.get(o.name()); if(sign==null || sign.isRemoved()) spawnSign(world,o);
            }
        }
    }
    private static void spawnCounter(ServerWorld world,ShopOutlet o) {
        InteractionEntity root=new InteractionEntity(EntityType.INTERACTION,world); tag(root,o); root.addCommandTag(ROOT);
        NbtCompound n=new NbtCompound(); root.writeNbt(n); n.putFloat("width",1.7f); n.putFloat("height",1.4f); n.putBoolean("response",true); root.readNbt(n);
        root.refreshPositionAndAngles(o.x()+0.5,o.y()+1,o.z()+0.5,0,0); if(!world.spawnEntity(root)) return;
        part(root,o,Blocks.SPRUCE_PLANKS,-.75,0,-.4,1.5,.9,.8);
        part(root,o,Blocks.SMOOTH_QUARTZ,-.8,.9,-.45,1.6,.1,.9);
        part(root,o,Blocks.EMERALD_BLOCK,-.8,1,-.46,1.6,.05,.06);
        part(root,o,Blocks.BARREL,-.3,1.02,-.2,.6,.6,.6);
    }
    private static void part(InteractionEntity root,ShopOutlet o,Block block,double x,double y,double z,double sx,double sy,double sz) {
        DisplayEntity.BlockDisplayEntity e=new DisplayEntity.BlockDisplayEntity(EntityType.BLOCK_DISPLAY,root.getWorld()); tag(e,o);
        NbtCompound n=new NbtCompound(); e.writeNbt(n); NbtCompound b=new NbtCompound(); b.putString("Name",Registries.BLOCK.getId(block).toString()); n.put("block_state",b);
        NbtCompound t=new NbtCompound(); t.put("translation",vec(CounterRotation.x(o.facing(),x,z),y,CounterRotation.z(o.facing(),x,z))); t.put("scale",vec(sx,sy,sz)); t.put("left_rotation",vec(0,-Math.sin(o.facing()*Math.PI/4),0,Math.cos(o.facing()*Math.PI/4)));t.put("right_rotation",vec(0,0,0,1));n.put("transformation",t);e.readNbt(n);
        e.refreshPositionAndAngles(root.getX(),root.getY(),root.getZ(),0,0); if(root.getWorld().spawnEntity(e) && !e.startRiding(root,true)) e.discard();
    }
    private static void spawnSign(ServerWorld world,ShopOutlet o) {
        DisplayEntity.TextDisplayEntity e=new DisplayEntity.TextDisplayEntity(EntityType.TEXT_DISPLAY,world); tag(e,o); e.addCommandTag(SIGN);
        NbtCompound n=new NbtCompound();e.writeNbt(n);n.putString("text",Text.Serializer.toJson(Text.literal("✦ 商店 ✦\n"+o.name()+"\n物品买卖 · 银行结算\n右键柜台查看商品").formatted(net.minecraft.util.Formatting.GREEN)));
        n.putString("billboard","center");n.putString("alignment","center");n.putInt("line_width",220);n.putInt("background",0x99001810);n.putBoolean("shadow",true);n.putBoolean("see_through",false);n.putByte("text_opacity",(byte)-1);e.readNbt(n);
        e.refreshPositionAndAngles(o.x()+.5,o.y()+3.3,o.z()+.5,0,0);world.spawnEntity(e);
    }
    private static boolean matchesFacing(Entity e, int facing) {
        String prefix = "shopmod_facing_";
        var tags = e.getCommandTags();
        return tags.contains(prefix + CounterRotation.normalize(facing)) || (CounterRotation.normalize(facing) == 0 && tags.stream().noneMatch(t -> t.startsWith(prefix)));
    }
    private static NbtList vec(double... values) { NbtList list=new NbtList();for(double v:values)list.add(NbtFloat.of((float)v));return list; }
}
