package app.jingman.reader;

import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Resolve missing list covers from the book detail page, then load off the UI thread. */
public final class CoverLoader {
    private final ExecutorService executor=Executors.newFixedThreadPool(2);
    private final Handler main=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> cache=new LruCache<String,Bitmap>(6*1024*1024){
        @Override protected int sizeOf(String key,Bitmap bitmap){return bitmap.getByteCount();}
    };
    private final Map<String,String> resolved=new HashMap<>();
    private volatile int epoch;
    private static final class Request {
        final String key,url,book;final TextView feedback;final int epoch;Future<?> task;
        Request(String key,String url,String book,TextView feedback,int epoch){this.key=key;this.url=url;this.book=book;this.feedback=feedback;this.epoch=epoch;}
    }
    // Cancel old views without throwing away reusable covers on every navigation.
    public void cancelRequests(){epoch++;}
    public void clear(){cancelRequests();cache.evictAll();resolved.clear();}
    public void close(){clear();executor.shutdownNow();}
    public void resume(android.view.View root){
        if(root instanceof ImageView&&root.getTag() instanceof Request){Request r=(Request)root.getTag();bind((ImageView)root,r.url,r.book,r.feedback);}
        if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++)resume(group.getChildAt(i));}
    }
    public void bind(ImageView view,String url){bind(view,url,"",null);}
    public void bindWithoutDetailLookup(ImageView view,String url,String book,TextView feedback){
        String known=url.isEmpty()?resolved.getOrDefault(book,""):url;
        if(!known.isEmpty()){bind(view,known,book,feedback);return;}
        Object old=view.getTag();if(old instanceof Request&&((Request)old).task!=null)((Request)old).task.cancel(true);
        view.setTag(null);view.setImageResource(android.R.drawable.ic_menu_gallery);view.setContentDescription("暂无已保存封面");if(feedback!=null)feedback.setText("暂无封面");
    }
    public void retry(ImageView view,String url,String book,TextView feedback){
        Object old=view.getTag();if(old instanceof Request&&((Request)old).task!=null)((Request)old).task.cancel(true);
        view.setTag(null);bind(view,url,book,feedback);
    }
    public void bind(ImageView view,String url,String book,TextView feedback){
        final String key=url+"|"+book;
        Object old=view.getTag();
        if(old instanceof Request){Request r=(Request)old;if(r.epoch==epoch&&r.key.equals(key))return;if(r.task!=null)r.task.cancel(true);}
        Request request=new Request(key,url,book,feedback,epoch);view.setTag(request);
        view.setImageResource(android.R.drawable.ic_menu_gallery);
        view.setContentDescription("封面加载中");if(feedback!=null)feedback.setText("加载中…");
        final String known=url.isEmpty()?resolved.getOrDefault(book,""):url;
        Bitmap saved=cache.get(known);
        if(saved!=null){view.setImageBitmap(saved);view.setContentDescription("漫画封面");if(feedback!=null)feedback.setText("");return;}
        request.task=executor.submit(()->{
            if(request.epoch!=epoch||Thread.currentThread().isInterrupted())return;
            Bitmap bitmap=null;String found="",error="加载失败，点击重试";
            try {
                found=CoverSource.resolve(known,book,address->{
                    if(request.epoch!=epoch||Thread.currentThread().isInterrupted())throw new InterruptedIOException("请求已取消");
                    return CoverSource.detail(address);
                });
                if(request.epoch!=epoch||Thread.currentThread().isInterrupted())return;
                byte[] bytes=CoverSource.image(found);
                BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
                BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(options.outWidth<=0||options.outHeight<=0)throw new IOException("图片格式不支持");
                options.inSampleSize=1;while(options.outWidth/options.inSampleSize>640||options.outHeight/options.inSampleSize>960)options.inSampleSize*=2;
                options.inJustDecodeBounds=false;bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
                if(bitmap==null)throw new IOException("图片解码失败");
            }catch(IOException|RuntimeException e){error=e.getMessage()==null?"加载失败，点击重试":e.getMessage();}
            final Bitmap result=bitmap;final String cover=found,message=error;
            main.post(()->{
                if(request.epoch!=epoch||view.getTag()!=request)return;
                if(result!=null){cache.put(cover,result);resolved.put(book,cover);view.setImageBitmap(result);view.setContentDescription("漫画封面");if(feedback!=null)feedback.setText("");}
                else{view.setContentDescription(message);if(feedback!=null)feedback.setText(message.equals("原站暂无封面")?"暂无封面":message+" · 点图重试");}
            });
        });
    }
}
