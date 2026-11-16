package app.jingman.reader;
import java.nio.file.*;
import java.util.*;
public class QimanDuplicateChaptersTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        String html="<div id='chapterlistload'><a href='/chapter/A.html'>第1话 标题</a><a href='/chapter/B.html'>第1话 标题</a>"
            +"<a href='/chapter/C.html'>第2话 上</a><a href='/chapter/D.html'>第2话 下</a>"
            +"<a href='/chapter/E.html'>第3话 标题</a><a href='/chapter/F.html'>第3话　标题</a></div>";
        QimanChapters list=QimanChapters.parse(html);
        check(list.chapters.size()==4,"identical full labels merge; different subtitles remain");
        check(new ArrayList<>(list.chapters.keySet()).equals(Arrays.asList("/chapter/B.html","/chapter/C.html","/chapter/D.html","/chapter/F.html")),"replacement URL chosen without reordering chapters");
        check(list.canonical("/chapter/A.html").equals("/chapter/B.html"),"old progress resolves to merged chapter");
        check(list.alternatives("/chapter/A.html").equals(Arrays.asList("/chapter/B.html","/chapter/A.html")),"all alternate links retained");
        Set<String> attempted=new HashSet<>();attempted.add("/chapter/B.html");
        check(list.nextAlternative("/chapter/B.html",attempted).equals("/chapter/A.html"),"failed primary falls back to other entry");
        attempted.add("/chapter/A.html");
        check(list.nextAlternative("/chapter/A.html",attempted)==null,"exhausted alternatives stop instead of looping");
        check(list.nextAlternative("/chapter/C.html",Collections.singleton("/chapter/C.html"))==null,"single entry never retries another chapter or subtitle");
        check(ChapterTotals.count(html,SiteIndex.ORIGIN+"/comic/Test")==4,"totals use the same deduplication");
        String real=new String(Files.readAllBytes(Paths.get("build/duplicate-detail.html")),"UTF-8");
        QimanChapters comic=QimanChapters.parse(real);
        check(comic.chapters.containsKey("/chapter/lGoawTk1lm.html"),"reported working first chapter is primary");
        check(!comic.chapters.containsKey("/chapter/5p16giym45.html"),"reported 404 image entry removed from directory");
        check(comic.canonical("/chapter/5p16giym45.html").equals("/chapter/lGoawTk1lm.html"),"reported old progress retained");
        check(comic.chapters.containsValue("第14话 周婉婷尬住了")&&comic.chapters.containsValue("第14话 女朋友的定义"),"same chapter number with different subtitle remains accessible");
        System.out.println("Duplicate chapters passed: real directory "+ChapterTotals.qimanLinks(real).size()+" links -> "+comic.chapters.size()+" entries; progress aliases, alternatives and distinct subtitles preserved.");
    }
}
