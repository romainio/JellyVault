package tv.jellyvault.app.ui.nav

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.DrawerState
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.ProvideTextStyle
import androidx.tv.material3.Text
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.imageApi
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import tv.jellyvault.app.R
import tv.jellyvault.app.data.model.JellyfinServer
import tv.jellyvault.app.data.model.JellyfinUser
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.services.BackdropService
import tv.jellyvault.app.services.MusicService
import tv.jellyvault.app.services.NavDrawerService
import tv.jellyvault.app.services.NavigationManager
import tv.jellyvault.app.services.SetupDestination
import tv.jellyvault.app.services.SetupNavigationManager
import tv.jellyvault.app.ui.FontAwesome
import tv.jellyvault.app.ui.components.TimeDisplay
import tv.jellyvault.app.ui.ifElse
import tv.jellyvault.app.ui.launchDefault
import tv.jellyvault.app.ui.preferences.PreferenceScreenOption
import tv.jellyvault.app.ui.setup.UserIconCardImage
import tv.jellyvault.app.ui.spacedByWithFooter
import tv.jellyvault.app.ui.toServerString
import tv.jellyvault.app.ui.tryRequestFocus
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NavDrawerViewModel
    @Inject
    constructor(
        private val api: ApiClient,
        private val navDrawerService: NavDrawerService,
        val navigationManager: NavigationManager,
        val setupNavigationManager: SetupNavigationManager,
        val backdropService: BackdropService,
        private val musicService: MusicService,
    ) : ViewModel() {
        val serviceState = navDrawerService.state

        private val _state = MutableStateFlow(NavDrawerState())
        val state: StateFlow<NavDrawerState> = _state

        fun onClickDrawerItem(
            index: Int,
            item: NavDrawerItem,
        ) {
            when (item) {
                NavDrawerItem.Favorites -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(
                        Destination.Favorites,
                    )
                }

                NavDrawerItem.MyLibrary -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(Destination.MyLibrary)
                }

                NavDrawerItem.More -> {
                    setShowMore(!state.value.moreExpanded)
                }

                is ServerNavDrawerItem -> {
                    setIndex(index)
                    navigationManager.navigateToFromDrawer(item.destination)
                }
            }
        }

        fun setIndex(index: Int) {
            _state.update { it.copy(selectedIndex = index) }
        }

        fun setShowMore(value: Boolean) {
            _state.update { it.copy(moreExpanded = value) }
        }

        fun getUserImage(user: JellyfinUser): String = api.imageApi.getUserImageUrl(user.id)

        /**
         * Determine which nav drawer item should be highlighted as currently selected
         */
        fun updateSelectedIndex() {
            viewModelScope.launchDefault {
                val asDestinations =
                    buildList {
                        addAll(serviceState.value.items)
                        addAll(serviceState.value.moreItems)
                    }.map {
                        when (it) {
                            is ServerNavDrawerItem -> it.destination
                            is NavDrawerItem.Favorites -> Destination.Favorites
                            is NavDrawerItem.MyLibrary -> Destination.MyLibrary
                            else -> null
                        }
                    }

                val backstack = navigationManager.backStack.toList().reversed()
                for (i in 0..<backstack.size) {
                    val key = backstack[i]
                    val index =
                        if (key is Destination.Home) {
                            HOME_INDEX
                        } else if (key is Destination.Search) {
                            SEARCH_INDEX
                        } else if (key is Destination.NowPlaying) {
                            NOW_PLAYING_INDEX
                        } else {
                            val idx = asDestinations.indexOf(key)
                            if (idx >= 0) {
                                idx
                            } else {
                                null
                            }
                        }
                    Timber.v("Found $index => $key")
                    if (index != null) {
                        _state.update { it.copy(selectedIndex = index) }
                        break
                    }
                }
            }
        }

        fun navigateToSetup(userList: SetupDestination) {
            viewModelScope.launchDefault {
                musicService.stop()
                setupNavigationManager.navigateTo(userList)
            }
        }
    }

data class NavDrawerState(
    val selectedIndex: Int = -1,
    val moreExpanded: Boolean = false,
)

/**
 * An item that can be shown in the nav drawer
 *
 * Some are built-in such as Favorites. Others are created dynamically for libraries.
 */
sealed interface NavDrawerItem {
    val id: String

    fun name(context: Context): String

    object Favorites : NavDrawerItem {
        override val id: String
            get() = "a_favorites"

        override fun name(context: Context): String = context.getString(R.string.favorites)
    }

