package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class ChapterTotalsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        String html="<a href='/chapter/Outside.html'>推荐</a><div id='chapterlistload'><div><a href='/chapter/A.html'>第1话</a><a href='/chapter/A.html'>重复</a></div><div style='display:none'><a href='/chapter/B.html'><span>第2话</span></a></div></div><a href='/chapter/Other.html'>相关推荐</a>";
        LinkedHashMap<String,String> chapters=ChapterTotals.qimanChapters(html);
        check(chapters.size()==2,"count unique hidden chapters but exclude recommendations");
        check(new ArrayList<>(chapters.keySet()).equals(Arrays.asList("/chapter/A.html","/chapter/B.html")),"canonical reading order");
        check(chapters.get("/chapter/B.html").equals("第2话"),"decode chapter labels");
        for(String invalid:Arrays.asList("<html>403</html>","<div id='chapterlistload'><a href='/chapter/A.html'>1</a>","<div id='chapterlistload'></div>")){
            boolean failed=false;try{ChapterTotals.qimanChapters(invalid);}catch(IOException e){failed=true;}check(failed,"unavailable total must not become zero");
        }
        String qiman=new String(Files.readAllBytes(Paths.get("build/site-detail.html")),"UTF-8");int qimanCount=ChapterTotals.count(qiman,SiteIndex.ORIGIN+"/comic/Zr2gL5nd86");check(qimanCount>=71,"real qiman detail");
        String baozi=new String(Files.readAllBytes(Paths.get("build/baozi-source/detail.html")),"UTF-8");int baoziCount=ChapterTotals.count(baozi,BaoziSource.ORIGIN+"/baozimhapp/comic/test");check(baoziCount==287,"all hidden baozi chapters included");
        System.out.println("Chapter totals passed: Qiman="+qimanCount+", Baozi="+baoziCount+", unique paths, hidden chapters, exact boundaries, reading order and explicit failures.");
    }
}
