# WorkLens

WorkLens 采集 Windows 桌面应用使用数据，为员工提供个人回顾，为管理者提供隐私受限的团队聚合分析和自动报告。

> 当前状态：已完成八阶段真实回归测试。

## 技术栈

- 后端：Java 17、Spring Boot、MyBatis-Plus、PostgreSQL
- 前端：Vue 3、TypeScript、Vite
- 桌面客户端：Python、pywin32、pystray、SQLite
- AI 报告：DeepSeek API

## 项目结构

```text
.
├── worklens_backend/         # Spring Boot API、权限控制、报告任务和后端镜像
├── worklens_frontend/        # Vue 应用、Nginx 代理配置和前端镜像
├── worklens_desktop_client/  # Windows 桌面采集和托盘客户端
├── docs/                     # 设计说明与阶段记录
├── compose.yml               # 前端、后端与 PostgreSQL 一键编排
└── .env.example              # 环境变量示例
```

## 快速体验（一键启动）

这条路径适合第一次获取项目后快速查看完整 Web 系统。宿主机只需要安装 Docker Desktop 与 Docker Compose，不需要安装 Java、Maven、Node.js、npm 或 PostgreSQL。Windows 桌面采集客户端不参与容器化，仍需按开发模式中的说明运行在员工电脑上。

在项目根目录执行一条命令：

```powershell
docker compose up
```

Compose 会依次完成以下工作：

1. 启动 PostgreSQL，并等待数据库健康检查通过。
2. 在容器内构建并启动 Spring Boot 后端。
3. 在容器内构建 Vue 静态文件，启动 Nginx，并等待后端健康检查通过。
4. 由 Nginx 将浏览器的 `/api/*` 请求转发到 Docker 网络内的 `backend:8080`。

启动完成后访问：

- Web 前端：`http://localhost:5173`
- 后端健康检查：`http://localhost:8080/health`

首次冷构建需要下载基础镜像和依赖，耗时会长于后续启动。若希望后台运行，可使用 `docker compose up -d`；修改源码后需要强制重建镜像时，可增加 `--build`。查看状态和日志可执行：

```powershell
docker compose ps
docker compose logs -f
```

停止服务但保留 PostgreSQL 数据：

```powershell
docker compose down
```

所有 Compose 配置均有本地体验默认值，因此 `.env` 不是启动前置条件。如需修改端口、数据库凭据、报告任务或配置真实的 DeepSeek API Key，可先执行 `Copy-Item .env.example .env` 再编辑 `.env`。API Key 未配置或留空时，Compose 会给后端传入不可用的占位凭据：系统仍能启动，登录、权限、采集和非 LLM 页面不受影响；实际 LLM 调用会通过现有错误处理返回失败，报告来源数据会保留且不会写入半成品。

PostgreSQL 用户名和密码只在数据卷首次初始化时生效。已有数据卷创建后若修改这两项，应先在数据库中同步修改凭据；仅用于本地体验且允许删除全部数据时，也可执行 `docker compose down -v` 后重新启动。

> 全新数据库只会初始化表结构。首次部署时在 `.env` 中设置 `WORKLENS_ADMIN_PASSWORD`（配合 `WORKLENS_ADMIN_USERNAME`/`WORKLENS_ADMIN_NAME`），后端启动后会自动创建首个绑定员工档案的 `MANAGER` 账号；该账号首次登录强制改密。密码留空则跳过创建——这与生产环境的"预置账号"方式互不冲突（已存在任何 MANAGER 时自动跳过）。

## Windows 桌面客户端（免 Python 运行）

普通员工不需要安装 Python 或任何依赖。推荐从项目的 GitHub Releases 下载 `WorkLens-windows-x64.zip`，完整解压后保留目录结构，不能只复制其中的 `WorkLens.exe`。

首次运行前，打开 exe 同目录的 `config.ini`（发布包内置的空白模板），填写后端地址：

```ini
[backend]
base_url=http://localhost:8080
```

- 仅在本机 Docker Compose 演示环境可填写 `http://localhost:8080`。明文 HTTP 流量可被本机其他进程窃听，**局域网或远程部署必须使用 HTTPS 地址**（客户端对非本机地址强制要求 HTTPS，否则拒绝启动并提示配置错误）。
- 留空或不填写时客户端会提示"base_url 为空"。
- 客户端不再接受 `--password` 命令行参数：密码只通过登录对话框输入，避免出现在进程命令行中。修改配置后无需重新打包。

员工应先在 Web 端完成首次登录和强制改密，然后双击 `WorkLens.exe`：

