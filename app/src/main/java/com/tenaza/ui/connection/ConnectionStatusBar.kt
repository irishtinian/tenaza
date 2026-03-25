package com.tenaza.ui.connection

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tenaza.data.remote.ws.ConnectionState

/**
 * Barra compacta que indica el estado de la conexión WebSocket.
 * Se muestra cuando NO está Connected para no ocupar espacio innecesariamente.
 */
@Composable
fun ConnectionStatusBar(connectionState: ConnectionState) {
    val (color, label) = when (connectionState) {
        is ConnectionState.Connected -> Color(0xFF4CAF50) to "Connected"
        is ConnectionState.Connecting -> Color(0xFFFFC107) to "Connecting..."
        is ConnectionState.Disconnected -> Color(0xFFF44336) to "Disconnected"
        is ConnectionState.Reconnecting -> Color(0xFFFF9800) to "Reconnecting (attempt ${connectionState.attempt})..."
        is ConnectionState.Error -> Color(0xFFF44336) to "Error: ${connectionState.reason}"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Canvas(modifier = Modifier.size(8.dp)) {
            drawCircle(color)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
