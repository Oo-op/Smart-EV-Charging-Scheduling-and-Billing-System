# Smart Charging System

智能电动汽车充电管理系统

## 项目结构

```
smart-charging-system/
├── backend/              # 后端 (Spring Boot 3 + Java 17)
│   ├── src/main/java/com/example/charging/
│   │   ├── controller/   # 控制器层 (REST API)
│   │   ├── service/      # 业务逻辑层
│   │   ├── repository/   # 数据访问层 (JPA)
│   │   ├── entity/       # 数据库实体
│   │   ├── dto/          # 数据传输对象
│   │   ├── enums/        # 枚举定义
│   │   ├── common/       # 通用工具 (响应包装等)
│   │   └── config/       # 配置类 (CORS 等)
│   └── src/main/resources/
│       ├── application.yml
│       └── schema.sql
└── frontend/             # 前端 (Vue 3 + Vite)
    └── src/
        ├── api/          # API 请求封装
        ├── views/        # 页面视图
        ├── router/       # 路由配置
        └── components/   # 可复用组件
```

## 快速开始

### 后端

```bash
cd backend
./mvnw spring-boot:run
```

- API 基础路径: `http://localhost:8080/api`
- 健康检查: `GET /api/health`

### 前端

```bash
cd frontend
npm install
npm run dev
```

- 开发地址: `http://localhost:5173`
