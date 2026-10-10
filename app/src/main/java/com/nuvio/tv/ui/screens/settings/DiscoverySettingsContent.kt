package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Text
import com.nuvio.tv.data.local.DiscoveryPreferences
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.ui.screens.discovery.Action
import com.nuvio.tv.ui.screens.discovery.DiscoveryChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

@HiltViewModel
class DiscoverySettingsViewModel @Inject constructor(val preferences: DiscoveryPreferences,private val api:TmdbApi,private val tmdb:TmdbService):ViewModel() {
    val missing=preferences.includeMissing.stateIn(viewModelScope,SharingStarted.Eagerly,false)
    val languages=preferences.languages.stateIn(viewModelScope,SharingStarted.Eagerly,listOf("he","en","original"))
    val choices=MutableStateFlow(listOf(DiscoveryChoice("original","שפת המקור"),DiscoveryChoice("he","עברית"),DiscoveryChoice("en","English")))
    val apiKey=preferences.preferences.map{it[androidx.datastore.preferences.core.stringPreferencesKey("tmdb_key")].orEmpty()}.stateIn(viewModelScope,SharingStarted.Eagerly,"")
    init {viewModelScope.launch {
        preferences.preferences.map { it[androidx.datastore.preferences.core.stringPreferencesKey("tmdb_key")].orEmpty() }.distinctUntilChanged().collectLatest { key ->
            try {
                choices.value=(choices.value+api.discoveryLanguages(key.ifBlank{tmdb.apiKey()}).body().orEmpty().map{DiscoveryChoice(it.code,it.name.ifBlank{it.englishName})}).distinctBy{it.id}
            } catch(e:CancellationException){throw e}catch(_:Exception){}
        }
    }}
    fun missing(){viewModelScope.launch{preferences.setIncludeMissing(!missing.value)}}
    fun language(index:Int,value:String){viewModelScope.launch{preferences.setLanguage(index,value)}}
    fun key(value:String){viewModelScope.launch{preferences.save("tmdb_key",value.trim())}}
}

@Composable
internal fun DiscoverySettingsContent(initialFocusRequester: FocusRequester? = null, vm:DiscoverySettingsViewModel=hiltViewModel()) {
    val missing by vm.missing.collectAsState()
    val languages by vm.languages.collectAsState()
    val choices by vm.choices.collectAsState()
    var selecting by remember{mutableStateOf<Int?>(null)}
    var query by remember{mutableStateOf("")}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SettingsToggleRow("כלול כותרים עם מידע חסר","חל כאשר חסר נתון שנדרש לפילטר פעיל",missing,vm::missing,
            modifier=initialFocusRequester?.let{Modifier.focusRequester(it)} ?: Modifier)
        languages.forEachIndexed{index,value ->
            SettingsActionRow(title=listOf("שפה ראשית","שפה משנית","שפה שלישית")[index],subtitle="שפת שמות התוכן · עדיפות ${index+1}",
                value=choices.find{it.id==value}?.name ?: value,onClick={selecting=index;query=""})
        }
        if(selecting!=null) {
            OutlinedTextField(query,{query=it},singleLine=true,label={Text("חיפוש שפה")})
            LazyColumn(Modifier.heightIn(max=160.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                items(choices.filter{it.name.contains(query,true)||it.id.contains(query,true)}) {c -> Action(c.name,{vm.language(selecting!!,c.id);selecting=null})}
            }
        }
    }
}

@Composable
internal fun TmdbApiKeySetting(vm:DiscoverySettingsViewModel=hiltViewModel()) {
    val savedKey by vm.apiKey.collectAsState()
    var key by remember(savedKey){mutableStateOf(savedKey)}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),singleLine=true,
            visualTransformation=PasswordVisualTransformation(),label={Text("TMDB API key · אופציונלי")})
        Action("שמור מפתח",{vm.key(key)})
        Text("מפתח משותף לאינטגרציית TMDB ולתוכן וגילוי",color=com.nuvio.tv.ui.theme.NuvioTheme.colors.TextSecondary)
    }
}
