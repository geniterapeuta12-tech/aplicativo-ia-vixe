package com.salvadordetexto.app;

public class Note {
    public long id;
    public String title;
    public String content;
    public String category;
    public boolean favorite;
    public long createdAt;
    public long updatedAt;

    public Note(long id, String title, String content, String category, boolean favorite, long createdAt, long updatedAt) {
        this.id = id;
        this.title = title == null ? "" : title;
        this.content = content == null ? "" : content;
        this.category = category == null ? "" : category;
        this.favorite = favorite;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
