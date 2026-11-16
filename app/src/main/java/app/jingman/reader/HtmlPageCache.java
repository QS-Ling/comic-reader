package app.jingman.reader;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Short-lived HTML reuse and single-flight requests; failed pages are never cached. */
public final class HtmlPageCache {
    public interface Reader {String read()throws IOException;}
    private final long ttl;private final int maxChars,maxEntries;private int chars;
    private static final class Saved {final String html;final long time;Saved(String html){this.html=html;time=System.currentTimeMillis();}}
    private final LinkedHashMap<String,Saved> pages=new LinkedHashMap<>(16,.75f,true);
    private final Map<String,FutureTask<String>> pending=new HashMap<>();
    public HtmlPageCache(long ttl,int maxChars,int maxEntries){this.ttl=ttl;this.maxChars=maxChars;this.maxEntries=maxEntries;}
    public synchronized String get(String url){Saved saved=pages.get(url);if(saved==null)return null;long age=System.currentTimeMillis()-saved.time;if(age<0||age>=ttl){pages.remove(url);chars-=saved.html.length();return null;}return saved.html;}
    public synchronized void put(String url,String html){
        if(html.length()>maxChars)return;Saved old=pages.put(url,new Saved(html));if(old!=null)chars-=old.html.length();chars+=html.length();
        Iterator<Saved> entries=pages.values().iterator();while(chars>maxChars||pages.size()>maxEntries){chars-=entries.next().html.length();entries.remove();}
    }
    public String load(String url,Reader reader)throws IOException {
        FutureTask<String> task;boolean owner=false;
        synchronized(this){String saved=get(url);if(saved!=null)return saved;task=pending.get(url);if(task==null){task=new FutureTask<>(()->{String html=reader.read();put(url,html);return html;});pending.put(url,task);owner=true;}}
        if(owner)task.run();
        try{return task.get();}
        catch(InterruptedException e){Thread.currentThread().interrupt();throw new InterruptedIOException("请求已取消");}
        catch(ExecutionException e){Throwable cause=e.getCause();if(cause instanceof IOException)throw (IOException)cause;throw new IOException("页面请求失败",cause);}
        finally{if(owner)synchronized(this){if(pending.get(url)==task)pending.remove(url);}}
    }
}
