package de.laurenz.scrollwave

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder

class ScrollwaveApplication : Application(), SingletonImageLoader.Factory {
    override fun newImageLoader(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(AnimatedImageDecoder.Factory())
        }
        .build()
}
