package com.glass.launcher.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.glass.launcher.LauncherState
import com.glass.launcher.Screen
import kotlin.math.min

/** Tamaño de diseño: todo se dibuja en "unidades" de una pantalla de 1280×720. */
private const val DESIGN_W = 1280f
private const val DESIGN_H = 690f

@Composable
fun GlassRoot(state: LauncherState) {
    var rootSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(state.wallpaperId, state.wallpaperVersion, rootSize) {
        if (rootSize.width > 0) state.renderWallpaper(rootSize.width, rootSize.height)
    }

    BackHandler(enabled = true) { state.back() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0E22))
            .onSizeChanged { rootSize = it }
    ) {
        val wp = state.wallpaperImages
        if (wp != null) {
            Image(wp.full, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = Modifier.fillMaxSize())
        }
        if (rootSize.width > 0) {
            // Escala la interfaz para que se vea igual con cualquier densidad de pantalla
            val scale = min(rootSize.width / DESIGN_W, rootSize.height / DESIGN_H)
            val backdrop = wp?.let { Backdrop(it.blurred, rootSize.width.toFloat(), rootSize.height.toFloat()) }
            CompositionLocalProvider(
                LocalDensity provides Density(scale, 1f),
                LocalBackdrop provides backdrop,
            ) {
                HomeScreen(state)
                AnimatedVisibility(
                    state.screen == Screen.LIBRARY,
                    enter = fadeIn() + scaleIn(initialScale = 1.04f),
                    exit = fadeOut() + scaleOut(targetScale = 1.04f),
                ) {
                    AppLibraryScreen(state)
                }
                AnimatedVisibility(
                    state.screen == Screen.SETTINGS,
                    enter = fadeIn() + scaleIn(initialScale = 0.97f),
                    exit = fadeOut() + scaleOut(targetScale = 0.97f),
                ) {
                    SettingsScreen(state)
                }
                SheetHost(state)
            }
        }
    }
}
