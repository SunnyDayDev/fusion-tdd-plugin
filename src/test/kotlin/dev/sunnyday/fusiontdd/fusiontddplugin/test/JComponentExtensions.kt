package dev.sunnyday.fusiontdd.fusiontddplugin.test

import java.awt.Component
import java.awt.Container
import kotlin.reflect.KClass

inline fun <reified T : Component> Container.requireChildByName(name: String): T {
    return requireNotNull(findChildByName<T>(name))
}

inline fun <reified T : Component> Container.findChildByName(name: String): T? {
    return findChildByName(name, T::class)
}

@Suppress("UNCHECKED_CAST") // expected
fun <T : Component> Container.findChildByName(name: String, klass: KClass<T>): T? {
    return findChild { it.name == name && klass.isInstance(it) } as? T
}

fun Container.findChild(predicate: (Component) -> Boolean): Component? {
    components.forEach { component ->
        when {
            predicate.invoke(component) -> return component
            component is Container -> component.findChild(predicate)?.let { return it }
        }
    }

    return null
}