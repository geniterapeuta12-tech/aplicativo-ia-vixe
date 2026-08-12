package com.salvadordetexto.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class NoteDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "salvador_texto.db";
    private static final int DB_VERSION = 2;

    public NoteDbHelper(Context context) { super(context, DB_NAME, null, DB_VERSION); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY AUTOINCREMENT,title TEXT NOT NULL DEFAULT '',content TEXT NOT NULL DEFAULT '',category TEXT NOT NULL DEFAULT '',favorite INTEGER NOT NULL DEFAULT 0,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL,deleted_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX idx_notes_updated ON notes(updated_at DESC)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) db.execSQL("ALTER TABLE notes ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
    }

    public long save(Note note) {
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("title", note.title == null ? "" : note.title.trim());
        v.put("content", note.content == null ? "" : note.content);
        v.put("category", note.category == null ? "" : note.category.trim());
        v.put("favorite", note.favorite ? 1 : 0);
        v.put("updated_at", now);
        v.put("deleted_at", 0);
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

    public void moveToTrash(long id) {
        ContentValues v = new ContentValues();
        v.put("deleted_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void restore(long id) {
        ContentValues v = new ContentValues();
        v.put("deleted_at", 0);
        v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().update("notes", v, "id=?", new String[]{String.valueOf(id)});
    }

    public void deletePermanently(long id) {
        getWritableDatabase().delete("notes", "id=?", new String[]{String.valueOf(id)});
    }

    public void emptyTrash() {
        getWritableDatabase().delete("notes", "deleted_at>0", null);
    }

    public Note duplicate(Note source) {
        Note copy = new Note(0, source.title.isEmpty() ? "Cópia" : source.title + " (cópia)", source.content, source.category, false, 0, 0);
        save(copy);
        return copy;
    }

    public List<Note> search(String query, boolean favoritesOnly) {
        return queryNotes(query, favoritesOnly, false);
    }

    public List<Note> searchTrash(String query) {
        return queryNotes(query, false, true);
    }

    private List<Note> queryNotes(String query, boolean favoritesOnly, boolean trash) {
        SQLiteDatabase db = getReadableDatabase();
        List<Note> out = new ArrayList<>();
        String q = query == null ? "" : query.trim();
        StringBuilder where = new StringBuilder(trash ? "deleted_at>0" : "deleted_at=0");
        List<String> args = new ArrayList<>();
        if (favoritesOnly && !trash) where.append(" AND favorite=1");
        if (!q.isEmpty()) {
            where.append(" AND (title LIKE ? OR content LIKE ? OR category LIKE ?)");
            String like = "%" + q + "%";
            args.add(like); args.add(like); args.add(like);
        }
        try (Cursor c = db.query("notes", null, where.toString(), args.toArray(new String[0]), null, null, trash ? "deleted_at DESC" : "updated_at DESC")) {
            while (c.moveToNext()) out.add(fromCursor(c));
        }
        return out;
    }

    private Note fromCursor(Cursor c) {
        return new Note(
                c.getLong(c.getColumnIndexOrThrow("id")),
                c.getString(c.getColumnIndexOrThrow("title")),
                c.getString(c.getColumnIndexOrThrow("content")),
                c.getString(c.getColumnIndexOrThrow("category")),
                c.getInt(c.getColumnIndexOrThrow("favorite")) == 1,
                c.getLong(c.getColumnIndexOrThrow("created_at")),
                c.getLong(c.getColumnIndexOrThrow("updated_at"))
        );
    }

    public String exportJson() throws Exception {
        JSONArray notes = new JSONArray();
        SQLiteDatabase db = getReadableDatabase();
        try (Cursor c = db.query("notes", null, null, null, null, null, "id ASC")) {
            while (c.moveToNext()) {
                JSONObject o = new JSONObject();
                o.put("title", c.getString(c.getColumnIndexOrThrow("title")));
                o.put("content", c.getString(c.getColumnIndexOrThrow("content")));
                o.put("category", c.getString(c.getColumnIndexOrThrow("category")));
                o.put("favorite", c.getInt(c.getColumnIndexOrThrow("favorite")) == 1);
                o.put("createdAt", c.getLong(c.getColumnIndexOrThrow("created_at")));
                o.put("updatedAt", c.getLong(c.getColumnIndexOrThrow("updated_at")));
                o.put("deletedAt", c.getLong(c.getColumnIndexOrThrow("deleted_at")));
                notes.put(o);
            }
        }
        JSONObject root = new JSONObject();
        root.put("format", "salvador-de-texto-backup-v1");
        root.put("createdAt", System.currentTimeMillis());
        root.put("notes", notes);
        return root.toString(2);
    }

    public int importJson(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        if (!"salvador-de-texto-backup-v1".equals(root.optString("format"))) throw new IllegalArgumentException("Arquivo de backup inválido");
        JSONArray notes = root.getJSONArray("notes");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("notes", null, null);
            for (int i = 0; i < notes.length(); i++) {
                JSONObject o = notes.getJSONObject(i);
                ContentValues v = new ContentValues();
                v.put("title", o.optString("title", ""));
                v.put("content", o.optString("content", ""));
                v.put("category", o.optString("category", ""));
                v.put("favorite", o.optBoolean("favorite", false) ? 1 : 0);
                v.put("created_at", o.optLong("createdAt", System.currentTimeMillis()));
                v.put("updated_at", o.optLong("updatedAt", System.currentTimeMillis()));
                v.put("deleted_at", o.optLong("deletedAt", 0));
                db.insertOrThrow("notes", null, v);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return notes.length();
    }
}
