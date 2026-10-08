package dev.titanslot.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import dev.titanslot.R

/** The insert scrape and eject clunk: slot's recording of a cart going into a GBA. */
class Sfx(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
        .build()
    private val insert = pool.load(context, R.raw.insert, 1)
    private val eject = pool.load(context, R.raw.eject, 1)

    fun insert() = play(insert)
    fun eject() = play(eject)

    private fun play(id: Int) {
        pool.play(id, 0.9f, 0.9f, 1, 0, 1f)
    }

    fun release() = pool.release()
}
