package com.mochyy.mochymusic.ui.component

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.constants.EnableGoogleCastKey
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.utils.rememberPreference
import timber.log.Timber
import kotlin.math.roundToInt

// RouteInfo is mutable: copy its visible fields so onRouteChanged actually triggers recomposition.
private data class CastRoute(
    val id: String,
    val name: String,
    val description: String?,
    val deviceType: Int,
    val enabled: Boolean,
    val selected: Boolean,
    val volumeHandling: Int,
    val route: MediaRouter.RouteInfo,
)

@Composable
fun CastDevicesSection() {
    val context = LocalContext.current
    val connection = LocalPlayerConnection.current
    val handler = connection?.service?.castConnectionHandler
    val (castEnabled, setCastEnabled) = rememberPreference(EnableGoogleCastKey, true)
    val isCasting by handler?.isCasting?.collectAsState() ?: remember { mutableStateOf(false) }
    val isConnecting by handler?.isConnecting?.collectAsState() ?: remember { mutableStateOf(false) }
    val deviceName by handler?.castDeviceName?.collectAsState() ?: remember { mutableStateOf(null) }
    val castVolume by handler?.castVolume?.collectAsState() ?: remember { mutableStateOf(1f) }
    val castPlaying by handler?.castIsPlaying?.collectAsState() ?: remember { mutableStateOf(false) }
    val connectionError by handler?.connectionError?.collectAsState() ?: remember { mutableStateOf(null) }
    val metadata by connection?.mediaMetadata?.collectAsState() ?: remember { mutableStateOf(null) }

    var routes by remember { mutableStateOf<List<CastRoute>>(emptyList()) }
    var ready by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var connectingId by remember { mutableStateOf<String?>(null) }
    val selector = remember {
        MediaRouteSelector.Builder()
            .addControlCategory(
                CastMediaControlIntent.categoryForCast(
                    CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID,
                ),
            )
            .build()
    }

    // MediaRouter requires the main thread. Discovery is deliberately scoped to this open sheet.
    DisposableEffect(handler, castEnabled, retry) {
        var router: MediaRouter? = null
        var registered = false
        ready = false
        routes = emptyList()

        fun update(source: MediaRouter) {
            routes = source.routes.asSequence()
                .filter { !it.isDefault && it.matchesSelector(selector) }
                .map {
                    CastRoute(
                        id = it.id,
                        name = it.name,
                        description = it.description,
                        deviceType = it.deviceType,
                        enabled = it.isEnabled,
                        selected = it.isSelected,
                        volumeHandling = it.volumeHandling,
                        route = it,
                    )
                }
                .sortedBy { it.name.lowercase() }
                .toList()
        }

        val callback = object : MediaRouter.Callback() {
            override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = update(router)
            override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = update(router)
            override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = update(router)
            override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = update(router)
            override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo, reason: Int) = update(router)
            override fun onRouteVolumeChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = update(router)
        }

        if (!castEnabled) {
            if (handler?.isCasting?.value == true || handler?.isConnecting?.value == true) {
                handler?.disconnect()
            }
        } else if (handler != null && handler.initialize()) {
            try {
                val discoveredRouter = MediaRouter.getInstance(context.applicationContext)
                router = discoveredRouter
                discoveredRouter.addCallback(
                    selector,
                    callback,
                    MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN,
                )
                registered = true
                update(discoveredRouter)
                ready = true
            } catch (e: Exception) {
                Timber.e(e, "Cannot discover Cast devices")
            }
        }
        onDispose {
            if (registered) router?.removeCallback(callback)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarIcon(active = ready && !isCasting)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Google Cast", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(
                    when {
                        !castEnabled -> "Turn on Cast to find devices"
                        handler == null -> "Starting playback service…"
                        !ready -> "Cast unavailable"
                        isConnecting -> "Connecting…"
                        isCasting -> "Connected on your network"
                        else -> "Searching your Wi-Fi network"
                    },
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            !castEnabled -> CastMessage("Google Cast is off.", action = "Enable", onAction = { setCastEnabled(true) })
            handler == null -> CastMessage("The playback service is starting. Please wait.")
            !ready -> CastMessage("Google Play services or Cast is unavailable.", action = "Retry", onAction = { retry++ })
            else -> {
                if (isCasting) {
                    val selected = routes.firstOrNull { it.selected }
                    ConnectedCastCard(
                        name = deviceName ?: selected?.name ?: "Cast device",
                        isPlaying = castPlaying,
                        isConnecting = isConnecting,
                        volume = castVolume,
                        canSetVolume = selected?.volumeHandling != MediaRouter.RouteInfo.PLAYBACK_VOLUME_FIXED,
                        onVolumeChange = handler::setVolume,
                        onDisconnect = handler::disconnect,
                    )
                    Spacer(Modifier.height(10.dp))
                } else if (metadata == null) {
                    CastMessage("Play a song first to connect to a Cast device.")
                    Spacer(Modifier.height(10.dp))
                }

                if (connectionError != null) {
                    CastMessage(connectionError!!)
                    Spacer(Modifier.height(10.dp))
                }

                val available = if (isCasting) routes.filterNot { it.selected } else routes
                if (available.isEmpty() && !isCasting) {
                    CastMessage("No Cast devices found yet. Make sure both devices are on the same Wi-Fi network.")
                } else if (available.isNotEmpty()) {
                    Text(
                        if (isCasting) "OTHER CAST DEVICES" else "AVAILABLE CAST DEVICES",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp, start = 2.dp),
                    )
                    available.forEach { route ->
                        CastRouteCard(
                            route = route,
                            connecting = isConnecting && (connectingId == route.id || (connectingId == null && route.selected)),
                            canConnect = route.enabled && !isConnecting && (metadata != null || isCasting),
                            onClick = {
                                connectingId = route.id
                                handler.connectToRoute(route.route)
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RadarIcon(active: Boolean) {
    val transition = rememberInfiniteTransition(label = "castRadar")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600), RepeatMode.Restart),
        label = "castRadarPulse",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(42.dp)) {
        Canvas(Modifier.matchParentSize()) {
            if (active) {
                drawCircle(Color.White.copy(alpha = (1f - pulse) * 0.32f), radius = size.minDimension * (0.30f + 0.18f * pulse), style = Stroke(1.5.dp.toPx()))
            }
            drawCircle(Color.White.copy(alpha = 0.08f), radius = size.minDimension * 0.35f)
        }
        Icon(painterResource(R.drawable.cast), contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun CastMessage(message: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MenuRowSurface).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(message, modifier = Modifier.weight(1f).padding(vertical = 5.dp), color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp, lineHeight = 17.sp)
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = Color.White) }
        }
    }
}

@Composable
private fun CastRouteCard(route: CastRoute, connecting: Boolean, canConnect: Boolean, onClick: () -> Unit) {
    val icon = when (route.deviceType) {
        MediaRouter.RouteInfo.DEVICE_TYPE_TV -> R.drawable.cast_tv
        MediaRouter.RouteInfo.DEVICE_TYPE_REMOTE_SPEAKER, MediaRouter.RouteInfo.DEVICE_TYPE_BUILTIN_SPEAKER -> R.drawable.speaker_apple
        else -> R.drawable.cast
    }
    val alpha = if (canConnect || connecting) 1f else 0.5f
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MenuRowSurface)
            .clickable(enabled = canConnect, onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f))) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(route.name, color = Color.White.copy(alpha = alpha), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when {
                    connecting -> "Connecting…"
                    !route.enabled -> "Unavailable"
                    !route.description.isNullOrBlank() -> route.description.orEmpty()
                    else -> "Google Cast"
                },
                color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (connecting) CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
    }
}

