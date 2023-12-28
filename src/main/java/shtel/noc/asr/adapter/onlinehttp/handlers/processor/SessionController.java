package shtel.noc.asr.adapter.onlinehttp.handlers.processor;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.ConcurrencyException;
import shtel.noc.asr.adapter.onlinehttp.utils.CodeMappingEnum;
import shtel.noc.asr.adapter.onlinehttp.utils.Constants;
import shtel.noc.asr.adapter.onlinehttp.utils.RedisUtils;
import shtel.noc.asr.adapter.onlinehttp.utils.TimeStamp;

import java.util.Arrays;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 并发控制，提供初始化，增加，减少并发
 */
@Slf4j
public class SessionController {
    /**
     * 增减app许可lua脚本。操作成功则返回当前并发数，因并发已满或值超出并发范围则返回-1，运行失败则报错
     */
    private static final String APP_INCR_SCRIPT = "local concurrency = redis.call('get' , KEYS[1]) local isIn = redis.call('zscore',KEYS[2],ARGV[2]) if tonumber(concurrency) < tonumber(ARGV[1]) and isIn==false then redis.call('zadd',KEYS[2],ARGV[3],ARGV[2]) return redis.call('incr', KEYS[1]) else if isIn~=false then return concurrency else return -1 end end";
    private static final String APP_DECR_SCRIPT = "if redis.call('get' , KEYS[1]) > '0' and redis.call('zscore',KEYS[2],ARGV[1]) then redis.call('zrem',KEYS[2],ARGV[1]) return redis.call('decr', KEYS[1]) else return -1 end";

    /**
     * 删除超时许可的lua脚本，并减去相应的并发
     */
    private static final String POP_EXPIRED_SCRIPT = "local remList = redis.call('zrangebyscore', KEYS[1],0,ARGV[1]) if (next(remList)==nil) then return 0 else local remLen = tonumber(redis.call('zcount',KEYS[1],0,ARGV[1])) local realConcurrency = tonumber(redis.call('zcount',KEYS[1],0,99999999999999999)) redis.call('zremrangebyscore',KEYS[1],0,ARGV[1]) redis.call('set',KEYS[2],realConcurrency-remLen) return remList end";

    /**
     * 用于检查uid是否超app的并发限制
     *
     * @param keyPostFix 需要查询的key 包含了是app并发的信息 appId_engineId
     * @param uid    uid
     * @return 抢成功则success 否则为failure
     */
    public static Future<String> checkAndIncrAppSession(String keyPostFix, String uid) {
        Promise<String> promise = Promise.promise();
        log.info("start2add uid: " + uid + " to app license!");
        log.info("11111 {}---{}", ConfigStore.getAppLimitMap(), keyPostFix);

        /**初始化并发，判断是否小于设置的redis键值，以及redis中是否存在键值存在的时间，没有则并发增加1，
         * 并且插入键值:key为CONCURRENCY_ASRONLINE_APP_LICENSE+ConfigStore.getTestAffix()
         * score为TimeStamp.currentTimeStamp())
         * value：uid
         * 若存在score，则已经添加过了，否则添加失败
         * */
        RedisAPI.api(RedisUtils.getClient()).eval(Arrays.asList(APP_INCR_SCRIPT, "2",
                Constants.CONCURRENCY_APP_PREFIX + keyPostFix,
                Constants.CONCURRENCY_APP_LICENSE_PREFIX + keyPostFix,
                ConfigStore.getAppLimitMap().get(keyPostFix).toString(),
                uid,
                TimeStamp.currentTimeStamp())
        )
                .onSuccess(rs -> {
                    log.info("uid {}, check if in the list, result is {}", uid, rs);
                    if ("-1".equals(rs.toString())) {
                        //没有加成功，已经存在这个值了
                        log.warn("APP reaches its limit! callId is " + uid);
                        promise.fail(CodeMappingEnum.ENGINE_CONCURRENCY_FULL.getTransCode());
                    } else {
                        //已经存在了，
                        log.info("metricsLog asrOnlineHttpSessionCount {} appId {} incr and add to APP license done!",
                                rs, keyPostFix.split("_")[0]);
                        promise.complete("add uid " + uid + " to app license done!");
                    }
                })
                .onFailure(rf -> {
                    log.warn("check redis failed! uid is " + uid);
                    promise.fail(CodeMappingEnum.REDIS_COMMUNICATION_ERROR.getTransCode());
                });
        return promise.future();
    }

