package com.example.playlistmaker.search.model.data.network

import com.example.playlistmaker.search.model.data.dto.Response
import com.example.playlistmaker.search.model.data.dto.TracksSearchRequest
import com.example.playlistmaker.search.model.data.dto.TracksSearchResponse
import retrofit2.Retrofit

class RetrofitNetworkClient(retrofit: Retrofit) : NetworkClient {
    private val iTunesService = retrofit.create(ITunesApi::class.java)
    override fun doRequest(dto: Any): Response {
        if (dto is TracksSearchRequest) {
            val resp: retrofit2.Response<TracksSearchResponse>
            try {
                resp = iTunesService.findTrack(dto.expression).execute()
            } catch (e: Throwable) {
                return Response().apply {
                    resultCode = 400
                }
            }
            if (resp.code() != 200) {
                return Response().apply {
                    resultCode = 404
                }
            }
            val body = resp.body() ?: Response()
            return (body as TracksSearchResponse).apply {
                expression = dto.expression
                resultCode = resp.code()
            }
        } else {
            return Response().apply {
                resultCode = 400
            }
        }
    }
}