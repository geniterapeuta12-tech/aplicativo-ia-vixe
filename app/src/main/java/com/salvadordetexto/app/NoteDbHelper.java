package com.salvadordetexto.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class NoteDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "salvador_texto.db";
    private static final int DB_VERSION = 1;

    public NoteDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL DEFAULT '',content TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT '',favorite INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_notes_updated ON notes(updated_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}

    public long save(Note note) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", note.title == null ? "" : note.title.trim());
        v.put("content", note.content == null ? "" : note.content);
        v.put("category", note.category == null ? "" : note.category.trim());
        v.put("favorite", note.favorite ? 1 : 0);
        v.put("updated_at", now);
        if (note.id <= 0) {
            v.put("created_at", now);
            note.id = db.insertOrThrow("notes", null, v);
            note.createdAt = now;
        } else {
            db.update("notes", v, "id=?", new String[]{String.valueOf(note.id)});
        }
        note.updatedAt = now;
        return note.id;
    }

    public void delete(long id) { getWritableDatabase().delete("notes", "id=?", new String[]{String.valueOf(id)}); }

    public Note duplicate(Note source) {
        Note copy = new Note(0, source.title.isEmpty() ? "Cópia" : source.title + " (cópia)", source.content, source.category, false, 0, 0);
        save(copy);
        return copy;
    }

    public List<Note> search(String query, boolean favoritesOnly) {
        SQLiteDatabase db = getReadableDatabase();
        List<Note> out = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        StringBuilder where = new StringBuilder("1=1");
        List<String> args = new ArrayList<>();
        if (favoritesOnly) where.append(" AND favorite=1");
        if (!q.isEmpty()) {
            where.append(" AND (title LIKE ? OR content LIKE ? OR category LIKE ?)");
            String like = "%" + q + "%";
            args.add(like); args.add(like); args.add(like);
        }
        try (Cursor c = db.query("notes", null, where.toString(), args.toArray(new String[0]), null, null, "updated_at DESC")) {
            while (c.moveToNext()) {
                out.add(new Note(c.getLong(c.getColumnIndexOrThrow("id")), c.getString(c.getColumnIndexOrThrow("title")), c.getString(c.getColumnIndexOrThrow("content")), c.getString(c.getColumnIndexOrThrow("category")), c.getInt(c.getColumnIndexOrThrow("favorite")) == 1, c.getLong(c.getColumnIndexOrThrow("created_at")), c.getLong(c.getColumnIndexOrThrow("updated_at"))));
            }
        }
        return out;
    }
}
