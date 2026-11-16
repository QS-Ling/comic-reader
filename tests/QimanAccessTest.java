package app.jingman.reader;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
public class QimanAccessTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static final String HTML="<div id='chapterlistload'><a href='/chapter/A.html'>第1话</a></div>";
    static final class Reply {final int code;final String html,location;Reply(int code,String html){this(code,html,null);}Reply(int code,String html,String location){this.code=code;this.html=html;this.location=location;}}
    static final class Connection extends HttpURLConnection {
        final Reply reply;Connection(URL url,Reply reply){super(url);this.reply=reply;}
        public int getResponseCode(){return reply.code;}
        public String getHeaderField(String name){return name.equals("Location")?reply.location:null;}
        public InputStream getInputStream()throws IOException{return new ByteArrayInputStream(reply.html.getBytes("UTF-8"));}
        public InputStream getErrorStream(){try{return reply.code>=400?new ByteArrayInputStream(reply.html.getBytes("UTF-8")):null;}catch(IOException e){throw new RuntimeException(e);}}
        public void connect(){}public void disconnect(){}public boolean usingProxy(){return false;}
    }
    public static void main(String[] args)throws Exception {
        String origin=SiteIndex.ORIGIN;
        Map<String,Reply> replies=new HashMap<>();AtomicInteger requests=new AtomicInteger(),verifications=new AtomicInteger();Connection[] last={null};
        QimanSource.ConnectionFactory original=QimanSource.connections;
        try{
            QimanSource.connections=url->{requests.incrementAndGet();Reply reply=replies.get(SiteIndex.ORIGIN+url.getPath());if(reply==null)throw new IOException("Unexpected URL "+url);last[0]=new Connection(url,reply);return last[0];};
            QimanSource.visitorCookies=url->"cf_clearance=visitor";
            check(QimanSource.browserUserAgent("Mozilla/5.0 (Linux; Android 16; device; wv) AppleWebKit/537.36 Version/4.0 Chrome/157.0 Mobile Safari/537.36").equals("Mozilla/5.0 (Linux; Android 16; device) AppleWebKit/537.36 Chrome/157.0 Mobile Safari/537.36"),"actual mobile browser version retained without WebView-only markers");
            QimanSource.guestReader=(url,referrer)->{verifications.incrementAndGet();return HTML;};
            String a=origin+"/comic/TestA",b=origin+"/comic/TestB",c=origin+"/comic/TestC";
            replies.put(a,new Reply(200,HTML));check(QimanSource.fetch(a,false).equals(HTML),"normal detail");
            check(last[0].getRequestProperty("User-Agent").equals(QimanSource.USER_AGENT)&&last[0].getRequestProperty("Cookie").contains("cf_clearance"),"consistent UA and visitor cookie");
            check(last[0].getURL().getQuery().equals("pc=1"),"official desktop view selected");
            check(QimanSource.desktopUrl(SiteIndex.searchUrl("A+B")).startsWith(SiteIndex.ORIGIN+"/index.php/search?pc=1&key="),"pc mode preserves search query");
            check(QimanSource.desktopUrl(a+"?pc=2").equals(a+"?pc=1"),"pc mode does not duplicate flag");
            QimanSource.fetch(a,true);check(requests.get()==1,"count and directory reuse HTML");
            replies.put(b,new Reply(403,"<script src='/cdn-cgi/challenge-platform/verify'></script>"));boolean failed=false;try{QimanSource.fetch(b,false);}catch(IOException e){failed=true;}check(failed&&verifications.get()==0,"passive requests never open verification");
            int before=requests.get();try{QimanSource.fetch(c,false);}catch(IOException expected){}check(requests.get()==before,"cooldown stops background request bursts");
            check(QimanSource.fetch(a,false).equals(HTML),"cached pages remain usable during cooldown");
            check(QimanSource.fetch(b,true).equals(HTML)&&verifications.get()==1,"foreground 403 opens standard verification");
            before=requests.get();QimanSource.fetch(b,false);check(requests.get()==before,"verified document reused");
            replies.put(c,new Reply(200,"<title>Just a moment...</title><script src='/cdn-cgi/challenge-platform/test'></script>"));QimanSource.fetch(c,true);check(verifications.get()==2,"HTTP 200 challenge is not a usable detail");
            String d=origin+"/comic/TestD";replies.put(d,new Reply(403,"<script src='/cdn-cgi/challenge-platform/verify'></script>"));QimanSource.guestReader=(url,referrer)->{verifications.incrementAndGet();return "<title>403 Forbidden</title>";};
            failed=false;try{QimanSource.fetch(d,true);}catch(IOException e){failed=true;}check(failed,"verification denial is not accepted or cached");
            replies.put(d,new Reply(200,HTML));check(QimanSource.fetch(d,true).equals(HTML),"failed verification can be retried");
            String e=origin+"/comic/TestE";replies.put(e,new Reply(200,"<title>作品已下架</title>"));before=verifications.get();try{QimanSource.fetch(e,true);}catch(IOException expected){}check(verifications.get()==before,"missing directory is not misreported as a challenge");
            String f=origin+"/comic/TestF";replies.put(f,new Reply(302,"","https://evil.test/account"));before=requests.get();try{QimanSource.fetch(f,true);}catch(IOException expected){}check(requests.get()==before+1,"no off-site redirect requests");
            String chapter=origin+"/chapter/TestChapter.html";replies.put(chapter,new Reply(200,"<script>params = 'YWJj';</script>"));QimanSource.fetch(chapter,true,a);check(last[0].getRequestProperty("Referer").equals(a+"?pc=1"),"same-origin chapter document navigation");
            String denied=origin+"/comic/PlainDenied";replies.put(denied,new Reply(403,"<h1>403 Forbidden</h1><hr>openresty/1.27.1.2"));before=verifications.get();failed=false;try{QimanSource.fetch(denied,true);}catch(IOException expected){failed=expected.getMessage().contains("未提供验证码");}check(failed&&before==verifications.get(),"plain OpenResty denial must not open a fictitious verification dialog");
            check(GuestPagePolicy.page(a)&&GuestPagePolicy.page(origin+"/chapter/A.html"),"supported Qiman document routes");
            check(GuestPagePolicy.page(BaoziSource.HOME),"preserve Baozi verification");
            for(String unsafe:Arrays.asList("http://www.qimanwu.app/comic/A","https://user@www.qimanwu.app/comic/A","https://www.qimanwu.app:444/comic/A","https://www.qimanwu.app/account","https://evil.test/comic/A"))check(!GuestPagePolicy.page(unsafe),"reject unsafe document "+unsafe);
            check(GuestPagePolicy.challenge("https://challenges.cloudflare.com/turnstile/v0/api.js")&&!GuestPagePolicy.challenge("https://ads.test/ad.js"),"challenge resources only");
            check(GuestPagePolicy.hasChallenge("<script src='/cdn-cgi/challenge-platform/check'></script>")&&!GuestPagePolicy.hasChallenge("<title>403 Forbidden</title>"),"plain denial must not wait for a nonexistent challenge");
            check(GuestPagePolicy.matches(a,a+"?__cf_chl_rt_tk=test")&&!GuestPagePolicy.matches(a,b),"stale document callback guard");
            check(GuestPagePolicy.matches(SiteIndex.searchUrl("闪现"),origin+"/search/%E9%97%AA%E7%8E%B0"),"equivalent search routes");
            check(GuestPagePolicy.clearanceCookies("session=private; cf_clearance=one; __cf_bm=two; account=private").equals("cf_clearance=one; __cf_bm=two"),"only visitor cookies retained");
            System.out.println("Qiman access tests passed: cached directory reuse, visitor cookies/UA, passive cooldown, foreground verification, HTTP 200 challenges, failed verification retry, redirect/origin guards and stale callback isolation.");
        }finally{QimanSource.connections=original;QimanSource.guestReader=null;QimanSource.visitorCookies=null;}
    }
}
