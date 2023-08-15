package djei.clockpanda.scheduling.optimization

import arrow.core.Either
import org.slf4j.Logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class OptimizationCronJob(
    private val optimizationService: OptimizationService,
    private val logger: Logger
) {

    @Scheduled(fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun optimizeSchedule() {
        logger.info("Running optimization cron job")
        when (val optimizationResult = optimizationService.calculateOptimizedSchedule()) {
            is Either.Left -> {
                logger.error(
                    "Optimization run failed: ${optimizationResult.value.message}",
                    optimizationResult.value
                )
            }

            is Either.Right -> {
                logger.info("Optimization run succeeded. Apply it on user calendar")
                optimizationResult.value.forEach {
                    when (val syncResult = optimizationService.syncOptimizedScheduleWithUserCalendar(it)) {
                        is Either.Left -> {
                            logger.error(
                                "Failed to sync user calendar: ${syncResult.value.message}",
                                syncResult.value
                            )
                        }

                        is Either.Right -> {
                            logger.info("User ${it.user.email} calendar synced successfully")
                        }
                    }
                }
            }
        }
        logger.info("Finished optimization cron job")
    }
}