1. 在登录窗口输入工号和密码。
2. 登录成功后程序进入系统托盘，菜单显示运行状态和当前员工。
3. “开机自动启动”默认关闭；员工可以在托盘菜单中主动开启或再次关闭。客户端不会保存密码，开机启动后仍会显示登录窗口。
4. 选择“退出”会停止采集并真正退出进程。

离线缓存和运行日志保存在 `%LOCALAPPDATA%\WorkLens`，日志文件为 `%LOCALAPPDATA%\WorkLens\logs\worklens.log`。

维护者需要重新构建客户端时，构建机需要 Python，员工电脑不需要。在项目根目录执行：

```powershell
.\worklens_desktop_client\build_exe.ps1
```

输出目录为 `worklens_desktop_client/dist/WorkLens/`，同时生成可直接上传到 GitHub Releases 的 `worklens_desktop_client/dist/WorkLens-windows-x64.zip`。`build/`、`dist/`、exe 和 ZIP 均属于构建产物，已被 Git 忽略。

## 开发模式（分步启动）

这条路径适合日常开发调试，保留 Spring Boot 和 Vite 的热更新体验。前后端直接运行在宿主机，Docker Compose 只启动 PostgreSQL。

以下命令均从项目根目录或对应子目录执行，只使用项目相对路径。示例使用 PowerShell。

### 环境要求

- Windows（桌面采集依赖 pywin32）
- JDK 17，并正确设置 `JAVA_HOME`
- Docker Desktop 与 Docker Compose
- Node.js `20.19+` 或 `22.12+`，以及 npm
- Python 3 与 pip
- DeepSeek API Key；没有真实 Key 时可在本地使用非空占位值启动，LLM 调用会失败

### 1. 准备环境变量

复制示例文件：

```powershell
Copy-Item .env.example .env
```

填写 `.env` 中的数据库连接信息，并补充真实 DeepSeek API Key；只调试非 LLM 功能时可使用 `disabled`：

```dotenv
WORKLENS_DB_HOST=127.0.0.1
WORKLENS_DB_PORT=5432
WORKLENS_DB_NAME=worklens
WORKLENS_DB_USERNAME=worklens
WORKLENS_DB_PASSWORD=change-me
WORKLENS_DEEPSEEK_API_KEY=disabled
```

数据库密码和 API Key 不应提交到版本库。DeepSeek 地址、模型、超时以及报告定时配置均可继续通过环境变量覆盖，详见“自动化报告体系”。

### 2. 启动 PostgreSQL

`compose.yml` 会自动读取项目根目录的 `.env`。开发模式只启动数据库服务：

```powershell
docker compose up -d postgres
```

查看数据库容器状态：

```powershell
docker compose ps
```

### 3. 启动 Spring Boot 后端

在新的 PowerShell 窗口中，从项目根目录把 `.env` 载入当前进程，再启动后端：

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
    Set-Item -Path "Env:$($matches[1].Trim())" -Value $matches[2]
  }
}
Set-Location worklens_backend
.\mvnw.cmd spring-boot:run
```

这段脚本只把简单的 `KEY=value` 配置载入当前 PowerShell 进程；每个新的后端或后端测试窗口都需要重新执行，不要在值两侧添加引号或行尾注释。

后端默认监听 `http://localhost:8080`，启动时会通过 `schema.sql` 创建或补齐表结构。

验证无需认证的公开健康检查：

```powershell
Invoke-RestMethod http://localhost:8080/health
```

预期返回 `OK`。

> 全新数据库只会初始化表结构。开发模式下可通过 `WORKLENS_ADMIN_PASSWORD` 等环境变量创建首个管理者（见"快速体验"），也可沿用预置账号方式。

### 4. 启动 Vue 前端

在新的 PowerShell 窗口中执行：

```powershell
Set-Location worklens_frontend
npm install
npm run dev
```

默认访问地址为 `http://localhost:5173`。浏览器侧请求使用 `/api/*`，Vite 会移除 `/api` 前缀后代理到 `http://127.0.0.1:8080`；接口表列出的均是后端直连路径。

### 5. 启动桌面托盘客户端

员工应先在 Web 端完成首次登录和强制改密。然后在新的 PowerShell 窗口中，从项目根目录安装依赖：

```powershell
python -m pip install -r worklens_desktop_client/requirements.txt
```

启动 Windows 托盘客户端：

```powershell
pythonw -m worklens_desktop_client.tray_app
```

客户端会弹出登录窗口。托盘菜单显示运行状态和当前登录员工，选择“退出”会停止后台采集并退出进程。

如需在控制台观察采集和同步日志，可改用：

