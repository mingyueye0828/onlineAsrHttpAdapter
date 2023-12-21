package shtel.noc.asr.adapter.onlinehttp.handlers.common;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.RedisException;
import shtel.noc.asr.adapter.onlinehttp.utils.RedisUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation redis中相关操作，获取并发
 */
@Slf4j
public class RedisHandler {

    private static Vertx vertx;
    //在主节点中的vertx与之相关联
    public static void init(Vertx vertx) {
        RedisHandler.vertx = vertx;
    }

    /**
     * 用于批量获取键值
     * @param redisKey 需要查询的appId_engineId的列表
     * @return 从redis中查询得到的list
     */
    public static Future<Map<String, Integer>> mGetMaxConcurrency(String[] redisKey) {
        Promise<Map<String, Integer>> promise = Promise.promise();

        //获取多个键的值[xxx，xxxx,xxx]
        RedisAPI.api(RedisUtils.getClient()).mget(Arrays.asList(redisKey))
                .onSuccess(rs -> {
                    Map<String, Integer> concurrencyLimits = new HashMap<>();
                    log.info("获取的键值为：",rs.toString());
                    String resultStr = rs.toString().substring(1, rs.toString().length() - 1);
                    //分割结果并解析出各个app的engine的并发上限
                    for (String value : resultStr.split(", ")) {
                        JsonObject resultJson = new JsonObject(value);
                        String appId = resultJson.getString("app_id");
                        String engineId = resultJson.getString("engine_id");
                        Integer concurrencyLimit = resultJson.getInteger("max_concurrency");
                        //获取失败则设一个极大值，变相取消并发限制
                        if (concurrencyLimit == null) {
                            log.warn("get {} failed!", Arrays.toString(redisKey));
                            concurrencyLimit = 100000;
                        }
                        //appId不是必须的
                        if (appId == null) {
                            concurrencyLimits.put(engineId, concurrencyLimit);
                        } else {
                            concurrencyLimits.put(appId + "_" + engineId, concurrencyLimit);
                        }
                    }
                    promise.complete(concurrencyLimits);
                })
                .onFailure(rf -> promise.fail(new RedisException("Get call status Failed!", rf.getCause())));

        return promise.future();

    }

}

