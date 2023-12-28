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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static shtel.noc.asr.adapter.onlinehttp.utils.Constants.MAX_ATTEMPTS;
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
        log.debug("Adapter return url is ：{}",voiceSeg.getParams().getResultPipeUrl());
        //音频状态 1 2 4，1为起始，2为中间，4为结束
        String audioStatus = voiceSeg.getAudioStatus();
        log.info("Audio status is：{}", audioStatus);

        String uid = voiceSeg.getUid();
        log.info("========packet received! uid {}", voiceSeg.getUid());
        //这里根据相应的状态码进行响应；
        switch (audioStatus) {
            case ("1"):
                //初始化 需要建立音频的状态信息，将相关的信息以及送入的引擎记录下来，还要记录下返回地址
                flowProcessor.initNewSeg(voiceSeg,context);
//                processReceivedResult(uid, "1", vertx).onSuccess(result -> {
//                    log.debug("1111111111111111");
//                    context.response().end(result.encode());
//                });
                break;
            case ("2"):
                //发送语音流中间的包
                flowProcessor.middleSeg(voiceSeg,context);
//                processReceivedResult(uid, "2", vertx).onSuccess(result -> {
//                    log.debug("22222222222222");
//                    context.response().end(result.encode());
//                });
                break;
            case ("4"):
                //同上，另外需要删去app并发
                flowProcessor.finalSeg(voiceSeg,context);
//                processReceivedResult(uid, "4", vertx).onSuccess(result -> {
//                    log.info("4444444444444444444444444444");
//                    context.response().end(result.encode());
//                    //将最后的结果获取并发送之后，在减去并发
//                    String appId = voiceSeg.getAppId();
//                    String appEnginePostFix = appId + "_" + ConfigStore.getAppEngineMap().get(appId);
//                    SessionController.decrAppSession(appEnginePostFix, uid);
//                });
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
            log.debug("The last package is coming");
            AtomicBoolean timerCancelled = new AtomicBoolean(false);
            AtomicInteger attempts = new AtomicInteger(0);
            vertx.setPeriodic(1000, timerHandler -> {
                RedisHandler.getReceiverResult(uid, true)
                        .onSuccess(result -> {
                            attempts.incrementAndGet();
                            for (String oneResult : result) {
                                JsonObject jsonObject = new JsonObject(oneResult);
                                log.debug("The every json is：{}", jsonObject);
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
                            } else if (!timerCancelled.get() && attempts.get() >= MAX_ATTEMPTS){
                                JsonObject response = buildResponse(allResults);
                                promise.complete(response);
                                vertx.cancelTimer(timerHandler);
                                log.warn("Get the last result timeout !!!");
                                promise.fail(CodeMappingEnum.ENGINE_GET_RESULT_FAILURE.toJson().toString());
                            }
                        })
                        .onFailure(error -> {// 处理异常情况
                                    vertx.cancelTimer(timerHandler);
                                    promise.fail(CodeMappingEnum.REDIS_COMMUNICATION_ERROR.toJson().toString());
                                });
            });
        } else {
            RedisHandler.getReceiverResult(uid, true)
                    .onSuccess(result -> {
                        log.debug("Engine return response：{}", (Object) result);
                        JsonObject response = buildResponse(result);
                        log.debug("Return to the client response:{}", response.toString());
                        promise.complete(response);
                    })
                    .onFailure(error ->{
                        log.error("Redis can not reply, message is {}",error.getMessage());
                        promise.fail(CodeMappingEnum.REDIS_COMMUNICATION_ERROR.toJson().toString());}
                    );
        }
        return promise.future();
    }

    public static JsonObject buildResponse(String[] result) {
        JsonObject response = CodeMappingEnum.SUCCESS.toJson().copy();
        log.debug("redis get result is String[] {}", result);
        if (result.length != 0) {
            JsonArray resultArray = new JsonArray();

            for (String item : result) {
                JsonObject jsonItem = new JsonObject(item);
                resultArray.add(jsonItem);
            }
            response.put("results", resultArray);
            log.debug("String[] response is {}",response);
            return response;
        }
        return CodeMappingEnum.SUCCESS.toJson();
    }

    private static JsonObject buildResponse(List<String[]> results) {
        log.debug("redis get result is List<String[]> {}", results.toString());
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
            log.debug("List<String[]> response is {}",response);
            return response;
        }
        return CodeMappingEnum.SUCCESS.toJson();
    }
}
