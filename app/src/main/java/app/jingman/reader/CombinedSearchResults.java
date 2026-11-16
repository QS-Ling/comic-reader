package app.jingman.reader;
import java.util.*;

/** UI-owned search state; keep separate source editions and interleave both sources. */
public final class CombinedSearchResults {
    public final String query;
    private final LinkedHashMap<String,SiteIndex.Entry> qiman=new LinkedHashMap<>(),baozi=new LinkedHashMap<>();
    private final Set<String> loadedPages=new HashSet<>();
    public boolean qimanLoading=true,baoziLoading;
    public String qimanError="",baoziError="",baoziNext;
    public CombinedSearchResults(String query){this.query=query;baoziNext=BaoziSource.searchUrl(query);}
    public void setQiman(List<SiteIndex.Entry> entries){add(qiman,entries,false);qimanLoading=false;}
    private static void add(Map<String,SiteIndex.Entry> target,List<SiteIndex.Entry> entries,boolean expectedBaozi){
        for(SiteIndex.Entry e:entries)try{String url=Sources.normalize(e.url);if(Sources.isComic(url)&&Sources.baozi(url)==expectedBaozi)target.put(url,new SiteIndex.Entry(url,e.title,3,e.cover));}catch(IllegalArgumentException ignored){}
    }
    public void appendBaozi(String pageUrl,SiteIndex.Page page){
        int oldSize=baozi.size();boolean alreadyHadPage=!loadedPages.isEmpty();add(baozi,page.books,true);loadedPages.add(pageUrl);baoziLoading=false;baoziError="";
        String next=page.next;
        try{if(next!=null){next=BaoziSource.normalize(next);if(!java.net.URI.create(next).getPath().equals("/baozimhapp/search")||!BaoziSource.query(next,"q","").equals(query))next=null;}}catch(IllegalArgumentException e){next=null;}
        baoziNext=next!=null&&!loadedPages.contains(next)&&(!alreadyHadPage||baozi.size()>oldSize)?next:null;
    }
    public int qimanCount(){return qiman.size();}
    public int baoziCount(){return baozi.size();}
    public List<SiteIndex.Entry> merged(){
        List<SiteIndex.Entry> result=new ArrayList<>();Iterator<SiteIndex.Entry> a=qiman.values().iterator(),b=baozi.values().iterator();
        while(a.hasNext()||b.hasNext()){if(a.hasNext())result.add(a.next());if(b.hasNext())result.add(b.next());}return result;
    }
}
