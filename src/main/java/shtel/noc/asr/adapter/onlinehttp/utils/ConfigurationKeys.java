package shtel.noc.asr.adapter.onlinehttp.utils;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation  定义配置文件key
 */
public enum ConfigurationKeys {
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
