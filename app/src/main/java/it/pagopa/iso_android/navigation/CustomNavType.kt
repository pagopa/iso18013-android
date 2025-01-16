package it.pagopa.iso_android.navigation

import android.net.Uri
import android.os.Bundle
import androidx.navigation.NavType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Use this to manage arguments in navigation for ***custom*** types.
 * example usage:
 * ```kotlin
 * composable<YOUR_ROUTE>(
 *      typeMap = mapOf(
 *      typeOf<YOUR_SERIALIZABLE_CLASS>() to toCustomNavType<YOUR_SERIALIZABLE_CLASS>()
 *    )
 *)
 * ```*/
inline fun <reified T> toCustomNavType() = object : NavType<T>(
    isNullableAllowed = true
) {
    override fun get(bundle: Bundle, key: String): T? {
        return Json.decodeFromString(bundle.getString(key) ?: return null)
    }

    override fun parseValue(value: String): T {
        return Json.decodeFromString(Uri.decode(value))
    }

    override fun serializeAsValue(value: T): String {
        return Uri.encode(Json.encodeToString(value))
    }

    override fun put(bundle: Bundle, key: String, value: T) {
        bundle.putString(key, Json.encodeToString(value))
    }
}