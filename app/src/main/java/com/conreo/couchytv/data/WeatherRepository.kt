// Modified for Couchy HyperOS, 2026-11-23. Upstream and change notices: UPSTREAM.md, CHANGELOG.md.
package com.conreo.couchytv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.net.URLEncoder
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

@Serializable
data class WeatherReading(val temperature:Double,val code:Int,val day:Boolean,val updatedAt:Long,
    val condition:String="",val source:String="Open-Meteo") {
    val description:String get()=condition.ifEmpty {when(code) {
        0->"晴";1,2->"多云";3->"阴";45,48->"雾";51,53,55,56,57->"小雨"
        61,63,65,66,67,80,81,82->"雨";71,73,75,77,85,86->"雪";95,96,99->"雷雨";else->"天气"
    }}
}
data class WeatherPlace(val name:String,val latitude:Double=0.0,val longitude:Double=0.0,val station:String="")

/** Domestic observations first, with a geographic model fallback and a 30-minute disk cache. */
object WeatherRepository {
    private val client=okhttp3.OkHttpClient.Builder().connectTimeout(5,TimeUnit.SECONDS).readTimeout(6,TimeUnit.SECONDS).build()
    private val json=Json {ignoreUnknownKeys=true}
    private fun text(url:String):String {
        val builder=okhttp3.Request.Builder().url(url).header("User-Agent","Mozilla/5.0")
        if(url.contains("weather.com.cn")) builder.header("Referer","https://www.weather.com.cn/")
        client.newCall(builder.build()).execute().use {r->check(r.isSuccessful) {"天气服务暂不可用"};return requireNotNull(r.body).string()}
    }
    private fun observation(station:String):WeatherReading {
        require(station.matches(Regex("[0-9]{9}")))
        val raw=text("https://d1.weather.com.cn/sk_2d/$station.html?_=${System.currentTimeMillis()}")
        val o=json.parseToJsonElement(raw.substring(raw.indexOf('{'),raw.lastIndexOf('}')+1)).jsonObject
        val date=o["date"]?.jsonPrimitive?.content.orEmpty()
        val today=Calendar.getInstance()
        val parsed=Regex("(\\d+)月(\\d+)日").find(date)
        if(parsed!=null) check(parsed.groupValues[1].toInt()==today.get(Calendar.MONTH)+1 && parsed.groupValues[2].toInt()==today.get(Calendar.DAY_OF_MONTH)) {"天气数据已过期"}
        val condition=o["weather"]!!.jsonPrimitive.content
        val code=when {condition.contains("雷")->95;condition.contains("雪")->71;condition.contains("雨")->61;condition.contains("雾")->45;condition.contains("多云")->2;condition.contains("阴")->3;else->0}
        return WeatherReading(o["temp"]!!.jsonPrimitive.content.toDouble(),code,today.get(Calendar.HOUR_OF_DAY) in 6..18,
            System.currentTimeMillis(),condition,"中国天气网")
    }
    suspend fun current(context:Context,latitude:Double,longitude:Double,station:String):WeatherReading?=withContext(Dispatchers.IO) {
        val key=station.takeIf {it.matches(Regex("[0-9]{9}"))} ?: "$latitude-$longitude"
        val file=File(context.filesDir,"weather-$key.json")
        val old=runCatching {json.decodeFromString<WeatherReading>(file.readText())}.getOrNull()
        if(old!=null && System.currentTimeMillis()-old.updatedAt<30*60_000L) return@withContext old
        val live=runCatching {observation(station)}.getOrElse {first->
            if(latitude==0.0 && longitude==0.0) {android.util.Log.w("CouchyWeather","observation unavailable",first);null}
            else runCatching {
                val o=json.parseToJsonElement(text("https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude&current=temperature_2m,weather_code,is_day&timezone=Asia%2FShanghai")).jsonObject["current"]!!.jsonObject
                WeatherReading(o["temperature_2m"]!!.jsonPrimitive.double,o["weather_code"]!!.jsonPrimitive.int,o["is_day"]!!.jsonPrimitive.int==1,System.currentTimeMillis())
            }.getOrElse {android.util.Log.w("CouchyWeather","weather sources unavailable",it);null}
        }
        live?.also {val tmp=File(file.path+".tmp");tmp.writeText(json.encodeToString(WeatherReading.serializer(),it));tmp.renameTo(file)} ?: old
    }
    suspend fun findCity(name:String):List<WeatherPlace> = withContext(Dispatchers.IO) {
        val encoded=URLEncoder.encode(name.trim(),"UTF-8")
        val domestic=runCatching {
            val raw=text("https://toy1.weather.com.cn/search?cityname=$encoded&callback=weatherLookup")
            val array=json.parseToJsonElement(raw.substring(raw.indexOf('['),raw.lastIndexOf(']')+1)).jsonArray
            array.mapNotNull {entry->
                val p=entry.jsonObject["ref"]!!.jsonPrimitive.content.split('~')
                if(p.size<3 || !p[0].matches(Regex("[0-9]{9}"))) null
                else WeatherPlace(p[2]+" · "+p.last(),station=p[0])
            }.distinctBy {it.station}.take(5)
        }.getOrDefault(emptyList())
        if(domestic.isNotEmpty()) return@withContext domestic
        val root=json.parseToJsonElement(text("https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=5&language=zh&format=json")).jsonObject
        root["results"]?.jsonArray.orEmpty().map {item->
            val o=item.jsonObject;val city=o["name"]!!.jsonPrimitive.content;val area=o["admin1"]?.jsonPrimitive?.content.orEmpty()
            WeatherPlace(if(area.isNotEmpty() && area!=city) "$city · $area" else city,o["latitude"]!!.jsonPrimitive.double,o["longitude"]!!.jsonPrimitive.double)
        }
    }
}
