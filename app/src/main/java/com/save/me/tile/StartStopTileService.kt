package com.save.me.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.save.me.ForegroundActionService
import com.save.me.R

class StartStopTileService : TileService() {

    override fun onClick() {
        super.onClick()
        val isActive = ForegroundActionService.isRunning(this)
        if (isActive) {
            ForegroundActionService.stop(this)
        } else {
            ForegroundActionService.start(this)
        }
        // After toggling, update the tile state and icon
        setTileState(!isActive)
    }

    override fun onStartListening() {
        // Set the tile state each time the Quick Settings are shown
        setTileState(ForegroundActionService.isRunning(this))
    }

    private fun setTileState(active: Boolean) {
        qsTile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        qsTile.icon = Icon.createWithResource(
            this,
            if (active) R.drawable.ic_qs_tile_pointer_active else R.drawable.ic_qs_tile_pointer
        )
        qsTile.label = if (active) "Remote: Active" else "Remote: Inactive"
        qsTile.updateTile()
    }
}