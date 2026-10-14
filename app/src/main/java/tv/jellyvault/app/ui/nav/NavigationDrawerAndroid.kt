/*
 * This file contains code copied and modified from:
 * https://android.googlesource.com/platform/frameworks/support/+/refs/heads/androidx-androidx-tv-material-release/tv/tv-material/src/main/java/androidx/tv/material3/
 *
 * Their license & required attribution is below.
 *
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package tv.jellyvault.app.ui.nav

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemColors
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.rememberDrawerState
import tv.jellyvault.app.ui.theme.jv.JvMotion
import tv.jellyvault.app.ui.theme.jv.JvShapes
import tv.jellyvault.app.ui.theme.jv.jvMillis
import tv.jellyvault.app.ui.theme.jv.rememberAnimatorScale

/**
 * This is a re-implementation of [androidx.tv.material3.ModalNavigationDrawer].
 *
 * It changes padding, sizing, and animations that couldn't be changed in the Androidx implementation.
 */
@Composable
fun ModalNavigationDrawer(
    drawerContent: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
    modifier: Modifier = Modifier,
    drawerState: DrawerState = rememberDrawerState(DrawerValue.Closed),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        content()
        // Drawn after the content: the open rail covers it instead of pushing it
        DrawerSheet(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .then(
                        if (drawerState.currentValue == DrawerValue.Open) {
                            // Solid with a clean edge: a fading edge let the page show through blurred
                            Modifier.background(MaterialTheme.colorScheme.background)
                        } else {
                            Modifier
                        },
                    ),
            drawerState = drawerState,
            content = drawerContent,
        )
    }
}

@Composable
private fun DrawerSheet(
    modifier: Modifier = Modifier,
    drawerState: DrawerState = remember { DrawerState() },
    content: @Composable NavigationDrawerScope.(DrawerValue) -> Unit,
) {
    // indicates that the drawer has been set to its initial state and has grabbed focus if
    // necessary. Controls whether focus is used to decide the state of the drawer going forward.
    var initializationComplete: Boolean by remember { mutableStateOf(false) }
    var focusState by remember { mutableStateOf<FocusState?>(null) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(key1 = drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open && focusState?.hasFocus == false) {
            // used to grab focus if the drawer state is set to Open on start.
            focusRequester.requestFocus()
        }
        initializationComplete = true
    }

    val internalModifier =
        Modifier
            .focusRequester(focusRequester)
            .animateContentSize(
                animationSpec = tween(jvMillis(JvMotion.DRAWER_MILLIS, rememberAnimatorScale()), easing = JvMotion.Easing),
            ).fillMaxHeight()
            // adding passed-in modifier here to ensure animateContentSize is called before other
            // size based modifiers.
            .then(modifier)
            .onFocusChanged {
                focusState = it

                if (initializationComplete) {
                    drawerState.setValue(if (it.hasFocus) DrawerValue.Open else DrawerValue.Closed)
                }
            }.focusGroup()

    Box(modifier = internalModifier) {
        NavigationDrawerScopeImpl(drawerState.currentValue == DrawerValue.Open).apply {
            content(drawerState.currentValue)
        }
    }
}

internal class NavigationDrawerScopeImpl(
    override val hasFocus: Boolean,
) : NavigationDrawerScope

internal val CollapsedDrawerItemWidth = 56.dp
internal val ExpandedDrawerItemWidth = 220.dp
internal val DrawerIconSize = 20.dp

/** Small padding so the 20 dp icons are not squeezed in the 56 dp rail */
internal val DrawerIconPadding = 2.dp

@Composable
internal fun NavigationDrawerScope.NavigationDrawerItem(
    selected: Boolean,
    onClick: () -> Unit,
    leadingContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    tonalElevation: Dp = NavigationDrawerItemDefaults.NavigationDrawerItemElevation,
    colors: NavigationDrawerItemColors = NavigationDrawerItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    val animatedWidth by
        animateDpAsState(
            animationSpec = tween(jvMillis(JvMotion.DRAWER_MILLIS, rememberAnimatorScale()), easing = JvMotion.Easing),
            targetValue =
                if (hasFocus) {
                    ExpandedDrawerItemWidth
                } else {
                    CollapsedDrawerItemWidth
                },
            label = "NavigationDrawerItem width open/closed state of the drawer item",
        )
    val navDrawerItemHeight =
        if (supportingContent == null) {
            NavigationDrawerItemDefaults.ContainerHeightOneLine
        } else {
            NavigationDrawerItemDefaults.ContainerHeightTwoLine
        }

    ListItem(
        selected = selected,
        onClick = onClick,
        headlineContent = content,
        leadingContent = {
            Box(
                Modifier
                    .padding(horizontal = DrawerIconPadding)
                    .size(DrawerIconSize),
            ) { leadingContent() }
        },
        trailingContent = trailingContent,
        supportingContent = supportingContent,
        modifier =
            modifier
                .layout { measurable, constraints ->
                    val width = animatedWidth.roundToPx()
                    val height = navDrawerItemHeight.roundToPx()
                    val placeable =
                        measurable.measure(
                            constraints.copy(
                                minWidth = width,
                                maxWidth = width,
                                minHeight = height,
                                maxHeight = height,
                            ),
                        )
                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                },
        enabled = enabled,
        onLongClick = onLongClick,
        tonalElevation = tonalElevation,
        colors = colors.toToggleableListItemColors(hasFocus),
        shape = ListItemDefaults.shape(shape = JvShapes.Button),
        scale = ListItemDefaults.scale(1f, 1f),
        interactionSource = interactionSource,
    )
}

@Composable
private fun NavigationDrawerItemColors.toToggleableListItemColors(doesNavigationDrawerHaveFocus: Boolean) =
    ListItemDefaults.colors(
        containerColor = containerColor,
        contentColor = if (doesNavigationDrawerHaveFocus) contentColor else inactiveContentColor,
        focusedContainerColor = focusedContainerColor,
        focusedContentColor = focusedContentColor,
        pressedContainerColor = pressedContainerColor,
        pressedContentColor = pressedContentColor,
        selectedContainerColor = selectedContainerColor,
        selectedContentColor = selectedContentColor,
        disabledContainerColor = disabledContainerColor,
        disabledContentColor =
            if (doesNavigationDrawerHaveFocus) {
                disabledContentColor
            } else {
                disabledInactiveContentColor
            },
        focusedSelectedContainerColor = focusedSelectedContainerColor,
        focusedSelectedContentColor = focusedSelectedContentColor,
        pressedSelectedContainerColor = pressedSelectedContainerColor,
        pressedSelectedContentColor = pressedSelectedContentColor,
    )
