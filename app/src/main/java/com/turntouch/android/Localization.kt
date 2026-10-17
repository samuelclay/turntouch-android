package com.turntouch.android

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.util.Locale

/** Only application-owned display messages belong here. Device names and action IDs stay literal. */
object Localization {
    private lateinit var context: Context
    private var screenshotLanguage: String? = null
    private var messages: Map<String,String> = emptyMap()
    var revision by mutableIntStateOf(0); private set
    var selection by mutableStateOf("auto"); private set
    var code by mutableStateOf("en"); private set
    val languages: List<Pair<String,String>> by lazy {
        val json=JSONObject(context.assets.open("localization/languages.json").bufferedReader().use { it.readText() })
        json.keys().asSequence().map { it to json.getString(it) }.toList()
    }
    private val prefs get()=context.getSharedPreferences("turntouch-language",Context.MODE_PRIVATE)
    val needsWelcome get()=!prefs.getBoolean("welcomed",false)
    fun initialize(context: Context) { this.context=context.applicationContext; refresh() }
    fun resolve(preferred: List<String>)=LanguageResolver.resolve(preferred,languages.map { it.first }.toSet())
    fun refresh() {
        selection=prefs.getString("language","auto") ?: "auto"
        val system=android.content.res.Resources.getSystem().configuration.locales.toLanguageTags().split(',')
        val platform=if(Build.VERSION.SDK_INT>=33)context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags() else ""
        if(Build.VERSION.SDK_INT>=33)selection=platform.ifBlank { "auto" }
        code=if(selection=="auto")resolve(system) else resolve(selection.split(','))
        screenshotLanguage?.let { code=it }
        val json=JSONObject(context.assets.open("localization/$code.json").bufferedReader().use { it.readText() })
        messages=json.keys().asSequence().associateWith { json.getString(it) }
        revision++
    }
    internal fun previewLanguage(value: String?) {
        check(BuildConfig.DEBUG)
        require(value==null || languages.any { it.first==value })
        screenshotLanguage=value
        refresh()
    }
    fun choose(value: String) {
        require(value=="auto" || languages.any { it.first==value })
        prefs.edit().putString("language",value).putBoolean("welcomed",true).apply()
        if(Build.VERSION.SDK_INT>=33)context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=LocaleList.forLanguageTags(if(value=="auto")"" else value)
        refresh()
    }
    fun text(source: String,vararg args: Any?): String {
        revision // Compose observes language changes, including catalog getters.
        val template=messages[source] ?: source
        return Regex("\\{(\\d+)\\}").replace(template) { m->args.getOrNull(m.groupValues[1].toInt())?.toString() ?: m.value }
    }
    val rtl get()=code in listOf("ar","he")
    fun name()=if(selection=="auto")text("Automatic (device language)") else languages.firstOrNull { it.first==code }?.second ?: "English"
}
fun tr(source: String,vararg args: Any?)=Localization.text(source,*args)

@Composable fun LanguageDialog(onDismiss: ()->Unit,firstLaunch: Boolean=false) {
    var selected by remember { mutableStateOf(Localization.selection) }
    AlertDialog(onDismissRequest={if(!firstLaunch)onDismiss()},
        title={Text(tr(if(firstLaunch)"Welcome to Turn Touch" else "Language"))},
        text={Column {
            Text(tr("Choose your language. You can change it anytime in Settings."))
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                (listOf("auto" to tr("Automatic (device language)"))+Localization.languages).forEach { (code,name)->
                    Row(Modifier.fillMaxWidth().clickable { selected=code }.padding(vertical=4.dp)) {
                        RadioButton(selected==code,onClick={selected=code})
                        Text(name,Modifier.padding(top=12.dp,start=8.dp))
                    }
                }
            }
        }},
        confirmButton={TextButton(onClick={Localization.choose(selected);onDismiss()}) { Text(tr("Continue")) }},
        dismissButton={if(!firstLaunch)TextButton(onClick=onDismiss) { Text(tr("Cancel")) }})
}
