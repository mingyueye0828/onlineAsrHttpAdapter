package shtel.noc.asr.adapter.onlinehttp.handlers.common.exception;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 健康检查异常
 */
public class UnhealthyException extends RuntimeException{
    private static final String MSG = "ASR online adapter unhealthy! Redis maybe get problem!";

    public UnhealthyException() {
        super(MSG, new Throwable(MSG));
    }

    public String getMsgDes() {
        return MSG;
    }
}
