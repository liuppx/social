# Social Production Deployment

This document describes how the current package script builds Social, where production configuration should be placed after packaging, and when changes take effect.

## Package Layout

Run the package script from a clean source checkout:

```bash
./scripts/package.sh
```

The script creates an archive under `output/`:

```text
output/social-vX.Y.Z-<commit>.tar.gz
```

After extracting the archive, the deployment directory contains:

```text
social-vX.Y.Z-<commit>/
  bin/
    platform.jar
    server.jar
    rtc.jar
    web3-identity.jar
  config/
    platform/
      application.yml
      application-dev.yml
      application-test.yml
      application-prod.yml
      logback.xml
    server/
      application.yml
      application-dev.yml
      application-test.yml
      application-prod.yml
      logback.xml
    rtc/
      application.yml
      application-dev.yml
      logback.xml
    web3-identity/
      application.yml
      application-dev.yml
      logback.xml
    runtime.env.template
  web/
    dist/
  scripts/
    starter.sh
    health-check.sh
    test.sh
  logs/
  pids/
  tests/
```

`scripts/starter.sh` starts only the backend Java services. It does not start MySQL, Redis, MinIO, Nginx, or any frontend static file server.

## Backend Configuration Loading

Each service is started with its own external configuration directory:

```bash
java -jar bin/<service>.jar --spring.config.additional-location=file:config/<service>/
```

The mapping is:

```text
platform.jar       -> config/platform/
server.jar         -> config/server/
rtc.jar            -> config/rtc/
web3-identity.jar  -> config/web3-identity/
```

Spring Boot loads the packaged configuration from inside the jar and also loads the external files under `config/<service>/`. External deployment files should be treated as the place for production values.

Configuration changes take effect only after restarting the service:

```bash
./scripts/starter.sh restart
```

## Runtime Environment

The package includes:

```text
config/runtime.env.template
```

Create the real runtime file from the template:

```bash
cp config/runtime.env.template config/runtime.env
```

`scripts/starter.sh` sources `config/runtime.env` when it exists. Use it for Java executable and JVM options shared by all services:

```bash
export JAVA_BIN=java
export JAVA_OPTS="-Xms512m -Xmx1024m -Dspring.profiles.active=prod"
```

`JAVA_OPTS` is placed before `-jar`, so Spring settings in this file should be JVM system properties such as:

```bash
-Dspring.profiles.active=prod
```

Do not put Spring application arguments such as `--spring.profiles.active=prod` in `JAVA_OPTS`; they are not placed in the application-argument position by the startup script.

## Production Profile

The checked-in default `application.yml` files currently set:

```yaml
spring:
  profiles:
    active: dev
```

Production deployments must override this to `prod`. There are two supported approaches.

Approach 1, set it once for all services in `config/runtime.env`:

```bash
export JAVA_OPTS="-Xms512m -Xmx1024m -Dspring.profiles.active=prod"
```

Approach 2, set it in each service external config file:

```yaml
spring:
  profiles:
    active: prod
```

Use one approach consistently. The `runtime.env` approach is convenient for all services; the per-service file approach makes the active profile visible beside each service's configuration.

## Backend Production Values

The following values normally need to be set for production.

For `config/platform/application.yml` and `config/platform/application-prod.yml`:

```yaml
server:
  port: 8888

spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:mysql://<mysql-host>:3306/yeying_social?useSSL=false&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true
    username: <mysql-user>
    password: <mysql-password>
  data:
    redis:
      host: <redis-host>
      port: 6379
      password: <redis-password>

jwt:
  accessToken:
    secret: <production-access-token-secret>
  refreshToken:
    secret: <production-refresh-token-secret>

minio:
  endpoint: http://<minio-internal-host>:9000
  domain: https://<public-domain>/file
  accessKey: <minio-access-key>
  secretKey: <minio-secret-key>
  bucketName: yeying-social

passport:
  node-base-url: https://<passport-node-domain>
  app-id: <passport-app-id>
  callback-url: https://<social-domain>/api/passport/login/callback
  scopes: identity.basic,identity.username,identity.email,identity.wallet
  session-ttl-seconds: 300
```

For `config/server/application.yml` and `config/server/application-prod.yml`:

```yaml
spring:
  profiles:
    active: prod
  data:
    redis:
      host: <redis-host>
      port: 6379
      password: <redis-password>

websocket:
  enable: true
  port: 8878

tcpsocket:
  enable: false
  port: 8879

jwt:
  accessToken:
    secret: <same-as-platform-access-token-secret>
```

