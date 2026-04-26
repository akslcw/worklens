# WorkLens 企业应用时间管理平台

企业员工应用时间监控系统，通过采集员工电脑的应用使用时间，接入 AI 分析，自动判断工作/摸鱼时长和工作效率。

## 技术栈

- **后端**：Spring Boot 2.6.13 + MyBatis + MySQL 8.0
- **前端**：Vue 3 + Element Plus + ECharts
- **客户端**：Python + ttkbootstrap
- **AI**：DeepSeek API

## 项目结构
worklens/  
├── backend/      # Spring Boot 后端  
├── frontend/     # Vue 3 管理后台  
├── client/       # Python 客户端  
└── schema.sql    # 数据库初始化脚本  

## 快速启动

### 1. 数据库

```bash
docker run -d --name worklens-mysql \
  -e MYSQL_ROOT_PASSWORD=worklens123 \
  -e MYSQL_DATABASE=app_monitor \
  -p 3308:3306 \
  --restart unless-stopped \
  mysql:8.0
```

执行 `schema.sql` 初始化表结构。

### 2. 后端

配置环境变量：
DEEPSEEK_API_KEY=你的Key

```bash
cd backend
mvn spring-boot:run
```

### 3. 前端

```bash
cd frontend
npm install
npm run dev
```

### 4. 客户端

```bash
cd client
pip install pywin32 requests psutil ttkbootstrap
python app.py
```

## 主要功能

- 员工应用使用时间采集
- 规则分类 + AI 点评双层分析
- 管理后台数据总览、员工管理、效率报告
- 每日定时分析 + 手动触发