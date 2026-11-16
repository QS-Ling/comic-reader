package app.jingman.reader;

import java.net.*;
import java.util.*;
import java.util.regex.*;
import java.io.IOException;
import java.io.*;

/** Extract public page data only. Never evaluate remote scripts or app messages. */
public final class BaoziSource {
    public static final String ORIGIN="https://appcn.baozimh.com";
    public static final String HOME=ORIGIN+"/baozimhapp/homepage";
    public static final String USER_AGENT="Mozilla/5.0";
    public interface PageReader{String read(String url)throws IOException;}
    public interface CookieReader{String cookies(String url);}
    public static volatile PageReader guestReader;
    public static volatile CookieReader visitorCookies;
    public static volatile String visitorUserAgent=USER_AGENT;
    private static volatile long passiveBlockedUntil;
    public static String fetch(String address)throws IOException{
        return fetch(address,true);
    }
    public static String fetch(String address,boolean interactive)throws IOException{
        if(!interactive&&System.currentTimeMillis()<passiveBlockedUntil)throw new IOException("包子漫画暂时限制请求，请打开目录验证后重试");
        String normalized=normalize(address),path=normalized.substring(ORIGIN.length()).split("#",2)[0];IOException failure=null;
        for(String host:Arrays.asList(ORIGIN,"https://app.baozimh.com")){
            HttpURLConnection c=(HttpURLConnection)new URL(host+path).openConnection();
            c.setConnectTimeout(12000);c.setReadTimeout(18000);c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setRequestProperty("User-Agent",visitorUserAgent);
            CookieReader cookies=visitorCookies;if(cookies!=null){String value=cookies.cookies(host+path);if(value!=null&&!value.isEmpty())c.setRequestProperty("Cookie",value);}
            try{
                int code=c.getResponseCode();
                if(code!=200){
                    String hint="";InputStream error=c.getErrorStream();if(error!=null)try{byte[] sample=new byte[800];int n=error.read(sample);if(n>0){String s=new String(sample,0,n,"UTF-8");Matcher t=Pattern.compile("(?is)<title>(.*?)</title>").matcher(s);hint=t.find()?SiteIndex.text(t.group(1)):SiteIndex.text(s).substring(0,Math.min(100,SiteIndex.text(s).length()));}}finally{error.close();}
                    System.err.println("Jingman source HTTP "+code+" host="+new URL(host).getHost()+" server="+c.getHeaderField("Server")+" hint="+hint);
                    failure=new IOException("包子漫画返回 "+code+(code==403?"（来源访问受限）":""));
                    if(code==403||code==429)passiveBlockedUntil=System.currentTimeMillis()+60_000;
                    if(code==403){PageReader browser=guestReader;if(interactive&&browser!=null){String html=browser.read(normalized);passiveBlockedUntil=0;return html;}if(!interactive)throw failure;continue;}throw failure;
                }
                try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
                    byte[] bytes=new byte[8192];int n;while((n=in.read(bytes))!=-1){if(out.size()+n>4_000_000)throw new IOException("页面过大");out.write(bytes,0,n);}return out.toString("UTF-8");
                }
            }catch(IOException e){failure=e;if(!host.equals(ORIGIN)||!interactive&&System.currentTimeMillis()<passiveBlockedUntil)throw e;}
            finally{c.disconnect();}
        }
        throw failure==null?new IOException("来源暂时不可用"):failure;
    }
    public static boolean isUrl(String value) {
        try { String host=URI.create(value).getHost();return Arrays.asList("appcn.baozimh.com","app.baozimh.com","www.baozimh.com","baozimh.com").contains(host); }
        catch(Exception e){return false;}
    }
    public static String normalize(String input) {
        try {
            String s=input.trim();if(s.matches("^(?:(?:www|app|appcn)\\.)?baozimh\\.com(?:[/?#].*)?$"))s="https://"+s;
            URI u=URI.create(HOME).resolve(s);
            if(!isUrl(u.toString())||!Arrays.asList("https","http").contains(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))throw new IllegalArgumentException();
            String path=u.getRawPath();if(path==null||path.equals("/")||path.isEmpty())path="/baozimhapp/homepage";
            if(path.startsWith("/comic/"))path="/baozimhapp"+path;
            if(!path.matches("/baozimhapp/(?:homepage|classify|search|comic/[A-Za-z0-9_-]+|comic/chapter/[A-Za-z0-9_-]+/[0-9]+_[0-9]+\\.html)"))throw new IllegalArgumentException();
            String query=u.getRawQuery();if(query!=null){
                if(query.length()>2000)throw new IllegalArgumentException();
                for(String pair:query.split("&")){String[] kv=pair.split("=",2);String k=URLDecoder.decode(kv[0],"UTF-8");if(!Arrays.asList("q","page","limit","type","region","state","filter").contains(k))throw new IllegalArgumentException();}
            }
            String fragment=u.getRawFragment();if(fragment!=null&&!fragment.matches("hot|index_(?:cn|kr|jp|rx)_recommend"))throw new IllegalArgumentException();
            return ORIGIN+path+(query==null?"":"?"+query)+(fragment==null?"":"#"+fragment);
        }catch(Exception e){throw new IllegalArgumentException("请输入包子漫画首页、分类或漫画链接");}
    }
    public static boolean isComic(String url){return isUrl(url)&&URI.create(url).getPath().matches("/baozimhapp/comic/[A-Za-z0-9_-]+");}
    public static String searchUrl(String q){try{return ORIGIN+"/baozimhapp/search?q="+URLEncoder.encode(q.trim(),"UTF-8");}catch(Exception e){throw new IllegalArgumentException(e);}}
    public static String imageUrl(String address,boolean cover) {
        try {
            URI u=URI.create(SiteIndex.decode(address));String host=u.getHost(),path=u.getRawPath();
            if(!"https".equals(u.getScheme())||host==null||!host.matches("s[0-9]*\\.baozicdn\\.com")||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||path==null||path.contains(".."))return "";
            if(cover?!path.matches("/baozimhapp/cover/[A-Za-z0-9_./-]+\\.(?:jpg|jpeg|png|webp)"):!path.matches("/(?:w[0-9]+/)?scomic/[A-Za-z0-9_./-]+\\.(?:jpg|jpeg|png|webp)"))return "";
            if(path.endsWith("/default_cover.png"))return "";
            return u.toASCIIString();
        }catch(Exception e){return "";}
    }
    private static String safeHtml(String s){return s.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","").replaceAll("(?s)<!--.*?-->","");}
    public static String cover(String html){
        Matcher meta=Pattern.compile("(?is)<meta\\b([^>]*)>").matcher(html);
        while(meta.find())if("og:image".equals(SiteIndex.attribute(meta.group(1),"name"))||"og:image".equals(SiteIndex.attribute(meta.group(1),"property"))){String url=imageUrl(SiteIndex.attribute(meta.group(1),"content"),true);if(!url.isEmpty())return url;}
        Matcher m=Pattern.compile("(?is)<img\\b([^>]*)>").matcher(html);
        while(m.find())for(String attr:Arrays.asList("data-src","src")){String url=imageUrl(SiteIndex.attribute(m.group(1),attr),true);if(!url.isEmpty())return url;}
        return "";
    }
    public static SiteIndex.Page parse(String html,String base) {
        SiteIndex.Page page=new SiteIndex.Page();String safe=safeHtml(html),body=safe;
        String section=URI.create(base).getFragment();
        if(section!=null){
            Matcher sections=Pattern.compile("(?is)<div\\b[^>]*id=\"nav-([^\"]+)-box\"[^>]*>").matcher(safe);int start=-1,end=safe.length();
            while(sections.find()){if(start>=0){end=sections.start();break;}if(section.equals(sections.group(1)))start=sections.end();}
            body=start<0?"":safe.substring(start,end);
        }
        Matcher clicks=Pattern.compile("(?is)<(?:div|a)\\b([^>]*onclick=\"([^\"]*)\"[^>]*)>").matcher(body);
        ArrayList<String[]> cards=new ArrayList<>();ArrayList<Integer> ends=new ArrayList<>(),starts=new ArrayList<>();
        while(clicks.find()){Matcher id=Pattern.compile("\\['comic',\\s*'([A-Za-z0-9_-]+)'\\]").matcher(SiteIndex.decode(clicks.group(2)));if(id.find()){cards.add(new String[]{id.group(1),clicks.group(1)});starts.add(clicks.start());ends.add(clicks.end());}}
        LinkedHashMap<String,SiteIndex.Entry> books=new LinkedHashMap<>();
        for(int i=0;i<cards.size();i++){
            String[] card=cards.get(i);String title=SiteIndex.attribute(card[1],"title");if(title.isEmpty())title=SiteIndex.attribute(card[1],"aria-label");if(title.isEmpty())continue;
            String url=ORIGIN+"/baozimhapp/comic/"+card[0];String preview=cover(body.substring(ends.get(i),i+1<cards.size()?starts.get(i+1):body.length()));
            SiteIndex.Entry old=books.get(url);if(old!=null&&!old.cover.isEmpty())preview=old.cover;
            books.put(url,new SiteIndex.Entry(url,title,3,preview));
        }
        page.books.addAll(books.values());
        if(URI.create(base).getPath().endsWith("/homepage")){
            Matcher tabs=Pattern.compile("(?is)<li\\b[^>]*aria-controls=\"nav-([^\"]+)-box\"[^>]*>(.*?)</li>").matcher(safe);
            while(tabs.find())page.filters.add(new SiteIndex.Entry(HOME+"#"+tabs.group(1),SiteIndex.text(tabs.group(2))));
        }
        if(URI.create(base).getPath().endsWith("/classify")){
            Matcher filters=Pattern.compile("(?is)onclick=\"handle_change_filters\\(this, '([^']+)', '([^']+)'\\)\"[^>]*>\\s*([^<]+)").matcher(safe);
            while(filters.find()){
                String key=filters.group(1),value=filters.group(2),label=SiteIndex.text(filters.group(3));
                if(!Arrays.asList("region","state","type","filter").contains(key))continue;
                String group=key.equals("region")?"地区":key.equals("state")?"状态":key.equals("type")?"题材":"首字母";
                page.filters.add(new SiteIndex.Entry(withQuery(withQuery(base,key,value),"page","1"),group+" · "+label));
            }
        }
        if(URI.create(base).getPath().endsWith("/classify")||URI.create(base).getPath().endsWith("/search")){
            int number=1;try{number=Integer.parseInt(query(base,"page","1"));}catch(Exception ignored){}
            if(number>1)page.previous=withQuery(base,"page",String.valueOf(number-1));
            if(!page.books.isEmpty())page.next=withQuery(base,"page",String.valueOf(number+1));
        }
        return page;
    }
    public static String query(String url,String key,String fallback){try{String q=URI.create(url).getRawQuery();if(q!=null)for(String pair:q.split("&")){String[] kv=pair.split("=",2);if(URLDecoder.decode(kv[0],"UTF-8").equals(key))return kv.length>1?URLDecoder.decode(kv[1],"UTF-8"):"";}}catch(Exception ignored){}return fallback;}
    public static String withQuery(String url,String key,String value){
        try{URI u=URI.create(normalize(url));LinkedHashMap<String,String> pairs=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String pair:u.getRawQuery().split("&")){String[] kv=pair.split("=",2);pairs.put(URLDecoder.decode(kv[0],"UTF-8"),kv.length>1?URLDecoder.decode(kv[1],"UTF-8"):"");}pairs.put(key,value);
            StringJoiner out=new StringJoiner("&");for(Map.Entry<String,String> p:pairs.entrySet())out.add(URLEncoder.encode(p.getKey(),"UTF-8")+"="+URLEncoder.encode(p.getValue(),"UTF-8"));return ORIGIN+u.getRawPath()+"?"+out;
        }catch(Exception e){throw new IllegalArgumentException(e);}
    }
    public static final class Detail {
        public String title="漫画",author="",summary="",cover="";
        public final ArrayList<String> paths=new ArrayList<>(),names=new ArrayList<>();
    }
    public static Detail detail(String html,String base)throws IOException{
        Detail d=new Detail();String safe=safeHtml(html);d.cover=cover(safe);
        Matcher title=Pattern.compile("(?is)<h1\\b[^>]*>(.*?)</h1>").matcher(safe);if(title.find())d.title=SiteIndex.text(title.group(1));
        Matcher author=Pattern.compile("(?is)<h2\\b[^>]*class=\"[^\"]*comics-detail__author[^\"]*\"[^>]*>(.*?)</h2>").matcher(safe);if(author.find())d.author=SiteIndex.text(author.group(1));
        Matcher meta=Pattern.compile("(?is)<meta\\b([^>]*)>").matcher(safe);while(meta.find())if("description".equals(SiteIndex.attribute(meta.group(1),"name"))){d.summary=SiteIndex.attribute(meta.group(1),"content").replace("\\n","\n");break;}
        int start=safe.indexOf("id=\"chapter-items\"");if(start<0)throw new IOException("未找到公开章节目录");
        Matcher items=Pattern.compile("(?is)<div\\b[^>]*onclick=\"([^\"]*)\"[^>]*>\\s*<div\\b[^>]*>\\s*<span\\b[^>]*>(.*?)</span>").matcher(safe.substring(start));
        while(items.find()){
            Matcher args=Pattern.compile("\\['chapter',\\s*'([A-Za-z0-9_-]+)',\\s*'?([0-9]+)'?,\\s*'?([0-9]+)'?\\]").matcher(SiteIndex.decode(items.group(1)));
            if(!args.find())continue;String path="/baozimhapp/comic/chapter/"+args.group(1)+"/"+args.group(2)+"_"+args.group(3)+".html";
            if(!d.paths.contains(path)){d.paths.add(path);d.names.add(SiteIndex.text(items.group(2)));}
        }
        if(d.paths.isEmpty())throw new IOException("源站没有返回可阅读章节");return d;
    }
    public static ArrayList<String> images(String html)throws IOException{
        String safe=safeHtml(html);ArrayList<String> out=new ArrayList<>();Matcher m=Pattern.compile("(?is)<img\\b([^>]*)>").matcher(safe);
        while(m.find()){
            String attrs=m.group(1);if(!Arrays.asList(SiteIndex.attribute(attrs,"class").split("\\s+")).contains("comic-contain__item"))continue;
            String image=imageUrl(SiteIndex.attribute(attrs,"data-src"),false);if(!image.isEmpty()){image=image.replace(".com/scomic/",".com/w640/scomic/");if(!out.contains(image))out.add(image);}
        }
        if(out.isEmpty())throw new IOException("源站未提供公开图片，此章节可能受限或格式已变化");return out;
    }
}
