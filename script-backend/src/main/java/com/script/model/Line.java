package com.script.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 一句角色台词：角色名、台词内容、语气/动作备注。 */
public final class Line {
    public String id;
    public String character = "";
    public String text = "";
    public String note = "";

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("character", character);
        map.put("text", text);
        map.put("note", note);
        return map;
    }

    public static Line fromMap(Map<String, Object> map) {
        Line line = new Line();
        line.id = str(map.get("id"));
        line.character = str(map.get("character"));
        line.note = str(map.get("note"));
        line.text = str(map.get("text"));
        return line;
    }

    static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
