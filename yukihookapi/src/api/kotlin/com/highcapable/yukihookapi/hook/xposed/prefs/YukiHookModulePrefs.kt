/*
 * YukiHookAPI - An efficient Kotlin version of the Xposed Hook API.
 * Copyright (C) 2019-2022 HighCapable
 * https://github.com/fankes/YukiHookAPI
 *
 * MIT License
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * This file is Created by fankes on 2022/2/8.
 */
@file:Suppress(
    "SetWorldReadable", "CommitPrefEdits", "DEPRECATION", "WorldReadableFiles",
    "unused", "UNCHECKED_CAST", "MemberVisibilityCanBePrivate", "StaticFieldLeak"
)

package com.highcapable.yukihookapi.hook.xposed.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceFragmentCompat
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.hook.log.yLoggerW
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiHookBridge
import com.highcapable.yukihookapi.hook.xposed.prefs.data.PrefsData
import com.highcapable.yukihookapi.hook.xposed.prefs.ui.ModulePreferenceFragment
import de.robv.android.xposed.XSharedPreferences
import java.io.File

/**
 * ?????? Xposed ?????????????????????
 *
 * ?????? [SharedPreferences] ??? [XSharedPreferences]
 *
 * ????????????????????????????????????????????????
 *
 * - ??????????????????????????????????????? - ?????? LSPosed ?????????????????? - EdXposed ????????????????????????????????????
 *
 * - ?????? LSPosed ???????????? AndroidManifests.xml ?????? "xposedminversion" ??????????????? 93
 *
 * - ????????? LSPosed ???????????????????????? API ?????? 26 ?????? - [YukiHookAPI] ?????????????????? [makeWorldReadable] ????????????????????????
 *
 * - ?????????????????????????????????????????? [context] ?????????????????????
 *
 * - ?????????????????? [PreferenceFragmentCompat] - ???????????? [ModulePreferenceFragment] ???????????????????????????
 *
 * - ??????????????? [API ?????? - YukiHookModulePrefs](https://fankes.github.io/YukiHookAPI/#/api/document?id=yukihookmoduleprefs-class)
 * @param context ??????????????? - ?????????
 */
class YukiHookModulePrefs private constructor(private var context: Context? = null) {

    internal companion object {

        /** ????????? Xposed ?????? */
        private val isXposedEnvironment = YukiHookBridge.hasXposedBridge

        /** ?????? [YukiHookModulePrefs] ?????? */
        private var instance: YukiHookModulePrefs? = null

        /**
         * ?????? [YukiHookModulePrefs] ??????
         * @param context ?????? - Xposed ????????????
         * @return [YukiHookModulePrefs]
         */
        internal fun instance(context: Context? = null) =
            instance?.apply { if (context != null) this.context = context } ?: YukiHookModulePrefs(context).apply { instance = this }

        /**
         * ????????????????????????
         * @param context ??????
         * @param prefsFileName Sp ?????????
         */
        internal fun makeWorldReadable(context: Context?, prefsFileName: String) {
            runCatching {
                context?.also {
                    File(File(it.applicationInfo.dataDir, "shared_prefs"), prefsFileName).apply {
                        setReadable(true, false)
                        setExecutable(true, false)
                    }
                }
            }
        }
    }

    /** ???????????? - ???????????? + _preferences */
    private var prefsName = "${YukiHookBridge.modulePackageName.ifBlank { context?.packageName ?: "" }}_preferences"

    /** [XSharedPreferences] ????????? [String] ???????????? */
    private var xPrefCacheKeyValueStrings = HashMap<String, String>()

    /** [XSharedPreferences] ????????? [Set]<[String]> ???????????? */
    private var xPrefCacheKeyValueStringSets = HashMap<String, Set<String>>()

    /** [XSharedPreferences] ????????? [Boolean] ???????????? */
    private var xPrefCacheKeyValueBooleans = HashMap<String, Boolean>()

    /** [XSharedPreferences] ????????? [Int] ???????????? */
    private var xPrefCacheKeyValueInts = HashMap<String, Int>()

    /** [XSharedPreferences] ????????? [Long] ???????????? */
    private var xPrefCacheKeyValueLongs = HashMap<String, Long>()

    /** [XSharedPreferences] ????????? [Float] ???????????? */
    private var xPrefCacheKeyValueFloats = HashMap<String, Float>()

    /** ???????????????????????? */
    private var isUsingKeyValueCache = YukiHookAPI.Configs.isEnableModulePrefsCache

    /** ?????????????????????????????? EdXposed/LSPosed */
    private var isUsingNewXSharePrefs = false

