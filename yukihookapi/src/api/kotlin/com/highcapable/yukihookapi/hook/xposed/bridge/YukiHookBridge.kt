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
 * This file is Created by fankes on 2022/4/3.
 */
@file:Suppress("unused", "StaticFieldLeak")

package com.highcapable.yukihookapi.hook.xposed.bridge

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.content.res.Resources
import com.highcapable.yukihookapi.YukiHookAPI
import com.highcapable.yukihookapi.annotation.YukiGenerateApi
import com.highcapable.yukihookapi.hook.factory.allConstructors
import com.highcapable.yukihookapi.hook.factory.allMethods
import com.highcapable.yukihookapi.hook.factory.hasClass
import com.highcapable.yukihookapi.hook.param.PackageParam
import com.highcapable.yukihookapi.hook.param.type.HookEntryType
import com.highcapable.yukihookapi.hook.param.wrapper.HookParamWrapper
import com.highcapable.yukihookapi.hook.param.wrapper.PackageParamWrapper
import com.highcapable.yukihookapi.hook.type.android.ApplicationClass
import com.highcapable.yukihookapi.hook.type.android.ConfigurationClass
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.android.InstrumentationClass
import com.highcapable.yukihookapi.hook.type.java.IntType
import com.highcapable.yukihookapi.hook.xposed.bridge.dummy.YukiModuleResources
import com.highcapable.yukihookapi.hook.xposed.bridge.dummy.YukiResources
import com.highcapable.yukihookapi.hook.xposed.bridge.inject.YukiHookBridge_Injector
import com.highcapable.yukihookapi.hook.xposed.bridge.status.YukiHookModuleStatus
import com.highcapable.yukihookapi.hook.xposed.channel.YukiHookDataChannel
import dalvik.system.PathClassLoader
import de.robv.android.xposed.*
import de.robv.android.xposed.callbacks.XC_InitPackageResources
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * ?????????????????? Xposed Hook ????????? [XposedBridge] ?????????????????????
 *
 * ????????? [IXposedHookZygoteInit]???[IXposedHookLoadPackage]???[IXposedHookInitPackageResources] ????????????
 *
 * - ?????????????????????????????? - ??????????????????????????????????????????????????????????????????
 */
@YukiGenerateApi
object YukiHookBridge {

    /** Android ?????????????????? */
    @PublishedApi
    internal const val SYSTEM_FRAMEWORK_NAME = "android"

    /** Xposed ?????????????????? */
    private var isXposedInitialized = false

    /** [YukiHookDataChannel] ?????????????????? */
    private var isDataChannelRegister = false

    /** ?????? [PackageParam] ??????????????? APP ?????? */
    private val loadedPackageNames = HashSet<String>()

    /** ?????? [PackageParamWrapper] ???????????? */
    private var packageParamWrappers = HashMap<String, PackageParamWrapper>()

    /** ?????? [PackageParam] ??????????????? */
    internal var packageParamCallback: (PackageParam.() -> Unit)? = null

    /** ?????? Hook Framework ???????????? Resources Hook */
    internal var isSupportResourcesHook = false

    /**
     * ?????? Hook APP (??????) ????????????????????? [Application]
     *
     * ?????? [YukiHookAPI.Configs.isEnableDataChannel] ??? [AppLifecycleCallback.isCallbackSetUp] ????????????
     */
    internal var hostApplication: Application? = null

    /** ?????? Xposed ???????????? APK ?????? */
    internal var moduleAppFilePath = ""

    /** ?????? Xposed ???????????? [Resources] */
    internal var moduleAppResources: YukiModuleResources? = null

    /**
     * ???????????? Xposed ?????????????????? [Resources]
     * @return [YukiModuleResources] or null
     */
    internal val dynamicModuleAppResources get() = runCatching { YukiModuleResources.createInstance(moduleAppFilePath) }.getOrNull()

    /**
     * ??????????????? Xposed ?????????????????????
     * @return [String]
     */
    internal val moduleGeneratedVersion get() = YukiHookBridge_Injector.getModuleGeneratedVersion()

    /**
     * ????????????????????? Xposed ????????????
     *
     * - ?????????????????????????????? - ?????????????????? - ?????????????????????
     * @return [Boolean]
     */
    @YukiGenerateApi
    val isXposedCallbackSetUp
        get() = isXposedInitialized.not() && packageParamCallback != null

