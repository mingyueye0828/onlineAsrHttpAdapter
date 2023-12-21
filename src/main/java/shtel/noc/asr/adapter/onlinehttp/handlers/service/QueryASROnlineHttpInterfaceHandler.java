package shtel.noc.asr.adapter.onlinehttp.handlers.service;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.HealthyCheck;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ResponseBody;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.VoiceSeg;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.UnhealthyException;
import shtel.noc.asr.adapter.onlinehttp.handlers.processor.FlowProcessor;


/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 请求参数进行处理,这里需要改造，并返回给客户识别字段
 * todo:这里需要改造，并返回给客户识别字段
 */
@Slf4j
public class QueryASROnlineHttpInterfaceHandler implements Handler<RoutingContext> {
    private final ResponseBody responseBody;
    private final FlowProcessor flowProcessor;

    public QueryASROnlineHttpInterfaceHandler(FlowProcessor flowProcessor, ResponseBody responseBody) {
        this.responseBody = responseBody;
        this.flowProcessor = flowProcessor;
    }


    @Override
    public void handle(RoutingContext context) {
        if(!HealthyCheck.isHealthy()) {
            context.fail(new UnhealthyException());
        }

        //这里需要先进行初始化，并进行向下分发，之后判断redis里面有没有返回结果，之后拿出结果来返回给他；
//        context.response().end(responseBody.voiceSegReceived());
        JsonObject requestBody = context.getBody().toJsonObject();
        VoiceSeg voiceSeg = requestBody.mapTo(VoiceSeg.class);
        //音频状态 1 2 4，1为起始，2为中间，4为结束
        String audioStatus = voiceSeg.getAudioStatus();
        log.info("========packet received! uid {}", voiceSeg.getUid());
        //这里根据相应的状态码进行响应；
        switch (audioStatus) {
            case ("1"):
                //初始化 需要建立音频的状态信息，将相关的信息以及送入的引擎记录下来，还要记录下返回地址
                flowProcessor.initNewSeg(voiceSeg);
                break;
            case ("2"):
                //发送语音流中间的包
                flowProcessor.middleSeg(voiceSeg);
                break;
            case ("4"):
                //同上，另外需要删去app并发
                flowProcessor.finalSeg(voiceSeg);
                break;
            default:
                log.error("Input audioStatus is not qualified!");

        }
        //********************************************************



    }
}
