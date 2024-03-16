package com.example.playlistmaker.search.domain

data class Track(
    val id: String,
    val trackTitle: String,
    val artistName: String,
    val duration: String,
    val artwork: String,
    val collectionName: String,
    val releaseDate: String,
    val genre: String,
    val country: String,
    val trackPreview:String,
)