package app.jingman.reader;
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
public class HtmlPageCacheTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        HtmlPageCache cache=new HtmlPageCache(120000,100,2);AtomicInteger calls=new AtomicInteger();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);ExecutorService threads=Executors.newFixedThreadPool(2);
        try{
            HtmlPageCache.Reader reader=()->{calls.incrementAndGet();entered.countDown();try{if(!release.await(2,TimeUnit.SECONDS))throw new IOException("test timeout");}catch(InterruptedException e){throw new IOException(e);}return "detail";};
            Future<String> first=threads.submit(()->cache.load("same",reader));check(entered.await(2,TimeUnit.SECONDS),"owner entered");Future<String> second=threads.submit(()->cache.load("same",reader));release.countDown();
            check(first.get(2,TimeUnit.SECONDS).equals("detail")&&second.get(2,TimeUnit.SECONDS).equals("detail")&&calls.get()==1,"single-flight detail load");
        }finally{release.countDown();threads.shutdownNow();}
        for(int i=0;i<2;i++)try{cache.load("failed",()->{calls.incrementAndGet();throw new IOException("403");});}catch(IOException expected){}
        check(calls.get()==3,"failed requests not cached");
        HtmlPageCache limited=new HtmlPageCache(120000,5,2);limited.put("a","123");limited.put("b","456");check(limited.get("a")==null&&limited.get("b")!=null,"character cap eviction");limited.put("c","78");limited.get("b");limited.put("d","9");check(limited.get("c")==null&&limited.get("b")!=null,"LRU eviction");
        HtmlPageCache expired=new HtmlPageCache(0,100,2);expired.put("old","data");check(expired.get("old")==null,"expiry");
        System.out.println("HTML cache tests passed: concurrent request reuse, failed-response isolation, bounded LRU and expiry.");
    }
}
