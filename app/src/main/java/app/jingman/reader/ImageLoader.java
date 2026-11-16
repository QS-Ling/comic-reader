package app.jingman.reader;

import java.io.*;
import java.net.*;
import java.security.*;

/** Direct images omit browser headers; the official Qiman proxy uses a validated chapter Referer. */
public final class ImageLoader {
    interface ConnectionFactory { HttpURLConnection open(URL url)throws IOException; }
    static volatile ConnectionFactory connections=url->(HttpURLConnection)url.openConnection();
    private static final java.util.LinkedHashMap<String,Boolean> proxyChapters=new java.util.LinkedHashMap<>(16,0.75f,true);
    public static Result loadQiman(String proxy,String direct,String referrer,String chapter,String title)throws IOException {
        URL url=new URL(proxy);
        if(!"https".equals(url.getProtocol())||!"s2.325784.xyz".equals(url.getHost())||url.getUserInfo()!=null||(url.getPort()!=-1&&url.getPort()!=443)||url.getQuery()!=null||url.getRef()!=null)throw new IOException("Unsupported image proxy");
        URL image=new URL(direct);
        if(!"https".equals(image.getProtocol())||!"c-nd3-1.6wm.top".equals(image.getHost())||image.getUserInfo()!=null||(image.getPort()!=-1&&image.getPort()!=443))throw new IOException("Unsupported image origin");
        final String parent;
        try{parent=QimanSource.normalize(referrer);if(!new URL(parent).getPath().startsWith("/chapter/"))throw new IllegalArgumentException();}catch(IllegalArgumentException e){throw new IOException("图片来源页面不受支持");}
        ChapterCache store=chapters;int diskEpoch=store==null?0:store.epoch();
        Result saved=store==null?null:store.get(chapter,proxy);
        if(saved==null)saved=cached(proxy);
        if(saved!=null)return saved;
        boolean preferred;synchronized(proxyChapters){preferred=proxyChapters.containsKey(chapter);}
        if(!preferred)try{return load(direct,chapter,title);}catch(IOException e){if(!missing(e))throw e;}
        Result result=download(proxy,parent);
        synchronized(proxyChapters){proxyChapters.put(chapter,true);while(proxyChapters.size()>128)proxyChapters.remove(proxyChapters.keySet().iterator().next());}
        if(store!=null)try{store.put(chapter,title,proxy,result,diskEpoch);}catch(IOException ignored){}
        return result;
    }
    public static ChapterCache chapters;
    public static Result load(String address,String chapter,String title) throws IOException {
        ChapterCache store=chapters;
        if(store==null) return load(address);
        int epoch=store.epoch();
        Result saved=store.get(chapter,address);
        if(saved!=null) return saved;
        Result result=load(address);
        try { store.put(chapter,title,address,result,epoch); } catch(IOException ignored) { /* A full disk must not prevent online reading. */ }
        return result;
    }
    public static final int MAX_CACHE_BYTES = 12*1024*1024;
    private static final java.util.LinkedHashMap<String,Result> cache = new java.util.LinkedHashMap<>(16,0.75f,true);
    private static int cacheBytes;
    private static int cacheEpoch;
    public static synchronized int cachedBytes() { return cacheBytes; }
    public static synchronized void clearCache() { cache.clear(); cacheBytes=0; cacheEpoch++; }
    private static synchronized int epoch() { return cacheEpoch; }
    private static synchronized void rememberIfCurrent(int epoch,String address,Result result) { if(epoch==cacheEpoch) remember(address,result); }
    static synchronized Result cached(String address) { return cache.get(address); }
    static synchronized void remember(String address,Result result) {
        if(result.bytes.length>MAX_CACHE_BYTES) return;
        Result old=cache.put(address,result);
        if(old!=null) cacheBytes-=old.bytes.length;
        cacheBytes+=result.bytes.length;
        java.util.Iterator<Result> entries=cache.values().iterator();
        while(cacheBytes>MAX_CACHE_BYTES || cache.size()>48) {
            cacheBytes-=entries.next().bytes.length; entries.remove();
        }
    }
    public static final String USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36";
    public static final class HttpError extends IOException {
        public final int code;
        HttpError(int code){super("图片服务器返回 "+code);this.code=code;}
    }
    public static boolean missing(IOException error){return error instanceof HttpError&&(((HttpError)error).code==404||((HttpError)error).code==410);}
    private static final String PLACEHOLDER = "4080ca5057dbf7f865082aec81e5e718045ff996dadd9c0021df31dcad418254";
    public static final class Result {
        public final byte[] bytes;
        public final String mime;
        Result(byte[] bytes, String mime) { this.bytes=bytes; this.mime=mime; }
    }
    public static Result load(String address) throws IOException {
        URL url = new URL(address);
        if(!"https".equals(url.getProtocol()) || (!"c-nd3-1.6wm.top".equals(url.getHost()) && !address.equals(BaoziSource.imageUrl(address,false))) || url.getUserInfo()!=null || (url.getPort()!=-1 && url.getPort()!=443))
            throw new IOException("Unsupported image origin");
        return download(address,null);
    }
    private static Result download(String address,String referrer) throws IOException {
        int epoch=epoch();
        Result existing=cached(address);
        if(existing!=null) return existing;
        URL url = new URL(address);
        HttpURLConnection c=connections.open(url);
        c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setInstanceFollowRedirects(false); c.setUseCaches(false);
        c.setRequestProperty("User-Agent",address.equals(BaoziSource.imageUrl(address,false))?BaoziSource.USER_AGENT:USER_AGENT);
        c.setRequestProperty("Accept","image/webp,image/*;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Cache-Control","no-cache");
        if(referrer!=null)c.setRequestProperty("Referer",referrer);
        boolean complete=false;
        // Intentionally create a fresh connection without copying any WebView headers.
        try {
            int code=c.getResponseCode();
            if(code!=200) throw new HttpError(code);
            String type=c.getContentType();
            if(type==null || !type.toLowerCase(java.util.Locale.ROOT).startsWith("image/")) throw new IOException("图片服务器返回了非图片内容");
            try(InputStream in=c.getInputStream(); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                byte[] buffer=new byte[8192]; int n;
                while((n=in.read(buffer))!=-1) { if(out.size()+n>16*1024*1024) throw new IOException("图片过大"); out.write(buffer,0,n); }
                byte[] bytes=out.toByteArray();
                try {
                    byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);
                    StringBuilder hex=new StringBuilder();
                    for(byte b:digest) hex.append(String.format(java.util.Locale.ROOT,"%02x",b & 255));
                    if(PLACEHOLDER.equals(hex.toString())) throw new IOException("图片服务器返回了提示图，请重试");
                } catch(NoSuchAlgorithmException e) { throw new IOException(e); }
                Result result=new Result(bytes,type.split(";")[0]);
                rememberIfCurrent(epoch,address,result);
                complete=true;
                return result;
            }
        } finally {
            // Fully consumed, closed responses return their connection to the HTTP pool.
            // Disconnecting a successful response can discard a reusable TLS connection.
            if(!complete) c.disconnect();
        }
    }
}
