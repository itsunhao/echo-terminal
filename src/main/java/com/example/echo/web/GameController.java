package com.example.echo.web;

import com.example.echo.game.GameEngine;
import com.example.echo.game.GameSession;
import com.example.echo.game.Reply;
import com.example.echo.story.StoryDefinition;
import com.example.echo.story.StoryLibrary;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 游戏进行中的 API。会话状态存于 HttpSession，对同一 session 加锁防止双标签页交错。
 */
@RestController
public class GameController {

    private static final String SESSION_KEY = "gameSession";

    private final GameEngine engine;
    private final StoryLibrary library;

    public GameController(GameEngine engine, StoryLibrary library) {
        this.engine = engine;
        this.library = library;
    }

    public record StartRequest(String storyId) {
    }

    public record InputRequest(String text) {
    }

    @PostMapping("/api/game/start")
    public ResponseEntity<?> start(@RequestBody StartRequest request, HttpSession httpSession) {
        if (request == null || request.storyId() == null || request.storyId().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "missing-story-id"));
        }
        StoryDefinition story = library.get(request.storyId());
        if (story == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "story-not-found"));
        }
        GameSession game = session(httpSession);
        synchronized (game) {
            return ResponseEntity.ok(engine.start(story, game));
        }
    }

    @PostMapping("/api/game/input")
    public ResponseEntity<?> input(@RequestBody InputRequest request, HttpSession httpSession) {
        GameSession game = session(httpSession);
        synchronized (game) {
            if (game.storyId() == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "no-active-game"));
            }
            StoryDefinition story = library.get(game.storyId());
            if (story == null) {
                return ResponseEntity.status(HttpStatus.GONE).body(Map.of("error", "story-missing"));
            }
            return ResponseEntity.ok(engine.input(story, game, request == null ? "" : request.text()));
        }
    }

    private static GameSession session(HttpSession httpSession) {
        GameSession game = (GameSession) httpSession.getAttribute(SESSION_KEY);
        if (game == null) {
            game = new GameSession();
            httpSession.setAttribute(SESSION_KEY, game);
        }
        return game;
    }
}
