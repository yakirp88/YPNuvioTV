package com.nuvio.tv.domain.repository
class MetaRepository {
    class Meta(val imdbId: String?)
    fun getCachedMeta(type: String, id: String) = Meta(null)
}
