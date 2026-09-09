package com.example.echo.story;

import com.example.echo.game.Line;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 故事文件（JSON）解析与校验。
 *
 * <p>解析结果包含错误（拒绝加载）与警告（允许加载）两级。
 * 解析为手工 JsonNode 递归，限制嵌套深度与各类数量，防止构造恶意文件。</p>
 */
public final class StoryParser {

    public static final int MAX_SCENES = 200;
    public static final int MAX_ACTIONS_PER_SCENE = 100;
    public static final int MAX_BRANCHES_PER_ACTION = 50;
    public static final int MAX_LINES_PER_BLOCK = 300;
    public static final int MAX_LINE_TEXT = 2000;

    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9-]{0,63}$");
    private static final Set<String> STYLES = Set.of(
            "banner", "system", "ai", "narr", "warn", "item", "voice", "ending", "dim");
    private static final Set<String> GLOBAL_KEYWORDS = Set.of(
            "帮助", "背包", "物品", "重新开始", "重来", "关于", "help", "inv", "inventory", "restart", "about");

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    static {
        MAPPER.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        MAPPER.getFactory().setStreamReadConstraints(
                StreamReadConstraints.builder().maxNestingDepth(64).build());
    }

    private StoryParser() {
    }

    public record ParseResult(StoryDefinition story, List<String> errors, List<String> warnings) {
        public boolean ok() {
            return errors.isEmpty() && story != null;
        }
    }

    public static ParseResult parse(String json) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (Exception e) {
            String msg = e.getMessage();
            errors.add("JSON 语法错误：" + (msg == null ? e.getClass().getSimpleName() : msg.split("\n")[0]));
            return new ParseResult(null, errors, warnings);
        }
        if (root == null || !root.isObject()) {
            errors.add("故事文件的根节点必须是 JSON 对象");
            return new ParseResult(null, errors, warnings);
        }

        int format = root.path("format").asInt(-1);
        if (format != 1) {
            errors.add("不支持的 format（当前仅支持 1，实际：" + format + "）");
        }

        String id = text(root, "id", errors, "id");
        if (id != null && !ID_PATTERN.matcher(id).matches()) {
            errors.add("id 只能包含小写字母、数字和连字符，且以字母或数字开头（实际：\"" + id + "\"）");
            id = null;
        }
        String title = text(root, "title", errors, "title");
        String startScene = text(root, "startScene", errors, "startScene");

        JsonNode scenesNode = root.get("scenes");
        if (scenesNode == null || !scenesNode.isObject() || scenesNode.isEmpty()) {
            errors.add("scenes 必须是非空对象");
            return new ParseResult(null, errors, warnings);
        }
        if (scenesNode.size() > MAX_SCENES) {
            errors.add("场景数量超过上限 " + MAX_SCENES);
            return new ParseResult(null, errors, warnings);
        }

        Map<String, SceneDef> scenes = new LinkedHashMap<>();
        scenesNode.fields().forEachRemaining(e -> {
            SceneDef scene = parseScene(e.getKey(), e.getValue(), errors, warnings);
            if (scene != null) {
                scenes.put(e.getKey(), scene);
            }
        });

        if (startScene != null && !scenes.containsKey(startScene)) {
            errors.add("startScene 指向不存在的场景 \"" + startScene + "\"");
        }

        List<String> endings = stringList(root.get("endings"), errors, "endings");
        List<String> clues = stringList(root.get("clues"), errors, "clues");

        // 交叉校验：goto 目标存在、numbered 场景有编号选项、效果次序
        for (SceneDef scene : scenes.values()) {
            validateGoto(scene.id, scene.enter, scenes, errors);
            for (ActionDef action : scene.actions) {
                for (BranchDef b : action.branches) {
                    validateGoto(scene.id, b, scenes, errors);
                }
                if (action.self != null) {
                    validateGoto(scene.id, action.self, scenes, errors);
                }
                if (scene.numbered && action.label != null && action.keywords.isEmpty()) {
                    warnings.add("场景 \"" + scene.id + "\" 的选项 \"" + action.label + "\" 没有 keywords，只能靠数字选择");
                }
            }
            if (scene.numbered && scene.actions.stream().noneMatch(a -> a.label != null)) {
                errors.add("编号场景 \"" + scene.id + "\" 至少要有一个带 label 的选项");
            }
        }

        checkEndingEffectOrder(scenes.values(), errors);
        collectLints(scenes.values(), startScene, warnings);

        checkUnknown(root, List.of("format", "id", "title", "genre", "version", "author",
                "description", "startScene", "endings", "clues", "scenes"), errors, "顶层");

        if (!errors.isEmpty()) {
            return new ParseResult(null, errors, warnings);
        }
        StoryDefinition story = new StoryDefinition(format, id, title,
                text(root, "genre", errors, null), text(root, "version", errors, null),
                text(root, "author", errors, null), text(root, "description", errors, null),
                startScene, endings, clues, scenes);
        return new ParseResult(story, errors, warnings);
    }

    private static void checkUnknown(JsonNode node, List<String> allowed, List<String> errors, String where) {
        node.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) {
                errors.add(where + "存在未知字段 \"" + name + "\"");
            }
        });
    }

    private static String text(JsonNode node, String field, List<String> errors, String fieldLabel) {
        JsonNode v = node.get(field);
        if (v == null || !v.isTextual() || v.asText().isEmpty()) {
            if (fieldLabel != null) {
                errors.add(fieldLabel + " 必须是非空字符串");
            }
            return null;
        }
        return v.asText();
    }

    private static List<String> stringList(JsonNode node, List<String> errors, String field) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(field + " 必须是字符串数组");
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual()) {
                out.add(item.asText());
            } else {
                errors.add(field + " 中存在非字符串元素");
            }
        }
        return out;
    }

    private static SceneDef parseScene(String id, JsonNode node, List<String> errors, List<String> warnings) {
        if (!node.isObject()) {
            errors.add("场景 \"" + id + "\" 必须是对象");
            return null;
        }
        checkUnknown(node, List.of("numbered", "enter", "effects", "actions", "fallback"), errors,
                "场景 \"" + id + "\" ");

        boolean numbered = node.path("numbered").asBoolean(false);
        ParsedEnter parsedEnter = parseEnter(node.get("enter"), id, errors);
        List<Effect> enterEffects = parseEffects(node.get("effects"), errors, "场景 \"" + id + "\" 的 effects");
        List<Line> fallback = parseLines(node.get("fallback"), errors, "场景 \"" + id + "\" 的 fallback");

        List<ActionDef> actions = new ArrayList<>();
        JsonNode actionsNode = node.get("actions");
        if (actionsNode != null) {
            if (!actionsNode.isArray()) {
                errors.add("场景 \"" + id + "\" 的 actions 必须是数组");
            } else if (actionsNode.size() > MAX_ACTIONS_PER_SCENE) {
                errors.add("场景 \"" + id + "\" 的动作数量超过上限 " + MAX_ACTIONS_PER_SCENE);
            } else {
                int i = 0;
                for (JsonNode an : actionsNode) {
                    ActionDef action = parseAction(an, id, i++, errors, warnings);
                    if (action != null) {
                        actions.add(action);
                    }
                }
            }
        }
        return new SceneDef(id, numbered, parsedEnter.branches, parsedEnter.def, enterEffects, actions, fallback);
    }

    private record ParsedEnter(List<BranchDef> branches, BranchDef def) {
    }

    private static ParsedEnter parseEnter(JsonNode node, String sceneId, List<String> errors) {
        if (node == null || node.isNull()) {
            return new ParsedEnter(List.of(), new BranchDef(null, List.of(), List.of(), null));
        }
        if (node.isArray()) {
            return new ParsedEnter(List.of(),
                    new BranchDef(null, parseLines(node, errors, "场景 \"" + sceneId + "\" 的 enter"), List.of(), null));
        }
        if (!node.isObject()) {
            errors.add("场景 \"" + sceneId + "\" 的 enter 必须是数组或对象");
            return new ParsedEnter(List.of(), new BranchDef(null, List.of(), List.of(), null));
        }
        checkUnknown(node, List.of("branches", "lines"), errors, "场景 \"" + sceneId + "\" 的 enter ");
        List<Line> lines = parseLines(node.get("lines"), errors, "场景 \"" + sceneId + "\" 的 enter.lines");
        List<BranchDef> branches = new ArrayList<>();
        JsonNode branchesNode = node.get("branches");
        if (branchesNode != null) {
            if (!branchesNode.isArray()) {
                errors.add("场景 \"" + sceneId + "\" 的 enter.branches 必须是数组");
            } else {
                int i = 0;
                for (JsonNode bn : branchesNode) {
                    BranchDef b = parseBranch(bn, "场景 \"" + sceneId + "\" 的入场分支 " + (i++ + 1), errors);
                    if (b != null) {
                        branches.add(b);
                    }
                }
            }
        }
        return new ParsedEnter(branches, new BranchDef(null, lines, List.of(), null));
    }

    private static ActionDef parseAction(JsonNode node, String sceneId, int index,
                                         List<String> errors, List<String> warnings) {
        String where = "场景 \"" + sceneId + "\" 的第 " + (index + 1) + " 个动作";
        if (!node.isObject()) {
            errors.add(where + "必须是对象");
            return null;
        }
        checkUnknown(node, List.of("label", "keywords", "when", "branches", "lines", "effects", "goto"),
                errors, where + " ");

        String label = node.hasNonNull("label") ? node.get("label").asText() : null;
        List<String> keywords = stringList(node.get("keywords"), errors, where + " 的 keywords");
        Condition when = Condition.parse(node.get("when"), 0, errors);

        List<BranchDef> branches = new ArrayList<>();
        JsonNode branchesNode = node.get("branches");
        if (branchesNode != null) {
            if (!branchesNode.isArray()) {
                errors.add(where + " 的 branches 必须是数组");
            } else if (branchesNode.size() > MAX_BRANCHES_PER_ACTION) {
                errors.add(where + " 的分支数量超过上限 " + MAX_BRANCHES_PER_ACTION);
            } else {
                int i = 0;
                for (JsonNode bn : branchesNode) {
                    BranchDef b = parseBranch(bn, where + " 的分支 " + (i++ + 1), errors);
                    if (b != null) {
                        branches.add(b);
                    }
                }
            }
        }

        BranchDef self = null;
        List<Line> lines = parseLines(node.get("lines"), errors, where + " 的 lines");
        List<Effect> effects = parseEffects(node.get("effects"), errors, where + " 的 effects");
        String gotoScene = node.hasNonNull("goto") ? node.get("goto").asText() : null;
        if (!lines.isEmpty() || !effects.isEmpty() || gotoScene != null) {
            self = new BranchDef(null, lines, effects, gotoScene);
        }

        if (branches.isEmpty() && self == null) {
            errors.add(where + "没有任何台词、效果或跳转");
            return null;
        }
        if (label == null && keywords.isEmpty()) {
            errors.add(where + "既没有 label 也没有 keywords，玩家无法触发");
            return null;
        }
        for (String k : keywords) {
            if (GLOBAL_KEYWORDS.contains(k.toLowerCase())) {
                warnings.add(where + " 的关键词 \"" + k + "\" 与全局指令冲突，会被全局指令拦截");
            }
        }
        return new ActionDef(label, keywords, when, branches, self);
    }

    private static BranchDef parseBranch(JsonNode node, String where, List<String> errors) {
        if (!node.isObject()) {
            errors.add(where + "必须是对象");
            return null;
        }
        checkUnknown(node, List.of("when", "lines", "effects", "goto"), errors, where + " ");
        Condition when = Condition.parse(node.get("when"), 0, errors);
        List<Line> lines = parseLines(node.get("lines"), errors, where + " 的 lines");
        List<Effect> effects = parseEffects(node.get("effects"), errors, where + " 的 effects");
        String gotoScene = node.hasNonNull("goto") ? node.get("goto").asText() : null;
        return new BranchDef(when, lines, effects, gotoScene);
    }

    private static List<Line> parseLines(JsonNode node, List<String> errors, String where) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(where + " 必须是数组");
            return List.of();
        }
        if (node.size() > MAX_LINES_PER_BLOCK) {
            errors.add(where + " 行数超过上限 " + MAX_LINES_PER_BLOCK);
            return List.of();
        }
        List<Line> out = new ArrayList<>();
        int i = 0;
        for (JsonNode ln : node) {
            i++;
            if (ln.isTextual()) {
                out.add(checkLine(ln.asText(), "narr", errors, where + " 第 " + i + " 行"));
            } else if (ln.isObject()) {
                checkUnknown(ln, List.of("text", "style"), errors, where + " 第 " + i + " 行 ");
                JsonNode textNode = ln.get("text");
                if (textNode == null || !textNode.isTextual()) {
                    errors.add(where + " 第 " + i + " 行缺少 text");
                    continue;
                }
                String style = ln.path("style").asText("narr");
                if (!STYLES.contains(style)) {
                    errors.add(where + " 第 " + i + " 行的 style \"" + style + "\" 无效（支持：" + STYLES + "）");
                    style = "narr";
                }
                out.add(checkLine(textNode.asText(), style, errors, where + " 第 " + i + " 行"));
            } else {
                errors.add(where + " 第 " + i + " 行必须是字符串或对象");
            }
        }
        return out;
    }

    private static Line checkLine(String text, String style, List<String> errors, String where) {
        if (text.length() > MAX_LINE_TEXT) {
            errors.add(where + " 文本超过 " + MAX_LINE_TEXT + " 字符");
        }
        return Line.of(style, text);
    }

    private static List<Effect> parseEffects(JsonNode node, List<String> errors, String where) {
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            errors.add(where + " 必须是数组");
            return List.of();
        }
        List<Effect> out = new ArrayList<>();
        int i = 0;
        for (JsonNode en : node) {
            i++;
            Effect effect = parseEffect(en, errors, where + " 第 " + i + " 个效果");
            if (effect != null) {
                out.add(effect);
            }
        }
        return out;
    }

    private static Effect parseEffect(JsonNode node, List<String> errors, String where) {
        if (!node.isObject() || node.size() != 1) {
            errors.add(where + "必须是单键对象，如 {\"setFlag\": \"x\"}（支持：" + Effect.knownKeys() + "）");
            return null;
        }
        Map.Entry<String, JsonNode> e = node.fields().next();
        String key = e.getKey();
        JsonNode value = e.getValue();
        switch (key) {
            case "setFlag":
            case "addItem":
            case "addCounter":
                if (!value.isTextual() || value.asText().isEmpty()) {
                    errors.add(where + "：\"" + key + "\" 的值必须是非空字符串");
                    return null;
                }
                return switch (key) {
                    case "setFlag" -> Effect.setFlag(value.asText());
                    case "addItem" -> Effect.addItem(value.asText());
                    default -> Effect.addCounter(value.asText());
                };
            case "trust":
                if (!value.isInt()) {
                    errors.add(where + "：trust 的值必须是整数（正负皆可）");
                    return null;
                }
                return Effect.trust(value.asInt());
            case "ending":
                if (!value.isTextual() || value.asText().isEmpty()) {
                    errors.add(where + "：ending 的值必须是结局标题字符串");
                    return null;
                }
                return Effect.ending(value.asText());
            default:
                errors.add(where + "：未知效果 \"" + key + "\"（支持：" + Effect.knownKeys() + "）");
                return null;
        }
    }

    private static void validateGoto(String sceneId, BranchDef branch,
                                     Map<String, SceneDef> scenes, List<String> errors) {
        if (branch == null || branch.gotoScene == null) {
            return;
        }
        if (!scenes.containsKey(branch.gotoScene)) {
            errors.add("场景 \"" + sceneId + "\" 的 goto 指向不存在的场景 \"" + branch.gotoScene + "\"");
        }
    }

    private static void checkEndingEffectOrder(Iterable<SceneDef> scenes, List<String> errors) {
        for (SceneDef scene : scenes) {
            checkEndingLast(scene.enterEffects, "场景 \"" + scene.id + "\" 的 effects", errors);
            for (int i = 0; i < scene.enterBranches.size(); i++) {
                checkEndingLast(scene.enterBranches.get(i).effects,
                        "场景 \"" + scene.id + "\" 的入场分支 " + (i + 1), errors);
            }
            for (ActionDef action : scene.actions) {
                if (action.self != null) {
                    checkEndingLast(action.self.effects, "场景 \"" + scene.id + "\" 动作 \"" + nameOf(action) + "\"", errors);
                }
                for (int i = 0; i < action.branches.size(); i++) {
                    checkEndingLast(action.branches.get(i).effects,
                            "场景 \"" + scene.id + "\" 动作 \"" + nameOf(action) + "\" 的分支 " + (i + 1), errors);
                }
            }
        }
    }

    private static String nameOf(ActionDef action) {
        if (action.label != null) {
            return action.label;
        }
        return action.keywords.isEmpty() ? "?" : action.keywords.get(0);
    }

    private static void checkEndingLast(List<Effect> effects, String where, List<String> errors) {
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i).kind == Effect.Kind.ENDING && i != effects.size() - 1) {
                errors.add(where + "：ending 必须是最后一个效果");
                return;
            }
        }
    }

    /** 警告类检查：不可达场景、条件引用了从未产出的 flag/item。 */
    private static void collectLints(Iterable<SceneDef> scenes, String startScene, List<String> warnings) {
        Map<String, SceneDef> byId = new LinkedHashMap<>();
        scenes.forEach(s -> byId.put(s.id, s));

        if (startScene != null && byId.containsKey(startScene)) {
            Set<String> reached = new HashSet<>();
            Deque<String> queue = new ArrayDeque<>();
            queue.add(startScene);
            reached.add(startScene);
            while (!queue.isEmpty()) {
                SceneDef cur = byId.get(queue.poll());
                for (String target : gotoTargets(cur)) {
                    // 不存在的目标已由 goto 校验报错，这里跳过以免 NPE
                    if (byId.containsKey(target) && reached.add(target)) {
                        queue.add(target);
                    }
                }
            }
            for (String id : byId.keySet()) {
                if (!reached.contains(id)) {
                    warnings.add("场景 \"" + id + "\" 从 startScene 不可达");
                }
            }
        }

        Set<String> producedFlags = new HashSet<>();
        Set<String> producedItems = new HashSet<>();
        List<String> referencedFlags = new ArrayList<>();
        List<String> referencedItems = new ArrayList<>();
        for (SceneDef scene : scenes) {
            collectEffectProducts(scene.enterEffects, producedFlags, producedItems);
            for (BranchDef b : scene.enterBranches) {
                collectEffectProducts(b.effects, producedFlags, producedItems);
                b.when.collectReferences(referencedFlags, referencedItems);
            }
            for (ActionDef action : scene.actions) {
                List<BranchDef> all = new ArrayList<>(action.branches);
                if (action.self != null) {
                    all.add(action.self);
                }
                for (BranchDef b : all) {
                    collectEffectProducts(b.effects, producedFlags, producedItems);
                    b.when.collectReferences(referencedFlags, referencedItems);
                }
                action.when.collectReferences(referencedFlags, referencedItems);
            }
            scene.enter.when.collectReferences(referencedFlags, referencedItems);
        }
        for (String f : referencedFlags) {
            if (!producedFlags.contains(f)) {
                warnings.add("条件引用了从未被 setFlag 产出的旗标 \"" + f + "\"");
            }
        }
        for (String it : referencedItems) {
            if (!producedItems.contains(it)) {
                warnings.add("条件引用了从未被 addItem 产出的物品 \"" + it + "\"");
            }
        }
    }

    private static List<String> gotoTargets(SceneDef scene) {
        List<String> targets = new ArrayList<>();
        if (scene.enter.gotoScene != null) {
            targets.add(scene.enter.gotoScene);
        }
        for (BranchDef b : scene.enterBranches) {
            if (b.gotoScene != null) {
                targets.add(b.gotoScene);
            }
        }
        for (ActionDef action : scene.actions) {
            if (action.self != null && action.self.gotoScene != null) {
                targets.add(action.self.gotoScene);
            }
            for (BranchDef b : action.branches) {
                if (b.gotoScene != null) {
                    targets.add(b.gotoScene);
                }
            }
        }
        return targets;
    }

    private static void collectEffectProducts(List<Effect> effects, Set<String> flags, Set<String> items) {
        for (Effect e : effects) {
            if (e.kind == Effect.Kind.SET_FLAG) {
                flags.add(e.text);
            } else if (e.kind == Effect.Kind.ADD_ITEM) {
                items.add(e.text);
            }
        }
    }
}
