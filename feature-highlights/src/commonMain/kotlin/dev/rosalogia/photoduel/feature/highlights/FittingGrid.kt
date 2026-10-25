package dev.rosalogia.photoduel.feature.highlights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/**
 * Square tiles sized so all [items] fit the available space without scrolling, as large as
 * possible up to [maxTile]. If that would make them smaller than [minTile], it scrolls instead.
 * A handful of picks then fills a desktop window rather than sitting in one small row.
 */
@Composable
internal fun <T> FittingGrid(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    minTile: Dp = 96.dp,
    maxTile: Dp = 360.dp,
    padding: Dp = 12.dp,
    gap: Dp = 4.dp,
    tile: @Composable (T, Modifier) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val width = maxWidth - padding * 2
        val height = maxHeight - padding * 2
        val (columns, size) = bestFit(items.size, width, height, gap, maxTile)
        val fits = size >= minTile
        Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = if (fits) GridCells.Fixed(columns) else GridCells.Adaptive(minTile),
                contentPadding = PaddingValues(padding),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(gap),
                // Only as wide as the tiles, so a short row stays centered.
                modifier = if (fits) Modifier.width(size * columns + gap * (columns - 1) + padding * 2) else Modifier,
            ) {
                items(items, key = key) { tile(it, Modifier.aspectRatio(1f)) }
            }
        }
    }
}

/** The column count giving the largest square tile that fits [count] tiles in the area. */
private fun bestFit(count: Int, width: Dp, height: Dp, gap: Dp, maxTile: Dp): Pair<Int, Dp> {
    if (count == 0) return 1 to maxTile
    var best = 1 to 0.dp
    var bestGaps = 0
    for (columns in 1..count) {
        val rows = (count + columns - 1) / columns
        val byWidth = (width - gap * (columns - 1)) / columns
        val byHeight = (height - gap * (rows - 1)) / rows
        val size = min(min(byWidth, byHeight), maxTile)
        // On a tie (both capped by height or maxTile), the fuller last row wins: 2×2 over 3+1.
        val gaps = rows * columns - count
        if (size > best.second || size == best.second && gaps < bestGaps) {
            best = columns to size
            bestGaps = gaps
        }
    }
    return best
}
