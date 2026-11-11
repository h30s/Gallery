package com.oxlo.gallary.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.oxlo.gallary.di.AppContainer
import com.oxlo.gallary.ui.SimpleViewModelFactory
import com.oxlo.gallary.ui.albums.AlbumsScreen
import com.oxlo.gallary.ui.gallery.GalleryScreen
import com.oxlo.gallary.ui.gallery.GalleryViewModel
import com.oxlo.gallary.ui.people.PeopleScreen
import com.oxlo.gallary.ui.people.PeopleViewModel
import com.oxlo.gallary.ui.search.SearchScreen
import com.oxlo.gallary.ui.search.SearchViewModel
import com.oxlo.gallary.ui.settings.MoreScreen
import com.oxlo.gallary.ui.settings.SettingsViewModel
import com.oxlo.gallary.ui.sources.SourcesScreen
import com.oxlo.gallary.ui.sources.SourcesViewModel
import com.oxlo.gallary.ui.tags.TagsScreen
import com.oxlo.gallary.ui.tags.TagsViewModel
import com.oxlo.gallary.ui.viewer.ViewerScreen
import com.oxlo.gallary.ui.viewer.ViewerViewModel

object Routes {
    const val LIBRARY = "library"
    const val ALBUMS = "albums"
    const val PEOPLE = "people"
    const val TAGS = "tags"
    const val MORE = "more"
    const val SEARCH = "search"
    const val SOURCES = "sources"
    const val VIEWER = "viewer"
}

private data class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector
)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.LIBRARY, "Photos", Icons.Rounded.PhotoLibrary),
    TopLevelDestination(Routes.ALBUMS, "Albums", Icons.Rounded.PhotoAlbum),
    TopLevelDestination(Routes.PEOPLE, "People", Icons.Rounded.Face),
    TopLevelDestination(Routes.TAGS, "Tags", Icons.Rounded.Sell),
    TopLevelDestination(Routes.MORE, "More", Icons.Rounded.MoreHoriz)
)

@Composable
fun GallaryNavHost(
    container: AppContainer,
    galleryViewModel: GalleryViewModel
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val settings by galleryViewModel.settings.collectAsState()

    val topLevelRoutes = remember { topLevelDestinations.map { it.route }.toSet() }

    val goToLibraryWith: (() -> Unit) -> Unit = { applyFilter ->
        applyFilter()
        navController.navigate(Routes.LIBRARY) {
            popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (currentRoute in topLevelRoutes) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    topLevelDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = currentRoute == destination.route,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Routes.LIBRARY,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding())
        ) {
            composable(Routes.LIBRARY) {
                val tags by remember(container) { container.mediaRepository.observeRawTags() }
                    .collectAsState(initial = emptyList())

                GalleryScreen(
                    viewModel = galleryViewModel,
                    tags = tags,
                    onOpenViewer = { index ->
                        container.viewerSession.open(galleryViewModel.media.value, index)
                        navController.navigate(Routes.VIEWER)
                    },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSettings = { navController.navigate(Routes.MORE) },
                    onOpenSources = { navController.navigate(Routes.SOURCES) }
                )
            }

            composable(Routes.ALBUMS) {
                val albums by galleryViewModel.albums.collectAsState()
                AlbumsScreen(
                    albums = albums,
                    onOpenAlbum = { bucket ->
                        goToLibraryWith { galleryViewModel.filterByAlbum(bucket) }
                    },
                    onOpenSources = { navController.navigate(Routes.SOURCES) }
                )
            }

            composable(Routes.PEOPLE) {
                val peopleViewModel: PeopleViewModel = viewModel(
                    factory = remember { SimpleViewModelFactory { PeopleViewModel(container) } }
                )
                PeopleScreen(
                    viewModel = peopleViewModel,
                    onOpenPerson = { personId ->
                        goToLibraryWith { galleryViewModel.filterByPerson(personId) }
                    }
                )
            }

            composable(Routes.TAGS) {
                val tagsViewModel: TagsViewModel = viewModel(
                    factory = remember { SimpleViewModelFactory { TagsViewModel(container) } }
                )
                TagsScreen(
                    viewModel = tagsViewModel,
                    onOpenTag = { tagId ->
                        goToLibraryWith { galleryViewModel.filterByTag(tagId) }
                    }
                )
            }

            composable(Routes.MORE) {
                val settingsViewModel: SettingsViewModel = viewModel(
                    factory = remember { SimpleViewModelFactory { SettingsViewModel(container) } }
                )
                MoreScreen(
                    viewModel = settingsViewModel,
                    onOpenSources = { navController.navigate(Routes.SOURCES) }
                )
            }

            composable(Routes.SEARCH) {
                val searchViewModel: SearchViewModel = viewModel(
                    factory = remember { SimpleViewModelFactory { SearchViewModel(container) } }
                )
                val filter by searchViewModel.filter.collectAsState()
                val results by searchViewModel.results.collectAsState()
                val tags by searchViewModel.tags.collectAsState()
                val people by searchViewModel.people.collectAsState()

                SearchScreen(
                    filter = filter,
                    results = results,
                    tags = tags,
                    people = people,
                    repository = container.mediaRepository,
                    columns = settings.gridColumns,
                    onFilterChange = searchViewModel::setFilter,
                    onOpenResult = { index ->
                        container.viewerSession.open(results, index)
                        navController.navigate(Routes.VIEWER)
                    },
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SOURCES) {
                val sourcesViewModel: SourcesViewModel = viewModel(
                    factory = remember { SimpleViewModelFactory { SourcesViewModel(container) } }
                )
                SourcesScreen(
                    viewModel = sourcesViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.VIEWER) {
                val viewerViewModel: ViewerViewModel = viewModel(
                    factory = remember { SimpleViewModelFactory { ViewerViewModel(container) } }
                )
                val allTags by remember(container) { container.mediaRepository.observeRawTags() }
                    .collectAsState(initial = emptyList())

                ViewerScreen(
                    items = container.viewerSession.items,
                    startIndex = container.viewerSession.startIndex,
                    viewModel = viewerViewModel,
                    repository = container.mediaRepository,
                    allTags = allTags,
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}
