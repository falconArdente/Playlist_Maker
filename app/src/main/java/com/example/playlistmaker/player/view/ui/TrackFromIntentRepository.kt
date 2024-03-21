package com.example.playlistmaker.player.view.ui

import android.app.Activity
import com.example.playlistmaker.R
import com.example.playlistmaker.player.model.repository.GetTrackToPlayRepository
import com.example.playlistmaker.search.model.domain.Track
import com.google.gson.Gson

class TrackFromIntentRepository(private val activityToGetFrom: Activity) : GetTrackToPlayRepository {
    private val trackKeyConst = activityToGetFrom.getString(R.string.TRACK_KEY)
    override fun getTrack(): Track {
        val json = Gson()
        val tempString = activityToGetFrom.intent.getStringExtra(trackKeyConst)
        return if (!tempString.isNullOrEmpty()) {
            json.fromJson(tempString, Track::class.java)
        } else {
            Track(
                id = "-1", trackTitle = "Intent income error", "", "", "",
                "", "", "", "", ""
            )
        }
    }
}