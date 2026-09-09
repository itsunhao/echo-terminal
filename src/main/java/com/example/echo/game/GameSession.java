package com.example.echo.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单个玩家的游戏状态，随 HttpSession 保存。
 */
public class GameSession {

    private String storyId;
    private String sceneId;
    private final Set<String> flags = new LinkedHashSet<>();
    private final List<String> inventory = new ArrayList<>();
    private final Map<String, Integer> counters = new HashMap<>();
    private int trust = 0;
    private boolean ended = false;
    private String endingTitle;

    public String storyId() {
        return storyId;
    }

    public String sceneId() {
        return sceneId;
    }

    public void sceneId(String id) {
        this.sceneId = id;
    }

    public boolean hasFlag(String flag) {
        return flags.contains(flag);
    }

    public void setFlag(String flag) {
        flags.add(flag);
    }

    public boolean hasItem(String item) {
        return inventory.contains(item);
    }

    public void addItem(String item) {
        if (!inventory.contains(item)) {
            inventory.add(item);
        }
    }

    public List<String> inventory() {
        return inventory;
    }

    public int trust() {
        return trust;
    }

    public void addTrust(int delta) {
        trust += delta;
    }

    public int counter(String name) {
        return counters.getOrDefault(name, 0);
    }

    public void addCounter(String name) {
        counters.merge(name, 1, Integer::sum);
    }

    public boolean ended() {
        return ended;
    }

    public void ended(boolean ended) {
        this.ended = ended;
    }

    public String endingTitle() {
        return endingTitle;
    }

    public void endingTitle(String title) {
        this.endingTitle = title;
    }

    /** 按故事声明的线索列表统计已收集数量；未声明时退回 clue_ 前缀约定。 */
    public long clueCount(List<String> declared) {
        if (declared != null && !declared.isEmpty()) {
            return declared.stream().filter(flags::contains).count();
        }
        return flags.stream().filter(f -> f.startsWith("clue_")).count();
    }

    /** 重置为新故事的一局。 */
    public void reset(String storyId) {
        this.storyId = storyId;
        this.sceneId = null;
        flags.clear();
        inventory.clear();
        counters.clear();
        trust = 0;
        ended = false;
        endingTitle = null;
    }
}
