package djei.clockpanda.scheduling.optimization

import djei.clockpanda.scheduling.model.TimeSpan
import djei.clockpanda.scheduling.optimization.model.OptimizationProblem
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OptimizationProblemTest {

    @Test
    fun `test constructor validates start before end`() {
        val e = assertThrows<IllegalArgumentException> {
            OptimizationProblem(
                parametrization = OptimizationProblem.OptimizationProblemParametrization(
                    optimizationRange = TimeSpan(
                        start = Instant.parse("2021-01-01T00:00:00Z"),
                        end = Instant.parse("2020-12-31T00:00:00Z")
                    )
                ),
                schedule = emptyList(),
                users = emptyList()
            )
        }

        assertThat(e.message).isEqualTo("Optimization range start must be before end")
    }

    @Test
    fun `test constructor validates start is at start of day in UTC`() {
        val e1 = assertThrows<IllegalArgumentException> {
            OptimizationProblem(
                parametrization = OptimizationProblem.OptimizationProblemParametrization(
                    optimizationRange = TimeSpan(
                        start = Instant.parse("2021-01-01T05:00:00Z"),
                        end = Instant.parse("2021-01-31T00:00:00Z")
                    )
                ),
                schedule = emptyList(),
                users = emptyList()
            )
        }
        val e2 = assertThrows<IllegalArgumentException> {
            OptimizationProblem(
                parametrization = OptimizationProblem.OptimizationProblemParametrization(
                    optimizationRange = TimeSpan(
                        start = Instant.parse("2021-01-01T00:05:00Z"),
                        end = Instant.parse("2021-01-31T00:00:00Z")
                    )
                ),
                schedule = emptyList(),
                users = emptyList()
            )
        }

        assertThat(e1.message).isEqualTo("Optimization range start must be at start of day in UTC")
        assertThat(e2.message).isEqualTo("Optimization range start must be at start of day in UTC")
    }

    @Test
    fun `test constructor validates end is at start of day in UTC`() {
        val e1 = assertThrows<IllegalArgumentException> {
            OptimizationProblem(
                parametrization = OptimizationProblem.OptimizationProblemParametrization(
                    optimizationRange = TimeSpan(
                        start = Instant.parse("2021-01-01T00:00:00Z"),
                        end = Instant.parse("2021-01-31T05:00:00Z")
                    )
                ),
                schedule = emptyList(),
                users = emptyList()
            )
        }
        val e2 = assertThrows<IllegalArgumentException> {
            OptimizationProblem(
                parametrization = OptimizationProblem.OptimizationProblemParametrization(
                    optimizationRange = TimeSpan(
                        start = Instant.parse("2021-01-01T00:00:00Z"),
                        end = Instant.parse("2021-01-31T00:05:00Z")
                    )
                ),
                schedule = emptyList(),
                users = emptyList()
            )
        }

        assertThat(e1.message).isEqualTo("Optimization range end must be at start of day in UTC")
        assertThat(e2.message).isEqualTo("Optimization range end must be at start of day in UTC")
    }

    @Test
    fun `test getStartTimeGrainRange return proper values`() {
        val optimizationProblem = OptimizationProblem(
            parametrization = OptimizationProblem.OptimizationProblemParametrization(
                optimizationRange = TimeSpan(
                    start = Instant.parse("2021-01-01T00:00:00Z"),
                    end = Instant.parse("2021-01-15T00:00:00Z")
                )
            ),
            schedule = emptyList(),
            users = emptyList()
        )

        val result = optimizationProblem.getStartTimeGrainRange()

        // 96 15-minutes time grains per day
        assertThat(result.size).isEqualTo(96 * 14)
        // Check that all time grains are at the 15 minutes mark
        assertThat(result.all { it.start.toLocalDateTime(TimeZone.UTC).minute in listOf(0, 15, 30, 45) })
        // Check that all time grains are different
        assertThat(result.distinct().size).isEqualTo(result.size)
    }
}
