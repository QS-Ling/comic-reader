package app.jingman.reader;

import java.io.*;
import java.net.*;
import java.util.Locale;

/** Shared, JVM-testable source resolution for list entries without an inline cover. */
public final class CoverSource {
    public interface DetailReader { String read(String url) throws IOException; }
    public static String resolve(String preview,String comic,DetailReader reader) throws IOException {
        if(preview!=null&&!preview.isEmpty()) {
            String safe=Sources.coverUrl(preview,SiteIndex.ORIGIN);
            if(safe.isEmpty())throw new IOException("封面地址不受支持");
            return safe;
        }
        final String book;
        try { book=Sources.normalize(comic);if(!Sources.isComic(book))throw new IllegalArgumentException(); }
        catch(Exception e){throw new IOException("缺少漫画详情链接");}
        String cover=Sources.detailCover(reader.read(book),book);
        if(cover.isEmpty())throw new IOException("原站暂无封面");
        return cover;
    }
    public static String detail(String url) throws IOException {
        return Sources.baozi(url)?BaoziSource.fetch(url,false):QimanSource.fetch(url,false);
    }
    public static byte[] image(String url) throws IOException { return download(url,true); }
    private static byte[] download(String address,boolean image) throws IOException {
        String url=address;
        for(int redirect=0;redirect<4;redirect++) {
            try {
                if(!image&&new URL(url).getPath().equals("/err/comic"))throw new IOException("原站漫画已下架或链接失效");
                if(image){if(!url.equals(Sources.coverUrl(url,SiteIndex.ORIGIN)))throw new IOException("无效封面地址");}
                else{if(!url.equals(Sources.normalize(url))||!Sources.isComic(url))throw new IOException("无效详情地址");}
            }catch(IllegalArgumentException e){throw new IOException("无效详情地址");}
            if(image&&url.startsWith(SiteIndex.ORIGIN+"/")&&QimanSource.isCoolingDown())throw new IOException("官网暂时拒绝访问，封面稍后可重试");
            HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();
            c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
            c.setRequestProperty("User-Agent",url.contains(".baozicdn.com/")?BaoziSource.USER_AGENT:QimanSource.visitorUserAgent);c.setRequestProperty("Cache-Control","no-cache");
            try {
                int code=c.getResponseCode();
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException("封面跳转缺少地址");
                    url=new URL(new URL(url),location).toString();continue;
                }
                if(code!=200)throw new IOException("封面服务器 HTTP "+code);
                String type=c.getContentType();
                if(image&&(type==null||!type.toLowerCase(Locale.ROOT).startsWith("image/")))throw new IOException("服务器未返回图片");
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] buffer=new byte[8192];int n;int max=(image?3:4)*1024*1024;
                    while((n=in.read(buffer))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("请求已取消");if(out.size()+n>max)throw new IOException("封面数据过大");out.write(buffer,0,n);}
                    return out.toByteArray();
                }
            }finally{c.disconnect();}
        }
        throw new IOException("封面跳转次数过多");
    }
}
