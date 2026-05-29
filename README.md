# 12306-Agent-Java: 企业级智能出行智能体架构实战 (v1.0)

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-Alibaba-blue.svg)](https://github.com/alibaba/spring-ai-alibaba)
[![Vue](https://img.shields.io/badge/Vue-3.x-green.svg)](https://vuejs.org/)

本工程是一款基于真实大高并发微服务底座（马丁12306 票务系统）构建的**企业级 Java 智能出行 Agent 架构系统**。底层基于 Java Spring Cloud 微服务基座，AI 大脑层全面拥抱 `Spring AI Alibaba` 生态，深度集成大模型 Function Calling 机制，完整打通了从用户自然语言意图识别、智能多步规划，到分布式微服务接口安全调用的全链路闭环。

---
## 🛠️ 技术栈 (Tech Stack)

| 层面 | 技术组件 |
| :--- | :--- |
| **语言** | Java 17、Python (预留多语言架构占位) |
| **核心框架** | Spring Boot 3.x、Spring Cloud 2023.x、Spring AI Alibaba |
| **AI 引擎** | 阿里云 DashScope (qwen-max)、Function Calling 机制 |
| **数据库** | MySQL + ShardingSphere 分库分表 |
| **分布式缓存** | Redis |
| **消息队列** | RocketMQ |
| **注册/配置中心** | Nacos |
| **前端大屏** | Vue 3 (console-vue) |
| **第三方支付** | 支付宝沙箱环境 |

## 💻 快速启动指南 (Quick Start)
### 1.马丁12306
https://github.com/nageoffer/12306
### 2. 准备
```yaml
# 阿里云通义千问大模型密钥
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
# 数据库初始化
先运行resources下的db文件夹下的sql,再运行data下的
# yml配置文件
Mysql,redis,nacos的密码配置
# 后台服务启动 
nacos: startup.cmd -m standalone
Rocketmq
       NameServer:start mqnamesrv.cmd
       Broker:start mqbroker.cmd -n localhost:9876
# 前端启动
进入到 12306/console-vue 目录
node.js
1.安装yarn
npm install -g yarn
2.下载依赖
yarn install
3.启动
yarn serve
```
### 3. 模块启动顺序
由于采用标准的 Spring Cloud 微服务架构，请按以下顺序启动基础设施及服务：
基础设施：启动 MySQL, Redis, RocketMQ, Nacos 控制台。
底层支撑：启动 gateway-service。
核心业务：启动 user-service, ticket-service, order-service。
AI 大脑：启动 agent-service。
前端交互：进入 console-vue 目录执行命令，体验大模型驱动的购票全链路。

