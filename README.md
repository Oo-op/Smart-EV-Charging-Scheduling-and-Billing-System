# Smart Charging System

一个更接近真实网页应用形态的智能充电系统示例，包含用户端与管理员后台两套入口。

## 当前能力

- 用户注册、登录、绑车、预约充电、查看订单、支付账单
- 管理员登录、查看运营看板、监控充电桩、查看队列、处理故障桩
- 系统启动后自动准备基础站点、充电桩、电价和默认管理员账号
- 默认使用 H2 内存数据库，本地无需先安装 MySQL

## 默认管理员账号

- 用户名：`admin`
- 密码：`admin123456`

## 启动

后端：

```bash
cd backend
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

访问地址：

- 前端：`http://localhost:5173`
- 后端：`http://localhost:8080/api`
- H2 控制台：`http://localhost:8080/api/h2-console`

如需切换 MySQL：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

配置文件见 [application-mysql.yml](/Users/yumo.li/Desktop/EV/backend/src/main/resources/application-mysql.yml:1)。

## 保留文档

- [docs/api-spec.md](/Users/yumo.li/Desktop/EV/docs/api-spec.md:1)