    /**
     * ????????? Xposed ????????????
     *
     * - ?????????????????????????????? - ?????????????????? - ?????????????????????
     */
    @YukiGenerateApi
    var modulePackageName = ""

    /**
     * ???????????? Hook ???????????????
     *
     * ??? [XposedBridge] ?????? TAG
     * @return [String] ????????????????????? unknown - [hasXposedBridge] ?????????????????? invalid
     */
    internal val executorName
        get() = runCatching {
            (Hooker.findField(XposedBridge::class.java, name = "TAG").get(null) as? String?)
                ?.replace(oldValue = "Bridge", newValue = "")?.replace(oldValue = "-", newValue = "")?.trim() ?: "unknown"
        }.getOrNull() ?: "invalid"

    /**
     * ???????????? Hook ???????????????
     *
     * ?????? [XposedBridge.getXposedVersion]
     * @return [Int] ????????????????????? -1
     */
    internal val executorVersion get() = runCatching { XposedBridge.getXposedVersion() }.getOrNull() ?: -1

    /**
     * ???????????? [XposedBridge]
     * @return [Boolean]
     */
    internal val hasXposedBridge get() = executorVersion >= 0

    /**
     * ???????????? MIUI ?????????????????????????????????????????????
     * @param packageName ????????????
     * @return [Boolean] ????????????
     */
    private fun isMiuiCatcherPatch(packageName: String?) =
        (packageName == "com.miui.contentcatcher" || packageName == "com.miui.catcherpatch") && ("android.miui.R").hasClass

    /**
     * ????????????????????????????????? [HookEntryType] ?????????
     * @param packageName ??????
     * @param type ?????? Hook ??????
     * @return [Boolean] ??????????????????
     */
    private fun isPackageLoaded(packageName: String, type: HookEntryType): Boolean {
        if (loadedPackageNames.contains("$packageName:$type")) return true
        loadedPackageNames.add("$packageName:$type")
        return false
    }

    /**
     * ??????????????? [PackageParamWrapper]
     *
     * ????????? [type] ?????? [HookEntryType.ZYGOTE] ??? [appClassLoader] ???????????????????????? [XposedBridge.BOOTCLASSLOADER] ???????????????
     * @param type ????????????????????? Hook ??????
     * @param packageName ??????
     * @param processName ???????????????
     * @param appClassLoader APP [ClassLoader]
     * @param appInfo APP [ApplicationInfo]
     * @param appResources APP [YukiResources]
     * @return [PackageParamWrapper] or null
     */
    private fun assignWrapper(
        type: HookEntryType,
        packageName: String?,
        processName: String? = "",
        appClassLoader: ClassLoader? = null,
        appInfo: ApplicationInfo? = null,
        appResources: YukiResources? = null
    ) = run {
        if (packageParamWrappers[packageName] == null)
            if (type == HookEntryType.ZYGOTE || appClassLoader != null)
                PackageParamWrapper(
                    type = type,
                    packageName = packageName ?: SYSTEM_FRAMEWORK_NAME,
                    processName = processName ?: SYSTEM_FRAMEWORK_NAME,
                    appClassLoader = appClassLoader ?: XposedBridge.BOOTCLASSLOADER,
                    appInfo = appInfo,
                    appResources = appResources
                ).also { packageParamWrappers[packageName ?: SYSTEM_FRAMEWORK_NAME] = it }
            else null
        else packageParamWrappers[packageName]?.also {
            it.type = type
            if (packageName?.isNotBlank() == true) it.packageName = packageName
            if (processName?.isNotBlank() == true) it.processName = processName
            if (appClassLoader != null && (type == HookEntryType.ZYGOTE || appClassLoader is PathClassLoader)) it.appClassLoader = appClassLoader
            if (appInfo != null) it.appInfo = appInfo
            if (appResources != null) it.appResources = appResources
        }
    }

