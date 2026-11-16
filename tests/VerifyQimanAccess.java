package app.jingman.reader;
public class VerifyQimanAccess {
    public static void main(String[] args)throws Exception{
        String url=SiteIndex.ORIGIN+"/comic/Zr2gL5nd86";
        String html=QimanSource.fetch(url,false);
        int count=ChapterTotals.count(html,url);
        if(count<=0||QimanSource.fetch(url,true)!=html)throw new AssertionError("Directory HTML not reused");
        System.out.println("Live reported comic fetched using production QimanSource: "+count+" chapters; passive count and foreground directory reused the same HTML.");
    }
}
