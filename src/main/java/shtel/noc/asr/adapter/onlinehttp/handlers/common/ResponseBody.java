package shtel.noc.asr.adapter.onlinehttp.handlers.common;

import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import static shtel.noc.asr.adapter.onlinehttp.utils.Constants.successResponse;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 这里是对客户端的回复
 */
@Slf4j
public class ResponseBody {

    private static final String QUERY_ASR_SUCCESS = successResponse.toString();

    /**
     * 收到片段就回复掉
     */
    public String voiceSegReceived(){ return QUERY_ASR_SUCCESS; }

    public String resultReceived(){
        return QUERY_ASR_SUCCESS;
    }



}
