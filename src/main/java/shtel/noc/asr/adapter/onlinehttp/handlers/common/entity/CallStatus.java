package shtel.noc.asr.adapter.onlinehttp.handlers.common.entity;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.utils.SystemClock;

import java.net.URL;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 语音流状态信息 存于redis中，可以根据这个来选择向下发送的引擎
 */
@Slf4j
@Data
public class CallStatus {
    /**
     * 请求id 其实是uid
     */
    private String reqId;
    /**
     * 应用id
     */
    private String appId;
    /**
     * 语音帧编号
     */
    private int frameId;
    /**
     * 引擎url
     */
    private URL engineUrl;

    /**
     * 语音流采样率
     */
    private int auf;

    /**
     * 初始化
     */
    public CallStatus() {
        reqId = "0";
        appId = "0";
        frameId = 0;
        auf=8000;
    }

    public CallStatus(VoiceSeg voiceSeg) {
        reqId = "0";
        appId = "0";
        frameId = 0;
        auf=voiceSeg.getAuf();
    }


}
