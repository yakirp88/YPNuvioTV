package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.core.intro.IntroSubmissionMode
import com.nuvio.tv.data.local.IntroDetectionStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IntroDetectionSettingsUiState(
    val profileId: Int = -1,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val error: Boolean = false,
    val snapshot: IntroDetectionStore.Snapshot? = null
)

@HiltViewModel
class IntroDetectionSettingsViewModel @Inject constructor(
    private val store: IntroDetectionStore,
    private val profiles: ProfileManager
) : ViewModel() {
    private val _state = MutableStateFlow(IntroDetectionSettingsUiState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(profiles.activeProfileId, store.revision) { profileId, _ -> profileId }
                .collectLatest { profileId ->
                    if (_state.value.profileId != profileId) {
                        _state.value = IntroDetectionSettingsUiState(profileId = profileId)
                    }
                    try {
                        val snapshot = store.snapshot(profileId)
                        _state.update { it.copy(profileId = profileId, snapshot = snapshot, loading = false) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        _state.update { it.copy(loading = false, error = true) }
                    }
                }
        }
    }

    fun setEnabled(value: Boolean) = mutate { profileId -> store.setEnabled(profileId, value) }

    fun setCloudEnabled(value: Boolean) = mutate { profileId -> store.setCloudEnabled(profileId, value) }

    fun setSubmissionMode(mode: IntroSubmissionMode) = mutate { profileId -> store.setSubmissionMode(profileId, mode) }

    fun saveKey(profileId: Int, ai: Boolean, value: String, onSaved: () -> Unit) {
        // Bind dialogs to the profile that opened them, even if the active profile changes.
        if (profileId != profiles.activeProfileId.value) return
        mutate(onSaved) { capturedId -> store.setKey(capturedId, ai, value) }
    }

    private fun mutate(onSaved: () -> Unit = {}, action: suspend (Int) -> Unit) {
        if (_state.value.loading || _state.value.saving) return
        val profileId = _state.value.profileId
        if (profileId != profiles.activeProfileId.value) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = false) }
            try {
                action(profileId)
                if (_state.value.profileId == profileId) onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (_state.value.profileId == profileId) _state.update { it.copy(error = true) }
            } finally {
                if (_state.value.profileId == profileId) _state.update { it.copy(saving = false) }
            }
        }
    }
}
