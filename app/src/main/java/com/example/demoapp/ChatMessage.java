package com.example.demoapp;

public class ChatMessage {
    private String message;
    private boolean isSentByUser;

    public ChatMessage(String message, boolean isSentByUser) {
        this.message = message;
        this.isSentByUser = isSentByUser;
    }

    public String getMessage() {
        return message;
    }

    public boolean isSentByUser() {
        return isSentByUser;
    }
    public void setMessage(String message) {
        this.message = message;
    }
}
