package uk.co.siland.culvery.provider.lights_hue

/**
 * Hue Bridge answers in CLIP v2's shape, from Hue's CLIP v2 documentation and the household bridge's shapes read from
 * the PC on 2026-10-16 (7 §4.5), redacted; replaced by the wall's captures (7 §8.2). Rooms and scenes are generic; the
 * ids, the bridge and the key are made up.
 */
internal object HueFixtures {
    const val BRIDGE_ID = "001788fffe00a1b2"
    const val KEY = "test-key-Zq81xLmN0pRsTuVwYz2468abcdEFGHijklMNOpqr"

    const val LOUNGE = "11111111-aaaa-4bbb-8ccc-000000000001"
    const val KITCHEN = "11111111-aaaa-4bbb-8ccc-000000000002"
    const val GARDEN = "11111111-aaaa-4bbb-8ccc-000000000003"
    const val LOUNGE_GROUP = "22222222-aaaa-4bbb-8ccc-000000000001"
    const val KITCHEN_GROUP = "22222222-aaaa-4bbb-8ccc-000000000002"
    const val GARDEN_GROUP = "22222222-aaaa-4bbb-8ccc-000000000003"
    const val HOME_GROUP = "22222222-aaaa-4bbb-8ccc-000000000009"
    const val LOUNGE_LAMP_DEVICE = "33333333-aaaa-4bbb-8ccc-000000000001"
    const val LOUNGE_STRIP_DEVICE = "33333333-aaaa-4bbb-8ccc-000000000002"
    const val KITCHEN_SPOT_DEVICE = "33333333-aaaa-4bbb-8ccc-000000000003"
    const val GARDEN_PLUG_DEVICE = "33333333-aaaa-4bbb-8ccc-000000000004"
    const val SHED_LIGHT_DEVICE = "33333333-aaaa-4bbb-8ccc-000000000005"
    const val SHED_LIGHT = "88888888-aaaa-4bbb-8ccc-000000000005"
    const val RELAX = "44444444-aaaa-4bbb-8ccc-000000000001"
    const val BRIGHT = "44444444-aaaa-4bbb-8ccc-000000000002"
    const val PM_STOPPED = "55555555-aaaa-4bbb-8ccc-000000000001"
    const val PM_STARTED = "55555555-aaaa-4bbb-8ccc-000000000002"
    const val SCHEDULE_ON = "55555555-aaaa-4bbb-8ccc-000000000003"
    const val SCHEDULE_OFF = "55555555-aaaa-4bbb-8ccc-000000000004"
    const val SWITCH = "55555555-aaaa-4bbb-8ccc-000000000005"
    const val UNNAMED = "55555555-aaaa-4bbb-8ccc-000000000006"
    const val PM_SCRIPT = "66666666-aaaa-4bbb-8ccc-000000000001"
    const val SCHEDULE_SCRIPT = "66666666-aaaa-4bbb-8ccc-000000000002"
    const val SWITCH_SCRIPT = "66666666-aaaa-4bbb-8ccc-000000000003"

    val ROOMS = """
        {"errors":[],"data":[
          {"id":"$LOUNGE","id_v1":"/groups/1",
           "children":[{"rid":"$LOUNGE_LAMP_DEVICE","rtype":"device"},{"rid":"$LOUNGE_STRIP_DEVICE","rtype":"device"}],
           "services":[{"rid":"$LOUNGE_GROUP","rtype":"grouped_light"}],
           "metadata":{"name":"Lounge","archetype":"living_room"},"type":"room"},
          {"id":"$KITCHEN","id_v1":"/groups/2",
           "children":[{"rid":"$KITCHEN_SPOT_DEVICE","rtype":"device"}],
           "services":[{"rid":"$KITCHEN_GROUP","rtype":"grouped_light"}],
           "metadata":{"name":"Kitchen","archetype":"kitchen"},"type":"room"},
          {"id":"$GARDEN","id_v1":"/groups/3",
           "children":[{"rid":"$GARDEN_PLUG_DEVICE","rtype":"device"}],
           "services":[{"rid":"$GARDEN_GROUP","rtype":"grouped_light"}],
           "metadata":{"name":"Garden","archetype":"garden"},"type":"room"}
        ]}
    """.trimIndent()

