package com.ravex.civilization.npc;

import com.ravex.civilization.RavexCiv;
import com.ravex.civilization.RavexConfig;
import com.ravex.civilization.ai.CollectWoodGoal;
import com.ravex.civilization.ai.FollowOwnerGoal;
import com.ravex.civilization.ai.GoHomeGoal;
import com.ravex.civilization.ai.GuardGoal;
import com.ravex.civilization.ai.MineBlocksGoal;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.Container;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NpcEntity extends PathfinderMob {
    private static final EntityDataAccessor<String> DATA_SKIN =
            SynchedEntityData.defineId(NpcEntity.class, EntityDataSerializers.STRING);
    private static final Set<String> KINDS = Set.of("stone", "coal", "iron", "gold", "diamond", "ores", "caving");
    private static final Set<String> FILTERS = Set.of("", "zombie", "skeleton", "creeper", "spider", "enderman", "witch", "slime");
    private static final Set<String> WOOD_TYPES = Set.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry");
    private static final ResourceLocation SMASH_ID = ResourceLocation.fromNamespaceAndPath(RavexCiv.MOD_ID, "mace_smash");

    private String npcKey = "";
    private NpcMission mission = NpcMission.IDLE;
    private int missionTarget;
    private int missionProgress;
    public int missionTicks;
    private UUID ownerId;
    private String lang = "en";
    private String collectKind = "";
    private String attackFilter = "";
    private String woodType = "";
    private boolean attackOnce;
    private boolean engaged;
    private int noTargetTicks;
    private String team = "";
    private BlockPos anchor;
    private BlockPos home;
    private UUID followNpcId;

    private boolean thinking;
    private long lastAiTick = -100000L;

    public final SimpleContainer inventory = new SimpleContainer(27);
    private final YassineCombat yassineCombat = new YassineCombat(this);

    public NpcEntity(EntityType<? extends NpcEntity> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
        for (EquipmentSlot s : new EquipmentSlot[]{EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            this.setDropChance(s, 1.0F);
        }
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 32.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_SKIN, "skin_1");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.15, true));
        this.goalSelector.addGoal(2, new CollectWoodGoal(this));
        this.goalSelector.addGoal(2, new MineBlocksGoal(this));
        this.goalSelector.addGoal(3, new GoHomeGoal(this));
        this.goalSelector.addGoal(3, new com.ravex.civilization.ai.DeliverGoal(this));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this));
        this.goalSelector.addGoal(4, new GuardGoal(this));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8) {
            @Override
            public boolean canUse() {
                return mission == NpcMission.IDLE && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, 10, true, false, this::isAutoTarget));
    }

    /** Reflex targeting (self-defence). Never creepers/endermen/wardens; STAY/GUARD only react near their post. */
    private boolean isAutoTarget(LivingEntity e) {
        if (e instanceof Creeper || e instanceof EnderMan || e instanceof Warden) return false;
        if ((mission == NpcMission.STAY || mission == NpcMission.GUARD) && anchor != null) {
            return e.distanceToSqr(Vec3.atCenterOf(anchor)) <= 144.0;
        }
        return true;
    }

    @Override
    public boolean removeWhenFarAway(double distanceSqr) {
        return false;
    }

    // ---- identity -------------------------------------------------------------------------------------------------
    public void initProfile(NpcProfile p) {
        this.npcKey = p.key();
        this.entityData.set(DATA_SKIN, p.skin());
        this.setCustomName(Component.literal(p.name()));
        this.setCustomNameVisible(true);
    }

    public String npcKey() { return npcKey; }
    public String skin() { return this.entityData.get(DATA_SKIN); }
    public boolean isYassine() { return NpcNames.YASSINE_KEY.equals(npcKey); }
    public String npcName() { return getCustomName() != null ? getCustomName().getString() : "NPC"; }

    public void giveStarterKit(RandomSource r) {
        if (!RavexConfig.STARTER_KIT.get()) return;
        inventory.addItem(new ItemStack(Items.BREAD, 3 + r.nextInt(4)));
        if (isYassine()) {
            inventory.addItem(new ItemStack(Items.STONE_AXE));
            inventory.addItem(new ItemStack(Items.STONE_SWORD));
            inventory.addItem(new ItemStack(Items.STONE_PICKAXE));
        } else {
            if (r.nextBoolean()) inventory.addItem(new ItemStack(Items.STONE_AXE));
            if (r.nextInt(3) == 0) inventory.addItem(new ItemStack(Items.WOODEN_SWORD));
            if (r.nextBoolean()) inventory.addItem(new ItemStack(Items.WOODEN_PICKAXE));
        }
    }

    /** Yassine's signature loadout. These cannot be crafted from scratch, so he starts with them (once). */
    public void giveYassineCombatKit() {
        if (!RavexConfig.YASSINE_COMBAT_KIT.get()) return;
        inventory.addItem(new ItemStack(Items.MACE));
        inventory.addItem(new ItemStack(Items.WIND_CHARGE, 16));
        inventory.addItem(new ItemStack(Items.SHIELD));
        inventory.addItem(new ItemStack(Items.ELYTRA));
        inventory.addItem(new ItemStack(Items.FIREWORK_ROCKET, 24));
    }

    // ---- simple accessors -----------------------------------------------------------------------------------------
    public NpcMission mission() { return mission; }
    public int missionTarget() { return missionTarget; }
    public int missionProgress() { return missionProgress; }
    public String collectKind() { return collectKind; }
    public String woodType() { return woodType; }
    public String lang() { return lang; }
    public void setLang(String l) { this.lang = l; }
    public boolean isThinking() { return thinking; }
    public void setThinking(boolean t) { this.thinking = t; }
    public long lastAiTick() { return lastAiTick; }
    public void setLastAiTick(long t) { this.lastAiTick = t; }
    public BlockPos home() { return home; }
    public void setHome(BlockPos p) { this.home = p.immutable(); }
    public BlockPos anchor() { return anchor; }
    public String team() { return team; }

    private String t(String en, String fr, String ar) { return Lang.t(lang, en, fr, ar); }

    public ServerPlayer owner() {
        if (ownerId == null || !(level() instanceof ServerLevel sl)) return null;
        return sl.getServer().getPlayerList().getPlayer(ownerId);
    }

    public Entity followedEntity() {
        if (mission == NpcMission.FOLLOW_NPC || mission == NpcMission.HELP_FIGHT) {
            if (followNpcId != null && level() instanceof ServerLevel sl) return sl.getEntity(followNpcId);
            return null;
        }
        return owner();
    }

    // ---- actions --------------------------------------------------------------------------------------------------
    private void begin(NpcMission m, ServerPlayer p) {
        mission = m;
        missionProgress = 0;
        missionTicks = 0;
        if (p != null) ownerId = p.getUUID();
        getNavigation().stop();
    }

    /**
     * Applies a whitelisted action. Returns null on success or a user-facing explanation of why the NPC cannot do it.
     * (AI output is only ever mapped onto this fixed set; it can never run arbitrary commands.)
     */
    public String applyAction(String type, int count, String arg, ServerPlayer player) {
        if (arg == null) arg = "";
        switch (type) {
            case "follow" -> { begin(NpcMission.FOLLOW, player); return null; }
            case "protect" -> { begin(NpcMission.PROTECT, player); return null; }
            case "come" -> { begin(NpcMission.COME, player); return null; }
            case "stop" -> { begin(NpcMission.IDLE, null); setTarget(null); return null; }
            case "stop_attack" -> { begin(NpcMission.IDLE, null); setTarget(null); return null; }
            case "stay" -> { begin(NpcMission.STAY, player); anchor = blockPosition(); setTarget(null); return null; }
            case "guard" -> { begin(NpcMission.GUARD, player); anchor = blockPosition(); return null; }
            case "set_home" -> { home = player.blockPosition().immutable(); return null; }
            case "return_home" -> {
                if (home == null) return t("I don't have a home yet. Tell me \"this is your home\" where you want it.",
                        "Je n'ai pas encore de maison. Dis-moi « c'est ta maison » à l'endroit voulu.",
                        "ليس لدي منزل بعد. قل لي \"هذا بيتك\" في المكان المطلوب.");
                begin(NpcMission.RETURN_HOME, player);
                return null;
            }
            case "attack_mobs" -> {
                String f = arg.toLowerCase();
                begin(NpcMission.ATTACK_MOBS, player);
                attackOnce = count == 1;
                engaged = false;
                noTargetTicks = 0;
                attackFilter = "";
                if (f.equals("look")) {
                    LivingEntity lt = lookedAtHostile(player);
                    if (lt == null) {
                        begin(NpcMission.IDLE, null);
                        return t("I can't tell which mob you mean - look straight at it and tell me again.",
                                "Je ne vois pas quel monstre tu veux dire - regarde-le et redis-le.",
                                "لا أعرف أي وحش تقصد - انظر إليه وأعد الطلب.");
                    }
                    setTarget(lt);
                    engaged = true;
                    attackOnce = true;
                } else {
                    attackFilter = FILTERS.contains(f) ? f : "";
                }
                return null;
            }
            case "collect_wood" -> {
                String w = arg.toLowerCase().replace(' ', '_');
                begin(NpcMission.COLLECT_WOOD, player);
                woodType = WOOD_TYPES.contains(w) ? w : "";
                missionTarget = count > 0 ? Math.min(count, 512) : 8;
                return null;
            }
            case "deliver" -> {
                if (!hasDeliverable()) return t("I'm not carrying anything to give you.",
                        "Je ne porte rien à te donner.", "لا أحمل شيئًا لأعطيك إياه.");
                begin(NpcMission.DELIVER, player);
                return null;
            }
            case "collect" -> {
                String kind = arg.toLowerCase();
                if (!KINDS.contains(kind)) return t("I don't know how to collect that.", "Je ne sais pas récolter ça.",
                        "لا أعرف كيف أجمع ذلك.");
                if (!hasPickaxe()) return t("I need a pickaxe for that and I don't have one (I can't craft yet).",
                        "Il me faut une pioche pour ça et je n'en ai pas (je ne sais pas encore fabriquer).",
                        "أحتاج فأسًا (بيكاكس) لذلك وليس معي واحد (لا أستطيع الصنع بعد).");
                if (!canHarvest(sampleBlock(kind))) return t("My pickaxe isn't good enough for " + kind + ".",
                        "Ma pioche n'est pas assez bonne pour ça (" + kind + ").",
                        "الفأس لدي ليس جيدًا بما يكفي لهذا (" + kind + ").");
                begin(NpcMission.COLLECT_BLOCKS, player);
                collectKind = kind;
                missionTarget = count > 0 ? Math.min(count, 512) : defaultCount(kind);
                return null;
            }
            case "follow_npc", "help_fight" -> {
                NpcEntity other = findOtherNpc(arg);
                if (other == null) return t("I can't find an NPC called '" + arg + "'.",
                        "Je ne trouve pas de PNJ nommé « " + arg + " ».", "لا أجد شخصية باسم " + arg + ".");
                begin(type.equals("follow_npc") ? NpcMission.FOLLOW_NPC : NpcMission.HELP_FIGHT, player);
                followNpcId = other.getUUID();
                return null;
            }
            case "join_team" -> { team = "team_" + player.getGameProfile().getName(); return null; }
            case "leave_team" -> { team = ""; return null; }
            default -> {
                return t("I can't do that yet - that skill isn't implemented in this version.",
                        "Je ne peux pas encore faire ça - cette capacité n'est pas implémentée.",
                        "لا أستطيع فعل ذلك بعد - هذه القدرة غير مُنفّذة.");
            }
        }
    }

    private NpcEntity findOtherNpc(String name) {
        if (name.isBlank() || !(level() instanceof ServerLevel sl)) return null;
        for (NpcEntity n : sl.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(NpcEntity.class), e -> e.isAlive())) {
            if (n != this && NpcNames.mentions(n.npcName(), name)) return n;
        }
        return null;
    }

    private static int defaultCount(String kind) {
        return switch (kind) {
            case "stone" -> 32;
            case "coal" -> 16;
            case "iron" -> 8;
            case "gold" -> 4;
            case "diamond" -> 3;
            case "caving" -> 24;
            default -> 16;
        };
    }

    private static BlockState sampleBlock(String kind) {
        return switch (kind) {
            case "stone" -> Blocks.STONE.defaultBlockState();
            case "iron" -> Blocks.IRON_ORE.defaultBlockState();
            case "gold" -> Blocks.GOLD_ORE.defaultBlockState();
            case "diamond" -> Blocks.DIAMOND_ORE.defaultBlockState();
            default -> Blocks.COAL_ORE.defaultBlockState();
        };
    }

    // ---- mission callbacks ----------------------------------------------------------------------------------------
    public void onLogCollected() {
        missionProgress++;
        if (missionProgress >= missionTarget) {
            finishAndReturn(String.format(t("Done! I collected %d logs.", "Terminé ! J'ai récolté %d bûches.",
                    "تم! جمعت %d قطعة خشب."), missionProgress));
        } else if (missionProgress % 8 == 0) {
            say(String.format(t("Progress: %d/%d logs.", "Progression : %d/%d bûches.", "التقدم: %d من %d قطعة خشب."),
                    missionProgress, missionTarget));
        }
    }

    public void onBlockCollected() {
        missionProgress++;
        if (missionProgress < missionTarget && missionProgress % (missionTarget >= 16 ? 8 : 1) == 0) {
            say(String.format(t("Progress: %d/%d.", "Progression : %d/%d.", "التقدم: %d من %d."),
                    missionProgress, missionTarget));
        }
    }

    public void finishMission(String message) {
        mission = NpcMission.IDLE;
        getNavigation().stop();
        say(message);
    }

    /** Ends the current job, then brings the items to the player (or home) if there is something to deliver. */
    public void finishAndReturn(String message) {
        say(message);
        getNavigation().stop();
        setTarget(null);
        missionTicks = 0;
        ServerPlayer o = owner();
        if (o != null && o.level() == level() && hasDeliverable()) {
            mission = NpcMission.DELIVER;
        } else if (home != null && distanceToSqr(Vec3.atCenterOf(home)) > 36.0) {
            mission = NpcMission.RETURN_HOME;
        } else {
            mission = NpcMission.IDLE;
        }
    }

    public boolean hasDeliverable() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && !isEssential(s)) return true;
        }
        return false;
    }

    /**
     * Real transfer to the player: each stack is removed from the NPC first, then added to the player's inventory;
     * whatever does not fit is dropped at the player's feet (never deleted, never duplicated).
     * Returns {given, dropped}.
     */
    public int[] giveAllTo(ServerPlayer p) {
        int given = 0, dropped = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty() || isEssential(s)) continue;
            ItemStack move = s.copy();
            int before = move.getCount();
            inventory.setItem(i, ItemStack.EMPTY);
            p.getInventory().add(move);
            given += before - move.getCount();
            if (!move.isEmpty()) {
                dropped += move.getCount();
                p.drop(move, false);
            }
        }
        return new int[]{given, dropped};
    }

    private LivingEntity lookedAtHostile(ServerPlayer p) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(40.0));
        AABB box = p.getBoundingBox().expandTowards(look.scale(40.0)).inflate(1.0);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e instanceof Enemy && !(e instanceof NpcEntity))) {
            java.util.Optional<Vec3> hit = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (hit.isPresent()) {
                double d = eye.distanceToSqr(hit.get());
                if (d < bd) { bd = d; best = e; }
            }
        }
        return best;
    }

    public void say(String message) {
        MinecraftServer server = level().getServer();
        if (server == null) return;
        Component c = Component.literal("<" + npcName() + "> ").withStyle(ChatFormatting.AQUA)
                .append(Component.literal(message).withStyle(ChatFormatting.WHITE));
        server.getPlayerList().broadcastSystemMessage(c, false);
    }

    // ---- inventory & tools ----------------------------------------------------------------------------------------
    public String inventorySummary() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(s.getItem()).getPath(), s.getCount(), Integer::sum);
        }
        if (counts.isEmpty()) return "empty";
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (n++ >= 12) { sb.append(", ..."); break; }
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append(" x").append(e.getValue());
        }
        return sb.toString();
    }

    public boolean inventoryFull() {
        return !inventory.canAddItem(new ItemStack(Items.COBBLESTONE));
    }

    public boolean hasPickaxe() {
        if (getMainHandItem().getItem() instanceof PickaxeItem) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).getItem() instanceof PickaxeItem) return true;
        }
        return false;
    }

    /** True if some tool I own (or bare hands) would give drops for this block. */
    public boolean canHarvest(BlockState st) {
        if (!st.requiresCorrectToolForDrops()) return true;
        if (getMainHandItem().isCorrectToolForDrops(st)) return true;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i).isCorrectToolForDrops(st)) return true;
        }
        return false;
    }

    /** Puts the best suitable tool in the main hand (moving items, never copying). False if none gives drops. */
    public boolean equipToolFor(BlockState st) {
        boolean needs = st.requiresCorrectToolForDrops();
        ItemStack main = getMainHandItem();
        boolean mainOk = !needs || main.isCorrectToolForDrops(st);
        float bestSpeed = mainOk ? main.getDestroySpeed(st) : -1.0F;
        int best = -1;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty()) continue;
            if (needs && !s.isCorrectToolForDrops(st)) continue;
            float sp = s.getDestroySpeed(st);
            if (sp > bestSpeed + 0.01F) {
                bestSpeed = sp;
                best = i;
            }
        }
        if (best >= 0) {
            moveToSlot(best, EquipmentSlot.MAINHAND);
            return true;
        }
        return mainOk;
    }

    /** Removes exactly one of the item from the inventory. False if none. */
    public boolean consumeOne(Item item) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.is(item)) {
                inventory.removeItem(i, 1);
                return true;
            }
        }
        return false;
    }

    private static boolean isEssential(ItemStack s) {
        Item it = s.getItem();
        return it instanceof TieredItem || it instanceof ArmorItem || it instanceof ShieldItem || it instanceof BowItem
                || it instanceof CrossbowItem || s.has(DataComponents.FOOD) || s.is(Items.MACE) || s.is(Items.WIND_CHARGE)
                || s.is(Items.FIREWORK_ROCKET) || s.is(Items.ELYTRA) || s.is(Items.ARROW) || s.is(Items.TORCH);
    }

    /** Moves non-essential items into a chest/barrel (real transfer). Returns number of items moved. */
    public int depositInto(ServerLevel level, BlockPos chestPos) {
        BlockEntity be = level.getBlockEntity(chestPos);
        if (!(be instanceof Container chest)) return 0;
        int moved = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.isEmpty() || isEssential(s)) continue;
            int before = s.getCount();
            ItemStack left = HopperBlockEntity.addItem(null, chest, s.copy(), null);
            moved += before - left.getCount();
            inventory.setItem(i, left);
        }
        return moved;
    }

    // ---- per-tick server logic ------------------------------------------------------------------------------------
    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (tickCount % 10 == 0) missionTargeting();
        if (tickCount % RavexConfig.UPDATE_INTERVAL_TICKS.get() == 0) refreshEquipment();
        shieldTactics();
        if (isYassine()) yassineCombat.tick();
    }

    private boolean matchesFilter(LivingEntity m) {
        String f = mission == NpcMission.ATTACK_MOBS ? attackFilter : "";
        return switch (f) {
            case "zombie" -> m instanceof Zombie;
            case "skeleton" -> m instanceof AbstractSkeleton;
            case "creeper" -> m instanceof Creeper;
            case "spider" -> m instanceof Spider;
            case "enderman" -> m instanceof EnderMan;
            case "witch" -> m instanceof Witch;
            case "slime" -> m instanceof Slime;
            default -> !(m instanceof Creeper) && !(m instanceof EnderMan) && !(m instanceof Warden);
        };
    }

    /** Nearest valid hostile (Enemy) within radius of center. Never players or NPCs. */
    private LivingEntity findHostile(Vec3 center, double radius) {
        List<LivingEntity> list = level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                m -> m.isAlive() && m instanceof Enemy && !(m instanceof NpcEntity) && !(m instanceof Player) && matchesFilter(m));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity m : list) {
            double d = distanceToSqr(m);
            if (d < bd) { bd = d; best = m; }
        }
        return best;
    }

    /** ATTACK_MOBS / GUARD / PROTECT / HELP_FIGHT: actively pick a hostile target. */
    private void missionTargeting() {
        LivingEntity cur = getTarget();
        boolean hasTarget = cur != null && cur.isAlive();

        if (mission == NpcMission.ATTACK_MOBS) {
            if (hasTarget) { engaged = true; noTargetTicks = 0; return; }
            if (engaged && attackOnce) {
                finishMission(t("Target down.", "Cible éliminée.", "تم القضاء على الهدف."));
                return;
            }
            int radius = RavexConfig.ATTACK_SEARCH_RADIUS.get();
            LivingEntity best = findHostile(position(), radius);
            if (best != null) {
                setTarget(best);
                engaged = true;
                noTargetTicks = 0;
                return;
            }
            noTargetTicks++;
            String what = attackFilter.isEmpty() ? "hostile mobs" : attackFilter;
            if (!engaged && noTargetTicks >= 3) {
                finishMission(String.format(t("I can't find any %s within %d blocks.",
                        "Je ne trouve aucun %s dans un rayon de %d blocs.", "لا أجد أي هدف (%s) ضمن %d بلوكات."), what, radius));
            } else if (engaged && noTargetTicks >= 60) {
                finishMission(t("The area looks clear now.", "La zone semble dégagée.", "المنطقة تبدو آمنة الآن."));
            }
            return;
        }
        if (hasTarget) return;

        Vec3 center = null;
        if (mission == NpcMission.GUARD && anchor != null) {
            center = Vec3.atCenterOf(anchor);
        } else if (mission == NpcMission.PROTECT) {
            ServerPlayer o = owner();
            if (o != null && o.level() == level()) center = o.position();
        } else if (mission == NpcMission.HELP_FIGHT) {
            Entity f = followedEntity();
            if (f instanceof NpcEntity other && other.isAlive()) {
                LivingEntity ot = other.getTarget();
                if (ot != null && ot.isAlive()) {
                    setTarget(ot);
                    return;
                }
                center = other.position();
            }
        }
        if (center == null) return;
        LivingEntity best = findHostile(center, 12.0);
        if (best != null) setTarget(best);
    }

    private void refreshEquipment() {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (s.getItem() instanceof ArmorItem a) {
                EquipmentSlot slot = a.getType().getSlot();
                if (getItemBySlot(slot).isEmpty()) moveToSlot(i, slot);
            } else if (s.getItem() instanceof ShieldItem && getOffhandItem().isEmpty()) {
                moveToSlot(i, EquipmentSlot.OFFHAND);
            }
        }
        if (isYassine() && !getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)) {
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                if (inventory.getItem(i).is(Items.ELYTRA)) {
                    moveToSlot(i, EquipmentSlot.CHEST);
                    break;
                }
            }
        }
        boolean fighting = getTarget() != null;
        int best = -1;
        float bestScore = toolScore(getMainHandItem(), fighting);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            float sc = toolScore(inventory.getItem(i), fighting);
            if (sc > bestScore) {
                bestScore = sc;
                best = i;
            }
        }
        if (best >= 0) moveToSlot(best, EquipmentSlot.MAINHAND);
    }

    private float toolScore(ItemStack s, boolean fighting) {
        if (s.isEmpty()) return 0;
        if (fighting) {
            if (isYassine() && s.is(Items.MACE)) return 20; // only Yassine fights with the Mace
            if (s.getItem() instanceof SwordItem sw) return 4 + sw.getTier().getAttackDamageBonus();
            if (s.getItem() instanceof AxeItem ax) return 3 + ax.getTier().getAttackDamageBonus();
            return 0;
        }
        if (mission == NpcMission.COLLECT_WOOD && s.getItem() instanceof AxeItem ax) return 1 + ax.getTier().getSpeed();
        return 0;
    }

    private void moveToSlot(int invIndex, EquipmentSlot slot) {
        ItemStack taken = inventory.removeItem(invIndex, 1);
        if (taken.isEmpty()) return;
        ItemStack old = getItemBySlot(slot);
        setItemSlot(slot, taken);
        if (!old.isEmpty()) {
            ItemStack left = inventory.addItem(old);
            if (!left.isEmpty()) spawnAtLocation(left);
        }
    }

    /** Raise the shield while an enemy is close (with short gaps so attacks still happen). */
    private void shieldTactics() {
        if (!(getOffhandItem().getItem() instanceof ShieldItem)) return;
        LivingEntity t = getTarget();
        if (t != null && t.isAlive() && distanceToSqr(t) < 25.0 && tickCount % 60 < 40) {
            if (!isUsingItem()) startUsingItem(InteractionHand.OFF_HAND);
        } else if (isUsingItem() && getUsedItemHand() == InteractionHand.OFF_HAND) {
            stopUsingItem();
        }
    }

    /** Vanilla mace smash bonus (4/block for the first 3 blocks fallen, 2/block for the next 5, 1/block after). */
    @Override
    public boolean doHurtTarget(Entity target) {
        AttributeInstance dmg = getAttribute(Attributes.ATTACK_DAMAGE);
        float bonus = 0;
        if (dmg != null && isYassine() && getMainHandItem().is(Items.MACE) && !onGround() && fallDistance > 1.5F) {
            float f = fallDistance;
            bonus = f <= 3 ? 4 * f : (f <= 8 ? 12 + 2 * (f - 3) : 22 + (f - 8));
            dmg.removeModifier(SMASH_ID);
            dmg.addTransientModifier(new AttributeModifier(SMASH_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
        }
        boolean r = super.doHurtTarget(target);
        if (bonus > 0 && dmg != null) {
            dmg.removeModifier(SMASH_ID);
            fallDistance = 0; // the mace cancels the fall, as in vanilla
        }
        return r;
    }

    // ---- Yassine flight hooks -----------------------------------------------------------------------------------
    public void startGliding() { setSharedFlag(7, true); }
    public void stopGliding() { setSharedFlag(7, false); }

    @Override
    public void travel(Vec3 travelVector) {
        if (isYassine() && isFallFlying()) yassineCombat.applyRotation();
        super.travel(travelVector);
    }

    // ---- death / persistence --------------------------------------------------------------------------------------
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide && isYassine() && level().getServer() != null) {
            NpcWorldData data = NpcWorldData.get(level().getServer());
            if (uuid.equals(data.yassineEntity())) data.setYassineEntity(null);
        }
    }

    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.removeItemNoUpdate(i);
            if (!s.isEmpty()) spawnAtLocation(s);
        }
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("NpcKey", npcKey);
        tag.putString("NpcSkin", skin());
        tag.putString("NpcLang", lang);
        tag.putString("Mission", mission.name());
        tag.putInt("MissionTarget", missionTarget);
        tag.putInt("MissionProgress", missionProgress);
        tag.putString("CollectKind", collectKind);
        tag.putString("AttackFilter", attackFilter);
        tag.putString("WoodType", woodType);
        tag.putString("Team", team);
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        if (followNpcId != null) tag.putUUID("FollowNpc", followNpcId);
        if (home != null) tag.putLong("Home", home.asLong());
        if (anchor != null) tag.putLong("Anchor", anchor.asLong());
        tag.put("NpcInventory", inventory.createTag(registryAccess()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        npcKey = tag.getString("NpcKey");
        if (tag.contains("NpcSkin")) entityData.set(DATA_SKIN, tag.getString("NpcSkin"));
        if (tag.contains("NpcLang")) lang = tag.getString("NpcLang");
        try {
            mission = NpcMission.valueOf(tag.getString("Mission"));
        } catch (IllegalArgumentException ex) {
            mission = NpcMission.IDLE;
        }
        missionTarget = tag.getInt("MissionTarget");
        missionProgress = tag.getInt("MissionProgress");
        collectKind = tag.getString("CollectKind");
        attackFilter = tag.getString("AttackFilter");
        woodType = tag.getString("WoodType");
        team = tag.getString("Team");
        if (tag.hasUUID("Owner")) ownerId = tag.getUUID("Owner");
        if (tag.hasUUID("FollowNpc")) followNpcId = tag.getUUID("FollowNpc");
        if (tag.contains("Home")) home = BlockPos.of(tag.getLong("Home"));
        if (tag.contains("Anchor")) anchor = BlockPos.of(tag.getLong("Anchor"));
        if (tag.contains("NpcInventory", Tag.TAG_LIST)) {
            inventory.fromTag(tag.getList("NpcInventory", Tag.TAG_COMPOUND), registryAccess());
        }
        if (hasCustomName()) setCustomNameVisible(true);
    }
}