    /** ?????? API ???????????? */
    private fun checkApi() {
        if (YukiHookAPI.isLoadedFromBaseContext) error("YukiHookModulePrefs not allowed in Custom Hook API")
        if (YukiHookBridge.hasXposedBridge && YukiHookBridge.modulePackageName.isBlank())
            error("Xposed modulePackageName load failed, please reset and rebuild it")
    }

    /**
     * ?????? [XSharedPreferences] ??????
     * @return [XSharedPreferences]
     */
    private val xPref
        get() = XSharedPreferences(YukiHookBridge.modulePackageName, prefsName).apply {
            checkApi()
            makeWorldReadable()
            reload()
        }

    /**
     * ?????? [SharedPreferences] ??????
     * @return [SharedPreferences]
     */
    private val sPref
        get() = try {
            checkApi()
            context?.getSharedPreferences(prefsName, Context.MODE_WORLD_READABLE).also { isUsingNewXSharePrefs = true }
                ?: error("If you want to use module prefs, you must set the context instance first")
        } catch (_: Throwable) {
            checkApi()
            context?.getSharedPreferences(prefsName, Context.MODE_PRIVATE).also { isUsingNewXSharePrefs = false }
                ?: error("If you want to use module prefs, you must set the context instance first")
        }

    /** ???????????????????????? */
    private fun makeWorldReadable() = runCatching {
        if (isUsingNewXSharePrefs.not()) makeWorldReadable(context, prefsFileName = "${prefsName}.xml")
    }

    /**
     * ?????? [XSharedPreferences] ????????????
     *
     * - ???????????? [isXposedEnvironment] ????????? - ??????????????????????????? false
     * @return [Boolean] ????????????
     */
    val isXSharePrefsReadable get() = runCatching { xPref.let { it.file.exists() && it.file.canRead() } }.getOrNull() ?: false

    /**
     * ?????? [YukiHookModulePrefs] ??????????????? EdXposed/LSPosed ?????????????????????
     *
     * - ????????????????????? Xposed ??????????????????
     *
     * - ????????????????????????????????? - [isXposedEnvironment] ????????????????????? false
     * @return [Boolean] ???????????????????????? - ????????? [XSharedPreferences] ????????????????????? false
     */
    val isRunInNewXShareMode
        get() = runCatching {
            /** ?????????????????? */
            sPref.edit()
            isUsingNewXSharePrefs
        }.getOrNull() ?: false

    /**
     * ????????? Sp ????????????
     * @param name ???????????? Sp ????????????
     * @return [YukiHookModulePrefs]
     */
    fun name(name: String): YukiHookModulePrefs {
        isUsingKeyValueCache = YukiHookAPI.Configs.isEnableModulePrefsCache
        prefsName = name
        return this
    }

    /**
     * ??????????????????????????????
     *
     * ?????????????????? [YukiHookAPI.Configs.isEnableModulePrefsCache]
     *
     * - ?????? [XSharedPreferences] ?????????
     * @return [YukiHookModulePrefs]
     */
    fun direct(): YukiHookModulePrefs {
        isUsingKeyValueCache = false
        return this
    }

    /**
     * ?????? [String] ??????
     *
     * - ??????????????????????????????????????????
     *
     * - ???????????? [PrefsData] ????????????????????? [get] ????????????
     * @param key ????????????
     * @param value ???????????? - ""
     * @return [String]
     */
    fun getString(key: String, value: String = "") =
        (if (isXposedEnvironment)
            if (isUsingKeyValueCache)
                xPrefCacheKeyValueStrings[key].let {
                    (it ?: xPref.getString(key, value) ?: value).let { value ->
                        xPrefCacheKeyValueStrings[key] = value
                        value
                    }
                }
            else resetCacheSet { xPref.getString(key, value) ?: value }
        else sPref.getString(key, value) ?: value).let {
            makeWorldReadable()
            it
        }

    /**
     * ?????? [Set]<[String]> ??????
     *
     * - ??????????????????????????????????????????
     *
     * - ???????????? [PrefsData] ????????????????????? [get] ????????????
     * @param key ????????????
     * @param value ????????????
     * @return [Set]<[String]>
     */
    fun getStringSet(key: String, value: Set<String>) =
        (if (isXposedEnvironment)
            if (isUsingKeyValueCache)
                xPrefCacheKeyValueStrings[key].let {
                    (it ?: xPref.getStringSet(key, value) ?: value).let { value ->
                        xPrefCacheKeyValueStringSets[key] = value as Set<String>
                        value
                    }
                }
            else resetCacheSet { xPref.getStringSet(key, value) ?: value }
        else sPref.getStringSet(key, value) ?: value).let {
            makeWorldReadable()
            it
        }

