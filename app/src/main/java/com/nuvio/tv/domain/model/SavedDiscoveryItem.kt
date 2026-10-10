package com.nuvio.tv.domain.model

/** Stable snapshot schema; opening a saved catalog makes no discovery API calls. */
@androidx.annotation.Keep
data class SavedDiscoveryItem(val id:String,val type:String,val name:String,
    val poster:String?,val background:String?,val logo:String?,val description:String?,
    val released:String?,val year:String?,val rating:Float?,val genres:List<String>,
    val imdbId:String?,val addonUrl:String?,val runtime:String?,val votes:Int?) {
    fun preview()=MetaPreview(id,ContentType.fromString(type),rawType=type,name=name,poster=poster,
        posterShape=PosterShape.POSTER,background=background,logo=logo,description=description,
        releaseInfo=year,imdbRating=rating,genres=genres,imdbId=imdbId,sourceAddonBaseUrl=addonUrl,
        released=released,runtime=runtime,voteCount=votes)
    companion object {
        fun from(p:MetaPreview)=SavedDiscoveryItem(p.id,p.apiType,p.name,p.poster,p.background,p.logo,p.description,
            p.released,p.releaseInfo,p.imdbRating,p.genres,p.imdbId,p.sourceAddonBaseUrl,p.runtime,p.voteCount)
    }
}
