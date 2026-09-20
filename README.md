# 闪食（Flash-Bite）

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 项目简介

闪食是面向餐饮商家的模块化单体外卖后端，提供商家管理端和用户端的菜品、优惠券、订单等业务能力。当前运行架构为：

```text
客户端 -> smart-server:8080 -> MySQL / Redis / RocketMQ
```

`smart-server` 直接完成 JWT 鉴权和跨域响应。开发环境可在其前方运行本地原生 Nginx，使用基础的 `limit_req`、`limit_conn` 进行反向代理和入口保护；完整配置与启动步骤见 [deploy/nginx](deploy/nginx)。当前未声明已在公网服务器部署验证。

## 核心功能

- **分布式菜品热销榜**：用 Redis ZSet 聚合浏览、加购、下单三类行为（默认权重 1/3/10，运营可动态调整），一段 Lua 原子完成"事件幂等 + 多榜单累加"，同用户同菜品 5 分钟滚动窗口只计一次；下单热度在订单事务提交后记录，Redis 故障时事件转 RocketMQ 补偿重放。接口：`GET /user/dish/hot`、`GET /user/dish/hot/category/{categoryId}`；运营配置与指标：`GET|PUT /admin/hot-rank/config`、`GET /admin/hot-rank/metrics`。
- **优惠券秒杀与营销闭环**：秒杀走"Redis Set 一人一单去重 → Lua 原子预扣库存 → RocketMQ 异步落库"；普通领取在 DB 内用条件扣减库存，并以 `(user_id, coupon_id)` 唯一约束兜底重复领取。用户券"可用 → 锁定 → 核销 / 释放 / 过期"全部通过条件 UPDATE 流转，订单保存券面额快照。秒杀落库对**永久失败**（幂等命中、DB 库存不足）记录日志后 ack，仅**瞬时故障**抛异常交由 MQ 重试，避免无效重试与死信堆积。
- **菜品多级缓存治理**：ZSet 滑动窗口自动识别冷/热分类，热点走 Caffeine L1 + Redis L2；热缓存采用"逻辑过期 + 异步重建"避免击穿，布隆过滤器与空值缓存防穿透、随机 TTL 防雪崩；Redis 宕机自动降级直查数据库并回写 L1；写路径按"更新 DB → 失效缓存"执行，失败经 RocketMQ 补偿，并广播清理各实例 L1。
- **订单超时自动取消**：下单事务提交后发送 RocketMQ 延时消息，消费者以 `setnx` 幂等 + 分布式锁 + 条件更新只取消"待付款"订单，取消后回补库存与优惠券；支付与取消均用条件 UPDATE 防止并发覆盖。
- **商家实时推送**：WebSocket 长连接 `/ws/{merchantId}`，支持来单提醒与用户催单，替代高频轮询。

## 本地 Nginx 代理

开发链路为 `客户端 -> 本地 Nginx:80 -> smart-server:8080`。Nginx 不验证 JWT；它透传鉴权 Header，并将登录、领券和下单端点按 IP 做基础限流。配置、规则、启动、重载、停止和测试步骤见 [deploy/nginx/README.md](deploy/nginx/README.md)。

## Git 提交建议

- 不提交 `doc/key/`、`doc/tmp/`、私钥、密码、Token、日志、`target/` 或 `.vscode/`。
- 每个提交只承载一个可验证目标；提交前运行对应测试、`git diff --check`，并检查暂存区内容。
- Gateway/Sentinel 删除、JWT 迁移、本地 Nginx 配置应拆为独立提交，便于审阅和回滚。
- 推荐提交说明：

```text
refactor(architecture): remove gateway and sentinel
feat(auth): verify JWT in server interceptor
chore(nginx): add native local proxy and rate limits
docs: record gateway removal and local nginx workflow
```

## 模块

- `smart-common`：公共工具、异常、常量、配置属性和统一返回结构。
- `smart-pojo`：实体、DTO、VO 等数据模型。
- `smart-server`：核心业务服务，默认监听 `8080`，包含菜品、优惠券、订单和用户相关接口。

## 鉴权与跨域

- 用户端受保护接口使用配置的 `authentication` Header，管理员接口使用 `token` Header；两者均兼容 `Authorization: Bearer <token>`。
- 服务端自行验证 JWT 并设置请求上下文；客户端传入的 `X-User-Id`、`X-Admin-Id` 不会被信任。
- 登录、公开菜品/分类查询和接口文档在白名单中；领取优惠券、下单等业务接口需要 JWT。
- 跨域由 `smart-server` 的 MVC 配置响应，允许常用 HTTP 方法和请求头，但不允许跨域携带 Cookie；JWT 通过请求头传递。

## 技术栈

| 类别 | 组件 |
|---|---|
| 基础框架 | Spring Boot 3.3.3 |
| 持久层 | MyBatis、PageHelper、Druid、MySQL |
| 缓存与锁 | Redis、Redisson、Caffeine |
| 异步消息 | RocketMQ |
| 实时推送 | Spring WebSocket（`@ServerEndpoint`） |
| 可观测性 | Micrometer / Spring Boot Actuator |
| 接口文档 | Knife4j / OpenAPI 3 |
| 鉴权 | JJWT |

## 本地启动

1. 准备 MySQL、Redis 与 RocketMQ，并在本地环境配置中提供相应连接信息及可选的微信配置。
2. 启动业务服务：

   ```bash
   mvn -pl smart-server -am spring-boot:run
   ```

3. 访问接口文档：`http://localhost:8080/doc.html`。

## 已通过测试

```bash
mvn "-Dsurefire.failIfNoSpecifiedTests=false" -pl smart-server -am test
```

| 测试类 | 覆盖点 |
|---|---|
| `UserContextInterceptorTest` | 登录签发 JWT 后可访问受保护接口、伪造身份 Header 被拒绝、管理员与用户 token 不可混用、公开菜品查询正常返回 |
| `HotDishRankingServiceImplTest` | 浏览 5 分钟滚动去重、Redis 故障不阻塞主流程、分类榜过滤下架/错分类、小时榜与周榜窗口 Key |
| `DishServiceCacheHitRateTest` | 热分类命中 L1 时不回落数据库（模拟命中率 99%） |
| `DishServiceColdCacheFallbackTest` | 冷/热分类在 Redis 宕机或超时下降级直查 DB、回种失败仍返回数据、布隆过滤器 fail-open |

热销榜另有 2026-09-09 的真机全链路 E2E 记录（Redis + RocketMQ + MySQL，覆盖浏览/加购/下单计分、去重、权重热更新与指标），见 `doc/AGENTS.md`。外部中间件部署与微信登录的完整集成测试仍需在具备相应环境时执行。