For `config/rtc/application.yml`:

```yaml
server:
  port: 8890

spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:mysql://<mysql-host>:3306/yeying_social?useSSL=false&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true
    username: <mysql-user>
    password: <mysql-password>
  data:
    redis:
      host: <redis-host>
      port: 6379
      password: <redis-password>

jwt:
  accessToken:
    secret: <same-as-platform-access-token-secret>
  refreshToken:
    secret: <same-as-platform-refresh-token-secret>

webrtc:
  max-channel: 9
  iceServers:
    - urls: stun:stun.l.google.com:19302
```

For `config/web3-identity/application.yml`:

```yaml
server:
  port: 8901

spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:mysql://<mysql-host>:3306/yeying_social?useSSL=false&useUnicode=true&characterEncoding=utf-8&allowPublicKeyRetrieval=true
    username: <mysql-user>
    password: <mysql-password>
  data:
    redis:
      host: <redis-host>
      port: 6379
      password: <redis-password>

jwt:
  accessToken:
    secret: <same-as-platform-access-token-secret>
  refreshToken:
    secret: <same-as-platform-refresh-token-secret>

web3:
  auth:
    nonceExpireIn: 300
    autoRegister: true
    defaultChainId: 1
    expectedDomain: <social-domain>
  did:
    enabled: false
  ucan:
    audience: did:web:<social-domain>
    resource: profile
    action: read
  passport:
    nodeBaseUrl: https://<passport-node-domain>
    appId: <passport-app-id>
    audience: did:web:<social-domain>
```

Keep JWT secrets consistent across `platform`, `server`, `rtc`, and `web3-identity`; otherwise tokens issued by one service may not be accepted by another.

## Frontend Configuration

Frontend production values are configured before packaging in:

```text
web/.env.production
```

Current production variables include:

```env
VUE_APP_BASE_API = 'https://social.yeying.pub/api'
VUE_APP_RTC_BASE_API = 'https://social.yeying.pub/rtc'
VUE_APP_WEB3_BASE_API = 'https://social.yeying.pub/web3'
VUE_APP_WEB3_AUDIENCE = 'did:web:social.yeying.pub'
VUE_APP_WS_URL = 'wss://social.yeying.pub/im'
```

These values are compiled into `web/dist` during:

```bash
npm run build
```

Changing `web/.env.production` after packaging does not change an already-built `web/dist`. To change frontend API domains or WebSocket URLs, update `web/.env.production` and package again.

## Reverse Proxy

Deploy `web/dist` using Nginx, CDN, object storage, or another static file server. The package startup script does not serve it.

A typical production path mapping is:

```text
/      -> web/dist static files
/api   -> platform:8888
/rtc   -> rtc:8890
/web3  -> web3-identity:8901
/im    -> server WebSocket port 8878
/file  -> MinIO public domain or file serving endpoint
```

The frontend variables and reverse proxy rules must agree. For example, if `VUE_APP_WS_URL` is `wss://social.yeying.pub/im`, the production proxy must support WebSocket upgrade on `/im`.

## Deployment Flow

```bash
# Build release archive from source.
./scripts/package.sh

# On the production host.
tar -xzf social-vX.Y.Z-<commit>.tar.gz
cd social-vX.Y.Z-<commit>

# Create runtime env.
cp config/runtime.env.template config/runtime.env
vim config/runtime.env

# Edit production service configs.
vim config/platform/application.yml
vim config/platform/application-prod.yml
vim config/server/application.yml
vim config/server/application-prod.yml
vim config/rtc/application.yml
vim config/web3-identity/application.yml

# Start services.
./scripts/starter.sh start

# Check readiness.
./scripts/health-check.sh --level readiness --timeout 10 --retries 10 --interval 3

# Optional smoke test.
./scripts/test.sh --suite smoke --timeout 120
```

## Common Problems

If the service starts with development configuration, check `spring.profiles.active`. The default is `dev`, and production must override it.

If the page opens but API requests fail, check `web/.env.production` at build time and the reverse proxy mappings for `/api`, `/rtc`, `/web3`, and `/im`.

If WebSocket login fails, check that `/im` supports WebSocket upgrade and that `server` uses the same JWT access token secret as `platform`.

If passport or wallet login fails in production, check `passport.node-base-url`, `passport.app-id`, `passport.callback-url`, `web3.auth.expectedDomain`, and `web3.ucan.audience`.
