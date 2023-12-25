package shtel.noc.asr.adapter.onlinehttp.handlers.service;

import io.vertx.core.*;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.checkerframework.checker.units.qual.C;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.HealthyCheck;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.RedisHandler;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ResponseBody;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.ResultReceived;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.VoiceSeg;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.UnhealthyException;
import shtel.noc.asr.adapter.onlinehttp.handlers.processor.FlowProcessor;
import shtel.noc.asr.adapter.onlinehttp.handlers.processor.SessionController;
import shtel.noc.asr.adapter.onlinehttp.utils.CodeMappingEnum;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static shtel.noc.asr.adapter.onlinehttp.utils.Constants.successResponse;


/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 请求参数进行处理, 这里需要改造，并返回给客户识别字段
 * todo:这里需要改造，并返回给客户识别字段
 */
@Slf4j
public class QueryASROnlineHttpInterfaceHandler implements Handler<RoutingContext> {

    private final FlowProcessor flowProcessor;
    private final Vertx vertx;

    public QueryASROnlineHttpInterfaceHandler(Vertx vertx, FlowProcessor flowProcessor) {

        this.flowProcessor = flowProcessor;
        this.vertx = vertx;
    }


    @Override
    public void handle(RoutingContext context) {
        if (!HealthyCheck.isHealthy()) {
            context.fail(new UnhealthyException());
        }
        //这里需要先进行初始化，并进行向下分发，之后判断redis里面有没有返回结果，之后拿出结果来返回给他；
        JsonObject requestBody = context.getBody().toJsonObject();
        //这里加上返回地址
        VoiceSeg voiceSeg = requestBody.mapTo(VoiceSeg.class);
        log.debug("返回地址为：{}",voiceSeg.getParams().getResultPipeUrl());
        //音频状态 1 2 4，1为起始，2为中间，4为结束
        String audioStatus = voiceSeg.getAudioStatus();
        String uid = voiceSeg.getUid();
        log.info("========packet received! uid {}", voiceSeg.getUid());
        //这里根据相应的状态码进行响应；
        switch (audioStatus) {
            case ("1"):
                //初始化 需要建立音频的状态信息，将相关的信息以及送入的引擎记录下来，还要记录下返回地址
                flowProcessor.initNewSeg(voiceSeg);
                processReceivedResult(uid, "1", vertx).onSuccess(result -> {
                    log.debug("1111111111111111");
                    context.response().end(result.encode());
                });
                break;
            case ("2"):
                //发送语音流中间的包
                flowProcessor.middleSeg(voiceSeg);
                processReceivedResult(uid, "2", vertx).onSuccess(result -> {
                    log.debug("22222222222222");
                    context.response().end(result.encode());
                });
                break;
            case ("4"):
                //同上，另外需要删去app并发
                flowProcessor.finalSeg(voiceSeg);
                processReceivedResult(uid, "4", vertx).onSuccess(result -> {
                    log.info("4444444444444444444444444444");
                    context.response().end(result.encode());
                    //将最后的结果获取并发送之后，在减去并发
                    String appId = voiceSeg.getAppId();
                    String appEnginePostFix = appId + "_" + ConfigStore.getAppEngineMap().get(appId);
                    SessionController.decrAppSession(appEnginePostFix, uid);
                });
                break;
            default:
                log.error("Input audioStatus is not qualified!");

        }
    }

    //********************这里进行响应************************************
    public static Future<JsonObject> processReceivedResult(String uid, String status, Vertx vertx) {
        Promise<JsonObject> promise = Promise.promise();

        // 定义一个变量用于存储最终结果
        List<String[]> allResults = new ArrayList<>();

        //如果status是4
        if ("4".equals(status)) {

            log.debug("进来4了！！！！！！！！！！！！");
            AtomicBoolean timerCancelled = new AtomicBoolean(false);
            vertx.setPeriodic(1000, timerHandler -> {
                RedisHandler.getReceiverResult(uid, true)
                        .onSuccess(result -> {
                            for (String oneResult : result) {
                                JsonObject jsonObject = new JsonObject(oneResult);
                                log.debug("映射的json为!!!!!!!：{}", jsonObject);
                                ResultReceived resultReceived = jsonObject.mapTo(ResultReceived.class);
                                int resultStatus = resultReceived.getStatus();
                                if (resultStatus == 4) {
                                    vertx.cancelTimer(timerHandler);
                                    timerCancelled.set(true);
                                    break;
                                }
                            }
                            Collections.addAll(allResults, result);

                            // 如果计时器没有被取消，则构建 JsonObject 并完成 Promise
                            if (timerCancelled.get()) {
                                JsonObject response = buildResponse(allResults);
                                promise.complete(response);
                            }
                        })
                        .onFailure(error -> {// 处理异常情况
                                    vertx.cancelTimer(timerHandler);
                                    promise.fail(error);
                                });
            });
        } else {
            RedisHandler.getReceiverResult(uid, true)
                    .onSuccess(result -> {
                        log.debug("返回的长度是：{}", result.length);
                        log.debug("返回的结果是：{}", (Object) result);
                        JsonObject response = buildResponse(result);
                        log.debug("这里的结果是？？？？？？？？？？？？{}", response.toString());
                        promise.complete(response);
                    })
                    .onFailure(error ->{
                        log.error("返回String[]失败!!!!!!!136");
                        promise.fail(error);}
                            );
        }
        return promise.future();
    }

    public static JsonObject buildResponse(String[] result) {
        JsonObject response = CodeMappingEnum.SUCCESS.toJson().copy();
        if (result.length != 0) {
            log.debug("String[]这里进来了！！！！");
//            response.put("results", new JsonArray(Arrays.asList(result)));
            JsonArray resultArray = new JsonArray();
            for (String item : result) {
                JsonObject jsonItem = new JsonObject(item);
                resultArray.add(jsonItem);
            }
            response.put("results", resultArray);
            log.debug("接收到string[]!!!!!!!!!");
            return response;
        }
        log.debug("没有接收到string[]!!!!!!!!!");
        log.debug("返回的response为：{}", CodeMappingEnum.SUCCESS.toJson());
        return CodeMappingEnum.SUCCESS.toJson();
    }

    private static JsonObject buildResponse(List<String[]> results) {
        log.debug("List<String[]>这里进来了！！！！");
        JsonObject response = CodeMappingEnum.SUCCESS.toJson().copy();
        if (!results.isEmpty()) {
            JsonArray resultArray = new JsonArray();
            for (String[] result : results) {
                for (String item : result) {
                    JsonObject jsonItem = new JsonObject(item);
                    resultArray.add(jsonItem);
                }
            }
            response.put("results", resultArray);
            return response;
        }
        return CodeMappingEnum.SUCCESS.toJson();
    }



}
