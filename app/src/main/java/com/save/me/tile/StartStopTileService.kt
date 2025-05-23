package com.save.me.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.save.me.ForegroundActionService

class StartStopTileService : TileService() {
    override fun onClick() {
        super.onClick()
        if (ForegroundActionService.isRunning(this)) {
            ForegroundActionService.stop(this)
            qsTile.state = Tile.STATE_INACTIVE
        } else {
            ForegroundActionService.start(this)
            qsTile.state = Tile.STATE_ACTIVE
        }
        qsTile.updateTile()
    }

    override fun onStartListening() {
        qsTile.state = if (ForegroundActionService.isRunning(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.updateTile()
    }
}