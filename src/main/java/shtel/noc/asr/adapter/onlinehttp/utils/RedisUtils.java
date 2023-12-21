package shtel.noc.asr.adapter.onlinehttp.utils;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.*;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.HealthyCheck;

import java.nio.channels.ClosedChannelException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation redis相关配置
 */
@Slf4j
public class RedisUtils extends AbstractVerticle {

    /**
     * 最大重连次数
     */
    private static final int MAX_RECONNECT_RETRIES = 10;
    /**
     * 同步锁最大重试次数
     */
    private static final int RETRY_TIMES = 100;
    /**
     * 同步锁重试固定间隔基数
     */
    private static final int RETRY_DELAY = 50;
    /**
     * 同步锁重试随机时间范围
     */
    private static final int RETRY_DELAY_SCOPE = 25;
    /**
     * 锁过期时间单位 EX:秒 PX:毫秒
     */
    private static final String TIMEOUT_UNIT = "PX";
    /**
     * 超时时长
     */
    private static final int EXPIRE_TIME = 950;
    /**
     * 哨兵模式主从切换关键字
     */
    private static final String SWITCH_MASTER = "SWITCH-MASTER Received +switch-master message from Redis Sentinel.";
    /**
     * redis连接客户端,连接的redis客户端
     */
    private static RedisConnection client;

    public static RedisConnection getClient() {
        return client;
    }

    private static void setClient(RedisConnection client) {
        RedisUtils.client = client;
    }

    /**
     * 连接字串（地址端口用户名密码）
     */
    private String connectString;
    /**
     * 连接池大小
     */
    private int maxPoolSize;
    /**
     * 最大等待数量
     */
    private int maxWaitingHandlers;
    /**
     * 哨兵1连接字串
     */
    private String sentinel1;
    /**
     * 哨兵2连接字串
     */
    private String sentinel2;
    /**
     * 哨兵3连接字串
     */
    private String sentinel3;
    /**
     * redis密码
     */
    private String password;
    /**
     * redis 配置
     */
    private RedisOptions options;
    /**
     * 是否单台redis
     */
    private boolean isStandAlone;


    @Override
    public void start(Promise<Void> startPromise) throws Exception {
        //将外部配置文件的参数赋值给各个参数
        vertx.eventBus().<JsonObject>consumer(EventBusChannels.SET_REDIS_OPTIONS.name()).handler(this::setRedisOptions);
        vertx.eventBus().<String>consumer(EventBusChannels.REDIS_CONNECT.name()).handler(this::initRedis);
        vertx.eventBus().<JsonObject>consumer(EventBusChannels.GET_DISTRIBUTED_LOCK.name()).handler(this::getDistributedLock);
        vertx.eventBus().<JsonObject>consumer(EventBusChannels.RELEASE_DISTRIBUTED_LOCK.name()).handler(this::releaseDistributedLock);
        log.info("start redis setup!!!!!!");
        vertx.eventBus().request(EventBusChannels.SET_REDIS_OPTIONS.name(), config().getJsonObject("redis"), r -> {
            if (r.succeeded()) {
                startPromise.complete();
            } else {
                startPromise.fail("Redis init failed!");
            }
        });

    }

    /**
     * 释放分布式锁
     *
     * @param task eventbus message 包括：锁，请求编号
     */

    public void releaseDistributedLock(Message<JsonObject> task) {
        String lockKey = task.body().getString("lock");
        String requestId = task.body().getString("requestId");
        String script =
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
        RedisAPI.api(client).eval(Arrays.asList(script, "1", lockKey, requestId), h -> {
            if (h.succeeded() && h.result().toInteger() == 1) {
                task.reply("Release dLock success");
            } else {
                task.fail(0, "Release dLock failed");
            }
        });
    }


