package shtel.noc.asr.adapter.onlinehttp.handlers.common.handler;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.json.schema.ValidationException;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.utils.CodeMappingEnum;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation 捕获抛出的异常，并进行响应，http统一错误处理类（注释掉了性能统计）
 *
 * todo: 这里可能需要增加并发异常等等处理
 */
@Slf4j
public class FailureHandler implements Handler<RoutingContext> {
    @Override
    public void handle(RoutingContext context) {
        Throwable thrown = context.failure();

        //参数校验错误
        if (thrown instanceof ValidationException) {
            log.info("Request parameters validation failed,{}", thrown.getMessage());
            context.response().end(new JsonObject().put("code", CodeMappingEnum.PARAMETER_ERROR.getTransCode())
                    .put("message", CodeMappingEnum.PARAMETER_ERROR.getMsg()).encodePrettily());
        } else {
            log.error("Request failed,{}", thrown.getCause().getMessage(), thrown);
            context.response().end(new JsonObject().put("code", CodeMappingEnum.REQUEST_FAILED.getTransCode())
                    .put("message", CodeMappingEnum.REQUEST_FAILED.getMsg()).encodePrettily());
        }
    }
}
