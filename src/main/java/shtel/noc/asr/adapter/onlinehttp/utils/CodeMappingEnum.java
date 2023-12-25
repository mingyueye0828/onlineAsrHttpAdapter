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
    PARAMETER_ERROR("122", "3201", "Parameters Error!"),
    ADAPTER_CONFIG_ERROR("130", "3204", "Adapter config Error!"),
    QUERY_OUT_OF_RANGE("123", "3202", "Required audio info not in the list!"),
    AL_INTERNAL_ERROR("101", "3203", "Adapter processing Error!"),
    DATA_SYNC_FAILURE("119", "3205", "metricsLog requestError Data sync Failed!"),
    ENGINE_GENERAL_FAILURE("110", "3299", "ASR Engine - General Failure!"),
    ENGINE_CONCURRENCY_FULL("111", "3211", "ASR Engine is full, request Rejected!"),
    ALREADY_IN_ASR("112", "3212", "This record is already in the process of ASR, please be patient!"),

    //引擎适配层会获得到的错误码
    //其实没用 因为真出错了只能返回给引擎适配层 或log用
    ADAPTER_RETURN_DATA_FAILURE("120", "120", "Return ASR result Failed !"),

    //Redis错误码
    REDIS_COMMUNICATION_ERROR("555", "5555", "metricsLog requestError Redis unreachable!"),

    //通用成功码
    SUCCESS("0", "0", "success"),
    //通用错误码
    REQUEST_FAILED("999", "9999", "metricsLog requestError Undefined response Error!");

    /**
     * 实时转写引擎返回码
     */
    private final String asrOnlineCode;

    /**
     * 适配层返回码
     */
    private final String transCode;

    /**
     * 适配层响应消息
     */
    private final String msg;

    CodeMappingEnum(String asrOnlineCode, String transCode, String msg) {
        this.asrOnlineCode = asrOnlineCode;
        this.transCode = transCode;
        this.msg = msg;
    }

    public static Map<String, String> getByASRCode(String asrOnlineErrorCode) {
        Map<String, String> map = new HashMap<>();
        for (CodeMappingEnum codeMappingEnum : CodeMappingEnum.values()) {
            if (codeMappingEnum.getAsrOnlineCode().equals(asrOnlineErrorCode)) {
                map.put("errorCode", codeMappingEnum.getTransCode());
                map.put("msg", codeMappingEnum.getMsg());
                return map;
            }
        }
        map.put("errorCode", "9999");
        map.put("msg", "undefined response error!");
        return map;


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

    public String getAsrOnlineCode() {
        return asrOnlineCode;
    }

    public String getMsg() {
        return msg;
    }
}