    val GROUPED_LIGHTS = """
        {"errors":[],"data":[
          {"id":"$LOUNGE_GROUP","id_v1":"/groups/1","owner":{"rid":"$LOUNGE","rtype":"room"},
           "on":{"on":true},"dimming":{"brightness":70.4},"dimming_delta":{},"alert":{"action_values":["breathe"]},
           "type":"grouped_light"},
          {"id":"$KITCHEN_GROUP","id_v1":"/groups/2","owner":{"rid":"$KITCHEN","rtype":"room"},
           "on":{"on":false},"dimming":{"brightness":0.0},"dimming_delta":{},"type":"grouped_light"},
          {"id":"$GARDEN_GROUP","id_v1":"/groups/3","owner":{"rid":"$GARDEN","rtype":"room"},
           "on":{"on":true},"type":"grouped_light"},
          {"id":"$HOME_GROUP","id_v1":"/groups/0","owner":{"rid":"77777777-aaaa-4bbb-8ccc-000000000001","rtype":"bridge_home"},
           "on":{"on":true},"type":"grouped_light"}
        ]}
    """.trimIndent()

    /** The shed light is in no room. */
    val LIGHTS = """
        {"errors":[],"data":[
          {"id":"88888888-aaaa-4bbb-8ccc-000000000001","id_v1":"/lights/1","owner":{"rid":"$LOUNGE_LAMP_DEVICE","rtype":"device"},
           "metadata":{"name":"Lounge lamp","archetype":"table_shade"},"on":{"on":true},
           "dimming":{"brightness":70.4,"min_dim_level":0.2},"type":"light"},
          {"id":"88888888-aaaa-4bbb-8ccc-000000000002","id_v1":"/lights/2","owner":{"rid":"$LOUNGE_STRIP_DEVICE","rtype":"device"},
           "metadata":{"name":"Lounge strip","archetype":"hue_lightstrip"},"on":{"on":true},
           "dimming":{"brightness":70.4,"min_dim_level":0.2},"type":"light"},
          {"id":"88888888-aaaa-4bbb-8ccc-000000000003","id_v1":"/lights/3","owner":{"rid":"$KITCHEN_SPOT_DEVICE","rtype":"device"},
           "metadata":{"name":"Kitchen spot","archetype":"recessed_ceiling"},"on":{"on":false},
           "dimming":{"brightness":45.0,"min_dim_level":0.2},"type":"light"},
          {"id":"88888888-aaaa-4bbb-8ccc-000000000004","id_v1":"/lights/4","owner":{"rid":"$GARDEN_PLUG_DEVICE","rtype":"device"},
           "metadata":{"name":"Garden plug","archetype":"plug"},"on":{"on":true},"type":"light"},
          {"id":"$SHED_LIGHT","id_v1":"/lights/5","owner":{"rid":"$SHED_LIGHT_DEVICE","rtype":"device"},
           "metadata":{"name":"Shed light","archetype":"classic_bulb"},"on":{"on":true},
           "dimming":{"brightness":100.0,"min_dim_level":0.2},"type":"light"}
        ]}
    """.trimIndent()

    val SCENES = """
        {"errors":[],"data":[
          {"id":"$RELAX","id_v1":"/scenes/aaaaaaaaaaaaaaaa","metadata":{"name":"Relax","image":{"rid":"99999999-aaaa-4bbb-8ccc-000000000001","rtype":"public_image"}},
           "group":{"rid":"$LOUNGE","rtype":"room"},"status":{"active":"static"},"type":"scene"},
          {"id":"$BRIGHT","id_v1":"/scenes/bbbbbbbbbbbbbbbb","metadata":{"name":"Bright"},
           "group":{"rid":"$LOUNGE","rtype":"room"},"status":{"active":"inactive"},"type":"scene"}
        ]}
    """.trimIndent()

