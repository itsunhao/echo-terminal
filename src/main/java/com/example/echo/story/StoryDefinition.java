package com.example.echo.story;

import java.util.List;
import java.util.Map;

/**
 * 一个完整的故事（游戏文件解析结果）。深度不可变。
 */
public final class StoryDefinition {

    public final int format;
    public final String id;
    public final String title;
    public final String genre;
    public final String version;
    public final String author;
    public final String description;
    public final String startScene;
    public final List<String> endings;
    public final List<String> clues;
    public final Map<String, SceneDef> scenes;

    public StoryDefinition(int format, String id, String title, String genre, String version,
                           String author, String description, String startScene,
                           List<String> endings, List<String> clues, Map<String, SceneDef> scenes) {
        this.format = format;
        this.id = id;
        this.title = title;
        this.genre = genre == null ? "" : genre;
        this.version = version == null ? "1.0" : version;
        this.author = author == null ? "" : author;
        this.description = description == null ? "" : description;
        this.startScene = startScene;
        this.endings = endings == null ? List.of() : List.copyOf(endings);
        this.clues = clues == null ? List.of() : List.copyOf(clues);
        this.scenes = Map.copyOf(scenes);
    }

    public SceneDef scene(String id) {
        return scenes.get(id);
    }

    public int endingIndex(String title) {
        for (int i = 0; i < endings.size(); i++) {
            if (endings.get(i).equals(title)) {
                return i + 1;
            }
        }
        return -1;
    }
}
