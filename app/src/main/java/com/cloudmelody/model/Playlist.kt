package com.cloudmelody.model

data class Playlist(
    val id: Long,
    val name: String,
    val coverImgUrl: String? = null,
    val trackCount: Int = 0,
    val description: String? = null
)