    /**
     * ???????????? Hook APP (??????) ??????????????????
     * @param packageName ??????
     */
    private fun registerToAppLifecycle(packageName: String) {
        /** Hook [Application] ???????????? */
        runCatching {
            if (AppLifecycleCallback.isCallbackSetUp) {
                Hooker.hookMethod(Hooker.findMethod(ApplicationClass, name = "attach", ContextClass), object : Hooker.YukiMemberHook() {
                    override fun beforeHookedMember(wrapper: HookParamWrapper) {
                        (wrapper.args?.get(0) as? Context?)?.also { AppLifecycleCallback.attachBaseContextCallback?.invoke(it, false) }
                    }

                    override fun afterHookedMember(wrapper: HookParamWrapper) {
                        (wrapper.args?.get(0) as? Context?)?.also { AppLifecycleCallback.attachBaseContextCallback?.invoke(it, true) }
                    }
                })
                Hooker.hookMethod(Hooker.findMethod(ApplicationClass, name = "onTerminate"), object : Hooker.YukiMemberHook() {
                    override fun afterHookedMember(wrapper: HookParamWrapper) {
                        (wrapper.instance as? Application?)?.also { AppLifecycleCallback.onTerminateCallback?.invoke(it) }
                    }
                })
                Hooker.hookMethod(Hooker.findMethod(ApplicationClass, name = "onLowMemory"), object : Hooker.YukiMemberHook() {
                    override fun afterHookedMember(wrapper: HookParamWrapper) {
                        (wrapper.instance as? Application?)?.also { AppLifecycleCallback.onLowMemoryCallback?.invoke(it) }
                    }
                })
                Hooker.hookMethod(Hooker.findMethod(ApplicationClass, name = "onTrimMemory", IntType), object : Hooker.YukiMemberHook() {
                    override fun afterHookedMember(wrapper: HookParamWrapper) {
                        val self = wrapper.instance as? Application? ?: return
                        val type = wrapper.args?.get(0) as? Int? ?: return
                        AppLifecycleCallback.onTrimMemoryCallback?.invoke(self, type)
                    }
                })
                Hooker.hookMethod(
                    Hooker.findMethod(ApplicationClass, name = "onConfigurationChanged", ConfigurationClass),
                    object : Hooker.YukiMemberHook() {
                        override fun afterHookedMember(wrapper: HookParamWrapper) {
                            val self = wrapper.instance as? Application? ?: return
                            val config = wrapper.args?.get(0) as? Configuration? ?: return
                            AppLifecycleCallback.onConfigurationChangedCallback?.invoke(self, config)
                        }
                    })
            }
            if (YukiHookAPI.Configs.isEnableDataChannel || AppLifecycleCallback.isCallbackSetUp)
                Hooker.hookMethod(
                    Hooker.findMethod(InstrumentationClass, name = "callApplicationOnCreate", ApplicationClass),
                    object : Hooker.YukiMemberHook() {
                        override fun afterHookedMember(wrapper: HookParamWrapper) {
                            (wrapper.args?.get(0) as? Application?)?.also {
                                hostApplication = it
                                AppLifecycleCallback.onCreateCallback?.invoke(it)
                                AppLifecycleCallback.onReceiversCallback.takeIf { e -> e.isNotEmpty() }?.forEach { (_, e) ->
                                    if (e.first.isNotEmpty()) it.registerReceiver(object : BroadcastReceiver() {
                                        override fun onReceive(context: Context?, intent: Intent?) {
                                            if (context == null || intent == null) return
                                            if (e.first.any { a -> a == intent.action }) e.second(context, intent)
                                        }
                                    }, IntentFilter().apply { e.first.forEach { a -> addAction(a) } })
                                }
                                if (isDataChannelRegister) return
                                isDataChannelRegister = true
                                runCatching { YukiHookDataChannel.instance().register(it, packageName) }
                            }
                        }
                    })
        }
    }

    /** ???????????? Xposed ???????????? [Resources] */
    internal fun refreshModuleAppResources() {
        dynamicModuleAppResources?.let { moduleAppResources = it }
    }

