package uk.co.siland.culvery.provider.music_sonos

/** The speakers' UPnP services on port 1400 (6 §3.2). */
internal enum class SonosService(val path: String, val urn: String) {
    AV_TRANSPORT("/MediaRenderer/AVTransport/Control", "urn:schemas-upnp-org:service:AVTransport:1"),
    RENDERING("/MediaRenderer/RenderingControl/Control", "urn:schemas-upnp-org:service:RenderingControl:1"),
    GROUP_RENDERING("/MediaRenderer/GroupRenderingControl/Control", "urn:schemas-upnp-org:service:GroupRenderingControl:1"),
    TOPOLOGY("/ZoneGroupTopology/Control", "urn:schemas-upnp-org:service:ZoneGroupTopology:1"),
    DEVICE("/DeviceProperties/Control", "urn:schemas-upnp-org:service:DeviceProperties:1"),
    CONTENT("/MediaServer/ContentDirectory/Control", "urn:schemas-upnp-org:service:ContentDirectory:1"),
}

private const val ENVELOPE_START =
    "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>"
private const val ENVELOPE_END = "</s:Body></s:Envelope>"

/** A SOAP request: [args] in the order the service declares them, each escaped. */
internal fun envelope(service: SonosService, action: String, args: List<Pair<String, String>>): String = buildString {
    append(ENVELOPE_START)
    append("<u:$action xmlns:u=\"${service.urn}\">")
    args.forEach { (name, value) -> append("<$name>${escapeXml(value)}</$name>") }
    append("</u:$action>")
    append(ENVELOPE_END)
}

internal fun soapAction(service: SonosService, action: String): String = "\"${service.urn}#$action\""

internal fun escapeXml(text: String): String = buildString(text.length) {
    text.forEach { ch ->
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            else -> append(ch)
        }
    }
}
