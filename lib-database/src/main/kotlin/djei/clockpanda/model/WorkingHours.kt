package djei.clockpanda.model

import kotlinx.datetime.DayOfWeek

typealias WorkingHours = Map<DayOfWeek, List<LocalTimeSpan>>
