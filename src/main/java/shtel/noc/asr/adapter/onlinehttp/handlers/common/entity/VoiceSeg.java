package shtel.noc.asr.adapter.onlinehttp.handlers.common.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.vertx.core.json.JsonObject;
import lombok.Data;

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
    private JsonObject callInfo;
    private JsonObject params;


    public VoiceSeg() {
        this.uid = "";
        this.auf=8000;
        this.audioData = "";
        this.audioStatus = "";
        this.modelId = "";
        this.appId = "";
        this.callInfo = new JsonObject();
        this.params = new JsonObject();
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
