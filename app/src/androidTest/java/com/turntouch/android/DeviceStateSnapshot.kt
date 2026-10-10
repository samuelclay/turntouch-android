package com.turntouch.android

/** Restore user button assignments and layout after an instrumentation scenario. */
class DeviceStateSnapshot(private val app: AppState) {
    private val prefs=app.context.getSharedPreferences("turntouch",0)
    private val values=prefs.all.toMap()
    private val twelve=app.twelve
    private val selected=app.selected
    private val perform=app.perform
    fun restore() {
        app.changeButtonMode(twelve);app.select(selected);app.changePerformMode(perform)
        val edit=prefs.edit().clear()
        values.forEach {(key,value)->when(value) {
            is String->edit.putString(key,value)
            is Boolean->edit.putBoolean(key,value)
            is Int->edit.putInt(key,value)
            is Long->edit.putLong(key,value)
            is Float->edit.putFloat(key,value)
            is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
        }}
        edit.commit();app.screen="remote"
    }
}
