package com.scriptmanager;

import com.scriptmanager.api.ScriptApi;
import com.scriptmanager.store.ScriptStore;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * 程序入口：启动 HTTP 服务，挂载 /api/ 接口；首次启动时写入一份示例剧本。
 *
 * 环境变量：
 *   PORT     监听端口，默认 8080
 *   DATA_DIR 数据目录，默认 data/scripts
 */
public class Main {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        Path dataDir = Paths.get(System.getenv().getOrDefault("DATA_DIR", "data/scripts"));

        ScriptStore store = new ScriptStore(dataDir);
        seedIfEmpty(store);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/", new ScriptApi(store));
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("剧本管理后端已启动: http://localhost:" + port + "/api/scripts");
        System.out.println("数据目录: " + dataDir.toAbsolutePath());
    }

    /** 数据目录为空时写入一份示例剧本，方便首次体验。 */
    private static void seedIfEmpty(ScriptStore store) {
        if (!store.list().isEmpty()) {
            return;
        }
        Map<String, Object> script = store.create(
                "深夜咖啡馆",
                "示例作者",
                "打烊前的咖啡馆里，两位陌生人因为一本遗落的剧本开始交谈。");

        Map<String, Object> scene1 = scene(
                "内景·咖啡馆·夜",
                "雨夜。暖黄灯光，吧台后的收音机低声播放着爵士乐。",
                line("店主", "擦着杯子", "要打烊了，不过……雨停之前你可以坐着。"),
                line("林晚", "", "谢谢。吧台上这本书，是有人落下的吗？"),
                line("店主", "看了一眼封面", "一个常客的。他说，谁翻开它，谁就得替他写完结局。"));

        Map<String, Object> scene2 = scene(
                "内景·咖啡馆·深夜",
                "雨声渐小。林晚坐在窗边，翻开了那本剧本。",
                line("林晚", "轻声读", "『第二幕：所有没说出口的话，都会在打烊前说完。』"),
                line("店主", "把一杯热牛奶放到她手边", "那看来，今晚会很长。"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> scenes = (List<Map<String, Object>>) script.get("scenes");
        scenes.add(scene1);
        scenes.add(scene2);
        store.save(script);
        System.out.println("已创建示例剧本数据。");
    }

    @SafeVarargs
    private static Map<String, Object> scene(String heading, String description, Map<String, Object>... lines) {
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("id", ScriptStore.newId());
        scene.put("heading", heading);
        scene.put("description", description);
        scene.put("lines", new ArrayList<>(Arrays.asList(lines)));
        return scene;
    }

    private static Map<String, Object> line(String character, String parenthetical, String text) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("id", ScriptStore.newId());
        line.put("character", character);
        line.put("parenthetical", parenthetical);
        line.put("text", text);
        return line;
    }
}
