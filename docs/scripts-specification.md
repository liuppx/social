# Scripts Specification

本文档说明 `scripts/` 目录下各脚本和模板文件的用途、参数、依赖和返回值约定。

## 脚本总览

| 文件 | 用途 |
| --- | --- |
| `scripts/package.sh` | 从 `main` 分支创建版本标签，构建前后端产物，并生成发布包。 |
| `scripts/starter.sh` | 启动、停止或重启发布目录中的后端 Java 服务。 |
| `scripts/health-check.sh` | 检查服务进程、HTTP readiness 和依赖检查状态。 |
| `scripts/test.sh` | 执行单元测试或基于健康检查的 smoke 测试。 |
| `scripts/config_backup.sh` | 将当前版本的 `config/` 目录和 Nginx 配置加密备份到 `/opt/backup`。 |
| `scripts/upgrade.sh` | 通过 `copy-config` 阶段将当前版本的 `config/` 目录复制到目标版本目录，并执行其他升级钩子。 |
| `scripts/backup.conf.template` | `config_backup.sh` 的备份开关、文件名前缀和后缀配置模板。 |
| `scripts/.passphrase-file.template` | `config_backup.sh` 使用的 GPG 对称加密口令文件模板。 |

## `package.sh`

用于生成发布包。脚本会同步 `main` 分支，按语义化版本标签生成或复用版本号，构建后端 JAR 和前端静态资源，并输出 `output/<project>-vX.Y.Z-<commit>.tar.gz`。

### 用法

```bash
./scripts/package.sh [v<major>.<minor>.<patch>]
```

### 参数

- 可选参数：已有 Git 标签，格式必须为 `v<major>.<minor>.<patch>`。
- 不传参数时：脚本会基于最新语义化版本标签自动递增 patch 版本；如果 `main` 当前提交已经有最新标签，则跳过打包。

### 主要行为

- 要求工作区干净，否则退出。
- 拉取远端分支和标签，并同步 `main`。
- 执行前端 `npm install` 和 `npm run build`，前提是存在 `web/` 目录。
- 执行后端 `mvn clean package -DskipTests`。
- 将各模块 JAR 复制到发布包 `bin/` 目录。
- 将各模块资源配置复制到发布包 `config/<module>/` 目录。
- 将 `starter.sh`、`health-check.sh`、`test.sh` 复制到发布包 `scripts/` 目录。
- 复制 `tests/` 目录，并生成 `config/runtime.env.template`。

### 返回值

- `0`：打包成功，或当前 `main` 已经发布且无需重复打包。
- 非 `0`：参数错误、依赖命令缺失、工作区不干净、构建失败或必要产物缺失。

## `starter.sh`

用于管理发布目录中的后端 Java 服务，包括 `platform`、`server`、`rtc`、`web3-identity`。

### 用法

```bash
./scripts/starter.sh [start|stop|restart]
```

### 参数

- `start`：启动所有可用服务，默认动作。
- `stop`：停止所有服务。
- `restart`：先停止再启动所有服务。

### 主要行为

- 从 `config/runtime.env` 读取运行时环境变量，文件不存在时跳过。
- 默认使用 `java`，可通过 `JAVA_BIN` 覆盖。
- 可通过 `JAVA_OPTS` 配置 JVM 参数。
- 服务日志写入 `logs/<service>.log`。
- 服务 PID 写入 `pids/<service>.pid`。
- 如果存在 `config/<service>/`，启动时追加 Spring Boot 外部配置路径。

### 返回值

- `0`：指定动作执行成功。
- 非 `0`：参数错误、Java 不存在、服务启动失败或没有可启动的 JAR。

## `health-check.sh`

用于检查当前发布目录下服务的运行状态，支持文本和 JSON 输出，可用于部署验证或自动化探活。

### 用法

```bash
./scripts/health-check.sh [options]
```

### 参数

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `--level <liveness|readiness|dependency|all>` | 检查级别。 | `readiness` |
| `--timeout <seconds>` | 单次 HTTP 检查超时时间。 | `10` |
| `--retries <count>` | 失败后的重试次数。 | `0` |
| `--interval <seconds>` | 重试间隔秒数。 | `2` |
| `--format <text|json>` | 输出格式。 | `text` |
| `--quiet` | 文本模式下只输出最终结果。 | 关闭 |
| `--help` | 显示帮助。 | - |

### 环境变量

