package dev.rosalogia.photoduel.platform

import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.map.Mapper
import coil3.request.Options
import dev.rosalogia.photoduel.core.common.AppLog
import dev.rosalogia.photoduel.core.ui.BrandIcons
import dev.rosalogia.photoduel.core.ui.PhotoActions
import dev.rosalogia.photoduel.core.ui.PhotoRef
import dev.rosalogia.photoduel.core.ui.ShareAction
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/**
 * [PhotoActions] through MediaStore requests and intents. Trash and favorite show Android's own
 * confirmation dialog, so the app never modifies files directly.
 */
@Composable
fun rememberAndroidPhotoActions(): PhotoActions {
    val context = LocalContext.current
    val library = remember(context) { LibraryActions(context.applicationContext) }
    val pending = remember { AtomicReference<CompletableDeferred<Boolean>?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        AppLog.i("Actions", "System request result ${it.resultCode}")
        pending.getAndSet(null)?.complete(it.resultCode == Activity.RESULT_OK)
    }
    return remember(context, library, launcher) {
        object : PhotoActions {
            private suspend fun request(sender: IntentSender): Boolean {
                val result = CompletableDeferred<Boolean>()
                pending.getAndSet(result)?.complete(false)
                launcher.launch(IntentSenderRequest.Builder(sender).build())
                return result.await()
            }

            override suspend fun trash(ids: List<Long>) = request(library.trashRequest(ids))

            override val trashNote = "Restorable for 30 days"

            override val canFavorite = true

            override suspend fun favorite(ids: List<Long>) = request(library.favoriteRequest(ids))

            override val share = ShareAction("Share", BrandIcons.Share)

            override fun share(ids: List<Long>) = context.startActivity(library.shareIntent(ids))

            override fun open(id: Long) = context.startActivity(library.viewIntent(id))
        }
    }
}

/** Lets Coil load a [PhotoRef] as its MediaStore content URI. */
class PhotoRefMapper : Mapper<PhotoRef, Uri> {
    override fun map(data: PhotoRef, options: Options): Uri = mediaContentUri(data.mediaId)
}
