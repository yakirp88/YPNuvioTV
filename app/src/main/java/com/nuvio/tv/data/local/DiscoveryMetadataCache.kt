package com.nuvio.tv.data.local

import android.content.Context
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

fun discoveryCacheLimitMb(value: Int?) = ((value ?: 50).coerceIn(0,1000) / 25) * 25

/** Only JSON metadata lives here. No image downloads, credentials or personal memberships. */
@Singleton
class DiscoveryMetadataCache @Inject constructor(
    @ApplicationContext context: Context, private val preferences: DiscoveryPreferences, private val moshi: Moshi
) {
    private val disk = DiscoveryMetadataDiskCache(File(context.cacheDir,"discovery_metadata_v1"))
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val mutableUsage=MutableStateFlow<Long?>(null)
    val usedBytes=mutableUsage.asStateFlow()
    private val mutableRevision=MutableStateFlow(0L)
    val revision=mutableRevision.asStateFlow()
    init {scope.launch {preferences.cacheLimitMb.collect {limit ->
        runCatching {disk.configure(limit.toLong()*1024*1024);mutableUsage.value=disk.usedBytes}
    }}}

    suspend fun measure()=withContext(Dispatchers.IO) {
        disk.configure(preferences.cacheLimitMb.first().toLong()*1024*1024)
        mutableUsage.value=disk.usedBytes
    }
    suspend fun clear()=withContext(Dispatchers.IO) {
        disk.clear();mutableUsage.value=disk.usedBytes;mutableRevision.value=disk.revision
    }
    suspend fun <T:Any> getOrFetch(key:String,type:Class<T>,ttlMs:Long,fetch:suspend()->T):T {
        val adapter=moshi.adapter(type)
        val ticket=disk.revision
        val cached=withContext(Dispatchers.IO) {
            val value=try {
                disk.configure(preferences.cacheLimitMb.first().toLong()*1024*1024)
                disk.read(key,ttlMs)?.let {adapter.fromJson(it)}
            } catch(e:CancellationException) {throw e} catch(_:Exception) {null}
            mutableUsage.value=runCatching {disk.usedBytes}.getOrNull()
            value
        }
        if(cached!=null) return cached
        val fresh=fetch()
        withContext(Dispatchers.IO) {
            runCatching {disk.write(key,adapter.toJson(fresh),ticket);mutableUsage.value=disk.usedBytes}
        }
        return fresh
    }
}

/** Lazy one-time index, byte quota and LRU eviction. All disk work runs on Dispatchers.IO. */
internal class DiscoveryMetadataDiskCache(private val directory:File,private val clock:()->Long=System::currentTimeMillis) {
    private data class Entry(val file:File,val bytes:Long,var accessed:Long)
    private val entries=linkedMapOf<String,Entry>()
    private var initialized=false
    private var limit=50L*1024*1024
    private var bytes=0L
    private var epoch=0L
    val revision:Long @Synchronized get()=epoch
    val usedBytes:Long @Synchronized get() {initialize();return bytes}
    private fun hash(key:String)=MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") {"%02x".format(it)}
    private fun initialize() {
        if(initialized) return
        directory.mkdirs()
        directory.listFiles().orEmpty().forEach {file ->
            if(file.name.endsWith(".tmp")) file.delete()
            else if(file.isFile && Regex("[a-f0-9]{64}\\.cache").matches(file.name)) {
                val entry=Entry(file,file.length(),file.lastModified());entries[file.name]=entry;bytes+=entry.bytes
            }
        }
        initialized=true
    }
    @Synchronized fun configure(maxBytes:Long) {initialize();limit=maxBytes.coerceAtLeast(0);trim()}
    private fun remove(name:String) {
        val entry=entries[name] ?: return
        if(!entry.file.exists() || entry.file.delete()) {entries.remove(name);bytes-=entry.bytes}
    }
    private fun trim() {
        if(bytes<=limit) return
        entries.entries.sortedBy {it.value.accessed}.map {it.key}.forEach {name ->if(bytes>limit) remove(name)}
    }
    @Synchronized fun read(key:String,ttlMs:Long):String? {
        initialize();if(limit==0L) return null
        val name=hash(key)+".cache";val entry=entries[name] ?: return null
        return try {
            DataInputStream(entry.file.inputStream().buffered()).use {input ->
                val created=input.readLong()
                if(clock()<created || clock()-created>=ttlMs) {remove(name);null}
                else {val text=input.readBytes().toString(Charsets.UTF_8);entry.accessed=clock();entry.file.setLastModified(entry.accessed);text}
            }
        } catch(_:Exception) {remove(name);null}
    }
    @Synchronized fun write(key:String,json:String,expectedRevision:Long=epoch) {
        initialize();if(expectedRevision!=epoch || limit==0L) return
        val payload=json.toByteArray(Charsets.UTF_8)
        val size=payload.size+8L
        if(size>limit || size>4L*1024*1024) return
        val name=hash(key)+".cache";val file=File(directory,name);val temporary=File(directory,"$name.tmp")
        try {
            DataOutputStream(temporary.outputStream().buffered()).use {it.writeLong(clock());it.write(payload)}
            if(!temporary.renameTo(file)) return
            bytes-=entries[name]?.bytes ?: 0L
            entries[name]=Entry(file,size,clock());bytes+=size;file.setLastModified(clock());trim()
        } finally {temporary.delete()}
    }
    @Synchronized fun clear() {initialize();epoch++;entries.keys.toList().forEach(::remove)}
}