    /**
     * Hook ??????????????????????????? Resources Hook ????????????
     *
     * - ?????????????????????????????? - ??????????????????????????????????????? Xposed ????????????
     * @param classLoader ????????? [ClassLoader]
     * @param isHookResourcesStatus ?????? Hook Resources ????????????
     */
    @YukiGenerateApi
    fun hookModuleAppStatus(classLoader: ClassLoader?, isHookResourcesStatus: Boolean = false) {
        if (YukiHookAPI.Configs.isEnableHookModuleStatus)
            Hooker.findClass(classLoader, YukiHookModuleStatus::class.java).also { statusClass ->
                if (isHookResourcesStatus.not()) {
                    Hooker.hookMethod(Hooker.findMethod(statusClass, YukiHookModuleStatus.IS_ACTIVE_METHOD_NAME),
                        object : Hooker.YukiMemberReplacement() {
                            override fun replaceHookedMember(wrapper: HookParamWrapper) = true
                        })
                    Hooker.hookMethod(Hooker.findMethod(statusClass, YukiHookModuleStatus.GET_XPOSED_TAG_METHOD_NAME),
                        object : Hooker.YukiMemberReplacement() {
                            override fun replaceHookedMember(wrapper: HookParamWrapper) = executorName
                        })
                    Hooker.hookMethod(Hooker.findMethod(statusClass, YukiHookModuleStatus.GET_XPOSED_VERSION_METHOD_NAME),
                        object : Hooker.YukiMemberReplacement() {
                            override fun replaceHookedMember(wrapper: HookParamWrapper) = executorVersion
                        })
                } else
                    Hooker.hookMethod(Hooker.findMethod(statusClass, YukiHookModuleStatus.HAS_RESOURCES_HOOK_METHOD_NAME),
                        object : Hooker.YukiMemberReplacement() {
                            override fun replaceHookedMember(wrapper: HookParamWrapper) = true
                        })
            }
    }

    /**
     * ?????? Xposed API ????????????
     *
     * - ?????????????????????????????? - ??????????????????????????????????????? Xposed ????????????
     */
    @YukiGenerateApi
    fun callXposedInitialized() {
        isXposedInitialized = true
    }

    /**
     * ?????? Xposed API Zygote ??????
     *
     * - ?????????????????????????????? - ??????????????????????????????????????? Xposed ????????????
     * @param sparam Xposed [IXposedHookZygoteInit.StartupParam]
     */
    @YukiGenerateApi
    fun callXposedZygoteLoaded(sparam: IXposedHookZygoteInit.StartupParam) {
        moduleAppFilePath = sparam.modulePath
        refreshModuleAppResources()
    }

    /**
     * ?????? Xposed API ??????
     *
     * ??????????????????????????????
     *
     * - ??? [IXposedHookZygoteInit.initZygote] ?????????
     *
     * - ??? [IXposedHookLoadPackage.handleLoadPackage] ?????????
     *
     * - ??? [IXposedHookInitPackageResources.handleInitPackageResources] ?????????
     *
     * - ?????????????????????????????? - ??????????????????????????????????????? Xposed API ??????
     * @param isZygoteLoaded ????????? Xposed [IXposedHookZygoteInit.initZygote]
     * @param lpparam Xposed [XC_LoadPackage.LoadPackageParam]
     * @param resparam Xposed [XC_InitPackageResources.InitPackageResourcesParam]
     */
    @YukiGenerateApi
    fun callXposedLoaded(
        isZygoteLoaded: Boolean,
        lpparam: XC_LoadPackage.LoadPackageParam? = null,
        resparam: XC_InitPackageResources.InitPackageResourcesParam? = null
    ) {
        if (isMiuiCatcherPatch(packageName = lpparam?.packageName ?: resparam?.packageName).not()) when {
            isZygoteLoaded -> assignWrapper(HookEntryType.ZYGOTE, SYSTEM_FRAMEWORK_NAME, SYSTEM_FRAMEWORK_NAME)
            lpparam != null ->
                if (isPackageLoaded(lpparam.packageName, HookEntryType.PACKAGE).not())
                    assignWrapper(HookEntryType.PACKAGE, lpparam.packageName, lpparam.processName, lpparam.classLoader, lpparam.appInfo)
                else null
            resparam != null ->
                if (isPackageLoaded(resparam.packageName, HookEntryType.RESOURCES).not())
                    assignWrapper(HookEntryType.RESOURCES, resparam.packageName, appResources = YukiResources.createFromXResources(resparam.res))
                else null
            else -> null
        }?.also {
            YukiHookAPI.onXposedLoaded(it)
            if (it.type == HookEntryType.PACKAGE) registerToAppLifecycle(it.packageName)
            if (it.type == HookEntryType.RESOURCES) isSupportResourcesHook = true
        }
    }

