# Smart Charging System

智能电动汽车充电管理系统。当前仓库目标是先搭一个可本地运行的基础骨架，用于后续补齐用户、车辆、排队调度、会话和账单主流程。

## 项目结构

```text
backend/                  Spring Boot 3 + Spring Data JPA
frontend/                 Vue 3 + Vite
docs/                     开发说明和接口规范
```

## 当前已实现

- 后端统一响应包装与全局异常处理
- 健康检查：`GET /api/health`
- 系统初始化：`POST /api/init`
- 充电站查询：`GET /api/stations`
- 枚举查询：`GET /api/enums`
- 分时计费与电价查询：`POST /api/fees/calc`、`GET /api/fees/prices`
- 前端最小控制台：健康检查、初始化演示数据、查看站点与电价

## 本地要求

- Java 17+
- Node.js 18+
- Maven 3.9+

仓库当前没有提交 `mvnw`，因此后端启动使用本机 `mvn`。

## 快速开始

### 后端

```bash
cd backend
mvn spring-boot:run
```

- API 基础路径: `http://localhost:8080/api`
- 健康检查: `GET /api/health`
- 默认使用 H2 内存数据库，无需本地 MySQL
- H2 控制台: `http://localhost:8080/api/h2-console`

如需切换到本地 MySQL：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

默认 MySQL 配置见 [backend/src/main/resources/application-mysql.yml](/Users/yumo.li/Desktop/EV/backend/src/main/resources/application-mysql.yml:1)。

### 前端

```bash
cd frontend
npm install
npm run dev
```

- 开发地址: `http://localhost:5173`
- Vite 会把 `/api` 代理到 `http://localhost:8080`

## 建议启动顺序

1. 启动后端。
2. 启动前端。
3. 打开首页先做健康检查。
4. 点击“初始化系统”生成演示数据。
5. 进入站点页确认 `GET /api/stations` 返回正常。

## 文档

- [docs/development-guide.md](/Users/yumo.li/Desktop/EV/docs/development-guide.md:1)
- [docs/api-spec.md](/Users/yumo.li/Desktop/EV/docs/api-spec.md:1)
