/*
 * Copyright 1999-2018 Alibaba Group Holding Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.csp.sentinel.dashboard.controller.v2;

import com.alibaba.csp.sentinel.dashboard.auth.AuthAction;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService;
import com.alibaba.csp.sentinel.dashboard.auth.AuthService.PrivilegeType;
import com.alibaba.csp.sentinel.dashboard.datasource.entity.rule.FlowRuleEntity;
import com.alibaba.csp.sentinel.dashboard.domain.Result;
import com.alibaba.csp.sentinel.dashboard.repository.rule.InMemoryRuleRepositoryAdapter;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRuleProvider;
import com.alibaba.csp.sentinel.dashboard.rule.DynamicRulePublisher;
import com.alibaba.csp.sentinel.util.StringUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Flow rule controller (v2).
 *
 * @author Eric Zhao
 * @since 1.4.0
 */
@RestController
@RequestMapping(value = "/v2/flow")
public class FlowControllerV2 {

    private final Logger logger = LoggerFactory.getLogger(FlowControllerV2.class);

    @Autowired
    private InMemoryRuleRepositoryAdapter<FlowRuleEntity> repository;
    @Resource
    private AuthService<HttpServletRequest> authService;
    @Autowired
    @Qualifier("flowRuleDefaultProvider")
    private DynamicRuleProvider<List<FlowRuleEntity>> ruleProvider;
    @Autowired
    @Qualifier("flowRuleDefaultPublisher")
    private DynamicRulePublisher<List<FlowRuleEntity>> rulePublisher;

    //本地锁，用于保证，同一时间只有一个请求可以操作
    private Map<String, Long> localLockMap = new HashMap<>();

    @GetMapping("/rules")
    @AuthAction(PrivilegeType.READ_RULE)
    public Result<List<FlowRuleEntity>> apiQueryMachineRules(@RequestParam String app) {

        if (StringUtil.isEmpty(app)) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        try {
            List<FlowRuleEntity> rules = ruleProvider.getRules(app);
            rules = getFlowRuleEntities(app, rules);
            return Result.ofSuccess(rules);
        } catch (Throwable throwable) {
            logger.error("Error when querying flow rules", throwable);
            return Result.ofThrowable(-1, throwable);
        }
    }

    private <R> Result<R> checkEntityInternal(FlowRuleEntity entity) {
        if (entity == null) {
            return Result.ofFail(-1, "invalid body");
        }
        if (StringUtil.isBlank(entity.getApp())) {
            return Result.ofFail(-1, "app can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getLimitApp())) {
            return Result.ofFail(-1, "limitApp can't be null or empty");
        }
        if (StringUtil.isBlank(entity.getResource())) {
            return Result.ofFail(-1, "resource can't be null or empty");
        }
        if (entity.getGrade() == null) {
            return Result.ofFail(-1, "grade can't be null");
        }
        if (entity.getGrade() != 0 && entity.getGrade() != 1) {
            return Result.ofFail(-1, "grade must be 0 or 1, but " + entity.getGrade() + " got");
        }
        if (entity.getCount() == null || entity.getCount() < 0) {
            return Result.ofFail(-1, "count should be at lease zero");
        }
        if (entity.getStrategy() == null) {
            return Result.ofFail(-1, "strategy can't be null");
        }
        if (entity.getStrategy() != 0 && StringUtil.isBlank(entity.getRefResource())) {
            return Result.ofFail(-1, "refResource can't be null or empty when strategy!=0");
        }
        if (entity.getControlBehavior() == null) {
            return Result.ofFail(-1, "controlBehavior can't be null");
        }
        int controlBehavior = entity.getControlBehavior();
        if (controlBehavior == 1 && entity.getWarmUpPeriodSec() == null) {
            return Result.ofFail(-1, "warmUpPeriodSec can't be null when controlBehavior==1");
        }
        if (controlBehavior == 2 && entity.getMaxQueueingTimeMs() == null) {
            return Result.ofFail(-1, "maxQueueingTimeMs can't be null when controlBehavior==2");
        }
        if (entity.isClusterMode() && entity.getClusterConfig() == null) {
            return Result.ofFail(-1, "cluster config should be valid");
        }
        return null;
    }

    @PostMapping("/rule")
    @AuthAction(value = AuthService.PrivilegeType.WRITE_RULE)
    public Result<FlowRuleEntity> apiAddFlowRule(HttpServletRequest request, @RequestBody FlowRuleEntity entity) {
        AuthService.AuthUser authUser = authService.getAuthUser(request);

        Result<FlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }
        entity.setId(null);
        Date date = new Date();
        entity.setGmtCreate(date);
        entity.setGmtModified(date);
        entity.setLimitApp(entity.getLimitApp().trim());
        entity.setResource(entity.getResource().trim());
        try {
            if (localLockMap.get(entity.getApp()) != null || localLockMap.get(entity.getApp()) < System.currentTimeMillis() - (10 * 1000)) {
                return Result.ofFail(5000001, "该项目正被修改，请稍后重试");
            }
            try {
                localLockMap.put(entity.getApp(), System.currentTimeMillis());
                entity = repository.save(entity);
                publishRules(entity.getApp(), authUser.getLoginName(), "增加限流");
                logger.info("time:{},username:{},operate: 新增限流配置:{}", LocalDateTime.now(), authUser.getLoginName(), JSON.toJSONString(entity));
            } finally {
                localLockMap.remove(entity.getApp());
            }
        } catch (Throwable throwable) {
            logger.error("Failed to add flow rule：{}userName:{}", throwable, authUser.getLoginName());
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(entity);
    }

    @PutMapping("/rule/{id}")
    @AuthAction(AuthService.PrivilegeType.WRITE_RULE)
    public Result<FlowRuleEntity> apiUpdateFlowRule(HttpServletRequest request,
                                                    @PathVariable("id") Long id,
                                                    @RequestBody FlowRuleEntity entity) {
        AuthService.AuthUser authUser = authService.getAuthUser(request);
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        FlowRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofFail(-1, "id " + id + " does not exist");
        }
        if (entity == null) {
            return Result.ofFail(-1, "invalid body");
        }

        entity.setApp(oldEntity.getApp());
        entity.setIp(oldEntity.getIp());
        entity.setPort(oldEntity.getPort());
        Result<FlowRuleEntity> checkResult = checkEntityInternal(entity);
        if (checkResult != null) {
            return checkResult;
        }

        entity.setId(id);
        Date date = new Date();
        entity.setGmtCreate(oldEntity.getGmtCreate());
        entity.setGmtModified(date);
        try {
            if (localLockMap.get(oldEntity.getApp()) != null || localLockMap.get(oldEntity.getApp()) < System.currentTimeMillis() - (10 * 1000)) {
                return Result.ofFail(5000001, "该项目正被修改，请稍后重试");
            }
            try {
                localLockMap.put(oldEntity.getApp(), System.currentTimeMillis());
                entity = repository.save(entity);
                if (entity == null) {
                    return Result.ofFail(-1, "save entity fail");
                }
                publishRules(oldEntity.getApp(), authUser.getLoginName(), "更新限流");
                logger.info("time:{},username:{},operate: 更新限流配置，原限流配置:{}￥￥￥￥￥￥￥￥￥￥新限流配置:{}", LocalDateTime.now(), authUser.getLoginName(), JSON.toJSONString(oldEntity), JSON.toJSONString(entity));
            } finally {
                localLockMap.remove(oldEntity.getApp());
            }
        } catch (Throwable throwable) {
            logger.error("Failed to update flow rule", throwable);
            return Result.ofThrowable(-1, throwable);
        }
        return Result.ofSuccess(entity);
    }

