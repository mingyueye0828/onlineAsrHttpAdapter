package shtel.noc.asr.adapter.onlinehttp.utils;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation
 */
public enum EventBusChannels {
    //配置文件
    CONFIGURATION_CHANGED,

    //周期任务
    PERIOD_JOB_RUN,

    // redis相关
    SET_REDIS_OPTIONS,
    REDIS_CONNECT,
    GET_DISTRIBUTED_LOCK,
    RELEASE_DISTRIBUTED_LOCK,

}