```powershell
python -m worklens_desktop_client.run_sync_client --base-url http://localhost:8080
```

## 核心功能

### 登录与账号体系

- 用户名等于员工工号；管理者新增员工时，系统同步创建登录账号。
- 新建员工或重置密码时，系统生成独立的 20 位随机临时密码，并强制包含大写字母、小写字母、数字和符号。
- 临时密码只在本次接口响应和管理页面中展示一次；首次登录或密码重置后必须修改密码。
- 强制改密由三层共同拦截：后端禁止访问业务 API，前端路由只允许进入改密页，桌面客户端拒绝启动采集。
- 桌面客户端只允许员工账号运行；服务端始终以有效登录身份判定账号和角色。

### 双视角权限边界

- 员工对本人的使用数据和报告拥有常规访问权限；管理者只有在该员工批准后才能一次性查看个人明细。
- 管理者默认只能访问团队聚合数据，不能直接读取员工个人明细。
- 团队聚合和团队报告只包含应用、累计时长及占比，不包含员工身份或个人记录。
- 服务端不信任前端或桌面客户端传入的身份字段，数据归属以登录 token 解析出的身份为准。

### 审计授权流程

1. 管理者选择目标员工并填写理由，发起个人明细查看申请。
2. 只有目标员工本人可以批准或拒绝申请。
3. 批准后，申请仅提供一次查看机会。
4. 管理者实际查看时写入访问记录，授权随即失效，不能重复使用。

### 桌面采集客户端

- 每 5 秒采样一次当前活跃进程，只记录应用名或进程名。
- 连续 5 分钟没有键盘或鼠标操作时，将该时间段记为 `Idle`。
- 每 5 分钟合并并批量上报采集记录。
- 网络或服务异常时写入本地 SQLite 缓存，连接恢复后自动补传。
- 后端地址由 exe 同目录的 `config.ini` 提供，无需重新打包即可修改。
- 托盘图标展示运行/停止状态、当前登录员工，并提供真实退出操作。
- 开机自启动默认关闭，员工可以通过托盘菜单自主开启或关闭。
- SQLite 缓存和滚动运行日志保存在 `%LOCALAPPDATA%\WorkLens`。

### 前端使用视图

- 员工端显示当天实时应用卡片，可展开查看应用使用时间段。
- 历史日期展示覆盖该日期的已归档日报、周报或月报。
- 管理者端提供团队聚合面板（当日实时聚合；历史数据由团队报告承载）、团队报告历史和审计申请页面。
- 员工端提供审批与访问记录页面，可查看申请状态及授权是否已被实际使用。

## 自动化报告体系

报告采用严格的三级归档链路：

```text
原始使用记录 → 日报 → 周报 → 月报
```

默认时区为 `Asia/Hong_Kong`，默认执行时间如下：

| 报告 | 默认执行时间 | 数据来源 |
| --- | --- | --- |
| 日报 | 每天 23:55 | 当天原始使用记录 |
| 周报 | 每周一 00:30 | 上周（截至周日）已生成日报 |
| 月报 | 每月 1 日 01:00 | 上月范围内已生成周报 |

系统遵循以下归档规则：

- 日报只汇总原始使用记录，周报只汇总已生成日报，月报只汇总已生成周报，不跨层回查或补齐。
- 报告成功落库后清理对应的上一级来源数据。
- DeepSeek 调用或归档失败时保留来源数据，不写入半成品报告。
- 每份报告包含应用名、累计时长、占比以及 AI 生成的中文总结。
- 团队报告只使用聚合指标，不包含员工姓名、工号、用户名、原始记录或个人拆分数据。
- 手动生成入口已经下线；通过认证和角色检查后，`POST /llm/employee-report` 与 `POST /llm/team-report` 返回 `410 Gone`。

可选的环境变量覆盖项：

| 环境变量 | 默认值 | 用途 |
| --- | --- | --- |
| `WORKLENS_REPORTS_DAILY_CRON` | `0 55 23 * * *` | 日报任务 Cron |
| `WORKLENS_REPORTS_WEEKLY_CRON` | `0 30 0 * * MON` | 周报任务 Cron（周一凌晨执行，聚合截至周日） |
| `WORKLENS_REPORTS_MONTHLY_CRON` | `0 0 1 * * *` | 月报任务 Cron（每天 01:00 候选，代码判断昨日是否为月末） |
| `WORKLENS_REPORTS_RETRY_CRON` | `0 10 0 * * *` | 失败补跑任务 Cron（重试近 7 天日报、近 4 周周报、近 2 个月月报） |
| `WORKLENS_REPORTS_ZONE` | `Asia/Hong_Kong` | 报告任务时区 |
| `WORKLENS_DEEPSEEK_BASE_URL` | `https://api.deepseek.com` | DeepSeek API 地址 |
| `WORKLENS_DEEPSEEK_MODEL` | `deepseek-v4-flash` | 报告生成模型 |
| `WORKLENS_DEEPSEEK_CONNECT_TIMEOUT` | `5s` | 连接超时 |
| `WORKLENS_DEEPSEEK_READ_TIMEOUT` | `15s` | 读取超时 |

