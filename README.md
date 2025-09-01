# tg-rpc-event

TG 模块化通信栈：基于 RabbitMQ 的 **同步 RPC**（`@RpcApi`）与 **异步事件**（`@MqChannel`）Spring Boot Starter。

| 模块 | Artifact | 说明 |
|------|----------|------|
| MQ RPC | `spring-boot-starter-mq-rpc` | 跨模块同步调用，Feign 等价物，底层 MQ Request-Reply |
| MQ Event | `spring-boot-starter-mq-event` | 契约驱动的 Pub/Sub 与 Request-Reply 事件 |

## 技术栈

- Java 21
- Spring Boot 3.5.5
- Spring Cloud Stream + RabbitMQ Binder
- 未配置外部 Broker 时自动启用嵌入式 `rabbitmq-mock`（本地开发零依赖）

## 快速开始

### 引入依赖

```xml
<!-- 同步 RPC -->
<dependency>
    <groupId>pub.module</groupId>
    <artifactId>spring-boot-starter-mq-rpc</artifactId>
    <version>1.1.0</version>
</dependency>

<!-- 异步事件 -->
<dependency>
    <groupId>pub.module</groupId>
    <artifactId>spring-boot-starter-mq-event</artifactId>
    <version>1.1.0</version>
</dependency>
```

### 连接 RabbitMQ（可选）

未配置 `spring.rabbitmq.host` 或 `spring.rabbitmq.addresses` 时，自动使用进程内 Mock Broker。生产环境示例：

```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
```

---

## MQ RPC（`@RpcApi`）

### 概念

- 在 `*-api` 模块定义带 `@RpcApi` 的契约接口（命名约定：`pub.module.{module}.api.service.ApiXxxService`）
- 提供方实现该接口并注册为 Spring Bean
- 调用方直接 `@Autowired` 注入契约接口，框架自动创建 MQ 动态代理
- 路由键由包路径自动推导：`pub.module.{module}` → 队列 `api.rpc.{module}`

### 契约示例

```java
package pub.module.order.api.service;

import pub.module.mqrpc.RpcApi;
import pub.module.mqrpc.RpcCompensate;

@RpcApi
public interface ApiOrderService {

  OrderDto createOrder(CreateOrderCmd cmd);

  @RpcCompensate(forMethod = "createOrder")
  void compensateCreateOrder(CreateOrderCmd cmd);
}
```

### 提供方实现

```java
@Service
public class OrderServiceImpl implements ApiOrderService {

  @Override
  public OrderDto createOrder(CreateOrderCmd cmd) {
    // 业务逻辑
    return new OrderDto();
  }

  @Override
  public void compensateCreateOrder(CreateOrderCmd cmd) {
    // Saga 补偿逻辑
  }
}
```

### 调用方使用

```java
@Service
@RequiredArgsConstructor
public class CheckoutService {

  private final ApiOrderService orderService; // 自动注入 MQ 代理

  public void checkout(CreateOrderCmd cmd) {
    orderService.createOrder(cmd);
  }
}
```

### 分布式事务（Saga）

- `@RpcApi(transactional = true)`（默认）启用 Saga：调用链失败时按逆序补偿
- 补偿方法通过 `@RpcCompensate(forMethod = "...")` 标注，或按约定命名为 `compensate{Method}`
- 事务记录保存在进程内存中（随请求生命周期），`txXid` / `branchId` 通过 MQ `RpcEnvelope` 传递
- 管理页面：`/pub/tg-rpc-tx`（REST API 位于 `/pub/tg-rpc-tx/api`）

### RPC 配置项

```yaml
tg:
  module-call:
    default-timeout-ms: 5000   # Request-Reply 超时（毫秒）
    tx:
      enabled: true            # 是否启用 Saga 分布式事务
      admin-enabled: true      # 是否启用事务管理页面与 API
```

---

## MQ Event（`@MqChannel`）

### 概念

- 在 `*-api` 模块的 `pub.module.{module}.api.subscribe` 包下定义 `XxxConsumer` 契约接口
- 用 `@MqChannel` 标记根契约，声明单向（`FIRE_AND_FORGET`）或 `REQUEST_REPLY` 模式
- 消费方实现契约接口（或嵌套 `@MqSubscribe` 槽位子接口）并注册为 Spring Bean
- `destination`、`function`、`group` 等命名可由包路径与方法名自动推导

### 单向事件契约

