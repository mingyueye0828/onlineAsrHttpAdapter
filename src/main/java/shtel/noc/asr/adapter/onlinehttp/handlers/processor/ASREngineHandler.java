package shtel.noc.asr.adapter.onlinehttp.handlers.processor;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import io.vertx.ext.web.codec.BodyCodec;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.CallStatus;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.Params;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.VoiceSeg;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.EngineException;
import shtel.noc.asr.adapter.onlinehttp.utils.Constants;
import java.net.URL;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 对引擎操作的封装，将相应的请求发送给下层，会返回并修改通话状态
 */
@Slf4j
public class ASREngineHandler {

    private final Vertx vertx;
    private final WebClient webClient;

    public ASREngineHandler(Vertx vertx, WebClient webClient) {
        this.vertx = vertx;
        this.webClient = webClient;
    }


    /**
     * 发送一帧或发送一句的最后一帧
     *
     * @param sentenceId 句子id
     * @param voiceSeg   请求数据
     * @param callStatus 通话状态
     * @return 在开始发送音频到引擎时就返回更新后的通话状态 不会fail
     */
    public Future<CallStatus> sendOrEnd(String sentenceId, VoiceSeg voiceSeg,
                                        CallStatus callStatus) {
        Promise<CallStatus> promise = Promise.promise();
        String audioStatus = voiceSeg.getAudioStatus();
        log.warn("发送的状态为：！{}", audioStatus);
        URL engineUrl = callStatus.getEngineUrl();

        //第一次的时候，选择引擎，之后直接callStatus中拿
        if (engineUrl==null){
            engineUrl = ConfigStore.randomSelectEngineModule(voiceSeg.getModelId());
            callStatus.setEngineUrl(engineUrl);
            voiceSeg.setAudioStatus("1");
        }

        //如果还是没有，就直接返回了
        if (engineUrl==null){
            promise.complete(callStatus);
            return promise.future();
        }
        URL finalEngineUrl = engineUrl;

        send2EngineAUW(voiceSeg, engineUrl)
                .onFailure(rf -> {
                    log.warn("send Audio failed! auw4 send seg failed! sentenceId {}, msg {}", sentenceId, rf.getMessage());
                    callStatus.setEngineUrl(null);
                    // 根据alive判断这个引擎是否存活
                    setDownAndCheckEngineStatus(voiceSeg.getModelId(),finalEngineUrl);
                })
                .onComplete(rc->{
                    promise.complete(callStatus);
                });
        return promise.future();
    }

    /**将包进行组合，发送给引擎
     * todo：还需要添加热词
     * */

    public Future<JsonObject> send2EngineAUW(VoiceSeg voiceSeg, URL engineUrl) {
        Promise<JsonObject> promise = Promise.promise();
        String audioStatus = voiceSeg.getAudioStatus();
        log.info("每次发送的状态为：{}！！！", audioStatus);
        String uid = voiceSeg.getUid();

        //去除初始化的热词，不然下层会报错
        Params params = voiceSeg.getParams();
        JsonObject paramsJson = JsonObject.mapFrom(params);
        if(paramsJson.getValue("hotWords").equals("")){
            paramsJson.remove("hotWords");
            paramsJson.remove("hotWordScore");
        }

        //将音频发送给引擎
        JsonObject reqBody = new JsonObject()
                .put("uid", voiceSeg.getUid())
                .put("auf", voiceSeg.getAuf())
                .put("audioStatus", audioStatus)
                .put("audioData", voiceSeg.getAudioData())
                .put("appId", voiceSeg.getAppId())
                .put("modelId", voiceSeg.getModelId())
                .put("callInfo", voiceSeg.getCallInfo())
                .put("params", paramsJson)
                ;
        log.debug("Params参数：{}", voiceSeg.getParams());
        if (voiceSeg.getAudioData().length() == 0 && !"4".equals(audioStatus)) {
            log.warn("Audio data is zero length! uid {}", uid);
        }
        log.debug("reqBody iS {}", reqBody.toString());
        log.debug("Engine module url is {}", engineUrl);

        //向引擎发送
        webClient.post(engineUrl.getPort(), engineUrl.getHost(), engineUrl.getPath())
                .as(BodyCodec.jsonObject())
                .expect(ResponsePredicate.SC_OK)
                .timeout(Constants.DEFAULT_POST_TIMEOUT)
                .sendJsonObject(reqBody)
                .onSuccess(rs -> {
                    log.debug("PCM has send engine !!!!!!");
                    JsonObject resultJson = rs.body();
                    if (null != resultJson) {
                        promise.complete(resultJson);
                    } else {
                        promise.fail(
                                new EngineException("auw result is null, need see uid " + uid
                                        + ", uid " + voiceSeg.getUid() + " result is " + rs.body()));
                    }
                })
                .onFailure(rf -> {
                    log.warn("返回错误信息：{}", rf.getMessage());
                            log.warn("metricsLog requestWarn {}", new JsonObject()
                                    .put("uid", voiceSeg.getUid()).put("type", "auw: " + audioStatus));
                            promise.fail(new EngineException("uid is " + voiceSeg.getUid() + ", " + rf.getMessage()));
                        }
                );
        return promise.future();
    }




    /**
     * 检查被标记为false的engineUrl的状态
     * 如果/alive接口没响应 或 返回的json中（包含引擎和当前剩余并发数）剩余并发数都小于等于0，则认为还在挂，否则认为存活，修改其为true
     */
    private void setDownAndCheckEngineStatus(String modelId, URL engine2bChecked){
        ConfigStore.getEngineIdUrlMap().get(modelId).put(engine2bChecked,false);
        vertx.setPeriodic(Constants.TIME_10_SECONDS, cs->
                webClient.get(engine2bChecked.getPort(), engine2bChecked.getHost(), Constants.ENGINE_ALIVE_PATH)
                        .addQueryParam("modelId", modelId)
                        .send()
                        .onSuccess(concurrency -> {
                            String result = concurrency.bodyAsString();
                            String[] list = result.split(":|}");
                            for (int i=2;i<list.length;i=i+3){
                                if (Integer.parseInt(list[i])>0){
                                    log.info("engine {}-{} is back online! current engine concurrency is {}",
                                            modelId, engine2bChecked, result);
                                    ConfigStore.getEngineIdUrlMap().get(modelId).put(engine2bChecked,true);
                                    vertx.cancelTimer(cs);
                                    break;
                                }
                            }
                            log.warn("test modelId {}, engine module {} has no engine available!",modelId,engine2bChecked);
                        })
                        .onFailure(rff->{
                            log.warn("test modelId {}, engine module {} is not good!",modelId,engine2bChecked);
                        })
        );
    }


}
