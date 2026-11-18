package app.jingman.reader;
import java.nio.file.*;
import java.util.*;

public class BaoziSourceTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String fixture(String name)throws Exception{return new String(Files.readAllBytes(Paths.get("build/baozi-source/"+name+".html")),"UTF-8");}
    public static void main(String[] args)throws Exception{
        check(BaoziSource.normalize("https://www.baozimh.com/comic/test-book").equals(BaoziSource.ORIGIN+"/baozimhapp/comic/test-book"),"public comic alias");
        check(Sources.normalize("baozimh.com").equals(BaoziSource.HOME),"bare source root");
        for(String s:Arrays.asList("https://evil.test/baozimhapp/homepage","https://user@appcn.baozimh.com/baozimhapp/homepage","https://appcn.baozimh.com:444/baozimhapp/homepage","https://appcn.baozimh.com/baozimhapp/user/signin","https://appcn.baozimh.com/baozimhapp/homepage?token=secret","https://appcn.baozimh.com/baozimhapp/../user")){
            boolean rejected=false;try{BaoziSource.normalize(s);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"reject unexpected endpoint: "+s);
        }
        String homepage=fixture("homepage");
        SiteIndex.Page home=BaoziSource.parse(homepage,BaoziSource.HOME);
        SiteIndex.Page hot=BaoziSource.parse(homepage,BaoziSource.HOME+"#hot");
        SiteIndex.Page recommend=BaoziSource.parse(homepage,BaoziSource.HOME+"#index_cn_recommend");
        SiteIndex.Page korean=BaoziSource.parse(homepage,BaoziSource.HOME+"#index_kr_recommend");
        SiteIndex.Page japanese=BaoziSource.parse(homepage,BaoziSource.HOME+"#index_jp_recommend");
        SiteIndex.Page action=BaoziSource.parse(homepage,BaoziSource.HOME+"#index_rx_recommend");
        SiteIndex.Page category=BaoziSource.parse(fixture("classify"),BaoziSource.ORIGIN+"/baozimhapp/classify");
        SiteIndex.Page search=BaoziSource.parse(fixture("search"),BaoziSource.searchUrl("死灵"));
        check(home.books.size()>50,"homepage includes public recommendations");check(hot.books.size()>5&&hot.books.size()<home.books.size(),"hot tab isolated");
        check(recommend.books.size()>5,"recommendation tab");check(category.books.size()==36,"classification listing count");check(category.filters.size()>30,"real filter values");
        check(action.books.size()>5&&action.books.size()<home.books.size(),"isolated action recommendations");
        List<SiteIndex.Entry> firstPage=new ArrayList<>();
        Set<String> selected=new HashSet<>();
        for(SiteIndex.Page channel:Arrays.asList(hot,recommend,korean,japanese)){
            List<SiteIndex.Entry> distinct=new ArrayList<>();
            for(SiteIndex.Entry entry:channel.books)if(!selected.contains(entry.url))distinct.add(entry);
            for(SiteIndex.Entry entry:distinct.subList(0,3)){firstPage.add(entry);selected.add(entry.url);}
        }
        for(int i=0;i<firstPage.size();i++)check(home.books.get(i).url.equals(firstPage.get(i).url),"first page follows channel order and skips shared books at "+i);
        check(category.next.endsWith("page=2"),"category pagination");check(search.books.size()>5,"live search results");
        check(home.books.get(0).title.equals("死灵法师！我即是天灾"),"native message title extraction");check(!home.books.get(0).cover.contains("default_cover"),"real cover, not placeholder");
        BaoziSource.Detail detail=BaoziSource.detail(fixture("detail"),home.books.get(0).url);
        check(detail.paths.size()==287,"all chapters, including hidden HTML section");check(detail.names.get(0).contains("001"),"reading order preserved");
        check(detail.paths.get(0).endsWith("_rjogsq/0_0.html"),"use actual chapter slug and index");check(!detail.summary.isEmpty()&&!detail.author.isEmpty()&&!detail.cover.isEmpty(),"detail metadata");
        check(detail.cover.contains("/cover/silingfashiwojishitianzai-"),"detail cover must use its own metadata rather than a recommended book thumbnail");
        ArrayList<String> images=BaoziSource.images(fixture("chapter-first"));check(images.size()>10,"chapter images");check(images.get(0).contains("/scomic/")&&images.get(0).endsWith("/1.jpg"),"first page image");
        check(BaoziSource.imageUrl("https://s1.baozicdn.com.evil.test/scomic/x/1.jpg",false).isEmpty(),"reject lookalike CDN");
        check(BaoziSource.imageUrl("http://s1.baozicdn.com/scomic/x/1.jpg",false).isEmpty(),"HTTPS only");
        String fake="<script><div onclick=\"send_app_msg('call_page', ['comic', 'bad']);\" title=\"bad\"></div></script>";
        check(BaoziSource.parse(fake,BaoziSource.HOME).books.isEmpty(),"never parse executable content as data");
        String filter=BaoziSource.withQuery(BaoziSource.withQuery(BaoziSource.ORIGIN+"/baozimhapp/classify?type=rexie&page=3","region","cn"),"page","1");
        check(BaoziSource.query(filter,"type","").equals("rexie")&&BaoziSource.query(filter,"region","").equals("cn")&&BaoziSource.query(filter,"page","").equals("1"),"combined filters preserved");
        System.out.println("Baozi passed: home="+home.books.size()+", hot="+hot.books.size()+", recommendation="+recommend.books.size()+", category="+category.books.size()+", search="+search.books.size()+", chapters="+detail.paths.size()+", images="+images.size());
    }
}
