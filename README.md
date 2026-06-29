
###  **Yeying Social** 
![MIT协议](https://img.shields.io/badge/license-MIT-red)
[![star](https://gitee.com/bluexsx/yeying-social/badge/star.svg)](https://gitee.com/bluexsx/yeying-social) 
[![star](https://img.shields.io/github/stars/bluexsx/yeying-social.svg?style=flat&logo=GitHub)](https://github.com/bluexsx/yeying-social) 
<a href="#加入交流群"><img src="https://img.shields.io/badge/QQ交流群-green.svg?style=plasticr"></a>

1. Yeying Social是一个仿微信实现的网页版聊天软件，不依赖任何第三方收费组件。
1. 支持私聊、群聊、离线消息、发送语音、图片、文件、已读未读、群@等功能
1. 支持音视频通话(基于原生webrtc实现,需要ssl证书)
1. uniapp端兼容app、h5、微信小程序,可与web端同时在线，并保持消息同步
1. 后端采用springboot+netty实现，网页端使用vue，移动端使用uniapp
1. 服务器支持集群化部署,具有良好的横向扩展能力


详细文档: https://www.yuque.com/u1475064/mufu2a  
官方论坛: https://bbs.social.yeying.pub
Web3 融合方案: docs/web3-integration-plan.md
AI Native 改造记录: docs/ai-native-plan.md

#### 近期更新
发布3.0版本：

- 后台管理端上线,后台管理代码仓库地址:https://gitee.com/bluexsx/yeying-social-admin
- 框架和组件版本全面升级: jdk17、springboot3.3、node18等
- 部分界面，功能、性能优化

#### 在线体验

web端：https://social.yeying.pub

安卓app：已上架至各大主流手机应用市场以及腾讯应用宝，搜索"Yeying Social",下载安装即可

ios-app: 已上架至app store,搜索"Yeying Social",下载安装即可

小程序: 已上架至微信小程序，搜索“Yeying Social”进入即可

h5: https://social.yeying.pub/h5/

测试账号：张三/Aa123123 李四/Aa123123,也可以自行注册

说明:  
1.**请勿利用测试账号辱骂他人、发布低俗内容，否则将直接对您的IP进行封禁**  
2.由于部分厂商上架审核要求实名制，app端隐藏了"用户名注册"注册通道，可通过长按注册页面蓝色文字标题解除限制   
3.体验环境部署的是商业版本,与开源版本功能存在一定差异，具体请参考:  
https://www.yuque.com/u1475064/imk5n2/qtezcg32q1d0dr29#SbvXq


#### 付费服务
商业版: https://www.yuque.com/u1475064/imk5n2/qtezcg32q1d0dr29

#### 项目结构
| 模块              | 功能                                     |
|-----------------|----------------------------------------|
| platform        | 业务平台服务，处理用户业务请求（HTTP）              |
| server          | 消息推送服务（Netty WebSocket）                |
| rtc             | RTC 信令与通话编排服务                          |
| web3-identity   | Web3 身份认证服务（SIWE/UCAN）                 |
| web3-graph      | Web3 社交图服务（关注/导入导出/同步）               |
| web3-incentive  | Web3 激励服务（积分/激励策略）                   |
| message-connector | 消息推送连接 SDK（服务端使用）                  |
| client          | 协议模型 SDK（对外发布，仅含协议模型）             |
| mq-api          | MQ 抽象接口                               |
| mq-redis        | Redis MQ 实现                           |
| common          | 公共包，后端服务均依赖此包                      |
| web             | web页面                                 |
| uniapp          | uniapp页面,可打包成app、h5、微信小程序          |

#### 消息推送方案
当消息的发送者和接收者连的不是同一个server时，消息是无法直接推送的，所以我们设计出了能够支持跨节点推送的方案。

- 利用了redis的list数据实现消息推送，其中key为im:unread:${serverid},每个key的数据可以看做一个queue,每个server根据自身的id只消费属于自己的queue
- redis记录了每个用户的websocket连接的是哪个server,当用户发送消息时，platform将根据所连接的server的id,决定将消息推向哪个queue


#### 本地启动
1.安装运行环境
- 安装node:v18.19.0
- 安装jdk:17
- 安装maven:3.9.6
- 安装mysql:8.0,账号密码分别为root/root,创建名为yeying_social的数据库（表结构由 Flyway 启动时自动迁移）
  - 迁移脚本位置：common/src/main/resources/db/migration
- 安装redis:6.2
- 安装minio:RELEASE.2024-xx,使用默认账号、密码、端口

2.启动后端服务（数据库迁移归属 platform）
```
mvn clean package
java -jar ./platform/target/platform.jar
java -jar ./server/target/server.jar
java -jar ./rtc/target/rtc.jar
java -jar ./web3-identity/target/web3-identity.jar
# web3-graph / web3-incentive 暂为占位服务，可按需启动
```

3.启动前端web
```
cd web
npm install
npm run serve
```
如需 RTC / Web3 身份服务，请在 `web/.env.development` 中配置 `VUE_APP_RTC_BASE_API` 与 `VUE_APP_WEB3_BASE_API`。
访问 http://localhost:8080

4.启动uniapp-h5
将uniapp目录导入HBuilderX,点击菜单"运行"->"开发环境-h5"
访问 http://localhost:5173


#### 加入交流群
从2026-01-01开始，我们正式开通了企业微信群（原来的QQ群不再开放），欢迎进群与小伙们一起交流， **申请加群前请务必先star哦** 


#### 点下star吧
如果项目对您有帮助，请点亮右上方的star，支持一下作者吧！


#### 说明几点
1. 本系统允许用于商业用途，且不收费，**但切记不要用于任何非法用途** ，本软件作者不会为此承担任何责任
1. 基于本系统二次开发后再次开源的项目，请注明引用出处，以避免引发不必要的误会
1. 为方便管理，要pr的同学请将代码提交到v_3.0.0分支，作者会在功能上线时合并到master分支

