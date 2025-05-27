# Sentinel Dashboard 增强版  
**支持Nacos规则持久化 & 配置文件多用户认证**  

基于Sentinel官方Dashboard的增强版本，提供规则持久化和多环境配置支持

## 项目简介  
本项目在原生Sentinel Dashboard基础上增加了以下功能：  
1. **Nacos规则持久化**：限流规则自动同步到Nacos，服务重启不丢失  
2. **配置文件多用户认证**：通过`application-{profile}.properties`配置用户凭证  
3. **多环境支持**：内置开发(dev)、测试(test)、生产(prod)三套配置  

> 项目Git仓库：https://github.com/linbaiyu/sentinel-dashboard-with-nacos.git

---

## 核心改进说明  

### 1. Nacos规则持久化  
- 所有限流规则自动存储到Nacos  
- 支持集群环境下规则实时同步  
- 变更立即生效，无需手动导入  

### 2. 配置文件多用户认证  
- 通过`application.properties`配置用户（明文或加密密码）  
- 支持动态添加/删除用户（需重启生效）  

### 3. 多环境配置支持  
- 预置三套环境配置：  
  - `application-prod.properties`（生产环境）  
  - `application-test.properties`（测试环境）  
  - `application-release.properties`（开发环境）  
- 通过JVM参数`-Dspring.profiles.active={profile}`指定  

---

## 快速开始  

#### 1. 基础配置（application.properties）  
```properties
# Nacos服务器配置（不同环境不共用）
nacos.config.server-addr=127.0.0.1:8848
nacos.config.namespace=sentinel
nacos.config.group=DEFAULT_GROUP

# 用户认证配置（示例）
auth.more.map.admin=admin123
auth.more.map.user1=test123