    /***
     * 获取分布式锁 键值有过期时间
     * @param task   eventbus message:锁，请求编号，锁过期时间单位（可选，默认秒），锁过期时间（默认1）
     */
    public void getDistributedLock(Message<JsonObject> task) {
        //EX:秒 PX:毫秒
        String timeUnit;
        //锁过期时间
        int expireTime;
        String lockKey = task.body().getString("lock");
        String requestId = task.body().getString("requestId");
        if (task.body().containsKey("timeUnit")) {
            timeUnit = task.body().getString("timeUnit");
        } else {
            timeUnit = TIMEOUT_UNIT;
        }
        if (task.body().containsKey("expireTime")) {
            expireTime = task.body().getInteger("expireTime");
        } else {
            expireTime = EXPIRE_TIME;
        }

        tryGetDistributedLock(lockKey, requestId, timeUnit, expireTime, rs -> {
            if (Boolean.TRUE.equals(rs)) {
                task.reply("Get dLock success");
            } else {
                // 获取失败，进入等待自旋，尝试N次后失败丢弃。
                waitLock(task, timeUnit, expireTime, lockKey, requestId);
            }
        });
    }

    /***
     * 自旋等待获取锁
     * @param task eventbus message
     * @param timeUnit 过期时间单位
     * @param expireTime 过期时间
     * @param lockKey 锁名
     * @param requestId 锁标识
     */
    private void waitLock(Message<JsonObject> task, String timeUnit, int expireTime, String lockKey, String requestId) {
        AtomicInteger waitTimes = new AtomicInteger(1);
        vertx.setPeriodic((long) new SecureRandom().nextInt(RETRY_DELAY_SCOPE) + RETRY_DELAY, ph ->
                tryGetDistributedLock(lockKey, requestId, timeUnit, expireTime, rs -> {
                    if (Boolean.TRUE.equals(rs)) {
                        vertx.cancelTimer(ph);
                        task.reply("Get dLock success");
                    } else {
                        if (waitTimes.addAndGet(1) > RETRY_TIMES) {
                            vertx.cancelTimer(ph);
                            task.fail(0, "Get dLock failed");
                        }
                    }
                })
        );
    }


    /***
     * 获取分布式锁，只有键不存在时，才能够执行成功
     * @param lockKey 锁名称
     * @param requestId 锁标识
     * @param timeUnit 过期时间单位
     * @param expireTime 过期时间
     */
    private void tryGetDistributedLock(String lockKey, String requestId,
                                       String timeUnit, int expireTime, Handler<Boolean> handler) {

        List<String> list = Arrays.asList(lockKey, requestId, timeUnit, String.valueOf(expireTime), "NX");
        RedisAPI.api(client).set(list, h -> handler.handle(h.result() != null));

    }



    /***
     *  初始化redis,这里没有用到发送的message，用的是已经赋值的变量
     * @param message redis连接配置
     *    本地测试单redis配置
     *         options.setConnectionString(connectString)
     *                 .setMaxPoolSize(maxPoolSize)
     *                 .setMaxWaitingHandlers(maxWaitingHandlers);
     */
    private void initRedis(Message<String> message) {
        options = new RedisOptions()
                .setMaxPoolSize(maxPoolSize)
                .setMaxPoolWaiting(maxWaitingHandlers)
                .setMaxWaitingHandlers(maxWaitingHandlers);

        if (isStandAlone){
            //单例模式
            options
                    .setType(RedisClientType.STANDALONE)
                    .setConnectionString(connectString);
            if (password.length()>0){
                options.setPassword(password);
            }
        }else {
            // 哨兵模式配置
            options
                    .setType(RedisClientType.SENTINEL)
                    .addConnectionString(sentinel1)
                    .addConnectionString(sentinel2)
                    .addConnectionString(sentinel3)
                    .setRole(RedisRole.MASTER);
        }
        //进行redis连接
        createRedisClient(onCreate -> {
            if (onCreate.succeeded()) {
                // connected to redis!
                log.info("Redis connected!");
                message.reply(true);
            } else {
                message.reply(false);
                throw new RuntimeException("Redis connecting failed!", onCreate.cause());
            }
        });
    }

