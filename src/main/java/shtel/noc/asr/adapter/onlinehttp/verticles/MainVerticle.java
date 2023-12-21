package shtel.noc.asr.adapter.onlinehttp.verticles;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ResponseBody;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.handler.AliveHandler;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.handler.FailureHandler;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.handler.PingHandler;
import shtel.noc.asr.adapter.onlinehttp.utils.EventBusChannels;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 主节点
 * todo:有待完善
 */
@Slf4j
public class MainVerticle extends AbstractVerticle {
    @Override
    public void start(Promise<Void> startPromise) throws Exception {

        //验证主机名,信任服务器端点证书，HTTP链接持久性，HTTP连接池最大连接数，连接超时时间，超过连接空闲时间则关闭，等待连接池大小
        WebClientOptions options = new WebClientOptions()
                .setVerifyHost(false)
                .setTrustAll(true)
                .setKeepAlive(true)
                .setMaxPoolSize(200)
                .setConnectTimeout(10000)
                .setIdleTimeout(10)
                .setMaxWaitQueueSize(200);
        //options.setKeepAlive(false);//如果需要立刻断开连接，不使用连接池，则加入此行代码
        WebClient client = WebClient.create(vertx, options);

        /**
         * 初始化相关处理器,探针，失败
         */

        PingHandler pingHandler = new PingHandler();
        AliveHandler aliveHandler = new AliveHandler();

        FailureHandler failureHandler = new FailureHandler();

//        ResponseBody responseBody = new ResponseBody();




    }

    /**
     * 配置文件变更处理
     */
    private void configureEventBus() {
        vertx
                .eventBus()
                .<JsonObject>consumer(
                        EventBusChannels.CONFIGURATION_CHANGED.name(),
                        message -> {

                            try {
                                log.info("Configuration has changed, verticle {} is updating...",
                                        deploymentID());
                                configureHandlers(message.body());
                                log.info(
                                        "Configuration has changed, verticle {} has been updated...",
                                        deploymentID());
                            } catch (Exception e) {
                                log.error("Update config error!", e);
                            }

                        });
    }

    /***
     * 配置信息更新
     * @param configuration 更新后的配置信息
     */
    private void configureHandlers(JsonObject configuration) {
        //打印配置文件
        log.info(configuration.encodePrettily());
        // redis 设置dock
        vertx.eventBus().request(
                EventBusChannels.SET_REDIS_OPTIONS.name(), configuration.getJsonObject("redis"), r -> {
                });

        ConfigStore.init(config().getJsonObject("adapter"), configuration.getJsonObject("sessionController"));

        vertx.setTimer(5000L,s-> ConfigStore.resetInitFlag());
    }


}
