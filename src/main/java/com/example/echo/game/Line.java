package com.example.echo.game;

/**
 * 终端里的一行输出。style 决定前端渲染颜色。
 */
public record Line(String text, String style) {

    public static Line of(String style, String text) {
        return new Line(text, style);
    }
}
