package org.openmbee.mms5.lib

import io.ktor.http.ContentType

object MimeTypes {
    object Text {
        object TTL {
            const val extension = "ttl"
            val contentType = ContentType("text", "turtle")
        }
    }
}