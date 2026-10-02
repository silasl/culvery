package uk.co.siland.culvery

import android.app.admin.DeviceAdminReceiver

/** Device owner for true lock-task (4c design §5.2): no policy beyond allowing lock-task. */
class CulveryDeviceAdmin : DeviceAdminReceiver()
