package com.example.echo.story;

import com.example.echo.game.Line;

import java.util.List;

/**
 * 场景定义：入场文本（可带条件分支）、入场效果、动作列表与兜底回复。
 */
public final class SceneDef {

    public final String id;
    public final boolean numbered;
    public final List<BranchDef> enterBranches;
    public final BranchDef enter;
    public final List<Effect> enterEffects;
    public final List<ActionDef> actions;
    public final List<Line> fallback;

    public SceneDef(String id, boolean numbered, List<BranchDef> enterBranches, BranchDef enter,
                    List<Effect> enterEffects, List<ActionDef> actions, List<Line> fallback) {
        this.id = id;
        this.numbered = numbered;
        this.enterBranches = enterBranches == null ? List.of() : List.copyOf(enterBranches);
        this.enter = enter;
        this.enterEffects = enterEffects == null ? List.of() : List.copyOf(enterEffects);
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.fallback = fallback == null
                ? List.of(Line.of("dim", "（无法解析这条指令。输入 帮助 查看可用指令。）"))
                : List.copyOf(fallback);
    }
}
