package shtel.noc.asr.adapter.onlinehttp.handlers.processor;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.redis.client.RedisAPI;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.RedisHandler;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.CallStatus;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.VoiceSeg;
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
     * @param voiceSeg 传入的第一个包
     */
    public void initNewSeg(VoiceSeg voiceSeg) {
        try {
            String uid = voiceSeg.getUid();
            String appId = voiceSeg.getAppId();
            //appId_engineId
            String concurrencyAppKeyPostFix = appId + "_" + ConfigStore.getAppEngineMap().get(appId);

            log.info("incoming new voice flow! app limit is " + ConfigStore.getAppLimitMap().toString());
            //先增加并发，将call初始化
            SessionController.checkAndIncrAppSession(concurrencyAppKeyPostFix, uid)
                    .compose(engineNotFull -> RedisHandler.checkAndCreateCallStatus(voiceSeg)
                            .onFailure(rrf -> {SessionController.decrAppSession(concurrencyAppKeyPostFix, uid);
                                //SessionController.decrEngineSession(concurrencyAppKeyPostFix, uid);
                            }))
                    // 在这里将相关信息放入redis
                    .compose(callStatus -> sendTheSeg(callStatus, voiceSeg))
                    .onSuccess(rrs -> log.info("INIT-NEW-VOICE-FLOW-DONE! uid {}", uid))
                    .onFailure(rf -> log.warn("INIT-NEW-VOICE-FLOW-FAILED! uid {}, msg {},{}", uid, rf.getCause(),rf.getStackTrace()));

        } catch (Exception e) {
            log.error("INIT-NEW-VOICE-FLOW-FAILED! UNCAUGHT ERROR, ", e);
        }
    }


    /**
     * 非首尾片段的处理
     * 获取锁，从callStatus中读取engineId，之后在释放锁
     * @param voiceSeg 传入的非首尾1s
     */
    public void middleSeg(VoiceSeg voiceSeg) {
        String appId = voiceSeg.getAppId();
        String uid = voiceSeg.getUid();
        String enginePostFix = ConfigStore.getAppEngineMap().get(appId);
        String appEnginePostFix = appId + "_" + enginePostFix;

        // 从redis中获取相关的callStatus，获取engineUrl
        RedisHandler.getCallStatus(uid)
                .onSuccess(callStatus -> {
                        voiceSeg.setAuf(callStatus.getAuf());
                    sendTheSeg(callStatus, voiceSeg)
                                .onSuccess(rrs -> log.debug("middle seg of call has been processed! uid {}",uid))
                                .onFailure(rrf -> log.warn("middle seg of call processed failed! uid {}, msg is {}",
                                        uid, rrf.getMessage()));
                }).onFailure(rf -> {
            RedisHandler.releaseDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid);
        });
    }


    /**
     * 末尾片段的处理，延迟500 ms
     * 需要减去应用的并发, NOTE 目前暂不考虑仍在缓存内的情况而直接删去app并发，后期若有需要可修改
     * @param voiceSeg 传入的最后1s
     */
    public void finalSeg(VoiceSeg voiceSeg) {
        String appId = voiceSeg.getAppId();
        String uid = voiceSeg.getUid();
        String appEnginePostFix = appId + "_" + ConfigStore.getAppEngineMap().get(appId);
        vertx.setTimer(500L, st -> {    //NOTE 延迟处理最后一个包 以避免某些语音流的最后一个包来的太快而影响前面的处理流程
            RedisHandler.getCallStatus(uid)
                    .onSuccess(callStatus -> {
                        voiceSeg.setAuf(callStatus.getAuf());
                        sendTheSeg(callStatus, voiceSeg)
//                                .onComplete(rr -> {
//
//
//                                    SessionController.decrAppSession(appEnginePostFix, uid);
//                                })
                                //.onSuccess(rrs -> log.debug("last seg of call has been processed! uid {}",uid))
                                .onFailure(rrf -> log.warn("last seg of call processed failed! uid {}, msg is {}",
                                        uid, rrf.getMessage()));
                    })
                    .onFailure(rf -> {
                        //该通话可能未被初始化（无callStatus）
                        RedisHandler.releaseDistributionLock(Constants.ASRONLINE_CALLSTATUS_PREFIX + uid + "_LOCK", uid);
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
                .onFailure(promise::fail)
                .onSuccess(rs->promise.complete())
                //这里将更改过的callStatus放到redis里面
                .onComplete(callStatusNew -> {
                    RedisHandler.setCallStatus(uid, callStatusNew.result(), true);
                });
        return promise.future();
    }


}
