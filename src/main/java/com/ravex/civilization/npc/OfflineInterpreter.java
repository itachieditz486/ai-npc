package com.ravex.civilization.npc;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline command understanding (no AI, no internet). Regex based, English / French / Arabic, tolerant of wording.
 * Extracts: action, quantity ("32", "2 stacks"), resource (wood type / ore kind), mob type, "nearest"/"that" targeting.
 * Replies are prefixed "[offline]" by the caller so they are never mistaken for AI output. Order of checks matters.
 */
public final class OfflineInterpreter {
    public record Result(String reply, String action, int count, String arg) {}

    private static final int F = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.DOTALL;

    private static Pattern p(String re) { return Pattern.compile(re, F); }

    private static final Pattern NUMBER = Pattern.compile("\\d+");
    private static final Pattern STACK = p("(stacks?|piles?|ستاك)");
    private static final Pattern STOP_ATTACK = p("(stop|cease|don'?t|do not|arr[êe]te|توقف|لا).{0,15}(attack|fight|attaqu|combat|الهجوم|هاجم|تهاجم|القتال|قاتل)");
    private static final Pattern STOP_FOLLOW = p("(stop|don'?t|do not|arr[êe]te|توقف|لا).{0,15}(follow|suivre|suis|اتباع|تتبع|تتبعني|يتبع)");
    private static final Pattern STAY = p("(\\bstay\\b|wait here|don'?t move|stand here|\\breste\\b|ne bouge|ابق|إبق|قف هنا)");
    private static final Pattern STOP = p("(\\bstop\\b|\\bwait\\b|\\bhalt\\b|arr[êe]te|stoppe|attends|توقف|قف|استنى)");
    private static final Pattern HELP_FIGHT = p("(?=.*(help|aide|ساعد))(?=.*(fight|combat|battre|قتال|معركة|قاتل))");
    private static final Pattern JOIN_TEAM = p("(join|rejoins|rejoindre|انضم).{0,12}(team|[ée]quipe|فريق)");
    private static final Pattern LEAVE_TEAM = p("(leave|quitte|quitter|غادر|اترك).{0,12}(team|[ée]quipe|فريق)");
    private static final Pattern GUARD = p("(guard|garde[rz]?|احرس|defend|d[ée]fend[sez]?|protect|prot[èe]ge[rz]?|دافع|احم).{0,20}(this (location|area|place|spot)|here|cet endroit|cette zone|ce lieu|\\bici\\b|هذا المكان|هذه المنطقة|هنا)");
    private static final Pattern PROTECT = p("(protect|defend|guard me|prot[èe]ge|d[ée]fends|احمني|احمي|دافع عني|حماية)");
    private static final Pattern SET_HOME = p("(this is|set|make).{0,12}home|home.{0,6}here|c'est ta maison|ta maison.{0,6}ici|هذا (بيتك|منزلك)");
    // deliver what you carry
    private static final Pattern DELIVER_GENERIC = p("(give|bring|hand|deliver|donne|apporte|remets|أعط|اعط|سلم|ائت).{0,25}(resources?|items?|everything|stuff|collected|ressources|\\btout\\b|الموارد|كل شيء)");
    private static final Pattern GIVE_VERB = p("(give me|hand me|hand over|deliver|donne[- ]moi|remets[- ]moi|أعطني|اعطني|سلمني)");
    private static final Pattern RETURN_HOME = p("(go|return|get|head|come|walk|run|retourne|rentre|ارجع|عد|روح).{0,15}(home|maison|chez toi|البيت|المنزل|للبيت|للمنزل|بيتك|منزلك)");
    private static final Pattern COME = p("(come (here|to me|back)|return to me|get (over )?here|\\bviens\\b|reviens|تعال|عد إلي|عد الي|ارجع إلي|ارجع الي)");
    // attack
    private static final Pattern ATTACK_STRONG = p("(attack|kill|fight|slay|attaque|tue[rz]?|combat|هاجم|اقتل|قاتل)");
    private static final Pattern ATTACK_WEAK = p("(hunt|clear|destroy|chasse|[ée]limine|اقض)");
    private static final Pattern MOB_WORD = p("(mobs?|monsters?|hostiles?|enemies|enemy|zombies?|skeletons?|creepers?|spiders?|endermen|enderman|witch|slimes?|monstres?|ennemis|zombis?|squelettes?|araign|وحوش|وحش|أعداء|اعداء|زومبي|زومبى|هياكل|هيكل|كريبر|عناكب|عنكبوت|اندرمان|إندرمان)");
    private static final Pattern ZOMBIE = p("zombi|زومب");
    private static final Pattern SKELETON = p("skelet|squelet|هيك");
    private static final Pattern CREEPER = p("creeper|كريبر");
    private static final Pattern SPIDER = p("spider|araign|عنكب");
    private static final Pattern ENDERMAN = p("ender|اندرمان|إندرمان");
    private static final Pattern WITCH = p("witch|sorci|ساحر");
    private static final Pattern SLIME = p("slime|سلايم");
    private static final Pattern NEAREST = p("(nearest|closest|plus proche|أقرب|اقرب)");
    private static final Pattern THAT = p("\\b(that|this one|those)\\b|celui-l|ce monstre|ce mob|ذلك|هذا الوحش|ذاك");
    // resources
    private static final Pattern DIAMOND = p("diamond|diamant|ألماس|الماس");
    private static final Pattern GOLD = p("\\bgold|l'or\\b|ذهب");
    private static final Pattern IRON = p("\\biron|\\bfer\\b|حديد");
    private static final Pattern COAL = p("\\bcoal|charbon|فحم");
    private static final Pattern ORES = p("\\bores?\\b|minerais?|خامات|معادن");
    private static final Pattern STONE = p("\\bstones?\\b|cobble|pierre|حجر|حجارة");
    private static final Pattern WOOD = p("(\\bwood|\\blogs?\\b|\\btrees?\\b|\\bbois\\b|arbres?|b[uû]ches?|tronc|خشب|شجر|أشجار|اشجار)");
    private static final Pattern DARK_OAK = p("dark[ _-]?oak|ch[êe]ne noir|بلوط داكن");
    private static final Pattern OAK = p("\\boak|ch[êe]ne|بلوط");
    private static final Pattern SPRUCE = p("spruce|[ée]pic[ée]a|sapin|تنوب|صنوبر");
    private static final Pattern BIRCH = p("birch|bouleau|بتولا");
    private static final Pattern JUNGLE = p("jungle");
    private static final Pattern ACACIA = p("acacia|أكاسيا|اكاسيا");
    private static final Pattern MANGROVE = p("mangrove|manglier|المانغروف");
    private static final Pattern CHERRY = p("cherry|cerisier|كرز");
    private static final Pattern CAVING = p("(caving|spelunk|explore.{0,10}caves?|\\bcaves?\\b|grottes?|sp[ée]l[ée]o|كهف|الكهوف)");
    private static final Pattern GO_MINING = p("(go mining|\\bmining\\b|va miner|\\bminer\\b|تعدين)");
    private static final Pattern FOLLOW = p("(follow|come with|\\bsuis\\b|suivre|اتبع|تبع)");
    private static final Pattern UNSUPPORTED = p("(build|house|craft|farm|smelt|cook|nether|dragon|construis|maison|fabrique|cuis|ابن|بناء|بيت|اصنع|مزرعة)");
    private static final Pattern GREETING = p("(hello|\\bhi\\b|\\bhey\\b|salut|bonjour|coucou|مرحبا|أهلا|اهلا|سلام|السلام)");

