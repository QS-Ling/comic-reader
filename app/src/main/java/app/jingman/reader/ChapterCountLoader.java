package app.jingman.reader;

import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.TextView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Load visible counts conservatively; cache each source independently for two hours. */
public final class ChapterCountLoader {
    public interface Reader {String read(String url)throws IOException;}
    private static final long TTL=2L*60*60*1000;
    private final Reader reader;
    private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
    private long lastRequestAt;
    private final Map<String,Pending> pending=new HashMap<>();
    private int epoch;private boolean closed;
    private static final class Request {
        final TextView view;final SiteIndex.Entry entry;final int epoch;
        Request(TextView view,SiteIndex.Entry entry,int epoch){this.view=view;this.entry=entry;this.epoch=epoch;}
    }
    private static final class Pending {final List<Request> listeners=new ArrayList<>();Future<?> task;volatile boolean cancelled;}
    public ChapterCountLoader(Context context,Reader reader){prefs=context.getSharedPreferences("chapter-counts",Context.MODE_PRIVATE);this.reader=reader;}
    public void bind(TextView view,SiteIndex.Entry entry){bind(view,entry,false);}
    private void bind(TextView view,SiteIndex.Entry entry,boolean force){
        Object old=view.getTag();if(!force&&old instanceof Request){Request r=(Request)old;if(r.epoch==epoch&&r.entry.url.equals(entry.url))return;}
        Request request=new Request(view,entry,epoch);view.setTag(request);view.setOnClickListener(null);view.setClickable(false);
        long age=System.currentTimeMillis()-prefs.getLong(entry.url+"#time",0);int cached=prefs.getInt(entry.url,-1);
        if(!force&&cached>0&&age>=0&&age<TTL){show(request,cached);return;}
        view.setText(Sources.name(entry.url)+" · 话数加载中");
        Pending job=pending.get(entry.url);if(job!=null){job.listeners.add(request);return;}
        job=new Pending();job.listeners.add(request);pending.put(entry.url,job);final Pending current=job;
        current.task=executor.submit(()->{
            int count=-1;try{
                long wait=1500-(SystemClock.elapsedRealtime()-lastRequestAt);if(wait>0)Thread.sleep(wait);
                if(current.cancelled||Thread.currentThread().isInterrupted())return;
                lastRequestAt=SystemClock.elapsedRealtime();count=ChapterTotals.count(reader.read(entry.url),entry.url);
            }catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(IOException|RuntimeException ignored){}
            final int total=count;
            main.post(()->{
                if(closed||pending.get(entry.url)!=current)return;
                pending.remove(entry.url);if(total>0)remember(entry.url,total);
                for(Request r:current.listeners)if(r.epoch==epoch&&r.view.getTag()==r)show(r,total);
            });
        });
    }
    private void show(Request r,int count){
        r.view.setText(Sources.name(r.entry.url)+(count>0?" · 共 "+count+" 话":" · 话数未获取，点此重试"));
        r.view.setContentDescription(r.entry.title+"，"+r.view.getText());
        if(count<=0)r.view.setOnClickListener(v->bind(r.view,r.entry,true));
    }
    public void remember(String url,int count){
        if(count<=0)return;
        SharedPreferences.Editor edit=prefs.edit().putInt(url,count).putLong(url+"#time",System.currentTimeMillis());
        List<String> timestamps=new ArrayList<>();for(String key:prefs.getAll().keySet())if(key.endsWith("#time")&&!key.equals(url+"#time"))timestamps.add(key);
        timestamps.sort(Comparator.comparingLong(key->prefs.getLong(key,0)));
        for(int i=0;i<timestamps.size()-255;i++){String key=timestamps.get(i);edit.remove(key);edit.remove(key.substring(0,key.length()-5));}edit.apply();
    }
    public void cancelRequests(){epoch++;for(Pending job:pending.values()){job.cancelled=true;if(job.task!=null)job.task.cancel(false);}pending.clear();executor.getQueue().clear();}
    public void resume(View view){
        if(view instanceof TextView&&view.getTag() instanceof Request){Request r=(Request)view.getTag();bind((TextView)view,r.entry);}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)resume(group.getChildAt(i));}
    }
    public void close(){closed=true;cancelRequests();executor.shutdownNow();}
}
