package me.echeung.moemoekyun.api.radio;

import android.content.Context;

import me.echeung.moemoekyun.api.player.StreamPlayer;

public class Stream {

    private StreamPlayer player;
    private Listener listener;

    public Stream(Context context) {
        this.player = new StreamPlayer(context);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void removeListener() {
        this.listener = null;
    }

    public boolean isStarted() {
        return player.isStarted();
    }

    public boolean isPlaying() {
        return player.isPlaying();
    }

    public void play() {
        if (player.play() && listener != null) {
            listener.onStreamPlay();
        }
    }

    public void pause() {
        if (player.pause() && listener != null) {
            listener.onStreamPause();
        }
    }

    public void stop() {
        if (player.stop() && listener != null) {
            listener.onStreamStop();
        }
    }

    public void fadeOut() {
        player.fadeOut(() -> {
            if (listener != null) {
                listener.onStreamStop();
            }
        });
    }

    public void duck() {
        player.duck();
    }

    public void unduck() {
        player.unduck();
    }

    public interface Listener {
        void onStreamPlay();
        void onStreamPause();
        void onStreamStop();
    }

}
