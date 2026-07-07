# 闪食 (Flash-Bite) - 餐饮自营外卖服务平台

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-green.svg)](https://spring.io/projects/spring-boot)
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2023.0.3-blue.svg)](https://spring.io/projects/spring-cloud)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

## 1. 项目简介

闪食是一套面向餐饮商家的自营外卖后端系统，覆盖商家管理端与微信小程序客户端双端能力。项目聚焦电商后端高并发经典场景，针对优惠券秒杀、接口限流、缓存优化、消息异步解耦等问题提供了完整的工程化实现，旨在为电商后端开发者提供学习参考。

> **项目状态**：当前为半成品版本，核心功能持续迭代开发中

## 2. 项目模块

项目采用多模块 Maven 架构：

- **smart-common**：通用基础模块，封装公共工具类、全局统一返回结果、全局异常处理、常量定义等公共能力
- **smart-pojo**：数据实体模块，存放数据库实体类、DTO、VO 等各类数据传输对象
- **smart-gateway**：网关服务模块，基于 Spring Cloud Gateway 构建，提供统一鉴权、路由转发、双层限流、熔断降级、跨域处理等网关能力([查看文档](smart-gateway/README.md))
- **smart-server**：核心业务模块，承载外卖平台全量业务逻辑，包含商家管理、菜品管理、优惠券秒杀、订单全生命周期等核心功能([查看文档](smart-server/README.md))

## 3. 核心能力

### 1. 高并发优惠券秒杀

针对秒杀场景设计全链路性能优化方案：

- 基于 Redis 预存优惠券库存，通过 Lua 脚本实现原子性库存扣减，从源头避免超卖问题
- 集成 RocketMQ 实现订单异步落库，削峰填谷，大幅提升秒杀接口吞吐量
- 多层限流防护，从网关层到业务层全链路管控，保障高并发下系统稳定性

### 2. 统一网关与流量治理

基于 Spring Cloud Gateway 构建系统统一入口，实现全方位流量管控：

- 统一 JWT 鉴权校验、全局跨域处理、请求路由转发，对外提供统一访问入口
- 集成 Sentinel 实现路由级别的接口限流与熔断降级，支持秒级流量精准管控
- 基于 Redis 令牌桶算法 + Guava 本地降级实现用户 ID、IP 维度的精细化限流，有效防止接口恶意刷取

### 3. 多级缓存与缓存问题治理

针对菜品查询场景构建高性能缓存体系，解决经典缓存问题：

- **Caffeine L1 + Redis L2** 二级缓存架构，热点数据下沉至本地缓存
- 结合**滑动窗口算法**统计访问热点，实现冷热数据智能分离
- 针对性解决缓存**穿透、击穿、雪崩**三大经典问题

### 4. 订单超时自动取消

基于 RocketMQ 延时消息实现订单状态自动化流转：

- 用户下单成功后发送延时消息，到期自动校验订单支付状态
- 超时未支付订单自动取消，同步释放占用的菜品库存与用户优惠券，避免资源无效占用

.....

## 4. 技术栈

### 1. 核心框架

| 类别        | 组件名称                 | 版本号        |
|-----------|----------------------|------------|
| 基础框架      | Spring Boot          | 3.3.3      |
| 微服务组件集    | Spring Cloud         | 2023.0.3   |
| 阿里云微服务生态  | Spring Cloud Alibaba | 2023.0.1.0 |
| ORM 持久层框架 | MyBatis              | 3.0.4      |
| 分页增强插件    | PageHelper           | 1.4.6      |
| 数据库连接池    | Druid                | 1.2.18     |

### 2. 中间件

| 类别         | 组件名称                | 版本号    |
|------------|---------------------|--------|
| 关系型数据库     | MySQL               | 8.0.37 |
| 分布式缓存      | Redis               | 5.0.14 |
| 分布式锁与缓存增强  | Redisson            | 3.27.2 |
| JVM 本地一级缓存 | Caffeine            | 3.1.8  |
| 分布式消息队列    | RocketMQ            | 5.3.1  |
| 流量治理与熔断降级  | Sentinel            | 1.8.9  |
| 接口文档组件     | Knife4j (OpenAPI 3) | 4.5.0  |

### 工具与特性

| 类别           | 组件 / 特性              | 版本 / 说明     |
|--------------|----------------------|-------------|
| 运行环境         | JDK                  | 21，支持虚拟线程特性 |
| 代码简化工具       | Lombok               | -           |
| Java 通用工具类库  | Hutool               | 5.8.40      |
| JSON 处理框架    | FastJSON2            | 2.0.53      |
| Java 语言增强工具类 | Apache Commons Lang3 | 3.17.0      |
| JWT 鉴权组件     | JJWT                 | 0.11.5      |
| 本地限流         | Guava                | 32.1.3-jre  |

## 5. 环境要求

| 环境 / 依赖工具          | 版本要求      |
|--------------------|-----------|
| JDK                | 21 及以上    |
| Maven              | 3.6 及以上   |
| MySQL              | 8.0.x     |
| Redis              | 5.0.x 及以上 |
| RocketMQ           | 5.3.x     |
| Sentinel Dashboard | 1.8.9     |

## 6. 快速启动

### 1. 克隆项目

```bash
git clone https://github.com/6uninu9/flash-bite.git
cd flash-bite
```

### 2. 中间件准备

依次安装并启动以下依赖中间件：

1. **MySQL**：创建项目对应数据库，执行sql目录下的init脚本

2. **Redis**：启动 Redis 服务，默认端口 6379

3. **RocketMQ**：启动 NameServer 与 Broker 服务，默认端口 9876

4. Sentinel Dashboard：

   - 下载地址：[Sentinel v1.8.9 发布页](https://link.wtturl.cn/?target=https%3A%2F%2Fgithub.com%2Falibaba%2FSentinel%2Freleases%2Ftag%2F1.8.9&scene=im&aid=497858&lang=zh)
   - 启动命令：

   ```bash
   java -Dserver.port=8099 -Dcsp.sentinel.dashboard.server=localhost:8099 -Dproject.name=sentinel-dashboard -Dcsp.sentinel.port=8721 -Dcsp.sentinel.web.context.unify=false -Dcsp.sentinel.http.method.specify=true -jar sentinel-dashboard.jar
   ```

### 3. 配置修改

分别添加 `smart-server` 与 `smart-gateway` 模块下的 `application-dev.yml` 配置文件，补充对应环境的配置：

`smart-server`:
- 数据库连接信息（地址、端口、账号、密码、库名）
- Redis 连接信息（地址、端口、密码、库号）
- RocketMQ NameServer 地址
- 微信小程序相关配置（可选）

`smart-gateway`:
- Redis 连接信息（地址、端口、密码、库号）

> 限流、熔断规则已通过本地文件配置，位于 `smart-gateway/src/main/resources/sentinel/` 目录下，启动即可生效

### 4. 启动服务

1. 启动 `smart-server` 核心业务服务（默认端口：8081）
2. 启动 `smart-gateway` 网关服务（默认端口：8080）

### 5. 访问验证

- 接口文档地址：[http://localhost:8080/doc.html](https://link.wtturl.cn/?target=http%3A%2F%2Flocalhost%3A8080%2Fdoc.html&scene=im&aid=497858&lang=zh)
- Sentinel 控制台地址：[http://localhost:8099](https://link.wtturl.cn/?target=http%3A%2F%2Flocalhost%3A8099&scene=im&aid=497858&lang=zh)
- 网关统一入口：[http://localhost:8080](https://link.wtturl.cn/?target=http%3A%2F%2Flocalhost%3A8080&scene=im&aid=497858&lang=zh)

## 7.后续规划

项目持续迭代中，后续将逐步完善以下内容：

- 补全商家管理端全量业务功能与用户端部分业务功能
- 使用 nginx 在网络层截断恶意流量，避免网关被海量连接打挂
- 将 Sentinel 下沉到下游服务做接口级限流、熔断以及线程隔离，解决下游服务降级后异常无法感知的问题，网关层 Sentinel 不再进行限流，只做服务级熔断降级。
- 使用 MySQL 与 Redis 存储网关路由规则与 Sentinel 限流熔断规则，实现动态规则修改
- ......

## 8. 说明

本项目主要用于电商后端技术学习与参考，欢迎提交 Issue 与 PR 交流。