## 主要接口

除登录和健康检查外，业务接口均需要有效的 Bearer token。以下仅列主要用途：

| 用途 | 主要接口 |
| --- | --- |
| 健康检查 | `GET /health` |
| 登录与账号状态 | `POST /auth/login`、`GET /auth/me`、`POST /auth/change-password` |
| 员工档案与密码重置 | `GET/POST /employees`、`GET/PUT/DELETE /employees/{id}`、`POST /employees/{id}/reset-password` |
| 使用数据与个人视图 | `POST /usage-records`、`GET /usage-records`、`GET /usage-records/view` |
| 团队聚合 | `GET /team-usage-summary` |
| 审计申请与审批 | `POST /detail-access-requests`、`GET /detail-access-requests`、`GET /detail-access-requests/targeting-me`、`PATCH /detail-access-requests/{id}/decision` |
| 一次性查看与访问记录 | `GET /detail-access-requests/{id}/usage-view`、`GET /detail-access-requests/{id}/access-logs` |
| 报告历史 | `GET /llm/employee-report-history`、`GET /llm/team-report-history` |

## 测试与验证

项目已完成覆盖登录、权限、审计、采集、断线恢复、前端视图和三级自动报告链路的八阶段真实回归测试。日常验证命令如下。

### 后端测试

后端集成测试会清空其目标数据库中的业务表，严禁直接连接开发或生产数据库。首次测试时，从项目根目录创建独立测试库；如果该库已经存在，可跳过此命令：

```powershell
docker compose exec postgres sh -c 'createdb -U "$POSTGRES_USER" worklens_test'
```

然后在新的 PowerShell 窗口中，从项目根目录载入 `.env`，把数据库名覆盖为测试库，再执行测试：

```powershell
Get-Content .env | ForEach-Object {
  if ($_ -match '^\s*([^#][^=]*)=(.*)$') {
    Set-Item -Path "Env:$($matches[1].Trim())" -Value $matches[2]
  }
}
$env:WORKLENS_DB_NAME='worklens_test'
Set-Location worklens_backend
.\mvnw.cmd test
```

测试完成后请关闭该 PowerShell 窗口；如果要在同一窗口启动开发后端，先重新载入 `.env`，避免继续连接 `worklens_test`。

### 前端测试与构建

在新的 PowerShell 窗口中，从项目根目录执行：

```powershell
Set-Location worklens_frontend
npm test
npm run build
```

### 桌面客户端测试

在新的 PowerShell 窗口中，从项目根目录执行：

```powershell
python -m unittest discover worklens_desktop_client/tests
```

验证可执行程序构建：

```powershell
.\worklens_desktop_client\build_exe.ps1
```

阶段 C 已在清空 Python 相关环境变量、且 `PATH` 中不存在 Python 的隔离目录中运行打包产物，真实完成登录、采样和上报；验证记录和截图位于 `docs/deployment-evidence/phase-c/`。

## 已知限制

- 小团队的聚合数据在数学上可能接近个体明细，当前尚未设置最小分组人数阈值（决策记录：暂不处理，先文档标注风险）。
- 桌面客户端只采集应用名或进程名，不采集窗口标题、浏览器 URL 或页面内容。
- 当前 Windows exe 未购买代码签名证书，因此首次运行时可能触发 SmartScreen“未知发布者”警告，也可能被部分杀毒软件误报；决策记录：暂缓购买（B 方案），先内部试用验证产品。
- 月报严格汇总已生成周报；周报边界与自然月边界不完全一致时，月报覆盖范围可能与自然月略有不同。
- 新建员工或重置密码时会生成独立的随机临时密码，并仅在接口响应和管理页面中展示一次；在密码安全传递给员工并完成首次改密之前，仍存在短暂的凭证交付窗口风险。
- `POST /usage-records` 会静默忽略客户端传入的 `employeeId`，记录归属仍以登录身份为准，但接口提示还不够严格。
- 删除员工为软删除（账号停用、历史数据保留），默认 30 天后由后台任务物理清理（`WORKLENS_RETENTION_DAYS` 可调）。
