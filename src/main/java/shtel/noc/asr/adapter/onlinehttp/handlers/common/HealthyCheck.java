package shtel.noc.asr.adapter.onlinehttp.handlers.common;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import shtel.noc.asr.adapter.onlinehttp.utils.EventBusChannels;
import shtel.noc.asr.adapter.onlinehttp.utils.RedisUtils;

import java.util.Collections;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19 健康检查
 * @annotation redisHealthy 三种状态：working,failed,reconnecting
 */
public class HealthyCheck {
    /**
     * redis正常状态
     */
    public static final String STATUS_WORKING = "working";
    /**
     * redis 异常状态
     */
    public static final String STATUS_FAILED = "failed";
    /**
     * redis重连状态
     */
    public static final String STATUS_RECONNECTING = "reconnecting";
    /**
     * 健康检查状态
     */
    private static boolean healthy = false;
    /**
     * redis状态
     */
    private static String redisHealthy = STATUS_FAILED;
    /**
     * 并发状态
     */
    private static boolean isConcurrency = true;

    private HealthyCheck() {
        throw new IllegalStateException("Utility class");
    }

    public static boolean isHealthy() {
        return healthy;
    }

    private static void setHealthy(boolean healthyStatus) {
        healthy = healthyStatus;
    }

    /**
     * 更新redis状态及健康检查状态
     */
    public static void setRedisHealthy(String redisHealthyStatus) {
        redisHealthy = redisHealthyStatus;
        setHealthy(STATUS_WORKING.equals(redisHealthyStatus));
    }

    public static boolean isConcurrency() {
        return isConcurrency;
    }

    public static void setConcurrencyStatus(boolean concurrencyStatus) {
        HealthyCheck.isConcurrency = concurrencyStatus;
    }

    /***
     * 检查并更新redis状态
     * 状态异常，进行重连
     *
     * @return 状态异常返回future success,异常返回future failed及异常原因
     */
    public static Future<Void> checkHealthy(Vertx vertx) {
        return Future.future(result -> {
            if (healthy) {
                // redis 健康测试
                RedisAPI.api(RedisUtils.getClient()).ping(Collections.singletonList("Healthy check"), send -> {
                    if (send.succeeded()) {
                        healthy = true;
                        //log.debug("Healthy check success!"); 屏蔽健康检查成功的日志
                        result.complete();
                    } else {
                        healthy = false;
                        result.fail(send.cause());
                    }
                });
            } else {
                // 尝试重新初始化连接，再次创建redis连接
                if (!STATUS_RECONNECTING.equals(redisHealthy)) {
                    vertx.eventBus().request(EventBusChannels.REDIS_CONNECT.name(), new JsonObject(), r -> {
                    });
                }
                result.fail("Redis failed!");
            }
        });

    }
}
