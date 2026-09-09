package com.example.echo.game;

import java.util.ArrayList;
import java.util.List;

/**
 * 一次交互返回给前端的全部内容。
 *
 * <p>{@code choices} 由引擎在进入编号选项场景时自动填充；
 * {@code ended=true} 表示到达结局，{@code ending} 为结局标题。</p>
 */
public record Reply(List<Line> lines, List<String> choices, boolean ended, String ending) {

    public static Reply of(List<Line> lines) {
        return new Reply(lines, null, false, null);
    }

    public static Reply of(Line... lines) {
        return new Reply(new ArrayList<>(List.of(lines)), null, false, null);
    }

    public static Reply ending(List<Line> lines, String endingTitle) {
        return new Reply(lines, null, true, endingTitle);
    }

    public Reply withChoices(List<String> choices) {
        return new Reply(new ArrayList<>(lines), choices, ended, ending);
    }
}
