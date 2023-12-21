package shtel.noc.asr.adapter.onlinehttp.utils;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation  定义配置文件key
 */
public enum ConfigurationKeys {
//******************会话相关配置*****************
    /**
     * 日志级别
     */
    LOG_LEVEL,
    /**
     * appId_engineId 列表，以--作分割
     */
    APPID_ENGINEID_LIST,
    /**
     * 引擎id列表，以分号;作分割。此处的engineId精确到model
     */
    ENGINEID_LIST,
    /**
     * 实时引擎适配地址列表，以分号;作分割
     */
    ASRONLINE_ENGINE_URLS,
    /**
     * 本适配对外的path
     */
    ASR_HTTP_INTERFACE,
    /**
     * 本适配接收识别结果的path
     */
    ASR_RECEIVER_INTERFACE,
    /**
     * 本适配http端口
     */
    ASR_ADAPTER_PORT,
    /**
     * redis键的小标，与其他适配做区分
     */
    TEST_AFFIX,

//******************redis相关配置*****************
    /**
     * redis 连接串 目标redis的地址
     */
    CONNECT_STRING,
    /**
     * redis 连接池大小
     */
    MAX_POOL_SIZE,
    /**
     * redis 最长等待队列
     */
    MAX_WAITING_HANDLERS,
    /**
     * redis 哨兵模式密码
     */
    PASSWORD,
    /**
     * redis sentinel1
     */
    SENTINEL1,
    /**
     * redis sentinel2
     */
    SENTINEL2,
    /**
     * redis sentinel3
     */
    SENTINEL3,
    /**
     * 是否使用单机redis模式? false=哨兵模式
     */
    STANDALONE,

}
