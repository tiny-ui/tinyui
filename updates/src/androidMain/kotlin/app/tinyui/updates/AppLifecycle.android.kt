package app.tinyui.updates

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

actual fun UpdatesScheduler.attachToAppLifecycle() {
    val observer = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) = onForeground()

        override fun onStop(owner: LifecycleOwner) = onBackground()
    }
    // the registry is main-thread only; an app already started gets onStart right away
    val attach = { ProcessLifecycleOwner.get().lifecycle.addObserver(observer) }
    if (Looper.myLooper() == Looper.getMainLooper()) attach() else Handler(Looper.getMainLooper()).post(attach)
}
