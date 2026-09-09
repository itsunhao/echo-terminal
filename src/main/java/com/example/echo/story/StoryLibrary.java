package com.example.echo.story;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 故事库：内置故事（classpath:stories/）+ 外部安装故事（echo.games-dir，默认 ./games）。
 *
 * <p>同 id 时外部文件覆盖内置；删除外部副本后回退到内置版本。
 * {@link StoryDefinition} 深度不可变，运行中的会话持有旧引用不受热替换影响。</p>
 */
@Component
public class StoryLibrary {

    public record StoryInfo(String id, String title, String genre, String version, String author,
                            String description, int sceneCount, int endingCount,
                            boolean builtin, boolean overridden) {
    }

    /** 安装结果：成功返回故事信息，失败返回错误列表。 */
    public record InstallResult(boolean ok, StoryInfo story, List<String> errors, List<String> warnings) {
    }

    private static final Logger log = LoggerFactory.getLogger(StoryLibrary.class);

    private record Entry(StoryDefinition story, String rawJson, boolean builtin) {
    }

    private final Map<String, Entry> builtin = new LinkedHashMap<>();
    private final Map<String, Entry> stories = new ConcurrentHashMap<>();
    private final Path gamesDir;

    public StoryLibrary(@Value("${echo.games-dir:}") String gamesDirProp) {
        this.gamesDir = (gamesDirProp == null || gamesDirProp.isBlank())
                ? Path.of(System.getProperty("user.dir"), "games")
                : Path.of(gamesDirProp);
        loadBuiltin();
        loadExternal();
    }

    private void loadBuiltin() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath:stories/*.json");
            for (Resource resource : resources) {
                String json = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                StoryParser.ParseResult result = StoryParser.parse(json);
                if (!result.ok()) {
                    log.error("内置故事文件 {} 校验失败：{}", resource.getFilename(), result.errors());
                    continue;
                }
                Entry entry = new Entry(result.story(), json, true);
                builtin.put(result.story().id, entry);
                stories.put(result.story().id, entry);
                result.warnings().forEach(w -> log.warn("内置故事 {}：{}", result.story().id, w));
            }
        } catch (IOException e) {
            log.error("扫描内置故事目录失败", e);
        }
    }

    private void loadExternal() {
        if (!Files.isDirectory(gamesDir)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(gamesDir, "*.json")) {
            stream.forEach(files::add);
        } catch (IOException e) {
            log.error("读取外部游戏目录 {} 失败", gamesDir, e);
            return;
        }
        files.sort(Comparator.comparing(p -> p.getFileName().toString()));
        for (Path file : files) {
            try {
                String json = Files.readString(file, StandardCharsets.UTF_8);
                StoryParser.ParseResult result = StoryParser.parse(json);
                if (!result.ok()) {
                    log.error("外部游戏文件 {} 校验失败（已跳过）：{}", file, result.errors());
                    continue;
                }
                stories.put(result.story().id, new Entry(result.story(), json, false));
                result.warnings().forEach(w -> log.warn("外部游戏 {}：{}", result.story().id, w));
            } catch (IOException e) {
                log.error("读取外部游戏文件 {} 失败（已跳过）", file, e);
            }
        }
    }

    public StoryDefinition get(String id) {
        Entry entry = stories.get(id);
        return entry == null ? null : entry.story();
    }

    public String rawJson(String id) {
        Entry entry = stories.get(id);
        return entry == null ? null : entry.rawJson();
    }

    public List<StoryInfo> list() {
        List<StoryInfo> out = new ArrayList<>();
        stories.values().stream()
                .sorted(Comparator.comparing(e -> e.story().title))
                .forEach(e -> out.add(info(e)));
        return out;
    }

    private StoryInfo info(Entry e) {
        StoryDefinition s = e.story();
        Entry current = stories.get(s.id);
        boolean overridden = builtin.containsKey(s.id) && current != null && !current.builtin();
        return new StoryInfo(s.id, s.title, s.genre, s.version, s.author, s.description,
                s.scenes.size(), s.endings.size(), e.builtin(), overridden);
    }

    /** 安装一个游戏文件：校验通过后原子写入外部目录并注册（覆盖同 id）。 */
    public synchronized InstallResult install(byte[] content) {
        String json;
        try {
            json = new String(content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new InstallResult(false, null, List.of("文件不是有效的 UTF-8 文本"), List.of());
        }
        StoryParser.ParseResult result = StoryParser.parse(json);
        if (!result.ok()) {
            return new InstallResult(false, null, result.errors(), result.warnings());
        }
        StoryDefinition story = result.story();
        try {
            Files.createDirectories(gamesDir);
            Path target = gamesDir.resolve(story.id + ".json").normalize();
            if (!target.startsWith(gamesDir)) {
                return new InstallResult(false, null, List.of("非法的故事 id"), List.of());
            }
            Path temp = Files.createTempFile(gamesDir, ".upload-", ".tmp");
            Files.write(temp, content);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.error("写入游戏文件失败", e);
            return new InstallResult(false, null, List.of("写入游戏目录失败：" + e.getMessage()), List.of());
        }
        Entry entry = new Entry(story, json, false);
        stories.put(story.id, entry);
        log.info("已安装游戏《{}》（id={}），来源：上传", story.title, story.id);
        return new InstallResult(true, info(entry), result.errors(), result.warnings());
    }

    /** 删除外部安装的游戏；内置故事不可删。删除后若存在同 id 内置版本则回退。 */
    public synchronized boolean remove(String id) {
        Entry entry = stories.get(id);
        if (entry == null || entry.builtin()) {
            return false;
        }
        try {
            Files.deleteIfExists(gamesDir.resolve(id + ".json"));
        } catch (IOException e) {
            log.warn("删除外部游戏文件 {} 失败", id, e);
        }
        Entry fallback = builtin.get(id);
        if (fallback != null) {
            stories.put(id, fallback);
        } else {
            stories.remove(id);
        }
        return true;
    }
}
