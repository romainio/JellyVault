package tv.jellyvault.app.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.tv.material3.Text
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import timber.log.Timber
import tv.jellyvault.app.catalog.tmdb.TmdbMediaType
import tv.jellyvault.app.data.filter.DefaultForGenresFilterOptions
import tv.jellyvault.app.data.filter.DefaultForStudiosFilterOptions
import tv.jellyvault.app.preferences.PlayerBackend
import tv.jellyvault.app.preferences.UserPreferences
import tv.jellyvault.app.ui.components.ItemGrid
import tv.jellyvault.app.ui.components.LicenseInfo
import tv.jellyvault.app.ui.data.MovieSortOptions
import tv.jellyvault.app.ui.data.rememberSortOptions
import tv.jellyvault.app.ui.detail.CollectionFolderGeneric
import tv.jellyvault.app.ui.detail.CollectionFolderLiveTv
import tv.jellyvault.app.ui.detail.CollectionFolderMovie
import tv.jellyvault.app.ui.detail.CollectionFolderMusic
import tv.jellyvault.app.ui.detail.CollectionFolderPhotoAlbum
import tv.jellyvault.app.ui.detail.CollectionFolderPlaylist
import tv.jellyvault.app.ui.detail.CollectionFolderRecordings
import tv.jellyvault.app.ui.detail.CollectionFolderTv
import tv.jellyvault.app.ui.detail.DebugPage
import tv.jellyvault.app.ui.detail.FavoritesPage
import tv.jellyvault.app.ui.detail.HomeRowGrid
import tv.jellyvault.app.ui.detail.PersonPage
import tv.jellyvault.app.ui.detail.PlaylistDetails
import tv.jellyvault.app.ui.detail.collection.CollectionDetails
import tv.jellyvault.app.ui.detail.episode.EpisodeDetails
import tv.jellyvault.app.ui.detail.movie.MovieDetails
import tv.jellyvault.app.ui.detail.music.AlbumDetailsPage
import tv.jellyvault.app.ui.detail.music.ArtistDetailsPage
import tv.jellyvault.app.ui.detail.music.NowPlayingPage
import tv.jellyvault.app.ui.detail.music.SongDetailsPage
import tv.jellyvault.app.ui.detail.series.SeriesDetails
import tv.jellyvault.app.ui.detail.series.SeriesOverview
import tv.jellyvault.app.ui.detail.tmdb.TmdbDetailPage
import tv.jellyvault.app.ui.discover.DiscoverPage
import tv.jellyvault.app.ui.discover.TmdbBrowsePage
import tv.jellyvault.app.ui.library.MyLibraryPage
import tv.jellyvault.app.ui.main.HomePage
import tv.jellyvault.app.ui.main.settings.HomeSettingsPage
import tv.jellyvault.app.ui.playback.PlayExternalPage
import tv.jellyvault.app.ui.playback.PlaybackPage
import tv.jellyvault.app.ui.preferences.PreferencesPage
import tv.jellyvault.app.ui.preferences.arr.ArrSettingsPage
import tv.jellyvault.app.ui.preferences.subtitle.SubtitleStylePage
import tv.jellyvault.app.ui.preferences.tmdb.TmdbAccountPage
import tv.jellyvault.app.ui.preferences.user.UserProfilePreferencesPage
import tv.jellyvault.app.ui.search.SearchPage
import tv.jellyvault.app.ui.setup.InstallUpdatePage
import tv.jellyvault.app.ui.slideshow.SlideshowPage

/**
 * Chose the page for the [Destination]
 */
