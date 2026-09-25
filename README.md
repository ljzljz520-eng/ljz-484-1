# 剧本创作管理（Script Studio）

面向编剧的本地剧本创作管理工具：管理**剧本、场次、角色台词与草稿状态**。

- 后端使用 **Java（JDK 内置 HttpServer，零第三方依赖，无需 Maven/Gradle）**，提供 REST JSON 接口，数据以**本地 JSON 文件**持久化。
- 前端使用**原生 HTML / CSS / JavaScript**，包含剧本**列表页、编辑页、阅读预览页**三个页面。
- 后端启动时会同时托管前端静态页面，一个进程即可使用全部功能。

## 功能一览

| 模块 | 功能 |
| --- | --- |
| 剧本 | 新建 / 编辑 / 删除剧本，维护剧名、作者、简介 |
| 草稿状态 | 草稿 `DRAFT` → 修改中 `REVISING` → 已定稿 `FINAL`，列表页可按状态筛选 |
| 场次 | 每个剧本下添加多场戏，维护场景标题、场景描述，支持上移/下移排序与删除 |
| 角色台词 | 每场戏下维护角色名、台词内容、语气/动作备注，支持排序、编辑与删除 |
| 阅读预览 | 按剧本排版只读呈现（角色名居中、备注斜体），支持浏览器打印 / 导出 PDF |

首次启动会自动生成一个示例剧本《雨夜书店》，便于直接体验。

## 目录结构与职责

```
.
├── README.md
├── script-backend/                    # 后端（Java 11+，零依赖）
│   ├── run.sh                         # 一键编译 + 启动脚本
│   ├── data/                          # 本地数据目录（每个剧本一个 JSON 文件）
│   └── src/main/java/com/script/
│       ├── Main.java                  # 入口：启动 HTTP 服务、注册路由、生成示例数据
│       ├── model/                     # 数据模型
│       │   ├── Script.java            #   剧本（含状态、摘要统计）
│       │   ├── Scene.java             #   场次（场景标题、描述、台词列表）
│       │   └── Line.java              #   角色台词（角色、内容、备注）
│       ├── store/
│       │   └── ScriptStore.java       # JSON 文件读写（临时文件原子替换、线程安全）
│       ├── server/
│       │   ├── ApiHandler.java        # /api/** REST 接口与统一异常处理
│       │   └── StaticHandler.java     # 托管 script-frontend 静态页面（防目录穿越）
│       └── util/
│           └── Json.java              # 极简 JSON 解析/序列化（无第三方库）
└── script-frontend/                   # 前端（原生 HTML/CSS/JS）
    ├── index.html                     # 剧本列表页：状态筛选、新建弹窗、删除
    ├── editor.html                    # 编辑页：剧本信息、场次、台词增删改排序
    ├── preview.html                   # 阅读预览页：剧本排版 + 打印
    ├── css/style.css                  # 全部页面样式（含打印样式）
    └── js/
        ├── api.js                     # fetch 接口封装、状态标签/转义/Toast 等工具
        ├── list.js                    # 列表页逻辑
        ├── editor.js                  # 编辑页逻辑
        └── preview.js                 # 预览页逻辑
```

## 环境要求

- **JDK 11 或以上**（推荐 JDK 17），仅需 `javac` 与 `java` 两个命令。
- 现代浏览器（Chrome / Edge / Firefox / Safari）。
- 不需要数据库、Node.js 或任何包管理器。

## 启动步骤

### macOS / Linux

```bash
cd script-backend
./run.sh
```

脚本会自动编译 `src` 下的全部 Java 文件到 `out/`，然后启动服务。

也可以手动执行：

```bash
cd script-backend
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out com.script.Main
```

### Windows（cmd）

```bat
cd script-backend
mkdir out
dir /s /b src\*.java > sources.txt
javac -encoding UTF-8 -d out @sources.txt
java -cp out com.script.Main
```

### 打开页面

浏览器访问：**http://localhost:8080/**

- `/` → 剧本列表页
- `/editor.html?id=剧本ID` → 编辑页
- `/preview.html?id=剧本ID` → 阅读预览页

### 可选配置

| 配置 | 方式 | 默认 |
| --- | --- | --- |
| 端口 | 启动参数 `./run.sh 9000` 或环境变量 `PORT=9000` | `8080` |
| 数据目录 | 环境变量 `DATA_DIR=/path/to/data` | `script-backend/data` |
| 前端目录 | 环境变量 `FRONTEND_DIR=/path/to/frontend` | `../script-frontend` |

> 前端也可以单独用任意静态服务器（或直接打开 HTML）运行：非 8080 端口访问时会自动直连 `http://localhost:8080` 的后端接口（后端已开启 CORS）。

## 数据存储说明

- 每个剧本保存为 `script-backend/data/{剧本ID}.json` 一个文件，包含剧本信息、全部场次与台词。
- 写入采用「临时文件 + 原子替换」，避免中途退出造成文件损坏；文件可直接备份或用编辑器查看。
- 删除 `data/` 下的 JSON 文件即删除对应剧本；清空目录后重启会重新生成示例剧本。

## REST 接口概览

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/scripts` | 剧本列表（含场次/台词数量，按更新时间倒序） |
| POST | `/api/scripts` | 新建剧本，body：`title`、`author`、`synopsis` |
| GET | `/api/scripts/{id}` | 剧本详情（含全部场次与台词） |
| PUT | `/api/scripts/{id}` | 更新剧名/作者/简介/状态（字段可选传） |
| PUT | `/api/scripts/{id}/status` | 仅更新草稿状态，body：`status` |
| DELETE | `/api/scripts/{id}` | 删除剧本 |
| POST | `/api/scripts/{id}/scenes` | 添加场次，body：`heading`、`description` |
| PUT | `/api/scripts/{id}/scenes/{sceneId}` | 更新场次标题与描述 |
| DELETE | `/api/scripts/{id}/scenes/{sceneId}` | 删除场次（含其台词） |
| POST | `/api/scripts/{id}/scenes/{sceneId}/move` | 场次排序，body：`{"direction":"up"}` / `down` |
| POST | `/api/scripts/{id}/scenes/{sceneId}/lines` | 添加台词，body：`character`、`text`、`note` |
| PUT | `/api/scripts/{id}/scenes/{sceneId}/lines/{lineId}` | 更新台词 |
| DELETE | `/api/scripts/{id}/scenes/{sceneId}/lines/{lineId}` | 删除台词 |
| POST | `/api/scripts/{id}/scenes/{sceneId}/lines/{lineId}/move` | 台词排序 |

状态枚举：`DRAFT`（草稿）、`REVISING`（修改中）、`FINAL`（已定稿）。
错误响应统一为 `{"error": "错误原因"}`，HTTP 状态码 400 / 404 / 500。

快速体验：

```bash
curl http://localhost:8080/api/scripts
curl -X POST http://localhost:8080/api/scripts \
  -H "Content-Type: application/json" \
  -d '{"title":"我的新剧本","author":"我"}'
```
