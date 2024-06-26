package com.example.playlistmaker.media.model.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.playlistmaker.media.model.data.db.entity.PlaylistEntity
import com.example.playlistmaker.media.model.data.db.entity.PlaylistTrackEntity
import com.example.playlistmaker.media.model.data.db.entity.PlaylistsWithCount
import com.example.playlistmaker.media.model.data.db.entity.PlaylistsWithTracks
import kotlinx.coroutines.flow.Flow

private const val PLAYLIST_TABLE = "playlists_table"
private const val TRACKS_TABLE = "playlists_tracks_table"

@Dao
interface PlaylistsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun createPlaylist(playlist: PlaylistEntity)

    @Delete(PlaylistEntity::class)
    suspend fun deletePlaylistEntity(playlist: PlaylistEntity): Int

    @Update(entity = PlaylistEntity::class, onConflict = OnConflictStrategy.REPLACE)
    fun updatePlaylist(playlistEntity: PlaylistEntity): Int

    @Query("SELECT * FROM $PLAYLIST_TABLE ORDER BY $PLAYLIST_TABLE.playlistId DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM $PLAYLIST_TABLE WHERE playlistId =:playlistId")
    fun getPlaylistsById(playlistId: Int): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM $PLAYLIST_TABLE WHERE playlistId =:playlistId")
    fun getFirstPlaylistById(playlistId: Int): PlaylistEntity

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun addPTrack(playlistTrackEntity: PlaylistTrackEntity)

    @Delete(PlaylistTrackEntity::class)
    suspend fun removePTrack(playlistTrackEntity: PlaylistTrackEntity): Int

    @Query("SELECT * FROM $TRACKS_TABLE WHERE playlistRelationId=:playlistId")
    fun getTracksOfPlaylist(playlistId: Int): Flow<List<PlaylistTrackEntity>>

    @Query("SELECT * FROM $TRACKS_TABLE WHERE playlistRelationId=:playlistId AND remoteId=:remoteId")
    fun getExactTrackOfPlaylist(playlistId: Int, remoteId: Long): PlaylistTrackEntity

    @Query("SELECT * FROM $TRACKS_TABLE WHERE playlistRelationId=:playlistId")
    fun getTracksOfPlaylistStraight(playlistId: Int): List<PlaylistTrackEntity>

    @Query("SELECT $TRACKS_TABLE.remoteId  FROM $TRACKS_TABLE WHERE playlistRelationId=:playlistId")
    fun getTracksRemoteIDsByPlaylistStraight(playlistId: Int): List<Long>

    @Transaction
    @Query(
        "SELECT $PLAYLIST_TABLE.* FROM  $PLAYLIST_TABLE  LEFT JOIN $TRACKS_TABLE ON " +
                "$PLAYLIST_TABLE.playlistId = $TRACKS_TABLE.playlistRelationId GROUP BY $PLAYLIST_TABLE.playlistId " +
                " ORDER BY $PLAYLIST_TABLE.playlistId DESC"
    )
    fun getPlaylistsWithTracks(): Flow<List<PlaylistsWithTracks>>

    @Transaction
    @Query(
        "SELECT $PLAYLIST_TABLE.*, COUNT( $TRACKS_TABLE.pTrackId) AS child_count " +
                " FROM  $PLAYLIST_TABLE  LEFT JOIN $TRACKS_TABLE ON " +
                "$PLAYLIST_TABLE.playlistId = $TRACKS_TABLE.playlistRelationId GROUP BY $PLAYLIST_TABLE.playlistId " +
                "ORDER BY $PLAYLIST_TABLE.playlistId DESC"
    )
    fun getPlaylistsWithCountOfTracks(): Flow<List<PlaylistsWithCount>>

    @Transaction
    @Query(
        "SELECT $PLAYLIST_TABLE.* FROM  $PLAYLIST_TABLE " +
                "  LEFT JOIN $TRACKS_TABLE ON " +
                "$PLAYLIST_TABLE.playlistId = $TRACKS_TABLE.playlistRelationId WHERE  $PLAYLIST_TABLE.playlistId=:playlistId "
    )
    fun getPlaylistWithTracksById(playlistId: Int): Flow<PlaylistsWithTracks>

}
