package com.example.echo.story;

import com.example.echo.game.GameSession;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 递归条件表达式，从 JsonNode 手工解析（不使用 Jackson 多态，避免反序列化风险）。
 *
 * <p>支持：flag / notFlag / item / notItem / trustMin / trustMax /
 * counterMin（名→阈值映射）/ and / or / not。同一对象内多个键为隐式 AND。
 * 解析深度上限 {@link #MAX_DEPTH}。</p>
 */
public final class Condition {

    public static final int MAX_DEPTH = 8;

    public enum Kind { ALWAYS, FLAG, NOT_FLAG, ITEM, NOT_ITEM, TRUST_MIN, TRUST_MAX, COUNTER_MIN, AND, OR, NOT }

    public final Kind kind;
    private final String strValue;
    private final int intValue;
    private final Map<String, Integer> counterThresholds;
    private final List<Condition> children;

    private Condition(Kind kind, String strValue, int intValue,
                      Map<String, Integer> counterThresholds, List<Condition> children) {
        this.kind = kind;
        this.strValue = strValue;
        this.intValue = intValue;
        this.counterThresholds = counterThresholds == null ? Map.of() : Map.copyOf(counterThresholds);
        this.children = children == null ? List.of() : List.copyOf(children);
    }

    public static Condition always() {
        return new Condition(Kind.ALWAYS, null, 0, null, null);
    }

    /** 解析条件节点；node 为 null 或缺省时返回恒真。错误追加到 errors。 */
    public static Condition parse(JsonNode node, int depth, List<String> errors) {
        if (node == null || node.isNull()) {
            return always();
        }
        if (depth > MAX_DEPTH) {
            errors.add("条件嵌套过深（上限 " + MAX_DEPTH + " 层）");
            return always();
        }
        if (!node.isObject()) {
            errors.add("条件必须是对象，例如 {\"flag\": \"x\"}");
            return always();
        }
        List<Condition> parts = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> f = fields.next();
            Condition c = parseKey(f.getKey(), f.getValue(), depth, errors);
            if (c != null) {
                parts.add(c);
            }
        }
        if (parts.isEmpty()) {
            return always();
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return new Condition(Kind.AND, null, 0, null, parts);
    }

    private static Condition parseKey(String key, JsonNode value, int depth, List<String> errors) {
        switch (key) {
            case "flag":
            case "notFlag":
            case "item":
            case "notItem":
                if (!value.isTextual() || value.asText().isEmpty()) {
                    errors.add("条件 \"" + key + "\" 的值必须是非空字符串");
                    return null;
                }
                return new Condition(kindOf(key), value.asText(), 0, null, null);
            case "trustMin":
            case "trustMax":
                if (!value.isInt()) {
                    errors.add("条件 \"" + key + "\" 的值必须是整数");
                    return null;
                }
                return new Condition(kindOf(key), null, value.asInt(), null, null);
            case "counterMin": {
                if (!value.isObject() || value.isEmpty()) {
                    errors.add("counterMin 必须是 {\"计数器名\": 阈值} 形式的对象");
                    return null;
                }
                Map<String, Integer> thresholds = new LinkedHashMap<>();
                value.fields().forEachRemaining(e -> {
                    if (!e.getValue().isInt()) {
                        errors.add("counterMin[\"" + e.getKey() + "\"] 的阈值必须是整数");
                    } else {
                        thresholds.put(e.getKey(), e.getValue().asInt());
                    }
                });
                return new Condition(Kind.COUNTER_MIN, null, 0, thresholds, null);
            }
            case "and":
            case "or": {
                if (!value.isArray() || value.isEmpty()) {
                    errors.add("条件 \"" + key + "\" 必须是非空数组");
                    return null;
                }
                List<Condition> children = new ArrayList<>();
                for (JsonNode child : value) {
                    children.add(parse(child, depth + 1, errors));
                }
                return new Condition(key.equals("and") ? Kind.AND : Kind.OR, null, 0, null, children);
            }
            case "not":
                return new Condition(Kind.NOT, null, 0, null, List.of(parse(value, depth + 1, errors)));
            default:
                errors.add("未知条件键 \"" + key + "\"（支持：flag/notFlag/item/notItem/trustMin/trustMax/counterMin/and/or/not）");
                return null;
        }
    }

    private static Kind kindOf(String key) {
        switch (key) {
            case "flag": return Kind.FLAG;
            case "notFlag": return Kind.NOT_FLAG;
            case "item": return Kind.ITEM;
            case "notItem": return Kind.NOT_ITEM;
            case "trustMin": return Kind.TRUST_MIN;
            case "trustMax": return Kind.TRUST_MAX;
            default: throw new IllegalStateException(key);
        }
    }

    public boolean evaluate(GameSession s) {
        switch (kind) {
            case ALWAYS: return true;
            case FLAG: return s.hasFlag(strValue);
            case NOT_FLAG: return !s.hasFlag(strValue);
            case ITEM: return s.hasItem(strValue);
            case NOT_ITEM: return !s.hasItem(strValue);
            case TRUST_MIN: return s.trust() >= intValue;
            case TRUST_MAX: return s.trust() <= intValue;
            case COUNTER_MIN:
                for (Map.Entry<String, Integer> e : counterThresholds.entrySet()) {
                    if (s.counter(e.getKey()) < e.getValue()) {
                        return false;
                    }
                }
                return true;
            case AND:
                for (Condition c : children) {
                    if (!c.evaluate(s)) {
                        return false;
                    }
                }
                return true;
            case OR:
                for (Condition c : children) {
                    if (c.evaluate(s)) {
                        return true;
                    }
                }
                return false;
            case NOT: return !children.get(0).evaluate(s);
            default: return true;
        }
    }

    /** 收集条件中引用的 flag / item 名（用于校验警告）。 */
    public void collectReferences(List<String> flags, List<String> items) {
        if (kind == Kind.FLAG || kind == Kind.NOT_FLAG) {
            flags.add(strValue);
        } else if (kind == Kind.ITEM || kind == Kind.NOT_ITEM) {
            items.add(strValue);
        }
        for (Condition c : children) {
            c.collectReferences(flags, items);
        }
    }
}