    object MyLibrary : NavDrawerItem {
        override val id: String
            get() = "a_mylibrary"

        override fun name(context: Context): String = context.getString(R.string.my_library)
    }

    object More : NavDrawerItem {
        override val id: String
            get() = "a_more"

        override fun name(context: Context): String = context.getString(R.string.more)
    }
}

/**
 * A server provided nav drawer item, typically a library
 */
data class ServerNavDrawerItem(
    val itemId: UUID,
    val name: String,
    val destination: Destination,
    val type: CollectionType,
) : NavDrawerItem {
    override val id: String = getId(itemId)

    override fun name(context: Context): String = name

    companion object {
        fun getId(itemId: UUID) = "s_" + itemId.toServerString()
    }
}

private const val HOME_INDEX = -1
private const val SEARCH_INDEX = -2
private const val NOW_PLAYING_INDEX = -3

/**
 * Display the left side navigation drawer with [DestinationContent] on the right
 */
@Composable
fun NavDrawer(
    destination: Destination,
    preferences: UserPreferences,
    user: JellyfinUser,
    server: JellyfinServer,
    drawerState: DrawerState,
    navDrawerListState: LazyListState,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NavDrawerViewModel =
        hiltViewModel(
            LocalView.current.findViewTreeViewModelStoreOwner()!!,
            key = "${server.id}_${user.id}", // Keyed to the server & user to ensure its reset when switching either
        ),
) {
    LaunchedEffect(Unit) { viewModel.updateSelectedIndex() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current

    val focusRequester = remember { FocusRequester() }

    // If the user presses back while on the home page, open the nav drawer, another back press will quit the app
    BackHandler(enabled = (drawerState.currentValue == DrawerValue.Closed && destination is Destination.Home)) {
        drawerState.setValue(DrawerValue.Open)
        focusRequester.requestFocus()
    }
    val serviceState by viewModel.serviceState.collectAsState()
    val state by viewModel.state.collectAsState()
    val moreExpanded = state.moreExpanded
    // A negative index is a built-in page, >=0 is a library
    val selectedIndex = state.selectedIndex

    BackHandler(enabled = moreExpanded && drawerState.currentValue == DrawerValue.Open) {
        viewModel.setShowMore(false)
    }

    val closedDrawerWidth = CollapsedDrawerItemWidth

    ModalNavigationDrawer(
        modifier = modifier,
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            val isOpen = drawerValue.isOpen
            val spacedBy = 4.dp
            val searchFocusRequester = remember { FocusRequester() }

            ProvideTextStyle(MaterialTheme.typography.labelMedium) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(spacedBy),
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    // Even though some must be clicked, focusing on it should clear other focused items
                    val interactionSource = remember { MutableInteractionSource() }
                    val userImageUrl = remember(user) { viewModel.getUserImage(user) }
                    ProfileIcon(
                        user = user,
                        imageUrl = userImageUrl,
                        serverName = server.name ?: server.url,
                        drawerOpen = isOpen,
                        interactionSource = interactionSource,
                        onClick = {
                            viewModel.navigateToSetup(
                                SetupDestination.UserList(server),
                            )
                        },
                        modifier = Modifier,
                    )
                    AnimatedVisibility(
                        visible = serviceState.nowPlayingEnabled,
                        enter = expandVertically(expandFrom = Alignment.Top),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top),
                    ) {
                        val interactionSource = remember { MutableInteractionSource() }
                        IconNavItem(
                            text = stringResource(R.string.now_playing),
                            subtext = serviceState.nowPlayingTitle,
                            icon = Icons.Default.PlayArrow,
                            selected = selectedIndex == NOW_PLAYING_INDEX,
                            drawerOpen = isOpen,
                            interactionSource = interactionSource,
                            onClick = {
                                viewModel.setIndex(NOW_PLAYING_INDEX)
                                viewModel.navigationManager.navigateTo(Destination.NowPlaying)
                            },
                            modifier =
                                Modifier
                                    .ifElse(
                                        selectedIndex == NOW_PLAYING_INDEX,
                                        Modifier.focusRequester(focusRequester),
                                    ),
                        )
                    }
                    LazyColumn(
                        state = navDrawerListState,
                        contentPadding = PaddingValues(0.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedByWithFooter(spacedBy),
                        modifier =
                            Modifier
                                .focusGroup()
                                .focusProperties {
                                    onEnter = {
                                        if (requestedFocusDirection == FocusDirection.Down) {
                                            searchFocusRequester.tryRequestFocus()
                                        } else {
                                            focusRequester.tryRequestFocus()
                                        }
                                    }
                                }.fillMaxHeight(),
                    ) {
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.search),
                                icon = Icons.Default.Search,
                                selected = selectedIndex == SEARCH_INDEX,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.setIndex(SEARCH_INDEX)
                                    viewModel.navigationManager.navigateToFromDrawer(Destination.Search())
                                },
                                modifier =
                                    Modifier
                                        .focusRequester(searchFocusRequester)
                                        .ifElse(
                                            selectedIndex == SEARCH_INDEX,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        }
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.home),
                                icon = Icons.Default.Home,
                                selected = selectedIndex == HOME_INDEX,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.setIndex(HOME_INDEX)
                                    if (destination is Destination.Home) {
                                        viewModel.navigationManager.reloadHome()
                                        onClearBackdrop.invoke()
                                    } else {
                                        viewModel.navigationManager.goToHome()
                                    }
                                },
                                modifier =
                                    Modifier
                                        .ifElse(
                                            selectedIndex == HOME_INDEX,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        }
                        itemsIndexed(serviceState.items) { index, it ->
                            val interactionSource = remember { MutableInteractionSource() }
                            NavItem(
                                library = it,
                                selected = selectedIndex == index,
                                moreExpanded = moreExpanded,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.onClickDrawerItem(index, it)
                                },
                                modifier =
                                    Modifier
                                        .ifElse(
                                            selectedIndex == index,
                                            Modifier.focusRequester(focusRequester),
                                        ),
                            )
                        }
                        if (serviceState.moreItems.isNotEmpty()) {
                            item {
                                val index = serviceState.items.size
                                val interactionSource = remember { MutableInteractionSource() }
                                NavItem(
                                    library = NavDrawerItem.More,
                                    selected = false,
                                    moreExpanded = moreExpanded,
                                    drawerOpen = isOpen,
                                    interactionSource = interactionSource,
                                    onClick = {
                                        viewModel.onClickDrawerItem(index, NavDrawerItem.More)
                                    },
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                selectedIndex == index,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                        }
                        if (moreExpanded) {
                            itemsIndexed(serviceState.moreItems) { index, it ->
                                val adjustedIndex =
                                    remember(serviceState) { (index + serviceState.items.size) }
                                val interactionSource = remember { MutableInteractionSource() }
                                NavItem(
                                    library = it,
                                    selected = selectedIndex == adjustedIndex,
                                    moreExpanded = moreExpanded,
                                    drawerOpen = isOpen,
                                    onClick = {
                                        viewModel.onClickDrawerItem(
                                            adjustedIndex,
                                            it,
                                        )
                                    },
                                    containerColor =
                                        if (isOpen) {
                                            MaterialTheme.colorScheme.surface.copy(alpha = .5f)
                                        } else {
                                            Color.Unspecified
                                        },
                                    interactionSource = interactionSource,
                                    modifier =
                                        Modifier
                                            .ifElse(
                                                selectedIndex == adjustedIndex,
                                                Modifier.focusRequester(focusRequester),
                                            ),
                                )
                            }
                        }
                        item {
                            val interactionSource = remember { MutableInteractionSource() }
                            IconNavItem(
                                text = stringResource(R.string.settings),
                                icon = Icons.Default.Settings,
                                selected = false,
                                drawerOpen = isOpen,
                                interactionSource = interactionSource,
                                onClick = {
                                    viewModel.navigationManager.navigateTo(
                                        Destination.Settings(
                                            PreferenceScreenOption.BASIC,
                                        ),
                                    )
                                },
                                modifier = Modifier,
                            )
                        }
                    }
                }
            }
        },
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Drawer content
            DestinationContent(
                destination = destination,
                preferences = preferences,
                onClearBackdrop = onClearBackdrop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        // The rail opens over the content: nothing moves
                        .padding(start = closedDrawerWidth + 8.dp, end = 16.dp),
            )
            if (preferences.appPreferences.interfacePreferences.showClock) {
                TimeDisplay()
            }
        }
    }
}

