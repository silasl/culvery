package uk.co.siland.culvery.provider.lights_hue

/**
 * Hue Bridge answers in CLIP v2's shape, from Hue's CLIP v2 documentation and the household bridge's shapes read from
 * the PC on 2026-10-16 (7 §4.5), with the fields, services and kinds of resource the household bridge sent on 2026-10-19
 * (7 §8.2). Rooms and scenes are generic; the ids, the bridge and the key are made up.
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
           "services":[{"rid":"$LOUNGE_GROUP","rtype":"grouped_light"},{"rid":"22222222-aaaa-4bbb-8ccc-000000000011","rtype":"grouped_motion"},
                       {"rid":"22222222-aaaa-4bbb-8ccc-000000000012","rtype":"grouped_light_level"}],
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
           "on":{"on":true},"dimming":{"brightness":70.4},"dimming_delta":{},"color_temperature":{},"color_temperature_delta":{},
           "color":{},"alert":{"action_values":["breathe"]},"signaling":{"signal_values":["alternating","no_signal","on_off","on_off_color"]},
           "dynamics":{},"type":"grouped_light"},
          {"id":"$KITCHEN_GROUP","id_v1":"/groups/2","owner":{"rid":"$KITCHEN","rtype":"room"},
           "on":{"on":false},"dimming":{"brightness":0.0},"dimming_delta":{},"alert":{"action_values":["breathe"]},
           "signaling":{"signal_values":["no_signal","on_off"]},"dynamics":{},"type":"grouped_light"},
          {"id":"$GARDEN_GROUP","id_v1":"/groups/3","owner":{"rid":"$GARDEN","rtype":"room"},
           "on":{"on":true},"dimming_delta":{},"alert":{"action_values":["breathe"]},"dynamics":{},"type":"grouped_light"},
          {"id":"$HOME_GROUP","id_v1":"/groups/0","owner":{"rid":"77777777-aaaa-4bbb-8ccc-000000000001","rtype":"bridge_home"},
           "on":{"on":true},"dimming_delta":{},"color_temperature":{},"color_temperature_delta":{},
           "color":{},"alert":{"action_values":["breathe"]},"dynamics":{},"type":"grouped_light"}
        ]}
    """.trimIndent()

    /** The shed light is in no room. */
    val LIGHTS = """
        {"errors":[],"data":[
          {"id":"88888888-aaaa-4bbb-8ccc-000000000001","id_v1":"/lights/1","owner":{"rid":"$LOUNGE_LAMP_DEVICE","rtype":"device"},
           "metadata":{"name":"Lounge lamp","archetype":"table_shade","function":"mixed"},"on":{"on":true},
           "dimming":{"brightness":70.4,"min_dim_level":0.2},
           "product_data":{"function":"mixed"},"identify":{},"service_id":0,"dimming_delta":{},
           "color_temperature":{"mirek":369,"mirek_valid":true,"mirek_schema":{"mirek_minimum":153,"mirek_maximum":454}},
           "color_temperature_delta":{},"dynamics":{"status":"none","status_values":["none"],"speed":0.0,"speed_valid":false},
           "alert":{"action_values":["breathe"]},"signaling":{"signal_values":["no_signal","on_off"]},"mode":"normal",
           "effects_v2":{"action":{"effect_values":["no_effect","candle"]},"status":{"effect":"no_effect","effect_values":["no_effect","candle"]}},
           "powerup":{"preset":"powerfail","configured":true,"on":{"mode":"previous"},"dimming":{"mode":"previous"},
                      "color":{"mode":"color_temperature","color_temperature":{"mirek":0}}},"type":"light"},
          {"id":"88888888-aaaa-4bbb-8ccc-000000000002","id_v1":"/lights/2","owner":{"rid":"$LOUNGE_STRIP_DEVICE","rtype":"device"},
           "metadata":{"name":"Lounge strip","archetype":"hue_lightstrip","function":"mixed"},"on":{"on":true},
           "dimming":{"brightness":70.4,"min_dim_level":0.2},
           "product_data":{"function":"mixed"},"identify":{},"service_id":0,"dimming_delta":{},
           "color_temperature":{"mirek":369,"mirek_valid":true,"mirek_schema":{"mirek_minimum":153,"mirek_maximum":454}},
           "color_temperature_delta":{},"dynamics":{"status":"none","status_values":["none"],"speed":0.0,"speed_valid":false},
           "alert":{"action_values":["breathe"]},"signaling":{"signal_values":["no_signal","on_off"]},"mode":"normal",
           "effects_v2":{"action":{"effect_values":["no_effect","candle"]},"status":{"effect":"no_effect","effect_values":["no_effect","candle"]}},
           "powerup":{"preset":"powerfail","configured":true,"on":{"mode":"previous"},"dimming":{"mode":"previous"},
                      "color":{"mode":"color_temperature","color_temperature":{"mirek":0}}},"type":"light"},
          {"id":"88888888-aaaa-4bbb-8ccc-000000000003","id_v1":"/lights/3","owner":{"rid":"$KITCHEN_SPOT_DEVICE","rtype":"device"},
           "metadata":{"name":"Kitchen spot","archetype":"recessed_ceiling","function":"mixed"},"on":{"on":false},
           "dimming":{"brightness":45.0,"min_dim_level":0.2},
           "product_data":{"function":"mixed"},"identify":{},"service_id":0,"dimming_delta":{},
           "color_temperature":{"mirek":369,"mirek_valid":true,"mirek_schema":{"mirek_minimum":153,"mirek_maximum":454}},
           "color_temperature_delta":{},"dynamics":{"status":"none","status_values":["none"],"speed":0.0,"speed_valid":false},
           "alert":{"action_values":["breathe"]},"signaling":{"signal_values":["no_signal","on_off"]},"mode":"normal",
           "effects_v2":{"action":{"effect_values":["no_effect","candle"]},"status":{"effect":"no_effect","effect_values":["no_effect","candle"]}},
           "powerup":{"preset":"powerfail","configured":true,"on":{"mode":"previous"},"dimming":{"mode":"previous"},
                      "color":{"mode":"color_temperature","color_temperature":{"mirek":0}}},"type":"light"},
          {"id":"88888888-aaaa-4bbb-8ccc-000000000004","id_v1":"/lights/4","owner":{"rid":"$GARDEN_PLUG_DEVICE","rtype":"device"},
           "metadata":{"name":"Garden plug","archetype":"plug"},"product_data":{"function":"functional"},"identify":{},
           "service_id":0,"on":{"on":true},"dynamics":{"status":"none","status_values":["none"],"speed":0.0,"speed_valid":false},
           "alert":{"action_values":["breathe"]},"mode":"normal","powerup":{"preset":"safety","configured":true,"on":{"mode":"on","on":{"on":true}}},"type":"light"},
          {"id":"$SHED_LIGHT","id_v1":"/lights/5","owner":{"rid":"$SHED_LIGHT_DEVICE","rtype":"device"},
           "metadata":{"name":"Shed light","archetype":"classic_bulb","function":"mixed"},"on":{"on":true},
           "dimming":{"brightness":100.0,"min_dim_level":0.2},
           "product_data":{"function":"mixed"},"identify":{},"service_id":0,"dimming_delta":{},
           "color_temperature":{"mirek":369,"mirek_valid":true,"mirek_schema":{"mirek_minimum":153,"mirek_maximum":454}},
           "color_temperature_delta":{},"dynamics":{"status":"none","status_values":["none"],"speed":0.0,"speed_valid":false},
           "alert":{"action_values":["breathe"]},"signaling":{"signal_values":["no_signal","on_off"]},"mode":"normal",
           "effects_v2":{"action":{"effect_values":["no_effect","candle"]},"status":{"effect":"no_effect","effect_values":["no_effect","candle"]}},
           "powerup":{"preset":"powerfail","configured":true,"on":{"mode":"previous"},"dimming":{"mode":"previous"},
                      "color":{"mode":"color_temperature","color_temperature":{"mirek":0}}},"type":"light"}
        ]}
    """.trimIndent()

    val SCENES = """
        {"errors":[],"data":[
          {"id":"$RELAX","id_v1":"/scenes/aaaaaaaaaaaaaaaa",
           "actions":[{"target":{"rid":"88888888-aaaa-4bbb-8ccc-000000000001","rtype":"light"},"action":{"on":{"on":true},"dimming":{"brightness":53.75},"color":{"xy":{"x":0.4162,"y":0.4341}}}}],
           "palette":{"color":[{"color":{"xy":{"x":0.4162,"y":0.4341}},"dimming":{"brightness":80.63}}],"dimming":[],"color_temperature":[],"effects":[],"effects_v2":[]},
           "recall":{},"metadata":{"name":"Relax","image":{"rid":"99999999-aaaa-4bbb-8ccc-000000000001","rtype":"public_image"},"appdata":"abcDE_r01_d01"},
           "group":{"rid":"$LOUNGE","rtype":"room"},"speed":0.5,"auto_dynamic":false,
           "status":{"active":"static","last_recall":"2026-10-15T19:00:00.000Z"},"last_actions_update":{"source":"clip"},"type":"scene"},
          {"id":"$BRIGHT","id_v1":"/scenes/bbbbbbbbbbbbbbbb",
           "actions":[{"target":{"rid":"88888888-aaaa-4bbb-8ccc-000000000001","rtype":"light"},"action":{"on":{"on":true},"dimming":{"brightness":100.0},"color_temperature":{"mirek":156}}}],
           "palette":{"color":[],"dimming":[],"color_temperature":[{"color_temperature":{"mirek":156},"dimming":{"brightness":100.0}}],"effects":[],"effects_v2":[]},
           "recall":{},"metadata":{"name":"Bright","image":{"rid":"99999999-aaaa-4bbb-8ccc-000000000002","rtype":"public_image"},"appdata":"fghIJ_r01_d02"},
           "group":{"rid":"$LOUNGE","rtype":"room"},"speed":0.5,"auto_dynamic":false,
           "status":{"active":"inactive"},"last_actions_update":{"source":"clip"},"type":"scene"}
        ]}
    """.trimIndent()

    /** The PM instances stay enabled and running; only `pm_state` says if Mimic presence is on. The Schedules keep `status` running even when off. */
    val BEHAVIORS = """
        {"errors":[],"data":[
          {"id":"$PM_STOPPED","type":"behavior_instance","script_id":"$PM_SCRIPT","enabled":true,"status":"running",
           "state":{"pm_state":"stopped"},"dependees":[],"last_error":"",
           "metadata":{"name":"Mimic presence"},"configuration":{"end_state":"last_state","excluded_lights":[],"mode":"auto","where":[]}},
          {"id":"$PM_STARTED","type":"behavior_instance","script_id":"$PM_SCRIPT","enabled":true,"status":"running",
           "state":{"pm_state":"started"},"dependees":[],"last_error":"",
           "metadata":{"name":"Mimic presence away"},"configuration":{"end_state":"last_state","excluded_lights":[],"mode":"auto","where":[]}},
          {"id":"$SCHEDULE_ON","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":true,"status":"running",
           "state":null,"dependees":[],"last_error":"",
           "metadata":{"name":"Evening lights"},"migrated_from":"/resourcelinks/1001",
           "configuration":{"what":[],"when_extended":{"start_at":{"time_point":{"type":"sunset"}}},"where":[]}},
          {"id":"$SCHEDULE_OFF","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":false,"status":"running",
           "state":null,"dependees":[],"last_error":"",
           "metadata":{"name":"Morning lights"},"migrated_from":"/resourcelinks/1002",
           "configuration":{"what":[],"when_extended":{"start_at":{"time_point":{"type":"time"}}},"where":[]}},
          {"id":"$SWITCH","type":"behavior_instance","script_id":"$SWITCH_SCRIPT","enabled":true,"status":"running",
           "state":{"model_id":"RWL021","source_type":"device"},"dependees":[],"last_error":"",
           "metadata":{"name":"Hall dimmer"},"migrated_from":"/resourcelinks/1003","configuration":{"buttons":{},"device":{},"model_id":"RWL021","where":[]}},
          {"id":"$UNNAMED","type":"behavior_instance","script_id":"$SCHEDULE_SCRIPT","enabled":true,"status":"running",
           "dependees":[],"last_error":"","metadata":{},"configuration":{}}
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
        data: [{"creationtime":"2026-10-16T10:00:00Z","data":[{"id":"$LOUNGE_GROUP","id_v1":"/groups/1","on":{"on":false},"type":"grouped_light"},{"id":"$RELAX","status":{"active":"static","last_recall":"2026-10-16T10:00:00.000Z"},"type":"scene"},{"id":"99999999-aaaa-4bbb-8ccc-000000000011","id_v1":"/lights/7","owner":{"rid":"$SHED_LIGHT_DEVICE","rtype":"device"},"status":"connectivity_issue","type":"zigbee_connectivity"},{"id":"99999999-aaaa-4bbb-8ccc-000000000012","id_v1":"/sensors/3","light":{"light_level":22420,"light_level_report":{"changed":"2026-10-16T10:00:00.000Z","light_level":22420},"light_level_valid":true},"owner":{"rid":"$SHED_LIGHT_DEVICE","rtype":"device"},"type":"light_level"},{"id":"$PM_STARTED","state":{"pm_state":"stopped"},"type":"behavior_instance"},{"id":"88888888-aaaa-4bbb-8ccc-000000000001","id_v1":"/lights/1","on":{"on":false},"dimming":{"brightness":40.0},"color_temperature":{"mirek":null,"mirek_valid":false},"owner":{"rid":"$LOUNGE_LAMP_DEVICE","rtype":"device"},"service_id":0,"type":"light"}],"id":"cccccccc-aaaa-4bbb-8ccc-000000000001","type":"update"}]

        id: 2
        data: [{"creationtime":"2026-10-16T10:00:02Z","data":[{"id":"$BRIGHT","metadata":{"name":"Evening"},"type":"scene"}],"id":"cccccccc-aaaa-4bbb-8ccc-000000000002","type":"add"}]
    """.trimIndent()
}
