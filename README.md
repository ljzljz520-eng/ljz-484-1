# 剧本创作管理（Script Manager）

面向剧本作者的轻量管理工具：管理**剧本、场次、角色台词与草稿状态**。

- 后端：纯 Java（JDK 内置 HTTP 服务，**零第三方依赖**），数据以 JSON 文件形式保存在本地
- 前端：原生 HTML / CSS / JS 单页应用（**无构建步骤**），包含列表、编辑页、阅读预览三个视图

## 目录结构与职责

```
├── README.md                  # 本文件
├── script-backend/            # Java 后端：本地文件数据接口
│   ├── run.sh                 # 一键编译并启动后端
│   ├── src/main/java/com/scriptmanager/
│   │   ├── Main.java          # 入口：启动 HTTP 服务，首次运行写入示例剧本
│   │   ├── api/ScriptApi.java # REST 接口路由与请求处理（剧本/场次/台词）
│   │   ├── store/ScriptStore.java # 本地 JSON 文件存储（每个剧本一个文件）
│   │   └── http/
│   │       ├── Json.java      # 极简 JSON 解析/序列化（无第三方依赖）
│   │       └── ApiError.java  # 业务异常 → HTTP 状态码
│   └── data/scripts/          # 运行时生成的剧本数据（*.json，已 gitignore）
│
└── script-frontend/           # 前端：原生 Web 单页应用
    ├── index.html             # 单页入口（hash 路由）
    ├── css/style.css          # 全部样式（列表 / 编辑 / 预览 / 状态徽章）
    └── js/
        ├── api.js             # 后端接口封装（改端口只需改 API_BASE）
        └── app.js             # 路由与三个视图：剧本列表、编辑页、阅读预览
```

## 环境要求

- 后端：JDK 11 及以上（`java` / `javac` 可用即可，无需 Maven）
- 前端：任意静态文件服务器（Python 3 或 Node 均可）

## 启动步骤

### 1. 启动后端（默认端口 8080）

```bash
cd script-backend
./run.sh
```

`run.sh` 会自动编译 `src/` 下所有 Java 文件并启动服务。首次启动会在
`data/scripts/` 下生成一份示例剧本《深夜咖啡馆》。

也可手动执行（Windows 同理，把 `find` 换成手动列出的源文件即可）：

```bash
cd script-backend
mkdir -p out
javac -encoding UTF-8 -d out $(find src -name "*.java")
java -cp out com.scriptmanager.Main
```

可选环境变量：

| 变量       | 默认值         | 说明           |
| ---------- | -------------- | -------------- |
| `PORT`     | `8080`         | 后端监听端口   |
| `DATA_DIR` | `data/scripts` | 剧本数据目录   |

### 2. 启动前端（默认端口 5173）

```bash
cd script-frontend
python3 -m http.server 5173
# 或者：npx serve .
```

浏览器打开 **http://localhost:5173** 即可使用。

> 若后端不在 8080 端口，修改 `script-frontend/js/api.js` 顶部的 `API_BASE`。

## 功能说明

- **剧本列表**（`#/`）：查看全部剧本的标题、作者、状态、场次数、更新时间；
  支持按状态筛选、新建剧本、删除剧本
- **编辑页**（`#/edit/{id}`）：编辑标题/作者/简介/状态；场次的增删改与上下移动；
  每个场次下角色台词（角色、动作/语气提示、台词正文）的增删改与排序
- **阅读预览**（`#/preview/{id}`）：剧本样式的纯净阅读视图——
  场景标题、场景描述、角色名居中、台词排版

### 草稿状态流转

| 状态       | 含义     |
| ---------- | -------- |
| `DRAFT`    | 草稿     |
| `REVISING` | 修改中   |
| `FINAL`    | 定稿     |

新建剧本默认为 `DRAFT`，可在编辑页「基本信息」中切换。

## API 一览

| 方法   | 路径                                                        | 说明                       |
| ------ | ----------------------------------------------------------- | -------------------------- |
| GET    | `/api/scripts`                                              | 剧本摘要列表               |
| POST   | `/api/scripts`                                              | 新建剧本                   |
| GET    | `/api/scripts/{id}`                                         | 剧本完整详情（含场次台词） |
| PUT    | `/api/scripts/{id}`                                         | 更新标题/作者/简介/状态    |
| DELETE | `/api/scripts/{id}`                                         | 删除剧本                   |
| POST   | `/api/scripts/{id}/scenes`                                  | 添加场次                   |
| PUT    | `/api/scripts/{id}/scenes/{sceneId}`                        | 更新场次                   |
| DELETE | `/api/scripts/{id}/scenes/{sceneId}`                        | 删除场次                   |
| POST   | `/api/scripts/{id}/scenes/reorder`                          | 场次排序 `{sceneIds:[…]}`  |
| POST   | `/api/scripts/{id}/scenes/{sceneId}/lines`                  | 添加台词                   |
| PUT    | `/api/scripts/{id}/scenes/{sceneId}/lines/{lineId}`         | 更新台词                   |
| DELETE | `/api/scripts/{id}/scenes/{sceneId}/lines/{lineId}`         | 删除台词                   |
| POST   | `/api/scripts/{id}/scenes/{sceneId}/lines/reorder`          | 台词排序 `{lineIds:[…]}`   |

## 数据存储

所有数据保存在 `script-backend/data/scripts/` 目录下，**每个剧本一个 JSON 文件**
（含场次与台词的完整聚合），可直接查看、备份或版本管理。
删除该目录后重启后端，会重新生成示例数据。
