package app.jingman.reader;
import java.io.*;
import java.util.*;
public class SearchAndOrderTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        for(String q:Arrays.asList("元尊","A+B & C","a/b","100%"," 空 格 ")){
            check(SiteIndex.searchQuery(SiteIndex.normalize(SiteIndex.searchUrl(q))).equals(q.trim()),"query encoding "+q);
        }
        check(SiteIndex.searchQuery(SiteIndex.ORIGIN+"/search/A+B%20C/page/2").equals("A+B C"),"legacy route pagination");
        check(SiteIndex.searchQuery(SiteIndex.ORIGIN+"/search?key=A%2BB+C&page=2").equals("A+B C"),"query route");
        check(SiteIndex.isSearch(SiteIndex.searchUrl("元尊")),"official search detection");
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(new byte[0]));
        index.add(new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/ABC","元尊"));
        index.add(new SiteIndex.Entry("https://appcn.baozimh.com/comic/abc","元尊"));
        index.add(new SiteIndex.Entry("https://evil.test/comic/abc","元尊"));
        index.add(new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/ABC","元尊"));
        index.add(new SiteIndex.Entry(SiteIndex.ORIGIN+"/comic/DEF","Ａ B C"));
        check(index.size()==2,"deduplicate and isolate sources");
        check(index.search("元尊").size()==1,"unvisited indexed comic searchable");
        check(index.search("abc").size()==1,"width, space and case normalization");
        check(index.search("").isEmpty(),"empty query");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();index.write(bytes);
        QimanSearchIndex reopened=new QimanSearchIndex(new ByteArrayInputStream(bytes.toByteArray()));
        check(reopened.size()==2&&reopened.search("元尊").size()==1,"persisted index");
        QimanSearchIndex shipped=new QimanSearchIndex(new FileInputStream("app/src/main/assets/qiman-catalog.tsv"));
        check(shipped.size()>=500,"shipped catalog independent of browsing history");
        check(!shipped.search("元尊").isEmpty()&&!shipped.search("斗罗").isEmpty(),"real official catalog examples");
        boolean flash=false;for(SiteIndex.Entry entry:shipped.search("闪现"))if(entry.url.equals(SiteIndex.ORIGIN+"/comic/Zr2gL5nd86"))flash=true;
        check(flash,"user reported comic searchable by partial name");
        for(int count:new int[]{0,1,5,287})for(boolean descending:new boolean[]{false,true}){
            Set<Integer> seen=new HashSet<>();for(int p=0;p<count;p++){
                int actual=ChapterOrder.index(p,count,descending);check(actual>=0&&actual<count,"valid chapter");seen.add(actual);
                check(ChapterOrder.index(actual,count,descending)==p,"reversible order");
            }
            check(seen.size()==count,"no missing or duplicated chapters");
            check(ChapterOrder.index(-1,count,descending)==-1&&ChapterOrder.index(count,count,descending)==-1,"header and bounds");
        }
        check(ChapterOrder.index(0,287,true)==286,"reverse opens latest chapter");
        System.out.println("Search and chapter order tests passed.");
    }
}
