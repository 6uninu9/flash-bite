# Flash-Bite 本地原生 Nginx

本目录用于开发阶段的本机反向代理：`客户端 -> Nginx:80 -> smart-server:8080`。不使用 Docker、不连接公网服务器，也不需要将未完成的业务服务部署到服务器。

`nginx.conf` 是完整的原生 Nginx 主配置；`proxy-common.conf` 是它引用的公共反向代理配置。将两个文件复制到本机 Nginx 安装目录的 `conf` 下后即可运行。

## 前置条件

1. 已在本机安装 Windows Nginx，并记其安装目录为 `<NGINX_HOME>`，例如 `C:\tools\nginx`。
2. `smart-server` 已在本机启动并监听 `127.0.0.1:8080`。
3. 本机 80 端口未被 IIS、Docker Desktop 或其他程序占用。若必须改端口，只修改 `nginx.conf` 中的 `listen 80`，访问时同步带上新端口。

## 配置与启动

在 PowerShell 中执行以下命令，并将 `<NGINX_HOME>` 替换为实际目录：

```powershell
$nginxHome = '<NGINX_HOME>'
Copy-Item "$nginxHome\conf\nginx.conf" "$nginxHome\conf\nginx.conf.before-flash-bite" -ErrorAction Stop
Copy-Item '.\deploy\nginx\nginx.conf' "$nginxHome\conf\nginx.conf" -Force
Copy-Item '.\deploy\nginx\proxy-common.conf' "$nginxHome\conf\proxy-common.conf" -Force
& "$nginxHome\nginx.exe" -t -p "$nginxHome\" -c conf\nginx.conf
& "$nginxHome\nginx.exe" -p "$nginxHome\" -c conf\nginx.conf
```

配置改动后先校验再重载：

```powershell
& "$nginxHome\nginx.exe" -t -p "$nginxHome\" -c conf\nginx.conf
& "$nginxHome\nginx.exe" -s reload -p "$nginxHome\" -c conf\nginx.conf
```

停止 Nginx：

```powershell
& "$nginxHome\nginx.exe" -s quit -p "$nginxHome\" -c conf\nginx.conf
```

恢复启动前配置：先停止 Nginx，将 `nginx.conf.before-flash-bite` 还原为 `nginx.conf`，再按启动命令重新启动。

## 规则说明

| 范围 | 规则 | 目的 |
| --- | --- | --- |
| 全部路径 | 单 IP 最多 20 个并发连接 | 限制单来源长期占满连接 |
| `POST /user/user/login` | 5 请求/分钟，`burst=5`，超限返回 429 | 限制登录接口突发刷取 |
| `POST /user/coupon/seckill` | 10 请求/秒，`burst=20`，超限返回 429 | 降低领券接口瞬时冲击 |
| `POST /user/order/submit` | 5 请求/秒，`burst=10`，超限返回 429 | 降低下单接口瞬时冲击 |
| 全部路径 | 透传 `Authorization`、`authentication`、`token` 及转发地址 Header | 保持服务端 JWT 鉴权和审计上下文 |

Nginx 不解析 JWT、不创建用户身份，也不替代库存、一人限领等业务正确性校验。上述规则是基础 HTTP 层限流和并发控制，不是 DDoS 或完整连接风暴防护。

## 可复现测试步骤

1. 启动依赖与 `smart-server`，确认 `http://127.0.0.1:8080/doc.html` 可访问。
2. 执行 `nginx.exe -t`，预期输出 `syntax is ok` 和 `test is successful`。
3. 启动 Nginx 后请求 `http://127.0.0.1/user/category/list?type=1`，预期得到与直连 `8080` 一致的业务响应。
4. 使用真实、未过期且未使用的微信登录 `code` 调用 `POST /user/user/login`；预期 Nginx 返回的响应与直连 server 一致，并获得 JWT。没有有效 code 时，不得将登录失败误判为代理故障。
5. 使用上一步 JWT 调用一个受保护查询接口，预期服务端鉴权通过；伪造 `X-User-Id` 而不携带 JWT，预期 HTTP 401。
6. 在一分钟内连续快速请求登录接口超过初始令牌与突发额度，预期其中部分请求为 HTTP 429；测试完成后等待一个完整限流窗口，避免影响后续人工登录。
