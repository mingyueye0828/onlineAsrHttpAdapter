package shtel.noc.asr.adapter.onlinehttp.handlers.processor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.RedisHandler;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.CallStatus;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.VoiceSeg;
import shtel.noc.asr.adapter.onlinehttp.handlers.service.QueryASROnlineHttpInterfaceHandler;
import shtel.noc.asr.adapter.onlinehttp.utils.CodeMappingEnum;
import shtel.noc.asr.adapter.onlinehttp.utils.Constants;
import shtel.noc.asr.adapter.onlinehttp.utils.RedisUtils;

import java.util.Collections;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 语音帧处理模块
 */
@Slf4j
public class FlowProcessor {
    private final Vertx vertx;
    private final ASREngineHandler asrEngineHandler;

    public FlowProcessor(Vertx vertx, ASREngineHandler asrEngineHandler) {
        this.vertx = vertx;
        this.asrEngineHandler = asrEngineHandler;
    }

    /**
     * 新call的处理
     * 需要检查是否有并发的余量,若有则检查并初始化通话状态，并且选择发送的引擎，并将相关的状态送入redis中
     *
     * @param voiceSeg 传入的第一个包
     */
    public void initNewSeg(VoiceSeg voiceSeg, RoutingContext context) {
        try {
            String uid = voiceSeg.getUid();
            String appId = voiceSeg.getAppId();
            //appId_engineId
            String concurrencyAppKeyPostFix = appId + "_" + ConfigStore.getAppEngineMap().get(appId);

            log.info("incoming new voice flow! app limit is " + ConfigStore.getAppLimitMap().toString());
            //先增加并发，将call初始化
            SessionController.checkAndIncrAppSession(concurrencyAppKeyPostFix, uid)
                    .compose(engineNotFull -> RedisHandler.checkAndCreateCallStatus(voiceSeg)
                            .onFailure(rrf -> {
                                SessionController.decrAppSession(concurrencyAppKeyPostFix, uid);
                            }))
                    // 在这里将相关信息放入redis
                    .compose(callStatus -> sendTheSeg(callStatus, voiceSeg))
                    .onSuccess(rrs -> {
                        log.info("INIT-NEW-VOICE-FLOW-DONE! uid {}", uid);
                        QueryASROnlineHttpInterfaceHandler.processReceivedResult(uid, "1", vertx)
                                .onSuccess(result -> {
                                    log.info("1111111111111111111111111111111");
                                    log.debug("The first response 1 success !!!!!!");
                                    context.response().end(result.encode());
                                })
                                .onFailure(failureResult -> {
                                    log.warn("The first response 1 failed !!!!!!");
                                    context.response().end(failureResult.toString());
                                });
                    })
                    .onFailure(rf -> {
                        log.warn("INIT-NEW-VOICE-FLOW-FAILED! uid {}, msg {},{}", uid, rf.getCause(), rf.getStackTrace());
                        context.response().end(CodeMappingEnum.getByTransCode(rf.getMessage()).toJson().encode());
                    });

        } catch (Exception e) {
            log.error("INIT-NEW-VOICE-FLOW-FAILED! UNCAUGHT ERROR, ", e);
        }
    }


