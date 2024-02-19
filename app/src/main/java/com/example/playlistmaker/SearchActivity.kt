package com.example.playlistmaker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.playlistmaker.databinding.ActivitySearchBinding
import com.google.gson.Gson
import kotlinx.coroutines.Runnable
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Calendar.*

class SearchActivity : AppCompatActivity() {
    companion object {
        const val SEARCH_PROMPT = "PROMPT"
        const val SEARCH_DEF = ""
        const val TRACK_KEY = "track"
        private const val AUTO_SEND_REQUEST_DELAY = 2000L
        private const val CHOICE_DEBOUNCE_DELAY = 1000L

        enum class State {
            History,
            CleanHistory,
            SearchGot,
            SearchIsEmpty,
            Error,
            WaitingForResponse,
        }
    }

    private lateinit var iTunesService: ITunesApi
    private var uiHandler: Handler? = null
    private var choiceTimeStamp: Long = 0L
    private val searchTracks: ArrayList<Track> = arrayListOf()
    private lateinit var history: SearchHistory
    private var historyTracks: ArrayList<Track> = arrayListOf()
    private var trackOnClickListener = object : TrackOnClickListener {
        override fun onClick(item: Track) {
            if (canMakeAChoice()) {
                (applicationContext as App).history.addTrack(item)
                val intent =
                    Intent(this@SearchActivity, PlayerActivity::class.java)
                val json = Gson()
                intent.putExtra(TRACK_KEY, json.toJson(item))
                ContextCompat.startActivity(this@SearchActivity, intent, null)
            }
        }
    }
    private val tracksAdapter = TrackAdapter(historyTracks, trackOnClickListener)
    private var searchPromptString: String = ""
    private lateinit var binding: ActivitySearchBinding
    private fun canMakeAChoice(): Boolean {
        val currentTime = getInstance().get(MILLISECOND).toLong()
        return if ((currentTime - choiceTimeStamp) < CHOICE_DEBOUNCE_DELAY) {
            choiceTimeStamp = currentTime
            true
        } else false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        uiHandler = Handler(Looper.getMainLooper())
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)
        iTunesService = Utility.initItunesService(this)
        history = (applicationContext as App).history
        historyTracks = history.tracks
        backButtonClickAttach()
        searchBarTextWatcherAttach()
        searchBarSetActionDone()//temporary use only
        clearTextAttach()
        startUpViewHolder()
        binding.updateButton.setOnClickListener { uiHandler?.post(sendRequest) }
        clearHistoryButtonClickAttach()
    }

    override fun onResume() {
        super.onResume()
        if (binding.searchBar.text.isNullOrEmpty()) {
            binding.clearIcon.visibility = View.GONE
            history.getFromVault()
            if (historyTracks.isEmpty()) showLayout(State.CleanHistory)
            else showLayout(State.History)
        }
    }

    override fun onSaveInstanceState(outState: Bundle, outPersistentState: PersistableBundle) {
        super.onSaveInstanceState(outState, outPersistentState)
        outState.putString(SEARCH_PROMPT, searchPromptString)
    }

    override fun onRestoreInstanceState(
        savedInstanceState: Bundle?,
        persistentState: PersistableBundle?
    ) {
        super.onRestoreInstanceState(savedInstanceState, persistentState)
        binding.searchBar.setText(
            savedInstanceState?.getString(SEARCH_PROMPT)
                ?: SEARCH_DEF
        )
    }

    @SuppressLint("NotifyDataSetChanged")
    fun showLayout(state: State) {
        when (state) {
            State.SearchGot -> {
                binding.progressBar.isVisible = false
                binding.beenSearchedTitle.isVisible = false
                binding.clearSearchList.isVisible = false
                binding.placeholderFrame.isVisible = false
                if (tracksAdapter.tracks != searchTracks) {
                    tracksAdapter.tracks = searchTracks
                    tracksAdapter.notifyDataSetChanged()
                }
                binding.tracksRecyclerView.visibility = View.VISIBLE
            }

            State.SearchIsEmpty -> {
                binding.progressBar.isVisible = false
                binding.beenSearchedTitle.isVisible = false
                binding.clearSearchList.isVisible = false
                binding.updateButton.isVisible = false
                binding.tracksRecyclerView.isVisible = false
                binding.statusText.text = this.getString(R.string.search_status_nothing)
                binding.statusImage.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.image_sad_smile_mus
                    )
                )
                binding.placeholderFrame.visibility = View.VISIBLE
            }

            State.Error -> {
                binding.progressBar.isVisible = false
                binding.beenSearchedTitle.isVisible = false
                binding.clearSearchList.isVisible = false
                binding.tracksRecyclerView.isVisible = false
                binding.statusText.text =
                    applicationContext.getString(R.string.search_status_connection_problem)
                binding.statusImage.setImageDrawable(
                    AppCompatResources.getDrawable(
                        this,
                        R.drawable.image_no_wifi_mus
                    )
                )
                binding.updateButton.visibility = View.VISIBLE
                binding.placeholderFrame.visibility = View.VISIBLE
            }

            State.History -> {
                binding.progressBar.isVisible = false
                binding.placeholderFrame.isVisible = false
                binding.updateButton.isVisible = false
                if (tracksAdapter.tracks != historyTracks) {
                    tracksAdapter.tracks = historyTracks
                    tracksAdapter.notifyDataSetChanged()
                }
                binding.beenSearchedTitle.visibility = View.VISIBLE
                binding.clearSearchList.visibility = View.VISIBLE
                binding.tracksRecyclerView.visibility = View.VISIBLE
            }

            State.CleanHistory -> {
                binding.progressBar.isVisible = false
                binding.placeholderFrame.isVisible = false
                binding.updateButton.isVisible = false
                binding.beenSearchedTitle.isVisible = false
                binding.clearSearchList.isVisible = false
                binding.tracksRecyclerView.isVisible = false
            }

            State.WaitingForResponse -> {
                binding.placeholderFrame.isVisible = false
                binding.updateButton.isVisible = false
                binding.beenSearchedTitle.isVisible = false
                binding.clearSearchList.isVisible = false
                binding.tracksRecyclerView.isVisible = false
                binding.progressBar.visibility = View.VISIBLE
            }
        }
    }

    private val sendRequest: Runnable = object : Runnable {
        override fun run() {
            uiHandler?.removeCallbacks(this)
            showLayout(State.WaitingForResponse)
            if (binding.searchBar.text.isNotEmpty()) {
                iTunesService.findTrack(binding.searchBar.text.toString()).enqueue(object :
                    Callback<TrackSearchResponse> {
                    @SuppressLint("NotifyDataSetChanged")
                    override fun onResponse(
                        call: Call<TrackSearchResponse>,
                        response: Response<TrackSearchResponse>
                    ) {
                        Log.e("servRequests", "$response.code()")
                        if (response.code() == 200) {
                            searchTracks.clear()
                            if (response.body()?.results?.isNotEmpty() == true) {
                                searchTracks.addAll(response.body()?.results ?: emptyList())
                                tracksAdapter.notifyDataSetChanged()
                            }
                            if (searchTracks.isEmpty()) {///!!!!
                                showLayout(State.SearchIsEmpty)
                            } else {
                                showLayout(State.SearchGot)
                            }
                        } else {
                            showLayout(State.Error)
                        }
                    }

                    override fun onFailure(call: Call<TrackSearchResponse>, t: Throwable) {
                        showLayout(State.Error)
                    }
                })
            }
        }

    }

    private fun searchBarSetActionDone() {
        binding.searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                uiHandler?.post(sendRequest)
            }
            false
        }
    }

    private fun clearHistoryButtonClickAttach() {
        binding.clearSearchList.setOnClickListener {
            history.clear()
            showLayout(State.CleanHistory)
        }
    }

    private fun clearTextAttach() {
        binding.clearIcon.setOnClickListener {
            binding.searchBar.setText("")
            this.currentFocus?.let { view ->
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(view.windowToken, 0)
            }
        }
    }

    private fun searchBarTextWatcherAttach() {
        val searchBarTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    binding.clearIcon.visibility = View.GONE
                } else {
                    binding.clearIcon.visibility = View.VISIBLE
                    uiHandler?.postDelayed(sendRequest, AUTO_SEND_REQUEST_DELAY)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                searchPromptString = binding.searchBar.text.toString()
                if (searchPromptString.isEmpty()) showLayout(State.History)
            }
        }
        binding.searchBar.addTextChangedListener(searchBarTextWatcher)
    }

    private fun backButtonClickAttach() {
        val header = findViewById<androidx.appcompat.widget.Toolbar>(R.id.header)
        header.setNavigationOnClickListener { finish() }
    }

    private fun startUpViewHolder() {
        binding.tracksRecyclerView.adapter = tracksAdapter
        binding.tracksRecyclerView.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
    }
}