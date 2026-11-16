package app.jingman.reader;
import java.util.*;
public class CombinedSearchTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static SiteIndex.Entry q(String id,String title){return new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/"+id,title);}
    static SiteIndex.Entry b(String id,String title){return new SiteIndex.Entry(BaoziSource.ORIGIN+"/baozimhapp/comic/"+id,title);}
    public static void main(String[] args){
        CombinedSearchResults results=new CombinedSearchResults("同名漫画");
        results.setQiman(Arrays.asList(q("A","同名漫画"),q("B","其他漫画"),q("A","同名漫画"),b("bad","不该混入")));
        check(results.qimanCount()==2,"deduplicate within source and reject wrong source");
        SiteIndex.Page first=new SiteIndex.Page();first.books.add(b("one","同名漫画"));first.books.add(b("two","第二部"));first.books.add(q("Bad","wrong source"));first.next=BaoziSource.withQuery(BaoziSource.searchUrl(results.query),"page","2");
        results.appendBaozi(BaoziSource.searchUrl(results.query),first);
        List<SiteIndex.Entry> merged=results.merged();
        check(merged.size()==4&&results.baoziCount()==2,"keep same-title editions from both sources");
        check(!Sources.baozi(merged.get(0).url)&&Sources.baozi(merged.get(1).url),"interleave results");
        check(results.baoziNext!=null,"preserve remote pagination");
        String secondUrl=results.baoziNext;SiteIndex.Page second=new SiteIndex.Page();second.books.add(b("one","同名漫画"));second.books.add(b("three","第三部"));second.next=BaoziSource.searchUrl("其他关键词");results.appendBaozi(secondUrl,second);
        check(results.merged().size()==5&&results.baoziNext==null,"append without loss, reject unrelated next query");
        CombinedSearchResults repeated=new CombinedSearchResults("同名漫画");repeated.appendBaozi(BaoziSource.searchUrl(repeated.query),first);String url=repeated.baoziNext;first.next=BaoziSource.withQuery(BaoziSource.searchUrl(repeated.query),"page","3");repeated.appendBaozi(url,first);check(repeated.baoziNext==null,"stop repeated remote result pages");
        results.baoziError="offline";check(results.qimanCount()==2&&results.merged().size()==5,"source failure keeps existing results");
        System.out.println("Combined search tests passed: separate editions, interleaving, deduplication, source isolation, pagination, failure preservation and repeated-page guard.");
    }
}
