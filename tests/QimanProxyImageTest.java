package app.jingman.reader;
import java.io.*;
import java.net.*;
import java.util.*;
public class QimanProxyImageTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static class Reply extends HttpURLConnection {
        final int code;Reply(URL u,int code){super(u);this.code=code;}
        public int getResponseCode(){return code;}
        public String getContentType(){return "image/webp";}
        public InputStream getInputStream(){return new ByteArrayInputStream(new byte[]{1,2,3});}
        public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
    }
    public static void main(String[] args)throws Exception {
        ImageLoader.ConnectionFactory original=ImageLoader.connections;
        List<Reply> requests=new ArrayList<>();
        String proxy="https://s2.325784.xyz/test",direct="https://c-nd3-1.6wm.top/coco/14/test.webp",parent=SiteIndex.ORIGIN+"/chapter/XLDBNSAOvX.html";
        try{
            ImageLoader.clearCache();ImageLoader.chapters=null;
            ImageLoader.connections=u->{Reply r=new Reply(u,u.getHost().equals("s2.325784.xyz")?200:404);requests.add(r);return r;};
            check(ImageLoader.loadQiman(proxy,direct,parent,"chapter-a","test").bytes.length==3,"404 direct falls back to official proxy");
            check(requests.size()==2,"one direct and one proxy request");
            check(requests.get(0).getRequestProperty("Referer")==null&&requests.get(0).getRequestProperty("Origin")==null,"direct image retains independent headers");
            check(parent.equals(requests.get(1).getRequestProperty("Referer")),"proxy gets exact validated chapter Referer");
            check(!requests.get(1).getInstanceFollowRedirects(),"proxy cannot redirect outside allowlist");
            ImageLoader.loadQiman(proxy+"2",direct+"2",parent,"chapter-a","test");check(requests.size()==3&&requests.get(2).getURL().getHost().equals("s2.325784.xyz"),"remaining pages skip known missing direct route");
            ImageLoader.loadQiman(proxy,direct,parent,"chapter-a","test");check(requests.size()==3,"proxy response reused from cache");
            File disk=java.nio.file.Files.createTempDirectory(java.nio.file.Paths.get("build"),"qiman-proxy-cache-test-").toFile();
            ImageLoader.chapters=new ChapterCache(disk);
            ImageLoader.loadQiman(proxy+"disk",direct+"disk",parent,"chapter-disk","test");
            ImageLoader.clearCache();ImageLoader.chapters=new ChapterCache(disk);
            ImageLoader.connections=u->{throw new IOException("offline");};
            check(ImageLoader.loadQiman(proxy+"disk",direct+"disk",parent,"chapter-disk","test").bytes.length==3,"proxy disk cache survives restart and works offline before direct request");
            ImageLoader.chapters=null;
            for(String bad:Arrays.asList("https://evil.test/test","https://s2.325784.xyz.evil.test/test","https://user@s2.325784.xyz/test","http://s2.325784.xyz/test")){
                boolean failed=false;try{ImageLoader.loadQiman(bad,direct,parent,"bad","test");}catch(IOException e){failed=true;}check(failed,"reject untrusted proxy");
            }
            boolean rejected=false;try{ImageLoader.loadQiman(proxy,direct,"https://evil.test/chapter/Test.html","bad","test");}catch(IOException e){rejected=true;}check(rejected,"reject external referrer before cached response");
            requests.clear();ImageLoader.connections=u->{Reply r=new Reply(u,403);requests.add(r);return r;};
            try{ImageLoader.loadQiman(proxy+"3",direct+"3",parent,"chapter-b","test");throw new AssertionError("403 must fail");}catch(ImageLoader.HttpError e){check(e.code==403&&requests.size()==1,"403 does not trigger automatic proxy retry");}
            System.out.println("Qiman proxy image tests passed: missing direct route, exact proxy Referer, bounded fallback, cached reuse, origin guards and no retry for 403.");
        }finally{ImageLoader.connections=original;ImageLoader.chapters=null;ImageLoader.clearCache();}
    }
}
