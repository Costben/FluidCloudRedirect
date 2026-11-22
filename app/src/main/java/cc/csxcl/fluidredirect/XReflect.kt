package cc.csxcl.fluidredirect

import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal object XReflect {

    fun findClass(className: String, classLoader: ClassLoader?): Class<*>? = runCatching {
        Class.forName(className, false, classLoader ?: ClassLoader.getSystemClassLoader())
    }.getOrNull()

    fun findField(clazz: Class<*>, fieldName: String): Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            runCatching {
                return current.getDeclaredField(fieldName).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    fun getObjectField(obj: Any?, fieldName: String): Any? {
        if (obj == null) return null
        return runCatching { findField(obj.javaClass, fieldName)?.get(obj) }.getOrNull()
    }

    fun getStaticObjectField(clazz: Class<*>, fieldName: String): Any? = runCatching {
        findField(clazz, fieldName)?.get(null)
    }.getOrNull()

    fun findMethod(clazz: Class<*>, methodName: String, vararg parameterTypes: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            runCatching {
                return current.getDeclaredMethod(methodName, *parameterTypes).apply { isAccessible = true }
            }
            current = current.superclass
        }
        return null
    }

    fun callMethod(obj: Any?, methodName: String, vararg args: Any?): Any? {
        if (obj == null) return null
        var current: Class<*>? = obj.javaClass
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (method.name == methodName && matchesParams(method.parameterTypes, args)) {
                    method.isAccessible = true
                    return method.invoke(obj, *args)
                }
            }
            current = current.superclass
        }
        return null
    }

    @Suppress("unused")
    fun callStaticMethod(clazz: Class<*>, methodName: String, vararg args: Any?): Any? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            for (method in current.declaredMethods) {
                if (Modifier.isStatic(method.modifiers) &&
                    method.name == methodName &&
                    matchesParams(method.parameterTypes, args)
                ) {
                    method.isAccessible = true
                    return method.invoke(null, *args)
                }
            }
            current = current.superclass
        }
        return null
    }

    private fun matchesParams(paramTypes: Array<Class<*>>, args: Array<out Any?>): Boolean {
        if (paramTypes.size != args.size) return false
        for (i in paramTypes.indices) {
            val arg = args[i] ?: continue
            val expected = paramTypes[i]
            if (expected.isPrimitive) {
                if (!matchesBoxedPrimitive(expected, arg.javaClass)) return false
            } else if (!expected.isAssignableFrom(arg.javaClass)) {
                return false
            }
        }
        return true
    }

    private fun matchesBoxedPrimitive(primitive: Class<*>, boxed: Class<*>): Boolean =
        (primitive == Int::class.javaPrimitiveType && boxed == Int::class.javaObjectType) ||
            (primitive == Boolean::class.javaPrimitiveType && boxed == Boolean::class.javaObjectType) ||
            (primitive == Long::class.javaPrimitiveType && boxed == Long::class.javaObjectType) ||
            (primitive == Float::class.javaPrimitiveType && boxed == Float::class.javaObjectType) ||
            (primitive == Double::class.javaPrimitiveType && boxed == Double::class.javaObjectType) ||
            (primitive == Byte::class.javaPrimitiveType && boxed == Byte::class.javaObjectType) ||
            (primitive == Short::class.javaPrimitiveType && boxed == Short::class.javaObjectType) ||
            (primitive == Char::class.javaPrimitiveType && boxed == Char::class.javaObjectType)
}