    /**
     * ?????? [Boolean] ??????
     *
     * - ??????????????????????????????????????????
     *
     * - ???????????? [PrefsData] ????????????????????? [get] ????????????
     * @param key ????????????
     * @param value ???????????? - false
     * @return [Boolean]
     */
    fun getBoolean(key: String, value: Boolean = false) =
        (if (isXposedEnvironment)
            if (isUsingKeyValueCache)
                xPrefCacheKeyValueBooleans[key].let {
                    it ?: xPref.getBoolean(key, value).let { value ->
                        xPrefCacheKeyValueBooleans[key] = value
                        value
                    }
                }
            else resetCacheSet { xPref.getBoolean(key, value) }
        else sPref.getBoolean(key, value)).let {
            makeWorldReadable()
            it
        }

    /**
     * ?????? [Int] ??????
     *
     * - ??????????????????????????????????????????
     *
     * - ???????????? [PrefsData] ????????????????????? [get] ????????????
     * @param key ????????????
     * @param value ???????????? - 0
     * @return [Int]
     */
    fun getInt(key: String, value: Int = 0) =
        (if (isXposedEnvironment)
            if (isUsingKeyValueCache)
                xPrefCacheKeyValueInts[key].let {
                    it ?: xPref.getInt(key, value).let { value ->
                        xPrefCacheKeyValueInts[key] = value
                        value
                    }
                }
            else resetCacheSet { xPref.getInt(key, value) }
        else sPref.getInt(key, value)).let {
            makeWorldReadable()
            it
        }

    /**
     * ?????? [Float] ??????
     *
     * - ??????????????????????????????????????????
     *
     * - ???????????? [PrefsData] ????????????????????? [get] ????????????
     * @param key ????????????
     * @param value ???????????? - 0f
     * @return [Float]
     */
    fun getFloat(key: String, value: Float = 0f) =
        (if (isXposedEnvironment)
            if (isUsingKeyValueCache)
                xPrefCacheKeyValueFloats[key].let {
                    it ?: xPref.getFloat(key, value).let { value ->
                        xPrefCacheKeyValueFloats[key] = value
                        value
                    }
                }
            else resetCacheSet { xPref.getFloat(key, value) }
        else sPref.getFloat(key, value)).let {
            makeWorldReadable()
            it
        }

    /**
     * ?????? [Long] ??????
     *
     * - ??????????????????????????????????????????
     *
     * - ???????????? [PrefsData] ????????????????????? [get] ????????????
     * @param key ????????????
     * @param value ???????????? - 0L
     * @return [Long]
     */
    fun getLong(key: String, value: Long = 0L) =
        (if (isXposedEnvironment)
            if (isUsingKeyValueCache)
                xPrefCacheKeyValueLongs[key].let {
                    it ?: xPref.getLong(key, value).let { value ->
                        xPrefCacheKeyValueLongs[key] = value
                        value
                    }
                }
            else resetCacheSet { xPref.getLong(key, value) }
        else sPref.getLong(key, value)).let {
            makeWorldReadable()
            it
        }

    /**
     *  ?????????????????????????????????
     *
     * - ??????????????????????????????????????????
     *
     * - ?????????????????????????????????????????? - ?????????????????? - ?????????????????????????????????
     * @return [HashMap] ???????????????????????????
     */
    fun all() = HashMap<String, Any?>().apply {
        if (isXposedEnvironment)
            xPref.all.forEach { (k, v) -> this[k] = v }
        else sPref.all.forEach { (k, v) -> this[k] = v }
    }

    /**
     * ?????????????????? [key] ???????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     */
    fun remove(key: String) = moduleEnvironment {
        sPref.edit().remove(key).apply()
        makeWorldReadable()
    }

    /**
     * ?????? [PrefsData.key] ???????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param prefs ????????????
     */
    inline fun <reified T> remove(prefs: PrefsData<T>) = remove(prefs.key)

    /**
     * ????????????????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     */
    fun clear() = moduleEnvironment {
        sPref.edit().clear().apply()
        makeWorldReadable()
    }

    /**
     * ?????? [String] ??????
     *
     * - ???????????? [PrefsData] ????????????????????? [put] ????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     * @param value ????????????
     */
    fun putString(key: String, value: String) = moduleEnvironment {
        sPref.edit().putString(key, value).apply()
        makeWorldReadable()
    }

    /**
     * ?????? [Set]<[String]> ??????
     *
     * - ???????????? [PrefsData] ????????????????????? [put] ????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     * @param value ????????????
     */
    fun putStringSet(key: String, value: Set<String>) = moduleEnvironment {
        sPref.edit().putStringSet(key, value).apply()
        makeWorldReadable()
    }

