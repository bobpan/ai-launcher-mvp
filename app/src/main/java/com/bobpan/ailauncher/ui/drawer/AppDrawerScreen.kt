package com.bobpan.ailauncher.ui.drawer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bobpan.ailauncher.R
import com.bobpan.ailauncher.ui.components.AppIconTile
import com.bobpan.ailauncher.ui.theme.LauncherPalette
import com.bobpan.ailauncher.ui.theme.LauncherShapes
import com.bobpan.ailauncher.ui.theme.Space
import com.bobpan.ailauncher.ui.theme.ambientBlobs
import com.bobpan.ailauncher.ui.theme.glassSurface
import com.bobpan.ailauncher.ui.theme.homeGradient
import kotlinx.coroutines.launch

@Composable
fun AppDrawerScreen(
    viewModel: AppDrawerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxSize()
            .homeGradient()
    ) {
        Box(Modifier.fillMaxSize().ambientBlobs())

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val count = when (val s = state) {
                    is DrawerUiState.Content -> s.apps.size
                    else -> 0
                }
                Text(
                    text = stringResource(R.string.drawer_header, count),
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back),
                        tint = LauncherPalette.TextPrimary
                    )
                }
            }

            when (val s = state) {
                is DrawerUiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("…", style = MaterialTheme.typography.titleLarge)
                    }
                }
                is DrawerUiState.Empty -> {
                    DrawerEmptyState(text = stringResource(R.string.drawer_empty))
                }
                is DrawerUiState.Error -> {
                    DrawerEmptyState(text = "应用列表暂时不可用")
                }
                is DrawerUiState.Content -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(
                            top = 12.dp,
                            bottom = 24.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ) {
                        items(s.apps, key = { it.packageName }) { app ->
                            AppIconTile(
                                app = app,
                                loadIcon = { pkg -> viewModel.iconFor(pkg) },
                                onClick = {
                                    scope.launch {
                                        val intent = viewModel.launchIntentFor(app.packageName)
                                        if (intent != null) {
                                            runCatching { context.startActivity(intent) }
                                                .onFailure {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(R.string.drawer_cannot_open)
                                                    )
                                                }
                                        } else {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.drawer_cannot_open)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp)
        )
    }
}

@Composable
private fun DrawerEmptyState(text: String) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minHeight = 180.dp)
                .glassSurface(shape = LauncherShapes.medium, elevated = true)
                .padding(24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Text("🫙", fontSize = 36.sp)
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
