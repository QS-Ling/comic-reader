package app.jingman.reader;

import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Extract plain navigation data; never execute the site's HTML or scripts. */
public final class SiteIndex {
    public static final String ORIGIN="https://www.qimanwu.app";
    public static String normalize(String input) {
        try {
            String s=input.trim();
            if(s.matches("^/(?:www\\.|m\\.)?qimanwu\\.app(?:/.*)?$")) s=s.substring(1);
            if(s.matches("^(?:www\\.|m\\.)?qimanwu\\.app(?:[/?#].*)?$")) s="https://"+s;
            URI u=new URI(ORIGIN+"/").resolve(new URI(s));
            if(!Arrays.asList("https","http").contains(u.getScheme()) || u.getUserInfo()!=null || (u.getPort()!=-1&&u.getPort()!=443) ||
                !Arrays.asList("qimanwu.app","www.qimanwu.app","m.qimanwu.app").contains(u.getHost())) throw new IllegalArgumentException();
            String p=u.getRawPath();if(p==null||p.isEmpty())p="/";
            if(!p.matches("/(?:comic/[A-Za-z0-9]+/?|category(?:/[A-Za-z0-9]+)*/?|custom/(?:update|top)(?:/[A-Za-z0-9]+)*/?|search(?:/[^?#]+)?|index\\.php/search)?")) throw new IllegalArgumentException();
            return new URI(ORIGIN+p+(u.getRawQuery()==null?"":"?"+u.getRawQuery())).toASCIIString();
        } catch(Exception e) { throw new IllegalArgumentException("请输入奇漫屋首页、分类或漫画链接"); }
    }
    public static boolean isComic(String url) { return URI.create(url).getPath().matches("/comic/[A-Za-z0-9]+/?"); }
    public static String searchUrl(String query) { try { return ORIGIN+"/index.php/search?key="+URLEncoder.encode(query.trim(),"UTF-8"); } catch(Exception e) { throw new IllegalArgumentException(e); } }
    public static boolean isSearch(String url){String path=URI.create(url).getPath();return path.startsWith("/search/")||path.equals("/search")||path.equals("/index.php/search");}
    public static String searchQuery(String url){
        try {
            URI uri=URI.create(url);String path=uri.getRawPath();
            if(path.startsWith("/search/"))return URLDecoder.decode(path.substring(8).replace("+","%2B").replaceFirst("/page/[0-9]+/?$",""),"UTF-8");
            String query=uri.getRawQuery();if(query!=null)for(String pair:query.split("&")){String[] p=pair.split("=",2);if(URLDecoder.decode(p[0],"UTF-8").equals("key"))return p.length<2?"":URLDecoder.decode(p[1],"UTF-8");}
        }catch(Exception ignored){}
        return "";
    }
    static String attribute(String tag,String name) {
        Matcher m=Pattern.compile("(?:^|\\s)"+name+"\\s*=\\s*([\"'])(.*?)\\1",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(tag);
        return m.find()?decode(m.group(2)):"";
    }
    static String decode(String value) {
        Matcher m=Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);?").matcher(value);StringBuffer out=new StringBuffer();
        while(m.find()) { String x=m.group(1),r="";try{r=new String(Character.toChars(Integer.parseInt(x.startsWith("x")?x.substring(1):x,x.startsWith("x")?16:10)));}catch(Exception ignored){}m.appendReplacement(out,Matcher.quoteReplacement(r)); }m.appendTail(out);
        return out.toString().replace("&quot;","\"").replace("&apos;","'").replace("&nbsp;"," ").replace("&lt;","<").replace("&gt;",">").replace("&amp;","&");
    }
    public static String text(String value) { return decode(value.replaceAll("<[^>]*>"," ")).replaceAll("\\s+"," ").trim(); }
    public static final class Entry {
        public final String url,title,cover; final int score;
        public Entry(String url,String title) { this(url,title,3); }
        Entry(String url,String title,int score) { this(url,title,score,""); }
        Entry(String url,String title,int score,String cover) { this.url=url;this.title=title;this.score=score;this.cover=cover; }
        @Override public String toString() { return title; }
    }
    public static final class Page {
        public final ArrayList<Entry> books=new ArrayList<>(),filters=new ArrayList<>();
        public String previous,next;
        public boolean indexSearch;
        public int indexedBooks;
        public boolean savedHome;
        public long snapshotTime;
        public String sourceNote="";
        public boolean homeRefreshing;
    }
    public static String coverUrl(String address,String base) {
        try {
            URI u=URI.create(base).resolve(address.replace(" ","%20"));
            if(!Arrays.asList("https","http").contains(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443)||
                !Arrays.asList("qimanwu.app","www.qimanwu.app","m.qimanwu.app").contains(u.getHost())||
                !u.getPath().matches("(?i)/attachment/comic/[^?#]+\\.(?:webp|jpe?g|png|gif|avif)"))return "";
            return ORIGIN+u.getRawPath()+(u.getRawQuery()==null?"":"?"+u.getRawQuery());
        }catch(Exception e){return "";}
    }
    static String imageCover(String html,String base) {
        Matcher images=Pattern.compile("(?is)<img\\b([^>]*)>").matcher(html);
        while(images.find())for(String key:Arrays.asList("data-src","data-original","data-lazy-src","src")){
            String url=coverUrl(attribute(images.group(1),key),base);if(!url.isEmpty())return url;
        }
        return "";
    }
    public static String detailCover(String html,String base) {
        String safe=html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","").replaceAll("(?s)<!--.*?-->","");
        Matcher info=Pattern.compile("(?is)<div\\b[^>]*class=[\"'][^\"']*\\bcomicInfo\\b[^\"']*[\"']").matcher(safe);
        if(!info.find())return "";
        String body=safe.substring(info.start());
        Matcher end=Pattern.compile("(?is)<div\\b[^>]*class=[\"'][^\"']*\\bchapterList\\b[^\"']*[\"']").matcher(body);
        return imageCover(end.find()?body.substring(0,end.start()):body,base);
    }
    public static Page parse(String html,String base) {
        Page page=new Page();
        String safe=html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","").replaceAll("(?s)<!--.*?-->","");
        Matcher main=Pattern.compile("<div\\b[^>]*class=[\"'][^\"']*\\b(?:mainSearch|searchResult|mainForm|banner)\\b[^\"']*[\"']",Pattern.CASE_INSENSITIVE).matcher(safe);
        String body=main.find()?safe.substring(main.start()):safe;
        LinkedHashMap<String,Entry> books=new LinkedHashMap<>(),filters=new LinkedHashMap<>();
        Matcher a=Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a>").matcher(body);
        while(a.find()) {
            String href=attribute(a.group(1),"href"),url;
            try { url=normalize(URI.create(base).resolve(href).toString()); } catch(Exception e) { continue; }
            String label=text(a.group(2));
            if(label.equals("上一页")&&!url.equals(base))page.previous=url;
            if(label.equals("下一页")&&!url.equals(base))page.next=url;
            if(isComic(url)) {
                String cover=imageCover(a.group(2),base);
                String title=attribute(a.group(1),"title");int score=3;
                if(title.isEmpty()) {
                    Matcher img=Pattern.compile("(?is)<img\\b([^>]*)>").matcher(a.group(2));
                    if(img.find()){title=attribute(img.group(1),"title");if(title.isEmpty())title=attribute(img.group(1),"alt");score=2;}
                    if(title.isEmpty()){title=label;score=1;}
                }
                if(title.isEmpty()||title.equals("开始阅读")||title.length()>150)continue;
                Entry prior=books.get(url);
                if(prior!=null){if(cover.isEmpty())cover=prior.cover;if(score<=prior.score){title=prior.title;score=prior.score;}}
                books.put(url,new Entry(url,title,score,cover));
            } else if(URI.create(url).getPath().matches("/category/(?:tags|list|status|order)/[^/]+/?")&&!label.isEmpty()) filters.put(url,new Entry(url,label));
        }
        page.books.addAll(books.values());page.filters.addAll(filters.values());return page;
    }
}
