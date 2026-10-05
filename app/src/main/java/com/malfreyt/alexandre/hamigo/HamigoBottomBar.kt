package com.malfreyt.alexandre.hamigo

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val HamigoNavigationOverhang = 18.dp

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
fun HamigoBottomBar(route: String, onDestination: (String) -> Unit, modifier: Modifier = Modifier) {
    // Reserve the raised part rather than offsetting it outside Scaffold's measured bottom bar.
    // Only the labels grow with text size; the icons, notch and touch targets stay predictable.
    val labelGrowth = ((LocalDensity.current.fontScale.coerceAtLeast(1f) - 1f) * 14f).dp
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind {
                // The raised zone is transparent, so scrolling content stays visible beneath it.
                // Draw through the inset padding as well to keep the system navigation area white.
                val bodyTop = HamigoNavigationOverhang.toPx()
                drawRect(Color.White, topLeft = Offset(0f, bodyTop), size = Size(size.width, (size.height - bodyTop).coerceAtLeast(0f)))
            }
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)),
    ) {
        Box(Modifier.fillMaxWidth().height(94.dp + labelGrowth)) {
            Canvas(Modifier.fillMaxSize()) {
                // Keep the requested 6 dp negative space local to the 64 dp central circle.
                drawCircle(Cream, radius = 38.dp.toPx(), center = Offset(size.width / 2f, 34.dp.toPx()))
            }
            Row(Modifier.fillMaxSize().selectableGroup(), verticalAlignment = Alignment.Bottom) {
                bottomDestinations.forEach { destination ->
                    val selected = route == destination.route
                    val central = destination.route == "path"
                    val interactionSource = remember(destination.route) { MutableInteractionSource() }
                    val iconColor by animateColorAsState(
                        if (central && selected) Color.White else if (central) Ink else if (selected) Teal else Muted,
                        label = "${destination.route} icon",
                    )
                    val fillColor by animateColorAsState(
                        if (central) { if (selected) Teal else Coral } else { if (selected) Mist else Color.Transparent },
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
                                Modifier.size(64.dp).shadow(3.dp, CircleShape).clip(CircleShape).background(fillColor)
                            } else {
                                Modifier.width(56.dp).height(40.dp).clip(RoundedCornerShape(18.dp)).background(fillColor)
                            }).indication(interactionSource, ripple(bounded = false))
                                .testTag("navigation-icon-${destination.route}"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(destination.icon, contentDescription = null, Modifier.size(if (central) 30.dp else 24.dp), tint = iconColor)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            destination.label,
                            modifier = Modifier.height(14.dp + labelGrowth),
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
