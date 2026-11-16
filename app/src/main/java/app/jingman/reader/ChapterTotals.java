package app.jingman.reader;
import java.io.IOException;
import java.util.*;
import java.util.regex.*;

/** Count unique readable chapter paths, including hidden chapters, excluding recommendations. */
public final class ChapterTotals {
    public static LinkedHashMap<String,String> qimanChapters(String html)throws IOException {
        return QimanChapters.parse(html).chapters;
    }
    static LinkedHashMap<String,String> qimanLinks(String html)throws IOException {
        String safe=html.replaceAll("(?is)<(script|style)\\b[^>]*>.*?</\\1>","").replaceAll("(?s)<!--.*?-->","");
        Matcher start=Pattern.compile("(?is)<div\\b[^>]*\\bid\\s*=\\s*(['\"])chapterlistload\\1[^>]*>").matcher(safe);
        if(!start.find())throw new IOException("未找到章节目录");
        int bodyStart=start.end(),end=-1,depth=1;
        Matcher div=Pattern.compile("(?is)</?div\\b[^>]*>").matcher(safe);div.region(bodyStart,safe.length());
        while(div.find()){if(div.group().startsWith("</"))depth--;else depth++;if(depth==0){end=div.start();break;}}
        if(end<0)throw new IOException("章节目录格式不完整");
        LinkedHashMap<String,String> result=new LinkedHashMap<>();
        Matcher links=Pattern.compile("(?is)<a\\b[^>]*href\\s*=\\s*['\"](/chapter/[A-Za-z0-9]+\\.html)['\"][^>]*>(.*?)</a>").matcher(safe.substring(bodyStart,end));
        while(links.find())if(!result.containsKey(links.group(1)))result.put(links.group(1),SiteIndex.text(links.group(2)));
        if(result.isEmpty())throw new IOException("未找到可阅读章节");
        return result;
    }
    public static int count(String html,String comic)throws IOException {
        return Sources.baozi(comic)?BaoziSource.detail(html,comic).paths.size():qimanChapters(html).size();
    }
}
