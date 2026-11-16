package app.jingman.reader;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Official Qiman pages with visitor cookies, passive-request cooldown and visible verification. */
public final class QimanSource {
    public static final String USER_AGENT="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/130.0.0.0 Safari/537.36";
    public static volatile String visitorUserAgent=USER_AGENT;
    public static String browserUserAgent(String webViewAgent){return webViewAgent.replace("; wv","").replace(" Version/4.0","");}
    public interface PageReader {String read(String url,String referrer)throws IOException;}
    public static volatile PageReader guestReader;
    public static volatile BaoziSource.CookieReader visitorCookies;
    interface ConnectionFactory {HttpURLConnection open(URL url)throws IOException;}
    static volatile ConnectionFactory connections=url->(HttpURLConnection)url.openConnection();
    private static volatile long blockedUntil;
    public static boolean isCoolingDown(){return System.currentTimeMillis()<blockedUntil;}
    private static final HtmlPageCache cache=new HtmlPageCache(120_000,4_000_000,24);
    private static final class Status extends IOException {
        final int code;final boolean challenge;
        Status(int code){this(code,true);}
        Status(int code,boolean challenge){super("奇漫屋返回 "+code+(code==403?(challenge?"（需要访客验证）":"（源站直接拒绝访问，未提供验证码）"):""));this.code=code;this.challenge=challenge;if(code==403||code==429)blockedUntil=System.currentTimeMillis()+60_000;}
    }
    public static String normalize(String address){
        try{
            URI u=URI.create(SiteIndex.ORIGIN+"/").resolve(address);
            if(!Arrays.asList("https","http").contains(u.getScheme())||!Arrays.asList("www.qimanwu.app","qimanwu.app","m.qimanwu.app").contains(u.getHost())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new IllegalArgumentException();
            if(u.getRawPath()!=null&&u.getRawPath().matches("/chapter/[A-Za-z0-9]+\\.html"))return SiteIndex.ORIGIN+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
            return SiteIndex.normalize(address);
        }catch(Exception e){throw new IllegalArgumentException("页面地址不受支持");}
    }
    public static String desktopUrl(String address){
        String url=normalize(address);URI uri=URI.create(url);StringBuilder query=new StringBuilder("pc=1");
        if(uri.getRawQuery()!=null)for(String pair:uri.getRawQuery().split("&")){String key=pair.split("=",2)[0];if(!key.equals("pc"))query.append('&').append(pair);}
        return SiteIndex.ORIGIN+uri.getRawPath()+"?"+query;
    }
    public static boolean readable(String html,String url){
        String path=URI.create(url).getPath();
        if(path.startsWith("/comic/")){try{return !ChapterTotals.qimanChapters(html).isEmpty();}catch(IOException e){return false;}}
        if(path.startsWith("/chapter/"))return Pattern.compile("\\bparams\\s*=\\s*['\"][A-Za-z0-9+/=]+['\"]").matcher(html).find();
        return Pattern.compile("(?is)<div\\b[^>]*class=['\"][^'\"]*\\b(?:mainForm|mainSearch|searchResult|banner)\\b[^'\"]*['\"]").matcher(html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","")).find();
    }
    public static String fetch(String address,boolean interactive)throws IOException {
        return fetch(address,interactive,null);
    }
    public static String fetch(String address,boolean interactive,String parent)throws IOException {
        final String url;try{url=normalize(address);}catch(IllegalArgumentException e){throw new IOException(e.getMessage());}
        final String referrer;try{referrer=parent==null?null:normalize(parent);}catch(IllegalArgumentException e){throw new IOException("来源页面地址不受支持");}
        String saved=cache.get(url);if(saved!=null)return saved;
        if(!interactive&&System.currentTimeMillis()<blockedUntil)throw new IOException("奇漫屋请求冷却中，请稍后手动刷新");
        try{String html=cache.load(url,()->download(url,referrer));if(interactive)blockedUntil=0;return html;}
        catch(Status e){
            PageReader browser=guestReader;
            if(interactive&&e.code==403&&e.challenge&&browser!=null){String html=browser.read(url,referrer);if(!readable(html,url))throw new IOException("访客验证后仍未取得目录，请稍后重试");blockedUntil=0;cache.put(url,html);return html;}
            throw e;
        }
    }
    private static String download(String address,String referrer)throws IOException {
        String url=address;
        for(int redirect=0;redirect<4;redirect++){
            HttpURLConnection c=connections.open(new URL(desktopUrl(url)));
            c.setConnectTimeout(15000);c.setReadTimeout(20000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);
            c.setRequestProperty("User-Agent",visitorUserAgent);c.setRequestProperty("Accept","text/html,application/xhtml+xml;q=0.9,*/*;q=0.8");c.setRequestProperty("Accept-Language","zh-CN,zh;q=0.9,en;q=0.6");
            if(referrer!=null)c.setRequestProperty("Referer",desktopUrl(referrer));
            BaoziSource.CookieReader cookies=visitorCookies;if(cookies!=null){String value=cookies.cookies(url);if(value!=null&&!value.isEmpty())c.setRequestProperty("Cookie",value);}
            try{
                int code=c.getResponseCode();
                if(code==403||code==429)blockedUntil=System.currentTimeMillis()+60_000;
                System.err.println("Jingman Qiman HTTP "+code+" path="+c.getURL().getPath()+" desktop=true referrer="+(referrer!=null)+" server="+c.getHeaderField("Server"));
                if(code==301||code==302||code==303||code==307||code==308){
                    String location=c.getHeaderField("Location");if(location==null)throw new IOException("来源跳转地址缺失");
                    String next=new URL(new URL(url),location).toString();
                    if(GuestPagePolicy.challenge(next))throw new Status(403);
                    try{url=normalize(next);}catch(IllegalArgumentException e){throw new IOException("来源跳转到不受支持的页面");}continue;
                }
                if(code!=200){
                    boolean challenge="challenge".equalsIgnoreCase(c.getHeaderField("cf-mitigated"));
                    InputStream error=c.getErrorStream();if(error!=null)try{byte[] sample=new byte[8192];int n=error.read(sample);if(n>0)challenge|=GuestPagePolicy.hasChallenge(new String(sample,0,n,"UTF-8"));}finally{error.close();}
                    throw new Status(code,challenge);
                }
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("请求已取消");if(out.size()+n>4_000_000)throw new IOException("页面过大");out.write(bytes,0,n);}
                    String html=out.toString("UTF-8");if(!readable(html,address)){
                        if(Pattern.compile("(?is)cf-chl|/cdn-cgi/challenge-platform/|<title>\\s*(?:403|just a moment|attention required)").matcher(html).find())throw new Status(403,GuestPagePolicy.hasChallenge(html));
                        throw new IOException("奇漫屋页面缺少可用内容，作品可能下架或页面格式变化");
                    }return html;
                }
            }finally{c.disconnect();}
        }
        throw new IOException("来源跳转次数过多");
    }
}
