package shtel.noc.asr.adapter.onlinehttp.handlers.common.exception;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 下层引擎报错
 */
public class EngineException extends RuntimeException{
    public EngineException(String msg) {
        super(msg, new Throwable(msg));
    }

    public EngineException(String msg, Throwable thrown) {
        super(msg, thrown);
    }

}
