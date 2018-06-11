package de.markusressel.mkdocseditor.websocket

import android.support.annotation.CheckResult
import com.github.ajalt.timberkt.Timber
import hu.agta.rxwebsocket.RxWebSocket
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers


class SyncManager(url: String) {

    val rxWebSocket: RxWebSocket = RxWebSocket("wss://$url")

    /**
     * Connect to the given URL
     *
     * @param the url to connect to
     */
    fun connect(onConnected: () -> Unit) {
        rxWebSocket.onOpen()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = {
                    Timber.d { "WebSocket is Open" }
                    onConnected()
                }, onError = {
                    Timber.e(it)
                })

        rxWebSocket.onClosing()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = {
                    Timber.d { "WebSocket is Closing..." }
                }, onError = {
                    Timber.e(it)
                })

        rxWebSocket.onClosed()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = {
                    Timber.d { "WebSocket Closed" }
                }, onError = {
                    Timber.e(it)
                })

        rxWebSocket.onTextMessage()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = {
                    Timber.d { "Received Message: ${it.text}" }
                }, onError = {
                    Timber.e(it)
                })

        rxWebSocket.onFailure()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeBy(onNext = {
                    Timber.e(it.exception)
                }, onError = {
                    Timber.e(it)
                })

        rxWebSocket.connect()
    }

    /**
     * Send a patch to the server
     */
    @CheckResult
    fun sendPatch(patch: String): Single<Boolean> {
        return rxWebSocket.sendMessage(patch).subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
    }

    /**
     * Disconnect
     */
    fun disconnect(): Single<Boolean> {
        return rxWebSocket.close()
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
    }

}