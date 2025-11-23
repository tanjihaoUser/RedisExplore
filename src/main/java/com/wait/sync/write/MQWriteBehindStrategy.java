package com.wait.sync.write;

import com.wait.entity.CacheSyncParam;
import com.wait.entity.type.DataOperationType;
import com.wait.entity.type.WriteStrategyType;
import com.wait.service.MQService;
import com.wait.sync.MethodExecutor;
import com.wait.util.AsyncSQLWrapper;
import com.wait.util.BoundUtil;
import com.wait.util.message.AsyncDataMsg;
import com.wait.util.message.CompensationMsg;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * 对于写入量较大，且对一致性要求不高的场景，或者对性能和延时有较高要求，可以采用MQ异步写入的方式，提高写入性能
 * */
@Component
@Slf4j
@RequiredArgsConstructor
public class MQWriteBehindStrategy implements WriteStrategy {

    private final BoundUtil boundUtil;

    @Qualifier("thirdMQService")
    private final MQService mqService;

    private final AsyncSQLWrapper asyncSQLWrapper;

    @Override
    public void write(CacheSyncParam<?> param, MethodExecutor methodExecutor) {
        try {
            // 1. 立即写入缓存
            if (param.getNewValue() != null) {
                boundUtil.writeWithAsyncRetry(param, 3);
                log.debug("MQ Write-Behind: 缓存写入成功, key: {}", param.getKey());
            }

            // 2. 发送消息到MQ（异步更新数据库）
            @SuppressWarnings("unchecked")
            AsyncDataMsg<Object> task = new AsyncDataMsg<>(DataOperationType.UPDATE, (CacheSyncParam<Object>) param);
            mqService.sendMessage(extractEntityType(param), param.getKey(), task);

            log.debug("MQ Write-Behind: 更新消息已发送, key: {}", param.getKey());

        } catch (Exception e) {
            log.error("MQ Write-Behind写入失败, key: {}", param.getKey(), e);
            handleWriteFailure(param, methodExecutor, e);
        }
    }

    @Override
    public void delete(CacheSyncParam<?> param, MethodExecutor methodExecutor) {
        try {
            String key = param.getKey();
            // 1. 立即删除缓存
            boundUtil.del(key);
            log.debug("MQ Write-Behind: 缓存删除成功, key: {}", key);

            // 2. 发送删除消息到MQ
            @SuppressWarnings("unchecked")
            AsyncDataMsg<Object> task = new AsyncDataMsg<>(DataOperationType.DELETE, (CacheSyncParam<Object>) param);
            mqService.sendMessage(extractEntityType(param), key, task);

            log.debug("MQ Write-Behind: 删除消息已发送, key: {}", key);

        } catch (Exception e) {
            log.error("MQ Write-Behind删除失败, key: {}", param.getKey(), e);
            handleWriteFailure(param, methodExecutor, e);
        }
    }

    /**
     * 统一的失败处理
     */
    private void handleWriteFailure(CacheSyncParam<?> param, MethodExecutor methodExecutor, Exception e) {
        try {
            asyncSQLWrapper.executeAspectMethod(param, methodExecutor);
            log.info("降级同步写入成功, key: {}", param.getKey());
        } catch (Exception ex) {
            log.error("同步写入也失败, 保存到死信队列, key: {}", param.getKey(), ex);
            @SuppressWarnings("unchecked")
            CompensationMsg<Object> compensationMsg = new CompensationMsg<>((CacheSyncParam<Object>) param, ex.getMessage(), System.currentTimeMillis());
            mqService.sendDLMessage(param.getKey(), compensationMsg);
        }
    }

    private String extractEntityType(CacheSyncParam<?> param) {
        String type = param.getClazz().toString();
        int beginInd = type.indexOf(".");
        return beginInd == -1 ? "Unknown" : type.substring(beginInd + 1) + "_topic";
    }

    @Override
    public WriteStrategyType getStrategyType() {
        return WriteStrategyType.WRITE_BEHIND_MQ;
    }
}