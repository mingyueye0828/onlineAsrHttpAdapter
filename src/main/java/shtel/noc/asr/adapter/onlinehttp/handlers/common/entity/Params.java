package shtel.noc.asr.adapter.onlinehttp.handlers.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ConfigStore;

import java.util.HashMap;
import java.util.Map;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/25
 * @annotation
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Params {
    private Integer silenceDuration;
    private Integer silencePeriod;
    private Integer maxLen;
    private Boolean punctuation;
    private Boolean needPieces;
    private String hotWords;
    private String hotWordScore;
    private String resultPipeUrl;

    // 可以用这个存储不确定的值
    private Map<String, Object> additionalProperties = new HashMap<>();

    public Params() {
        this.silenceDuration = 400;
        this.maxLen =12000;
        this.silencePeriod =2000;
        this.punctuation = true;
        this.needPieces=false;
        this.hotWords = "";
        this.hotWordScore = "";
        this.resultPipeUrl = ConfigStore.getAsrReceiverIp()+":"+ConfigStore.getAsrAdapterPort()+ConfigStore.getAsrReceiverInterface();
    }
}
