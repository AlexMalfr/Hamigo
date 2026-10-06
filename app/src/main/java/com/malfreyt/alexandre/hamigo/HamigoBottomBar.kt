package com.malfreyt.alexandre.hamigo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.sqrt

internal val HamigoNavigationOverhang = 18.dp
internal val HamigoNavigationCutoutDepth = 69.dp
internal val HamigoNavigationContentOverlap = HamigoNavigationCutoutDepth - HamigoNavigationOverhang
internal val LocalNavigationContentOverlap = staticCompositionLocalOf { 0.dp }
internal const val HamigoEdgeShadowLayers = 10
internal const val HamigoEdgeShadowAlpha = .020f

private data class BottomDestination(val route: String, val label: String, val icon: ImageVector)

private val bottomDestinations = listOf(
    BottomDestination("practice", "Défis", Icons.Rounded.Bolt),
    BottomDestination("resources", "Mémo", Icons.Rounded.MenuBook),
    BottomDestination("path", "Parcours", Icons.Rounded.Route),
    BottomDestination("friends", "Équipe", Icons.Rounded.Groups),
    BottomDestination("profile", "Moi", Icons.Rounded.Person),
)

/** Parcours stays at the heart of the app, with room for its raised button inside the bar's bounds. */
@Composable
fun HamigoBottomBar(
    route: String,
    onDestination: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDestinationBounds: (String, Rect) -> Unit = { _, _ -> },
    friendRequestCount: Int = 0,
) {
    // Reserve the raised part rather than offsetting it outside Scaffold's measured bottom bar.
    // Only the labels grow with text size; the icons, notch and touch targets stay predictable.
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val navigationInsets = WindowInsets.navigationBars
    val labelGrowth = ((density.fontScale.coerceAtLeast(1f) - 1f) * 14f).dp
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val bodyTop = HamigoNavigationOverhang.toPx()
                val leftInset = navigationInsets.getLeft(density, layoutDirection).toFloat()
                val rightInset = navigationInsets.getRight(density, layoutDirection).toFloat()
                val center = Offset((size.width + leftInset - rightInset) / 2f, 34.dp.toPx())
                val radius = 35.dp.toPx()
                val circleBounds = Rect(center.x-radius, center.y-radius, center.x+radius, center.y+radius)
                // Tangent circles round the shoulders and join the notch without a kink.
                val shoulderRadius = 9.dp.toPx()
                val shoulderY = bodyTop + shoulderRadius
                val dy = shoulderY - center.y
                val shoulderX = sqrt((radius+shoulderRadius)*(radius+shoulderRadius)-dy*dy)
                val tangentAngle = Math.toDegrees(atan2(-dy,shoulderX).toDouble()).toFloat()
                val silhouette = Path().apply {
                    moveTo(0f, bodyTop)
                    lineTo(center.x-shoulderX, bodyTop)
                    arcTo(Rect(center.x-shoulderX-shoulderRadius,shoulderY-shoulderRadius,
                        center.x-shoulderX+shoulderRadius,shoulderY+shoulderRadius),-90f,90f+tangentAngle,false)
                    arcTo(circleBounds,180f+tangentAngle,-180f-2f*tangentAngle,false)
                    arcTo(Rect(center.x+shoulderX-shoulderRadius,shoulderY-shoulderRadius,
                        center.x+shoulderX+shoulderRadius,shoulderY+shoulderRadius),180f-tangentAngle,90f+tangentAngle,false)
                    lineTo(size.width, bodyTop)
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                // The same soft edge follows the straight top and the notch. Translucent strokes
                // preserve the real content through the cutout; the white fill covers their inner half.
                for (spread in HamigoEdgeShadowLayers downTo 1) {
                    drawPath(silhouette, Color.Black.copy(alpha = HamigoEdgeShadowAlpha), style = Stroke(spread * 2.dp.toPx()))
                }
                // Paint only the bar itself: the hole reveals the actual scrolling screen below.
                drawPath(silhouette, Color.White)
            }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        Box(Modifier.fillMaxWidth().height(94.dp + labelGrowth)) {
            Row(Modifier.fillMaxSize().selectableGroup(), verticalAlignment = Alignment.Bottom) {
                bottomDestinations.forEach { destination ->
                    val selected = route == destination.route
                    val central = destination.route == "path"
                    val interactionSource = remember(destination.route) { MutableInteractionSource() }
                    val iconColor by animateColorAsState(
                        if (central && selected) Ink else if (central) Color.White else if (selected) Teal else Muted,
                        label = "${destination.route} icon",
                    )
                    val fillColor by animateColorAsState(
                        if (central) { if (selected) Coral else Teal } else { if (selected) Mist else Color.Transparent },
                        label = "${destination.route} background",
                    )
                    Column(
                        Modifier
                            .weight(1f)
                            .height((if (central) 94.dp else 76.dp) + labelGrowth)
                            .selectable(
                                selected = selected,
                                interactionSource = interactionSource,
                                indication = null,
                                role = Role.Tab,
                                onClick = { onDestination(destination.route) },
                            )
                            .semantics(mergeDescendants = true) {}
                            .padding(bottom = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Box(
                            (if (central) {
                                Modifier.size(64.dp).drawBehind {
                                    val buttonRadius=size.minDimension/2f
                                    val haloRadius=buttonRadius+14.dp.toPx()
                                    drawCircle(Brush.radialGradient(
                                        0f to Color.Black.copy(alpha=.10f),
                                        buttonRadius/haloRadius to Color.Black.copy(alpha=.10f),
                                        (buttonRadius+5.dp.toPx())/haloRadius to Color.Black.copy(alpha=.055f),
                                        1f to Color.Transparent,
                                        center=center,radius=haloRadius),radius=haloRadius)
                                }
                                    .clip(CircleShape).background(fillColor)
                            } else {
                                Modifier.width(56.dp).height(40.dp).clip(RoundedCornerShape(18.dp)).background(fillColor)
                            }).onGloballyPositioned { onDestinationBounds(destination.route, it.boundsInWindow()) }
                                .indication(interactionSource, ripple(bounded = false))
                                .testTag("navigation-icon-${destination.route}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(destination.icon, contentDescription = null, Modifier.size(if (central) 30.dp else 24.dp), tint = iconColor)
                            if (destination.route == "friends" && friendRequestCount > 0) {
                                Box(Modifier.align(Alignment.TopEnd).padding(end = 3.dp, top = 3.dp).size(18.dp)
                                    .background(Color(0xFFB3261E), CircleShape)
                                    .testTag("friend-request-badge")
                                    .semantics { contentDescription = "$friendRequestCount demandes d’amis en attente" },
                                    contentAlignment = Alignment.Center) {
                                    Text(if (friendRequestCount > 99) "99+" else friendRequestCount.toString(),
                                        color = Color.White, fontSize = 9.sp, lineHeight = 10.sp, maxLines = 1,
                                        fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            destination.label,
                            modifier = Modifier.height(14.dp + labelGrowth).offset(y=if(central)4.dp else (-3).dp)
                                .padding(horizontal=4.dp)
                                .testTag("navigation-label-${destination.route}"),
                            color = if (selected) Teal else Muted,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
