package com.zuehlke.carrera.javapilot.io;

public class StartReplayCommand {

    private final String tag;

    public StartReplayCommand(String tag) {
        this.tag = tag;
    }

    public String getTag() {
        return tag;
    }
}
