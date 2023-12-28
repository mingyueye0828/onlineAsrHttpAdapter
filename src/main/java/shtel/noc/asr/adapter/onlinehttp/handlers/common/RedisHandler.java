package shtel.noc.asr.adapter.onlinehttp.handlers.common;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.CallStatus;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.ResultReceived;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.VoiceSeg;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.RedisException;
import shtel.noc.asr.adapter.onlinehttp.utils.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static shtel.noc.asr.adapter.onlinehttp.handlers.service.QueryASROnlineHttpInterfaceHandler.buildResponse;

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
     *
     * @param redisKey 需要查询的appId_engineId的列表
     * @return 从redis中查询得到的list
     */
    public static Future<Map<String, Integer>> mGetMaxConcurrency(String[] redisKey) {
        Promise<Map<String, Integer>> promise = Promise.promise();
        //获取多个键的值[xxx，xxxx,xxx]
        RedisAPI.api(RedisUtils.getClient()).mget(Arrays.asList(redisKey))
                .onSuccess(rs -> {
                    Map<String, Integer> concurrencyLimits = new HashMap<>();
                    String resultStr = rs.toString().substring(1, rs.toString().length() - 1);
                    //分割结果并解析出各个app的engine的并发上限
                    for (String value : resultStr.split(", ")) {
                        JsonObject resultJson = new JsonObject(value);
                        String appId = resultJson.getString("app_id");
                        String engineId = resultJson.getString("engine_id");
                        Integer concurrencyLimit = resultJson.getInteger("max_concurrency");
//                        log.info("app_id: {}, engine_id: {}, max_concurrency :{}", appId, engineId, concurrencyLimit);
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


    /**
     * 接收到新通话接入，主要记录redis中的引擎连接情况
     *
     * @param voiceSeg 接收到的第一个请求内容
     * @return 返回初始化的callStatus
     */
    public static Future<CallStatus> checkAndCreateCallStatus(VoiceSeg voiceSeg) {
        Promise<CallStatus> promise = Promise.promise();
        log.info("Begin create CallEngineStatus in Redis");
        String uid = voiceSeg.getUid();
        String appId = voiceSeg.getAppId();

        //先看看有没有 有的话获取旧的duration作为offset
        RedisHandler.getDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid)
                .onSuccess(rss ->
                        //这里就算获取失败，也是返回的空，走的还是success
                        RedisAPI.api(RedisUtils.getClient()).get(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid)
                                .onSuccess(rs -> {
                                    //用于记录与引擎连接的信息
                                    CallStatus initCallStatus = new CallStatus(voiceSeg);
                                    initCallStatus.setReqId(uid);
                                    initCallStatus.setAppId(appId);
                                    // 这里设置了callStatus的信息，直接覆盖掉，这里可能比较久，所以不主动释放，等待过期释放
                                    RedisHandler.setCallStatus(uid, initCallStatus, false)
                                            .onFailure(rrf -> promise.fail(new RedisException("Init callStatus failed, uid " + uid)))
                                            .onSuccess(rrs -> promise.complete(initCallStatus));
                                })
                                .onFailure(rf -> {
                                    log.warn("Check call status failed! uid is {}", uid);
                                    promise.fail(new RedisException("Check call status failed! uid " + uid));
                                })
                )
                .onFailure(rf -> promise.fail(new RedisException("get lock failed! uid " + uid)));
        return promise.future();
    }


    /**
     * 将callStatus写入Redis
     *
     * @param callStatus      call信息
     * @param needReleaseLock 是否需要解callStatus锁？目前仅当初始化callStatus时不需要解锁 300s时间
     * @return 返回 已完成
     */
    public static Future<Void> setCallStatus(String uid, CallStatus callStatus, boolean needReleaseLock) {
        Promise<Void> promise = Promise.promise();

        RedisAPI.api(RedisUtils.getClient())
                .set(Arrays.asList(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid, JsonObject.mapFrom(callStatus).encodePrettily(),
                        "EX", Constants.DEFAULT_KEY_EXPIRE_SEC), rr -> {

                    if (needReleaseLock) {
                        vertx.setTimer(100L, tl ->//// NOTE: 2021/6/3 这里延迟100ms解锁，给出时间传输
                                RedisHandler.releaseDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid)
                        );
                    }
                    if (rr.succeeded()) {
                        promise.complete();
                    } else {
                        promise.fail(new RedisException("Set call status Failed!", rr.cause()));
                    }
                });
        return promise.future();
    }


    /***
     * 用于获取callStatus
     * @param uid uid
     * @return 从redis中查询得到的callStatus
     */
    public static Future<CallStatus> getCallStatus(String uid) {
        Promise<CallStatus> promise = Promise.promise();
        RedisHandler.getDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid)
                .onSuccess(rs ->
                        RedisAPI.api(RedisUtils.getClient()).get(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid, rr -> {
                            if (rr.succeeded() && rr.result() != null) {
                                try {
                                    CallStatus callStatus = new JsonObject(rr.result().toString()).mapTo(CallStatus.class);
                                    promise.complete(callStatus);
                                } catch (Exception e) {
                                    log.warn("parse call status failed! the result is {} |||", rr.result(), e);
                                    promise.fail("parse call status failed! the result is |||" + rr.result());
                                }
                            } else {
                                log.warn("Get call status Failed!");
                                promise.fail(CodeMappingEnum.REDIS_GET_CALL_RECORDE_FAILURE.toJson().toString());
                            }
                        })
                )
                .onFailure(rf -> log.warn("get lock failed for get callStatus! uid is {}", uid));
        return promise.future();
    }


    /**
     * 直接set就行，根据score来 提取出来的时候也是按照score排序的,
     * 也加一个过期时间（这里可以在SessionController进行删除），也可以直接取走，就将对应成员删除呀
     * 将ReceiverResult写入Redis，先上锁，在解锁
     *  key value score
     * @param resultReceived      接收字段信息
     * @param needReleaseLock 是否需要解锁
     * @return 返回 已完成
     */
    private static final String SET_RESULT_SCRIPT = "local result = redis.call('zadd', KEYS[1], ARGV[2] ,ARGV[1])";

    public static Future<Void> setReceiverResult(String uid, JsonObject requestBody, boolean needReleaseLock) {
        Promise<Void> promise = Promise.promise();

        RedisHandler.getDistributionLock(Constants.ASRONLINE_RECEIVERRESULT_PREFIX + uid + "_LOCK", uid)
                .onSuccess(rss ->
                        RedisAPI.api(RedisUtils.getClient()).eval(Arrays.asList(
                                SET_RESULT_SCRIPT,
                                "1",
                                Constants.ASRONLINE_RECEIVERRESULT_PREFIX + uid,
                                requestBody.encode(),
                                TimeStamp.currentTimeStamp()
                        )).onSuccess(
                                rr -> {
                                    log.debug("Insert Redis success");
                                    if (needReleaseLock) {
                                        RedisHandler.releaseDistributionLock(Constants.ASRONLINE_RECEIVERRESULT_PREFIX + uid + "_LOCK", uid);
                                        log.debug("Get result from redis has release lock");
                                    }
                                })
                        .onFailure(rf ->{
                            log.error("Result set into Redis failed! uid is {}", uid);
                        })
                )
                .onFailure(rf -> promise.fail(new RedisException("Get insert result lock failed! uid " + uid)));
        return promise.future();
    }


    /**
     * 将集合里面的所有元素取出来，之后组成一个集合，发送给客户端，并将集合中的元素删除
     * 将ReceiverResult写入Redis，先上锁，在解锁
     *  key value score,获取的值按照从高到低排序
     * @param resultReceived  接收字段信息
     * @param needReleaseLock 是否需要解锁
     * @return 返回 已完成
     */

    private static final String GET_RESULT_SCRIPT = "local result = redis.call('zrange', KEYS[1], 0 , -1) if(next(result)~=nil) then redis.call('zremrangebyrank', KEYS[1], 0, -1) return result else return 0 end";

    public static Future<String[]> getReceiverResult(String uid, boolean needReleaseLock) {
        Promise<String[]> promise = Promise.promise();
        RedisHandler.getDistributionLock(Constants.ASRONLINE_RECEIVERRESULT_PREFIX + uid + "_LOCK", uid)
                .onSuccess(rss ->
                        RedisAPI.api(RedisUtils.getClient()).eval(Arrays.asList(
                                GET_RESULT_SCRIPT,
                                "1",
                                Constants.ASRONLINE_RECEIVERRESULT_PREFIX + uid
                        )).onSuccess(
                                result -> {
                                    // 返回是一个数组 List<String>
                                    int resultLen = result.toString().length();
                                    // 进行判断是否有返回
                                    if (resultLen < 3) {
                                        log.debug("Redis get 0！！！！！！！！！！！");
                                        promise.complete(new String[0]);
                                        // 若有返回，去掉首位，并将其分隔成String[]
                                    } else {
                                        String[] resultList = result.toString().substring(1, resultLen - 1).split(", ");
                                        log.info("app session {}, results {} has been get", uid, resultList);
                                        promise.complete(resultList);
                                    }
                                    if (needReleaseLock) {
                                        vertx.setTimer(100L, tl ->//// NOTE: 2021/6/3 这里延迟100ms解锁，给出时间传输
                                                RedisHandler.releaseDistributionLock(Constants.ASRONLINE_RECEIVERRESULT_PREFIX + uid + "_LOCK", uid)
                                        );
                                    }


                                })
                                .onFailure(rf ->{
                                    log.error("Result get from Redis failed! uid is {}", uid);
                                    promise.fail("Result get from Redis failed! uid is" + uid);
                                })
                )
                .onFailure(rf -> promise.fail(new RedisException("Get from result lock failed! uid " + uid)));
        return promise.future();
    }








    /**
     * 释放分布式锁
     *
     * @param requestId 锁编号
     * @return future成功失败信息
     */
    public static Future<Void> releaseDistributionLock(String lock, String requestId) {
        JsonObject data = new JsonObject()
                .put("lock", lock)
                .put("requestId", requestId);
        return Future.future(result -> vertx.eventBus().request(
                EventBusChannels.RELEASE_DISTRIBUTED_LOCK.name(), data, rs -> {
                    if (rs.succeeded()) {
                        result.complete();
                    } else {
                        log.warn("Release distributed lock failed! Release distributed lock requestId:{}", requestId);
                        result.fail(new RedisException("Release distributed lock failed!", rs.cause()));
                    }
                }));
    }


    /**
     * 获取分布式锁
     *
     * @param requestId 锁编号,实际上还是uid
     * @return future成功失败信息
     */
    public static Future<Void> getDistributionLock(String lock, String requestId) {
        return getDistributionLock(lock, requestId, 0);
    }

    public static Future<Void> getDistributionLock(String lock, String requestId, int expireTime) {
        Promise<Void> promise = Promise.promise();
        JsonObject data = new JsonObject()
                .put("lock", lock)
                .put("requestId", requestId);
        if (expireTime > 0) {
            data.put("expireTime", expireTime);
        }
        vertx.eventBus().request(
                EventBusChannels.GET_DISTRIBUTED_LOCK.name(), data)
                .onSuccess(rs ->
                        promise.complete())
                .onFailure(rf -> {
                    log.warn("Get distributed lock failed! Get distributed lock requestId:{}", requestId);
                    promise.fail(new RedisException("Get distributed lock failed!", rf.getCause()));
                });
        return promise.future();
    }


}

