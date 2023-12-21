package shtel.noc.asr.adapter.onlinehttp.handlers.common.entity;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/21
 * @annotation 接收实体，之后进行封装，返回给客户端
 */
public class ResultReceived {
    private String sentence;
    private int bg;
    private int ed;
    private String type;
    private String appId;
    private String engineId;
    private String uid;
    private int status;
    private int ret;
    private JsonObject callInfo;
    private JsonArray word_pieces;



    public ResultReceived(){
        this.sentence ="";
        this.bg=0;
        this.ed=0;
        this.type="";
        this.appId="";
        this.engineId="1";
        this.uid="";
        this.status=3;
        this.ret=0;
        this.callInfo= new JsonObject();
        this.word_pieces = new JsonArray();
    }


}
