package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class ChapterCacheTest {
    static void check(boolean value) { if(!value)throw new AssertionError(); }
    public static void main(String[] args) throws Exception {
        File root=Files.createTempDirectory("jingman-chapters-").toFile();
        ChapterCache c=new ChapterCache(root);
        String url="https://c-nd3-1.6wm.top/test.webp";
        ImageLoader.Result a=new ImageLoader.Result(new byte[]{1,2,3},"image/webp");
        c.put("book/ch1","第1话",url,a,c.epoch());
        c.put("book/ch2","第2话",url,new ImageLoader.Result(new byte[]{4,5},"image/webp"),c.epoch());
        // Reopening the cache simulates restarting the app; chapters remain isolated.
        c=new ChapterCache(root); check(Arrays.equals(c.get("book/ch1",url).bytes,a.bytes));
        check(c.get("book/ch2",url).bytes.length==2);check(c.list().size()==2);
        ImageLoader.chapters=c; ImageLoader.clearCache();
        check(ImageLoader.load(url,"book/ch1","第1话").bytes.length==3); // Disk hit; no network needed.
        int old=c.epoch();c.remove(ChapterCache.hash("book/ch1"));
        check(c.get("book/ch1",url)==null);check(c.get("book/ch2",url)!=null);
        c.put("book/ch1","第1话",url,a,old);check(c.get("book/ch1",url)==null);
        File broken=new File(c.folder("book/ch2"),ChapterCache.hash(url)+".img");
        try(FileOutputStream out=new FileOutputStream(broken)){out.write(0);}
        check(c.get("book/ch2",url)==null);
        c.clear();check(c.list().isEmpty());
        c.put("old","old",url,a,c.epoch());
        c.put("recent","recent",url,a,c.epoch());
        c.folder("old").setLastModified(1000);c.folder("recent").setLastModified(2000);
        long recentBytes=0;for(File f:c.folder("recent").listFiles())recentBytes+=f.length();
        old=c.epoch();c.setLimit(recentBytes);
        check(c.get("old",url)==null);check(c.get("recent",url)!=null);
        c.put("old","old",url,a,old);check(c.get("old",url)==null);
        c.setLimit(1);c.put("active","active",url,a,c.epoch());
        check(c.get("active",url)!=null); // One active chapter may exceed the limit.
        c.setLimit(0);c.put("unlimited","unlimited",url,a,c.epoch());check(c.list().size()==2);
        c.clear();Files.delete(root.toPath());
        System.out.println("Chapter cache tests passed: restart, offline disk hit, isolation, per-chapter delete, stale write, corrupt file.");
    }
}
