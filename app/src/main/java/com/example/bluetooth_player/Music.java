package com.example.bluetooth_player;

import android.net.Uri;

public class Music {

    private String title, artist, duration;
    private boolean isPlaying;
    private String musicFile;

    public Music(String title, String artist, String duration, boolean isPlaying, String musicFile) {
        this.title = title;
        this.artist = artist;
        this.duration = duration;
        this.isPlaying = isPlaying;
        this.musicFile = musicFile;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getDuration() {
        return duration;
    }

    public boolean isPlaying() {
        return isPlaying;
    }

    public String getMusicFile() {
        return musicFile;
    }

    public void setPlaying(boolean playing) {
        isPlaying = playing;
    }
}

