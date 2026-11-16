package app.jingman.reader;

import android.content.SharedPreferences;
import org.json.*;
import java.util.*;

/** Personal data stays in the reader's private preferences, independently of source accounts. */
public final class LibraryStore {
    public static final class Book {
        public String url,title,cover,summary,chapter="";
        public boolean favorite;
        public long readAt,addedAt;
        Book(String url,String title,String cover,String summary){this.url=url;this.title=title;this.cover=cover;this.summary=summary;}
    }
    private final SharedPreferences prefs;
    private final LinkedHashMap<String,Book> books=new LinkedHashMap<>();
    public LibraryStore(SharedPreferences prefs){
        this.prefs=prefs;
        try{JSONArray array=new JSONArray(prefs.getString("library.v1","[]"));for(int i=0;i<array.length();i++){
            try{JSONObject o=array.getJSONObject(i);String url=Sources.normalize(o.getString("url"));if(!Sources.isComic(url))continue;
                Book b=new Book(url,o.optString("title","漫画"),o.optString("cover",""),o.optString("summary",""));b.favorite=o.optBoolean("favorite");b.chapter=o.optString("chapter","");b.readAt=o.optLong("readAt");b.addedAt=o.optLong("addedAt");books.put(url,b);
            }catch(Exception ignored){}
        }}catch(Exception ignored){}
        if(prefs.getInt("library.coverMetadata",0)<2){for(Book b:books.values())if(Sources.baozi(b.url))b.cover="";persist();prefs.edit().putInt("library.coverMetadata",2).apply();}
    }
    public Book get(String url){return books.get(url);}
    public boolean favorite(String url){Book b=get(url);return b!=null&&b.favorite;}
    public Book remember(String url,String title,String cover,String summary){
        Book b=books.get(url);if(b==null){b=new Book(url,title,cover,summary);books.put(url,b);}else{b.title=title;if(!cover.isEmpty())b.cover=cover;if(!summary.isEmpty())b.summary=summary;}
        persist();return b;
    }
    public void toggle(String url){Book b=books.get(url);if(b==null)return;b.favorite=!b.favorite;if(b.favorite)b.addedAt=System.currentTimeMillis();persist();}
    public void read(String url,String title,String chapter){Book b=books.get(url);if(b==null)b=remember(url,title,"","");b.chapter=chapter;b.readAt=System.currentTimeMillis();persist();}
    public List<Book> list(boolean favorites){ArrayList<Book> result=new ArrayList<>();for(Book b:books.values())if(favorites?b.favorite:b.readAt>0)result.add(b);result.sort((a,b)->Long.compare(favorites?b.addedAt:b.readAt,favorites?a.addedAt:a.readAt));return result;}
    private void persist(){
        // Limit transient history while retaining every explicit favorite.
        if(books.size()>1500){ArrayList<Book> old=new ArrayList<>(books.values());old.sort((a,b)->Long.compare(a.readAt,b.readAt));for(Book b:old){if(books.size()<=1200)break;if(!b.favorite)books.remove(b.url);}}
        JSONArray array=new JSONArray();for(Book b:books.values())try{JSONObject o=new JSONObject();o.put("url",b.url);o.put("title",b.title);o.put("cover",b.cover);o.put("summary",b.summary);o.put("favorite",b.favorite);o.put("chapter",b.chapter);o.put("readAt",b.readAt);o.put("addedAt",b.addedAt);array.put(o);}catch(JSONException ignored){}
        prefs.edit().putString("library.v1",array.toString()).apply();
    }
}