```java
package pub.module.system.api.subscribe;

import pub.module.mqevent.MqChannel;
import pub.module.mqevent.MqChannelMode;
import pub.module.mqevent.MqMessageConsumer;

@MqChannel(mode = MqChannelMode.FIRE_AND_FORGET)
public interface UserLoginConsumer extends MqMessageConsumer<UserLoginEvent> {

  void onUserLogin(UserLoginEvent event);
}
```

### 消费方 Handler

```java
@Component
public class UserLoginHandler implements UserLoginConsumer {

  @Override
  public void onUserLogin(UserLoginEvent event) {
    // 处理登录事件
  }
}
```

### 发布事件

```java
@Service
@RequiredArgsConstructor
public class AuthService {

  private final MqPublisher mqPublisher;

  public void login(UserLoginEvent event) {
    // 事务提交后再投递，避免脏读
    mqPublisher.publishAfterCommit(UserLoginConsumer.class, event);
  }
}
```

### Request-Reply 模式

```java
@MqChannel(mode = MqChannelMode.REQUEST_REPLY)
public interface QueryUserConsumer extends MqReplyConsumer<QueryUserCmd> {

  UserDto respond(QueryUserCmd cmd);
}

// 嵌套应答槽位
@MqSubscribe
interface Dating { }
```

调用方通过 `MqRequestReplyClient` 向指定槽位发起请求：

```java
UserDto user = mqRequestReplyClient.request(
    QueryUserConsumer.class,
    QueryUserConsumer.Dating.class,
    cmd,
    UserDto.class,
    5000L);
```

### 命名推导规则

| 元素 | 规则 | 示例 |
|------|------|------|
| destination | `{module}.{subject-kebab}.{action}` | `system.user.login` |
| producerFunction | `{module}{MethodBase}` | `systemUserLogin` |
| consumer group | 槽位包 module 或嵌套类名小写 | `dating` |
| consumer function | `{prefix}{RootConsumerBase}` | `systemUserLogin` |

`@MqChannel` / `@MqSubscribe` 属性显式填写时优先使用显式值。

### Event 配置项

应用需引入 Stream 消费基线（推荐通过 `spring.config.import`）：

```yaml
spring:
  config:
    import: classpath:application-messaging-baseline.yml
```

基线包含 DLQ、重试退避等运维参数，渠道 binding 由框架按契约自动注册。

```yaml
tg:
  messaging:
    consumer:
      default-max-attempts: 3
```

---

## 架构概览

```
┌─────────────┐     @RpcApi 代理      ┌─────────────┐
│  调用方模块   │ ──────────────────► │  提供方模块   │
│  (Client)   │   MQ Request-Reply   │  (Provider) │
└─────────────┘                      └─────────────┘

┌─────────────┐   MqPublisher        ┌─────────────┐
│  生产者模块   │ ──────────────────► │  消费者模块   │
│             │   Spring Cloud Stream │  (Handler)  │
└─────────────┘                      └─────────────┘
```

## 构建

```bash
mvn clean install
```

## 发布到 Maven Central

项目已配置 `central-publishing-maven-plugin`、`flatten-maven-plugin`、sources/javadoc 与 GPG 签名（`release` profile）。

### 1. 生成 Sonatype User Token

