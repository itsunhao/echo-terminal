package com.example.echo.web;

import com.example.echo.story.StoryDefinition;
import com.example.echo.story.StoryLibrary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 游戏库页面、播放页面与故事文件管理 API。
 */
@Controller
public class StoryController {

    private final StoryLibrary library;

    public StoryController(StoryLibrary library) {
        this.library = library;
    }

    @GetMapping("/")
    public String libraryPage(@RequestParam(name = "err", required = false) String err, Model model) {
        model.addAttribute("err", err);
        return "library";
    }

    @GetMapping("/play")
    public String playPage(@RequestParam("story") String storyId, Model model) {
        StoryDefinition story = library.get(storyId);
        if (story == null) {
            return "redirect:/?err=notfound";
        }
        model.addAttribute("story", story);
        return "play";
    }

    @GetMapping("/api/stories")
    @ResponseBody
    public List<StoryLibrary.StoryInfo> list() {
        return library.list();
    }

    /** 上传安装游戏文件。无论成功失败都返回 200 + JSON，便于前端统一处理。 */
    @PostMapping("/api/stories/install")
    @ResponseBody
    public Map<String, Object> install(@RequestParam("file") MultipartFile file) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (file == null || file.isEmpty()) {
            body.put("ok", false);
            body.put("errors", List.of("没有收到文件或文件为空"));
            return body;
        }
        String name = file.getOriginalFilename();
        if (name != null && !name.toLowerCase().endsWith(".json")) {
            body.put("ok", false);
            body.put("errors", List.of("仅支持 .json 游戏文件（收到：" + name + "）"));
            return body;
        }
        try {
            StoryLibrary.InstallResult result = library.install(file.getBytes());
            body.put("ok", result.ok());
            if (result.ok()) {
                body.put("story", result.story());
            } else {
                body.put("errors", result.errors());
            }
            if (!result.warnings().isEmpty()) {
                body.put("warnings", result.warnings());
            }
            return body;
        } catch (Exception e) {
            body.put("ok", false);
            body.put("errors", List.of("安装失败：" + e.getMessage()));
            return body;
        }
    }

    /** 删除外部安装的游戏（内置故事不可删）。 */
    @DeleteMapping("/api/stories/{id}")
    @ResponseBody
    public ResponseEntity<?> remove(@PathVariable String id) {
        if (library.remove(id)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.badRequest().body(Map.of("ok", false,
                "errors", List.of("该游戏不存在，或为内置游戏（不可删除）")));
    }

    /** 下载游戏文件（方便用户取走样本或分享）。 */
    @GetMapping("/api/stories/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable String id) {
        String json = library.rawJson(id);
        if (json == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json.getBytes(StandardCharsets.UTF_8));
    }
}
