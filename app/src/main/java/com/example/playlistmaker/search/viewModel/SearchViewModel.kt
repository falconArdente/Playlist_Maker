package com.example.playlistmaker.search.viewModel

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.playlistmaker.search.model.domain.ErrorConsumer
import com.example.playlistmaker.search.model.domain.HistoryInteractor
import com.example.playlistmaker.search.model.domain.SearchInteractor
import com.example.playlistmaker.search.model.domain.SendTrackToPlayerUseCase
import com.example.playlistmaker.search.model.domain.Track
import com.example.playlistmaker.search.model.domain.TracksConsumer

private const val AUTO_SEND_REQUEST_DELAY = 2000L

class SearchViewModel(
    private val history: HistoryInteractor,
    private val search: SearchInteractor,
    private val trackToPlayer: SendTrackToPlayerUseCase
) : ViewModel() {
    private val screenState = MutableLiveData<SearchScreenState>(SearchScreenState.Loading)
    private var searchPrompt: String = ""
    private val handler = Handler(Looper.getMainLooper()).apply { showHistory() }
    fun getScreenState(): LiveData<SearchScreenState> = screenState
    fun doSearchTracks(prompt: String) {
        if (prompt.isNotEmpty()) {
            this.searchPrompt = prompt
            handler.post(doSearchTracksRunnable)
        } else {
            showHistory()
        }
    }

    private fun showHistory() {
        val tracks = history.getTracksHistory()
        screenState.postValue(
            if (tracks.isEmpty()) SearchScreenState.HistoryIsEmpty
            else SearchScreenState.HistoryHaveData(tracks)
        )
    }

    fun doAutoSearchTracks(prompt: String) {
        searchPrompt = prompt
        handler.postDelayed(doSearchTracksRunnable, AUTO_SEND_REQUEST_DELAY)
    }

    fun doRepeatSearch() {
        handler.post(doSearchTracksRunnable)
    }

    fun addTrackToHistory(track: Track) {
        history.addTrackToHistory(track)
    }

    fun doClearHistory() {
        screenState.postValue(SearchScreenState.HistoryIsEmpty)
        history.clearHistory()
    }

    val searchConsumer = object : TracksConsumer {
        override fun consume(foundTracks: List<Track>) {
            screenState.postValue(
                if (foundTracks.isEmpty()) SearchScreenState.ResultIsEmpty
                else SearchScreenState.ResultHaveData(foundTracks)
            )
        }
    }
    val searchErrorConsumer = object : ErrorConsumer {
        override fun consume() {
            screenState.postValue(SearchScreenState.Error)
        }
    }

    private val doSearchTracksRunnable = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            screenState.postValue(SearchScreenState.Loading)
            if (searchPrompt.isNotEmpty()) search.searchTracks(
                expression = searchPrompt,
                trackConsumer = searchConsumer,
                errorConsumer = searchErrorConsumer
            )
        }
    }

    fun openTrack(trackToOpen: Track) {
        trackToPlayer.sendToPlayer(trackToOpen)
    }

    override fun onCleared() {
        handler.removeCallbacks(doSearchTracksRunnable)
        super.onCleared()
    }
}