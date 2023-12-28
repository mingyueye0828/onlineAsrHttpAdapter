package shtel.noc.asr.adapter.onlinehttp.utils;

import io.vertx.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation
 */
public enum CodeMappingEnum {
    /**
     * 参数映射
     * 0=Success
     * 3102=Parameters error
     * 3202=Required audio info not in the list
     * 3203=Adapter processing general error
     * 3205=Data sync from mySQL to redis failed
     * 3211=ASR Engine is full, request rejected
     * 3212=This record is already in the process of ASR, please be patient!
     * 3299=ASR Engine - General Failure
     * 9999=REQUEST_FAILED
     */

    // 定义实时转写返回码
    // APP侧会获得到的错误码
    PARAMETER_ERROR("3201", "Request parameters Error!"),
    ENGINE_CONCURRENCY_FULL( "3202", "ASR Engine is full, request Rejected!"),
    QUERY_OUT_OF_RANGE( "3203", "Required audio info not in the list!"),
    ENGINE_RESPONSE_FAILURE("3204", "ASR Engine refuse request！"),
    ENGINE_GENERAL_FAILURE("3205", "ASR Engine - General Failure!"),

    ENGINE_GET_RESULT_FAILURE("3301", "ASR Engine can not return result!"),

    //Redis错误码
    REDIS_COMMUNICATION_ERROR("3401", "MetricsLog requestError Redis unreachable!"),
    //没有获取响应地call记录(返回没有记录)
    REDIS_GET_CALL_RECORDE_FAILURE("3402", "Get uid status Failed, no record of uid!"),

    //通用成功码
    SUCCESS("200", "Success"),
    //通用错误码
    REQUEST_FAILED("9999", "metricsLog request Error Undefined response Error!"),

    //暂时没用到的
    //引擎适配层会获得到的错误码
    //其实没用 因为真出错了只能返回给引擎适配层 或log用
    ADAPTER_RETURN_DATA_FAILURE("120", "Return ASR result Failed !"),
    ALREADY_IN_ASR("3212", "This record is already in the process of ASR, please be patient!"),
    AL_INTERNAL_ERROR( "3203", "Adapter processing Error!"),
    DATA_SYNC_FAILURE( "3205", "metricsLog requestError Data sync Failed!"),
    ADAPTER_CONFIG_ERROR("3204", "Adapter config Error!");


    /**
     * 适配层返回码
     */
    private final String transCode;

    /**
     * 适配层响应消息
     */
    private final String msg;

    CodeMappingEnum(String transCode, String msg) {

        this.transCode = transCode;
        this.msg = msg;
    }



    public static CodeMappingEnum getByTransCode(String transErrorCode) {
        for (CodeMappingEnum codeMappingEnum : CodeMappingEnum.values()) {
            if (codeMappingEnum.getTransCode().equals(transErrorCode)) {
                return codeMappingEnum;
            }
        }
        //默认返回9999
        return CodeMappingEnum.REQUEST_FAILED;

    }

    public JsonObject toJson() {
        return new JsonObject()
                .put("code", this.transCode)
                .put("message", this.msg);
    }


    public String getTransCode() {
        return transCode;
    }


    public String getMsg() {
        return msg;
    }
}