    /**
     * ?????? Hook APP (??????) ??????????????????????????????
     */
    internal object AppLifecycleCallback {

        /** ????????????????????? */
        internal var isCallbackSetUp = false

        /** [Application.attachBaseContext] ?????? */
        internal var attachBaseContextCallback: ((Context, Boolean) -> Unit)? = null

        /** [Application.onCreate] ?????? */
        internal var onCreateCallback: (Application.() -> Unit)? = null

        /** [Application.onTerminate] ?????? */
        internal var onTerminateCallback: (Application.() -> Unit)? = null

        /** [Application.onLowMemory] ?????? */
        internal var onLowMemoryCallback: (Application.() -> Unit)? = null

        /** [Application.onTrimMemory] ?????? */
        internal var onTrimMemoryCallback: ((Application, Int) -> Unit)? = null

        /** [Application.onConfigurationChanged] ?????? */
        internal var onConfigurationChangedCallback: ((Application, Configuration) -> Unit)? = null

        /** ???????????????????????? */
        internal val onReceiversCallback = HashMap<String, Pair<Array<out String>, (Context, Intent) -> Unit>>()
    }

    /**
     * Hook ????????????????????????
     *
     * ?????? [XposedBridge] ?????? Hook ??????
     */
    internal object Hooker {

        /** ?????? Hook ??? [Member] ?????? */
        private val hookedMembers = HashSet<String>()

        /** ?????? Hook ????????? [Method] ?????? */
        private val hookedAllMethods = HashSet<String>()

        /** ?????? Hook ????????? [Constructor] ?????? */
        private val hookedAllConstructors = HashSet<String>()

        /** ?????? Hook ??????????????? */
        internal const val PRIORITY_DEFAULT = 50

        /** ???????????? Hook ???????????? */
        internal const val PRIORITY_LOWEST = -10000

        /** ???????????? Hook ???????????? */
        internal const val PRIORITY_HIGHEST = 10000

        /**
         * ?????? [Class]
         * @param classLoader ?????? [ClassLoader]
         * @param baseClass ?????????
         * @return [Field]
         * @throws IllegalStateException ?????? [ClassLoader] ??????
         */
        internal fun findClass(classLoader: ClassLoader?, baseClass: Class<*>) =
            classLoader?.loadClass(baseClass.name) ?: error("ClassLoader is null")

        /**
         * ????????????
         * @param baseClass ?????????
         * @param name ????????????
         * @return [Field]
         * @throws NoSuchFieldError ?????????????????????
         */
        internal fun findField(baseClass: Class<*>, name: String) = baseClass.getDeclaredField(name).apply { isAccessible = true }

        /**
         * ????????????
         * @param baseClass ?????????
         * @param name ????????????
         * @param paramTypes ????????????
         * @return [Method]
         * @throws NoSuchMethodError ?????????????????????
         */
        internal fun findMethod(baseClass: Class<*>, name: String, vararg paramTypes: Class<*>) =
            baseClass.getDeclaredMethod(name, *paramTypes).apply { isAccessible = true }

        /**
         * Hook ??????
         *
         * ?????? [XposedBridge.hookMethod]
         * @param hookMethod ?????? Hook ????????????????????????
         * @param callback ??????
         * @return [Pair] - ([Member] or null,[Boolean] ???????????? Hook)
         */
        internal fun hookMethod(hookMethod: Member?, callback: YukiHookCallback): Pair<Member?, Boolean> {
            if (hookedMembers.contains(hookMethod.toString())) return Pair(hookMethod, true)
            hookedMembers.add(hookMethod.toString())
            return Pair(XposedBridge.hookMethod(hookMethod, compatCallback(callback))?.hookedMethod, false)
        }

