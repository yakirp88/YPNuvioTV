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
    val choices=MutableStateFlow(listOf(DiscoveryChoice("original","שפת המקור"),DiscoveryChoice("he","Hebrew"),DiscoveryChoice("en","English")))
    val position=preferences.infoPosition.stateIn(viewModelScope,SharingStarted.Eagerly,com.nuvio.tv.ui.screens.discovery.DiscoveryInfoPosition.TOP)
    val expandCards=preferences.expandCards.stateIn(viewModelScope,SharingStarted.Eagerly,true)
    fun expandCards(){viewModelScope.launch{preferences.setExpandCards(!expandCards.value)}}
    val expansionDelay=preferences.expansionDelay.stateIn(viewModelScope,SharingStarted.Eagerly,3)
    fun position(value:com.nuvio.tv.ui.screens.discovery.DiscoveryInfoPosition){viewModelScope.launch{preferences.setInfoPosition(value)}}
    fun expansionDelay(value:Int){viewModelScope.launch{preferences.setExpansionDelay(value)}}
    init {viewModelScope.launch {
        preferences.preferences.map { it[androidx.datastore.preferences.core.stringPreferencesKey("tmdb_key")].orEmpty() }.distinctUntilChanged().collectLatest { key ->
            try {
                choices.value=(choices.value+api.discoveryLanguages(key.ifBlank{tmdb.apiKey()}).body().orEmpty().map{DiscoveryChoice(it.code,it.englishName.ifBlank{it.name})}).distinctBy{it.id}
            } catch(e:CancellationException){throw e}catch(_:Exception){}
        }
    }}
    fun missing(){viewModelScope.launch{preferences.setIncludeMissing(!missing.value)}}
    fun language(index:Int,value:String){viewModelScope.launch{preferences.setLanguage(index,value)}}
}

@Composable
internal fun DiscoverySettingsContent(initialFocusRequester: FocusRequester? = null, vm:DiscoverySettingsViewModel=hiltViewModel(), layoutVm:LayoutSettingsViewModel=hiltViewModel()) {
    val position by vm.position.collectAsState()
    val expansionDelay by vm.expansionDelay.collectAsState()
    var selectingPosition by remember { mutableStateOf(false) }
    val positions=listOf("מודרני — למעלה","מודרני — צד")
    val expandCards by vm.expandCards.collectAsState()
    val layout by layoutVm.uiState.collectAsState()
    val missing by vm.missing.collectAsState()
    val languages by vm.languages.collectAsState()
    val choices by vm.choices.collectAsState()
    var selecting by remember{mutableStateOf<Int?>(null)}
    var query by remember{mutableStateOf("")}
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SettingsToggleRow("כלול כותרים עם מידע חסר","חל כאשר חסר נתון שנדרש לפילטר פעיל",missing,vm::missing,
            modifier=initialFocusRequester?.let{Modifier.focusRequester(it)} ?: Modifier)
        DiscoverLocationRow(layout.discoverLocation,layout.lastNonOffDiscoverLocation) {layoutVm.onEvent(LayoutSettingsEvent.SetDiscoverLocation(it))}
        SettingsActionRow(title="מבנה תצוגת תוכן וגילוי",subtitle="מידע קבוע למעלה או בשליש השמאלי של המסך",value=positions[position.ordinal],onClick={selectingPosition=!selectingPosition})
        if(selectingPosition) com.nuvio.tv.ui.screens.discovery.DiscoveryInfoPosition.entries.forEach { option ->
            Action(positions[option.ordinal],{vm.position(option);selectingPosition=false},position==option)
        }
        SettingsToggleRow("הרחבת תמונת הכותר","הרחבת הכרטיס המסומן לאחר השהיה, בלי להסתיר את שאר השורה",expandCards,vm::expandCards)
        if(expandCards) SliderSettingsItem(
            title="השהיה לפני הרחבת תמונת הכותר",subtitle="0 שניות — הרחבה מיידית",value=expansionDelay,valueText="${expansionDelay}s",minValue=0,maxValue=10,step=1,onValueChange=vm::expansionDelay)
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