    @DeleteMapping("/rule/{id}")
    @AuthAction(PrivilegeType.DELETE_RULE)
    public Result<Long> apiDeleteRule(HttpServletRequest request,
                                      @PathVariable("id") Long id) {
        if (id == null || id <= 0) {
            return Result.ofFail(-1, "Invalid id");
        }
        FlowRuleEntity oldEntity = repository.findById(id);
        if (oldEntity == null) {
            return Result.ofSuccess(null);
        }
        AuthService.AuthUser authUser = authService.getAuthUser(request);


        try {
            if (localLockMap.get(oldEntity.getApp()) != null || localLockMap.get(oldEntity.getApp()) < System.currentTimeMillis() - (10 * 1000)) {
                return Result.ofFail(5000001, "该项目正被修改，请稍后重试");
            }
            try {
                localLockMap.put(oldEntity.getApp(), System.currentTimeMillis());
                repository.delete(id);
                publishRules(oldEntity.getApp(), authUser.getLoginName(), "删除限流");
                logger.info("time:{},username:{},operate: 删除限流配置:{}", LocalDateTime.now(), authUser.getLoginName(), oldEntity.getResource());
            } finally {
                localLockMap.remove(oldEntity.getApp());
            }
        } catch (Exception e) {
            return Result.ofFail(-1, e.getMessage());
        }
        return Result.ofSuccess(id);
    }

    private void publishRules(String app, String userName, String operate) throws Exception {
        List<FlowRuleEntity> rules = repository.findAllByApp(app);
        if (CollectionUtils.isEmpty(rules)) {
            rules = ruleProvider.getRules(app);
            rules = getFlowRuleEntities(app, rules);
        }
        if (CollectionUtils.isNotEmpty(rules)) {
            logger.info("time:{},username:{},operate:{},rules:{}", LocalDateTime.now(), userName, operate, rules);
            rulePublisher.publish(app, rules);
        } else {
            logger.info("发布时，已有rules为空，time:{},username:{},operate:{},rules:{}", LocalDateTime.now(), userName, operate, rules.toString());
        }
    }

    private List<FlowRuleEntity> getFlowRuleEntities(String app, List<FlowRuleEntity> rules) {
        if (rules != null && !rules.isEmpty()) {
            for (FlowRuleEntity entity : rules) {
                entity.setApp(app);
                if (entity.getClusterConfig() != null && entity.getClusterConfig().getFlowId() != null) {
                    entity.setId(entity.getClusterConfig().getFlowId());
                }
            }
        }
        rules = repository.saveAll(rules);
        return rules;
    }
}
