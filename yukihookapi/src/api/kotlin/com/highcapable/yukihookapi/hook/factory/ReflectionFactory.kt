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
 * This file is Created by fankes on 2022/2/2.
 */
@file:Suppress("unused")

package com.highcapable.yukihookapi.hook.factory

import com.highcapable.yukihookapi.hook.bean.CurrentClass
import com.highcapable.yukihookapi.hook.bean.HookClass
import com.highcapable.yukihookapi.hook.core.finder.ConstructorFinder
import com.highcapable.yukihookapi.hook.core.finder.FieldFinder
import com.highcapable.yukihookapi.hook.core.finder.MethodFinder
import com.highcapable.yukihookapi.hook.core.finder.type.ModifierRules
import com.highcapable.yukihookapi.hook.store.MemberCacheStore
import com.highcapable.yukihookapi.hook.xposed.bridge.YukiHookBridge
import de.robv.android.xposed.XposedHelpers
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method

/**
 * [Class] ????????? [HookClass]
 * @return [HookClass]
 */
val Class<*>.hookClass get() = HookClass(instance = this, name)

/**
 * [HookClass] ????????? [Class]
 * @return [Class] or null
 */
val HookClass.normalClass get() = instance

/**
 * ????????????????????????????????????
 *
 * - ???????????????????????? [ClassLoader]
 * @return [Boolean] ????????????
 */
val String.hasClass get() = hasClass(loader = null)

/**
 * ?????? [Class] ????????????????????? - ????????? [Any] ??????????????????????????????
 * @return [Boolean]
 */
val Class<*>.hasExtends get() = superclass.name != "java.lang.Object"

/**
 * ?????????????????????????????????
 * @param name [Class] ???????????????+??????
 * @param loader [Class] ????????? [ClassLoader] - ????????? - ?????????
 * @return [Class]
 * @throws NoClassDefFoundError ??????????????? [Class] ????????????????????? [ClassLoader]
 */
fun classOf(name: String, loader: ClassLoader? = null): Class<*> {
    val hashCode = ("[$name][$loader]").hashCode()
    return MemberCacheStore.findClass(hashCode) ?: run {
        when {
            YukiHookBridge.hasXposedBridge ->
                runCatching { XposedHelpers.findClassIfExists(name, loader) }.getOrNull()
                    ?: when (loader) {
                        null -> Class.forName(name)
                        else -> loader.loadClass(name)
                    }
            loader == null -> Class.forName(name)
            else -> loader.loadClass(name)
        }.also { MemberCacheStore.putClass(hashCode, it) }
    }
}

/**
 * ????????????????????????????????????
 * @param loader [Class] ????????? [ClassLoader]
 * @return [Boolean] ????????????
 */
fun String.hasClass(loader: ClassLoader?) = try {
    classOf(name = this, loader)
    true
} catch (_: Throwable) {
    false
}

/**
 * ????????????????????????
 * @param initiate ?????????
 * @return [Boolean] ????????????
 */
inline fun Class<*>.hasField(initiate: FieldFinder.() -> Unit) = field(initiate).ignoredError().isNoSuch.not()

/**
 * ????????????????????????
 * @param initiate ?????????
 * @return [Boolean] ????????????
 */
inline fun Class<*>.hasMethod(initiate: MethodFinder.() -> Unit) = method(initiate).ignoredError().isNoSuch.not()

/**
 * ??????????????????????????????
 * @param initiate ?????????
 * @return [Boolean] ????????????
 */
inline fun Class<*>.hasConstructor(initiate: ConstructorFinder.() -> Unit) = constructor(initiate).ignoredError().isNoSuch.not()

/**
 * ?????? [Member] ?????????????????????
 * @param initiate ?????????
 * @return [Boolean] ????????????
 */
inline fun Member.hasModifiers(initiate: ModifierRules.() -> Unit) = ModifierRules().apply(initiate).contains(this)

/**
 * ?????????????????????
 * @param initiate ???????????????
 * @return [FieldFinder.Result]
 */
inline fun Class<*>.field(initiate: FieldFinder.() -> Unit) = FieldFinder(classSet = this).apply(initiate).build()

/**
 * ?????????????????????
 * @param initiate ???????????????
 * @return [MethodFinder.Result]
 */
inline fun Class<*>.method(initiate: MethodFinder.() -> Unit) = MethodFinder(classSet = this).apply(initiate).build()

/**
 * ????????????????????????
 * @param initiate ???????????????
 * @return [ConstructorFinder.Result]
 */
inline fun Class<*>.constructor(initiate: ConstructorFinder.() -> Unit) = ConstructorFinder(classSet = this).apply(initiate).build()

/**
 * ????????????????????????????????????
 * @param initiate ?????????
 * @return [T]
 */
inline fun <reified T : Any> T.current(initiate: CurrentClass.() -> Unit): T {
    CurrentClass(javaClass, self = this).apply(initiate)
    return this
}

/**
 * ????????????????????????????????? - ???????????? [Any]
 * @param param ????????????
 * @param initiate ???????????????
 * @return [Any] or null
 */
inline fun Class<*>.buildOfAny(vararg param: Any?, initiate: ConstructorFinder.() -> Unit = {}) = constructor(initiate).get().call(*param)

/**
 * ????????????????????????????????? - ???????????? [T]
 * @param param ????????????
 * @param initiate ???????????????
 * @return [T] or null
 */
inline fun <T> Class<*>.buildOf(vararg param: Any?, initiate: ConstructorFinder.() -> Unit = {}) =
    constructor(initiate).get().newInstance<T>(*param)

/**
 * ?????????????????????????????????
 * @param result ?????? - ([Int] ??????,[Method] ??????)
 */
inline fun Class<*>.allMethods(result: (index: Int, method: Method) -> Unit) =
    declaredMethods.forEachIndexed { p, it -> result(p, it.apply { isAccessible = true }) }

/**
 * ???????????????????????????????????????
 * @param result ?????? - ([Int] ??????,[Constructor] ??????)
 */
inline fun Class<*>.allConstructors(result: (index: Int, constructor: Constructor<*>) -> Unit) =
    declaredConstructors.forEachIndexed { p, it -> result(p, it.apply { isAccessible = true }) }

/**
 * ?????????????????????????????????
 * @param result ?????? - ([Int] ??????,[Field] ??????)
 */
inline fun Class<*>.allFields(result: (index: Int, field: Field) -> Unit) =
    declaredFields.forEachIndexed { p, it -> result(p, it.apply { isAccessible = true }) }