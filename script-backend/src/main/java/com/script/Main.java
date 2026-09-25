package com.script;

import com.script.model.Line;
import com.script.model.Scene;
import com.script.model.Script;
import com.script.server.ApiHandler;
import com.script.server.StaticHandler;
import com.script.store.ScriptStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executors;

/**
 * 应用入口：启动本地 HTTP 服务。
 *
 * 默认端口 8080，可用环境变量 PORT 或第一个启动参数覆盖；
 * /api/** 提供 JSON 数据接口，其余路径托管前端静态页面。
 */
public final class Main {

    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) throws Exception {
        int port = envInt("PORT", DEFAULT_PORT);
        if (args.length > 0) {
            port = Integer.parseInt(args[0]);
        }

        Path dataDir = resolveDataDir();
        Path frontendDir = resolveFrontendDir();

        ScriptStore store = new ScriptStore(dataDir);
        seedSampleIfEmpty(store);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api", new ApiHandler(store));
        server.createContext("/", new StaticHandler(frontendDir));
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();

        System.out.println("===================================================");
        System.out.println("  剧本创作管理服务已启动");
        System.out.println("  前端入口 : http://localhost:" + port + "/");
        System.out.println("  数据目录 : " + dataDir.toAbsolutePath());
        System.out.println("  前端目录 : " + frontendDir.toAbsolutePath());
        System.out.println("  按 Ctrl+C 停止服务");
        System.out.println("===================================================");
    }

    /** 首次启动且数据目录为空时，写入一个示例剧本。 */
    private static void seedSampleIfEmpty(ScriptStore store) throws IOException {
        if (!store.list().isEmpty()) {
            return;
        }
        Script script = new Script();
        script.id = "sample-yuye-shudian";
        script.title = "雨夜书店";
        script.author = "示例作者";
        script.synopsis = "一个暴雨之夜，即将打烊的旧书店里来了两位不速之客，一段尘封的手稿就此重见天日。";
        script.status = Script.STATUS_DRAFT;
        script.createdAt = System.currentTimeMillis();

        Scene scene1 = new Scene();
        scene1.id = ScriptStore.newId();
        scene1.heading = "内景 · 旧书店 · 夜";
        scene1.description = "雨点敲打着临街的橱窗。老周正踩着梯子整理书架，门口的铜铃突然响了。";
        scene1.lines.add(line("老周", "抱歉，我们已经打烊了。", "头也不抬地"));
        scene1.lines.add(line("林晓", "外面雨太大了，能让我躲一会儿吗？", "浑身湿透，抱着书包"));
        scene1.lines.add(line("老周", "……进来吧，别碰那排绝版书。", "叹了口气，放下手中的书"));

        Scene scene2 = new Scene();
        scene2.id = ScriptStore.newId();
        scene2.heading = "内景 · 书店阁楼 · 夜";
        scene2.description = "阁楼灯光昏黄，角落里堆着成捆的旧手稿。林晓翻开其中一本，发现扉页上没有名字。";
        scene2.lines.add(line("林晓", "这本书……为什么没有作者？", "借着昏黄的灯光翻看手稿"));
        scene2.lines.add(line("老周", "因为写它的人，还没写完。", "意味深长地望着窗外的雨"));

        script.scenes.add(scene1);
        script.scenes.add(scene2);
        store.save(script);
        System.out.println("已创建示例剧本：《" + script.title + "》");
    }

    private static Line line(String character, String text, String note) {
        Line line = new Line();
        line.id = ScriptStore.newId();
        line.character = character;
        line.text = text;
        line.note = note;
        return line;
    }

    private static Path resolveDataDir() {
        String env = System.getenv("DATA_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        // 在 script-backend 目录下运行使用 data/，在仓库根目录运行使用 script-backend/data/
        if (Files.isDirectory(Paths.get("data")) || !Files.isDirectory(Paths.get("script-backend"))) {
            return Paths.get("data");
        }
        return Paths.get("script-backend", "data");
    }

    private static Path resolveFrontendDir() {
        String env = System.getenv("FRONTEND_DIR");
        if (env != null && !env.isBlank()) {
            return Paths.get(env);
        }
        for (String candidate : new String[]{"../script-frontend", "script-frontend"}) {
            if (Files.isDirectory(Paths.get(candidate))) {
                return Paths.get(candidate);
            }
        }
        return Paths.get("../script-frontend");
    }

    private static int envInt(String name, int defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Main() {
    }
}