- `HEALTH_TIMEOUT`：默认 HTTP 超时时间。
- `HEALTH_RETRIES`：默认重试次数。
- `HEALTH_INTERVAL`：默认重试间隔。
- `HEALTH_FORMAT`：默认输出格式。
- `HEALTH_PLATFORM_URL`：`platform` readiness URL，默认 `http://127.0.0.1:8888/`。
- `HEALTH_RTC_URL`：`rtc` readiness URL，默认 `http://127.0.0.1:8890/`。
- `HEALTH_IDENTITY_URL`：`web3-identity` readiness URL，默认 `http://127.0.0.1:8901/`。

### 返回值

- `0`：所有检查通过。
- 非 `0`：参数错误或至少一个检查失败。

## `test.sh`

用于执行项目测试。默认执行 Maven 单元测试，也支持基于 `health-check.sh` 的 smoke 测试。

### 用法

```bash
./scripts/test.sh [options]
```

### 参数

| 参数 | 说明 | 默认值 |
| --- | --- | --- |
| `--suite <unit|smoke>` | 测试套件。 | `unit` |
| `--timeout <seconds>` | smoke 测试整体超时时间。 | `120` |
| `--base-url <url>` | 平台基础 URL。当前脚本保留该参数。 | `http://127.0.0.1:8888` |
| `--format <text|json>` | 输出格式。 | `text` |
| `--help` | 显示帮助。 | - |

### 主要行为

- `unit`：要求源码目录存在 `pom.xml` 且本机安装 Maven，执行 `mvn test`。
- `smoke`：调用 `health-check.sh --level readiness` 检查服务进程和 HTTP readiness。

### 返回值

- `0`：测试通过。
- 非 `0`：参数错误、依赖缺失或测试失败。

## `config_backup.sh`

用于备份当前版本配置。脚本会读取 `/data/<module>/backup.conf`，将当前模块目录下的 `config/` 目录和 `/etc/nginx/conf.d/social.conf` 打包，并使用 GPG 对称加密输出到 `/opt/backup`。

### 用法

```bash
./scripts/config_backup.sh
```

### 配置文件

- `/data/<module>/backup.conf`：由 `scripts/backup.conf.template` 复制并按需修改。
- `/data/<module>/.passphrase-file`：由 `scripts/.passphrase-file.template` 复制，并将内容替换为 GPG 加密口令。

### 主要行为

- 根据当前目录名识别模块名和版本包名。
- 读取 `BACKUP_CONF_FLAG` 判断是否启用备份。
- 检查 `config/` 目录、Nginx 配置和口令文件是否存在。
- 将备份内容临时复制到 `/tmp/<module>-conf/`。
- 生成加密备份文件：`/opt/backup/<prefix><module-basename><suffix>`。
- 如果目标备份文件已存在，脚本退出并返回 `255`，避免覆盖已有备份。

### 返回值

- `0`：备份成功，或 `BACKUP_CONF_FLAG=False` 时跳过备份。
- `255`：目标备份文件已存在。
- 非 `0`：配置缺失、口令文件缺失、源文件缺失或备份失败。

## `upgrade.sh` 的 `copy-config` 阶段

用于升级流程中复制配置目录。脚本会将当前模块目录下与 `scripts/` 同级的 `config/` 目录复制到目标版本目录下，并替换目标版本目录中的 `config/`。

### 用法

```bash
bash scripts/upgrade.sh copy-config <current-dir> <target-directory-full-path>
```

### 参数

- 必填参数：目标版本目录的全路径，必须是绝对路径且目录已存在。

### 主要行为

- 校验参数数量。
- 校验目标目录是绝对路径并且存在。
- 校验当前版本 `config/` 目录存在。
- 删除目标目录下已有的 `config/`。
- 将当前版本 `config/` 复制到目标目录下。
- 不做备份。

### 返回值

- `0`：复制成功。
- `1`：参数错误、目标目录不存在、源配置目录不存在、删除或复制失败。

## `backup.conf.template`

`config_backup.sh` 的配置模板。使用前复制为 `/data/<module>/backup.conf`。

### 配置项

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| `BACKUP_CONF_FLAG` | 是否启用配置备份，只允许 `True` 或 `False`。 | `True` |
| `BACKUP_CONF_PREFIX` | 备份文件名前缀，可为空。 | 空字符串 |
| `BACKUP_CONF_SUFFIX` | 备份文件名后缀。 | `.conf.tar.gz.gpg` |

## `.passphrase-file.template`

`config_backup.sh` 的 GPG 对称加密口令模板。使用前复制为 `/data/<module>/.passphrase-file`，并将模板内容替换为实际加密口令。

该文件应按敏感配置管理，避免提交实际口令到代码仓库。
