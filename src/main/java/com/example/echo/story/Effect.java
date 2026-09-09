package com.example.echo.story;

import java.util.List;

/**
 * 一条效果：改变会话状态或触发结局。
 *
 * <p>JSON 形如单键对象：{@code {"setFlag":"x"}} / {@code {"addItem":"门禁卡"}} /
 * {@code {"trust":1}} / {@code {"addCounter":"ignores"}} / {@code {"ending":"静默"}}。</p>
 */
public final class Effect {

    public enum Kind { SET_FLAG, ADD_ITEM, TRUST, ADD_COUNTER, ENDING }

    public final Kind kind;
    public final String text;
    public final int number;

    private Effect(Kind kind, String text, int number) {
        this.kind = kind;
        this.text = text;
        this.number = number;
    }

    public static Effect setFlag(String flag) { return new Effect(Kind.SET_FLAG, flag, 0); }
    public static Effect addItem(String item) { return new Effect(Kind.ADD_ITEM, item, 0); }
    public static Effect trust(int delta) { return new Effect(Kind.TRUST, null, delta); }
    public static Effect addCounter(String name) { return new Effect(Kind.ADD_COUNTER, name, 0); }
    public static Effect ending(String title) { return new Effect(Kind.ENDING, title, 0); }

    public static List<String> knownKeys() {
        return List.of("setFlag", "addItem", "trust", "addCounter", "ending");
    }
}
