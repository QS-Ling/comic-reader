package app.jingman.reader;

import java.io.*;
import java.text.Normalizer;
import java.util.*;

/** Searchable public catalog snapshot; never mix entries from other sources. */
public final class QimanSearchIndex {
    private final LinkedHashMap<String,SiteIndex.Entry> books=new LinkedHashMap<>();
    public QimanSearchIndex(InputStream source) throws IOException { read(source); }
    public synchronized void read(InputStream source) throws IOException {
        try(BufferedReader in=new BufferedReader(new InputStreamReader(source,"UTF-8"))){
            String line;while((line=in.readLine())!=null){
                if(line.startsWith("#"))continue;
                String[] parts=line.split("\t",-1);if(parts.length<2)continue;
                try { add(new SiteIndex.Entry(parts[0],parts[1],3,parts.length>2?parts[2]:"")); }catch(IllegalArgumentException ignored){}
            }
        }
    }
    public synchronized void add(SiteIndex.Entry entry) {
        try {
            String url=SiteIndex.normalize(entry.url);
            if(!SiteIndex.isComic(url)||entry.title.trim().isEmpty())return;
            SiteIndex.Entry old=books.get(url);
            String cover=SiteIndex.coverUrl(entry.cover,SiteIndex.ORIGIN);
            if(cover.isEmpty()&&old!=null)cover=old.cover;
            books.put(url,new SiteIndex.Entry(url,entry.title,3,cover));
        }catch(IllegalArgumentException ignored){}
    }
    public synchronized int size(){return books.size();}
    public synchronized List<SiteIndex.Entry> entries(){return new ArrayList<>(books.values());}
    public synchronized SiteIndex.Entry find(String url){return books.get(url);}
    public synchronized List<SiteIndex.Entry> search(String query) {
        final String needle=key(query);List<SiteIndex.Entry> result=new ArrayList<>();if(needle.isEmpty())return result;
        for(SiteIndex.Entry entry:books.values())if(key(entry.title).contains(needle))result.add(entry);
        result.sort(Comparator.comparingInt((SiteIndex.Entry e)->key(e.title).equals(needle)?0:key(e.title).startsWith(needle)?1:2).thenComparing(e->e.title));
        return result;
    }
    private static String key(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replaceAll("\\s+","");}
    public synchronized void write(OutputStream target)throws IOException {
        try(Writer out=new OutputStreamWriter(target,"UTF-8")){
            for(SiteIndex.Entry entry:books.values())out.write(entry.url+"\t"+entry.title.replace('\t',' ').replace('\n',' ').replace('\r',' ')+"\t"+entry.cover+"\n");
        }
    }
}