    /**
     * 创建redis连接及设置错误处理，并且设置重连机制
     */
    private void createRedisClient(Handler<AsyncResult<RedisConnection>> handler) {
        Redis.createClient(vertx, options)
                .connect(onConnect -> {
                    if (onConnect.succeeded()) {
                        setClient(onConnect.result());
                        //NOTE 现网需去掉下一行的注释 redis passwd  //这里是哨兵模式
                        if (password.length()>0 && !isStandAlone) {
                            //Collections.singletonList只能存放一个元素，多放或者少放都会导致报错
                            RedisAPI.api(client).auth(Collections.singletonList(password), ar -> {
                            });
                        }

                        HealthyCheck.setRedisHealthy("working");
                        client.exceptionHandler(e -> {
                            if (e instanceof ClosedChannelException || e.getMessage().equals(SWITCH_MASTER)) {
                                HealthyCheck.setRedisHealthy("reconnecting");
                                attemptReconnect(0);
                            } else {
                                log.warn("Redis encounters an error", e);
                            }
                        });
                    } else {
                        log.warn(onConnect.cause().getMessage());
                    }
                    // allow further processing
                    handler.handle(onConnect);
                });
    }
    //尝试重连redis
    private void attemptReconnect(int retry) {
        if (retry > MAX_RECONNECT_RETRIES) {
            log.warn("Redis connect failed ,stop!");
            HealthyCheck.setRedisHealthy("failed");
            throw new RuntimeException("Redis connection lost!");
        } else {
            // retry with backoff up to 10240 ms
            long backoff = (long) (Math.pow(2, retry) * 10);
            vertx.setTimer(backoff, timer -> createRedisClient(onReconnect -> {
                if (onReconnect.failed()) {
                    log.warn("Redis connection retry:" + retry);
                    attemptReconnect(retry + 1);
                }
            }));
        }
    }

    /***
     *  redis配置变更，将修改的外部配置文件赋值给变量
     * @param message connectString，maxPoolSize，maxWaitingHandlers配置信息
     */
    private void setRedisOptions(Message<JsonObject> message) {

        String newConnectString = message.body().getString(ConfigurationKeys.CONNECT_STRING.name());
        int newMaxPoolSize = message.body().getInteger(ConfigurationKeys.MAX_POOL_SIZE.name());
        int newMaxWaitingHandlers = message.body().getInteger(ConfigurationKeys.MAX_WAITING_HANDLERS.name());
        String newPassword = message.body().getString(ConfigurationKeys.PASSWORD.name());
        String newSentinel1 = message.body().getString(ConfigurationKeys.SENTINEL1.name());
        String newSentinel2 = message.body().getString(ConfigurationKeys.SENTINEL2.name());
        String newSentinel3 = message.body().getString(ConfigurationKeys.SENTINEL3.name());
        boolean newIsStandAlone = message.body().getBoolean(ConfigurationKeys.STANDALONE.name());
        // 若是配置文件有更改，则内部进行通信，将修改过后的message发送给REDIS_CONNECT，再次进行连接，如果不涉及变更就回复reply
        if (!newConnectString.equals(this.connectString)
                || this.maxPoolSize != newMaxPoolSize
                || this.maxWaitingHandlers != newMaxWaitingHandlers
                || !this.password.equals(newPassword)
                || !this.sentinel1.equals(newSentinel1)
                || !this.sentinel2.equals(newSentinel2)
                || !this.sentinel3.equals(newSentinel3)
                || this.isStandAlone!= newIsStandAlone
        ) {

            this.connectString = newConnectString;
            this.maxPoolSize = newMaxPoolSize;
            this.maxWaitingHandlers = newMaxWaitingHandlers;
            this.password = newPassword;
            this.sentinel1 = newSentinel1;
            this.sentinel2 = newSentinel2;
            this.sentinel3 = newSentinel3;
            this.isStandAlone = newIsStandAlone;
            vertx.eventBus().request(EventBusChannels.REDIS_CONNECT.name(), message.body(),
                    r -> {
                        if (r.succeeded()) {
                            message.reply(true);
                        } else {
                            log.warn("Redis connect failed!", r.cause());
                            message.fail(-1, "Redis connect failed!");
                        }
                    }
            );
        } else {
            message.reply(true);
        }
    }
}
