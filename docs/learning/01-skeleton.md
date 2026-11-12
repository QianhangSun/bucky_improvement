# 第 01 课 · 项目骨架

## 这一课的目标
搭一个能启动的空 Spring Boot 4 应用，先不碰 AI。下一课只需加一个依赖、写几行配置就能调用大模型；这一课是在为那一步做准备。

## 文件一览
```
bucky_improvement/
├── pom.xml                                  依赖和版本
├── .mvn/
│   ├── maven.config                         让 mvn 自动使用下面这份 settings
│   └── settings.xml                         本项目专用的仓库和代理配置
├── src/main/java/com/bucky/
│   ├── BuckyApplication.java                入口
│   └── common/HealthController.java         /api/ping 自检接口
└── src/main/resources/application.yml       应用配置
```

## 概念 1：BOM，为什么依赖不用写版本号
Spring AI 有几十个模块：模型适配（`spring-ai-anthropic`、`spring-ai-openai`…）、向量库、MCP、各种 starter。它们之间的版本必须一致，否则运行时会出现 `NoSuchMethodError` 这类问题。

BOM（Bill of Materials）就是一张“版本清单”。在 `pom.xml` 里：

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>org.springframework.ai</groupId>
      <artifactId>spring-ai-bom</artifactId>
      <version>${spring-ai.version}</version>   <!-- 2.0.2 -->
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

这段**本身不引入任何 jar**，只是告诉 Maven：“以后遇到 spring-ai-* 依赖，按这张清单定版本。”所以下一课加依赖时只写 `groupId` 和 `artifactId` 就行。

`spring-boot-starter-parent` 也是同一个道理，只是它管的是 Spring Boot 自己的依赖，以及 Jackson、Tomcat 这类第三方库。

**版本对应关系**：Spring AI 2.0.x 是基于 Spring Boot 4.x 构建的（2.0.2 用的是 4.1.2），所以两者要配套。Spring AI 1.x 对应 Spring Boot 3.x，网上很多教程还是 1.x 的写法，看的时候注意区分。

## 概念 2：starter 和自动配置
`spring-boot-starter-web` 是一个 **starter**：它自己几乎没有代码，作用是把一组依赖打包带进来（Spring MVC、内嵌 Tomcat、Jackson）。

真正让它“开箱即用”的是**自动配置**：`@SpringBootApplication` 里包含 `@EnableAutoConfiguration`，启动时 Spring Boot 会检查 classpath 上有哪些类，满足条件就自动创建对应的 Bean。比如发现了 Tomcat，就启动一个监听 8080 端口的 Web 服务器。

下一课会看到同样的机制：加入 `spring-ai-starter-model-anthropic` 后，Spring AI 的自动配置读取 `spring.ai.anthropic.*` 配置，自动创建一个 `ChatModel` Bean，你直接注入使用即可。**理解了这一点，Spring AI 一大半的“魔法”就不神秘了。**

## 概念 3：虚拟线程
`application.yml` 里开了：
```yaml
spring.threads.virtual.enabled: true
```
调用大模型的一次请求常常要几秒到几十秒，大部分时间都在等网络。传统线程池（Tomcat 默认 200 个线程）里，每个等待中的请求都占着一个线程。虚拟线程是 JVM 管理的轻量线程，等待时几乎不占资源。打开这个开关后，每个 HTTP 请求都跑在虚拟线程上，代码写法完全不变。

## 这次踩到的坑：Maven 下载失败
你的全局 `~/.m2/settings.xml` 配置了公司内网镜像，在家连不上；而 Maven 也不会自动使用系统代理。所以项目里加了 `.mvn/settings.xml`：
- `mirror`：把所有下载请求都指向 Maven Central
- `proxies`：经由本机 `127.0.0.1:17891` 代理

`.mvn/maven.config` 里的 `--settings=.mvn/settings.xml` 让你在这个项目里直接敲 `mvn` 就生效，不影响其他项目。如果之后换了网络环境（比如在公司，不需要代理），把 `proxies` 那段删掉即可。

## 运行
```bash
mvn spring-boot:run
# 另开一个终端
curl localhost:8080/api/ping
# {"status":"ok","time":"...","javaVersion":"21.0.6"}
```
我已经跑过一次：启动耗时 0.5 秒，接口返回正常。

## 课后练习
1. 打开 `~/.m2/repository/org/springframework/ai/spring-ai-bom/2.0.2/spring-ai-bom-2.0.2.pom`，搜一下 `anthropic`，看看清单里有哪几个和 Anthropic 相关的模块。
2. 启动时加上 `--debug`（`mvn spring-boot:run -Dspring-boot.run.arguments=--debug`），在日志里找 `CONDITIONS EVALUATION REPORT`。这就是自动配置的“决策记录”：哪些配置生效了（Positive matches）、哪些没生效以及原因。下一课加入 Spring AI 后，再看一次，对比多了什么。
3. 思考：如果不开虚拟线程，100 个用户同时问导师问题、每个回答要 20 秒，Tomcat 会怎样？

## 下一课预告
第 02 课：加入 `spring-ai-starter-model-anthropic`，配置模型名和 API Key，注入 `ChatModel`，写一个 `/api/mentor/hello` 接口，完成第一次大模型调用。