@Composable
fun NavigationDrawerScope.ProfileIcon(
    user: JellyfinUser,
    imageUrl: String?,
    serverName: String,
    drawerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        leadingContent = {
            UserIconCardImage(
                id = user.id,
                name = user.name,
                imageUrl = imageUrl,
                alpha = if (drawerOpen) 1f else .5f,
                modifier = Modifier.size(DrawerIconSize),
            )
        },
        supportingContent = {
            Text(
                text = serverName,
                maxLines = 1,
            )
        },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = user.name ?: user.id.toString(),
            maxLines = 1,
        )
    }
}

@Composable
fun NavigationDrawerScope.IconNavItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    selected: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    subtext: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        colors = jvNavItemColors(Color.Transparent),
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(DrawerIconSize),
            )
        },
        supportingContent =
            subtext?.let {
                {
                    Text(
                        text = it,
                        maxLines = 1,
                    )
                }
            },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun NavigationDrawerScope.NavItem(
    library: NavDrawerItem,
    onClick: () -> Unit,
    selected: Boolean,
    moreExpanded: Boolean,
    drawerOpen: Boolean,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    containerColor: Color = Color.Unspecified,
) {
    val context = LocalContext.current
    val useFont = library !is ServerNavDrawerItem || library.type != CollectionType.LIVETV
    val icon =
        remember(library) {
            when (library) {
                NavDrawerItem.Favorites -> {
                    R.string.fa_heart
                }

                NavDrawerItem.MyLibrary -> {
                    R.string.fa_open_folder
                }

                NavDrawerItem.More -> {
                    R.string.fa_ellipsis
                }

                is ServerNavDrawerItem -> {
                    when (library.type) {
                        CollectionType.MOVIES -> R.string.fa_film
                        CollectionType.TVSHOWS -> R.string.fa_tv
                        CollectionType.HOMEVIDEOS -> R.string.fa_video
                        CollectionType.LIVETV -> R.drawable.gf_dvr
                        CollectionType.MUSIC -> R.string.fa_music
                        CollectionType.BOXSETS -> R.string.fa_open_folder
                        CollectionType.PLAYLISTS -> R.string.fa_list_ul
                        else -> R.string.fa_film
                    }
                }
            }
        }
    val focused by interactionSource.collectIsFocusedAsState()
    NavigationDrawerItem(
        modifier = modifier,
        selected = false,
        onClick = onClick,
        colors = jvNavItemColors(containerColor),
        leadingContent = {
            val color = navItemColor(selected, focused, drawerOpen)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (useFont) {
                    Text(
                        text = stringResource(icon),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp,
                        fontFamily = FontAwesome,
                        color = color,
                        modifier = Modifier,
                    )
                } else {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(DrawerIconSize),
                    )
                }
            }
        },
        trailingContent =
            if (library is NavDrawerItem.More) {
                {
                    Icon(
                        imageVector = if (moreExpanded) Icons.Default.ArrowDropDown else Icons.Default.KeyboardArrowLeft,
                        contentDescription = null,
                    )
                }
            } else {
                null
            },
        interactionSource = interactionSource,
    ) {
        Text(
            modifier = Modifier,
            text = library.name(context),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * [accent] if it reads on [background] (contrast 3:1 or more), else [fallback]; some themes have a dark primary
 */
fun readableAccent(
    accent: Color,
    background: Color,
    fallback: Color,
): Color {
    val a = accent.compositeOver(background).luminance()
    val b = background.luminance()
    val ratio = (maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f)
    return if (ratio >= 3f) accent else fallback
}

/**
 * Rail item container: a white pill with dark text when focused
 */
@Composable
fun jvNavItemColors(containerColor: Color) =
    NavigationDrawerItemDefaults.colors(
        containerColor = containerColor,
        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

/**
 * Rail item content color: dark on the focused white pill, lavender for the current page, secondary otherwise
 */
fun jvNavItemColor(
    selected: Boolean,
    focused: Boolean,
    text: Color,
    textSecondary: Color,
    accent: Color,
    onFocus: Color,
): Color =
    when {
        focused -> onFocus
        selected -> accent
        else -> textSecondary
    }

@Composable
@ReadOnlyComposable
@Suppress("UNUSED_PARAMETER")
fun navItemColor(
    selected: Boolean,
    focused: Boolean,
    drawerOpen: Boolean,
): Color =
    jvNavItemColor(
        selected = selected,
        focused = focused,
        text = MaterialTheme.colorScheme.onSurface,
        textSecondary = MaterialTheme.colorScheme.onSurfaceVariant,
        accent =
            readableAccent(
                MaterialTheme.colorScheme.primary,
                MaterialTheme.colorScheme.background,
                MaterialTheme.colorScheme.onSurface,
            ),
        onFocus = MaterialTheme.colorScheme.inverseOnSurface,
    )

val DrawerState.isOpen: Boolean get() = this.currentValue.isOpen

val DrawerValue.isOpen: Boolean get() = this == DrawerValue.Open
