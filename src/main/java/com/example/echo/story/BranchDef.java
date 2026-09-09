package com.example.echo.story;

import com.example.echo.game.Line;

import java.util.List;

/**
 * 一个分支：条件 + 台词 + 效果 + 跳转。
 * 同时用于动作的 branches 和场景入场的 enter 结构。
 */
public final class BranchDef {

    public final Condition when;
    public final List<Line> lines;
    public final List<Effect> effects;
    public final String gotoScene;

    public BranchDef(Condition when, List<Line> lines, List<Effect> effects, String gotoScene) {
        this.when = when == null ? Condition.always() : when;
        this.lines = lines == null ? List.of() : List.copyOf(lines);
        this.effects = effects == null ? List.of() : List.copyOf(effects);
        this.gotoScene = gotoScene;
    }

    /** 无台词、无效果、无跳转的空分支。 */
    public boolean trivial() {
        return lines.isEmpty() && effects.isEmpty() && gotoScene == null;
    }
}
