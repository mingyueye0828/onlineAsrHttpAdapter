package shtel.noc.asr.adapter.onlinehttp.handlers.common;

import io.vertx.core.Vertx;
import lombok.extern.slf4j.Slf4j;
import shtel.noc.asr.adapter.onlinehttp.handlers.processor.SessionController;
import shtel.noc.asr.adapter.onlinehttp.utils.Constants;
import shtel.noc.asr.adapter.onlinehttp.utils.EventBusChannels;

/**
 * @author JWZ
 * @version 1.0
 * @date 2023/12/19
 * @annotation
 */
@Slf4j
public class PeriodJobs {

    private final Vertx vertx;
    private final SessionController sessionController;


    public PeriodJobs(Vertx vertx, SessionController sessionController) {
        this.vertx = vertx;
        this.sessionController = sessionController;

        vertx.eventBus().<Void>consumer(EventBusChannels.PERIOD_JOB_RUN.name()).handler(voidMessage -> periodJobStart());


    }

    private void periodJobStart() {

        periodUpdateConfigFromRedis(Constants.CONFIG_UPDATE_PERIOD);
        appSessionCleaner(Constants.HEALTH_CHECK_PERIOD);
        periodCheck(Constants.HEALTH_CHECK_PERIOD);

    }


    /***
     * 周期检查应用状态ll
     * @param period 检查周期
     */
    public void periodCheck(Long period) {
        vertx.setPeriodic(period, rs -> HealthyCheck.checkHealthy(vertx)
                .onFailure(res -> log.warn("Healthy check failed!", res.getCause())));
    }


    /**
     * 周期检查应用并发状态
     *
     * @param period 检查周期
     */
    public void appSessionCleaner(Long period) {
        vertx.setPeriodic(period, rs -> sessionController.appSessionCleaner());
    }


    /**
     * 周期检查并更新redis内的配置
     *
     * @param period 检查周期
     */
    public void periodUpdateConfigFromRedis(Long period) {
        vertx.setPeriodic(period, rs -> ConfigStore.getAndSetConcurrencyLimit());
    }
}
