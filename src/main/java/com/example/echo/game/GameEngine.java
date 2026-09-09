package com.example.echo.game;

import com.example.echo.story.ActionDef;
import com.example.echo.story.BranchDef;
import com.example.echo.story.Effect;
import com.example.echo.story.SceneDef;
import com.example.echo.story.StoryDefinition;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.example.echo.game.Line.of;

/**
 * 通用游戏引擎：无状态，所有剧情逻辑来自 {@link StoryDefinition}（游戏文件）。
 *
 * <p>执行次序规范：</p>
 * <ol>
 *   <li>归一化输入 → 数字选项 / 全局指令 / 关键词最长匹配</li>
 *   <li>动作 when 条件决定可见性（编号列表与关键词共用）</li>
 *   <li>选中分支（第一个 when 为真的 branch，否则动作自身）→ 应用 effects</li>
 *   <li>有 goto：求值目标场景入场分支 → 入场行追加进同一回复 → 应用入场效果 → 切换场景</li>
 *   <li>有 ending 效果：置 ended，引擎自动追加结局标题与统计行，忽略 goto</li>
 *   <li>回复结束后按新状态重算编号选项</li>
 * </ol>
 * <p>注意：条件求值发生在本轮效果应用之前。</p>
 */
@Component
public class GameEngine {

    /** 开始（或重置）一局。 */
    public Reply start(StoryDefinition story, GameSession s) {
        s.reset(story.id);
        return enrich(story, s, withEnded(Reply.of(enterScene(story, s, story.startScene)), s));
    }

    /** 处理玩家输入。 */
    public Reply input(StoryDefinition story, GameSession s, String raw) {
        return enrich(story, s, withEnded(handle(story, s, raw), s));
    }

    private static Reply withEnded(Reply reply, GameSession s) {
        if (s.ended() && !reply.ended()) {
            return Reply.ending(reply.lines(), s.endingTitle());
        }
        return reply;
    }

    /** 切换到指定场景：入场行 + 入场效果，返回应追加到回复中的行。 */
    public List<Line> enterScene(StoryDefinition story, GameSession s, String sceneId) {
        SceneDef scene = story.scene(sceneId);
        s.sceneId(sceneId);
        BranchDef chosen = null;
        for (BranchDef b : scene.enterBranches) {
            if (b.when.evaluate(s)) {
                chosen = b;
                break;
            }
        }
        List<Line> lines = new ArrayList<>();
        if (chosen != null) {
            lines.addAll(chosen.lines);
            applyEffects(story, s, chosen.effects, lines);
        } else {
            lines.addAll(scene.enter.lines);
        }
        applyEffects(story, s, scene.enterEffects, lines);
        return lines;
    }

    private Reply handle(StoryDefinition story, GameSession s, String raw) {
        if (s.ended()) {
            return start(story, s);
        }
        String text = normalize(raw);
        if (text.isEmpty()) {
            return Reply.of(of("dim", "（空指令。输入 帮助 查看可用指令。）"));
        }

        SceneDef scene = story.scene(s.sceneId());
        List<ActionDef> visible = scene.actions.stream().filter(a -> a.visible(s)).toList();

        Integer number = parseNumber(text);
        if (number != null) {
            List<ActionDef> numbered = visible.stream()
                    .filter(a -> a.label != null && a.resolve(s) != null)
                    .toList();
            if (number >= 1 && number <= numbered.size()) {
                return executeAction(story, s, numbered.get(number - 1));
            }
            return Reply.of(scene.fallback);
        }

        Reply global = globalCommand(story, s, text);
        if (global != null) {
            return global;
        }

        ActionDef best = null;
        int bestLen = 0;
        for (ActionDef a : visible) {
            int len = a.longestKeywordMatch(text);
            if (len > bestLen && a.resolve(s) != null) {
                best = a;
                bestLen = len;
            }
        }
        if (best != null) {
            return executeAction(story, s, best);
        }
        return Reply.of(scene.fallback);
    }

    private Reply executeAction(StoryDefinition story, GameSession s, ActionDef action) {
        BranchDef branch = action.resolve(s);
        List<Line> lines = new ArrayList<>(branch.lines);
        boolean ended = applyEffects(story, s, branch.effects, lines);
        if (!ended && branch.gotoScene != null) {
            lines.addAll(enterScene(story, s, branch.gotoScene));
        }
        return Reply.of(lines);
    }

