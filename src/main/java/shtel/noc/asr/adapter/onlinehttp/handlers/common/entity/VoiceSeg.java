package shtel.noc.asr.adapter.onlinehttp.handlers.common.entity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.json.JsonObject;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 请求参数映射，进行处理
 * todo：需要添加热词
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class VoiceSeg {
    private String uid;
    private int auf;
    private String audioStatus;
    private String audioData;
    private String modelId;
    private String appId;
    private Map<String, Object> callInfo;
    private Params params;



    public VoiceSeg() {
        this.uid = "";
        this.auf=8000;
        this.audioData = "";
        this.audioStatus = "";
        this.modelId = "";
        this.appId = "";
        this.params = new Params();
        this.callInfo = new HashMap<>();

    }



    /**
     * 不是16000则就是8000
     */
    public void checkAudioFormat(){
        if (this.auf !=16000){
            this.auf = 8000;
        }
    }



    public void setAuf(int auf){
        this.auf=auf;
    }

}
