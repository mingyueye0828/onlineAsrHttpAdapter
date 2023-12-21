package shtel.noc.asr.adapter.onlinehttp.handlers.common;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.redis.client.RedisAPI;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import shtel.noc.asr.adapter.onlinehttp.utils.ConfigurationKeys;
import shtel.noc.asr.adapter.onlinehttp.utils.Constants;
import shtel.noc.asr.adapter.onlinehttp.utils.RedisUtils;

import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 储存一些配置 并支持实时更新配置文件和redis里的配置(如app并发数)
 */
@Slf4j
@Data
public class ConfigStore {

    @Getter
    @Setter
    private static AtomicBoolean notFirstInit=new AtomicBoolean(false);

    @Getter
    private static String testAffix;

    /**
     * 实时asr接口path,asrHttpInterface对外接口，asrReceiverInterface下层HTTP接收接口
     */
    @Getter
    private static String asrHttpInterface;
    @Getter
    private static String asrReceiverInterface;
    @Getter
    private static int asrAdapterPort;

    /**
     * 应用并发上限 ($appId + "_" + $engineId, concurrencyLimit)
     */
    @Getter
    private static Map<String, Integer> appLimitMap = new HashMap<>();

    /**
     * app和引擎组合的列表，
     * appEngineKeyList为PUB_APPENG_APPID_ENGINEID
     * appEngineMap： key为appId value为engineId
     */
    @Getter
    private static String[] appEngineList;
    @Getter
    private static String[] appEngineKeyList;
    @Getter
    private static Map<String, String> appEngineMap = new HashMap<>();
    /**
     * 引擎列表
     */
    @Getter
    private static String[] engineList;

    @Getter
    private static JsonObject sessionControllerConfig;

    /**
     * 引擎列表;key:engineId; value:(engineIdUrl, true)
     * modelId: [{url: status}]
     * status :true = available
     * 这里主要标记引擎是否还活着
     */
    @Getter
    private static Map<String, LinkedHashMap<URL,Boolean>> engineIdUrlMap = new HashMap<>();

    public static void init(JsonObject adapterConfig, JsonObject sessionControllerConfig){
        //查看期望值false与当前值是否一致，一致则设置为true;
        if(!notFirstInit.compareAndSet(false, true)){
            return;
        }
        ConfigStore.asrHttpInterface = adapterConfig.getString(ConfigurationKeys.ASR_HTTP_INTERFACE.name());
        ConfigStore.asrReceiverInterface = adapterConfig.getString(ConfigurationKeys.ASR_RECEIVER_INTERFACE.name());
        ConfigStore.asrAdapterPort = adapterConfig.getInteger(ConfigurationKeys.ASR_ADAPTER_PORT.name());

        ConfigStore.testAffix = adapterConfig.getString(ConfigurationKeys.TEST_AFFIX.name());
        setLevel(Level.valueOf(adapterConfig.getString(ConfigurationKeys.LOG_LEVEL.name())));

        //检测下层引擎配置是否有误，并初始化各个下层引擎的并发限制
        ConfigStore.sessionControllerConfig = sessionControllerConfig;
        if (sessionControllerConfig == null || asrHttpInterface == null) {
            log.error("Adapter config configStore Error!!!!");
        }
        else {
            initSessionLimits(sessionControllerConfig);
        }
    }

    /**
     * 初始化各个下层引擎的并发限制
     *
     * @param config 输入的配置
     */
    public static Future<Void> initSessionLimits(JsonObject config) {
        Promise<Void> promise = Promise.promise();
        appEngineList = config.getString(ConfigurationKeys.APPID_ENGINEID_LIST.name()).split("--");
        engineList = config.getString(ConfigurationKeys.ENGINEID_LIST.name()).split(";");
        String[] engineUrlList = config.getString(ConfigurationKeys.ASRONLINE_ENGINE_URLS.name()).split(";");

        //engineIdUrlMap.clear(); //// NOTE: 2021/11/4 为什么可以不用clear 呢，因为引擎选择是靠redis完成的，redis里的数据是准确的，这里留着可以用于热切换，而且也不怎么占内存
        for (int i = 0; i < engineUrlList.length; i++) {
            if (!engineIdUrlMap.containsKey(engineList[i])){
                engineIdUrlMap.put(engineList[i], new LinkedHashMap<>());
            }
            try {
                engineIdUrlMap.get(engineList[i]).put(new URL(engineUrlList[i]),true);
            } catch (Exception e) {
                log.warn("engine URL error!", e);
            }
        }

        log.info("ASR Engine is {}", engineIdUrlMap.toString());

        //加前缀
        appEngineKeyList = addPrefix2Array(Constants.PUB_APPENG_PREFIX, appEngineList);

        //appId engineId
        for (String appEngineStr : appEngineList) {
            String[] appEngineTempList = appEngineStr.split("_");
            appEngineMap.put(appEngineTempList[0], appEngineTempList[1]);
        }

        //redis里获取引擎并发，并初始化
        getAndSetConcurrencyLimit()
                .compose(r -> setConcurrencyKeys(map2RedisKeys(Constants.CONCURRENCY_APP_PREFIX, appLimitMap)))
                .onSuccess(rs -> {
                    log.info("Concurrency Limit Set!");
                    promise.complete();
                }).onFailure(rf -> {
            log.error("Concurrency Limit Set ERROR! ", rf.getCause());
            promise.fail(rf.getCause());
        });
        return promise.future();
    }

