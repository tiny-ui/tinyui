package app.tinyui.updates

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

internal actual fun observeAppLifecycle(onForeground: () -> Unit, onBackground: () -> Unit): () -> Unit {
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()

        override fun onStop(owner: LifecycleOwner) = onBackground()
    }
    // the registry is main-thread only; an app already started gets onStart right away. Both go through the queue, so
    // a stop never runs ahead of the add it undoes
    val main = Handler(Looper.getMainLooper())
    val lifecycle = { ProcessLifecycleOwner.get().lifecycle }
    main.post { lifecycle().addObserver(observer) }
    return { main.post { lifecycle().removeObserver(observer) } }
}
