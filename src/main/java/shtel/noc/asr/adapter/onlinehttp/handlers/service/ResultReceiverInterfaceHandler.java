package shtel.noc.asr.adapter.onlinehttp.handlers.service;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.HealthyCheck;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.RedisHandler;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.ResponseBody;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.entity.ResultReceived;
import shtel.noc.asr.adapter.onlinehttp.handlers.common.exception.UnhealthyException;
import shtel.noc.asr.adapter.onlinehttp.utils.Constants;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/22
 * @annotation 这里主要用来异步接收下层引擎发送的结果，将其放入Redis
 */
@Slf4j
public class ResultReceiverInterfaceHandler implements Handler<RoutingContext> {

    public ResultReceiverInterfaceHandler(){}

    @Override
    public void handle(RoutingContext context) {

        if (!HealthyCheck.isHealthy()) {
           context.fail(new UnhealthyException());
        }

        //下层引擎发送的结果，我直接回复掉
        context.response().end(Constants.successResponse.encode());

        //将接收的字段进行类映射，放入redis中,然后判断进行日志打印
        JsonObject requestBody = context.getBody().toJsonObject();
        ResultReceived resultReceived = requestBody.mapTo(ResultReceived.class);
        String uid = resultReceived.getUid();
        if (resultReceived.getStatus()==1) {
            log.info("Received the first field from the engine");
        }else if (resultReceived.getStatus()==4) {
            log.info("Received the last field from the engine");
        }
        RedisHandler.setReceiverResult(uid, requestBody, true);
    }
}