    /**
     * 非首尾片段的处理
     * 获取锁，从callStatus中读取engineId，之后在释放锁
     *
     * @param voiceSeg 传入的非首尾1s
     */
    public void middleSeg(VoiceSeg voiceSeg, RoutingContext context) {
        String appId = voiceSeg.getAppId();
        String uid = voiceSeg.getUid();

        // 从redis中获取相关的callStatus，获取engineUrl,若是没有则丢丢弃
        RedisHandler.getCallStatus(uid)
                .onSuccess(callStatus -> {
                    voiceSeg.setAuf(callStatus.getAuf());
                    sendTheSeg(callStatus, voiceSeg)
                            .onSuccess(rrs -> {
                                log.debug("middle seg of call has been processed! uid {}", uid);
                                QueryASROnlineHttpInterfaceHandler.processReceivedResult(uid, "2", vertx)
                                        .onSuccess(result -> {
                                            log.info("2222222222222222222222222222");
                                            log.debug("The middle response 2 success !!!!!!");
                                            context.response().end(result.encode());
                                        })
                                        .onFailure(failureResult -> {
                                            log.debug("The middle response 2 failed !!!!!!");
                                            context.response().end(failureResult.toString());
                                        });
                            })
                            .onFailure(rrf -> {
                                log.warn("middle seg of call processed failed! uid {}, msg is {}",
                                        uid, rrf.getMessage());
                                context.response().end(CodeMappingEnum.ENGINE_GENERAL_FAILURE.toJson().encode());
                            });
                    //若是没有初始化，在redis中找不到就报这个错误
                }).onFailure(rf -> {
            log.warn("Required uid {} audio info not in the list", uid);
            RedisHandler.releaseDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid);
            context.response().end(CodeMappingEnum.QUERY_OUT_OF_RANGE.toJson().encode());
        });
    }


    /**
     * 末尾片段的处理，延迟500 ms
     * 需要减去应用的并发, NOTE 目前暂不考虑仍在缓存内的情况而直接删去app并发，后期若有需要可修改
     *
     * @param voiceSeg 传入的最后1s
     */
    public void finalSeg(VoiceSeg voiceSeg, RoutingContext context) {
        String appId = voiceSeg.getAppId();
        String uid = voiceSeg.getUid();
        String appEnginePostFix = appId + "_" + ConfigStore.getAppEngineMap().get(appId);
        vertx.setTimer(500L, st -> {    //NOTE 延迟处理最后一个包 以避免某些语音流的最后一个包来的太快而影响前面的处理流程
            RedisHandler.getCallStatus(uid)
                    .onSuccess(callStatus -> {
                        voiceSeg.setAuf(callStatus.getAuf());
                        sendTheSeg(callStatus, voiceSeg)
                                .onSuccess(rrs -> {
                                    log.debug("last seg of call has been processed! uid {}", uid);
                                    QueryASROnlineHttpInterfaceHandler.processReceivedResult(uid, "4", vertx)
                                            .onSuccess(result -> {
                                                log.info("4444444444444444444444444444");
                                                context.response().end(result.encode());
                                                //将最后的结果获取并发送之后，在减去并发
                                                SessionController.decrAppSession(appEnginePostFix, uid);
                                            })
                                            .onFailure(failureResult -> {
                                                log.debug("The middle response 2 failed !!!!!!");
                                                context.response().end(failureResult.toString());
                                            });
                                })
                                .onFailure(rrf -> {
                                    log.warn("last seg of call processed failed! uid {}, msg is {}", uid, rrf.getMessage());
                                    SessionController.decrAppSession(appEnginePostFix, uid);
                                    context.response().end(CodeMappingEnum.ENGINE_GENERAL_FAILURE.toJson().encode());
                                });
                    })
                    .onFailure(rf -> {
                        //该通话可能未被初始化（无callStatus）
                        log.warn("Required uid {} audio info not in the list", uid);
                        RedisHandler.releaseDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid);
                        context.response().end(CodeMappingEnum.QUERY_OUT_OF_RANGE.toJson().encode());
                    });
        });
    }

    //发送语音片段，并且上锁
    public Future<Void> sendTheSeg(CallStatus callStatus, VoiceSeg voiceSeg) {
        Promise<Void> promise = Promise.promise();
        String uid = voiceSeg.getUid();
        callStatus.setFrameId(callStatus.getFrameId() + 1);
        String sentenceId = uid + "__" + callStatus.getFrameId();
        //sendOrEnd 不管引擎返回什么 都应该正常返回更新后的callStatus 不会fail
        asrEngineHandler.sendOrEnd(sentenceId, voiceSeg, callStatus)
//                .onFailure(promise::fail)
                .onFailure(rf ->{
                    promise.fail(CodeMappingEnum.ENGINE_GENERAL_FAILURE.getTransCode());
                })
                .onSuccess(rs -> {
                    promise.complete();
//                    RedisHandler.setCallStatus(uid, callStatusNew1, true);
                })
                //这里将更改过的callStatus放到redis里面
                .onComplete(callStatusNew -> {
                    RedisHandler.setCallStatus(uid, callStatusNew.result(), true);
                });
        return promise.future();
    }


}