@Composable
fun DestinationContent(
    destination: Destination,
    preferences: UserPreferences,
    onClearBackdrop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (destination.fullScreen) {
        LaunchedEffect(Unit) { onClearBackdrop.invoke() }
    }
    when (destination) {
        is Destination.Home -> {
            HomePage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        is Destination.HomeSettings -> {
            HomeSettingsPage(preferences, modifier)
        }

        is Destination.Playback -> {
            val backend =
                destination.backend ?: preferences.appPreferences.playbackPreferences.playerBackend
            if (backend == PlayerBackend.EXTERNAL_PLAYER) {
                PlayExternalPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                PlaybackPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            }
        }

        is Destination.PlaybackList -> {
            if (preferences.appPreferences.playbackPreferences.playerBackend == PlayerBackend.EXTERNAL_PLAYER) {
                PlayExternalPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            } else {
                PlaybackPage(
                    preferences = preferences,
                    destination = destination,
                    modifier = modifier,
                )
            }
        }

        is Destination.Settings -> {
            PreferencesPage(
                preferences.appPreferences,
                destination.screen,
                modifier,
            )
        }

        is Destination.SubtitleSettings -> {
            SubtitleStylePage(
                preferences.appPreferences,
                destination.hdr,
                modifier,
            )
        }

        Destination.UserAppPreferences -> {
            UserProfilePreferencesPage(modifier)
        }

        is Destination.SeriesOverview -> {
            SeriesOverview(
                preferences = preferences,
                destination = destination,
                initialSeasonEpisode = destination.seasonEpisode,
                modifier = modifier,
            )
        }

        is Destination.MediaItem -> {
            when (destination.type) {
                BaseItemKind.SERIES -> {
                    SeriesDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.MOVIE -> {
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.VIDEO,
                BaseItemKind.MUSIC_VIDEO,
                -> {
                    // TODO Use VideoDetails
                    MovieDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.EPISODE -> {
                    EpisodeDetails(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.BOX_SET -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionDetails(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PLAYLIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PlaylistDetails(
                        preferences = preferences,
                        destination = destination,
                        modifier = modifier,
                    )
                }

                BaseItemKind.COLLECTION_FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.FOLDER -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = true,
                        recursiveOverride = null,
                        modifier = modifier,
                    )
                }

                BaseItemKind.USER_VIEW -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolder(
                        preferences = preferences,
                        destination = destination,
                        collectionType = destination.collectionType,
                        usePostersOverride = null,
                        recursiveOverride = true,
                        modifier = modifier,
                    )
                }

                BaseItemKind.PERSON -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    PersonPage(
                        preferences,
                        destination,
                        modifier,
                    )
                }

                BaseItemKind.PHOTO_ALBUM -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    CollectionFolderPhotoAlbum(
                        preferences = preferences,
                        itemId = destination.itemId,
                        recursive = false,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MUSIC_ALBUM -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    AlbumDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        initialSongId = destination.initialSongId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.MUSIC_ARTIST -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    ArtistDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                BaseItemKind.AUDIO -> {
                    LaunchedEffect(Unit) { onClearBackdrop.invoke() }
                    SongDetailsPage(
                        preferences = preferences,
                        itemId = destination.itemId,
                        modifier = modifier,
                    )
                }

                else -> {
                    Timber.w("Unsupported item type: ${destination.type}")
                    Text("Unsupported item type: ${destination.type}", modifier)
                }
            }
        }

        is Destination.FilteredCollection -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                filter = destination.filter,
                recursive = destination.recursive,
                usePosters = true,
                playEnabled = true, // TODO only genres use this currently, so might need to change in future
                filterOptions =
                    when (destination.parentType) {
                        BaseItemKind.GENRE -> DefaultForGenresFilterOptions
                        BaseItemKind.STUDIO -> DefaultForStudiosFilterOptions
                        else -> throw IllegalArgumentException("Unsupported parentType ${destination.parentType}")
                    },
                sortOptions = rememberSortOptions(destination.collectionType),
                modifier = modifier,
            )
        }

        is Destination.MoreHomeRow -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            HomeRowGrid(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        is Destination.Recordings -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            CollectionFolderRecordings(
                preferences,
                destination.itemId,
                false,
                modifier,
            )
        }

        is Destination.ItemGrid<*> -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            ItemGrid(
                preferences,
                destination,
                modifier,
            )
        }

        is Destination.Slideshow -> {
            SlideshowPage(
                slideshow = destination,
            )
        }

        is Destination.ArrSettings -> {
            ArrSettingsPage(service = destination.service, modifier = modifier)
        }

        Destination.TmdbAccount -> {
            TmdbAccountPage(modifier = modifier)
        }

        Destination.MyLibrary -> {
            MyLibraryPage(preferences = preferences, modifier = modifier)
        }

        is Destination.TmdbBrowse -> {
            TmdbBrowsePage(target = destination.target, preferences = preferences, modifier = modifier)
        }

        is Destination.TmdbDetail -> {
            TmdbDetailPage(item = destination.item, preferences = preferences, modifier = modifier)
        }

        Destination.Favorites -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            FavoritesPage(
                preferences = preferences,
                modifier = modifier,
            )
        }

        Destination.NowPlaying -> {
            NowPlayingPage(modifier)
        }

        Destination.UpdateApp -> {
            InstallUpdatePage(preferences, modifier)
        }

        Destination.License -> {
            LicenseInfo(modifier)
        }

        is Destination.Search -> {
            LaunchedEffect(Unit) { onClearBackdrop.invoke() }
            SearchPage(
                initialQuery = destination.query,
                userPreferences = preferences,
                modifier = modifier,
            )
        }

        Destination.Debug -> {
            DebugPage(preferences, modifier)
        }
    }
}

@Composable
fun CollectionFolder(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    collectionType: CollectionType?,
    usePostersOverride: Boolean?,
    recursiveOverride: Boolean?,
    modifier: Modifier = Modifier,
) {
    when (collectionType) {
        CollectionType.TVSHOWS -> {
            DiscoverPage(preferences, destination, TmdbMediaType.TV, modifier)
        }

        CollectionType.MOVIES -> {
            DiscoverPage(preferences, destination, TmdbMediaType.MOVIE, modifier)
        }

        CollectionType.BOXSETS -> {
            CollectionFolderGeneric(
                preferences = preferences,
                itemId = destination.itemId,
                usePosters = true,
                recursive = false,
                playEnabled = false,
                modifier = modifier,
                sortOptions = MovieSortOptions,
            )
        }

        CollectionType.PLAYLISTS -> {
            CollectionFolderPlaylist(
                preferences,
                destination.itemId,
                true,
                modifier,
            )
        }

        CollectionType.LIVETV -> {
            CollectionFolderLiveTv(
                preferences = preferences,
                destination = destination,
                modifier = modifier,
            )
        }

        CollectionType.MUSIC -> {
            CollectionFolderMusic(
                preferences,
                destination,
                modifier,
            )
        }

        CollectionType.HOMEVIDEOS,
        CollectionType.PHOTOS,
        -> {
            CollectionFolderPhotoAlbum(
                preferences = preferences,
                itemId = destination.itemId,
                recursive = recursiveOverride ?: false,
                modifier = modifier,
            )
        }

        CollectionType.MUSICVIDEOS,
        CollectionType.BOOKS,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = true,
                modifier = modifier,
            )
        }

        CollectionType.FOLDERS,
        CollectionType.TRAILERS,
        CollectionType.UNKNOWN,
        null,
        -> {
            CollectionFolderGeneric(
                preferences,
                destination.itemId,
                usePosters = usePostersOverride ?: false,
                recursive = recursiveOverride ?: false,
                playEnabled = false,
                modifier = modifier,
            )
        }
    }
}
