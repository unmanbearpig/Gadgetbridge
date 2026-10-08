package nodomain.freeyourgadget.gadgetbridge.widgets

import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.*
import org.junit.Test

class WidgetLayoutStoreTest : TestBase() {
    @Test fun replacesPersistedRingsWithoutLosingOtherInstances() {
        WidgetLayoutStore.save(listOf(WidgetInstance("custom-today", "today", 1),
            WidgetInstance("custom-goals", "goals", 2), WidgetInstance("sleep", "sleep", 1)))
        val migrated = WidgetLayoutStore.load()
        assertEquals(listOf("hr_history", "activity_history", "sleep"), migrated.map { it.typeId })
        assertEquals(listOf("custom-today", "custom-goals", "sleep"), migrated.map { it.instanceId })
        assertEquals(listOf(2, 2, 1), migrated.map { it.columns })
        assertEquals(migrated, WidgetLayoutStore.load())
    }

    @Test fun freshLayoutStartsWithChartsAndEmptyLayoutStaysEmpty() {
        GBApplication.getPrefs().preferences.edit().remove("pref_dashboard_layout")
            .remove("pref_dashboard_widgets_order").commit()
        assertEquals(listOf("hr_history", "activity_history"), WidgetLayoutStore.load().take(2).map { it.typeId })
        WidgetLayoutStore.save(emptyList())
        assertTrue(WidgetLayoutStore.load().isEmpty())
    }
}
