package shtel.noc.asr.adapter.onlinehttp.handlers.common.handler;

import io.vertx.core.Handler;
import lombok.extern.slf4j.Slf4j;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 其他异常处理:各类非捕获异常打印
 */
@Slf4j
public class ExceptionHandler implements Handler<Throwable> {

    @Override
    public void handle(Throwable event) {
        // 暂时打印错误信息
        log.error("Exception: ", event);
    }
}