    /** The PM instances stay enabled and running; only `pm_state` says if Mimic presence is on. The Schedules keep `status` running even when off. */
    val BEHAVIORS = """
        {"errors":[],"data":[
          {"id":"$PM_STOPPED","type":"behavior_instance","script_id":"$PM_SCRIPT","enabled":true,"status":"running",
           "state":{"pm_state":"stopped"},"dependees":[],"last_error":"",
           "metadata":{"name":"Mimic presence"},"configuration":{"rooms":[]}},
          {"id":"$PM_STARTED","type":"behavior_instance","script_id":"$PM_SCRIPT","enabled":true,"status":"running",
           "state":{"pm_state":"started"},"dependees":[],"last_error":"",
           "metadata":{"name":"Mimic presence away"},"configuration":{"rooms":[]}},
          {"id":"$SCHEDULE_ON","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":true,"status":"running",
           "state":null,"dependees":[],"last_error":"",
           "metadata":{"name":"Evening lights"},"configuration":{"when":{"time_point":{"type":"sunset"}}}},
          {"id":"$SCHEDULE_OFF","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":false,"status":"running",
           "state":null,"dependees":[],"last_error":"",
           "metadata":{"name":"Morning lights"},"configuration":{"when":{"time_point":{"type":"time"}}}},
          {"id":"$SWITCH","type":"behavior_instance","script_id":"$SWITCH_SCRIPT","enabled":true,"status":"running",
           "state":{"model_id":"RWL021","source_type":"device"},"dependees":[],"last_error":"",
           "metadata":{"name":"Hall dimmer"},"configuration":{}},
          {"id":"$UNNAMED","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":true,"status":"running",
           "state":null,"dependees":[],"last_error":"","metadata":{},"configuration":{}}
        ]}
    """.trimIndent()

    val CONFIG = """
        {"name":"Hue Bridge","datastoreversion":"172","swversion":"1978418030","apiversion":"1.78.0",
         "mac":"00:17:88:a1:b2:c3","bridgeid":"$BRIDGE_ID","factorynew":false,"replacesbridgeid":"ecb5fafffe9a1b2c",
         "modelid":"BSB002","ipaddress":"192.168.1.30","gateway":"192.168.1.1","netmask":"255.255.255.0",
         "proxyaddress":"none",
         "whitelist":{"$KEY":{"last use date":"2026-10-16T10:00:00","create date":"2026-10-01T09:00:00","name":"culvery#tablet"}}}
    """.trimIndent()

    val PAIRED = """[{"success":{"username":"$KEY","clientkey":"0123456789ABCDEF0123456789ABCDEF"}}]"""

    val NOT_PRESSED = """[{"error":{"type":101,"address":"","description":"link button not pressed"}}]"""

    val UNAUTHORISED = """{"errors":[{"description":"unauthorized user"}]}"""

    val UNAUTHORISED_V1 = """[{"error":{"type":1,"address":"/","description":"unauthorized user"}}]"""

    /** A stream body: a comment, ids, a grouped_light update, a scene status update and an add. */
    val EVENTS = """
        : hi
        id: 1
        data: [{"creationtime":"2026-10-16T10:00:00Z","data":[{"id":"$LOUNGE_GROUP","id_v1":"/groups/1","on":{"on":false},"type":"grouped_light"},{"id":"$RELAX","status":{"active":"static"},"type":"scene"}],"id":"cccccccc-aaaa-4bbb-8ccc-000000000001","type":"update"}]

        id: 2
        data: [{"creationtime":"2026-10-16T10:00:02Z","data":[{"id":"$BRIGHT","metadata":{"name":"Evening"},"type":"scene"}],"id":"cccccccc-aaaa-4bbb-8ccc-000000000002","type":"add"}]
    """.trimIndent()
}