    /** 应用效果；触发结局时追加标题与统计行并返回 true。 */
    private boolean applyEffects(StoryDefinition story, GameSession s, List<Effect> effects, List<Line> out) {
        for (Effect e : effects) {
            switch (e.kind) {
                case SET_FLAG -> s.setFlag(e.text);
                case ADD_ITEM -> s.addItem(e.text);
                case TRUST -> s.addTrust(e.number);
                case ADD_COUNTER -> s.addCounter(e.text);
                case ENDING -> {
                    s.ended(true);
                    s.endingTitle(e.text);
                    out.add(of("ending", "—— 结局 · " + e.text + " ——"));
                    out.add(of("dim", statsLine(story, s, e.text)));
                    return true;
                }
            }
        }
        return false;
    }

    private String statsLine(StoryDefinition story, GameSession s, String title) {
        StringBuilder sb = new StringBuilder();
        int index = story.endingIndex(title);
        if (index > 0 && !story.endings.isEmpty()) {
            sb.append("结局 ").append(index).append('/').append(story.endings.size());
        } else {
            sb.append("结局");
        }
        sb.append(" · 信任值：").append(s.trust());
        long collected = s.clueCount(story.clues);
        if (!story.clues.isEmpty()) {
            sb.append(" · 关键线索：").append(collected).append('/').append(story.clues.size());
        } else {
            sb.append(" · 关键线索：").append(collected);
        }
        return sb.toString();
    }

    private Reply enrich(StoryDefinition story, GameSession s, Reply reply) {
        if (reply.ended()) {
            return reply;
        }
        SceneDef scene = story.scene(s.sceneId());
        if (!scene.numbered) {
            return reply;
        }
        List<String> choices = scene.actions.stream()
                .filter(a -> a.visible(s))
                .filter(a -> a.label != null)
                .filter(a -> a.resolve(s) != null)
                .map(a -> a.label)
                .toList();
        return reply.withChoices(choices);
    }

    private Reply globalCommand(StoryDefinition story, GameSession s, String text) {
        if (text.contains("帮助") || text.equals("help") || text.equals("?") || text.equals("？")) {
            return Reply.of(List.of(
                    of("system", "—— 指令帮助 ——"),
                    of("dim", " 输入数字或关键词，选择你的行动"),
                    of("dim", " 背包      查看随身物品"),
                    of("dim", " 关于      关于这个故事"),
                    of("dim", " 重新开始  重置本次旅程")
            ));
        }
        if (text.contains("背包") || text.contains("物品") || text.equals("inv") || text.equals("inventory")) {
            if (s.inventory().isEmpty()) {
                return Reply.of(of("dim", "你的随身包是空的。"));
            }
            List<Line> lines = new ArrayList<>();
            lines.add(of("system", "—— 随身物品 ——"));
            for (String item : s.inventory()) {
                lines.add(of("item", " · " + item));
            }
            return Reply.of(lines);
        }
        if (text.contains("重新开始") || text.contains("重来") || text.equals("restart")) {
            return start(story, s);
        }
        if (text.equals("关于") || text.equals("about")) {
            List<Line> lines = new ArrayList<>(List.of(
                    of("system", "《" + story.title + "》" + (story.genre.isEmpty() ? "" : " · " + story.genre)),
                    of("dim", " 版本 " + story.version + " · " + story.scenes.size() + " 个场景 · "
                            + story.endings.size() + " 个结局")
            ));
            if (!story.author.isEmpty()) {
                lines.add(of("dim", " 作者：" + story.author));
            }
            if (!story.description.isEmpty()) {
                lines.add(of("dim", " " + story.description));
            }
            return Reply.of(lines);
        }
        return null;
    }

    private static Integer parseNumber(String text) {
        String t = text.strip();
        if (t.startsWith("选择")) {
            t = t.substring(2).strip();
        } else if (t.startsWith("选")) {
            t = t.substring(1).strip();
        }
        if (t.matches("\\d{1,2}")) {
            return Integer.parseInt(t);
        }
        return null;
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : raw.strip().toCharArray()) {
            if (c >= '０' && c <= '９') {
                sb.append((char) ('0' + (c - '０')));
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