    /**
     * 从redis里获取并发限制，并初始化引擎并发，
     * appLimitMap：($appId + "_" + $engineId, concurrencyLimit)
     * 设置对应的返回地址
     */
    public static Future<Void> getAndSetConcurrencyLimit() {
        Promise<Void> promise = Promise.promise();
        RedisHandler.mGetMaxConcurrency(appEngineKeyList)
                .onSuccess(newAppLimitMap -> {
                    if (!appLimitMap.equals(newAppLimitMap)) {
                        appLimitMap = newAppLimitMap;
                        log.info("SET-APP-CONCURRENCY-LIMIT is {}" , appLimitMap.toString());
                    }
        }).onFailure(rf -> promise.fail("get App Concurrency Limit ERROR! " + rf.getCause()));
        return promise.future();
    }

    /***
     * 在redis中设置用于统计并发数的key,初始化为0
     * @param listKeys CONCURRENCY_ASRONLINE_$APPID_$ENGINEID
     */
    public static Future<Void> setConcurrencyKeys(List<String> listKeys) {
        Promise<Void> promise = Promise.promise();
        for (int i = 0; i < listKeys.size(); i++) {
            int finalI = i;
            RedisAPI.api(RedisUtils.getClient()).setnx(listKeys.get(i), "0")
                    .onSuccess(rs -> {
                        if (finalI >= listKeys.size() - 1) {
                            promise.complete();
                        }
                        log.debug("Init concurrency success,Key is {}",listKeys.get(finalI));
                    })
                    .onFailure(rf -> promise.fail("Init concurrency failed, Key is " + listKeys.get(finalI)));
        }
        return promise.future();
    }

    /**
     * 将map转为redis的键，意思就是CONCURRENCY_APP_PREFIX+appId + "_" + engineId
     * @param keyPrefix redis键前缀
     * @param map       需要被转换的map
     * @return 转换完成的redis键的list
     */
    private static List<String> map2RedisKeys(String keyPrefix, Map<String, Integer> map) {
        List<String> result = new ArrayList<>();
        List<String> listKeys = new ArrayList<>(map.keySet());
        for (String listKey : listKeys) {
            result.add(keyPrefix + listKey);
        }
        return result;
    }

    /**
     * 给数组每个元素加上前缀
     *
     * @param prefix     需要加的前缀
     * @param inputArray 需要改动的数组
     * @return 返回修改后的数组
     */
    private static String[] addPrefix2Array(String prefix, String[] inputArray) {
        String[] array = new String[inputArray.length];
        for (int i = 0; i < inputArray.length; i++) {
            array[i] = prefix + inputArray[i];
        }
        return array;
    }

    /**
     * 随机选择哪一个引擎发送
     */
    public static URL randomSelectEngineModule(String modelId){
        ArrayList<URL> trueUrls = new ArrayList<>();

        for (URL engineUrl : ConfigStore.getEngineIdUrlMap().get(modelId).keySet()){
            if (ConfigStore.getEngineIdUrlMap().get(modelId).get(engineUrl)){
                trueUrls.add(engineUrl);
            }
        }
        if (trueUrls.isEmpty()){
            return null;
        }
        int randomIndex = new Random().nextInt(trueUrls.size());
        log.info("select url is {} ,modelId is {}, index is {} , list is {}",trueUrls.get(randomIndex),modelId,randomIndex,ConfigStore.getEngineIdUrlMap().get(modelId).keySet());
        return trueUrls.get(randomIndex);
    }




    /**
     * 解除配置锁
     */
    public static void resetInitFlag(){
        notFirstInit.set(false);
    }

    /**
     * 设置日志级别
     * @param level 日志级别，并判断是否使用debug
     */
    public static void setLevel(Level level) {
        log.info("new log level {}, old is debug enabled {}",level,log.isDebugEnabled());
        Configurator.setAllLevels(Constants.LOG_NAME,level);
        log.info("now debug enabled {}",log.isDebugEnabled());
        log.debug("debug enabled！");
    }

}