    private static Result r(String lang, String action, int count, String arg, String en, String fr, String ar) {
        return new Result(Lang.t(lang, en, fr, ar), action, count, arg);
    }

    static String woodType(String t) {
        if (DARK_OAK.matcher(t).find()) return "dark_oak";
        if (OAK.matcher(t).find()) return "oak";
        if (SPRUCE.matcher(t).find()) return "spruce";
        if (BIRCH.matcher(t).find()) return "birch";
        if (JUNGLE.matcher(t).find()) return "jungle";
        if (ACACIA.matcher(t).find()) return "acacia";
        if (MANGROVE.matcher(t).find()) return "mangrove";
        if (CHERRY.matcher(t).find()) return "cherry";
        return "";
    }

    static String mobType(String t) {
        if (ZOMBIE.matcher(t).find()) return "zombie";
        if (SKELETON.matcher(t).find()) return "skeleton";
        if (CREEPER.matcher(t).find()) return "creeper";
        if (SPIDER.matcher(t).find()) return "spider";
        if (ENDERMAN.matcher(t).find()) return "enderman";
        if (WITCH.matcher(t).find()) return "witch";
        if (SLIME.matcher(t).find()) return "slime";
        return "";
    }

    public static Result interpret(String text, String lang) {
        String t = text.toLowerCase(Locale.ROOT);
        Matcher num = NUMBER.matcher(t);
        int n = num.find() ? Integer.parseInt(num.group().length() > 4 ? "512" : num.group()) : 0;
        if (STACK.matcher(t).find()) n = (n == 0 ? 1 : n) * 64;

        if (STOP_ATTACK.matcher(t).find())
            return r(lang, "stop_attack", 0, "", "Okay, I'll stop attacking.", "D'accord, j'arrête d'attaquer.", "حسنًا، سأتوقف عن الهجوم.");
        if (STOP_FOLLOW.matcher(t).find())
            return r(lang, "stop", 0, "", "Okay, I'll stop following you.", "D'accord, je ne te suis plus.", "حسنًا، لن أتبعك.");
        if (HELP_FIGHT.matcher(t).find())
            return r(lang, "help_fight", 0, "", "On my way to help!", "J'arrive pour aider !", "قادم للمساعدة!");
        if (LEAVE_TEAM.matcher(t).find())
            return r(lang, "leave_team", 0, "", "I left your team.", "J'ai quitté ton équipe.", "غادرت فريقك.");
        if (JOIN_TEAM.matcher(t).find())
            return r(lang, "join_team", 0, "", "I joined your team.", "J'ai rejoint ton équipe.", "انضممت إلى فريقك.");
        if (GUARD.matcher(t).find())
            return r(lang, "guard", 0, "", "I'll guard this spot.", "Je garde cet endroit.", "سأحرس هذا المكان.");
        if (PROTECT.matcher(t).find())
            return r(lang, "protect", 0, "", "I'll stay close and protect you.", "Je reste près de toi et je te protège.", "سأبقى قريبًا وأحميك.");
        if (SET_HOME.matcher(t).find())
            return r(lang, "set_home", 0, "", "Okay, this is my home now.", "D'accord, c'est ma maison maintenant.", "حسنًا، هذا منزلي الآن.");

        boolean resourceWord = DIAMOND.matcher(t).find() || GOLD.matcher(t).find() || IRON.matcher(t).find()
                || COAL.matcher(t).find() || ORES.matcher(t).find() || STONE.matcher(t).find() || WOOD.matcher(t).find();
        if (DELIVER_GENERIC.matcher(t).find() || (n == 0 && GIVE_VERB.matcher(t).find() && resourceWord))
            return r(lang, "deliver", 0, "", "Here, let me give you what I'm carrying.", "Tiens, je te donne ce que je porte.", "تفضل، سأعطيك ما أحمله.");

        if (RETURN_HOME.matcher(t).find())
            return r(lang, "return_home", 0, "", "Heading home.", "Je rentre à la maison.", "سأعود إلى المنزل.");
        if (STAY.matcher(t).find())
            return r(lang, "stay", 0, "", "Okay, staying here.", "D'accord, je reste ici.", "حسنًا، سأبقى هنا.");
        if (STOP.matcher(t).find())
            return r(lang, "stop", 0, "", "Okay, stopping.", "D'accord, j'arrête.", "حسنًا، توقفت.");
        if (COME.matcher(t).find())
            return r(lang, "come", 0, "", "Coming!", "J'arrive !", "قادم!");

        boolean mob = MOB_WORD.matcher(t).find();
        if (ATTACK_STRONG.matcher(t).find() || (ATTACK_WEAK.matcher(t).find() && mob)) {
            String type = mobType(t);
            boolean once = NEAREST.matcher(t).find();
            String arg = type;
            if (type.isEmpty() && THAT.matcher(t).find()) {
                arg = "look";
                once = true;
            }
            if (!type.isEmpty() && THAT.matcher(t).find()) once = true;
            String what = type.isEmpty() ? "hostile mobs" : type;
            return new Result(String.format(Lang.t(lang, "Going after %s.", "Je pars attaquer : %s.", "سأهاجم: %s."),
                    arg.equals("look") ? "that mob" : what), "attack_mobs", once ? 1 : 0, arg);
        }

        String kind = null;
        if (DIAMOND.matcher(t).find()) kind = "diamond";
        else if (GOLD.matcher(t).find()) kind = "gold";
        else if (IRON.matcher(t).find()) kind = "iron";
        else if (COAL.matcher(t).find()) kind = "coal";
        else if (ORES.matcher(t).find()) kind = "ores";
        else if (STONE.matcher(t).find()) kind = "stone";
        if (kind != null) {
            return new Result(String.format(Lang.t(lang, "On it - mining %s, I'll bring it to you.",
                    "Je m'en occupe - je mine : %s, je te l'apporte.", "حاضر - سأعدّن: %s وأحضره لك."), kind), "collect", n, kind);
        }
        if (CAVING.matcher(t).find())
            return r(lang, "collect", n, "caving", "Going caving for ores.", "Je pars explorer les grottes.", "سأستكشف الكهوف بحثًا عن الخامات.");
        if (GO_MINING.matcher(t).find())
            return r(lang, "collect", n, "ores", "Going mining for ores.", "Je pars miner des minerais.", "سأذهب لاستخراج الخامات.");
        if (WOOD.matcher(t).find() || !woodType(t).isEmpty()) {
            int count = n > 0 ? n : 8;
            String type = woodType(t);
            return new Result(String.format(Lang.t(lang, "On it - collecting %d logs, I'll bring them to you.",
                    "Je m'en occupe - je récolte %d bûches, je te les apporte.", "حاضر - سأجمع %d قطعة خشب وأحضرها لك."), count),
                    "collect_wood", count, type);
        }
        if (FOLLOW.matcher(t).find())
            return r(lang, "follow", 0, "", "Following you.", "Je te suis.", "أتبعك.");
        if (UNSUPPORTED.matcher(t).find())
            return r(lang, "none", 0, "", "I can't do that yet - that skill isn't implemented in this version of the mod.",
                    "Je ne peux pas encore faire ça - cette capacité n'est pas encore implémentée dans cette version du mod.",
                    "لا أستطيع فعل ذلك بعد - هذه القدرة غير مُنفّذة في هذا الإصدار من المود.");
        if (GREETING.matcher(t).find())
            return r(lang, "none", 0, "", "Hello!", "Salut !", "مرحبًا!");
        return r(lang, "none", 0, "",
                "Offline I understand: follow, stop, stay, come, go home, guard, protect, attack (zombies, skeletons...), collect wood/stone/coal/iron/gold/diamonds/ores, go caving/mining, give me the resources.",
                "Hors ligne je comprends : suis-moi, stop, reste, viens, rentre, garde, protège, attaque, récolte bois/pierre/charbon/fer/or/diamant/minerais, grottes, donne-moi les ressources.",
                "بدون إنترنت أفهم: اتبعني، توقف، ابق، تعال، ارجع للمنزل، احرس، احمني، هاجم، اجمع الخشب/الحجر/الفحم/الحديد/الذهب/الألماس، الكهوف، أعطني الموارد.");
    }

    private OfflineInterpreter() {}
}
