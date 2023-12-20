package shtel.noc.asr.adapter.onlinehttp.utils;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation  一些常用的变量
 */
public class Constants {

    public static final String LOG_NAME="shtel.noc.asr.adapter.onlinehttp";


    public static final long PERIOD_TIME_CACHE = 200;
    /**
     * 健康检查周期
     * 包含过期清理
     */
    public static final long HEALTH_CHECK_PERIOD = 5000;
    /**
     * 从redis中同步配置，默认10秒
     */
    public static final long CONFIG_UPDATE_PERIOD = 10000;

    public static final String MRCP_PREFIX = "MRCP_";

    public static final String CONCURRENCY_ENGINE_PREFIX = "CONCURRENCY_ASRONLINE_ENGINE" + ConfigStore.getTestAffix();
    public static final String CONCURRENCY_APP_PREFIX = "CONCURRENCY_ASRONLINE_APP" + ConfigStore.getTestAffix();
    public static final String CONCURRENCY_ENGINE_LICENSE_PREFIX = "CONCURRENCY_ASRONLINE_ENGINE_LICENSE" + ConfigStore.getTestAffix();
    public static final String CONCURRENCY_APP_LICENSE_PREFIX = "CONCURRENCY_ASRONLINE_APP_LICENSE" + ConfigStore.getTestAffix();
    public static final String ASRONLINE_CALLSTATUS_PREFIX = "ASRONLINE_HTTP" + ConfigStore.getTestAffix();

    public static final String PUB_APPENG_PREFIX = "PUB_APPENG_";
    public static final String SEG_ID_PREFIX = "SEG_ID"+ ConfigStore.getTestAffix();
    public static final String PUB_ENG_PREFIX = "PUB_ENG_";

    public static final String ENGINE_ALIVE_PATH = "/alive";


    /**
     * 默认的静音检测阈值
     */
    public static final double DEFAULT_THRESHOLD = -95D;

    /**
     * 默认http post超时时间
     */
    public static final int DEFAULT_POST_TIMEOUT = 800;

    /**
     * 10秒
     */
    public static final int TIME_10_SECONDS = 10000;

    /**
     * 默认callStatus过期时间 用于保存转接时间 建议300
     */
    public static final String DEFAULT_KEY_EXPIRE_SEC = "300";
    /**
     * app的license过期时间 默认30秒
     */
    public static final long APP_LICENSE_EXPIRED_TIME = 33000;
    /**
     * 引擎license过期时间 默认20秒
     */
    public static final long ENGINE_LICENSE_EXPIRED_TIME = 20100;
    /**
     * 句子超长时间 默认一句不超过10秒
     */
    public static final int SENTENCE_MAX_LENGTH = 10000;

    /**
     * 音频采样率 目前固定是8000
     */
    public static final int SAMPLING_RATE = 8000;


}
