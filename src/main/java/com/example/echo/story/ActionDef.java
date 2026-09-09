package com.example.echo.story;

import com.example.echo.game.GameSession;

import java.util.List;

/**
 * 故事中玩家可执行的动作。
 *
 * <p>label 非空时参与编号选项展示；keywords 用于自由输入匹配。
 * 执行时先在 branches 中找第一个 when 为真的分支，全不中则回退到动作自身的
 * lines/effects/goto；若最终没有任何台词，该动作视为不可用。</p>
 */
public final class ActionDef {

    public final String label;
    public final List<String> keywords;
    public final Condition when;
    public final List<BranchDef> branches;
    public final BranchDef self;

    public ActionDef(String label, List<String> keywords, Condition when,
                     List<BranchDef> branches, BranchDef self) {
        this.label = label;
        this.keywords = keywords == null ? List.of() : List.copyOf(keywords);
        this.when = when == null ? Condition.always() : when;
        this.branches = branches == null ? List.of() : List.copyOf(branches);
        this.self = self;
    }

    public boolean visible(GameSession s) {
        return when.evaluate(s);
    }

    /** 选出当前会话下实际执行的分支；返回 null 表示动作此刻不可用。 */
    public BranchDef resolve(GameSession s) {
        for (BranchDef b : branches) {
            if (b.when.evaluate(s)) {
                return b;
            }
        }
        if (self != null && !self.trivial()) {
            return self;
        }
        return null;
    }

    public boolean matches(String normalizedInput) {
        for (String k : keywords) {
            if (normalizedInput.contains(k)) {
                return true;
            }
        }
        return false;
    }

    public int longestKeywordMatch(String normalizedInput) {
        int best = 0;
        for (String k : keywords) {
            if (normalizedInput.contains(k) && k.length() > best) {
                best = k.length();
            }
        }
        return best;
    }
}