        /**
         * Hook ?????? [hookClass] ?????? [methodName] ?????????
         *
         * ?????? [XposedBridge.hookAllMethods]
         * @param hookClass ?????? Hook ??? [Class]
         * @param methodName ?????????
         * @param callback ??????
         * @return [Pair] - ([HashSet] ?????? Hook ???????????????,[Boolean] ???????????? Hook)
         */
        internal fun hookAllMethods(hookClass: Class<*>?, methodName: String, callback: YukiHookCallback): Pair<HashSet<Member>, Boolean> {
            var isAlreadyHook = false
            val hookedMembers = HashSet<Member>().also {
                val allMethodsName = "$hookClass$methodName"
                if (hookedAllMethods.contains(allMethodsName)) {
                    isAlreadyHook = true
                    hookClass?.allMethods { _, method -> if (method.name == methodName) it.add(method) }
                    return@also
                }
                hookedAllMethods.add(allMethodsName)
                XposedBridge.hookAllMethods(hookClass, methodName, compatCallback(callback)).takeIf { it.isNotEmpty() }
                    ?.forEach { e -> it.add(e.hookedMethod) }
            }
            return Pair(hookedMembers, isAlreadyHook)
        }

        /**
         * Hook ?????? [hookClass] ??????????????????
         *
         * ?????? [XposedBridge.hookAllConstructors]
         * @param hookClass ?????? Hook ??? [Class]
         * @param callback ??????
         * @return [Pair] - ([HashSet] ?????? Hook ?????????????????????,[Boolean] ???????????? Hook)
         */
        internal fun hookAllConstructors(hookClass: Class<*>?, callback: YukiHookCallback): Pair<HashSet<Member>, Boolean> {
            var isAlreadyHook = false
            val hookedMembers = HashSet<Member>().also {
                val allConstructorsName = "$hookClass<init>"
                if (hookedAllConstructors.contains(allConstructorsName)) {
                    isAlreadyHook = true
                    hookClass?.allConstructors { _, constructor -> it.add(constructor) }
                    return@also
                }
                hookedAllConstructors.add(allConstructorsName)
                XposedBridge.hookAllConstructors(hookClass, compatCallback(callback)).takeIf { it.isNotEmpty() }
                    ?.forEach { e -> it.add(e.hookedMethod) }
            }
            return Pair(hookedMembers, isAlreadyHook)
        }

        /**
         * ???????????? Hook ????????????
         * @param callback [YukiHookCallback] ??????
         * @return [XC_MethodHook] ????????????
         */
        private fun compatCallback(callback: YukiHookCallback) = when (callback) {
            is YukiMemberHook -> object : XC_MethodHook(callback.priority) {

                /** ?????? Hook ??? [HookParamWrapper] */
                val beforeHookWrapper = HookParamWrapper()

                /** ?????? Hook ??? [HookParamWrapper] */
                val afterHookWrapper = HookParamWrapper()

                override fun beforeHookedMethod(param: MethodHookParam?) {
                    if (param == null) return
                    callback.beforeHookedMember(beforeHookWrapper.assign(param))
                }

                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (param == null) return
                    callback.afterHookedMember(afterHookWrapper.assign(param))
                }
            }
            is YukiMemberReplacement -> object : XC_MethodReplacement(callback.priority) {

                /** ???????????? Hook [HookParamWrapper] */
                val replaceHookWrapper = HookParamWrapper()

                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                    if (param == null) return null
                    return callback.replaceHookedMember(replaceHookWrapper.assign(param))
                }
            }
            else -> error("Invalid YukiHookCallback type")
        }

        /**
         * Hook ??????????????????
         * @param priority Hook ????????? - ?????? [PRIORITY_DEFAULT]
         */
        internal abstract class YukiMemberHook(override val priority: Int = PRIORITY_DEFAULT) : YukiHookCallback(priority) {

            /**
             * ???????????????????????????
             * @param wrapper ????????????
             */
            open fun beforeHookedMember(wrapper: HookParamWrapper) {}

            /**
             * ???????????????????????????
             * @param wrapper ????????????
             */
            open fun afterHookedMember(wrapper: HookParamWrapper) {}
        }

        /**
         * Hook ????????????????????????
         * @param priority Hook ?????????- ?????? [PRIORITY_DEFAULT]
         */
        internal abstract class YukiMemberReplacement(override val priority: Int = PRIORITY_DEFAULT) : YukiHookCallback(priority) {

            /**
             * ???????????????????????????
             * @param wrapper ????????????
             * @return [Any] or null
             */
            abstract fun replaceHookedMember(wrapper: HookParamWrapper): Any?
        }

        /**
         * Hook ??????????????????
         * @param priority Hook ?????????
         */
        internal abstract class YukiHookCallback(open val priority: Int)
    }
}