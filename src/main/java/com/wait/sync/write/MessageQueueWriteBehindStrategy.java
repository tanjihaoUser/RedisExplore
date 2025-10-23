package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.util.message.AsyncDataMsg;
import com.wait.entity.type.DataOperationType;
import com.wait.entity.type.WriteStrategyType;
import com.wait.service.MQServiceImpl;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import com.wait.util.message.CompensationMsg;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.wait.service.MQServiceImpl.TOPIC_SUFFIX;

@Component
@Slf4j
public class MessageQueueWriteBehindStrategy implements WriteStrategy {

    @Autowired
    private BoundUtil boundUtil;

    @Autowired
    private MQServiceImpl mqService;

    @Autowired
    private AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public void write(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        try {
            // 1. 立即写入缓存
            if (param.getNewValue() != null) {
                boundUtil.cacheResult(param);
                log.debug("MQ Write-Behind: 缓存写入成功, all params: {}", param);
            }

            // 2. 发送消息到MQ（异步更新数据库）
            AsyncDataMsg task = new AsyncDataMsg(DataOperationType.UPDATE, param);

            mqService.sendMessage(extractEntityType(param), param.getKey(), task);

            log.debug("MQ Write-Behind: 更新消息已发送, all params: {}", param);

        } catch (Exception e) {
            log.error("MQ Write-Behind写入失败, params: {}", param, e);
            // 降级为同步写入
            try {
                asyncSQLWrapper.executeAspectMethod(param, joinPoint);
            } catch (Exception ex) {
                log.error("MQ Write-Behind写入失败，同步写入也失败, 保存到死信队列, params: {}", param, ex);
                CompensationMsg compensationMsg = new CompensationMsg(param, ex.getMessage(), System.currentTimeMillis());
                mqService.sendDLMessage(param.getKey(), compensationMsg);
            }
        }
    }

    @Override
    public void delete(CacheSyncParam param, ProceedingJoinPoint joinPoint) {
        String key = param.getKey();
        try {
            // 1. 立即删除缓存
            boundUtil.del(key);
            log.debug("MQ Write-Behind: 缓存删除成功, key: {}", key);

            // 2. 发送删除消息到MQ
            AsyncDataMsg<Object> task = new AsyncDataMsg<>(DataOperationType.DELETE, param);

            mqService.sendMessage(extractEntityType(param), key, task);

            log.debug("MQ Write-Behind: 删除消息已发送, key: {}", key);

        } catch (Exception e) {
            log.error("MQ Write-Behind删除失败, key: {}", param, e);
            // 降级为同步写入
            try {
                asyncSQLWrapper.executeAspectMethod(param, joinPoint);
            } catch (Exception ex) {
                log.error("MQ Write-Behind写入失败，同步写入也失败, 保存到死信队列, params: {}", param, ex);
                CompensationMsg compensationMsg = new CompensationMsg(param, ex.getMessage(), System.currentTimeMillis());
                mqService.sendDLMessage(param.getKey(), compensationMsg);
            }
        }
    }

    /**
     * 从param中提取实体类型
     */
    private String extractEntityType(CacheSyncParam param) {
        String type = param.getClazz().toString();
        int beginInd = type.indexOf(".");
        return beginInd == -1 ? "Unknown" : type.substring(beginInd + 1) + TOPIC_SUFFIX;
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.WRITE_BEHIND;
    }
}