@Composable
private fun ConnectedCastCard(
    name: String,
    isPlaying: Boolean,
    isConnecting: Boolean,
    volume: Float,
    canSetVolume: Boolean,
    onVolumeChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(MenuRowSurface).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.cast_connected), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isConnecting) "Switching device…" else "Connected", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            }
            CastEqualizer(isPlaying)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.volume_down), contentDescription = null, tint = Color.White.copy(alpha = 0.65f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Slider(
                value = volume.coerceIn(0f, 1f),
                onValueChange = onVolumeChange,
                enabled = canSetVolume,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Cast volume" },
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.22f)),
            )
            Spacer(Modifier.width(8.dp))
            Text("${(volume.coerceIn(0f, 1f) * 100).roundToInt()}%", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        }
        if (!canSetVolume) Text("Volume is controlled by the device", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
        TextButton(onClick = onDisconnect, modifier = Modifier.align(Alignment.End)) {
            Text("Stop casting", color = Color.White, fontSize = 13.sp)
        }
    }
}

@Composable
private fun CastEqualizer(playing: Boolean) {
    val transition = rememberInfiniteTransition(label = "castEqualizer")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom, modifier = Modifier.height(19.dp)) {
        repeat(3) { index ->
            val height by transition.animateFloat(
                initialValue = 0.28f + index * 0.1f,
                targetValue = 1f - index * 0.12f,
                animationSpec = infiniteRepeatable(tween(450 + index * 180), RepeatMode.Reverse),
                label = "castBar$index",
            )
            Box(Modifier.width(3.dp).height((if (playing) 19f * height else 5f).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.8f)))
        }
    }
}