1. 登录 [central.sonatype.com](https://central.sonatype.com/)
2. 进入 **Account** → **Generate User Token**
3. 记录 Token 的 **Username** 与 **Password**（不是网站登录密码）

### 2. 执行发布

```powershell
$env:SONATYPE_USERNAME = "<token-username>"
$env:SONATYPE_PASSWORD = "<token-password>"
.\scripts\publish-central.ps1
```

GPG 公钥已上传至 `keys.openpgp.org`（Key ID: `944B0799D96DE669`）。

---

## 答疑

本节汇总使用 `@RpcApi` 时常见的机制与疑问。

### 没有实现类，为什么能注入接口？

与 OpenFeign 类似：**契约是接口，运行时由框架注册 JDK 动态代理**，不要求 classpath 里一定有 `*Impl`。

```
@ApiXxxService + @RpcApi
       ↓
ModuleApiClientRegistrar 扫描注册 Bean 定义
       ↓
ModuleApiProxyCreator 按需创建 JDK 动态代理
       ↓
方法调用 → 本地 Provider 或 MQ Request-Reply
```

- **消费方**（只依赖 `*-api`）：只有接口即可，`@Autowired ApiXxxService` 会自动注入代理。
- **提供方**（`*-biz`）：需有 `ApiXxxServiceImpl` 实现接口并注册为 Spring Bean，框架在启动完成后将其登记为 Provider。

### 没有实现类会报错吗？

| 时机 | 行为 |
|------|------|
| **启动时** | 不会。所有 `@RpcApi` 契约都会注册为 `moduleApiProxy.*` Bean。 |
| **调用时（本机有 Impl）** | 走 **LOCAL** 直调，不发 MQ。 |
| **调用时（本机无 Impl，远端有消费者）** | 走 **MQ**，正常返回。 |
| **调用时（本机无 Impl，远端也无消费者 / 超时）** | **调用时报错**（超时等），与 Feign 连不上服务类似。 |

### 单体应用里还会走 MQ 吗？

默认 **AUTO 路由**：本 JVM 已注册对应 Provider 时优先本地直调，不经过 RabbitMQ。

典型场景：tg-boot 单体 runner 内所有 `*-biz` 同进程部署，跨模块调用多为 **LOCAL**；仅当调用方进程内没有该契约的 Provider 时才会发 MQ（例如拆成多实例、多服务部署后）。

### 对方有多个实例，每个都会响应吗？

**不会。** 这是 MQ **竞争消费（Competing Consumers）** 模型，一次 RPC 只由一个实例处理并回复一条结果。

路由按 **module**（包路径推导），不按实例：

```
pub.module.system.api.service.ApiSysUserService  →  module = system
                                              →  队列 api.rpc.system
```

若 `system` 模块部署了 3 个实例，三者都监听**同一队列** `api.rpc.system`。RabbitMQ 将每条消息投递给**其中一个**消费者：

```
                    ┌─ 实例 A ─┐
请求 ──► api.rpc.system ─┼─ 实例 B ─┼── 仅一个实例消费并 reply
                    └─ 实例 C ─┘
```

因此：

- 不会每个实例都执行一遍业务逻辑；
- 调用方只收到**一个** `RpcResult`；
- 多实例起到**负载均衡**作用，而非广播。

**注意**：Provider 应尽量无状态或依赖共享存储；多消费者不保证全局消息顺序；参与 Saga 的补偿方法需保证**幂等**。

### 和 Feign 的对比

| | OpenFeign | `@RpcApi` |
|---|-----------|-----------|
| 契约 | `@FeignClient` 接口 | `@RpcApi` 接口 |
| 代理 | Feign 动态代理 | JDK 动态代理 |
| 消费方是否需要实现类 | 否 | 否 |
| 提供方 | 远端 HTTP 服务 | `*Impl` + MQ 监听 |
| 传输 | HTTP | MQ Request-Reply |
| 路由 | `name` / `url` | 包路径 → `api.rpc.{module}` |
| 无提供方时 | 启动 OK，调用失败 | 启动 OK，调用失败 |

### 修改本仓库后，业务项目为何「重启不生效」？

`spring-boot-starter-mq-rpc` 以 Maven 依赖形式打入业务 fat jar。仅 IDE 热重启往往仍使用旧的本地仓库 JAR。

正确流程：

```bash
# 1. 安装到本地仓库
mvn -f mq-rpc-event/pom.xml clean install -DskipTests

# 2. 重新打包业务应用（如 tg-boot runner）
mvn -f tg-boot/pom.xml -pl spring-boot-starter-module/spring-boot-starter-runner -am clean package -DskipTests
```

再用新打的 jar 或 Reload Maven 后启动。管理页 `/pub/tg-rpc-tx` 若 404，多半是自动配置未加载或仍在使用旧依赖，按上述流程完整构建后再试。

### 事务管理页访问不了？

管理页路径固定为 `/pub/tg-rpc-tx`（API：`/pub/tg-rpc-tx/api`），需同时满足：

1. classpath 存在 `@RpcApi` 契约（触发 `ModuleRpcAutoConfiguration`）；
2. `tg.module-call.tx.admin-enabled=true`（默认开启）；
3. 业务侧 Security 将 `/pub/**` 放行（tg-boot 默认已配置）。

启动日志中应出现：

```
RPC tx admin page registered at /pub/tg-rpc-tx
RPC tx admin API registered at /pub/tg-rpc-tx/api
```

若无上述日志，检查是否已 `install` 最新版 starter 并完整打包。

## 许可证

[Apache License 2.0](LICENSE)