    /**
     * ?????? [Boolean] ??????
     *
     * - ???????????? [PrefsData] ????????????????????? [put] ????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     * @param value ????????????
     */
    fun putBoolean(key: String, value: Boolean) = moduleEnvironment {
        sPref.edit().putBoolean(key, value).apply()
        makeWorldReadable()
    }

    /**
     * ?????? [Int] ??????
     *
     * - ???????????? [PrefsData] ????????????????????? [put] ????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     * @param value ????????????
     */
    fun putInt(key: String, value: Int) = moduleEnvironment {
        sPref.edit().putInt(key, value).apply()
        makeWorldReadable()
    }

    /**
     * ?????? [Float] ??????
     *
     * - ???????????? [PrefsData] ????????????????????? [put] ????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     * @param value ????????????
     */
    fun putFloat(key: String, value: Float) = moduleEnvironment {
        sPref.edit().putFloat(key, value).apply()
        makeWorldReadable()
    }

    /**
     * ?????? [Long] ??????
     *
     * - ???????????? [PrefsData] ????????????????????? [put] ????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ????????????
     * @param value ????????????
     */
    fun putLong(key: String, value: Long) = moduleEnvironment {
        sPref.edit().putLong(key, value).apply()
        makeWorldReadable()
    }

    /**
     * ?????????????????????????????????
     * @param prefs ????????????
     * @param value ????????? - ?????????????????? [prefs] ?????? [PrefsData.value]
     * @return [T] ????????? [String]???[Int]???[Float]???[Long]???[Boolean]
     */
    inline fun <reified T> get(prefs: PrefsData<T>, value: T = prefs.value): T = getPrefsData(prefs.key, value) as T

    /**
     * ?????????????????????????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param prefs ????????????
     * @param value ??????????????? - ????????? [String]???[Int]???[Float]???[Long]???[Boolean]
     */
    inline fun <reified T> put(prefs: PrefsData<T>, value: T) = putPrefsData(prefs.key, value)

    /**
     * ?????????????????????????????????
     *
     * ?????????????????????????????????
     * @param key ??????
     * @param value ?????????
     * @return [Any]
     */
    @PublishedApi
    internal fun getPrefsData(key: String, value: Any?): Any = when (value) {
        is String -> getString(key, value)
        is Set<*> -> getStringSet(key, value as? Set<String> ?: error("Key-Value type ${value.javaClass.name} is not allowed"))
        is Int -> getInt(key, value)
        is Float -> getFloat(key, value)
        is Long -> getLong(key, value)
        is Boolean -> getBoolean(key, value)
        else -> error("Key-Value type ${value?.javaClass?.name} is not allowed")
    }

    /**
     * ?????????????????????????????????
     *
     * ?????????????????????????????????
     *
     * - ????????? [Context] ???????????????
     *
     * - ?????? [XSharedPreferences] ??????????????? - ????????????
     * @param key ??????
     * @param value ??????????????? - ????????? [String]???[Int]???[Float]???[Long]???[Boolean]
     */
    @PublishedApi
    internal fun putPrefsData(key: String, value: Any?) = when (value) {
        is String -> putString(key, value)
        is Set<*> -> putStringSet(key, value as? Set<String> ?: error("Key-Value type ${value.javaClass.name} is not allowed"))
        is Int -> putInt(key, value)
        is Float -> putFloat(key, value)
        is Long -> putLong(key, value)
        is Boolean -> putBoolean(key, value)
        else -> error("Key-Value type ${value?.javaClass?.name} is not allowed")
    }

    /**
     * ?????? [XSharedPreferences] ????????????????????????
     *
     * ?????????????????? [YukiHookAPI.Configs.isEnableModulePrefsCache]
     *
     * ?????????????????????????????????????????????????????????
     *
     * ???????????? [XSharedPreferences] ????????????
     */
    fun clearCache() {
        xPrefCacheKeyValueStrings.clear()
        xPrefCacheKeyValueStringSets.clear()
        xPrefCacheKeyValueBooleans.clear()
        xPrefCacheKeyValueInts.clear()
        xPrefCacheKeyValueLongs.clear()
        xPrefCacheKeyValueFloats.clear()
    }

    /**
     * ?????? [isUsingKeyValueCache] ???????????????
     * @param result ????????????????????????
     * @return [T]
     */
    private inline fun <T> resetCacheSet(result: () -> T): T {
        isUsingKeyValueCache = YukiHookAPI.Configs.isEnableModulePrefsCache
        return result()
    }

    /**
     * ????????????????????????
     *
     * ??????????????????????????????????????????
     * @param callback ?????????????????????
     */
    private inline fun moduleEnvironment(callback: () -> Unit) {
        if (isXposedEnvironment.not()) callback()
        else yLoggerW(msg = "You cannot use write prefs function in Xposed Environment")
    }
}