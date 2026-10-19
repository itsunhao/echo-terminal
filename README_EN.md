# Echo Terminal

> 🤖 **AI-Generated Notice**: This project (including engine code, docs, and sample games) was AI-generated for learning and sharing purposes.

A **terminal-style text adventure game engine**. Built around a retro terminal interface, it carries interactive narrative games through text, symbols, and instant feedback — scenes, choices, keyword input, clue tracking, and ending statistics — all in a typewriter-like terminal UI.

**Key highlight**: **game-as-file** — upload a compliant `.json` game file and play instantly. Anyone can write their own interactive narrative game following the format spec in `samples/GAMEFILE.md`.

## Features

- 🖥️ **Terminal gameplay**: xterm.js retro interface with numbered choices and natural-language keyword input
- 📦 **Install & play**: upload a game file from the library homepage; same-ID overwrites installed version; deleting external file falls back to built-in
- 🎮 **Built-in stories + sample games**: sci-fi, horror, mystery, fantasy games shipped out of the box
- 🧩 **Structured narrative model**: scene / action / condition / effect 4-layer model with flags, items, trust values, counters
- 📜 **Ending & clue tracking**: auto-track endings reached, clues collected, trust changes
- ⚙️ **Strict validation**: format, references, nesting depth checked on install; typos reported immediately
- 🌐 **Lightweight deployment**: single Spring Boot process, all static resources bundled, no external dependencies

## Quick Start

Requirements: **JDK 21+** (Maven Wrapper included, no need to install Maven separately).

```bash
# Start (auto-downloads Maven dependencies on first run)
./mvnw spring-boot:run

# Windows
mvnw.cmd spring-boot:run
```

Then open in browser:

- **Library homepage**: http://localhost:8080/
- **Gameplay**: http://localhost:8080/play?story=<game-id>

Default port `8080`, configurable via `server.port`.

## Write Your Own Game

A game is a **UTF-8 JSON file** (≤ 5MB) describing scenes, actions, conditions, and effects:

```json
{
  "format": 1,
  "id": "my-story",
  "title": "My Story",
  "genre": "Genre · Tags",
  "version": "1.0",
  "author": "Your Name",
  "description": "One-line summary shown on the library card.",
  "startScene": "boot",
  "endings": ["Ending 1", "Ending 2"],
  "clues": ["clue_a"],
  "scenes": { "...": "..." }
}
```

- **Full spec**: see [`samples/GAMEFILE.md`](samples/GAMEFILE.md) (format, fields, scene/action/condition/effect details, line styles, limits)
- **Sample games**: see `samples/` directory covering numbered choices, free input, trust values, counters, dual-item gates, first-visit/return-visit branches

Built-in global commands available in all stories, no definition needed: `help`, `backpack`, `restart`, `about`.

### Install a Game

| Method | Action |
|---|---|
| Web | Click "Upload" on the library homepage, select a `.json` file |
| API | `POST /api/stories/install` (multipart field `file`) |
| Direct deploy | Place file in external games directory (`games/` under working dir), restart or hot-scan |

External files with the same ID as a built-in take priority; deleting the external file falls back to the built-in version.

## Built-in & Sample Games

| Game | Genre | Location |
|---|---|---|
| Aurora-7 Deep Space Station | Sci-fi · Mystery | Built-in `src/main/resources/stories/` |
| Rainy Night Manor Murder | Crime · Mystery | Built-in `src/main/resources/stories/` |
| Fog Town Clinic | Horror · Adventure · Puzzle | `games/` + `samples/` |
| Kowloon Walled City · Faceless Buddha | Chinese horror · Suspense | `games/` |
| Hogwarts: Gargoyle Night | Fantasy · Horror · Adventure | `samples/` |

## Project Structure

```
echo-terminal/
├── pom.xml                          # Maven config (Spring Boot 3.3.5, Java 21)
├── mvnw / mvnw.cmd                  # Maven Wrapper
├── games/                           # External installed games (runtime loaded)
├── samples/
│   ├── GAMEFILE.md                  # ★ Game file format spec (must-read)
│   └── *.json                       # Sample games
└── src/main/
    ├── java/com/example/echo/
    │   ├── EchoApplication.java     # App entry
    │   ├── game/                    # Game runtime engine (session, reply, render)
    │   ├── story/                   # Story model & parser (scene/action/condition/effect)
    │   └── web/                     # Pages & REST API
    └── resources/
        ├── stories/                 # Built-in stories
        ├── static/                  # Frontend assets (xterm.js, styles, scripts)
        └── templates/               # Library / play pages (Thymeleaf)
```

## HTTP API

| Method | Path | Description |
|---|---|---|
| GET | `/` | Library homepage |
| GET | `/play?story=<id>` | Play page |
| GET | `/api/stories` | Game list (with scene count, ending count, built-in flag) |
| POST | `/api/stories/install` | Upload & install game (multipart `file`, `.json` only) |
| DELETE | `/api/stories/{id}` | Delete externally installed game (built-in cannot be deleted) |
| GET | `/api/stories/{id}/download` | Download game file |
| POST | `/api/game/start` | Start a game session, body: `{"storyId":"<id>"}` |
| POST | `/api/game/input` | Submit player input, body: `{"text":"..."}` |

## Tech Stack

- **Backend**: Java 21 · Spring Boot 3.3.5 · Thymeleaf
- **Frontend**: xterm.js (terminal emulation) + jQuery, native CSS
- **Data**: games are JSON files, no database; session state in HttpSession

## License

This project is open-sourced under the **MIT License** — free to use, modify, distribute, and commercially use, with attribution required. See [LICENSE](LICENSE) for details.