    /**
     * 减少app并发
     *
     * @param concurrencyKeyPostFix 需要查询的key的后缀 包含了是app并发的信息
     * @param uid                   语音流id
     * @return 操作结果的信息
     */
    public static Future<String> decrAppSession(String concurrencyKeyPostFix, String uid) {
        Promise<String> promise = Promise.promise();
        RedisAPI.api(RedisUtils.getClient()).eval(Arrays.asList(APP_DECR_SCRIPT, "2",
                Constants.CONCURRENCY_APP_PREFIX + concurrencyKeyPostFix,
                Constants.CONCURRENCY_APP_LICENSE_PREFIX + concurrencyKeyPostFix,
                uid))
                .onSuccess(rs -> {
                    log.debug("Decrease concurrency is processing");
                    if (!"-1".equals(rs.toString())) {
                        log.info("metricsLog asrOnlineHttpSessionCount {} appId {} decr and rem from APP license done!",
                                rs, concurrencyKeyPostFix.split("_")[0]);
                    }
                    promise.complete("rem uid " + uid + "to app license done!");
                })
                .onFailure(rf -> {
                    log.warn("decr check app license failed! uid is " + uid);
                    promise.fail(new ConcurrencyException("APP rem license failed! uid is " + uid));
                });
        return promise.future();
    }


    /**
     * 清理过期app license；引擎license过期时间需要比app license过期时间短 因此仅需直接去除app license即可
     * appEngineList包含格式为PUB_ENG_$appId_$engineId的元素  删除过期的license
     */
    public void appSessionCleaner() {
        for (String appEnginePubKey : ConfigStore.getAppEngineList()) {
            String[] appEngineKeys = appEnginePubKey.split("_");
            String enginePostFix = appEngineKeys[appEngineKeys.length - 1];
            String appId = appEngineKeys[appEngineKeys.length - 2];
            String appEnginePostFix = appId + "_" + enginePostFix;
            popExpiredAppSession(appEnginePostFix)
                    .onSuccess(rList -> {
                        for (String callId : rList) {
                            log.warn("app Cleaner! rem callId {} from APP list!!!", callId);
                        }
                    });
        }
    }

    /**
     * 取出所有超时的app的callId以数组形式返回
     *
     * @param appEnginePostFix appId_engineId
     * @return 有过期的返回过期app license的String[]
     */
    public static Future<String[]> popExpiredAppSession(String appEnginePostFix) {
        Promise<String[]> promise = Promise.promise();
        RedisAPI.api(RedisUtils.getClient()).eval(Arrays.asList(
                POP_EXPIRED_SCRIPT,
                "2",
                Constants.CONCURRENCY_APP_LICENSE_PREFIX + appEnginePostFix,
                Constants.CONCURRENCY_APP_PREFIX + appEnginePostFix,
                TimeStamp.beforeTimeStamp(Constants.APP_LICENSE_EXPIRED_TIME)))
                .onSuccess(result -> {
                    int resultLen = result.toString().length();
                    if (resultLen < 3) {
                        promise.fail("no expired App session");
                    } else {
                        String[] resultList = result.toString().substring(1, resultLen - 1).split(", ");
                        log.warn("pop expired app session! app_engine is {}, popped list is {}",
                                appEnginePostFix, resultList);
                        promise.complete(resultList);
                    }
                })
                .onFailure(rf -> {
                    log.warn("pop expired app session failed! app_engine is " + appEnginePostFix);
                    promise.fail("pop expired app session failed! app_engine is " + appEnginePostFix);
                })
        ;
        return promise.future();
    }




}
